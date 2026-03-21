package app.support

import com.familymessenger.contract.ApiError
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend inline fun <reified T> ApplicationCall.respondOk(data: T) {
    respond(ApiResponse(success = true, data = data))
}

suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: ErrorCode,
    message: String,
    details: Map<String, String> = emptyMap(),
) {
    respond(
        status,
        ApiResponse<Unit>(
            success = false,
            error = ApiError(
                code = code,
                message = message,
                details = details,
            ),
        ),
    )
}
