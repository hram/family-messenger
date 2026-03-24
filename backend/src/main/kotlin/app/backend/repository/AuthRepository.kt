package app.backend.repository

import app.backend.model.SessionPrincipal
import com.familymessenger.contract.AuthPayload
import kotlinx.datetime.Instant

interface AuthRepository {
    suspend fun login(
        inviteCode: String,
        platform: String,
        pushToken: String?,
        tokenHash: String,
        rawToken: String,
        expiresAt: Instant,
        now: Instant,
    ): AuthPayload

    suspend fun resolveSession(tokenHash: String, now: Instant): SessionPrincipal?
}
