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
    suspend fun <T> execute(block: suspend () -> ApiResponse<T>): T {
        var attempt = 0
        var lastNetworkException: Throwable? = null
        while (attempt < 3) {
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
                platformLogError(LOG_TAG_API, "Network failure on attempt=$attempt: ${error.message}", error)
            }
            delay(attempt * 300L)
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
            ErrorCode.RATE_LIMITED -> AppException.RateLimited(message)
            ErrorCode.FORBIDDEN,
            ErrorCode.NOT_FOUND,
            ErrorCode.INTERNAL_ERROR,
            null,
            -> AppException.Server(message)
        }
    }
}
