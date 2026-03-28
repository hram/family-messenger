package app

import app.repository.ContactsRepository
import app.repository.MessagesRepository
import app.repository.SessionRepository
import app.storage.ClientSettingsRepository
import app.storage.LocalDatabase
import app.storage.SessionStore
import app.ui.AdminState
import app.ui.AppUiState
import app.ui.OnboardingFormState
import app.ui.Screen
import app.ui.SettingsState
import app.ui.UiText
import app.ui.uiText
import app.usecase.CreateMemberUseCase
import app.usecase.LoadContactsUseCase
import app.usecase.LoadSetupStatusUseCase
import app.usecase.LoginUseCase
import app.usecase.RemoveMemberUseCase
import app.usecase.SendQuickActionUseCase
import app.usecase.SendTextMessageUseCase
import app.usecase.ShareLocationUseCase
import app.usecase.VerifyAdminAccessUseCase
import app.usecase.VerifyMasterPasswordUseCase
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.UserRole
import com.familymessenger.composeapp.generated.resources.*
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
    private val localDatabase: LocalDatabase,
    private val settingsRepository: ClientSettingsRepository,
    private val sessionStore: SessionStore,
    private val contactsRepository: ContactsRepository,
    private val messagesRepository: MessagesRepository,
    private val sessionRepository: SessionRepository,
    private val syncEngine: SyncEngine,
    private val login: LoginUseCase,
    private val loadSetupStatus: LoadSetupStatusUseCase,
    private val verifyAdminAccess: VerifyAdminAccessUseCase,
    private val verifyMasterPassword: VerifyMasterPasswordUseCase,
    private val createMember: CreateMemberUseCase,
    private val removeMember: RemoveMemberUseCase,
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
            ),
        ),
    )
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        localDatabase.snapshots
            .onEach { snapshot ->
                val contacts = snapshot.contacts.map { it.contact }
                val selectedContactId = mutableState.value.selectedContactId
                val selectedContact = selectedContactId?.let { selectedId ->
                    contacts.firstOrNull { it.user.id == selectedId }
                }
                val currentUserId = sessionStore.currentSession()?.auth?.user?.id
                val unreadCounts = if (currentUserId != null) {
                    snapshot.messages
                        .map { it.payload }
                        .filter { it.isIncomingFor(currentUserId) }
                        .filter { it.status != MessageStatus.READ }
                        .groupingBy {
                            if (it.recipientUserId == FAMILY_GROUP_CHAT_ID) FAMILY_GROUP_CHAT_ID else it.senderUserId
                        }
                        .eachCount()
                } else {
                    emptyMap()
                }
                mutableState.value = mutableState.value.copy(
                    screen = if (selectedContact == null && mutableState.value.screen == Screen.CHAT) Screen.CONTACTS else mutableState.value.screen,
                    contacts = contacts,
                    unreadCounts = unreadCounts,
                    selectedContactId = selectedContact?.user?.id,
                    selectedContactName = selectedContact?.user?.displayName,
                    messages = snapshot.messages
                        .map { it.payload }
                        .filter { payload ->
                            when {
                                currentUserId == null || selectedContactId == null || selectedContact == null -> false
                                selectedContactId == FAMILY_GROUP_CHAT_ID -> payload.recipientUserId == FAMILY_GROUP_CHAT_ID
                                else ->
                                    (payload.senderUserId == currentUserId && payload.recipientUserId == selectedContactId) ||
                                        (payload.senderUserId == selectedContactId && payload.recipientUserId == currentUserId)
                            }
                        }
                        .sortedBy { it.createdAt?.toEpochMilliseconds() ?: 0L },
                    pendingMessageCount = snapshot.pendingMessages.size,
                    syncCursor = snapshot.syncState.sinceId,
                )
                if (
                    mutableState.value.screen == Screen.CHAT &&
                    selectedContactId != null &&
                    (unreadCounts[selectedContactId] ?: 0) > 0
                ) {
                    scope.launch {
                        runCatching { messagesRepository.markConversationRead(selectedContactId) }
                        refreshCurrentConversation()
                    }
                }
            }
            .launchIn(scope)

        sessionStore.session
            .onEach { session ->
                val settings = settingsRepository.settings()
                mutableState.value = mutableState.value.copy(
                    platform = platformInfo.type,
                    platformName = platformInfo.displayName,
                    currentUser = session?.auth?.user,
                    admin = if (session?.auth?.user?.isAdmin == true) mutableState.value.admin else AdminState(),
                    screen = when {
                        mutableState.value.screen == Screen.SPLASH -> Screen.SPLASH
                        session != null && mutableState.value.screen in setOf(Screen.ONBOARDING, Screen.SETUP) -> Screen.CONTACTS
                        session?.auth?.user?.isAdmin == false && mutableState.value.screen == Screen.ADMIN -> Screen.CONTACTS
                        else -> mutableState.value.screen
                    },
                    onboarding = mutableState.value.onboarding.copy(
                        baseUrl = settings.serverBaseUrl,
                    ),
                    settings = SettingsState(
                        pollingEnabled = settings.pollingEnabled,
                        pushEnabled = settings.pushEnabled,
                        unlocked = session?.auth?.user?.isAdmin == true,
                    ),
                )
            }
            .launchIn(scope)

        scope.launch {
            val settings = settingsRepository.settings()
            mutableState.value = mutableState.value.copy(
                onboarding = mutableState.value.onboarding.copy(
                    baseUrl = settings.serverBaseUrl,
                ),
                settings = SettingsState(
                    pollingEnabled = settings.pollingEnabled,
                    pushEnabled = settings.pushEnabled,
                    unlocked = mutableState.value.currentUser?.isAdmin == true,
                ),
            )
            val restoredSession = sessionRepository.restore()
            val setupStatus = runCatching { loadSetupStatus() }
                .onSuccess { status ->
                    mutableState.value = mutableState.value.copy(
                        isSystemInitialized = status.initialized,
                    )
                    if (!status.initialized && platformInfo.type != PlatformType.WEB) {
                        mutableState.value = mutableState.value.copy(
                            statusMessage = uiText(Res.string.status_system_not_initialized),
                        )
                    }
                }
                .getOrNull()

            if (restoredSession == null) {
                mutableState.value = mutableState.value.copy(
                    screen = when {
                        platformInfo.type == PlatformType.WEB && setupStatus?.initialized == false -> Screen.SETUP
                        else -> Screen.ONBOARDING
                    },
                )
                return@launch
            }

            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                currentUser = restoredSession.auth.user,
                contacts = contactsRepository.cachedContacts(),
                unreadCounts = emptyMap(),
                errorMessage = null,
            )
            runCatching {
                val session = sessionRepository.refreshSessionFromServer()
                val contacts = loadContacts()
                syncEngine.start(scope)
                mutableState.value = mutableState.value.copy(
                    screen = Screen.CONTACTS,
                    currentUser = session.auth.user,
                    contacts = contacts,
                    unreadCounts = emptyMap(),
                    errorMessage = null,
                )
            }.onFailure { error ->
                when (error) {
                    is AppException.Unauthorized -> {
                        sessionRepository.logout()
                        syncEngine.stop()
                        mutableState.value = mutableState.value.copy(
                            screen = fallbackLoggedOutScreen(),
                            currentUser = null,
                            contacts = emptyList(),
                            unreadCounts = emptyMap(),
                            messages = emptyList(),
                            selectedContactId = null,
                            selectedContactName = null,
                            draftMessage = "",
                            statusMessage = null,
                            errorMessage = uiText(Res.string.error_session_expired),
                        )
                    }

                    is AppException.Network,
                    is AppException.Server,
                    -> {
                        syncEngine.stop()
                        mutableState.value = mutableState.value.copy(
                            screen = Screen.CONTACTS,
                            currentUser = restoredSession.auth.user,
                            contacts = contactsRepository.cachedContacts(),
                            errorMessage = uiText(Res.string.error_server_unavailable_local_session),
                        )
                    }

                    else -> {
                        syncEngine.stop()
                        mutableState.value = mutableState.value.copy(
                            screen = Screen.CONTACTS,
                            currentUser = restoredSession.auth.user,
                            contacts = contactsRepository.cachedContacts(),
                            errorMessage = error.message?.let(UiText::Dynamic)
                                ?: uiText(Res.string.error_refresh_session_failed_local_session),
                        )
                    }
                }
            }
        }
    }

    fun updateBaseUrl(value: String) = mutate { copy(onboarding = onboarding.copy(baseUrl = value)) }

    fun updateInviteCode(value: String) = mutate { copy(onboarding = onboarding.copy(inviteCode = value)) }

    fun onSetupComplete() = mutate {
        copy(
            screen = Screen.ONBOARDING,
            isSystemInitialized = true,
            onboarding = onboarding.copy(inviteCode = ""),
        )
    }

    fun updateDraftMessage(value: String) = mutate { copy(draftMessage = value) }

    fun submitAuth() {
        runBusy {
            val onboarding = state.value.onboarding
            settingsRepository.updateServerBaseUrl(onboarding.baseUrl)
            val session = login(onboarding.inviteCode)
            val contacts = loadContacts()
            syncEngine.start(scope)
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                currentUser = session.auth.user,
                contacts = contacts,
                unreadCounts = emptyMap(),
                errorMessage = null,
                statusMessage = uiText(Res.string.status_session_active),
            )
        }
    }

    fun submitScannedAuth(baseUrl: String, inviteCode: String) {
        mutate {
            copy(
                onboarding = onboarding.copy(
                    baseUrl = baseUrl,
                    inviteCode = inviteCode,
                ),
                errorMessage = null,
            )
        }
        runBusy {
            settingsRepository.updateServerBaseUrl(baseUrl)
            val session = login(inviteCode)
            val contacts = loadContacts()
            syncEngine.start(scope)
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                currentUser = session.auth.user,
                contacts = contacts,
                unreadCounts = emptyMap(),
                errorMessage = null,
                statusMessage = uiText(Res.string.status_session_active),
            )
        }
    }

    fun refreshContacts() {
        runBusy {
            val contacts = loadContacts()
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                contacts = contacts,
                unreadCounts = emptyMap(),
                selectedContactId = null,
                selectedContactName = null,
                messages = emptyList(),
                pendingMessageCount = messagesRepository.pendingCount(),
                syncCursor = messagesRepository.syncCursor(),
            )
        }
    }

    fun openChat(contact: ContactSummary) {
        mutableState.value = mutableState.value.copy(
            screen = Screen.CHAT,
            selectedContactId = contact.user.id,
            selectedContactName = contact.user.displayName,
            draftMessage = "",
        )
        scope.launch {
            val messages = messagesRepository.conversation(contact.user.id)
            mutableState.value = mutableState.value.copy(
                messages = messages,
            )
            scope.launch {
                runCatching { messagesRepository.markConversationRead(contact.user.id) }
            }
        }
    }

    fun backToContacts() = mutate {
        copy(
            screen = Screen.CONTACTS,
            selectedContactId = null,
            selectedContactName = null,
            settings = settings.copy(
                unlocked = currentUser?.isAdmin == true,
                masterPassword = "",
            ),
        )
    }

    fun openSettings() = mutate {
        copy(
            screen = Screen.SETTINGS,
            settings = settings.copy(
                unlocked = currentUser?.isAdmin == true,
                masterPassword = "",
            ),
            errorMessage = null,
            statusMessage = null,
        )
    }

    fun openAdmin() = mutate { copy(screen = Screen.ADMIN, errorMessage = null, statusMessage = null) }

    fun updateSettingsMasterPassword(value: String) = mutate {
        copy(settings = settings.copy(masterPassword = value))
    }

    fun unlockSettings() {
        if (state.value.currentUser?.isAdmin == true) return
        runBusy {
            verifyMasterPassword(state.value.settings.masterPassword)
            mutableState.value = mutableState.value.copy(
                screen = Screen.SETTINGS,
                settings = mutableState.value.settings.copy(
                    unlocked = true,
                    masterPassword = "",
                ),
                errorMessage = null,
                statusMessage = uiText(Res.string.status_admin_access_granted),
            )
        }
    }

    fun updateAdminMasterPassword(value: String) = mutate { copy(admin = admin.copy(masterPassword = value)) }

    fun updateAdminNewMemberName(value: String) = mutate { copy(admin = admin.copy(newMemberName = value)) }

    fun updateAdminNewMemberRole(role: UserRole) = mutate {
        copy(admin = admin.copy(newMemberRole = role, newMemberIsAdmin = if (role == UserRole.PARENT) admin.newMemberIsAdmin else false))
    }

    fun updateAdminNewMemberIsAdmin(value: Boolean) = mutate {
        copy(admin = admin.copy(newMemberIsAdmin = if (admin.newMemberRole == UserRole.PARENT) value else false))
    }

    fun unlockAdmin() {
        runBusy {
            val response = verifyAdminAccess(state.value.admin.masterPassword)
            mutableState.value = mutableState.value.copy(
                screen = Screen.ADMIN,
                admin = mutableState.value.admin.copy(
                    unlocked = true,
                    members = response.members,
                ),
                errorMessage = null,
                statusMessage = uiText(Res.string.status_admin_access_granted),
            )
        }
    }

    fun createAdminMember() {
        runBusy {
            val admin = state.value.admin
            val response = createMember(admin.masterPassword, admin.newMemberName, admin.newMemberRole, admin.newMemberIsAdmin)
            mutableState.value = mutableState.value.copy(
                admin = admin.copy(
                    unlocked = true,
                    newMemberName = "",
                    newMemberRole = UserRole.CHILD,
                    newMemberIsAdmin = false,
                    members = admin.members + response.member,
                ),
                errorMessage = null,
                statusMessage = uiText(Res.string.status_invite_created),
            )
        }
    }

    fun removeAdminMember(inviteCode: String) {
        runBusy {
            val admin = state.value.admin
            val response = removeMember(admin.masterPassword, inviteCode)
            val contacts = runCatching { loadContacts() }.getOrElse { state.value.contacts }
            val selectedContactStillExists = state.value.selectedContactId?.let { selectedId ->
                contacts.any { it.user.id == selectedId }
            } ?: false
            mutableState.value = mutableState.value.copy(
                admin = admin.copy(
                    unlocked = true,
                    members = response.members,
                ),
                contacts = contacts,
                selectedContactId = state.value.selectedContactId?.takeIf { selectedContactStillExists },
                selectedContactName = state.value.selectedContactName?.takeIf { selectedContactStillExists },
                messages = if (selectedContactStillExists) state.value.messages else emptyList(),
                errorMessage = null,
                statusMessage = uiText(Res.string.status_member_removed),
            )
        }
    }

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
                statusMessage = if (ok) uiText(Res.string.status_location_sent) else uiText(Res.string.status_location_unavailable),
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
            mutableState.value = mutableState.value.copy(
                settings = mutableState.value.settings.copy(pollingEnabled = enabled),
            )
        }
    }

    fun updatePushEnabled(enabled: Boolean) {
        runBusy {
            settingsRepository.updatePushEnabled(enabled)
            mutableState.value = mutableState.value.copy(
                settings = mutableState.value.settings.copy(pushEnabled = enabled),
            )
        }
    }

    fun saveBaseUrl() {
        runBusy {
            settingsRepository.updateServerBaseUrl(state.value.onboarding.baseUrl)
            mutableState.value = mutableState.value.copy(statusMessage = uiText(Res.string.status_base_url_saved))
        }
    }

    fun logout() {
        runBusy {
            sessionRepository.logout()
            syncEngine.stop()
            mutableState.value = mutableState.value.copy(
                screen = fallbackLoggedOutScreen(),
                currentUser = null,
                onboarding = mutableState.value.onboarding.copy(inviteCode = ""),
                contacts = emptyList(),
                unreadCounts = emptyMap(),
                messages = emptyList(),
                selectedContactId = null,
                selectedContactName = null,
                draftMessage = "",
                admin = AdminState(),
                settings = SettingsState(),
                statusMessage = uiText(Res.string.status_session_cleared),
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

    private fun fallbackLoggedOutScreen(): Screen =
        if (platformInfo.type == PlatformType.WEB && mutableState.value.isSystemInitialized == false) Screen.SETUP else Screen.ONBOARDING

    private fun runBusy(block: suspend () -> Unit) {
        scope.launch {
            mutableState.value = mutableState.value.copy(isBusy = true, errorMessage = null)
            runCatching { block() }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        errorMessage = error.message?.let(UiText::Dynamic) ?: uiText(Res.string.error_operation_failed),
                    )
                }
            mutableState.value = mutableState.value.copy(isBusy = false)
        }
    }

    private fun mutate(transform: AppUiState.() -> AppUiState) {
        mutableState.value = mutableState.value.transform()
    }
}

private fun com.familymessenger.contract.MessagePayload.isIncomingFor(currentUserId: Long): Boolean =
    senderUserId != currentUserId &&
        (recipientUserId == currentUserId || recipientUserId == FAMILY_GROUP_CHAT_ID)

private fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { currentIndex, item -> if (currentIndex == index) transform(item) else item }
