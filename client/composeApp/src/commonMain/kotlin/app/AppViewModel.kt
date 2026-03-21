package app

import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.QuickActionCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AppViewModel(
    private val platformInfo: PlatformInfo,
    private val settingsRepository: ClientSettingsRepository,
    private val sessionStore: SessionStore,
    private val contactsRepository: ContactsRepository,
    private val messagesRepository: MessagesRepository,
    private val sessionRepository: SessionRepository,
    private val syncEngine: SyncEngine,
    private val registerDevice: RegisterDeviceUseCase,
    private val login: LoginUseCase,
    private val loadContacts: LoadContactsUseCase,
    private val sendTextMessageUseCase: SendTextMessageUseCase,
    private val sendQuickActionUseCase: SendQuickActionUseCase,
    private val shareLocationUseCase: ShareLocationUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(
        AppUiState(
            platform = platformInfo.type,
            platformName = platformInfo.displayName,
            onboarding = OnboardingFormState(
                baseUrl = platformInfo.defaultBaseUrl,
                deviceName = platformInfo.displayName,
            ),
        ),
    )
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        sessionStore.session
            .onEach { session ->
                val settings = settingsRepository.settings()
                mutableState.value = mutableState.value.copy(
                    platform = platformInfo.type,
                    platformName = platformInfo.displayName,
                    currentUser = session?.auth?.user,
                    screen = if (session == null) Screen.ONBOARDING else mutableState.value.screen.takeIf { it != Screen.ONBOARDING } ?: Screen.CONTACTS,
                    onboarding = mutableState.value.onboarding.copy(
                        baseUrl = settings.serverBaseUrl,
                        deviceName = settings.lastDeviceName.ifBlank { platformInfo.displayName },
                    ),
                    settings = SettingsState(
                        pollingEnabled = settings.pollingEnabled,
                        pushEnabled = settings.pushEnabled,
                    ),
                )
            }
            .launchIn(scope)

        scope.launch {
            val settings = settingsRepository.settings()
            mutableState.value = mutableState.value.copy(
                onboarding = mutableState.value.onboarding.copy(
                    baseUrl = settings.serverBaseUrl,
                    deviceName = settings.lastDeviceName.ifBlank { platformInfo.displayName },
                ),
                settings = SettingsState(settings.pollingEnabled, settings.pushEnabled),
            )
            if (sessionRepository.restore() != null) {
                syncEngine.start(scope)
                refreshContacts()
            }
        }
    }

    fun updateBaseUrl(value: String) = mutate { copy(onboarding = onboarding.copy(baseUrl = value)) }

    fun updateInviteCode(value: String) = mutate { copy(onboarding = onboarding.copy(inviteCode = value)) }

    fun updateDeviceName(value: String) = mutate { copy(onboarding = onboarding.copy(deviceName = value)) }

    fun setAuthMode(mode: AuthMode) = mutate { copy(onboarding = onboarding.copy(authMode = mode)) }

    fun updateDraftMessage(value: String) = mutate { copy(draftMessage = value) }

    fun submitAuth() {
        runBusy {
            val onboarding = state.value.onboarding
            settingsRepository.updateServerBaseUrl(onboarding.baseUrl)
            val session = when (onboarding.authMode) {
                AuthMode.REGISTER -> registerDevice(onboarding.inviteCode, onboarding.deviceName)
                AuthMode.LOGIN -> login(onboarding.inviteCode, onboarding.deviceName)
            }
            syncEngine.start(scope)
            val contacts = loadContacts()
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                currentUser = session.auth.user,
                contacts = contacts,
                errorMessage = null,
                statusMessage = "Сессия активна",
            )
        }
    }

    fun refreshContacts() {
        runBusy {
            val contacts = loadContacts()
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                contacts = contacts,
                selectedContactId = null,
                selectedContactName = null,
                messages = emptyList(),
                pendingMessageCount = messagesRepository.pendingCount(),
                syncCursor = messagesRepository.syncCursor(),
            )
        }
    }

    fun openChat(contact: ContactSummary) {
        scope.launch {
            val messages = messagesRepository.conversation(contact.user.id)
            mutableState.value = mutableState.value.copy(
                screen = Screen.CHAT,
                selectedContactId = contact.user.id,
                selectedContactName = contact.user.displayName,
                messages = messages,
                draftMessage = "",
            )
            runCatching { messagesRepository.markConversationDelivered(contact.user.id) }
            refreshCurrentConversation()
        }
    }

    fun backToContacts() = mutate { copy(screen = Screen.CONTACTS, selectedContactId = null, selectedContactName = null) }

    fun openSettings() = mutate { copy(screen = Screen.SETTINGS) }

    fun sendCurrentDraft() {
        val contactId = state.value.selectedContactId ?: return
        val draft = state.value.draftMessage.trim()
        if (draft.isBlank()) return
        runBusy {
            sendTextMessageUseCase(contactId, draft)
            refreshCurrentConversation()
            mutableState.value = mutableState.value.copy(draftMessage = "")
        }
    }

    fun sendQuickAction(code: QuickActionCode) {
        val contactId = state.value.selectedContactId ?: return
        runBusy {
            sendQuickActionUseCase(contactId, code)
            refreshCurrentConversation()
        }
    }

    fun shareLocation() {
        val contactId = state.value.selectedContactId ?: return
        runBusy {
            val ok = shareLocationUseCase(contactId)
            refreshCurrentConversation()
            mutableState.value = mutableState.value.copy(
                statusMessage = if (ok) "Локация отправлена" else "Локация недоступна на этой платформе",
            )
        }
    }

    fun markConversationRead() {
        val contactId = state.value.selectedContactId ?: return
        runBusy {
            messagesRepository.markConversationRead(contactId)
            refreshCurrentConversation()
        }
    }

    fun updatePollingEnabled(enabled: Boolean) {
        runBusy {
            settingsRepository.updatePollingEnabled(enabled)
            mutableState.value = mutableState.value.copy(settings = mutableState.value.settings.copy(pollingEnabled = enabled))
            syncEngine.kick()
        }
    }

    fun updatePushEnabled(enabled: Boolean) {
        runBusy {
            settingsRepository.updatePushEnabled(enabled)
            mutableState.value = mutableState.value.copy(settings = mutableState.value.settings.copy(pushEnabled = enabled))
        }
    }

    fun saveBaseUrl() {
        runBusy {
            settingsRepository.updateServerBaseUrl(state.value.onboarding.baseUrl)
            mutableState.value = mutableState.value.copy(statusMessage = "Base URL сохранён")
        }
    }

    fun logout() {
        runBusy {
            sessionRepository.logout()
            syncEngine.stop()
            mutableState.value = mutableState.value.copy(
                screen = Screen.ONBOARDING,
                currentUser = null,
                contacts = emptyList(),
                messages = emptyList(),
                selectedContactId = null,
                selectedContactName = null,
                draftMessage = "",
                statusMessage = "Сессия очищена",
            )
        }
    }

    fun clearBanner() = mutate { copy(errorMessage = null, statusMessage = null) }

    fun close() {
        scope.launch {
            syncEngine.stop()
        }
        scope.cancel()
    }

    private suspend fun refreshCurrentConversation() {
        val contactId = mutableState.value.selectedContactId ?: return
        mutableState.value = mutableState.value.copy(
            messages = messagesRepository.conversation(contactId),
            pendingMessageCount = messagesRepository.pendingCount(),
            syncCursor = messagesRepository.syncCursor(),
        )
    }

    private fun runBusy(block: suspend () -> Unit) {
        scope.launch {
            mutableState.value = mutableState.value.copy(isBusy = true, errorMessage = null)
            runCatching { block() }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Операция завершилась с ошибкой")
                }
            mutableState.value = mutableState.value.copy(isBusy = false)
        }
    }

    private fun mutate(transform: AppUiState.() -> AppUiState) {
        mutableState.value = mutableState.value.transform()
    }
}
