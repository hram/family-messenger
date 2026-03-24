package app.backend.routes

import app.backend.service.AuthService
import app.backend.support.respondOk
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ApiResponse
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.Route

fun Route.authRoutes(authService: AuthService) {
    post("/auth/login", {
        description = "Authenticate by invite code, creating the user or device binding on first use and issuing a bearer session token."
        request {
            body<LoginRequest> {
                description = "Invite code, platform and optional push token."
            }
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AuthPayload>> {
                    description = "Authenticated session payload."
                }
            }
        }
    }) {
        val request = call.receive<LoginRequest>()
        call.respondOk(authService.login(request, clientKey = call.clientKey()))
    }
}

private fun io.ktor.server.application.ApplicationCall.clientKey(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(",")?.trim().orEmpty()
        .ifBlank { request.local.remoteHost }
