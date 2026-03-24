package app.backend.repository.impl

import app.backend.db.FamiliesTable
import app.backend.db.LocationEventsTable
import app.backend.db.MessageReceiptsTable
import app.backend.db.MessagesTable
import app.backend.db.SyncEventsTable
import app.backend.db.UsersTable
import app.backend.model.SessionPrincipal
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageReceiptPayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.UserProfile
import com.familymessenger.contract.UserRole
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import java.security.SecureRandom

internal const val ENTITY_MESSAGE = "message"
internal const val ENTITY_RECEIPT = "receipt"
internal const val ENTITY_LOCATION = "location"

internal fun recordSyncEvent(familyId: Long, entityType: String, entityId: Long, now: Instant) {
    SyncEventsTable.insert {
        it[SyncEventsTable.familyId] = familyId
        it[SyncEventsTable.entityType] = entityType
        it[SyncEventsTable.entityId] = entityId
        it[createdAt] = now
    }
}

internal fun ResultRow.toUserProfile(): UserProfile = UserProfile(
    id = this[UsersTable.id],
    familyId = this[UsersTable.familyId],
    displayName = this[UsersTable.displayName],
    role = UserRole.valueOf(this[UsersTable.role]),
    isAdmin = this[UsersTable.isAdmin],
    lastSeenAt = this[UsersTable.lastSeenAt],
)

internal fun ResultRow.toFamilyChatContact(familyId: Long): ContactSummary = ContactSummary(
    user = UserProfile(
        id = FAMILY_GROUP_CHAT_ID,
        familyId = familyId,
        displayName = this[FamiliesTable.name],
        role = UserRole.FAMILY,
        isAdmin = false,
        lastSeenAt = null,
    ),
    isOnline = true,
)

internal fun ResultRow.toMessagePayload(): MessagePayload = MessagePayload(
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

internal fun ResultRow.toReceiptPayload(): MessageReceiptPayload = MessageReceiptPayload(
    messageId = this[MessageReceiptsTable.messageId],
    userId = this[MessageReceiptsTable.userId],
    deliveredAt = this[MessageReceiptsTable.deliveredAt],
    readAt = this[MessageReceiptsTable.readAt],
)

internal fun ResultRow.isReceiptVisibleTo(principal: SessionPrincipal): Boolean {
    val senderUserId = this[MessagesTable.senderUserId]
    val recipientUserId = this[MessagesTable.recipientUserId]
    val receiptUserId = this[MessageReceiptsTable.userId]

    return when {
        senderUserId == principal.userId -> {
            if (recipientUserId == FAMILY_GROUP_CHAT_ID) {
                receiptUserId != principal.userId
            } else {
                receiptUserId == recipientUserId
            }
        }
        recipientUserId == principal.userId -> receiptUserId == principal.userId
        recipientUserId == FAMILY_GROUP_CHAT_ID -> receiptUserId == principal.userId
        else -> false
    }
}

internal class InviteCodeGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val inviteAlphabet: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789",
) {
    fun nextUniqueCode(exists: (String) -> Boolean, length: Int = 8): String {
        while (true) {
            val code = buildString {
                repeat(length) {
                    append(inviteAlphabet[secureRandom.nextInt(inviteAlphabet.length)])
                }
            }
            if (!exists(code)) {
                return code
            }
        }
    }
}
