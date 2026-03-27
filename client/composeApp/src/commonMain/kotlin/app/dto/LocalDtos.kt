package app.dto

import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.SendMessageRequest
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class StoredContact(
    val contact: ContactSummary,
    val cachedAt: Instant,
)

@Serializable
data class StoredMessage(
    val payload: MessagePayload,
    val updatedAt: Instant,
)

@Serializable
data class PendingMessage(
    val request: SendMessageRequest,
    val createdAt: Instant,
)

@Serializable
data class SyncState(
    val sinceId: Long = 0,
    val serverInstanceId: String = "",
)

@Serializable
data class LocalSettings(
    val serverBaseUrl: String = "",
    val pollingEnabled: Boolean = true,
    val pushEnabled: Boolean = false,
)

@Serializable
data class LocalDatabaseSnapshot(
    val contacts: List<StoredContact> = emptyList(),
    val messages: List<StoredMessage> = emptyList(),
    val pendingMessages: List<PendingMessage> = emptyList(),
    val syncState: SyncState = SyncState(),
    val lastReadAtByChat: Map<Long, Instant> = emptyMap(),
    val settings: LocalSettings = LocalSettings(),
)

@Serializable
data class StoredSession(
    val auth: AuthPayload,
)
