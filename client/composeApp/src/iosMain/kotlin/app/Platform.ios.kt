package app

import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

private class IosStore(private val defaults: NSUserDefaults) : KeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}

private class IosGeolocationService : GeolocationService {
    override suspend fun currentLocation() = null
}

private class IosNotificationService : NotificationService {
    override fun notify(title: String, body: String) = Unit
}

actual fun createPlatformServices(): PlatformServices {
    val defaults = NSUserDefaults.standardUserDefaults
    val bundleName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleName") as? String ?: "iOS"
    return PlatformServices(
        platformInfo = PlatformInfo(
            type = PlatformType.IOS,
            displayName = bundleName,
            defaultBaseUrl = "http://localhost:8081",
        ),
        httpClient = HttpClient(Darwin),
        settingsStore = IosStore(defaults),
        secureStore = IosStore(defaults),
        geolocationService = IosGeolocationService(),
        notificationService = IosNotificationService(),
    )
}

actual fun randomUuid(): String = NSUUID().UUIDString()

actual fun platformLogInfo(tag: String, message: String) {
    NSLog("INFO [%@] %@", tag, message)
}

actual fun platformLogError(tag: String, message: String, throwable: Throwable?) {
    NSLog("ERROR [%@] %@", tag, message)
    throwable?.message?.let { NSLog("ERROR [%@] cause=%@", tag, it) }
}
