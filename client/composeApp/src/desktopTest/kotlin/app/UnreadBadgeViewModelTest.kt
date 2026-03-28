package app

import app.dto.StoredContact
import app.dto.StoredMessage
import app.dto.StoredSession
import app.network.ApiExecutor
import app.network.FamilyMessengerApiClient
import app.repository.AdminRepository
import app.repository.ContactsRepository
import app.repository.DeviceRepository
import app.repository.MessagesRepository
import app.repository.PresenceRepository
import app.repository.SessionRepository
import app.repository.SetupRepository
import app.storage.ClientSettingsRepository
import app.storage.LocalDatabase
import app.storage.SessionStore
import app.ui.Screen
import app.usecase.CreateMemberUseCase
import app.usecase.LoadContactsUseCase
import app.usecase.LoadSetupStatusUseCase
import app.usecase.LoginUseCase
import app.usecase.RemoveMemberUseCase
import app.usecase.SendQuickActionUseCase
import app.usecase.SendTextMessageUseCase
import app.usecase.ShareLocationUseCase
import app.usecase.VerifyAdminAccessUseCase
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.DeviceSession
import com.familymessenger.contract.ErrorCode
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.FamilySummary
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.SetupStatusResponse
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.UserProfile
import com.familymessenger.contract.UserRole
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UnreadBadgeViewModelTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun directIncomingMessageAddsUnreadBadgeAndOpeningChatClearsIt() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Катя", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val childContact = ContactSummary(user = child, isOnline = true)
        val apiClient = apiClient(parent, family, listOf(childContact))
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {
            sessionStore.save(
                StoredSession(
                    auth = AuthPayload(
                        user = parent,
                        family = family,
                        session = DeviceSession(token = "test-token"),
                    ),
                ),
            )
            localDatabase.update { snapshot ->
                snapshot.copy(
                    contacts = listOf(childContact).map { StoredContact(it, Clock.System.now()) },
                )
            }

            val incomingMessage = com.familymessenger.contract.MessagePayload(
                id = 101,
                clientMessageUuid = "11111111-1111-1111-1111-111111111111",
                familyId = family.id,
                senderUserId = child.id,
                recipientUserId = parent.id,
                type = com.familymessenger.contract.MessageType.TEXT,
                body = "Привет, папа",
                createdAt = Instant.parse("2026-03-22T20:05:00Z"),
            )
            localDatabase.update { snapshot ->
                snapshot.copy(messages = snapshot.messages + StoredMessage(incomingMessage, Clock.System.now()))
            }

            waitUntil("unread badge appears for child chat") {
                viewModel.state.value.unreadCounts[child.id] == 1
            }
            assertEquals(1, viewModel.state.value.unreadCounts[child.id])

            viewModel.openChat(childContact)

            waitUntil("chat opens") {
                viewModel.state.value.screen == Screen.CHAT && viewModel.state.value.selectedContactId == child.id
            }
            waitUntil("unread badge clears after opening chat") {
                (viewModel.state.value.unreadCounts[child.id] ?: 0) == 0
            }
            assertTrue(viewModel.state.value.messages.any { it.id == incomingMessage.id })
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun familyIncomingMessageAddsUnreadBadgeAndOpeningChatClearsIt() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Катя", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val familyContact = ContactSummary(
            user = UserProfile(
                id = FAMILY_GROUP_CHAT_ID,
                familyId = family.id,
                displayName = family.name,
                role = UserRole.FAMILY,
                isAdmin = false,
                lastSeenAt = null,
            ),
            isOnline = true,
        )
        val childContact = ContactSummary(user = child, isOnline = true)
        val apiClient = apiClient(parent, family, listOf(familyContact, childContact))
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {
            sessionStore.save(
                StoredSession(
                    auth = AuthPayload(
                        user = parent,
                        family = family,
                        session = com.familymessenger.contract.DeviceSession(token = "test-token"),
                    ),
                ),
            )
            localDatabase.update { snapshot ->
                snapshot.copy(
                    contacts = listOf(familyContact, childContact).map { StoredContact(it, Clock.System.now()) },
                )
            }

            val incomingFamilyMessage = com.familymessenger.contract.MessagePayload(
                id = 202,
                clientMessageUuid = "22222222-2222-2222-2222-222222222222",
                familyId = family.id,
                senderUserId = child.id,
                recipientUserId = FAMILY_GROUP_CHAT_ID,
                type = com.familymessenger.contract.MessageType.TEXT,
                body = "Сообщение в семейный чат",
                createdAt = Instant.parse("2026-03-22T20:10:00Z"),
            )
            localDatabase.update { snapshot ->
                snapshot.copy(messages = snapshot.messages + StoredMessage(incomingFamilyMessage, Clock.System.now()))
            }

            waitUntil("unread badge appears for family chat") {
                viewModel.state.value.unreadCounts[FAMILY_GROUP_CHAT_ID] == 1
            }
            assertEquals(1, viewModel.state.value.unreadCounts[FAMILY_GROUP_CHAT_ID])

            viewModel.openChat(familyContact)

            waitUntil("family chat opens") {
                viewModel.state.value.screen == Screen.CHAT &&
                    viewModel.state.value.selectedContactId == FAMILY_GROUP_CHAT_ID
            }
            waitUntil("family unread badge clears after opening chat") {
                (viewModel.state.value.unreadCounts[FAMILY_GROUP_CHAT_ID] ?: 0) == 0
            }
            assertTrue(viewModel.state.value.messages.any { it.id == incomingFamilyMessage.id })
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun unreadBadgeIgnoresMessagesFromOtherUsersSessionsThatTargetDifferentRecipient() = runBlocking {
        val mother = userProfile(id = 10, name = "Мама", role = UserRole.PARENT)
        val father = userProfile(id = 11, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 12, name = "Сын", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val fatherContact = ContactSummary(user = father, isOnline = true)
        val childContact = ContactSummary(user = child, isOnline = true)
        val apiClient = apiClient(mother, family, listOf(fatherContact, childContact))
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {
            sessionStore.save(
                StoredSession(
                    auth = AuthPayload(
                        user = mother,
                        family = family,
                        session = DeviceSession(token = "test-token"),
                    ),
                ),
            )
            localDatabase.update { snapshot ->
                snapshot.copy(
                    contacts = listOf(fatherContact, childContact).map { StoredContact(it, Clock.System.now()) },
                )
            }

            val staleDirectMessage = com.familymessenger.contract.MessagePayload(
                id = 301,
                clientMessageUuid = "33333333-3333-3333-3333-333333333333",
                familyId = family.id,
                senderUserId = father.id,
                recipientUserId = child.id,
                type = com.familymessenger.contract.MessageType.TEXT,
                body = "Сообщение сыну",
                createdAt = Instant.parse("2026-03-22T20:15:00Z"),
            )
            localDatabase.update { snapshot ->
                snapshot.copy(messages = snapshot.messages + StoredMessage(staleDirectMessage, Clock.System.now()))
            }

            waitUntil("state updated after stale message") {
                viewModel.state.value.contacts.size == 2
            }
            assertEquals(0, viewModel.state.value.unreadCounts[father.id] ?: 0)

            viewModel.openChat(fatherContact)

            waitUntil("father chat opens") {
                viewModel.state.value.screen == Screen.CHAT && viewModel.state.value.selectedContactId == father.id
            }
            assertTrue(viewModel.state.value.messages.isEmpty())
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun logoutClearsInviteCodeFromOnboardingState() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val apiClient = apiClient(parent, family, emptyList())
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {
            viewModel.updateInviteCode("OLD-INVITE-CODE")
            sessionStore.save(
                StoredSession(
                    auth = AuthPayload(
                        user = parent,
                        family = family,
                        session = DeviceSession(token = "test-token"),
                    ),
                ),
            )

            viewModel.logout()

            waitUntil("logout clears invite code") {
                !viewModel.state.value.isBusy && viewModel.state.value.onboarding.inviteCode.isEmpty()
            }
            assertEquals("", viewModel.state.value.onboarding.inviteCode)
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun syncDoesNotMarkIncomingDirectMessageReadFromSendersOwnReceipt() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Поля", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val childContact = ContactSummary(user = parent, isOnline = true)
        val incomingMessage = com.familymessenger.contract.MessagePayload(
            id = 303,
            clientMessageUuid = "33333333-3333-3333-3333-333333333333",
            familyId = family.id,
            senderUserId = parent.id,
            recipientUserId = child.id,
            type = com.familymessenger.contract.MessageType.TEXT,
            body = "Поля, привет",
            createdAt = Instant.parse("2026-03-23T18:00:00Z"),
        )
        val syncPayload = SyncPayload(
            nextSinceId = 2,
            messages = listOf(incomingMessage),
            receipts = listOf(
                com.familymessenger.contract.MessageReceiptPayload(
                    messageId = incomingMessage.id!!,
                    userId = parent.id,
                    deliveredAt = Instant.parse("2026-03-23T18:00:00Z"),
                    readAt = Instant.parse("2026-03-23T18:00:00Z"),
                ),
            ),
            events = emptyList(),
        )
        val apiClient = apiClient(child, family, listOf(childContact), syncPayload)
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)

        sessionStore.save(
            StoredSession(
                auth = AuthPayload(
                    user = child,
                    family = family,
                    session = com.familymessenger.contract.DeviceSession(token = "test-token"),
                ),
            ),
        )

        val payload = messagesRepository.fetchSync(messagesRepository.syncState())
        messagesRepository.applyTick(emptyList(), payload)

        val stored = localDatabase.snapshot().messages.single { it.payload.id == incomingMessage.id }.payload
        assertNotEquals(com.familymessenger.contract.MessageStatus.READ, stored.status)
        assertNotEquals(com.familymessenger.contract.MessageStatus.DELIVERED, stored.status)
    }

    @Test
    fun syncMarksIncomingMessagesDeliveredAfterApplyTick() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Поля", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val childContact = ContactSummary(user = child, isOnline = true)
        val incomingMessage = com.familymessenger.contract.MessagePayload(
            id = 404,
            clientMessageUuid = "44444444-4444-4444-4444-444444444444",
            familyId = family.id,
            senderUserId = child.id,
            recipientUserId = parent.id,
            type = com.familymessenger.contract.MessageType.TEXT,
            body = "Новое сообщение",
            createdAt = Instant.parse("2026-03-23T18:05:00Z"),
        )
        val requestedPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            synchronized(requestedPaths) {
                requestedPaths += request.url.encodedPath
            }
            when (request.url.encodedPath) {
                "/api/contacts" -> respondJson(ApiResponse(success = true, data = ContactsResponse(contacts = listOf(childContact))))
                "/api/messages/sync" -> respondJson(
                    ApiResponse(
                        success = true,
                        data = SyncPayload(
                            nextSinceId = 1,
                            messages = listOf(incomingMessage),
                            receipts = emptyList(),
                            events = emptyList(),
                        ),
                    ),
                )
                "/api/messages/mark-delivered" -> respondJson(ApiResponse(success = true, data = AckResponse(accepted = true)))
                else -> error("Unexpected path in test: ${request.url.encodedPath}")
            }
        }
        val httpClient = configuredHttpClient(HttpClient(engine))
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val apiClient = FamilyMessengerApiClient(
            httpClient = httpClient,
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            executor = ApiExecutor(sessionStore),
        )
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            sessionStore.save(
                StoredSession(
                    auth = AuthPayload(
                        user = parent,
                        family = family,
                        session = DeviceSession(token = "test-token"),
                    ),
                ),
            )

            syncEngine.start(scope)

            waitUntil("mark-delivered request sent from sync loop") {
                synchronized(requestedPaths) {
                    requestedPaths.contains("/api/messages/mark-delivered")
                }
            }

            val stored = localDatabase.snapshot().messages.single { it.payload.id == incomingMessage.id }.payload
            assertEquals(com.familymessenger.contract.MessageStatus.DELIVERED, stored.status)
        } finally {
            syncEngine.stop()
            scope.cancel()
        }
    }

    @Test
    fun syncResetClearsStaleLocalStateAndPerformsFullResync() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Поля", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val childContact = ContactSummary(user = child, isOnline = true)
        val incomingMessage = com.familymessenger.contract.MessagePayload(
            id = 505,
            clientMessageUuid = "55555555-5555-5555-5555-555555555555",
            familyId = family.id,
            senderUserId = child.id,
            recipientUserId = parent.id,
            type = com.familymessenger.contract.MessageType.TEXT,
            body = "После ресета",
            createdAt = Instant.parse("2026-03-23T18:10:00Z"),
        )
        var syncCalls = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/contacts" -> respondJson(ApiResponse(success = true, data = ContactsResponse(contacts = listOf(childContact))))
                "/api/messages/sync" -> {
                    syncCalls += 1
                    val sinceId = request.url.parameters["since_id"]
                    if (syncCalls == 1) {
                        respondJson(
                            ApiResponse<Unit>(
                                success = false,
                                error = com.familymessenger.contract.ApiError(
                                    code = ErrorCode.SYNC_RESET_REQUIRED,
                                    message = "reset required",
                                    details = mapOf("serverInstanceId" to "server-v2"),
                                ),
                            ),
                            status = HttpStatusCode.Conflict,
                        )
                    } else {
                        assertEquals("0", sinceId)
                        assertEquals("server-v2", request.url.parameters["server_instance_id"])
                        respondJson(
                            ApiResponse(
                                success = true,
                                data = SyncPayload(
                                    nextSinceId = 1,
                                    messages = listOf(incomingMessage),
                                    receipts = emptyList(),
                                    events = emptyList(),
                                    serverInstanceId = "server-v2",
                                ),
                            ),
                        )
                    }
                }
                "/api/messages/mark-delivered" -> respondJson(ApiResponse(success = true, data = AckResponse(accepted = true)))
                else -> error("Unexpected path in test: ${request.url.encodedPath}")
            }
        }
        val httpClient = configuredHttpClient(HttpClient(engine))
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val apiClient = FamilyMessengerApiClient(
            httpClient = httpClient,
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            executor = ApiExecutor(sessionStore),
        )
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        localDatabase.update { snapshot ->
            snapshot.copy(
                messages = snapshot.messages + StoredMessage(
                    payload = incomingMessage.copy(id = 999, body = "Старое сообщение"),
                    updatedAt = Clock.System.now(),
                ),
                syncState = app.dto.SyncState(sinceId = 77, serverInstanceId = "server-v1"),
            )
        }
        sessionStore.save(
            StoredSession(
                auth = AuthPayload(
                    user = parent,
                    family = family,
                    session = DeviceSession(token = "test-token"),
                    serverInstanceId = "server-v1",
                ),
            ),
        )

        try {
            syncEngine.start(scope)

            waitUntil("sync reset is recovered") {
                localDatabase.snapshot().messages.any { it.payload.id == incomingMessage.id } &&
                    localDatabase.snapshot().syncState.serverInstanceId == "server-v2"
            }

            val snapshot = localDatabase.snapshot()
            assertEquals(listOf(incomingMessage.id), snapshot.messages.mapNotNull { it.payload.id })
            assertEquals(1, snapshot.syncState.sinceId)
            assertEquals("server-v2", snapshot.syncState.serverInstanceId)
        } finally {
            syncEngine.stop()
            scope.cancel()
        }
    }

    @Test
    fun startupRefreshKeepsLocalSessionWhenServerIsUnavailable() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Поля", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val childContact = ContactSummary(user = child, isOnline = true)
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/setup/status" -> respondJson(ApiResponse(success = true, data = SetupStatusResponse(initialized = true)))
                "/api/profile/me" -> respondError(HttpStatusCode.InternalServerError)
                else -> error("Unexpected path in test: ${request.url.encodedPath}")
            }
        }
        val apiClient = FamilyMessengerApiClient(
            httpClient = configuredHttpClient(HttpClient(engine)),
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            executor = ApiExecutor(sessionStore),
        )
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        sessionStore.save(
            StoredSession(
                auth = AuthPayload(
                    user = parent,
                    family = family,
                    session = DeviceSession(token = "test-token"),
                ),
            ),
        )
        localDatabase.update { snapshot ->
            snapshot.copy(contacts = listOf(childContact).map { StoredContact(it, Clock.System.now()) })
        }
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {

            waitUntil("startup keeps user on authorized screen") {
                viewModel.state.value.screen == Screen.CONTACTS &&
                    viewModel.state.value.currentUser?.id == parent.id &&
                    viewModel.state.value.errorMessage == "Сервер недоступен. Используется локальная сессия."
            }

            assertEquals(parent.id, sessionStore.currentSession()?.auth?.user?.id)
            assertEquals(Screen.CONTACTS, viewModel.state.value.screen)
            assertEquals(1, viewModel.state.value.contacts.size)
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun startupRefreshLogsOutWhenSessionIsUnauthorized() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/setup/status" -> respondJson(ApiResponse(success = true, data = SetupStatusResponse(initialized = true)))
                "/api/profile/me" -> respondJson(
                    ApiResponse<Unit>(
                        success = false,
                        error = com.familymessenger.contract.ApiError(
                            code = ErrorCode.UNAUTHORIZED,
                            message = "token expired",
                        ),
                    ),
                    status = HttpStatusCode.Unauthorized,
                )
                else -> error("Unexpected path in test: ${request.url.encodedPath}")
            }
        }
        val apiClient = FamilyMessengerApiClient(
            httpClient = configuredHttpClient(HttpClient(engine)),
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            executor = ApiExecutor(sessionStore),
        )
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        sessionStore.save(
            StoredSession(
                auth = AuthPayload(
                    user = parent,
                    family = family,
                    session = DeviceSession(token = "test-token"),
                ),
            ),
        )
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {

            waitUntil("startup logs out unauthorized session") {
                viewModel.state.value.screen == Screen.ONBOARDING &&
                    sessionStore.currentSession() == null &&
                    viewModel.state.value.errorMessage == "Сессия истекла. Войдите заново."
            }

            assertEquals(null, sessionStore.currentSession())
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun loginLoadsContactsBeforeLongPollSyncCompletes() = runBlocking {
        val parent = userProfile(id = 1, name = "Папа", role = UserRole.PARENT)
        val child = userProfile(id = 3, name = "Поля", role = UserRole.CHILD)
        val family = FamilySummary(id = 1, name = "Ивановы")
        val childContact = ContactSummary(user = child, isOnline = true)
        val syncGate = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/setup/status" -> respondJson(ApiResponse(success = true, data = SetupStatusResponse(initialized = true)))
                "/api/auth/login" -> respondJson(
                    ApiResponse(
                        success = true,
                        data = AuthPayload(
                            user = parent,
                            family = family,
                            session = DeviceSession(token = "test-token"),
                        ),
                    ),
                )
                "/api/contacts" -> respondJson(ApiResponse(success = true, data = ContactsResponse(contacts = listOf(childContact))))
                "/api/messages/sync" -> {
                    syncGate.await()
                    respondJson(ApiResponse(success = true, data = SyncPayload(0, emptyList(), emptyList(), emptyList())))
                }
                "/api/messages/mark-delivered" -> respondJson(ApiResponse(success = true, data = AckResponse(accepted = true)))
                else -> error("Unexpected path in test: ${request.url.encodedPath}")
            }
        }
        val httpClient = configuredHttpClient(HttpClient(engine))
        val localDatabase = LocalDatabase(InMemoryKeyValueStore(), json)
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(localDatabase, platformInfo())
        val apiClient = FamilyMessengerApiClient(
            httpClient = httpClient,
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            executor = ApiExecutor(sessionStore),
        )
        val contactsRepository = ContactsRepository(apiClient, localDatabase)
        val messagesRepository = MessagesRepository(apiClient, localDatabase, sessionStore)
        val sessionRepository = SessionRepository(apiClient, sessionStore, localDatabase, platformInfo())
        val setupRepository = SetupRepository(apiClient)
        val adminRepository = AdminRepository(apiClient)
        val syncEngine = SyncEngine(
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            presenceRepository = PresenceRepository(apiClient, NoOpGeolocationService),
            deviceRepository = DeviceRepository(apiClient),
            notificationService = NoOpNotificationService,
        )
        val viewModel = AppViewModel(
            platformInfo = platformInfo(),
            localDatabase = localDatabase,
            settingsRepository = settingsRepository,
            sessionStore = sessionStore,
            contactsRepository = contactsRepository,
            messagesRepository = messagesRepository,
            sessionRepository = sessionRepository,
            syncEngine = syncEngine,
            login = LoginUseCase(sessionRepository),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            verifyAdminAccess = VerifyAdminAccessUseCase(adminRepository),
            createMember = CreateMemberUseCase(adminRepository),
            removeMember = RemoveMemberUseCase(adminRepository),
            loadContacts = LoadContactsUseCase(contactsRepository),
            sendTextMessageUseCase = SendTextMessageUseCase(messagesRepository),
            sendQuickActionUseCase = SendQuickActionUseCase(messagesRepository),
            shareLocationUseCase = ShareLocationUseCase(PresenceRepository(apiClient, NoOpGeolocationService), messagesRepository),
        )

        try {
            viewModel.updateInviteCode("PARENT-DEMO")
            viewModel.submitAuth()

            waitUntil("contacts appear before sync returns") {
                viewModel.state.value.screen == Screen.CONTACTS &&
                    viewModel.state.value.contacts.map { it.user.id } == listOf(child.id)
            }

            assertEquals(listOf(child.id), localDatabase.snapshot().contacts.map { it.contact.user.id })
        } finally {
            syncGate.complete(Unit)
            viewModel.close()
        }
    }

    private fun apiClient(
        currentUser: UserProfile,
        family: FamilySummary,
        contacts: List<ContactSummary>,
        syncPayload: SyncPayload = com.familymessenger.contract.SyncPayload(0, emptyList(), emptyList(), emptyList()),
    ): FamilyMessengerApiClient {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/setup/status" -> respondJson(ApiResponse(success = true, data = SetupStatusResponse(initialized = true)))
                "/api/messages/mark-delivered" -> respondJson(ApiResponse(success = true, data = AckResponse(accepted = true)))
                "/api/messages/mark-read" -> respondJson(ApiResponse(success = true, data = AckResponse(accepted = true)))
                "/api/messages/sync" -> respondJson(ApiResponse(success = true, data = syncPayload))
                "/api/profile/me" -> respondJson(ApiResponse(success = true, data = ProfileResponse(user = currentUser, family = family)))
                "/api/contacts" -> respondJson(ApiResponse(success = true, data = ContactsResponse(contacts = contacts)))
                else -> error("Unexpected path in test: ${request.url.encodedPath}")
            }
        }
        val httpClient = configuredHttpClient(HttpClient(engine))
        val sessionStore = SessionStore(InMemoryKeyValueStore(), json)
        val settingsRepository = ClientSettingsRepository(LocalDatabase(InMemoryKeyValueStore(), json), platformInfo())
        return FamilyMessengerApiClient(
            httpClient = httpClient,
            sessionStore = sessionStore,
            settingsRepository = settingsRepository,
            executor = ApiExecutor(sessionStore),
        )
    }

    private suspend fun waitUntil(description: String, timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(20)
        }
        error("Timed out waiting for: $description")
    }

    private fun platformInfo() = PlatformInfo(
        type = PlatformType.DESKTOP,
        displayName = "Desktop Test",
        defaultBaseUrl = "http://localhost:8080",
    )

    private fun userProfile(id: Long, name: String, role: UserRole) = UserProfile(
        id = id,
        familyId = 1,
        displayName = name,
        role = role,
        isAdmin = role == UserRole.PARENT,
        lastSeenAt = null,
    )

    private inline fun <reified T> MockRequestHandleScope.respondJson(
        body: ApiResponse<T>,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = json.encodeToString(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}

private class InMemoryKeyValueStore : KeyValueStore {
    private val values = linkedMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

private object NoOpGeolocationService : GeolocationService {
    override suspend fun currentLocation() = null
}

private object NoOpNotificationService : NotificationService {
    override fun notify(title: String, body: String) = Unit
}
