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
        appDatabase = Database.connect(
            url = config.jdbcUrl,
            driver = config.driver,
            user = config.user,
            password = config.password,
        )

        transaction {
            if (config.bootstrapSchema) {
                SchemaUtils.createMissingTablesAndColumns(
                    SystemSetupTable,
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
                exec("ALTER TABLE invites ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE")
                exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN DEFAULT FALSE")
                exec("ALTER TABLE system_setup ADD COLUMN IF NOT EXISTS family_id BIGINT")
                exec("ALTER TABLE system_setup ADD COLUMN IF NOT EXISTS master_password_hash VARCHAR(255)")
                exec("ALTER TABLE system_setup ADD COLUMN IF NOT EXISTS initialized_at TIMESTAMP")
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
                exec(
                    """
                    UPDATE invites
                    SET is_admin = TRUE
                    WHERE role = 'PARENT'
                      AND code = 'PARENT-DEMO'
                    """.trimIndent(),
                )
                exec(
                    """
                    UPDATE users
                    SET is_admin = TRUE
                    WHERE role = 'PARENT'
                      AND id = (
                        SELECT MIN(id) FROM users WHERE role = 'PARENT' AND is_active = TRUE
                      )
                      AND NOT EXISTS (
                        SELECT 1 FROM users WHERE role = 'PARENT' AND is_active = TRUE AND is_admin = TRUE
                      )
                    """.trimIndent(),
                )
                if (SystemSetupTable.selectAll().empty() && !FamiliesTable.selectAll().empty()) {
                    val familyId = FamiliesTable.selectAll().first()[FamiliesTable.id]
                    SystemSetupTable.insertIgnore {
                        it[id] = 1
                        it[SystemSetupTable.familyId] = familyId
                        it[masterPasswordHash] = "legacy-bootstrap-placeholder"
                        it[initializedAt] = now()
                    }
                }
            }

            if (config.seedOnStart) {
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
                        it[isAdmin] = true
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
                        it[isAdmin] = false
                        it[displayName] = "Child"
                        it[isActive] = true
                        it[maxUses] = 1
                        it[usesCount] = 0
                        it[createdAt] = now()
                        it[expiresAt] = null
                    }

                    SystemSetupTable.insertIgnore {
                        it[id] = 1
                        it[SystemSetupTable.familyId] = familyId
                        it[masterPasswordHash] = "seed-bootstrap-placeholder"
                        it[initializedAt] = now()
                    }
                }
            }
        }
    }
}

private var appDatabase: Database? = null

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(db = checkNotNull(appDatabase) { "Database is not initialized" }, context = Dispatchers.IO) {
        block()
    }
