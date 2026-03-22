package app.routes

import app.service.SetupService
import app.support.respondOk
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.SetupBootstrapRequest
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupStatusResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route

fun Route.setupRoutes(setupService: SetupService) {
    get("/setup/status", {
        description = "Returns whether the system has already been initialized."
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<SetupStatusResponse>>()
            }
        }
    }) {
        call.respondOk(setupService.status())
    }

    post("/setup/bootstrap", {
        description = "Initial one-time system bootstrap with master password, family and invite codes."
        request {
            body<SetupBootstrapRequest>()
        }
        response {
            HttpStatusCode.OK to {
                body<ApiResponse<SetupBootstrapResponse>>()
            }
        }
    }) {
        val request = call.receive<SetupBootstrapRequest>()
        call.respondOk(setupService.bootstrap(request))
    }
}
