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
                setBody(AdminCreateMemberRequest(masterPassword, displayName, role, isAdmin))
            }.body()
        }

    suspend fun removeMember(masterPassword: String, inviteCode: String): AdminMembersResponse =
        executor.execute {
            platformLogInfo(LOG_TAG_API, "POST /api/admin/members/remove invite=$inviteCode")
            httpClient.post(url("/api/admin/members/remove")) {
                authHeader()
                contentType(ContentType.Application.Json)
                setBody(AdminRemoveMemberRequest(masterPassword, inviteCode))
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
                url { parameters.append("since_id", sinceId.toString()) }
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
                setBody(ShareLocationRequest(location.latitude, location.longitude, location.accuracy, location.label))
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
