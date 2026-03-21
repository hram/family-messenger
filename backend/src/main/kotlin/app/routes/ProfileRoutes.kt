package app.routes

import app.plugins.sessionPrincipal
import app.service.ProfileService
import app.support.respondOk
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.profileRoutes(profileService: ProfileService) {
    authenticate("auth-bearer") {
        get("/profile/me") {
            val principal = requireNotNull(call.sessionPrincipal)
            call.respondOk(profileService.getProfile(principal))
        }

        get("/contacts") {
            val principal = requireNotNull(call.sessionPrincipal)
            call.respondOk(profileService.getContacts(principal))
        }
    }
}
