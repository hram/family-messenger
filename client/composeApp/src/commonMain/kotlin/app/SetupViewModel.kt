package app

import app.storage.ClientSettingsRepository
import app.ui.SetupMemberInputState
import app.ui.UiText
import app.ui.uiText
import app.usecase.BootstrapSystemUseCase
import com.familymessenger.composeapp.generated.resources.*
import com.familymessenger.contract.SetupInviteSummary
import com.familymessenger.contract.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val step: Int = 1,
    val serverUrl: String = "",
    val masterPassword: String = "",
    val masterPasswordConfirm: String = "",
    val familyName: String = "",
    val members: List<SetupMemberInputState> = listOf(
        SetupMemberInputState(role = UserRole.PARENT, isAdmin = true),
        SetupMemberInputState(role = UserRole.CHILD),
    ),
    val generatedInvites: List<SetupInviteSummary> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: UiText? = null,
)

class SetupViewModel(
    private val settingsRepository: ClientSettingsRepository,
    private val bootstrapSystem: BootstrapSystemUseCase,
    private val onFinished: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            val settings = settingsRepository.settings()
            mutate { copy(serverUrl = settings.serverBaseUrl) }
        }
    }

    fun updateServerUrl(value: String) = mutate { copy(serverUrl = value) }

    fun updateMasterPassword(value: String) = mutate { copy(masterPassword = value) }

    fun updateMasterPasswordConfirm(value: String) = mutate { copy(masterPasswordConfirm = value) }

    fun updateFamilyName(value: String) = mutate { copy(familyName = value) }

    fun updateMemberName(index: Int, value: String) = mutate {
        copy(members = members.updated(index) { it.copy(displayName = value) })
    }

    fun updateMemberRole(index: Int, role: UserRole) = mutate {
        copy(members = members.updated(index) {
            it.copy(role = role, isAdmin = if (role == UserRole.PARENT) it.isAdmin else false)
        })
    }

    fun updateMemberAdmin(index: Int, isAdmin: Boolean) = mutate {
        copy(members = members.updated(index) {
            if (it.role == UserRole.PARENT) it.copy(isAdmin = isAdmin) else it.copy(isAdmin = false)
        })
    }

    fun addMember() = mutate { copy(members = members + SetupMemberInputState()) }

    fun removeMember(index: Int) = mutate {
        copy(members = if (members.size <= 1) members else members.filterIndexed { i, _ -> i != index })
    }

    fun proceedFromPasswordStep() {
        val s = state.value
        when {
            s.masterPassword.isBlank() -> mutate { copy(errorMessage = uiText(Res.string.setup_error_master_password_required)) }
            s.masterPasswordConfirm.isBlank() -> mutate { copy(errorMessage = uiText(Res.string.setup_error_confirm_master_password_required)) }
            s.masterPassword != s.masterPasswordConfirm -> mutate { copy(errorMessage = uiText(Res.string.setup_error_password_mismatch)) }
            else -> mutate { copy(step = 2, errorMessage = null) }
        }
    }

    fun goToStep(step: Int) = mutate { copy(step = step.coerceIn(1, 3)) }

    fun submit() {
        runBusy {
            val s = state.value
            when {
                s.masterPassword != s.masterPasswordConfirm -> throw IllegalStateException(SetupError.PASSWORD_MISMATCH.name)
                s.familyName.isBlank() -> throw IllegalStateException(SetupError.FAMILY_NAME_REQUIRED.name)
                s.members.isEmpty() -> throw IllegalStateException(SetupError.MEMBER_REQUIRED.name)
                s.members.any { it.displayName.isBlank() } -> throw IllegalStateException(SetupError.MEMBER_NAME_REQUIRED.name)
            }
            settingsRepository.updateServerBaseUrl(s.serverUrl)
            val response = bootstrapSystem(
                masterPassword = s.masterPassword,
                familyName = s.familyName,
                members = s.members,
            )
            mutate {
                copy(
                    step = 3,
                    generatedInvites = response.invites,
                    masterPassword = "",
                    masterPasswordConfirm = "",
                    errorMessage = null,
                )
            }
        }
    }

    fun finish() {
        mutate { copy(generatedInvites = emptyList()) }
        onFinished()
    }

    fun clearError() = mutate { copy(errorMessage = null) }

    private fun runBusy(block: suspend () -> Unit) {
        scope.launch {
            mutate { copy(isBusy = true, errorMessage = null) }
            runCatching { block() }
                .onFailure { error ->
                    mutate {
                        copy(
                            errorMessage = when (error.message?.let { runCatching { SetupError.valueOf(it) }.getOrNull() }) {
                                SetupError.PASSWORD_MISMATCH -> uiText(Res.string.setup_error_password_mismatch)
                                SetupError.FAMILY_NAME_REQUIRED -> uiText(Res.string.setup_error_family_name_required)
                                SetupError.MEMBER_REQUIRED -> uiText(Res.string.setup_error_member_required)
                                SetupError.MEMBER_NAME_REQUIRED -> uiText(Res.string.setup_error_member_name_required)
                                null -> error.message?.let(UiText::Dynamic) ?: uiText(Res.string.error_operation_failed)
                            }
                        )
                    }
                }
            mutate { copy(isBusy = false) }
        }
    }

    private fun mutate(transform: SetupUiState.() -> SetupUiState) {
        mutableState.value = mutableState.value.transform()
    }
}

private enum class SetupError {
    PASSWORD_MISMATCH,
    FAMILY_NAME_REQUIRED,
    MEMBER_REQUIRED,
    MEMBER_NAME_REQUIRED,
}

private fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }
