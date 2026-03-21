package app

import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.PlatformType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen {
    ONBOARDING,
    CONTACTS,
    CHAT,
    SETTINGS,
}

data class AppUiState(
    val screen: Screen = Screen.ONBOARDING,
    val platform: PlatformType = PlatformType.ANDROID,
    val contacts: List<ContactSummary> = emptyList(),
    val selectedContactId: Long? = null,
    val messages: List<MessagePayload> = emptyList(),
    val serverBaseUrl: String = "http://10.0.2.2:8080",
)

class AppStateStore(initialPlatform: PlatformType) {
    private val mutableState = MutableStateFlow(AppUiState(platform = initialPlatform))
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    fun showContacts(contacts: List<ContactSummary>) {
        mutableState.value = mutableState.value.copy(
            screen = Screen.CONTACTS,
            contacts = contacts,
        )
    }

    fun openChat(contactId: Long, messages: List<MessagePayload>) {
        mutableState.value = mutableState.value.copy(
            screen = Screen.CHAT,
            selectedContactId = contactId,
            messages = messages,
        )
    }

    fun showSettings() {
        mutableState.value = mutableState.value.copy(screen = Screen.SETTINGS)
    }
}
