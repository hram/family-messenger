package app.routes

import app.plugins.sessionPrincipal
import app.service.DeviceService
import app.support.respondOk
import com.familymessenger.contract.UpdatePushTokenRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.deviceRoutes(deviceService: DeviceService) {
    authenticate("auth-bearer") {
        post("/device/update-push-token") {
            val principal = requireNotNull(call.sessionPrincipal)
            val request = call.receive<UpdatePushTokenRequest>()
            call.respondOk(deviceService.updatePushToken(principal, request))
        }
    }
}
