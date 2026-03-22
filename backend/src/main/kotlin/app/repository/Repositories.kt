package app.repository

import app.model.SessionPrincipal
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.ProfileResponse
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

    suspend fun sync(familyId: Long, sinceId: Long, limit: Int = 500): SyncPayload
    suspend fun markDelivered(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean
    suspend fun markRead(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean
}

interface PresenceRepository {
    suspend fun ping(principal: SessionPrincipal, now: Instant)
    suspend fun shareLocation(principal: SessionPrincipal, latitude: Double, longitude: Double, accuracy: Double?, label: String?, now: Instant)
}

interface DeviceRepository {
    suspend fun updatePushToken(principal: SessionPrincipal, pushToken: String?, now: Instant)
}
