package app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationCompat
import com.familymessenger.contract.PlatformType
import com.familymessenger.client.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.util.UUID

private object AndroidRuntime {
    lateinit var context: Context
}

private const val NOTIFICATION_CHANNEL_ID = "family_messenger_messages"
private const val NOTIFICATION_CHANNEL_NAME = "Сообщения"
private var notificationCounter = 0

fun initializeAndroidPlatform(context: Context) {
    AndroidRuntime.context = context.applicationContext
    createNotificationChannel(context.applicationContext)
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

fun showAndroidNotification(context: Context, title: String, body: String) {
    val intent = Intent(context, AndroidMainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val manager = context.getSystemService(NotificationManager::class.java)
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    manager.notify(++notificationCounter, notification)
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
    override fun notify(title: String, body: String) {
        showAndroidNotification(AndroidRuntime.context, title, body)
    }

    override suspend fun getPushToken(): String? = providePushToken()
}

actual fun createPlatformServices(): PlatformServices {
    val context = AndroidRuntime.context
    return PlatformServices(
        platformInfo = PlatformInfo(
            type = PlatformType.ANDROID,
            displayName = "Android",
            defaultBaseUrl = "http://82.97.243.127:8080",
        ),
        httpClient = HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 35_000
                requestTimeoutMillis = 35_000
            }
        },
        settingsStore = AndroidStore(context.getSharedPreferences("family-messenger-settings", Context.MODE_PRIVATE)),
        secureStore = AndroidStore(context.getSharedPreferences("family-messenger-secure", Context.MODE_PRIVATE)),
        geolocationService = AndroidGeolocationService(),
        notificationService = AndroidNotificationService(),
    )
}

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun platformLogInfo(tag: String, message: String) {
    Log.i(tag, message)
}

actual fun platformLogError(tag: String, message: String, throwable: Throwable?) {
    Log.e(tag, message, throwable)
}

actual fun currentLanguageCode(): String =
    AndroidRuntime.context.resources.configuration.locales[0]?.toLanguageTag() ?: "en"

@Composable
actual fun appLogoPainter(): Painter = painterResource(R.mipmap.ic_launcher)

@Composable
actual fun platformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
