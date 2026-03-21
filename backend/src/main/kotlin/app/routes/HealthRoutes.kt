package app.routes

import app.config.AppConfig
import app.support.respondOk
import com.familymessenger.contract.HealthResponse
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes(appConfig: AppConfig) {
    get("/api/health") {
        call.respondOk(
            HealthResponse(
                status = "ok",
                version = appConfig.version,
            ),
        )
    }
}
