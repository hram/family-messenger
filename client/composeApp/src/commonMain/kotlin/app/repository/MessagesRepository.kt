package app.repository

import app.AppException
import app.network.FamilyMessengerApiClient
import app.storage.LocalDatabase
import app.storage.SessionStore
import app.dto.PendingMessage
import app.dto.StoredContact
import app.dto.StoredMessage
import app.dto.SyncState
import app.randomUuid
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.MessageReceiptPayload
import com.familymessenger.contract.MessageStatus
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.QuickActionCode
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.SystemEventPayload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class MessagesRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val localDatabase: LocalDatabase,
    private val sessionStore: SessionStore,
) {
    private val readInFlightMutex = Mutex()
    private val readInFlight = mutableSetOf<Long>()

    suspend fun conversation(contactId: Long): List<MessagePayload> {
        val currentUserId = sessionStore.currentSession()?.auth?.user?.id ?: return emptyList()
        return localDatabase.snapshot().messages
            .map { it.payload }
            .filter { payload ->
                (contactId == FAMILY_GROUP_CHAT_ID && payload.recipientUserId == FAMILY_GROUP_CHAT_ID) ||
                    (payload.senderUserId == currentUserId && payload.recipientUserId == contactId) ||
                    (payload.senderUserId == contactId && payload.recipientUserId == currentUserId)
            }
            .sortedBy { it.createdAt?.toEpochMilliseconds() ?: 0L }
    }

    suspend fun queueTextMessage(contactId: Long, body: String): MessagePayload =
        queueMessage(
            SendMessageRequest(
                recipientUserId = contactId,
                clientMessageUuid = randomUuid(),
                type = MessageType.TEXT,
                body = body.trim(),
            ),
        )

    suspend fun queueQuickAction(contactId: Long, code: QuickActionCode): MessagePayload =
        queueMessage(
            SendMessageRequest(
                recipientUserId = contactId,
                clientMessageUuid = randomUuid(),
                type = MessageType.QUICK_ACTION,
                quickActionCode = code,
            ),
        )

    suspend fun queueLocationMessage(contactId: Long, location: LocationPayload): MessagePayload =
        queueMessage(
            SendMessageRequest(
                recipientUserId = contactId,
                clientMessageUuid = randomUuid(),
                type = MessageType.LOCATION,
                location = location,
            ),
        )

    suspend fun flushPendingMessages(): Int {
        val pending = localDatabase.snapshot().pendingMessages
        var flushed = 0
        pending.forEach { item ->
            val response = runCatching { apiClient.sendMessage(item.request).message }.getOrElse { return@forEach }
            flushed += 1
            localDatabase.update { snapshot ->
                val messages = snapshot.messages.upsert(response)
                snapshot.copy(
                    messages = messages,
                    pendingMessages = snapshot.pendingMessages.filterNot { it.request.clientMessageUuid == item.request.clientMessageUuid },
                )
            }
        }
        return flushed
    }

    suspend fun markConversationDelivered(contactId: Long) {
        val currentUserId = sessionStore.currentSession()?.auth?.user?.id ?: return
        val messageIds = localDatabase.snapshot().messages
            .map { it.payload }
            .filter {
                if (contactId == FAMILY_GROUP_CHAT_ID) {
                    it.senderUserId != currentUserId && it.recipientUserId == FAMILY_GROUP_CHAT_ID
                } else {
                    it.senderUserId == contactId && it.recipientUserId == currentUserId
                }
            }
            .mapNotNull { it.id }
            .take(200)
        if (messageIds.isEmpty()) return
        apiClient.markDelivered(messageIds)
        localDatabase.update { snapshot ->
            snapshot.copy(messages = snapshot.messages.advanceStatuses(messageIds, MessageStatus.DELIVERED))
        }
    }

    suspend fun markConversationRead(contactId: Long) {
        if (!startRead(contactId)) return
        try {
            val currentUserId = sessionStore.currentSession()?.auth?.user?.id ?: return
            val snapshot = localDatabase.snapshot()
            val incomingMessages = snapshot.messages
                .map { it.payload }
                .filter {
                    if (contactId == FAMILY_GROUP_CHAT_ID) {
                        it.senderUserId != currentUserId && it.recipientUserId == FAMILY_GROUP_CHAT_ID
                    } else {
                        it.senderUserId == contactId && it.recipientUserId == currentUserId
                    }
                }
            val messageIds = incomingMessages.mapNotNull { it.id }.take(200)
            if (messageIds.isNotEmpty()) {
                apiClient.markRead(messageIds)
            }
            val lastReadAt = incomingMessages.maxOfOrNull { it.createdAt ?: Clock.System.now() } ?: Clock.System.now()
            localDatabase.update { snap ->
                snap.copy(messages = snap.messages.advanceStatuses(messageIds, MessageStatus.READ))
                    .copy(lastReadAtByChat = snap.lastReadAtByChat + (contactId to lastReadAt))
            }
        } finally {
            finishRead(contactId)
        }
    }

    suspend fun fetchSync(sinceId: Long): SyncPayload = apiClient.sync(sinceId)

    fun applyTick(contacts: List<ContactSummary>, payload: SyncPayload) {
        val currentUserId = sessionStore.currentSession()?.auth?.user?.id
        localDatabase.update { snapshot ->
            val merged = snapshot.messages
                .mergePayloads(payload.messages)
                .applyReceipts(payload.receipts, currentUserId)
                .appendEvents(payload.events, currentUserId)
            snapshot.copy(
                contacts = contacts.map { StoredContact(it, Clock.System.now()) },
                messages = merged,
                syncState = SyncState(payload.nextSinceId),
            )
        }
    }

    fun pendingCount(): Int = localDatabase.snapshot().pendingMessages.size

    fun syncCursor(): Long = localDatabase.snapshot().syncState.sinceId

    private suspend fun queueMessage(request: SendMessageRequest): MessagePayload {
        val session = sessionStore.currentSession() ?: throw AppException.Unauthorized("Please authenticate first")
        val now = Clock.System.now()
        val localPayload = MessagePayload(
            id = null,
            clientMessageUuid = request.clientMessageUuid,
            familyId = session.auth.user.familyId,
            senderUserId = session.auth.user.id,
            recipientUserId = request.recipientUserId,
            type = request.type,
            body = request.body,
            quickActionCode = request.quickActionCode,
            location = request.location,
            status = MessageStatus.LOCAL_PENDING,
            createdAt = now,
        )
        localDatabase.update { snapshot ->
            snapshot.copy(
                messages = snapshot.messages.upsert(localPayload),
                pendingMessages = snapshot.pendingMessages + PendingMessage(request, now),
            )
        }
        runCatching { flushPendingMessages() }
        return localPayload
    }

    private suspend fun startRead(contactId: Long): Boolean =
        readInFlightMutex.withLock {
            if (contactId in readInFlight) false
            else { readInFlight += contactId; true }
        }

    private suspend fun finishRead(contactId: Long) =
        readInFlightMutex.withLock { readInFlight -= contactId }
}

// --- Message list extension functions ---

private fun List<StoredMessage>.upsert(payload: MessagePayload): List<StoredMessage> {
    val now = Clock.System.now()
    val existing = firstOrNull {
        (payload.id != null && it.payload.id == payload.id) || it.payload.clientMessageUuid == payload.clientMessageUuid
    }
    val merged = existing?.payload?.mergeWith(payload) ?: payload
    val filtered = filterNot {
        (payload.id != null && it.payload.id == payload.id) || it.payload.clientMessageUuid == payload.clientMessageUuid
    }
    return (filtered + StoredMessage(merged, now)).sortedBy { it.payload.createdAt?.toEpochMilliseconds() ?: 0L }
}

private fun List<StoredMessage>.mergePayloads(messages: List<MessagePayload>): List<StoredMessage> =
    messages.fold(this) { acc, payload -> acc.upsert(payload) }

private fun List<StoredMessage>.applyReceipts(
    receipts: List<MessageReceiptPayload>,
    currentUserId: Long?,
): List<StoredMessage> {
    if (currentUserId == null) return this
    var current = this
    receipts.forEach { receipt ->
        val message = current.firstOrNull { it.payload.id == receipt.messageId }?.payload ?: return@forEach
        if (!message.shouldApplyReceipt(receipt, currentUserId)) return@forEach
        val targetStatus = when {
            receipt.readAt != null -> MessageStatus.READ
            receipt.deliveredAt != null -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        }
        current = current.advanceStatuses(listOf(receipt.messageId), targetStatus)
    }
    return current
}

private fun MessagePayload.shouldApplyReceipt(receipt: MessageReceiptPayload, currentUserId: Long): Boolean = when {
    senderUserId == currentUserId -> {
        if (recipientUserId == FAMILY_GROUP_CHAT_ID) receipt.userId != currentUserId
        else receipt.userId == recipientUserId
    }
    recipientUserId == currentUserId -> receipt.userId == currentUserId
    recipientUserId == FAMILY_GROUP_CHAT_ID -> receipt.userId == currentUserId
    else -> false
}

private fun List<StoredMessage>.appendEvents(
    events: List<SystemEventPayload>,
    currentUserId: Long?,
): List<StoredMessage> {
    if (currentUserId == null) return this
    return events.fold(this) { acc, event ->
        val synthetic = MessagePayload(
            id = null,
            clientMessageUuid = "event-${event.createdAt.toEpochMilliseconds()}-${event.type}",
            familyId = 0,
            senderUserId = currentUserId,
            recipientUserId = currentUserId,
            type = MessageType.TEXT,
            body = event.message,
            status = MessageStatus.DELIVERED,
            createdAt = event.createdAt,
        )
        acc.upsert(synthetic)
    }
}

internal fun List<StoredMessage>.advanceStatuses(messageIds: List<Long>, target: MessageStatus): List<StoredMessage> =
    map { item ->
        if (item.payload.id in messageIds) item.copy(payload = item.payload.copy(status = advanceStatus(item.payload.status, target)))
        else item
    }

private fun MessagePayload.mergeWith(other: MessagePayload): MessagePayload =
    copy(
        id = other.id ?: id,
        familyId = if (familyId == 0L) other.familyId else familyId,
        senderUserId = if (senderUserId == 0L) other.senderUserId else senderUserId,
        recipientUserId = if (recipientUserId == 0L) other.recipientUserId else recipientUserId,
        body = other.body ?: body,
        quickActionCode = other.quickActionCode ?: quickActionCode,
        location = other.location ?: location,
        status = advanceStatus(status, other.status),
        createdAt = other.createdAt ?: createdAt,
    )

private fun advanceStatus(current: MessageStatus, candidate: MessageStatus): MessageStatus {
    val rank = mapOf(
        MessageStatus.LOCAL_PENDING to 0,
        MessageStatus.SENT to 1,
        MessageStatus.DELIVERED to 2,
        MessageStatus.READ to 3,
    )
    return if (rank.getValue(candidate) > rank.getValue(current)) candidate else current
}
