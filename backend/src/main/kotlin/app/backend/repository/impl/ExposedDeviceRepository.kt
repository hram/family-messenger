package app.backend.repository.impl

import app.backend.db.DevicesTable
import app.backend.db.dbQuery
import app.backend.model.SessionPrincipal
import app.backend.repository.DeviceRepository
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedDeviceRepository : DeviceRepository {
    override suspend fun updatePushToken(principal: SessionPrincipal, pushToken: String?, now: Instant): Unit = dbQuery {
        DevicesTable.update({ DevicesTable.id eq principal.deviceId }) {
            it[DevicesTable.pushToken] = pushToken
            it[updatedAt] = now
        }
    }

    override suspend fun getPushTokensForUsers(familyId: Long, userIds: List<Long>): List<String> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyList()
        DevicesTable.selectAll().where {
            (DevicesTable.familyId eq familyId) and
                (DevicesTable.userId inList userIds) and
                DevicesTable.pushToken.isNotNull()
        }.mapNotNull { it[DevicesTable.pushToken] }
    }

    override suspend fun getPushTokensForFamily(familyId: Long, excludeUserId: Long): List<String> = dbQuery {
        DevicesTable.selectAll().where {
            (DevicesTable.familyId eq familyId) and
                (DevicesTable.userId neq excludeUserId) and
                DevicesTable.pushToken.isNotNull()
        }.mapNotNull { it[DevicesTable.pushToken] }
    }
}
