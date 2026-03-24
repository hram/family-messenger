package app.network

import app.platformLogInfo
import app.storage.ClientSettingsRepository
import app.storage.SessionStore
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.AdminCreateMemberRequest
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.AdminRemoveMemberRequest
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.HealthResponse
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.MarkDeliveredRequest
import com.familymessenger.contract.MarkReadRequest
import com.familymessenger.contract.PresencePingRequest
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SendMessageResponse
import com.familymessenger.contract.SetupBootstrapRequest
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupStatusResponse
import com.familymessenger.contract.ShareLocationRequest
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.UpdatePushTokenRequest
import com.familymessenger.contract.UserRole
import com.familymessenger.contract.VerifyAdminAccessRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

private const val LOG_TAG_API = "FamilyMessengerApi"

class FamilyMessengerApiClient(
    private val httpClient: HttpClient,
    private val sessionStore: SessionStore,
    private val settingsRepository: ClientSettingsRepository,
    private val executor: ApiExecutor,
) {
    // ── Auth ──────────────────────────────────────────────────────────────────

    /** Аутентифицирует пользователя по инвайт-коду. Возвращает токен сессии и данные о пользователе и семье. */
    suspend fun login(request: LoginRequest): AuthPayload =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/auth/login platform=${request.platform}")
            httpClient.post(url("/api/auth/login")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    // ── Setup ─────────────────────────────────────────────────────────────────

    /** Возвращает статус первоначальной настройки: инициализирована ли система (создана ли семья). */
    suspend fun setupStatus(): SetupStatusResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "GET /api/setup/status")
            httpClient.get(url("/api/setup/status")).body()
        }

    /** Первоначальная настройка системы: создаёт семью, задаёт мастер-пароль и генерирует инвайт-коды для участников. */
    suspend fun bootstrap(request: SetupBootstrapRequest): SetupBootstrapResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/setup/bootstrap members=${request.members.size}")
            httpClient.post(url("/api/setup/bootstrap")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /** Проверяет мастер-пароль и возвращает список участников семьи. Используется для входа в раздел администрирования. */
    suspend fun verifyAdminAccess(masterPassword: String): AdminMembersResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/admin/verify")
            httpClient.post(url("/api/admin/verify")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(VerifyAdminAccessRequest(masterPassword))
            }.body()
        }

    /** Создаёт нового участника семьи и генерирует для него инвайт-код. Требует мастер-пароль. */
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
                setBody(AdminCreateMemberRequest(masterPassword, displayName, role, isAdmin))
            }.body()
        }

    /** Удаляет участника семьи по инвайт-коду. Аннулирует все его сессии. Требует мастер-пароль. */
    suspend fun removeMember(masterPassword: String, inviteCode: String): AdminMembersResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/admin/members/remove invite=$inviteCode")
            httpClient.post(url("/api/admin/members/remove")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(AdminRemoveMemberRequest(masterPassword, inviteCode))
            }.body()
        }

    // ── Profile & Contacts ────────────────────────────────────────────────────

    /** Возвращает профиль текущего пользователя и данные его семьи (актуальные данные с сервера). */
    suspend fun profile(): ProfileResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "GET /api/profile/me")
            httpClient.get(url("/api/profile/me")) {
                authHeader()
            }.body()
        }

    /** Возвращает список участников семьи с их статусом онлайн и последним временем активности. */
    suspend fun contacts(): ContactsResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "GET /api/contacts")
            httpClient.get(url("/api/contacts")) {
                authHeader()
            }.body()
        }

    // ── Messages ──────────────────────────────────────────────────────────────

    /** Отправляет сообщение (текст, быстрый ответ или геопозиция). Возвращает сообщение с серверным id. */
    suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse =
        executor.execute {
            httpClient.post(url("/api/messages/send")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    /**
     * Возвращает новые сообщения и квитанции (доставка/прочтение) начиная с [sinceId].
     * Используется polling'ом: при каждом опросе передаётся курсор последнего полученного сообщения.
     */
    suspend fun sync(sinceId: Long): SyncPayload =
        executor.execute {
            httpClient.get(url("/api/messages/sync")) {
                authHeader()
                url { parameters.append("since_id", sinceId.toString()) }
            }.body()
        }

    /** Подтверждает доставку сообщений на устройство. Сервер обновляет статус до DELIVERED. */
    suspend fun markDelivered(messageIds: List<Long>): AckResponse =
        executor.execute {
            httpClient.post(url("/api/messages/mark-delivered")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(MarkDeliveredRequest(messageIds))
            }.body()
        }

    /** Подтверждает прочтение сообщений пользователем. Сервер обновляет статус до READ. */
    suspend fun markRead(messageIds: List<Long>): AckResponse =
        executor.execute {
            httpClient.post(url("/api/messages/mark-read")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(MarkReadRequest(messageIds))
            }.body()
        }

    // ── Presence & Location ───────────────────────────────────────────────────

    /** Публикует текущую геопозицию пользователя. Отображается другим участникам семьи на карте. */
    suspend fun shareLocation(location: LocationPayload): AckResponse =
        executor.execute {
            httpClient.post(url("/api/location/share")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(ShareLocationRequest(location.latitude, location.longitude, location.accuracy, location.label))
            }.body()
        }

    /** Обновляет метку «онлайн» для текущего пользователя. Вызывается периодически пока приложение активно. */
    suspend fun ping(): AckResponse =
        executor.execute {
            httpClient.post(url("/api/presence/ping")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(PresencePingRequest())
            }.body()
        }

    // ── Device ────────────────────────────────────────────────────────────────

    /**
     * Регистрирует или обновляет FCM push-токен устройства.
     * Передайте null чтобы отозвать токен (например, при выходе из аккаунта).
     */
    suspend fun updatePushToken(pushToken: String?): AckResponse =
        executor.execute {
            httpClient.post(url("/api/device/update-push-token")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(UpdatePushTokenRequest(pushToken))
            }.body()
        }

    // ── Health ────────────────────────────────────────────────────────────────

    /** Проверяет доступность сервера. Используется при онбординге для валидации введённого URL. */
    suspend fun health(): HealthResponse =
        executor.execute {
            httpClient.get(url("/api/health")).body()
        }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun url(path: String): String = settingsRepository.settings().serverBaseUrl + path

    private fun io.ktor.client.request.HttpRequestBuilder.authHeader() {
        sessionStore.currentToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }
}
