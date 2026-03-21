package app.routes

import app.config.AppConfig
import app.support.respondOk
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.HealthResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route

fun Route.healthRoutes(appConfig: AppConfig) {
    get("/api/health", {
        description = "Healthcheck endpoint for local and container orchestration probes."
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<HealthResponse>>()
            }
        }
    }) {
        call.respondOk(
            HealthResponse(
                status = "ok",
                version = appConfig.version,
            ),
        )
    }
}
