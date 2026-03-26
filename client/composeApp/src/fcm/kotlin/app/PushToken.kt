package app

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal suspend fun providePushToken(): String? = suspendCoroutine { cont ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        cont.resume(if (task.isSuccessful) task.result else null)
    }
}
