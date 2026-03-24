package app.backend.repository.impl

import app.backend.db.LocationEventsTable
import app.backend.db.MessageReceiptsTable
import app.backend.db.MessagesTable
import app.backend.db.SyncEventsTable
import app.backend.db.UsersTable
import app.backend.db.dbQuery
import app.backend.error.ConflictException
import app.backend.error.NotFoundException
import app.backend.model.SessionPrincipal
import app.backend.repository.MessageRepository
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.SystemEventPayload
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

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

        val duplicate = MessagesTable.selectAll().where {
            (MessagesTable.senderUserId eq principal.userId) and
                (MessagesTable.clientMessageUuid eq clientMessageUuid)
        }.singleOrNull()

        if (duplicate != null) {
            return@dbQuery duplicate.toMessagePayload()
        }

        val targetRecipientIds = if (recipientUserId == FAMILY_GROUP_CHAT_ID) {
            UsersTable.selectAll().where {
                (UsersTable.familyId eq principal.familyId) and
                    (UsersTable.isActive eq true) and
                    (UsersTable.id neq principal.userId)
            }.map { it[UsersTable.id] }
        } else {
            val recipient = UsersTable.selectAll().where {
                (UsersTable.id eq recipientUserId) and
                    (UsersTable.familyId eq principal.familyId) and
                    (UsersTable.isActive eq true)
            }.singleOrNull() ?: throw NotFoundException("Recipient not found")
            listOf(recipient[UsersTable.id])
        }

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

        targetRecipientIds.forEach { targetUserId ->
            MessageReceiptsTable.insert {
                it[MessageReceiptsTable.messageId] = messageId
                it[MessageReceiptsTable.userId] = targetUserId
                it[MessageReceiptsTable.deliveredAt] = null
                it[MessageReceiptsTable.readAt] = null
                it[MessageReceiptsTable.updatedAt] = now
            }
        }

        recordSyncEvent(principal.familyId, ENTITY_MESSAGE, messageId, now)
        recordSyncEvent(principal.familyId, ENTITY_RECEIPT, senderReceiptId, now)

        MessagesTable.selectAll().where { MessagesTable.id eq messageId }.single().toMessagePayload()
    }

    override suspend fun sync(principal: SessionPrincipal, sinceId: Long, limit: Int): SyncPayload = dbQuery {
        val events = SyncEventsTable.selectAll().where {
            (SyncEventsTable.familyId eq principal.familyId) and (SyncEventsTable.id greater sinceId)
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
            MessageReceiptsTable.join(MessagesTable, JoinType.INNER, additionalConstraint = {
                MessageReceiptsTable.messageId eq MessagesTable.id
            }).selectAll().where {
                (MessageReceiptsTable.id inList receiptIds) and
                    (MessagesTable.familyId eq principal.familyId)
            }
                .orderBy(MessageReceiptsTable.id to SortOrder.ASC)
                .mapNotNull { row ->
                    row.toReceiptPayload()
                        .takeIf { row.isReceiptVisibleTo(principal) }
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
                ((MessagesTable.recipientUserId eq principal.userId) or
                    (MessagesTable.recipientUserId eq FAMILY_GROUP_CHAT_ID))
        }.toList()
    }
}
