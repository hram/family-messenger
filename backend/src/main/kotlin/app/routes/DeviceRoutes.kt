package app.routes

import app.plugins.sessionPrincipal
import app.service.DeviceService
import app.support.respondOk
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.UpdatePushTokenRequest
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route

fun Route.deviceRoutes(deviceService: DeviceService) {
    authenticate("auth-bearer") {
        post("/device/update-push-token", {
            description = "Update or clear the push token for the current device."
            request {
                body<UpdatePushTokenRequest>()
            }
            response {
                HttpStatusCode.OK to {
                    body<ApiResponse<AckResponse>>()
                }
            }
        }) {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<UpdatePushTokenRequest>()
            call.respondOk(deviceService.updatePushToken(principal, request))
        }
    }
}
