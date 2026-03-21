package app.routes

import app.service.AuthService
import app.support.respondOk
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.RegisterDeviceRequest
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ApiResponse
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.Route

fun Route.authRoutes(authService: AuthService) {
    post("/auth/register-device", {
        description = "Register a device using an invite code and issue a bearer session token."
        request {
            body<RegisterDeviceRequest> {
                description = "Invite code, device identity and platform."
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
        val request = call.receive<RegisterDeviceRequest>()
        call.respondOk(authService.registerDevice(request, clientKey = call.clientKey()))
    }

    post("/auth/login", {
        description = "Log in an already registered device using the invite code and device identity."
        request {
            body<LoginRequest> {
                description = "Invite code and previously known device identity."
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
