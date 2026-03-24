package app.backend.service

import app.backend.config.FirebaseConfig
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class FcmPushService(private val config: FirebaseConfig) {
    private val log = LoggerFactory.getLogger(FcmPushService::class.java)

    private val messaging: FirebaseMessaging? = if (config.enabled) {
        runCatching {
            val credentials = GoogleCredentials.fromStream(
                config.serviceAccountJson!!.byteInputStream()
            ).createScoped("https://www.googleapis.com/auth/firebase.messaging")
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()
            val app = try {
                FirebaseApp.initializeApp(options, "family-messenger")
            } catch (_: IllegalStateException) {
                FirebaseApp.getInstance("family-messenger")
            }
            FirebaseMessaging.getInstance(app)
        }.onFailure { e ->
            log.error("Failed to initialize Firebase: ${e.message}")
        }.getOrNull()
    } else {
        log.info("Firebase not configured (FIREBASE_SERVICE_ACCOUNT_JSON not set) — push disabled")
        null
    }

    suspend fun sendPush(tokens: List<String>, title: String, body: String) {
        if (messaging == null || tokens.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val messages = tokens.map { token ->
                    Message.builder()
                        .setNotification(
                            Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build()
                        )
                        .setToken(token)
                        .build()
                }
                val response: BatchResponse = messaging.sendEach(messages)
                if (response.failureCount > 0) {
                    log.warn("FCM: ${response.successCount} sent, ${response.failureCount} failed")
                }
            }.onFailure { e ->
                log.error("FCM sendPush failed: ${e.message}")
            }
        }
    }
}
