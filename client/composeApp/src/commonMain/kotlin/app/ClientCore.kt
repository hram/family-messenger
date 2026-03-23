package app

import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.AdminCreateMemberRequest
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.AdminRemoveMemberRequest
import com.familymessenger.contract.ApiError
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.ErrorCode
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.HealthResponse
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.MarkDeliveredRequest
import com.familymessenger.contract.MarkReadRequest
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageReceiptPayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.PresencePingRequest
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SendMessageResponse
import com.familymessenger.contract.ShareLocationRequest
import com.familymessenger.contract.SetupBootstrapRequest
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupMemberDraft
import com.familymessenger.contract.SetupStatusResponse
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.SystemEventPayload
import com.familymessenger.contract.UpdatePushTokenRequest
import com.familymessenger.contract.UserRole
import com.familymessenger.contract.VerifyAdminAccessRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.koinApplication

private const val LOCAL_DB_KEY = "client.local.db.v1"
private const val SESSION_KEY = "client.session.v1"
private const val LOG_TAG_API = "FamilyMessengerApi"

private val clientJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

sealed class AppException(message: String) : RuntimeException(message) {
    class Network(message: String) : AppException(message)

    class Unauthorized(message: String) : AppException(message)

    class Validation(message: String) : AppException(message)

    class Conflict(message: String) : AppException(message)

    class RateLimited(message: String) : AppException(message)

    class Server(message: String) : AppException(message)
}

@Serializable
data class StoredContact(
    val contact: com.familymessenger.contract.ContactSummary,
    val cachedAt: Instant,
)

@Serializable
data class StoredMessage(
    val payload: MessagePayload,
    val updatedAt: Instant,
)

@Serializable
data class PendingMessage(
    val request: SendMessageRequest,
    val createdAt: Instant,
)

@Serializable
data class SyncState(
    val sinceId: Long = 0,
)

@Serializable
data class LocalSettings(
    val serverBaseUrl: String = "",
    val pollingEnabled: Boolean = true,
    val pushEnabled: Boolean = false,
)

@Serializable
data class LocalDatabaseSnapshot(
    val contacts: List<StoredContact> = emptyList(),
    val messages: List<StoredMessage> = emptyList(),
    val pendingMessages: List<PendingMessage> = emptyList(),
    val syncState: SyncState = SyncState(),
    val lastReadAtByChat: Map<Long, Instant> = emptyMap(),
    val settings: LocalSettings = LocalSettings(),
)

@Serializable
data class StoredSession(
    val auth: AuthPayload,
)

class LocalDatabase(
    private val settingsStore: KeyValueStore,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(loadInitial())

    val snapshots: StateFlow<LocalDatabaseSnapshot> = state.asStateFlow()

    suspend fun snapshot(): LocalDatabaseSnapshot = state.value

    suspend fun update(transform: (LocalDatabaseSnapshot) -> LocalDatabaseSnapshot): LocalDatabaseSnapshot =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            persist(updated)
            updated
        }

    private fun loadInitial(): LocalDatabaseSnapshot =
        settingsStore.getString(LOCAL_DB_KEY)
            ?.let { runCatching { json.decodeFromString<LocalDatabaseSnapshot>(it) }.getOrNull() }
            ?: LocalDatabaseSnapshot()

    private fun persist(snapshot: LocalDatabaseSnapshot) {
        settingsStore.putString(LOCAL_DB_KEY, json.encodeToString(LocalDatabaseSnapshot.serializer(), snapshot))
    }
}

class SessionStore(
    private val secureStore: KeyValueStore,
    private val json: Json,
) {
    private val state = MutableStateFlow(loadInitial())

    val session: StateFlow<StoredSession?> = state.asStateFlow()

    fun currentSession(): StoredSession? = state.value

    fun currentToken(): String? = state.value?.auth?.session?.token

    fun save(session: StoredSession) {
        state.value = session
        secureStore.putString(SESSION_KEY, json.encodeToString(StoredSession.serializer(), session))
    }

    fun clear() {
        state.value = null
        secureStore.remove(SESSION_KEY)
    }

    private fun loadInitial(): StoredSession? =
        secureStore.getString(SESSION_KEY)
            ?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() }
}

class ClientSettingsRepository(
    private val database: LocalDatabase,
    private val platformInfo: PlatformInfo,
) {
    suspend fun settings(): LocalSettings = database.snapshot().settings.normalized(platformInfo)

    suspend fun updateServerBaseUrl(baseUrl: String) {
        database.update { snapshot ->
            snapshot.copy(settings = snapshot.settings.copy(serverBaseUrl = normalizeBaseUrl(baseUrl)))
        }
    }

    suspend fun updatePollingEnabled(enabled: Boolean) {
        database.update { snapshot ->
            snapshot.copy(settings = snapshot.settings.copy(pollingEnabled = enabled))
        }
    }

    suspend fun updatePushEnabled(enabled: Boolean) {
        database.update { snapshot ->
            snapshot.copy(settings = snapshot.settings.copy(pushEnabled = enabled))
        }
    }

    private fun LocalSettings.normalized(platformInfo: PlatformInfo): LocalSettings {
        val baseUrl = serverBaseUrl.ifBlank { platformInfo.defaultBaseUrl }
        return copy(serverBaseUrl = normalizeBaseUrl(baseUrl))
    }
}

class ApiExecutor(
    private val sessionStore: SessionStore,
) {
    suspend fun <T> execute(block: suspend () -> ApiResponse<T>): T {
        var attempt = 0
        var lastNetworkException: Throwable? = null
        while (attempt < 3) {
            attempt += 1
            try {
                val response = block()
                val data = response.data
                if (response.success && data != null) {
                    platformLogInfo(LOG_TAG_API, "Request completed successfully on attempt=$attempt")
                    return data
                }
                platformLogError(
                    LOG_TAG_API,
                    "API returned error response on attempt=$attempt code=${response.error?.code} message=${response.error?.message}",
                )
                throw mapError(response.error)
            } catch (error: HttpRequestTimeoutException) {
                lastNetworkException = error
                platformLogError(LOG_TAG_API, "HTTP timeout on attempt=$attempt", error)
            } catch (error: AppException) {
                platformLogError(LOG_TAG_API, "Application error on attempt=$attempt: ${error.message}", error)
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastNetworkException = error
                platformLogError(LOG_TAG_API, "Network failure on attempt=$attempt: ${error.message}", error)
            }
            delay(attempt * 300L)
        }
        throw AppException.Network(lastNetworkException?.message ?: "Network request failed")
    }

    private fun mapError(error: ApiError?): AppException {
        val message = error?.message ?: "Unknown server error"
        return when (error?.code) {
            ErrorCode.UNAUTHORIZED -> {
                sessionStore.clear()
                AppException.Unauthorized(message)
            }

            ErrorCode.INVALID_REQUEST -> AppException.Validation(message)
            ErrorCode.CONFLICT -> AppException.Conflict(message)
            ErrorCode.RATE_LIMITED -> AppException.RateLimited(message)
            ErrorCode.FORBIDDEN,
            ErrorCode.NOT_FOUND,
            ErrorCode.INTERNAL_ERROR,
            null,
            -> AppException.Server(message)
        }
    }
}

class FamilyMessengerApiClient(
    private val httpClient: HttpClient,
    private val sessionStore: SessionStore,
    private val settingsRepository: ClientSettingsRepository,
    private val executor: ApiExecutor,
) {
    suspend fun login(request: LoginRequest): AuthPayload =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/auth/login platform=${request.platform}")
            httpClient.post(url("/api/auth/login")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun setupStatus(): SetupStatusResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "GET /api/setup/status")
            httpClient.get(url("/api/setup/status")).body()
        }

    suspend fun bootstrap(request: SetupBootstrapRequest): SetupBootstrapResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/setup/bootstrap members=${request.members.size}")
            httpClient.post(url("/api/setup/bootstrap")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun verifyAdminAccess(masterPassword: String): AdminMembersResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/admin/verify")
            httpClient.post(url("/api/admin/verify")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(VerifyAdminAccessRequest(masterPassword))
            }.body()
        }

    suspend fun createMember(
        masterPassword: String,
        displayName: String,
        role: UserRole,
        isAdmin: Boolean,
    ): AdminCreateMemberResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/admin/members/create role=${role.name}")
            httpClient.post(url("/api/admin/members/create")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(
                    AdminCreateMemberRequest(
                        masterPassword = masterPassword,
                        displayName = displayName,
                        role = role,
                        isAdmin = isAdmin,
                    ),
                )
            }.body()
        }

    suspend fun removeMember(masterPassword: String, inviteCode: String): AdminMembersResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/admin/members/remove invite=$inviteCode")
            httpClient.post(url("/api/admin/members/remove")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(AdminRemoveMemberRequest(masterPassword = masterPassword, inviteCode = inviteCode))
            }.body()
        }

    suspend fun profile(): ProfileResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "GET /api/profile/me")
            httpClient.get(url("/api/profile/me")) {
                authHeader()
            }.body()
        }

    suspend fun contacts(): ContactsResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "GET /api/contacts")
            httpClient.get(url("/api/contacts")) {
                authHeader()
            }.body()
        }

    suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse =
        executor.execute {
            httpClient.post(url("/api/messages/send")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun sync(sinceId: Long): SyncPayload =
        executor.execute {
            httpClient.get(url("/api/messages/sync")) {
                authHeader()
                url {
                    parameters.append("since_id", sinceId.toString())
                }
            }.body()
        }

    suspend fun markDelivered(messageIds: List<Long>): AckResponse =
        executor.execute {
            httpClient.post(url("/api/messages/mark-delivered")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(MarkDeliveredRequest(messageIds))
            }.body()
        }

    suspend fun markRead(messageIds: List<Long>): AckResponse =
        executor.execute {
            httpClient.post(url("/api/messages/mark-read")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(MarkReadRequest(messageIds))
            }.body()
        }

    suspend fun shareLocation(location: LocationPayload): AckResponse =
        executor.execute {
            httpClient.post(url("/api/location/share")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(
                    ShareLocationRequest(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        label = location.label,
                    ),
                )
            }.body()
        }

    suspend fun ping(): AckResponse =
        executor.execute {
            httpClient.post(url("/api/presence/ping")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(PresencePingRequest())
            }.body()
        }

    suspend fun updatePushToken(pushToken: String?): AckResponse =
        executor.execute {
            httpClient.post(url("/api/device/update-push-token")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(UpdatePushTokenRequest(pushToken))
            }.body()
        }

    suspend fun health(): HealthResponse =
        executor.execute {
            httpClient.get(url("/api/health")).body()
        }

    private suspend fun url(path: String): String = settingsRepository.settings().serverBaseUrl + path

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        sessionStore.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }
}

class SessionRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val sessionStore: SessionStore,
    private val localDatabase: LocalDatabase,
    private val platformInfo: PlatformInfo,
) {
    suspend fun restore(): StoredSession? = sessionStore.currentSession()

    suspend fun refreshSessionFromServer(): StoredSession {
        val existing = sessionStore.currentSession() ?: throw AppException.Unauthorized("Please authenticate first")
        val profile = apiClient.profile()
        val refreshed = StoredSession(
            auth = AuthPayload(
                user = profile.user,
                family = profile.family,
                session = existing.auth.session,
            ),
        )
        sessionStore.save(refreshed)
        return refreshed
    }

    suspend fun login(inviteCode: String): StoredSession {
        val auth = apiClient.login(
            LoginRequest(
                inviteCode = inviteCode.trim(),
                platform = platformInfo.type,
            ),
        )
        return persistAuth(auth)
    }

    suspend fun logout() {
        sessionStore.clear()
        localDatabase.update {
            it.copy(
                contacts = emptyList(),
                messages = emptyList(),
                pendingMessages = emptyList(),
                syncState = SyncState(),
            )
        }
    }

    private suspend fun persistAuth(auth: AuthPayload): StoredSession {
        val session = StoredSession(auth)
        sessionStore.save(session)
        return session
    }
}

class SetupRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun status(): SetupStatusResponse = apiClient.setupStatus()

    suspend fun bootstrap(
        masterPassword: String,
        familyName: String,
        members: List<SetupMemberInputState>,
    ): SetupBootstrapResponse = apiClient.bootstrap(
        SetupBootstrapRequest(
            masterPassword = masterPassword,
            familyName = familyName,
            members = members.map {
                SetupMemberDraft(
                    displayName = it.displayName.trim(),
                    role = it.role,
                    isAdmin = it.isAdmin,
                )
            },
        ),
    )
}

class AdminRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun verifyAccess(masterPassword: String): AdminMembersResponse =
        apiClient.verifyAdminAccess(masterPassword)

    suspend fun createMember(masterPassword: String, displayName: String, role: UserRole, isAdmin: Boolean): AdminCreateMemberResponse =
        apiClient.createMember(masterPassword, displayName, role, isAdmin)

    suspend fun removeMember(masterPassword: String, inviteCode: String): AdminMembersResponse =
        apiClient.removeMember(masterPassword, inviteCode)
}

class ContactsRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val localDatabase: LocalDatabase,
) {
    suspend fun cachedContacts(): List<com.familymessenger.contract.ContactSummary> =
        localDatabase.snapshot().contacts.map { it.contact }

    suspend fun refreshContacts(): List<com.familymessenger.contract.ContactSummary> {
        val contacts = apiClient.contacts().contacts
        localDatabase.update { snapshot ->
            snapshot.copy(
                contacts = contacts.map { StoredContact(it, Clock.System.now()) },
            )
        }
        return contacts
    }
}

class MessagesRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val localDatabase: LocalDatabase,
    private val sessionStore: SessionStore,
) {
    private val readInFlightMutex = Mutex()
    private val readInFlight = mutableSetOf<Long>()

    suspend fun conversation(contactId: Long): List<MessagePayload> {
        val currentUserId = sessionStore.currentSession()?.auth?.user?.id ?: return emptyList()
        return localDatabase.snapshot().messages
            .map { it.payload }
            .filter { payload ->
                (contactId == FAMILY_GROUP_CHAT_ID && payload.recipientUserId == FAMILY_GROUP_CHAT_ID) ||
                    (payload.senderUserId == currentUserId && payload.recipientUserId == contactId) ||
                    (payload.senderUserId == contactId && payload.recipientUserId == currentUserId)
            }
            .sortedBy { it.createdAt?.toEpochMilliseconds() ?: 0L }
    }

    suspend fun queueTextMessage(contactId: Long, body: String): MessagePayload =
        queueMessage(
            SendMessageRequest(
                recipientUserId = contactId,
                clientMessageUuid = randomUuid(),
                type = MessageType.TEXT,
                body = body.trim(),
            ),
        )

    suspend fun queueQuickAction(contactId: Long, code: QuickActionCode): MessagePayload =
        queueMessage(
            SendMessageRequest(
                recipientUserId = contactId,
                clientMessageUuid = randomUuid(),
                type = MessageType.QUICK_ACTION,
                quickActionCode = code,
            ),
        )

    suspend fun queueLocationMessage(contactId: Long, location: LocationPayload): MessagePayload =
        queueMessage(
            SendMessageRequest(
                recipientUserId = contactId,
                clientMessageUuid = randomUuid(),
                type = MessageType.LOCATION,
                location = location,
            ),
        )

    suspend fun flushPendingMessages(): Int {
        val pending = localDatabase.snapshot().pendingMessages
        var flushed = 0
        pending.forEach { item ->
            val response = runCatching { apiClient.sendMessage(item.request).message }.getOrElse { return@forEach }
            flushed += 1
            localDatabase.update { snapshot ->
                val messages = snapshot.messages.upsert(response)
                snapshot.copy(
                    messages = messages,
                    pendingMessages = snapshot.pendingMessages.filterNot { it.request.clientMessageUuid == item.request.clientMessageUuid },
                )
            }
        }
        return flushed
    }

    suspend fun markConversationDelivered(contactId: Long) {
        val currentUserId = sessionStore.currentSession()?.auth?.user?.id ?: return
        val messageIds = localDatabase.snapshot().messages
            .map { it.payload }
            .filter {
                if (contactId == FAMILY_GROUP_CHAT_ID) {
                    it.senderUserId != currentUserId && it.recipientUserId == FAMILY_GROUP_CHAT_ID
                } else {
                    it.senderUserId == contactId && it.recipientUserId == currentUserId
                }
            }
            .mapNotNull { it.id }
            .take(200)
        if (messageIds.isEmpty()) return
        apiClient.markDelivered(messageIds)
        localDatabase.update { snapshot ->
            snapshot.copy(messages = snapshot.messages.advanceStatuses(messageIds, MessageStatus.DELIVERED))
        }
    }

    suspend fun markConversationRead(contactId: Long) {
        if (!startRead(contactId)) return
        try {
            val currentUserId = sessionStore.currentSession()?.auth?.user?.id ?: return
            val snapshot = localDatabase.snapshot()
            val incomingMessages = snapshot.messages
                .map { it.payload }
                .filter {
                    if (contactId == FAMILY_GROUP_CHAT_ID) {
                        it.senderUserId != currentUserId && it.recipientUserId == FAMILY_GROUP_CHAT_ID
                    } else {
                        it.senderUserId == contactId && it.recipientUserId == currentUserId
                    }
                }
            val messageIds = incomingMessages
                .mapNotNull { it.id }
                .take(200)
            if (messageIds.isNotEmpty()) {
                apiClient.markRead(messageIds)
            }
            val lastReadAt = incomingMessages.maxOfOrNull { it.createdAt ?: Clock.System.now() } ?: Clock.System.now()
            localDatabase.update { snapshot ->
                snapshot.copy(messages = snapshot.messages.advanceStatuses(messageIds, MessageStatus.READ))
                    .copy(lastReadAtByChat = snapshot.lastReadAtByChat + (contactId to lastReadAt))
            }
        } finally {
            finishRead(contactId)
        }
    }

    private suspend fun startRead(contactId: Long): Boolean =
        readInFlightMutex.withLock {
            if (contactId in readInFlight) {
                false
            } else {
                readInFlight += contactId
                true
            }
        }

    private suspend fun finishRead(contactId: Long) {
        readInFlightMutex.withLock {
            readInFlight -= contactId
        }
    }

    suspend fun sync(): SyncPayload {
        val sinceId = localDatabase.snapshot().syncState.sinceId
        val payload = apiClient.sync(sinceId)
        val currentUserId = sessionStore.currentSession()?.auth?.user?.id
        localDatabase.update { snapshot ->
            val merged = snapshot.messages
                .mergePayloads(payload.messages)
                .applyReceipts(payload.receipts, currentUserId)
                .appendEvents(payload.events, sessionStore.currentSession()?.auth?.user?.id)
            snapshot.copy(
                messages = merged,
                syncState = SyncState(payload.nextSinceId),
            )
        }
        return payload
    }

    suspend fun pendingCount(): Int = localDatabase.snapshot().pendingMessages.size

    suspend fun syncCursor(): Long = localDatabase.snapshot().syncState.sinceId

    private suspend fun queueMessage(request: SendMessageRequest): MessagePayload {
        val session = sessionStore.currentSession() ?: throw AppException.Unauthorized("Please authenticate first")
        val now = Clock.System.now()
        val localPayload = MessagePayload(
            id = null,
            clientMessageUuid = request.clientMessageUuid,
            familyId = session.auth.user.familyId,
            senderUserId = session.auth.user.id,
            recipientUserId = request.recipientUserId,
            type = request.type,
            body = request.body,
            quickActionCode = request.quickActionCode,
            location = request.location,
            status = MessageStatus.LOCAL_PENDING,
            createdAt = now,
        )
        localDatabase.update { snapshot ->
            snapshot.copy(
                messages = snapshot.messages.upsert(localPayload),
                pendingMessages = snapshot.pendingMessages + PendingMessage(request, now),
            )
        }
        runCatching { flushPendingMessages() }
        return localPayload
    }
}

class PresenceRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val geolocationService: GeolocationService,
) {
    suspend fun ping() {
        apiClient.ping()
    }

    suspend fun currentLocation(): LocationPayload? = geolocationService.currentLocation()

    suspend fun recordLocationEvent(location: LocationPayload) {
        apiClient.shareLocation(location)
    }
}

class DeviceRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun updatePushToken(pushToken: String?) {
        apiClient.updatePushToken(pushToken)
    }
}

class LoginUseCase(
    private val sessionRepository: SessionRepository,
    private val syncEngine: SyncEngine,
) {
    suspend operator fun invoke(inviteCode: String): StoredSession {
        val session = sessionRepository.login(inviteCode)
        syncEngine.kick()
        return session
    }
}

class LoadSetupStatusUseCase(
    private val setupRepository: SetupRepository,
) {
    suspend operator fun invoke(): SetupStatusResponse = setupRepository.status()
}

class BootstrapSystemUseCase(
    private val setupRepository: SetupRepository,
) {
    suspend operator fun invoke(
        masterPassword: String,
        familyName: String,
        members: List<SetupMemberInputState>,
    ): SetupBootstrapResponse = setupRepository.bootstrap(masterPassword, familyName, members)
}

class VerifyAdminAccessUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(masterPassword: String): AdminMembersResponse =
        adminRepository.verifyAccess(masterPassword)
}

class CreateMemberUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(masterPassword: String, displayName: String, role: UserRole, isAdmin: Boolean): AdminCreateMemberResponse =
        adminRepository.createMember(masterPassword, displayName, role, isAdmin)
}

class RemoveMemberUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(masterPassword: String, inviteCode: String): AdminMembersResponse =
        adminRepository.removeMember(masterPassword, inviteCode)
}

class LoadContactsUseCase(
    private val contactsRepository: ContactsRepository,
) {
    suspend operator fun invoke(): List<com.familymessenger.contract.ContactSummary> =
        runCatching { contactsRepository.refreshContacts() }
            .getOrElse { contactsRepository.cachedContacts() }
}

class SendTextMessageUseCase(
    private val messagesRepository: MessagesRepository,
) {
    suspend operator fun invoke(contactId: Long, body: String): MessagePayload =
        messagesRepository.queueTextMessage(contactId, body)
}

class SendQuickActionUseCase(
    private val messagesRepository: MessagesRepository,
) {
    suspend operator fun invoke(contactId: Long, code: QuickActionCode): MessagePayload =
        messagesRepository.queueQuickAction(contactId, code)
}

class ShareLocationUseCase(
    private val presenceRepository: PresenceRepository,
    private val messagesRepository: MessagesRepository,
) {
    suspend operator fun invoke(contactId: Long): Boolean {
        val location = presenceRepository.currentLocation() ?: return false
        messagesRepository.queueLocationMessage(contactId, location)
        runCatching { presenceRepository.recordLocationEvent(location) }
        messagesRepository.sync()
        return true
    }
}

class SyncEngine(
    private val sessionStore: SessionStore,
    private val settingsRepository: ClientSettingsRepository,
    private val contactsRepository: ContactsRepository,
    private val messagesRepository: MessagesRepository,
    private val presenceRepository: PresenceRepository,
    private val deviceRepository: DeviceRepository,
    private val notificationService: NotificationService,
) {
    private var pollingJob: Job? = null
    private var cycles = 0

    fun start(scope: CoroutineScope) {
        if (pollingJob != null) return
        pollingJob = scope.launchLoop()
    }

    suspend fun kick() {
        syncOnce()
    }

    suspend fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun CoroutineScope.launchLoop(): Job = launch {
        while (true) {
            runCatching { syncOnce() }
            delay(4_000)
        }
    }

    private suspend fun syncOnce() {
        if (sessionStore.currentSession() == null) return
        val settings = settingsRepository.settings()
        if (!settings.pollingEnabled) return

        contactsRepository.refreshContacts()
        messagesRepository.flushPendingMessages()
        val payload = messagesRepository.sync()
        cycles += 1

        if (payload.messages.isNotEmpty()) {
            notificationService.notify("Family Messenger", "Получено ${payload.messages.size} новых сообщений")
        }

        if (cycles % 3 == 0) {
            presenceRepository.ping()
        }

        if (settings.pushEnabled) {
            deviceRepository.updatePushToken("optional-local-placeholder")
        }
    }
}

private fun List<StoredMessage>.upsert(payload: MessagePayload): List<StoredMessage> {
    val now = Clock.System.now()
    val existing = firstOrNull {
        (payload.id != null && it.payload.id == payload.id) || it.payload.clientMessageUuid == payload.clientMessageUuid
    }
    val merged = existing?.payload?.mergeWith(payload) ?: payload
    val filtered = filterNot {
        (payload.id != null && it.payload.id == payload.id) || it.payload.clientMessageUuid == payload.clientMessageUuid
    }
    return (filtered + StoredMessage(merged, now)).sortedBy { it.payload.createdAt?.toEpochMilliseconds() ?: 0L }
}

private fun List<StoredMessage>.mergePayloads(messages: List<MessagePayload>): List<StoredMessage> =
    messages.fold(this) { acc, payload -> acc.upsert(payload) }

private fun List<StoredMessage>.applyReceipts(
    receipts: List<MessageReceiptPayload>,
    currentUserId: Long?,
): List<StoredMessage> {
    if (currentUserId == null) return this
    var current = this
    receipts.forEach { receipt ->
        val message = current.firstOrNull { it.payload.id == receipt.messageId }?.payload ?: return@forEach
        if (!message.shouldApplyReceipt(receipt, currentUserId)) {
            return@forEach
        }
        val targetStatus = when {
            receipt.readAt != null -> MessageStatus.READ
            receipt.deliveredAt != null -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        }
        current = current.advanceStatuses(listOf(receipt.messageId), targetStatus)
    }
    return current
}

private fun MessagePayload.shouldApplyReceipt(
    receipt: MessageReceiptPayload,
    currentUserId: Long,
): Boolean = when {
    senderUserId == currentUserId -> {
        if (recipientUserId == FAMILY_GROUP_CHAT_ID) {
            receipt.userId != currentUserId
        } else {
            receipt.userId == recipientUserId
        }
    }
    recipientUserId == currentUserId -> receipt.userId == currentUserId
    recipientUserId == FAMILY_GROUP_CHAT_ID -> receipt.userId == currentUserId
    else -> false
}

private fun List<StoredMessage>.appendEvents(
    events: List<SystemEventPayload>,
    currentUserId: Long?,
): List<StoredMessage> {
    if (currentUserId == null) return this
    return events.fold(this) { acc, event ->
        val synthetic = MessagePayload(
            id = null,
            clientMessageUuid = "event-${event.createdAt.toEpochMilliseconds()}-${event.type}",
            familyId = 0,
            senderUserId = currentUserId,
            recipientUserId = currentUserId,
            type = MessageType.TEXT,
            body = event.message,
            status = MessageStatus.DELIVERED,
            createdAt = event.createdAt,
        )
        acc.upsert(synthetic)
    }
}

private fun List<StoredMessage>.advanceStatuses(messageIds: List<Long>, target: MessageStatus): List<StoredMessage> =
    map { item ->
        if (item.payload.id in messageIds) {
            item.copy(payload = item.payload.copy(status = advanceStatus(item.payload.status, target)))
        } else {
            item
        }
    }

private fun MessagePayload.mergeWith(other: MessagePayload): MessagePayload =
    copy(
        id = other.id ?: id,
        familyId = if (familyId == 0L) other.familyId else familyId,
        senderUserId = if (senderUserId == 0L) other.senderUserId else senderUserId,
        recipientUserId = if (recipientUserId == 0L) other.recipientUserId else recipientUserId,
        body = other.body ?: body,
        quickActionCode = other.quickActionCode ?: quickActionCode,
        location = other.location ?: location,
        status = advanceStatus(status, other.status),
        createdAt = other.createdAt ?: createdAt,
    )

private fun advanceStatus(current: MessageStatus, candidate: MessageStatus): MessageStatus {
    val rank = mapOf(
        MessageStatus.LOCAL_PENDING to 0,
        MessageStatus.SENT to 1,
        MessageStatus.DELIVERED to 2,
        MessageStatus.READ to 3,
    )
    return if (rank.getValue(candidate) > rank.getValue(current)) candidate else current
}

private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')

fun configuredHttpClient(base: HttpClient): HttpClient = base.config {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }
    install(ContentNegotiation) {
        json(clientJson)
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}

class ClientApp private constructor(
    private val koinHandle: org.koin.core.KoinApplication,
) {
    val viewModel: AppViewModel = koinHandle.koin.get()

    fun close() {
        viewModel.close()
        koinHandle.close()
    }

    companion object {
        fun create(platformServices: PlatformServices): ClientApp {
            val app = koinApplication {
                modules(commonClientModule(platformServices))
            }
            return ClientApp(app)
        }
    }
}

private fun commonClientModule(platformServices: PlatformServices): Module = module {
    single { clientJson }
    single { platformServices.platformInfo }
    single { configuredHttpClient(platformServices.httpClient) }
    single { platformServices.settingsStore }
    single(qualifier = org.koin.core.qualifier.named("secure")) { platformServices.secureStore }
    single { platformServices.geolocationService }
    single { platformServices.notificationService }
    single { LocalDatabase(get(), get()) }
    single { SessionStore(get(org.koin.core.qualifier.named("secure")), get()) }
    single { ClientSettingsRepository(get(), get()) }
    single { ApiExecutor(get()) }
    single { FamilyMessengerApiClient(get(), get(), get(), get()) }
    single { SessionRepository(get(), get(), get(), get()) }
    single { SetupRepository(get()) }
    single { AdminRepository(get()) }
    single { ContactsRepository(get(), get()) }
    single { MessagesRepository(get(), get(), get()) }
    single { PresenceRepository(get(), get()) }
    single { DeviceRepository(get()) }
    single { SyncEngine(get(), get(), get(), get(), get(), get(), get()) }
    single { LoginUseCase(get(), get()) }
    single { LoadSetupStatusUseCase(get()) }
    single { BootstrapSystemUseCase(get()) }
    single { VerifyAdminAccessUseCase(get()) }
    single { CreateMemberUseCase(get()) }
    single { RemoveMemberUseCase(get()) }
    single { LoadContactsUseCase(get()) }
    single { SendTextMessageUseCase(get()) }
    single { SendQuickActionUseCase(get()) }
    single { ShareLocationUseCase(get(), get()) }
    single {
        AppViewModel(
            platformInfo = get(),
            localDatabase = get(),
            settingsRepository = get(),
            sessionStore = get(),
            contactsRepository = get(),
            messagesRepository = get(),
            sessionRepository = get(),
            syncEngine = get(),
            login = get(),
            loadSetupStatus = get(),
            bootstrapSystem = get(),
            verifyAdminAccess = get(),
            createMember = get(),
            removeMember = get(),
            loadContacts = get(),
            sendTextMessageUseCase = get(),
            sendQuickActionUseCase = get(),
            shareLocationUseCase = get(),
        )
    }
}
