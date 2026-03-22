package app.routes

import app.plugins.sessionPrincipal
import app.service.ClientLogService
import app.support.respondOk
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.ClientLogsRequest
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route

fun Route.clientLogRoutes(clientLogService: ClientLogService) {
    post("/client-logs", {
        description = "Accept batched client-side diagnostic logs for the current authenticated session."
        request {
            body<ClientLogsRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<AckResponse>>()
            }
        }
    }) {
        val principal = requireNotNull(call.sessionPrincipal)
        call.respondOk(clientLogService.ingest(principal, call.receive()))
    }
}
