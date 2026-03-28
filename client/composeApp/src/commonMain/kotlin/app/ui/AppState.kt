package app.ui

import com.familymessenger.contract.AdminMemberSummary
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.UserRole
import com.familymessenger.contract.UserProfile

enum class Screen {
    SPLASH,
    ONBOARDING,
    SETUP,
    CONTACTS,
    CHAT,
    SETTINGS,
    ADMIN,
}

data class OnboardingFormState(
    val baseUrl: String = "",
    val inviteCode: String = "",
)

data class SettingsState(
    val pollingEnabled: Boolean = true,
    val pushEnabled: Boolean = false,
    val unlocked: Boolean = false,
    val masterPassword: String = "",
)

data class SetupMemberInputState(
    val displayName: String = "",
    val role: UserRole = UserRole.CHILD,
    val isAdmin: Boolean = false,
)


data class AdminState(
    val unlocked: Boolean = false,
    val masterPassword: String = "",
    val members: List<AdminMemberSummary> = emptyList(),
    val newMemberName: String = "",
    val newMemberRole: UserRole = UserRole.CHILD,
    val newMemberIsAdmin: Boolean = false,
)

data class AppUiState(
    val screen: Screen = Screen.SPLASH,
    val platform: PlatformType = PlatformType.ANDROID,
    val platformName: String = "",
    val isBusy: Boolean = false,
    val errorMessage: UiText? = null,
    val statusMessage: UiText? = null,
    val isSystemInitialized: Boolean? = null,
    val onboarding: OnboardingFormState = OnboardingFormState(),
    val admin: AdminState = AdminState(),
    val settings: SettingsState = SettingsState(),
    val currentUser: UserProfile? = null,
    val contacts: List<ContactSummary> = emptyList(),
    val unreadCounts: Map<Long, Int> = emptyMap(),
    val selectedContactId: Long? = null,
    val selectedContactName: String? = null,
    val messages: List<MessagePayload> = emptyList(),
    val draftMessage: String = "",
    val syncCursor: Long = 0,
    val pendingMessageCount: Int = 0,
)

fun AppUiState.availableQuickActions(): List<QuickActionCode> = listOf(
    QuickActionCode.IM_OUT,
    QuickActionCode.AT_SCHOOL,
    QuickActionCode.PICK_ME_UP,
    QuickActionCode.ALL_OK,
)
