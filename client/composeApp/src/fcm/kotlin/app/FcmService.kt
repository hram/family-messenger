package app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FamilyMessengerFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["senderName"]
            ?: remoteMessage.notification?.title
            ?: "Family Messenger"
        val body = remoteMessage.data["messageBody"]
            ?: remoteMessage.notification?.body
            ?: return
        showAndroidNotification(applicationContext, title, body)
    }

    override fun onNewToken(token: String) {
        // Токен обновится автоматически при следующем цикле SyncEngine
        // через notificationService.getPushToken() → deviceRepository.updatePushToken()
    }
}
