package app.repository

import app.db.AuthTokensTable
import app.db.DevicesTable
import app.db.FamiliesTable
import app.db.InvitesTable
import app.db.LocationEventsTable
import app.db.MessageReceiptsTable
import app.db.MessagesTable
import app.db.SyncEventsTable
import app.db.UsersTable
import app.db.dbQuery
import app.error.ConflictException
import app.error.NotFoundException
import app.error.UnauthorizedException
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.DeviceSession
import com.familymessenger.contract.FamilySummary
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageReceiptPayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.SystemEventPayload
import com.familymessenger.contract.UserProfile
import com.familymessenger.contract.UserRole
import app.model.SessionPrincipal
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.time.Duration.Companion.minutes

private const val ENTITY_MESSAGE = "message"
private const val ENTITY_RECEIPT = "receipt"
private const val ENTITY_LOCATION = "location"

class ExposedAuthRepository : AuthRepository {
    override suspend fun registerDevice(
        inviteCode: String,
        deviceName: String,
        platform: String,
        pushToken: String?,
        tokenHash: String,
        rawToken: String,
        expiresAt: Instant,
        now: Instant,
    ): AuthPayload = dbQuery {
        val invite = findInvite(inviteCode)

        if (invite[InvitesTable.usesCount] >= invite[InvitesTable.maxUses]) {
            throw ConflictException("Invite code usage limit reached")
        }

        if (invite[InvitesTable.expiresAt]?.let { it < now } == true) {
            throw ConflictException("Invite code has expired")
        }

        val familyId = invite[InvitesTable.familyId]
        val existingDevice = DevicesTable.selectAll().where {
            (DevicesTable.familyId eq familyId) and
                (DevicesTable.deviceName eq deviceName) and
                (DevicesTable.platform eq platform)
        }.singleOrNull()

        if (existingDevice != null) {
            throw ConflictException(
                message = "Device is already registered",
                details = mapOf("deviceName" to deviceName, "platform" to platform),
            )
        }

        val userId = UsersTable.insert {
            it[UsersTable.familyId] = familyId
            it[displayName] = invite[InvitesTable.displayName]
            it[role] = invite[InvitesTable.role]
            it[isActive] = true
            it[lastSeenAt] = now
            it[createdAt] = now
        }[UsersTable.id]

        val deviceId = DevicesTable.insert {
            it[DevicesTable.familyId] = familyId
            it[DevicesTable.userId] = userId
            it[DevicesTable.deviceName] = deviceName
            it[DevicesTable.platform] = platform
            it[DevicesTable.pushToken] = pushToken
            it[lastSeenAt] = now
            it[createdAt] = now
            it[updatedAt] = now
        }[DevicesTable.id]

        InvitesTable.update({ InvitesTable.id eq invite[InvitesTable.id] }) {
            with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                it.update(InvitesTable.usesCount, InvitesTable.usesCount + 1)
            }
        }

        persistToken(userId, deviceId, familyId, tokenHash, expiresAt, now)
        buildAuthPayload(userId, familyId, rawToken, expiresAt)
    }

    override suspend fun login(
        inviteCode: String,
        deviceName: String,
        platform: String,
        tokenHash: String,
        rawToken: String,
        expiresAt: Instant,
        now: Instant,
    ): AuthPayload = dbQuery {
        val invite = findInvite(inviteCode)
        val familyId = invite[InvitesTable.familyId]

        val deviceRow = DevicesTable.join(UsersTable, JoinType.INNER, additionalConstraint = {
            DevicesTable.userId eq UsersTable.id
        }).selectAll().where {
            (DevicesTable.familyId eq familyId) and
                (DevicesTable.deviceName eq deviceName) and
                (DevicesTable.platform eq platform) and
                (UsersTable.isActive eq true)
        }.singleOrNull() ?: throw UnauthorizedException("Unknown device or invite code")

        val userId = deviceRow[DevicesTable.userId]
        val deviceId = deviceRow[DevicesTable.id]

        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[lastSeenAt] = now
            it[updatedAt] = now
        }
        UsersTable.update({ UsersTable.id eq userId }) {
            it[lastSeenAt] = now
        }

        persistToken(userId, deviceId, familyId, tokenHash, expiresAt, now)
        buildAuthPayload(userId, familyId, rawToken, expiresAt)
    }

    override suspend fun resolveSession(tokenHash: String, now: Instant): SessionPrincipal? = dbQuery {
        val row = AuthTokensTable.selectAll().where {
            (AuthTokensTable.tokenHash eq tokenHash) and
                (AuthTokensTable.revokedAt.isNull()) and
                (AuthTokensTable.expiresAt greater now)
        }.singleOrNull() ?: return@dbQuery null

        val tokenId = row[AuthTokensTable.id]
        val userId = row[AuthTokensTable.userId]
        val deviceId = row[AuthTokensTable.deviceId]
        val familyId = row[AuthTokensTable.familyId]

        AuthTokensTable.update({ AuthTokensTable.id eq tokenId }) {
            it[lastUsedAt] = now
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
        )
    }

    private fun findInvite(inviteCode: String): ResultRow {
        val normalized = inviteCode.trim().uppercase()
        val invite = InvitesTable.selectAll().where {
            (InvitesTable.code eq normalized) and (InvitesTable.isActive eq true)
        }.singleOrNull() ?: throw UnauthorizedException("Invalid invite code")

        return invite
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
        )
    }
}

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
        )
    }

    override suspend fun getContacts(currentUserId: Long, familyId: Long, now: Instant): List<ContactSummary> = dbQuery {
        UsersTable.selectAll().where {
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
    }
}

class ExposedMessageRepository : MessageRepository {
    override suspend fun sendMessage(
        principal: SessionPrincipal,
        recipientUserId: Long,
        clientMessageUuid: String,
        type: String,
        body: String?,
        quickActionCode: String?,
        location: LocationPayload?,
        now: Instant,
    ): MessagePayload = dbQuery {
        if (recipientUserId == principal.userId) {
            throw ConflictException("Cannot send messages to self")
        }

        val recipient = UsersTable.selectAll().where {
            (UsersTable.id eq recipientUserId) and
                (UsersTable.familyId eq principal.familyId) and
                (UsersTable.isActive eq true)
        }.singleOrNull() ?: throw NotFoundException("Recipient not found")

        val duplicate = MessagesTable.selectAll().where {
            (MessagesTable.senderUserId eq principal.userId) and
                (MessagesTable.clientMessageUuid eq clientMessageUuid)
        }.singleOrNull()

        if (duplicate != null) {
            return@dbQuery duplicate.toMessagePayload()
        }

        recipient[UsersTable.id]

        val messageId = MessagesTable.insert {
            it[familyId] = principal.familyId
            it[senderUserId] = principal.userId
            it[MessagesTable.recipientUserId] = recipientUserId
            it[MessagesTable.clientMessageUuid] = clientMessageUuid
            it[MessagesTable.type] = type
            it[MessagesTable.body] = body
            it[MessagesTable.quickActionCode] = quickActionCode
            it[locationLatitude] = location?.latitude
            it[locationLongitude] = location?.longitude
            it[locationAccuracy] = location?.accuracy
            it[locationLabel] = location?.label
            it[createdAt] = now
        }[MessagesTable.id]

        val senderReceiptId = MessageReceiptsTable.insert {
            it[MessageReceiptsTable.messageId] = messageId
            it[MessageReceiptsTable.userId] = principal.userId
            it[MessageReceiptsTable.deliveredAt] = now
            it[MessageReceiptsTable.readAt] = now
            it[MessageReceiptsTable.updatedAt] = now
        }[MessageReceiptsTable.id]

        MessageReceiptsTable.insert {
            it[MessageReceiptsTable.messageId] = messageId
            it[MessageReceiptsTable.userId] = recipientUserId
            it[MessageReceiptsTable.deliveredAt] = null
            it[MessageReceiptsTable.readAt] = null
            it[MessageReceiptsTable.updatedAt] = now
        }

        recordSyncEvent(principal.familyId, ENTITY_MESSAGE, messageId, now)
        recordSyncEvent(principal.familyId, ENTITY_RECEIPT, senderReceiptId, now)

        MessagesTable.selectAll().where { MessagesTable.id eq messageId }.single().toMessagePayload()
    }

    override suspend fun sync(familyId: Long, sinceId: Long, limit: Int): SyncPayload = dbQuery {
        val events = SyncEventsTable.selectAll().where {
            (SyncEventsTable.familyId eq familyId) and (SyncEventsTable.id greater sinceId)
        }.orderBy(SyncEventsTable.id to SortOrder.ASC).limit(limit).toList()

        val messageIds = events.filter { it[SyncEventsTable.entityType] == ENTITY_MESSAGE }.map { it[SyncEventsTable.entityId] }
        val receiptIds = events.filter { it[SyncEventsTable.entityType] == ENTITY_RECEIPT }.map { it[SyncEventsTable.entityId] }
        val locationIds = events.filter { it[SyncEventsTable.entityType] == ENTITY_LOCATION }.map { it[SyncEventsTable.entityId] }

        val messages = if (messageIds.isEmpty()) {
            emptyList()
        } else {
            MessagesTable.selectAll().where { MessagesTable.id inList messageIds }.orderBy(MessagesTable.id to SortOrder.ASC).map { it.toMessagePayload() }
        }

        val receipts = if (receiptIds.isEmpty()) {
            emptyList()
        } else {
            MessageReceiptsTable.selectAll().where { MessageReceiptsTable.id inList receiptIds }.orderBy(MessageReceiptsTable.id to SortOrder.ASC).map { row ->
                MessageReceiptPayload(
                    messageId = row[MessageReceiptsTable.messageId],
                    userId = row[MessageReceiptsTable.userId],
                    deliveredAt = row[MessageReceiptsTable.deliveredAt],
                    readAt = row[MessageReceiptsTable.readAt],
                )
            }
        }

        val systemEvents = if (locationIds.isEmpty()) {
            emptyList()
        } else {
            LocationEventsTable.join(UsersTable, JoinType.INNER, additionalConstraint = {
                LocationEventsTable.userId eq UsersTable.id
            })
                .selectAll()
                .where { LocationEventsTable.id inList locationIds }
                .orderBy(LocationEventsTable.id to SortOrder.ASC)
                .map { row ->
                    SystemEventPayload(
                        type = "location_shared",
                        createdAt = row[LocationEventsTable.createdAt],
                        message = buildString {
                            append(row[UsersTable.displayName])
                            append(" shared location")
                            row[LocationEventsTable.label]?.takeIf { it.isNotBlank() }?.let {
                                append(": ")
                                append(it)
                            }
                        },
                    )
                }
        }

        SyncPayload(
            nextSinceId = events.lastOrNull()?.get(SyncEventsTable.id) ?: sinceId,
            messages = messages,
            receipts = receipts,
            events = systemEvents,
        )
    }

    override suspend fun markDelivered(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean = dbQuery {
        val receiptRows = targetReceipts(principal, messageIds)
        var changed = false
        receiptRows.forEach { row ->
            if (row[MessageReceiptsTable.deliveredAt] == null) {
                val receiptId = row[MessageReceiptsTable.id]
                MessageReceiptsTable.update({ MessageReceiptsTable.id eq receiptId }) {
                    it[deliveredAt] = now
                    it[updatedAt] = now
                }
                recordSyncEvent(principal.familyId, ENTITY_RECEIPT, receiptId, now)
                changed = true
            }
        }
        changed
    }

    override suspend fun markRead(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean = dbQuery {
        val receiptRows = targetReceipts(principal, messageIds)
        var changed = false
        receiptRows.forEach { row ->
            val receiptId = row[MessageReceiptsTable.id]
            val needsUpdate = row[MessageReceiptsTable.readAt] == null || row[MessageReceiptsTable.deliveredAt] == null
            if (needsUpdate) {
                MessageReceiptsTable.update({ MessageReceiptsTable.id eq receiptId }) {
                    it[deliveredAt] = row[MessageReceiptsTable.deliveredAt] ?: now
                    it[readAt] = now
                    it[updatedAt] = now
                }
                recordSyncEvent(principal.familyId, ENTITY_RECEIPT, receiptId, now)
                changed = true
            }
        }
        changed
    }

    private fun targetReceipts(principal: SessionPrincipal, messageIds: List<Long>): List<ResultRow> {
        if (messageIds.isEmpty()) {
            return emptyList()
        }

        return MessageReceiptsTable.join(MessagesTable, JoinType.INNER, additionalConstraint = {
            MessageReceiptsTable.messageId eq MessagesTable.id
        }).selectAll().where {
            (MessageReceiptsTable.messageId inList messageIds) and
                (MessageReceiptsTable.userId eq principal.userId) and
                (MessagesTable.familyId eq principal.familyId) and
                (MessagesTable.recipientUserId eq principal.userId)
        }.toList()
    }
}

class ExposedPresenceRepository : PresenceRepository {
    override suspend fun ping(principal: SessionPrincipal, deviceName: String?, now: Instant): Unit = dbQuery {
        DevicesTable.update({ DevicesTable.id eq principal.deviceId }) {
            deviceName?.takeIf { it.isNotBlank() }?.let { safeName ->
                it[DevicesTable.deviceName] = safeName
            }
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

class ExposedDeviceRepository : DeviceRepository {
    override suspend fun updatePushToken(principal: SessionPrincipal, pushToken: String?, now: Instant): Unit = dbQuery {
        DevicesTable.update({ DevicesTable.id eq principal.deviceId }) {
            it[DevicesTable.pushToken] = pushToken
            it[updatedAt] = now
        }
    }
}

private fun recordSyncEvent(familyId: Long, entityType: String, entityId: Long, now: Instant) {
    SyncEventsTable.insert {
        it[SyncEventsTable.familyId] = familyId
        it[SyncEventsTable.entityType] = entityType
        it[SyncEventsTable.entityId] = entityId
        it[createdAt] = now
    }
}

private fun ResultRow.toUserProfile(): UserProfile = UserProfile(
    id = this[UsersTable.id],
    familyId = this[UsersTable.familyId],
    displayName = this[UsersTable.displayName],
    role = UserRole.valueOf(this[UsersTable.role]),
    lastSeenAt = this[UsersTable.lastSeenAt],
)

private fun ResultRow.toMessagePayload(): MessagePayload = MessagePayload(
    id = this[MessagesTable.id],
    clientMessageUuid = this[MessagesTable.clientMessageUuid],
    familyId = this[MessagesTable.familyId],
    senderUserId = this[MessagesTable.senderUserId],
    recipientUserId = this[MessagesTable.recipientUserId],
    type = MessageType.valueOf(this[MessagesTable.type]),
    body = this[MessagesTable.body],
    quickActionCode = this[MessagesTable.quickActionCode]?.let { QuickActionCode.valueOf(it) },
    location = this[MessagesTable.locationLatitude]?.let {
        LocationPayload(
            latitude = it,
            longitude = this[MessagesTable.locationLongitude] ?: 0.0,
            accuracy = this[MessagesTable.locationAccuracy],
            label = this[MessagesTable.locationLabel],
        )
    },
    status = MessageStatus.SENT,
    createdAt = this[MessagesTable.createdAt],
)
