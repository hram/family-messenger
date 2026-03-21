package app

import android.content.Context
import android.content.SharedPreferences
import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID

private object AndroidRuntime {
    lateinit var context: Context
}

fun initializeAndroidPlatform(context: Context) {
    AndroidRuntime.context = context.applicationContext
}

private class AndroidStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}

private class AndroidGeolocationService : GeolocationService {
    override suspend fun currentLocation() = null
}

private class AndroidNotificationService : NotificationService {
    override fun notify(title: String, body: String) = Unit
}

actual fun createPlatformServices(): PlatformServices {
    val context = AndroidRuntime.context
    return PlatformServices(
        platformInfo = PlatformInfo(
            type = PlatformType.ANDROID,
            displayName = "Android",
            defaultBaseUrl = "http://10.0.2.2:8081",
        ),
        httpClient = HttpClient(OkHttp),
        settingsStore = AndroidStore(context.getSharedPreferences("family-messenger-settings", Context.MODE_PRIVATE)),
        secureStore = AndroidStore(context.getSharedPreferences("family-messenger-secure", Context.MODE_PRIVATE)),
        geolocationService = AndroidGeolocationService(),
        notificationService = AndroidNotificationService(),
    )
}

actual fun randomUuid(): String = UUID.randomUUID().toString()
