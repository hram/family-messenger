package app.db

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object FamiliesTable : Table("families") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 120)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SystemSetupTable : Table("system_setup") {
    val id = integer("id")
    val familyId = long("family_id").nullable()
    val masterPasswordHash = varchar("master_password_hash", 255)
    val initializedAt = timestamp("initialized_at")
    override val primaryKey = PrimaryKey(id)
}

object UsersTable : Table("users") {
    val id = long("id").autoIncrement()
    val familyId = long("family_id").index()
    val displayName = varchar("display_name", 120)
    val role = varchar("role", 32)
    val isAdmin = bool("is_admin").default(false)
    val isActive = bool("is_active").default(true)
    val lastSeenAt = timestamp("last_seen_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object DevicesTable : Table("devices") {
    val id = long("id").autoIncrement()
    val familyId = long("family_id").index()
    val userId = long("user_id").index()
    val platform = varchar("platform", 32)
    val pushToken = varchar("push_token", 512).nullable()
    val lastSeenAt = timestamp("last_seen_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(familyId, userId, platform)
    }
}

object InvitesTable : Table("invites") {
    val id = long("id").autoIncrement()
    val familyId = long("family_id").index()
    val code = varchar("code", 64).uniqueIndex()
    val userId = long("user_id").nullable().index()
    val role = varchar("role", 32)
    val isAdmin = bool("is_admin").default(false)
    val displayName = varchar("display_name", 120)
    val isActive = bool("is_active").default(true)
    val maxUses = integer("max_uses").default(1)
    val usesCount = integer("uses_count").default(0)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object MessagesTable : Table("messages") {
    val id = long("id").autoIncrement()
    val familyId = long("family_id").index()
    val senderUserId = long("sender_user_id").index()
    val recipientUserId = long("recipient_user_id").index()
    val clientMessageUuid = varchar("client_message_uuid", 64)
    val type = varchar("type", 32)
    val body = text("body").nullable()
    val quickActionCode = varchar("quick_action_code", 64).nullable()
    val locationLatitude = double("location_latitude").nullable()
    val locationLongitude = double("location_longitude").nullable()
    val locationAccuracy = double("location_accuracy").nullable()
    val locationLabel = varchar("location_label", 128).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(senderUserId, clientMessageUuid)
        index(false, familyId, id)
        index(false, recipientUserId, id)
    }
}

object MessageReceiptsTable : Table("message_receipts") {
    val id = long("id").autoIncrement()
    val messageId = long("message_id").index()
    val userId = long("user_id").index()
    val deliveredAt = timestamp("delivered_at").nullable()
    val readAt = timestamp("read_at").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(messageId, userId)
        index(false, updatedAt)
    }
}

object LocationEventsTable : Table("location_events") {
    val id = long("id").autoIncrement()
    val familyId = long("family_id").index()
    val userId = long("user_id").index()
    val deviceId = long("device_id").index()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val accuracy = double("accuracy").nullable()
    val label = varchar("label", 128).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AuthTokensTable : Table("auth_tokens") {
    val id = long("id").autoIncrement()
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val userId = long("user_id").index()
    val deviceId = long("device_id").index()
    val familyId = long("family_id").index()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val lastUsedAt = timestamp("last_used_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SyncEventsTable : Table("sync_events") {
    val id = long("id").autoIncrement()
    val familyId = long("family_id").index()
    val entityType = varchar("entity_type", 32)
    val entityId = long("entity_id")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, familyId, id)
    }
}

fun now(): Instant = Clock.System.now()
