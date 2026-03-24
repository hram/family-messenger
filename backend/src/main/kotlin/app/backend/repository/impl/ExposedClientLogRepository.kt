package app.backend.repository.impl

import app.backend.db.ClientLogsTable
import app.backend.db.dbQuery
import app.backend.model.SessionPrincipal
import app.backend.repository.ClientLogRepository
import com.familymessenger.contract.ClientLogEntry
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.insertIgnore

class ExposedClientLogRepository : ClientLogRepository {
    override suspend fun store(principal: SessionPrincipal, entries: List<ClientLogEntry>, now: Instant): Int = dbQuery {
        var stored = 0
        entries.forEach { entry ->
            val inserted = ClientLogsTable.insertIgnore {
                it[familyId] = principal.familyId
                it[userId] = principal.userId
                it[deviceId] = principal.deviceId
                it[eventId] = entry.eventId
                it[level] = entry.level.name
                it[tag] = entry.tag
                it[message] = entry.message
                it[details] = entry.details
                it[occurredAt] = entry.occurredAt
                it[createdAt] = now
            }
            if (inserted.insertedCount > 0) {
                stored += 1
            }
        }
        stored
    }
}
