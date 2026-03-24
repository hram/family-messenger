package app.backend.repository.impl

import app.backend.db.DevicesTable
import app.backend.db.LocationEventsTable
import app.backend.db.UsersTable
import app.backend.db.dbQuery
import app.backend.model.SessionPrincipal
import app.backend.repository.PresenceRepository
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update

class ExposedPresenceRepository : PresenceRepository {
    override suspend fun ping(principal: SessionPrincipal, now: Instant): Unit = dbQuery {
        DevicesTable.update({ DevicesTable.id eq principal.deviceId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
        }
        UsersTable.update({ UsersTable.id eq principal.userId }) {
            it[lastSeenAt] = now
        }
    }

    override suspend fun shareLocation(
        principal: SessionPrincipal,
        latitude: Double,
        longitude: Double,
        accuracy: Double?,
        label: String?,
        now: Instant,
    ): Unit = dbQuery {
        val eventId = LocationEventsTable.insert {
            it[familyId] = principal.familyId
            it[userId] = principal.userId
            it[deviceId] = principal.deviceId
            it[LocationEventsTable.latitude] = latitude
            it[LocationEventsTable.longitude] = longitude
            it[LocationEventsTable.accuracy] = accuracy
            it[LocationEventsTable.label] = label
            it[createdAt] = now
        }[LocationEventsTable.id]

        DevicesTable.update({ DevicesTable.id eq principal.deviceId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
        }
        UsersTable.update({ UsersTable.id eq principal.userId }) {
            it[lastSeenAt] = now
        }

        recordSyncEvent(principal.familyId, ENTITY_LOCATION, eventId, now)
    }
}
