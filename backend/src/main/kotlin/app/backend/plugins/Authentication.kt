package app.backend.plugins

import app.backend.model.SessionPrincipal
import app.backend.service.AuthService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import org.koin.ktor.ext.inject

fun Application.configureAuthentication() {
    val authService by inject<AuthService>()

    install(Authentication) {
        bearer("auth-bearer") {
            authenticate { tokenCredential ->
                runCatching { authService.authenticate(tokenCredential.token) }.getOrNull()
            }
        }
    }
}

val io.ktor.server.application.ApplicationCall.sessionPrincipal: SessionPrincipal?
    get() = principal()
