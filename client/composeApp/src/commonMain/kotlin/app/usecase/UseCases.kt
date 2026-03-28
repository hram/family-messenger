package app.usecase

import app.dto.StoredSession
import app.repository.AdminRepository
import app.repository.ContactsRepository
import app.repository.MessagesRepository
import app.repository.PresenceRepository
import app.repository.SessionRepository
import app.repository.SetupRepository
import app.ui.SetupMemberInputState
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupStatusResponse
import com.familymessenger.contract.UserRole

class LoginUseCase(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(inviteCode: String): StoredSession = sessionRepository.login(inviteCode)
}

class LoadSetupStatusUseCase(
    private val setupRepository: SetupRepository,
) {
    suspend operator fun invoke(): SetupStatusResponse = setupRepository.status()
}

class BootstrapSystemUseCase(
    private val setupRepository: SetupRepository,
) {
    suspend operator fun invoke(
        masterPassword: String,
        familyName: String,
        members: List<SetupMemberInputState>,
    ): SetupBootstrapResponse = setupRepository.bootstrap(masterPassword, familyName, members)
}

class VerifyAdminAccessUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(masterPassword: String): AdminMembersResponse =
        adminRepository.verifyAccess(masterPassword)
}

class VerifyMasterPasswordUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(masterPassword: String): Boolean =
        adminRepository.verifyMasterPassword(masterPassword).accepted
}

class CreateMemberUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(
        masterPassword: String,
        displayName: String,
        role: UserRole,
        isAdmin: Boolean,
    ): AdminCreateMemberResponse = adminRepository.createMember(masterPassword, displayName, role, isAdmin)
}

class RemoveMemberUseCase(
    private val adminRepository: AdminRepository,
) {
    suspend operator fun invoke(masterPassword: String, inviteCode: String): AdminMembersResponse =
        adminRepository.removeMember(masterPassword, inviteCode)
}

class LoadContactsUseCase(
    private val contactsRepository: ContactsRepository,
) {
    suspend operator fun invoke(): List<ContactSummary> =
        runCatching { contactsRepository.refreshContacts() }
            .getOrElse { contactsRepository.cachedContacts() }
}

class SendTextMessageUseCase(
    private val messagesRepository: MessagesRepository,
) {
    suspend operator fun invoke(contactId: Long, body: String): MessagePayload =
        messagesRepository.queueTextMessage(contactId, body)
}

class SendQuickActionUseCase(
    private val messagesRepository: MessagesRepository,
) {
    suspend operator fun invoke(contactId: Long, code: QuickActionCode): MessagePayload =
        messagesRepository.queueQuickAction(contactId, code)
}

class ShareLocationUseCase(
    private val presenceRepository: PresenceRepository,
    private val messagesRepository: MessagesRepository,
) {
    suspend operator fun invoke(contactId: Long): Boolean {
        val location = presenceRepository.currentLocation() ?: return false
        messagesRepository.queueLocationMessage(contactId, location)
        runCatching { presenceRepository.recordLocationEvent(location) }
        return true
    }
}
