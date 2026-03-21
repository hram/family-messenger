package app.routes

import app.service.AuthService
import app.support.respondOk
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.RegisterDeviceRequest
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(authService: AuthService) {
    post("/auth/register-device") {
        val request = call.receive<RegisterDeviceRequest>()
        call.respondOk(authService.registerDevice(request, clientKey = call.clientKey()))
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        call.respondOk(authService.login(request, clientKey = call.clientKey()))
    }
}

private fun io.ktor.server.application.ApplicationCall.clientKey(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(",")?.trim().orEmpty()
        .ifBlank { request.local.remoteHost }
