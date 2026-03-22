package app

import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.UserProfile

enum class Screen {
    ONBOARDING,
    CONTACTS,
    CHAT,
    SETTINGS,
}

enum class AuthMode {
    REGISTER,
    LOGIN,
}

data class OnboardingFormState(
    val baseUrl: String = "",
    val inviteCode: String = "",
    val authMode: AuthMode = AuthMode.REGISTER,
)

data class SettingsState(
    val pollingEnabled: Boolean = true,
    val pushEnabled: Boolean = false,
)

data class AppUiState(
    val screen: Screen = Screen.ONBOARDING,
    val platform: PlatformType = PlatformType.ANDROID,
    val platformName: String = "",
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val onboarding: OnboardingFormState = OnboardingFormState(),
    val settings: SettingsState = SettingsState(),
    val currentUser: UserProfile? = null,
    val contacts: List<ContactSummary> = emptyList(),
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
