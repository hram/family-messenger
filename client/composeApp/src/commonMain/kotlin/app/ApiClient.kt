package app

import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.HealthResponse
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.RegisterDeviceRequest
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SendMessageResponse
import com.familymessenger.contract.SyncPayload
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class FamilyMessengerApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
) {
    suspend fun registerDevice(request: RegisterDeviceRequest): ApiResponse<AuthPayload> =
        httpClient.post("$baseUrl/api/auth/register-device") {
            setBody(request)
        }.body()

    suspend fun login(request: LoginRequest): ApiResponse<AuthPayload> =
        httpClient.post("$baseUrl/api/auth/login") {
            setBody(request)
        }.body()

    suspend fun profile(): ApiResponse<ProfileResponse> =
        httpClient.get("$baseUrl/api/profile/me").body()

    suspend fun contacts(): ApiResponse<ContactsResponse> =
        httpClient.get("$baseUrl/api/contacts").body()

    suspend fun sendMessage(request: SendMessageRequest): ApiResponse<SendMessageResponse> =
        httpClient.post("$baseUrl/api/messages/send") {
            setBody(request)
        }.body()

    suspend fun sync(sinceId: Long?): ApiResponse<SyncPayload> =
        httpClient.get("$baseUrl/api/messages/sync") {
            url {
                sinceId?.let { parameters.append("since_id", it.toString()) }
            }
        }.body()

    suspend fun health(): ApiResponse<HealthResponse> =
        httpClient.get("$baseUrl/api/health").body()

    companion object {
        fun create(baseUrl: String, engineFactory: () -> HttpClient): FamilyMessengerApiClient =
            FamilyMessengerApiClient(baseUrl, engineFactory())
    }
}

fun defaultHttpClient(factory: HttpClient): HttpClient = factory.config {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
