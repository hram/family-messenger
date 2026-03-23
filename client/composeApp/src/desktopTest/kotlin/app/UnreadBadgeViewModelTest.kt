package app

import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.ContactsResponse
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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
            login = LoginUseCase(sessionRepository, syncEngine),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            bootstrapSystem = BootstrapSystemUseCase(setupRepository),
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
            login = LoginUseCase(sessionRepository, syncEngine),
            loadSetupStatus = LoadSetupStatusUseCase(setupRepository),
            bootstrapSystem = BootstrapSystemUseCase(setupRepository),
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

        messagesRepository.sync()

        val stored = localDatabase.snapshot().messages.single { it.payload.id == incomingMessage.id }.payload
        assertNotEquals(com.familymessenger.contract.MessageStatus.READ, stored.status)
        assertNotEquals(com.familymessenger.contract.MessageStatus.DELIVERED, stored.status)
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

    private inline fun <reified T> MockRequestHandleScope.respondJson(body: ApiResponse<T>) = respond(
        content = json.encodeToString(body),
        status = HttpStatusCode.OK,
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
