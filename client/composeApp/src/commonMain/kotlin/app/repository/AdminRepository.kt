package app.repository

import app.network.FamilyMessengerApiClient
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.UserRole

class AdminRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun verifyMasterPassword(masterPassword: String): AckResponse =
        apiClient.verifyMasterPassword(masterPassword)

    suspend fun verifyAccess(masterPassword: String): AdminMembersResponse =
        apiClient.verifyAdminAccess(masterPassword)

    suspend fun createMember(
        masterPassword: String,
        displayName: String,
        role: UserRole,
        isAdmin: Boolean,
    ): AdminCreateMemberResponse = apiClient.createMember(masterPassword, displayName, role, isAdmin)

    suspend fun removeMember(masterPassword: String, inviteCode: String): AdminMembersResponse =
        apiClient.removeMember(masterPassword, inviteCode)
}
