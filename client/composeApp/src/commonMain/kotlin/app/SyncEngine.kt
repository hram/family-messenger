package app

import app.repository.ContactsRepository
import app.repository.DeviceRepository
import app.repository.MessagesRepository
import app.repository.PresenceRepository
import app.storage.ClientSettingsRepository
import app.storage.SessionStore
import com.familymessenger.contract.MessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SYNC_IDLE_DELAY_MS = 1_000L
private const val SYNC_RETRY_DELAY_MS = 3_000L

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

    fun start(scope: CoroutineScope) {
        if (pollingJob != null) return
        pollingJob = scope.launchLoop()
    }

    suspend fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun CoroutineScope.launchLoop(): Job = launch {
        while (isActive) {
            val result = try {
                syncOnce()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                SyncCycleResult.FAILED
            }
            when (result) {
                SyncCycleResult.SUCCESS -> Unit
                SyncCycleResult.IDLE -> delay(SYNC_IDLE_DELAY_MS)
                SyncCycleResult.FAILED -> delay(SYNC_RETRY_DELAY_MS)
            }
        }
    }

    private suspend fun syncOnce(): SyncCycleResult {
        if (sessionStore.currentSession() == null) return SyncCycleResult.IDLE
        val settings = settingsRepository.settings()
        if (!settings.pollingEnabled) return SyncCycleResult.IDLE

        val contacts = contactsRepository.fetchContacts()
        val payload = try {
            messagesRepository.fetchSync(messagesRepository.syncState())
        } catch (error: AppException.SyncResetRequired) {
            messagesRepository.resetAfterServerReset(error.serverInstanceId)
            messagesRepository.fetchSync(messagesRepository.syncState())
        }
        cycles += 1

        val currentUserId = sessionStore.currentSession()?.auth?.user?.id
        val incomingMessages = payload.messages.filter { it.senderUserId != currentUserId }
        val incomingMessageIds = incomingMessages.mapNotNull { it.id }

        if (incomingMessages.isNotEmpty() && !settings.pushEnabled) {
            val strings = notificationStrings(currentLanguageCode())
            val first = incomingMessages.first()
            val senderName = contacts.firstOrNull { it.user.id == first.senderUserId }?.user?.displayName
                ?: strings.appName
            val body = when {
                !first.body.isNullOrBlank() -> first.body!!
                first.type == MessageType.LOCATION -> strings.location
                first.type == MessageType.QUICK_ACTION -> strings.quickAction
                else -> ""
            }
            val title = if (incomingMessages.size > 1) strings.multipleSenders(senderName, incomingMessages.size - 1) else senderName
            notificationService.notify(title, body)
        }

        runCatching { messagesRepository.flushPendingMessages() }
        messagesRepository.applyTick(contacts, payload)
        runCatching { messagesRepository.markDelivered(incomingMessageIds) }

        if (cycles % 3 == 0) {
            presenceRepository.ping()
        }

        if (settings.pushEnabled) {
            val token = notificationService.getPushToken()
            deviceRepository.updatePushToken(token)
        }

        return SyncCycleResult.SUCCESS
    }
}

private enum class SyncCycleResult {
    SUCCESS,
    IDLE,
    FAILED,
}

private data class SyncNotificationStrings(
    val appName: String,
    val location: String,
    val quickAction: String,
    val multipleSenders: (String, Int) -> String,
)

private fun notificationStrings(languageCode: String): SyncNotificationStrings {
    val isRussian = languageCode.startsWith("ru", ignoreCase = true)
    return if (isRussian) {
        SyncNotificationStrings(
            appName = "Family Messenger",
            location = "Геопозиция",
            quickAction = "Быстрый ответ",
            multipleSenders = { sender, count -> "$sender (+$count)" },
        )
    } else {
        SyncNotificationStrings(
            appName = "Family Messenger",
            location = "Location",
            quickAction = "Quick action",
            multipleSenders = { sender, count -> "$sender (+$count)" },
        )
    }
}
