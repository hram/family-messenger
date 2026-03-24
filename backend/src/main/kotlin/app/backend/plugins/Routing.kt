package app.backend.plugins

import app.backend.config.AppConfig
import app.backend.routes.adminRoutes
import app.backend.routes.authRoutes
import app.backend.routes.clientLogRoutes
import app.backend.routes.deviceRoutes
import app.backend.routes.healthRoutes
import app.backend.routes.messageRoutes
import app.backend.routes.presenceRoutes
import app.backend.routes.profileRoutes
import app.backend.routes.setupRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting(appConfig: AppConfig) {
    val authService by inject<app.backend.service.AuthService>()
    val adminService by inject<app.backend.service.AdminService>()
    val profileService by inject<app.backend.service.ProfileService>()
    val messageService by inject<app.backend.service.MessageService>()
    val presenceService by inject<app.backend.service.PresenceService>()
    val deviceService by inject<app.backend.service.DeviceService>()
    val clientLogService by inject<app.backend.service.ClientLogService>()
    val setupService by inject<app.backend.service.SetupService>()

    routing {
        healthRoutes(appConfig)
        route("/api") {
            setupRoutes(setupService)
            authRoutes(authService)
            authenticate("auth-bearer") {
                adminRoutes(adminService)
                profileRoutes(profileService)
                messageRoutes(messageService)
                presenceRoutes(presenceService)
                deviceRoutes(deviceService)
                clientLogRoutes(clientLogService)
            }
        }
    }
}
