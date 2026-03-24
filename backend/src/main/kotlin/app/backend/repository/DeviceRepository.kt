package app.backend.repository

import app.backend.model.SessionPrincipal
import kotlinx.datetime.Instant

interface DeviceRepository {
    suspend fun updatePushToken(principal: SessionPrincipal, pushToken: String?, now: Instant)
    suspend fun getPushTokensForUsers(familyId: Long, userIds: List<Long>): List<String>
    suspend fun getPushTokensForFamily(familyId: Long, excludeUserId: Long): List<String>
}
