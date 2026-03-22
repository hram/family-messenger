package app.plugins

import app.config.AppConfig
import app.routes.adminRoutes
import app.routes.authRoutes
import app.routes.deviceRoutes
import app.routes.healthRoutes
import app.routes.messageRoutes
import app.routes.presenceRoutes
import app.routes.profileRoutes
import app.routes.setupRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting(appConfig: AppConfig) {
    val authService by inject<app.service.AuthService>()
    val adminService by inject<app.service.AdminService>()
    val profileService by inject<app.service.ProfileService>()
    val messageService by inject<app.service.MessageService>()
    val presenceService by inject<app.service.PresenceService>()
    val deviceService by inject<app.service.DeviceService>()
    val setupService by inject<app.service.SetupService>()

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
            }
        }
    }
}
