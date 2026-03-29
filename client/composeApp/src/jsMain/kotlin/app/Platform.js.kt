package app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.familymessenger.contract.PlatformType
import com.familymessenger.composeapp.generated.resources.Res
import com.familymessenger.composeapp.generated.resources.logo_ui
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.browser.window
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random
import org.w3c.dom.HTMLTextAreaElement
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
        val notif = window.asDynamic().Notification
        if (notif == null || js("typeof Notification === 'undefined'") as Boolean) return
        when (notif.permission as? String) {
            "granted" -> showNotification(title, body)
            "default" -> notif.requestPermission()
            // "denied" — do nothing
        }
    }

    private fun showNotification(title: String, body: String) {
        js("new Notification(title, { body: body })")
    }
}

fun requestBrowserNotificationPermission() {
    val notif = window.asDynamic().Notification
    if (notif != null && notif.permission == "default") {
        notif.requestPermission()
    }
}

actual fun createPlatformServices(): PlatformServices = PlatformServices(
    platformInfo = PlatformInfo(
        type = PlatformType.WEB,
        displayName = "Web",
        defaultBaseUrl = window.location.origin,
    ),
    httpClient = HttpClient(Js) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
        }
    },
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

actual fun currentLanguageCode(): String =
    window.navigator.language ?: "en"

actual fun copyTextToClipboard(text: String) {
    val navigatorClipboard = window.navigator.asDynamic().clipboard
    if (navigatorClipboard != null) {
        runCatching {
            navigatorClipboard.writeText(text)
            return
        }.onFailure {
            platformLogError("Clipboard", "navigator.clipboard.writeText failed", it)
        }
    }

    val textarea = document.createElement("textarea") as HTMLTextAreaElement
    textarea.value = text
    textarea.setAttribute("readonly", "true")
    textarea.style.position = "fixed"
    textarea.style.left = "-9999px"
    textarea.style.top = "0"
    document.body?.appendChild(textarea)
    textarea.select()
    runCatching {
        document.execCommand("copy")
    }.onFailure {
        platformLogError("Clipboard", "document.execCommand(copy) failed", it)
    }
    document.body?.removeChild(textarea)
}

@Composable
actual fun appLogoPainter(): Painter = painterResource(Res.drawable.logo_ui)

@Composable
actual fun platformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
