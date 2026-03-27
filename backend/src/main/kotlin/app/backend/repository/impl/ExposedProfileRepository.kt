package app.backend.repository.impl

import app.backend.db.FamiliesTable
import app.backend.db.SystemSetupTable
import app.backend.db.UsersTable
import app.backend.db.dbQuery
import app.backend.error.NotFoundException
import app.backend.repository.ProfileRepository
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FamilySummary
import com.familymessenger.contract.ProfileResponse
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import kotlin.time.Duration.Companion.minutes

class ExposedProfileRepository : ProfileRepository {
    override suspend fun getProfile(userId: Long, familyId: Long): ProfileResponse = dbQuery {
        val userRow = UsersTable.selectAll().where {
            (UsersTable.id eq userId) and
                (UsersTable.familyId eq familyId) and
                (UsersTable.isActive eq true)
        }.singleOrNull() ?: throw NotFoundException("Profile not found")

        val familyRow = FamiliesTable.selectAll().where { FamiliesTable.id eq familyId }.single()
        ProfileResponse(
            user = userRow.toUserProfile(),
            family = FamilySummary(
                id = familyId,
                name = familyRow[FamiliesTable.name],
            ),
            serverInstanceId = SystemSetupTable.selectAll().singleOrNull()?.get(SystemSetupTable.serverInstanceId).orEmpty(),
        )
    }

    override suspend fun getDisplayName(userId: Long, familyId: Long): String? = dbQuery {
        UsersTable.selectAll().where {
            (UsersTable.id eq userId) and (UsersTable.familyId eq familyId)
        }.singleOrNull()?.get(UsersTable.displayName)
    }

    override suspend fun getContacts(currentUserId: Long, familyId: Long, now: Instant): List<ContactSummary> = dbQuery {
        val familyChat = FamiliesTable.selectAll().where { FamiliesTable.id eq familyId }
            .single()
            .toFamilyChatContact(familyId)

        val directContacts = UsersTable.selectAll().where {
            (UsersTable.familyId eq familyId) and
                (UsersTable.isActive eq true) and
                (UsersTable.id neq currentUserId)
        }.orderBy(UsersTable.displayName to SortOrder.ASC).map { row ->
            val lastSeenAt = row[UsersTable.lastSeenAt]
            ContactSummary(
                user = row.toUserProfile(),
                isOnline = lastSeenAt != null && (lastSeenAt >= (now - 2.minutes)),
            )
        }

        listOf(familyChat) + directContacts
    }
}
