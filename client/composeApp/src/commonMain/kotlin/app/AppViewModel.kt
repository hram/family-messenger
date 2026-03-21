package app

import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.PlatformType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class AppViewModel(
    initialPlatform: PlatformType,
    private val contactsRepository: ContactsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = AppStateStore(initialPlatform)

    val state: StateFlow<AppUiState> = store.state

    fun loadContacts() {
        scope.launch {
            val contacts = runCatching { contactsRepository.loadContacts() }.getOrElse {
                demoContacts()
            }
            store.showContacts(contacts)
        }
    }

    fun openChat(contact: ContactSummary) {
        store.openChat(
            contactId = contact.user.id,
            messages = listOf(
                MessagePayload(
                    id = 1,
                    clientMessageUuid = "demo-1",
                    familyId = contact.user.familyId,
                    senderUserId = contact.user.id,
                    recipientUserId = 1,
                    type = MessageType.TEXT,
                    body = "Step 1 chat scaffold",
                    status = MessageStatus.DELIVERED,
                    createdAt = Clock.System.now(),
                ),
            ),
        )
    }

    fun openSettings() = store.showSettings()

    private fun demoContacts(): List<ContactSummary> = listOf(
        ContactSummary(
            user = com.familymessenger.contract.UserProfile(
                id = 2,
                familyId = 1,
                displayName = "Child",
                role = com.familymessenger.contract.UserRole.CHILD,
                lastSeenAt = Clock.System.now(),
            ),
            isOnline = true,
        ),
    )
}
