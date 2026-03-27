package app.backend.routes

import app.backend.plugins.sessionPrincipal
import app.backend.service.MessageService
import app.backend.support.respondOk
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.MarkDeliveredRequest
import com.familymessenger.contract.MarkReadRequest
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SendMessageResponse
import com.familymessenger.contract.SyncPayload
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route

fun Route.messageRoutes(messageService: MessageService) {

    post("/messages/send", {
        description = "Send a text, quick action, or location message inside the current family."
        request {
            body<SendMessageRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<SendMessageResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        val request = call.receive<SendMessageRequest>()
        call.respondOk(messageService.sendMessage(principal, request))
    }

    get("/messages/sync", {
        description = "Incremental sync endpoint returning messages, receipts and system events since the given cursor."
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<SyncPayload>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        val sinceId = call.request.queryParameters["since_id"]?.toLongOrNull() ?: 0L
        val serverInstanceId = call.request.queryParameters["server_instance_id"]?.trim()?.takeIf { it.isNotEmpty() }
        call.respondOk(messageService.sync(principal, sinceId, serverInstanceId))
    }

    post("/messages/mark-delivered", {
        description = "Mark one or more messages as delivered for the current user."
        request {
            body<MarkDeliveredRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AckResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        val request = call.receive<MarkDeliveredRequest>()
        call.respondOk(messageService.markDelivered(principal, request))
    }

    post("/messages/mark-read", {
        description = "Mark one or more messages as read for the current user."
        request {
            body<MarkReadRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AckResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        val request = call.receive<MarkReadRequest>()
        call.respondOk(messageService.markRead(principal, request))
    }
}
