package app.routes

import app.plugins.sessionPrincipal
import app.service.PresenceService
import app.support.respondOk
import com.familymessenger.contract.PresencePingRequest
import com.familymessenger.contract.ShareLocationRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.presenceRoutes(presenceService: PresenceService) {
    authenticate("auth-bearer") {
        post("/presence/ping") {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<PresencePingRequest>()
            call.respondOk(presenceService.ping(principal, request))
        }

        post("/location/share") {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<ShareLocationRequest>()
            call.respondOk(presenceService.shareLocation(principal, request))
        }
    }
}
