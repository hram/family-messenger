package app.backend.service

import app.backend.error.ForbiddenException
import app.backend.error.ValidationException
import app.backend.model.SessionPrincipal
import app.backend.repository.AdminRepository
import com.familymessenger.contract.AdminCreateMemberRequest
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.AdminRemoveMemberRequest
import com.familymessenger.contract.VerifyAdminAccessRequest
import kotlinx.datetime.Clock
import org.mindrot.jbcrypt.BCrypt

class AdminService(
    private val repository: AdminRepository,
) {
    suspend fun verifyAccess(principal: SessionPrincipal, request: VerifyAdminAccessRequest): AdminMembersResponse {
        validateAdminPrincipal(principal)
        validateMasterPassword(request.masterPassword)
        verifyMasterPassword(principal.familyId, request.masterPassword)
        return repository.listMembers(principal)
    }

    suspend fun createMember(principal: SessionPrincipal, request: AdminCreateMemberRequest): AdminCreateMemberResponse {
        validateAdminPrincipal(principal)
        validateMasterPassword(request.masterPassword)
        verifyMasterPassword(principal.familyId, request.masterPassword)
        validateMemberDraft(request.displayName, request.role, request.isAdmin)
        return repository.createMember(principal, request.displayName.trim(), request.role.name, request.isAdmin, Clock.System.now())
    }

    suspend fun removeMember(principal: SessionPrincipal, request: AdminRemoveMemberRequest): AdminMembersResponse {
        validateAdminPrincipal(principal)
        validateMasterPassword(request.masterPassword)
        verifyMasterPassword(principal.familyId, request.masterPassword)
        if (request.inviteCode.isBlank()) {
            throw ValidationException("inviteCode is required")
        }
        return repository.removeMember(principal, request.inviteCode, Clock.System.now())
    }

    private suspend fun verifyMasterPassword(familyId: Long, masterPassword: String) {
        val hash = repository.masterPasswordHash(familyId) ?: throw ForbiddenException("Master password is not configured")
        if (!BCrypt.checkpw(masterPassword, hash)) {
            throw ForbiddenException("Invalid master password")
        }
    }
}
