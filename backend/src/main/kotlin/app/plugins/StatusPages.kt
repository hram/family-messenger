package app.plugins

import app.error.AppException
import com.familymessenger.contract.ApiError
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    val log = environment.log

    install(StatusPages) {
        exception<AppException> { call, cause ->
            call.respond(
                cause.status,
                ApiResponse<Unit>(
                    success = false,
                    error = ApiError(
                        code = cause.errorCode,
                        message = cause.message,
                        details = cause.details,
                    ),
                ),
            )
        }

        exception<ContentTransformationException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = ApiError(
                        code = ErrorCode.INVALID_REQUEST,
                        message = "Malformed request body",
                    ),
                ),
            )
        }

        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = ApiError(
                        code = ErrorCode.INVALID_REQUEST,
                        message = cause.message ?: "Invalid request",
                    ),
                ),
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(
                    success = false,
                    error = ApiError(
                        code = ErrorCode.NOT_FOUND,
                        message = "Route not found",
                    ),
                ),
            )
        }

        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse<Unit>(
                    success = false,
                    error = ApiError(
                        code = ErrorCode.UNAUTHORIZED,
                        message = "Invalid or missing bearer token",
                    ),
                ),
            )
        }

        exception<Throwable> { call, cause ->
            log.error("Unhandled backend exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse<Unit>(
                    success = false,
                    error = ApiError(
                        code = ErrorCode.INTERNAL_ERROR,
                        message = "Unexpected server error",
                    ),
                ),
            )
        }
    }
}
