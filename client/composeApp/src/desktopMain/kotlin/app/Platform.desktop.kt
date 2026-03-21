package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID
import java.util.prefs.Preferences

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
    override fun notify(title: String, body: String) {
        println("$title: $body")
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
