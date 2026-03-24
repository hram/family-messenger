package app.backend.routes

import app.backend.plugins.sessionPrincipal
import app.backend.service.ProfileService
import app.backend.support.respondOk
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.ProfileResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route

fun Route.profileRoutes(profileService: ProfileService) {

    get("/profile/me", {
        description = "Return the current user profile and family summary."
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<ProfileResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        call.respondOk(profileService.getProfile(principal))
    }

    get("/contacts", {
        description = "Return active family contacts visible to the current user."
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<ContactsResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        call.respondOk(profileService.getContacts(principal))
    }
}
