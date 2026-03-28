package app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient

data class PlatformInfo(
    val type: PlatformType,
    val displayName: String,
    val defaultBaseUrl: String,
)

interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(key: String)
}

interface GeolocationService {
    suspend fun currentLocation(): LocationPayload?
}

interface NotificationService {
    fun notify(title: String, body: String)
    suspend fun getPushToken(): String? = null
}

data class PlatformServices(
    val platformInfo: PlatformInfo,
    val httpClient: HttpClient,
    val settingsStore: KeyValueStore,
    val secureStore: KeyValueStore,
    val geolocationService: GeolocationService,
    val notificationService: NotificationService,
)

expect fun createPlatformServices(): PlatformServices

expect fun randomUuid(): String

expect fun platformLogInfo(tag: String, message: String)

expect fun platformLogError(tag: String, message: String, throwable: Throwable? = null)

expect fun currentLanguageCode(): String

@Composable
expect fun appLogoPainter(): Painter

@Composable
expect fun platformBackHandler(enabled: Boolean, onBack: () -> Unit)
