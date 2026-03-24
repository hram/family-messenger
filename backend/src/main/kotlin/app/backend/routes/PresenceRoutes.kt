package app.backend.routes

import app.backend.plugins.sessionPrincipal
import app.backend.service.PresenceService
import app.backend.support.respondOk
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.PresencePingRequest
import com.familymessenger.contract.ShareLocationRequest
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route

fun Route.presenceRoutes(presenceService: PresenceService) {

    post("/presence/ping", {
        description = "Heartbeat endpoint that refreshes last_seen timestamps for the current device and user."
        request {
            body<PresencePingRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AckResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        val request = call.receive<PresencePingRequest>()
        call.respondOk(presenceService.ping(principal, request))
    }

    post("/location/share", {
        description = "Store a location event and expose it through incremental sync."
        request {
            body<ShareLocationRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AckResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        val request = call.receive<ShareLocationRequest>()
        call.respondOk(presenceService.shareLocation(principal, request))
    }
}
