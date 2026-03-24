package app

import app.repository.ContactsRepository
import app.repository.DeviceRepository
import app.repository.MessagesRepository
import app.repository.PresenceRepository
import app.storage.ClientSettingsRepository
import app.storage.SessionStore
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.SyncPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SyncTick(
    val contacts: List<ContactSummary>,
    val payload: SyncPayload,
)

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
    private val _ticks = MutableSharedFlow<SyncTick>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val ticks: SharedFlow<SyncTick> = _ticks.asSharedFlow()

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

        val contacts = contactsRepository.fetchContacts()
        val sinceId = messagesRepository.syncCursor()
        val payload = messagesRepository.fetchSync(sinceId)
        cycles += 1

        val currentUserId = sessionStore.currentSession()?.auth?.user?.id
        val incomingMessages = payload.messages.filter { it.senderUserId != currentUserId }

        if (incomingMessages.isNotEmpty() && !settings.pushEnabled) {
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

        _ticks.emit(SyncTick(contacts, payload))

        if (cycles % 3 == 0) {
            presenceRepository.ping()
        }

        if (settings.pushEnabled) {
            val token = notificationService.getPushToken()
            deviceRepository.updatePushToken(token)
        }
    }
}
