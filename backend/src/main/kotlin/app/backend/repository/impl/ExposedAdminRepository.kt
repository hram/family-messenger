package app.backend.repository.impl

import app.backend.db.AuthTokensTable
import app.backend.db.InvitesTable
import app.backend.db.SystemSetupTable
import app.backend.db.UsersTable
import app.backend.db.dbQuery
import app.backend.error.ConflictException
import app.backend.error.ForbiddenException
import app.backend.error.NotFoundException
import app.backend.model.SessionPrincipal
import app.backend.repository.AdminRepository
import com.familymessenger.contract.AdminCreateMemberResponse
import com.familymessenger.contract.AdminMemberSummary
import com.familymessenger.contract.AdminMembersResponse
import com.familymessenger.contract.UserRole
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ExposedAdminRepository : AdminRepository {
    private val inviteCodeGenerator = InviteCodeGenerator()

    override suspend fun masterPasswordHash(familyId: Long): String? = dbQuery {
        SystemSetupTable
            .selectAll()
            .where { SystemSetupTable.familyId eq familyId }
            .singleOrNull()
            ?.get(SystemSetupTable.masterPasswordHash)
    }

    override suspend fun listMembers(principal: SessionPrincipal): AdminMembersResponse = dbQuery {
        ensureAdmin(principal)
        AdminMembersResponse(members = memberRows(principal.familyId))
    }

    override suspend fun createMember(
        principal: SessionPrincipal,
        displayName: String,
        role: String,
        isAdmin: Boolean,
        now: Instant,
    ): AdminCreateMemberResponse = dbQuery {
        ensureAdmin(principal)
        val normalizedDisplayName = displayName.trim()
        val normalizedRole = role.uppercase()
        val duplicateExists = InvitesTable.selectAll().where {
            (InvitesTable.familyId eq principal.familyId) and
                (InvitesTable.role eq normalizedRole) and
                (InvitesTable.isActive eq true) and
                (InvitesTable.displayName eq normalizedDisplayName)
        }.singleOrNull() != null
        if (duplicateExists) {
            throw ConflictException("Family member with this display name already exists")
        }

        val inviteCode = inviteCodeGenerator.nextUniqueCode(exists = { code: String ->
            InvitesTable.selectAll().where { InvitesTable.code eq code }.singleOrNull() != null
        })
        InvitesTable.insert {
            it[InvitesTable.familyId] = principal.familyId
            it[code] = inviteCode
            it[userId] = null
            it[InvitesTable.role] = normalizedRole
            it[InvitesTable.isAdmin] = isAdmin
            it[InvitesTable.displayName] = normalizedDisplayName
            it[isActive] = true
            it[maxUses] = 1
            it[usesCount] = 0
            it[createdAt] = now
            it[expiresAt] = null
        }

        AdminCreateMemberResponse(
            member = AdminMemberSummary(
                inviteCode = inviteCode,
                userId = null,
                displayName = normalizedDisplayName,
                role = UserRole.valueOf(normalizedRole),
                isAdmin = isAdmin,
                isRegistered = false,
                isActive = true,
            ),
        )
    }

    override suspend fun removeMember(principal: SessionPrincipal, inviteCode: String, now: Instant): AdminMembersResponse = dbQuery {
        ensureAdmin(principal)
        val normalizedInviteCode = inviteCode.trim().uppercase()
        val invite = InvitesTable.selectAll().where {
            (InvitesTable.familyId eq principal.familyId) and
                (InvitesTable.code eq normalizedInviteCode)
        }.singleOrNull() ?: throw NotFoundException("Family member invite not found")

        if (invite[InvitesTable.isAdmin]) {
            val activeAdminCount = InvitesTable.selectAll().where {
                (InvitesTable.familyId eq principal.familyId) and
                    (InvitesTable.role eq UserRole.PARENT.name) and
                    (InvitesTable.isAdmin eq true) and
                    (InvitesTable.isActive eq true)
            }.count()
            if (activeAdminCount <= 1L) {
                throw ConflictException("At least one administrator must remain active")
            }
        }

        InvitesTable.update({ InvitesTable.id eq invite[InvitesTable.id] }) {
            it[isActive] = false
        }

        invite[InvitesTable.userId]?.let { childUserId ->
            UsersTable.update({ (UsersTable.id eq childUserId) and (UsersTable.familyId eq principal.familyId) }) {
                it[isActive] = false
            }
            AuthTokensTable.update({ AuthTokensTable.userId eq childUserId }) {
                it[revokedAt] = now
            }
        }

        AdminMembersResponse(members = memberRows(principal.familyId))
    }

    private fun memberRows(familyId: Long): List<AdminMemberSummary> =
        InvitesTable
            .selectAll()
            .where {
                (InvitesTable.familyId eq familyId) and
                    (InvitesTable.role neq UserRole.FAMILY.name)
            }
            .orderBy(InvitesTable.createdAt to SortOrder.ASC)
            .map { invite ->
                val boundUser = invite[InvitesTable.userId]?.let { userId ->
                    UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()
                }
                AdminMemberSummary(
                    inviteCode = invite[InvitesTable.code],
                    userId = invite[InvitesTable.userId],
                    displayName = invite[InvitesTable.displayName],
                    role = UserRole.valueOf(invite[InvitesTable.role]),
                    isAdmin = invite[InvitesTable.isAdmin],
                    isRegistered = invite[InvitesTable.userId] != null,
                    isActive = invite[InvitesTable.isActive] && (boundUser?.get(UsersTable.isActive) ?: true),
                )
            }

    private fun ensureAdmin(principal: SessionPrincipal) {
        if (!principal.isAdmin) {
            throw ForbiddenException("Administrator access required")
        }
    }
}
