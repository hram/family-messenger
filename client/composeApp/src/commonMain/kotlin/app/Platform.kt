package app

import androidx.compose.runtime.Composable
import com.familymessenger.contract.ClientLogLevel
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

object ClientDiagnosticsBridge {
    @Volatile
    private var sink: ((ClientLogLevel, String, String, String?) -> Unit)? = null

    fun install(sink: (ClientLogLevel, String, String, String?) -> Unit) {
        this.sink = sink
    }

    fun clear() {
        sink = null
    }

    fun record(level: ClientLogLevel, tag: String, message: String, details: String?) {
        sink?.invoke(level, tag, message, details)
    }
}

fun clientDiagnosticsInfo(tag: String, message: String) {
    platformLogInfo(tag, message)
    ClientDiagnosticsBridge.record(ClientLogLevel.INFO, tag, message, null)
}

fun clientDiagnosticsError(tag: String, message: String, throwable: Throwable? = null) {
    platformLogError(tag, message, throwable)
    val details = throwable?.let {
        buildString {
            append(it::class.simpleName ?: "Throwable")
            it.message?.takeIf { text -> text.isNotBlank() }?.let { text ->
                append(": ")
                append(text)
            }
        }
    }
    ClientDiagnosticsBridge.record(ClientLogLevel.ERROR, tag, message, details)
}

@Composable
expect fun platformBackHandler(enabled: Boolean, onBack: () -> Unit)
