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
                        it[role] = "CHILD"
                        it[displayName] = "Child"
                        it[isActive] = true
                        it[maxUses] = 3
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
