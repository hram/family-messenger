package app.backend.service

import app.backend.error.ValidationException
import app.backend.model.SessionPrincipal
import app.backend.repository.DeviceRepository
import app.backend.repository.MessageRepository
import app.backend.repository.ProfileRepository
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MarkDeliveredRequest
import com.familymessenger.contract.MarkReadRequest
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SendMessageResponse
import com.familymessenger.contract.SyncPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageService(
    private val repository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val profileRepository: ProfileRepository,
    private val fcmPushService: FcmPushService,
) {
    private val pushScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pushedUuids = ConcurrentHashMap.newKeySet<String>()

    suspend fun sendMessage(principal: SessionPrincipal, request: SendMessageRequest): SendMessageResponse {
        validateMessageRequest(request)
        val payload = repository.sendMessage(
            principal = principal,
            recipientUserId = request.recipientUserId,
            clientMessageUuid = request.clientMessageUuid.trim(),
            type = request.type.name,
            body = request.body?.trim(),
            quickActionCode = request.quickActionCode?.name,
            location = request.location,
            now = Clock.System.now(),
        )
        if (!pushedUuids.add(request.clientMessageUuid.trim())) return SendMessageResponse(payload)
        pushScope.launch {
            runCatching {
                val tokens = if (request.recipientUserId == FAMILY_GROUP_CHAT_ID) {
                    deviceRepository.getPushTokensForFamily(principal.familyId, principal.userId)
                } else {
                    deviceRepository.getPushTokensForUsers(principal.familyId, listOf(request.recipientUserId))
                }
                val senderName = profileRepository.getDisplayName(principal.userId, principal.familyId)
                    ?: "Family Messenger"
                val pushBody = when (request.type) {
                    MessageType.TEXT -> request.body?.trim() ?: ""
                    MessageType.QUICK_ACTION -> "Быстрый ответ"
                    MessageType.LOCATION -> "Геопозиция"
                }
                fcmPushService.sendPush(tokens, senderName, pushBody)
            }
        }
        return SendMessageResponse(payload)
    }

    suspend fun sync(principal: SessionPrincipal, sinceId: Long): SyncPayload {
        if (sinceId < 0) {
            throw ValidationException("since_id must be >= 0")
        }
        return repository.sync(principal, sinceId)
    }

    suspend fun markDelivered(principal: SessionPrincipal, request: MarkDeliveredRequest): AckResponse {
        validateMessageIds(request.messageIds)
        return AckResponse(repository.markDelivered(principal, request.messageIds.distinct(), Clock.System.now()))
    }

    suspend fun markRead(principal: SessionPrincipal, request: MarkReadRequest): AckResponse {
        validateMessageIds(request.messageIds)
        return AckResponse(repository.markRead(principal, request.messageIds.distinct(), Clock.System.now()))
    }
}
