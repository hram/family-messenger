package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.window
import kotlin.random.Random
import org.w3c.dom.get
import org.w3c.dom.set

private class BrowserStore(private val prefix: String) : KeyValueStore {
    override fun getString(key: String): String? = window.localStorage["$prefix:$key"]

    override fun putString(key: String, value: String) {
        window.localStorage["$prefix:$key"] = value
    }

    override fun remove(key: String) {
        window.localStorage.removeItem("$prefix:$key")
    }
}

private class BrowserGeolocationService : GeolocationService {
    override suspend fun currentLocation() = null
}

private class BrowserNotificationService : NotificationService {
    override fun notify(title: String, body: String) {
        println("$title: $body")
    }
}

actual fun createPlatformServices(): PlatformServices = PlatformServices(
    platformInfo = PlatformInfo(
        type = PlatformType.WEB,
        displayName = "Web",
        defaultBaseUrl = "http://localhost:8081",
    ),
    httpClient = HttpClient(Js),
    settingsStore = BrowserStore("fm-settings"),
    secureStore = BrowserStore("fm-secure"),
    geolocationService = BrowserGeolocationService(),
    notificationService = BrowserNotificationService(),
)

actual fun randomUuid(): String {
    val bytes = ByteArray(16)
    Random.nextBytes(bytes)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.joinToString("") { ((it.toInt() and 0xff) + 0x100).toString(16).substring(1) }
    return buildString {
        append(hex.substring(0, 8))
        append('-')
        append(hex.substring(8, 12))
        append('-')
        append(hex.substring(12, 16))
        append('-')
        append(hex.substring(16, 20))
        append('-')
        append(hex.substring(20, 32))
    }
}

actual fun platformLogInfo(tag: String, message: String) {
    println("INFO [$tag] $message")
}

actual fun platformLogError(tag: String, message: String, throwable: Throwable?) {
    println("ERROR [$tag] $message")
    if (throwable != null) {
        println(throwable.toString())
    }
}
