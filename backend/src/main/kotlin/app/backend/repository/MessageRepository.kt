package app.backend.repository

import app.backend.model.SessionPrincipal
import com.familymessenger.contract.LocationPayload
import com.familymessenger.contract.MessagePayload
import com.familymessenger.contract.SyncPayload
import kotlinx.datetime.Instant

interface MessageRepository {
    suspend fun sendMessage(
        principal: SessionPrincipal,
        recipientUserId: Long,
        clientMessageUuid: String,
        type: String,
        body: String?,
        quickActionCode: String?,
        location: LocationPayload?,
        now: Instant,
    ): MessagePayload

    suspend fun sync(principal: SessionPrincipal, sinceId: Long, serverInstanceId: String?, limit: Int = 500): SyncPayload
    suspend fun markDelivered(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean
    suspend fun markRead(principal: SessionPrincipal, messageIds: List<Long>, now: Instant): Boolean
}
