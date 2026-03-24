package app.backend.repository.impl

import app.backend.db.FamiliesTable
import app.backend.db.InvitesTable
import app.backend.db.SystemSetupTable
import app.backend.db.dbQuery
import app.backend.error.ConflictException
import app.backend.repository.SetupRepository
import com.familymessenger.contract.FamilySummary
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupInviteSummary
import com.familymessenger.contract.SetupMemberDraft
import com.familymessenger.contract.SetupStatusResponse
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class ExposedSetupRepository : SetupRepository {
    private val inviteCodeGenerator = InviteCodeGenerator()

    override suspend fun status(): SetupStatusResponse = dbQuery {
        val setupRow = SystemSetupTable.selectAll().where { SystemSetupTable.id eq 1 }.singleOrNull()
        val familyName = setupRow?.get(SystemSetupTable.familyId)?.let { familyId ->
            FamiliesTable.selectAll().where { FamiliesTable.id eq familyId }.singleOrNull()?.get(FamiliesTable.name)
        }
        SetupStatusResponse(
            initialized = setupRow != null,
            familyName = familyName,
        )
    }

    override suspend fun bootstrap(
        masterPasswordHash: String,
        familyName: String,
        members: List<SetupMemberDraft>,
        now: Instant,
    ): SetupBootstrapResponse = dbQuery {
        if (SystemSetupTable.selectAll().where { SystemSetupTable.id eq 1 }.singleOrNull() != null) {
            throw ConflictException("System is already initialized")
        }

        val familyId = FamiliesTable.insert {
            it[name] = familyName
            it[createdAt] = now
        }[FamiliesTable.id]

        val invites = members.map { member ->
            val inviteCode = inviteCodeGenerator.nextUniqueCode(exists = { code: String ->
                InvitesTable.selectAll().where { InvitesTable.code eq code }.singleOrNull() != null
            })
            InvitesTable.insert {
                it[InvitesTable.familyId] = familyId
                it[code] = inviteCode
                it[userId] = null
                it[role] = member.role.name
                it[isAdmin] = member.isAdmin
                it[displayName] = member.displayName
                it[isActive] = true
                it[maxUses] = 1
                it[usesCount] = 0
                it[createdAt] = now
                it[expiresAt] = null
            }
            SetupInviteSummary(
                displayName = member.displayName,
                role = member.role,
                isAdmin = member.isAdmin,
                inviteCode = inviteCode,
            )
        }

        SystemSetupTable.insert {
            it[id] = 1
            it[SystemSetupTable.familyId] = familyId
            it[SystemSetupTable.masterPasswordHash] = masterPasswordHash
            it[initializedAt] = now
        }

        SetupBootstrapResponse(
            family = FamilySummary(id = familyId, name = familyName),
            invites = invites,
        )
    }
}
