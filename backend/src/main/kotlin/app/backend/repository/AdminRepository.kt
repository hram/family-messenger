package app.backend.repository

import app.backend.model.SessionPrincipal
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import kotlinx.datetime.Instant

interface AdminRepository {
    suspend fun masterPasswordHash(familyId: Long): String?
    suspend fun listMembers(principal: SessionPrincipal): AdminMembersResponse
    suspend fun createMember(
        principal: SessionPrincipal,
        displayName: String,
        role: String,
        isAdmin: Boolean,
        now: Instant,
    ): AdminCreateMemberResponse
    suspend fun removeMember(principal: SessionPrincipal, inviteCode: String, now: Instant): AdminMembersResponse
}
