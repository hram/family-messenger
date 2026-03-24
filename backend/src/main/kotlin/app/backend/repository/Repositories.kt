package app.backend.repository

import app.backend.model.SessionPrincipal
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ClientLogEntry
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupMemberDraft
import com.familymessenger.contract.SetupStatusResponse
import com.familymessenger.contract.SyncPayload
import kotlinx.datetime.Instant

interface AuthRepository {
    suspend fun login(
        inviteCode: String,
        platform: String,
        pushToken: String?,
        tokenHash: String,
        rawToken: String,
        expiresAt: Instant,
        now: Instant,
    ): AuthPayload

    suspend fun resolveSession(tokenHash: String, now: Instant): SessionPrincipal?
}

interface ProfileRepository {
    suspend fun getProfile(userId: Long, familyId: Long): ProfileResponse
    suspend fun getContacts(currentUserId: Long, familyId: Long, now: Instant): List<ContactSummary>
    suspend fun getDisplayName(userId: Long, familyId: Long): String?
}

interface MessageRepository {
    suspend fun sendMessage(
        principal: SessionPrincipal,
        recipientUserId: Long,
        clientMessageUuid: String,
        type: String,
        body: String?,
        quickActionCode: String?,
        location: LocationPayload?,
        now: Instant,
    ): MessagePayload

    suspend fun sync(principal: SessionPrincipal, sinceId: Long, limit: Int = 500): SyncPayload
    suspend fun markDelivered(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean
    suspend fun markRead(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean
}

interface PresenceRepository {
    suspend fun ping(principal: SessionPrincipal, now: Instant)
    suspend fun shareLocation(principal: SessionPrincipal, latitude: Double, longitude: Double, accuracy: Double?, label: String?, now: Instant)
}

interface DeviceRepository {
    suspend fun updatePushToken(principal: SessionPrincipal, pushToken: String?, now: Instant)
    suspend fun getPushTokensForUsers(familyId: Long, userIds: List<Long>): List<String>
    suspend fun getPushTokensForFamily(familyId: Long, excludeUserId: Long): List<String>
}

interface ClientLogRepository {
    suspend fun store(principal: SessionPrincipal, entries: List<ClientLogEntry>, now: Instant): Int
}

interface SetupRepository {
    suspend fun status(): SetupStatusResponse
    suspend fun bootstrap(
        masterPasswordHash: String,
        familyName: String,
        members: List<SetupMemberDraft>,
        now: Instant,
    ): SetupBootstrapResponse
}

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
