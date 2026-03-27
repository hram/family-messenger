package app.backend.repository.impl

import app.backend.db.AuthTokensTable
import app.backend.db.DevicesTable
import app.backend.db.FamiliesTable
import app.backend.db.InvitesTable
import app.backend.db.SystemSetupTable
import app.backend.db.UsersTable
import app.backend.db.dbQuery
import app.backend.error.ConflictException
import app.backend.error.UnauthorizedException
import app.backend.model.SessionPrincipal
import app.backend.repository.AuthRepository
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.DeviceSession
import com.familymessenger.contract.FamilySummary
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedAuthRepository : AuthRepository {
    override suspend fun login(
        inviteCode: String,
        platform: String,
        pushToken: String?,
        tokenHash: String,
        rawToken: String,
        expiresAt: Instant,
        now: Instant,
    ): AuthPayload = dbQuery {
        authenticateByInvite(
            inviteCode = inviteCode,
            platform = platform,
            pushToken = pushToken,
            tokenHash = tokenHash,
            rawToken = rawToken,
            expiresAt = expiresAt,
            now = now,
        )
    }

    override suspend fun resolveSession(tokenHash: String, now: Instant): SessionPrincipal? = dbQuery {
        val row = AuthTokensTable
            .join(UsersTable, JoinType.INNER, additionalConstraint = {
                AuthTokensTable.userId eq UsersTable.id
            })
            .join(DevicesTable, JoinType.INNER, additionalConstraint = {
                AuthTokensTable.deviceId eq DevicesTable.id
            })
            .selectAll()
            .where {
                (AuthTokensTable.tokenHash eq tokenHash) and
                    (AuthTokensTable.revokedAt.isNull()) and
                    (AuthTokensTable.expiresAt greater now) and
                    (UsersTable.isActive eq true) and
                    (DevicesTable.userId eq AuthTokensTable.userId) and
                    (DevicesTable.familyId eq AuthTokensTable.familyId)
            }
            .singleOrNull() ?: return@dbQuery null

        val tokenId = row[AuthTokensTable.id]
        val userId = row[AuthTokensTable.userId]
        val deviceId = row[AuthTokensTable.deviceId]
        val familyId = row[AuthTokensTable.familyId]
        val shouldTouchToken = row[AuthTokensTable.lastUsedAt]?.let {
            now.toEpochMilliseconds() - it.toEpochMilliseconds() >= 60_000
        } ?: true

        if (shouldTouchToken) {
            AuthTokensTable.update({ AuthTokensTable.id eq tokenId }) {
                it[lastUsedAt] = now
            }
        }
        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
        }
        UsersTable.update({ UsersTable.id eq userId }) {
            it[lastSeenAt] = now
        }

        SessionPrincipal(
            userId = userId,
            deviceId = deviceId,
            familyId = familyId,
            isAdmin = row[UsersTable.isAdmin],
        )
    }

    private fun findInvite(inviteCode: String): ResultRow {
        val normalized = inviteCode.trim().uppercase()
        return InvitesTable.selectAll().where {
            (InvitesTable.code eq normalized) and (InvitesTable.isActive eq true)
        }.singleOrNull() ?: throw UnauthorizedException("Invalid invite code")
    }

    private fun authenticateByInvite(
        inviteCode: String,
        platform: String,
        pushToken: String?,
        tokenHash: String,
        rawToken: String,
        expiresAt: Instant,
        now: Instant,
    ): AuthPayload {
        val invite = findInvite(inviteCode)

        if (invite[InvitesTable.expiresAt]?.let { it < now } == true) {
            throw ConflictException("Invite code has expired")
        }

        val familyId = invite[InvitesTable.familyId]
        val userId = invite[InvitesTable.userId] ?: UsersTable.insert {
            it[UsersTable.familyId] = familyId
            it[displayName] = invite[InvitesTable.displayName]
            it[role] = invite[InvitesTable.role]
            it[isAdmin] = invite[InvitesTable.isAdmin]
            it[isActive] = true
            it[lastSeenAt] = now
            it[createdAt] = now
        }[UsersTable.id].also { createdUserId ->
            InvitesTable.update({ InvitesTable.id eq invite[InvitesTable.id] }) {
                it[InvitesTable.userId] = createdUserId
                it[usesCount] = 1
                it[maxUses] = 1
            }
        }

        val userRow = UsersTable.selectAll().where {
            (UsersTable.id eq userId) and
                (UsersTable.familyId eq familyId) and
                (UsersTable.isActive eq true)
        }.singleOrNull() ?: throw UnauthorizedException("Invite code is bound to an inactive user")

        val deviceId = DevicesTable.selectAll().where {
            (DevicesTable.familyId eq familyId) and
                (DevicesTable.userId eq userId) and
                (DevicesTable.platform eq platform)
        }.orderBy(DevicesTable.updatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { existingDevice ->
                DevicesTable.update({ DevicesTable.id eq existingDevice[DevicesTable.id] }) {
                    it[lastSeenAt] = now
                    it[updatedAt] = now
                    if (pushToken != null) {
                        it[DevicesTable.pushToken] = pushToken
                    }
                }
                existingDevice[DevicesTable.id]
            }
            ?: DevicesTable.insert {
                it[DevicesTable.familyId] = familyId
                it[DevicesTable.userId] = userRow[UsersTable.id]
                it[DevicesTable.platform] = platform
                it[DevicesTable.pushToken] = pushToken
                it[lastSeenAt] = now
                it[createdAt] = now
                it[updatedAt] = now
            }[DevicesTable.id]

        UsersTable.update({ UsersTable.id eq userId }) {
            it[lastSeenAt] = now
        }

        persistToken(userId, deviceId, familyId, tokenHash, expiresAt, now)
        return buildAuthPayload(userId, familyId, rawToken, expiresAt)
    }

    private fun persistToken(
        userId: Long,
        deviceId: Long,
        familyId: Long,
        tokenHash: String,
        expiresAt: Instant,
        now: Instant,
    ) {
        AuthTokensTable.insert {
            it[AuthTokensTable.tokenHash] = tokenHash
            it[AuthTokensTable.userId] = userId
            it[AuthTokensTable.deviceId] = deviceId
            it[AuthTokensTable.familyId] = familyId
            it[AuthTokensTable.expiresAt] = expiresAt
            it[revokedAt] = null
            it[lastUsedAt] = now
            it[createdAt] = now
        }
    }

    private fun buildAuthPayload(userId: Long, familyId: Long, rawToken: String, expiresAt: Instant): AuthPayload {
        val familyRow = FamiliesTable.selectAll().where { FamiliesTable.id eq familyId }.single()
        val userRow = UsersTable.selectAll().where { UsersTable.id eq userId }.single()
        return AuthPayload(
            user = userRow.toUserProfile(),
            family = FamilySummary(
                id = familyId,
                name = familyRow[FamiliesTable.name],
            ),
            session = DeviceSession(
                token = rawToken,
                expiresAt = expiresAt,
            ),
            serverInstanceId = SystemSetupTable.selectAll().singleOrNull()?.get(SystemSetupTable.serverInstanceId).orEmpty(),
        )
    }
}
