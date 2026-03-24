package app

import androidx.compose.runtime.Composable
import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.UUID
import java.util.prefs.Preferences
import javax.swing.SwingUtilities

private class DesktopStore(private val preferences: Preferences) : KeyValueStore {
    override fun getString(key: String): String? = preferences.get(key, null)

    override fun putString(key: String, value: String) {
        preferences.put(key, value)
    }

    override fun remove(key: String) {
        preferences.remove(key)
    }
}

private class DesktopGeolocationService : GeolocationService {
    override suspend fun currentLocation() = null
}

private class DesktopNotificationService : NotificationService {
    private val trayIcon: TrayIcon? by lazy { initTrayIcon() }

    private fun initTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        return runCatching {
            val url = DesktopNotificationService::class.java.getResource("/icon.png")
            val image = if (url != null) {
                Toolkit.getDefaultToolkit().createImage(url)
            } else {
                BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).also { img ->
                    val g = img.createGraphics()
                    g.color = java.awt.Color(0x4C, 0xAF, 0x50)
                    g.fillOval(0, 0, 16, 16)
                    g.dispose()
                }
            }
            val icon = TrayIcon(image, "Family Messenger")
            icon.isImageAutoSize = true
            SystemTray.getSystemTray().add(icon)
            icon
        }.getOrNull()
    }

    override fun notify(title: String, body: String) {
        val icon = trayIcon
        if (icon != null) {
            SwingUtilities.invokeLater {
                icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
            }
        } else {
            println("$title: $body")
        }
    }
}

actual fun createPlatformServices(): PlatformServices = PlatformServices(
    platformInfo = PlatformInfo(
        type = PlatformType.DESKTOP,
        displayName = "Desktop",
        defaultBaseUrl = "http://localhost:8081",
    ),
    httpClient = HttpClient(OkHttp),
    settingsStore = DesktopStore(Preferences.userRoot().node("family-messenger/settings")),
    secureStore = DesktopStore(Preferences.userRoot().node("family-messenger/secure")),
    geolocationService = DesktopGeolocationService(),
    notificationService = DesktopNotificationService(),
)

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun platformLogInfo(tag: String, message: String) {
    println("INFO [$tag] $message")
}

actual fun platformLogError(tag: String, message: String, throwable: Throwable?) {
    println("ERROR [$tag] $message")
    throwable?.printStackTrace()
}

@Composable
actual fun platformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
