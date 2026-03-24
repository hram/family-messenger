package app

import app.repository.ContactsRepository
import app.repository.DeviceRepository
import app.repository.MessagesRepository
import app.repository.PresenceRepository
import app.storage.ClientSettingsRepository
import app.storage.SessionStore
import com.familymessenger.contract.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SyncEngine(
    private val sessionStore: SessionStore,
    private val settingsRepository: ClientSettingsRepository,
    private val contactsRepository: ContactsRepository,
    private val messagesRepository: MessagesRepository,
    private val presenceRepository: PresenceRepository,
    private val deviceRepository: DeviceRepository,
    private val notificationService: NotificationService,
) {
    private var pollingJob: Job? = null
    private var cycles = 0
    private val kickChannel = Channel<Unit>(Channel.CONFLATED)

    fun start(scope: CoroutineScope) {
        if (pollingJob != null) return
        pollingJob = scope.launchLoop()
    }

    fun kick() {
        kickChannel.trySend(Unit)
    }

    suspend fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun CoroutineScope.launchLoop(): Job = launch {
        while (true) {
            runCatching { syncOnce() }
            withTimeoutOrNull(4_000) { kickChannel.receive() }
        }
    }

    private suspend fun syncOnce() {
        if (sessionStore.currentSession() == null) return
        val settings = settingsRepository.settings()
        if (!settings.pollingEnabled) return

        contactsRepository.refreshContacts()
        messagesRepository.flushPendingMessages()
        val payload = messagesRepository.sync()
        cycles += 1

        val currentUserId = sessionStore.currentSession()?.auth?.user?.id
        val incomingMessages = payload.messages.filter { it.senderUserId != currentUserId }

        if (incomingMessages.isNotEmpty() && !settings.pushEnabled) {
            val contacts = contactsRepository.cachedContacts()
            val first = incomingMessages.first()
            val senderName = contacts.firstOrNull { it.user.id == first.senderUserId }?.user?.displayName
                ?: "Family Messenger"
            val body = when {
                !first.body.isNullOrBlank() -> first.body!!
                first.type == MessageType.LOCATION -> "Геопозиция"
                first.type == MessageType.QUICK_ACTION -> "Быстрый ответ"
                else -> ""
            }
            val title = if (incomingMessages.size > 1) "$senderName (+${incomingMessages.size - 1})" else senderName
            notificationService.notify(title, body)
        }

        if (cycles % 3 == 0) {
            presenceRepository.ping()
        }

        if (settings.pushEnabled) {
            val token = notificationService.getPushToken()
            deviceRepository.updatePushToken(token)
        }
    }
}
