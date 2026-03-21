package app.plugins

import app.config.AppConfig
import app.routes.authRoutes
import app.routes.deviceRoutes
import app.routes.healthRoutes
import app.routes.messageRoutes
import app.routes.presenceRoutes
import app.routes.profileRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting(appConfig: AppConfig) {
    val authService by inject<app.service.AuthService>()
    val profileService by inject<app.service.ProfileService>()
    val messageService by inject<app.service.MessageService>()
    val presenceService by inject<app.service.PresenceService>()
    val deviceService by inject<app.service.DeviceService>()

    routing {
        healthRoutes(appConfig)
        route("/api") {
            authRoutes(authService)
            profileRoutes(profileService)
            messageRoutes(messageService)
            presenceRoutes(presenceService)
            deviceRoutes(deviceService)
        }
    }
}
