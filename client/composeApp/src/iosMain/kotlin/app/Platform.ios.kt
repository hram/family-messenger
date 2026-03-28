package app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.familymessenger.contract.PlatformType
import com.familymessenger.composeapp.generated.resources.Res
import com.familymessenger.composeapp.generated.resources.ic_launcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import org.jetbrains.compose.resources.painterResource
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIPasteboard

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
        httpClient = HttpClient(Darwin) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
            }
        },
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

actual fun currentLanguageCode(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

actual fun copyTextToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

@Composable
actual fun appLogoPainter(): Painter = painterResource(Res.drawable.ic_launcher)

@Composable
actual fun platformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
