package app.routes

import app.plugins.sessionPrincipal
import app.service.MessageService
import app.support.respondOk
import com.familymessenger.contract.MarkDeliveredRequest
import com.familymessenger.contract.MarkReadRequest
import com.familymessenger.contract.SendMessageRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.messageRoutes(messageService: MessageService) {
    authenticate("auth-bearer") {
        post("/messages/send") {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<SendMessageRequest>()
            call.respondOk(messageService.sendMessage(principal, request))
        }

        get("/messages/sync") {
            val principal = requireNotNull(call.sessionPrincipal)
            val sinceId = call.request.queryParameters["since_id"]?.toLongOrNull() ?: 0L
            call.respondOk(messageService.sync(principal, sinceId))
        }

        post("/messages/mark-delivered") {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<MarkDeliveredRequest>()
            call.respondOk(messageService.markDelivered(principal, request))
        }

        post("/messages/mark-read") {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<MarkReadRequest>()
            call.respondOk(messageService.markRead(principal, request))
        }
    }
}
