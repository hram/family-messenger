package app.network

import app.AppException
import app.platformLogError
import app.platformLogInfo
import app.storage.SessionStore
import com.familymessenger.contract.ApiError
import com.familymessenger.contract.ApiResponse
import com.familymessenger.contract.ErrorCode
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val LOG_TAG_API = "FamilyMessengerApi"

class ApiExecutor(
    private val sessionStore: SessionStore,
) {
    suspend fun <T> execute(
        maxAttempts: Int = 3,
        suppressAbortLikeLogging: Boolean = false,
        block: suspend () -> ApiResponse<T>,
    ): T {
        var attempt = 0
        var lastNetworkException: Throwable? = null
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                val response = block()
                val data = response.data
                if (response.success && data != null) {
                    platformLogInfo(LOG_TAG_API, "Request completed successfully on attempt=$attempt")
                    return data
                }
                platformLogError(
                    LOG_TAG_API,
                    "API returned error response on attempt=$attempt code=${response.error?.code} message=${response.error?.message}",
                )
                throw mapError(response.error)
            } catch (error: HttpRequestTimeoutException) {
                lastNetworkException = error
                platformLogError(LOG_TAG_API, "HTTP timeout on attempt=$attempt", error)
            } catch (error: AppException) {
                platformLogError(LOG_TAG_API, "Application error on attempt=$attempt: ${error.message}", error)
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastNetworkException = error
                if (suppressAbortLikeLogging && error.isAbortLikeNetworkError()) {
                    platformLogInfo(LOG_TAG_API, "Request aborted on attempt=$attempt: ${error.message}")
                } else {
                    platformLogError(LOG_TAG_API, "Network failure on attempt=$attempt: ${error.message}", error)
                }
            }
            if (attempt < maxAttempts) {
                delay(attempt * 300L)
            }
        }
        throw AppException.Network(lastNetworkException?.message ?: "Network request failed")
    }

    private fun mapError(error: ApiError?): AppException {
        val message = error?.message ?: "Unknown server error"
        return when (error?.code) {
            ErrorCode.UNAUTHORIZED -> {
                sessionStore.clear()
                AppException.Unauthorized(message)
            }
            ErrorCode.INVALID_REQUEST -> AppException.Validation(message)
            ErrorCode.CONFLICT -> AppException.Conflict(message)
            ErrorCode.SYNC_RESET_REQUIRED -> AppException.SyncResetRequired(message, error.details["serverInstanceId"])
            ErrorCode.RATE_LIMITED -> AppException.RateLimited(message)
            ErrorCode.FORBIDDEN,
            ErrorCode.NOT_FOUND,
            ErrorCode.INTERNAL_ERROR,
            null,
            -> AppException.Server(message)
        }
    }
}

private fun Throwable.isAbortLikeNetworkError(): Boolean {
    val text = buildString {
        append(message.orEmpty())
        append(' ')
        append(toString())
    }.lowercase()
    return "ns_binding_aborted" in text ||
        "aborterror" in text ||
        "the operation was aborted" in text
}
