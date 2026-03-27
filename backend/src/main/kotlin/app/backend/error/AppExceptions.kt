package app.backend.error

import com.familymessenger.contract.ErrorCode
import io.ktor.http.HttpStatusCode

open class AppException(
    val status: HttpStatusCode,
    val errorCode: ErrorCode,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)

class ValidationException(
    message: String,
    details: Map<String, String> = emptyMap(),
) : AppException(HttpStatusCode.BadRequest, ErrorCode.INVALID_REQUEST, message, details)

class UnauthorizedException(message: String = "Unauthorized") :
    AppException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, message)

class ForbiddenException(message: String) :
    AppException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, message)

class NotFoundException(message: String) :
    AppException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND, message)

class ConflictException(message: String, details: Map<String, String> = emptyMap()) :
    AppException(HttpStatusCode.Conflict, ErrorCode.CONFLICT, message, details)

class SyncResetRequiredException(message: String, details: Map<String, String> = emptyMap()) :
    AppException(HttpStatusCode.Conflict, ErrorCode.SYNC_RESET_REQUIRED, message, details)

class RateLimitedException(message: String) :
    AppException(HttpStatusCode.TooManyRequests, ErrorCode.RATE_LIMITED, message)
