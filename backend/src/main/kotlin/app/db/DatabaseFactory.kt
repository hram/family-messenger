package app.db

import app.config.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class DatabaseFactory(
    private val config: DatabaseConfig,
) {
    fun connectAndBootstrap() {
        Database.connect(
            url = config.jdbcUrl,
            driver = config.driver,
            user = config.user,
            password = config.password,
        )

        if (config.bootstrapSchema) {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    FamiliesTable,
                    UsersTable,
                    DevicesTable,
                    InvitesTable,
                    MessagesTable,
                    MessageReceiptsTable,
                    LocationEventsTable,
                    AuthTokensTable,
                    SyncEventsTable,
                )

                exec("DROP INDEX IF EXISTS ux_devices_family_name_platform")
                exec("ALTER TABLE devices DROP COLUMN IF EXISTS device_name")
                exec("ALTER TABLE invites ADD COLUMN IF NOT EXISTS user_id BIGINT")
                exec("CREATE INDEX IF NOT EXISTS idx_invites_user_id ON invites(user_id)")
                exec(
                    """
                    UPDATE invites AS i
                    SET user_id = chosen.id,
                        uses_count = 1,
                        max_uses = 1
                    FROM (
                        SELECT
                            MIN(u.id) AS id,
                            u.family_id,
                            u.display_name,
                            u.role
                        FROM users u
                        WHERE u.is_active = TRUE
                        GROUP BY u.family_id, u.display_name, u.role
                    ) AS chosen
                    WHERE i.user_id IS NULL
                      AND i.family_id = chosen.family_id
                      AND i.display_name = chosen.display_name
                      AND i.role = chosen.role
                    """.trimIndent(),
                )
                exec(
                    """
                    UPDATE users AS u
                    SET is_active = FALSE
                    FROM invites AS i
                    WHERE i.user_id IS NOT NULL
                      AND u.family_id = i.family_id
                      AND u.display_name = i.display_name
                      AND u.role = i.role
                      AND u.id <> i.user_id
                    """.trimIndent(),
                )
            }
        }

        if (config.seedOnStart) {
            transaction {
                if (FamiliesTable.selectAll().empty()) {
                    val familyId = FamiliesTable.insert {
                        it[name] = "Demo Family"
                        it[createdAt] = now()
                    }[FamiliesTable.id]

                    InvitesTable.insertIgnore {
                        it[InvitesTable.familyId] = familyId
                        it[code] = "PARENT-DEMO"
                        it[userId] = null
                        it[role] = "PARENT"
                        it[displayName] = "Parent"
                        it[isActive] = true
                        it[maxUses] = 1
                        it[usesCount] = 0
                        it[createdAt] = now()
                        it[expiresAt] = null
                    }

                    InvitesTable.insertIgnore {
                        it[InvitesTable.familyId] = familyId
                        it[code] = "CHILD-DEMO"
                        it[userId] = null
                        it[role] = "CHILD"
                        it[displayName] = "Child"
                        it[isActive] = true
                        it[maxUses] = 1
                        it[usesCount] = 0
                        it[createdAt] = now()
                        it[expiresAt] = null
                    }
                }
            }
        }
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
