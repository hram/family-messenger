package app.backend.service

import app.backend.error.UnauthorizedException
import app.backend.model.SessionPrincipal
import app.backend.repository.AuthRepository
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.LoginRequest
import kotlinx.datetime.Clock

class AuthService(
    private val repository: AuthRepository,
    private val tokenService: TokenService,
    private val rateLimitService: RateLimitService,
) {
    suspend fun login(request: LoginRequest, clientKey: String): AuthPayload {
        validateInviteCode(request.inviteCode)
        validatePushToken(request.pushToken)
        rateLimitService.checkAuth(clientKey)

        val issuedToken = tokenService.issueToken()
        val now = Clock.System.now()
        return repository.login(
            inviteCode = request.inviteCode,
            platform = request.platform.name,
            pushToken = request.pushToken?.trim(),
            tokenHash = issuedToken.hash,
            rawToken = issuedToken.rawToken,
            expiresAt = issuedToken.expiresAt,
            now = now,
        )
    }

    suspend fun authenticate(rawToken: String): SessionPrincipal {
        if (rawToken.isBlank()) {
            throw UnauthorizedException("Missing bearer token")
        }
        val tokenHash = tokenService.hash(rawToken)
        return repository.resolveSession(tokenHash, Clock.System.now())
            ?: throw UnauthorizedException("Invalid or expired token")
    }
}
