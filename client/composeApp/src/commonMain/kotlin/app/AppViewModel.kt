package app

import app.dto.LocalDatabaseSnapshot
import app.dto.StoredSession
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
import app.ui.SetupFormState
import app.ui.SetupMemberInputState
import app.ui.SettingsState
import app.usecase.BootstrapSystemUseCase
import app.usecase.CreateMemberUseCase
import app.usecase.LoadContactsUseCase
import app.usecase.LoadSetupStatusUseCase
import app.usecase.LoginUseCase
import app.usecase.RemoveMemberUseCase
import app.usecase.SendQuickActionUseCase
import app.usecase.SendTextMessageUseCase
import app.usecase.ShareLocationUseCase
import app.usecase.VerifyAdminAccessUseCase
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.UserRole
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
    private val bootstrapSystem: BootstrapSystemUseCase,
    private val verifyAdminAccess: VerifyAdminAccessUseCase,
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
                        .filter { it.senderUserId != currentUserId }
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
                        session == null && platformInfo.type == PlatformType.WEB && mutableState.value.setup.isInitialized == false -> Screen.SETUP
                        session == null -> Screen.ONBOARDING
                        session?.auth?.user?.isAdmin == false && mutableState.value.screen == Screen.ADMIN -> Screen.CONTACTS
                        else -> mutableState.value.screen.takeIf { it != Screen.ONBOARDING && it != Screen.SETUP } ?: Screen.CONTACTS
                    },
                    onboarding = mutableState.value.onboarding.copy(
                        baseUrl = settings.serverBaseUrl,
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
                ),
                settings = SettingsState(settings.pollingEnabled, settings.pushEnabled),
            )
            runCatching { loadSetupStatus() }
                .onSuccess { setupStatus ->
                    mutableState.value = mutableState.value.copy(
                        setup = mutableState.value.setup.copy(isInitialized = setupStatus.initialized),
                        screen = when {
                            sessionRepository.restore() == null && platformInfo.type == PlatformType.WEB && !setupStatus.initialized -> Screen.SETUP
                            else -> mutableState.value.screen
                        },
                    )
                    if (!setupStatus.initialized && platformInfo.type != PlatformType.WEB) {
                        mutableState.value = mutableState.value.copy(
                            statusMessage = "System is not initialized yet. Complete setup in the web client.",
                        )
                    }
                }
            if (sessionRepository.restore() != null) {
                runCatching {
                    val session = sessionRepository.refreshSessionFromServer()
                    syncEngine.start(scope)
                    val contacts = contactsRepository.refreshContacts()
                    mutableState.value = mutableState.value.copy(
                        screen = Screen.CONTACTS,
                        currentUser = session.auth.user,
                        contacts = contacts,
                        unreadCounts = emptyMap(),
                        errorMessage = null,
                    )
                }.onFailure {
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
                    )
                }
            }
        }
    }

    fun updateBaseUrl(value: String) = mutate { copy(onboarding = onboarding.copy(baseUrl = value)) }

    fun updateInviteCode(value: String) = mutate { copy(onboarding = onboarding.copy(inviteCode = value)) }

    fun updateSetupMasterPassword(value: String) = mutate { copy(setup = setup.copy(masterPassword = value)) }

    fun updateSetupMasterPasswordConfirm(value: String) = mutate { copy(setup = setup.copy(masterPasswordConfirm = value)) }

    fun updateSetupFamilyName(value: String) = mutate { copy(setup = setup.copy(familyName = value)) }

    fun updateSetupMemberName(index: Int, value: String) = mutate {
        copy(setup = setup.copy(members = setup.members.updated(index) { it.copy(displayName = value) }))
    }

    fun updateSetupMemberRole(index: Int, role: UserRole) = mutate {
        copy(
            setup = setup.copy(
                members = setup.members.updated(index) {
                    it.copy(
                        role = role,
                        isAdmin = if (role == UserRole.PARENT) it.isAdmin else false,
                    )
                },
            ),
        )
    }

    fun updateSetupMemberAdmin(index: Int, isAdmin: Boolean) = mutate {
        copy(
            setup = setup.copy(
                members = setup.members.updated(index) {
                    if (it.role == UserRole.PARENT) it.copy(isAdmin = isAdmin) else it.copy(isAdmin = false)
                },
            ),
        )
    }

    fun addSetupMember() = mutate {
        copy(setup = setup.copy(members = setup.members + SetupMemberInputState()))
    }

    fun removeSetupMember(index: Int) = mutate {
        copy(
            setup = setup.copy(
                members = if (setup.members.size <= 1) {
                    setup.members
                } else {
                    setup.members.filterIndexed { currentIndex, _ -> currentIndex != index }
                },
            ),
        )
    }

    fun proceedFromSetupPasswordStep() {
        val setup = state.value.setup
        when {
            setup.masterPassword.isBlank() -> mutate { copy(errorMessage = "Master password is required") }
            setup.masterPasswordConfirm.isBlank() -> mutate { copy(errorMessage = "Please confirm the master password") }
            setup.masterPassword != setup.masterPasswordConfirm -> mutate { copy(errorMessage = "Master password confirmation does not match") }
            else -> mutate { copy(setup = setup.copy(step = 2), errorMessage = null) }
        }
    }

    fun goToSetupStep(step: Int) = mutate { copy(setup = setup.copy(step = step.coerceIn(1, 3))) }

    fun updateDraftMessage(value: String) = mutate { copy(draftMessage = value) }

    fun submitSetup() {
        runBusy {
            val setup = state.value.setup
            when {
                setup.masterPassword != setup.masterPasswordConfirm -> error("Master password confirmation does not match")
                setup.familyName.isBlank() -> error("Family name is required")
                setup.members.isEmpty() -> error("At least one member is required")
                setup.members.any { it.displayName.isBlank() } -> error("All members must have a name")
            }
            settingsRepository.updateServerBaseUrl(state.value.onboarding.baseUrl)
            val response = bootstrapSystem(
                masterPassword = setup.masterPassword,
                familyName = setup.familyName,
                members = setup.members,
            )
            mutableState.value = mutableState.value.copy(
                screen = Screen.SETUP,
                setup = setup.copy(
                    step = 3,
                    isInitialized = true,
                    generatedInvites = response.invites,
                    masterPassword = "",
                    masterPasswordConfirm = "",
                ),
                admin = AdminState(),
                statusMessage = "System initialized",
                errorMessage = null,
            )
        }
    }

    fun finishSetup() = mutate {
        copy(
            screen = Screen.ONBOARDING,
            setup = setup.copy(generatedInvites = emptyList()),
            onboarding = onboarding.copy(inviteCode = ""),
        )
    }

    fun submitAuth() {
        runBusy {
            val onboarding = state.value.onboarding
            settingsRepository.updateServerBaseUrl(onboarding.baseUrl)
            val session = login(onboarding.inviteCode)
            syncEngine.start(scope)
            val contacts = loadContacts()
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                currentUser = session.auth.user,
                contacts = contacts,
                unreadCounts = emptyMap(),
                errorMessage = null,
                statusMessage = "Сессия активна",
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
            syncEngine.start(scope)
            val contacts = loadContacts()
            mutableState.value = mutableState.value.copy(
                screen = Screen.CONTACTS,
                currentUser = session.auth.user,
                contacts = contacts,
                unreadCounts = emptyMap(),
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
                runCatching { messagesRepository.markConversationDelivered(contact.user.id) }
                runCatching { messagesRepository.markConversationRead(contact.user.id) }
            }
        }
    }

    fun backToContacts() = mutate { copy(screen = Screen.CONTACTS, selectedContactId = null, selectedContactName = null) }

    fun openSettings() = mutate { copy(screen = Screen.SETTINGS) }

    fun openAdmin() = mutate { copy(screen = Screen.ADMIN, errorMessage = null, statusMessage = null) }

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
                statusMessage = "Administrator access granted",
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
                statusMessage = "Family member invite created",
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
                statusMessage = "Family member removed",
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
                screen = fallbackLoggedOutScreen(),
                currentUser = null,
                contacts = emptyList(),
                unreadCounts = emptyMap(),
                messages = emptyList(),
                selectedContactId = null,
                selectedContactName = null,
                draftMessage = "",
                admin = AdminState(),
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

    private fun fallbackLoggedOutScreen(): Screen =
        if (platformInfo.type == PlatformType.WEB && mutableState.value.setup.isInitialized == false) Screen.SETUP else Screen.ONBOARDING

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

private fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { currentIndex, item -> if (currentIndex == index) transform(item) else item }
