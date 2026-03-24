package app.backend.service

import app.backend.config.AuthConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.hours

class TokenService(
    private val config: AuthConfig,
) {
    private val secureRandom = SecureRandom()

    fun issueToken(now: Instant = Clock.System.now()): IssuedToken {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedToken(
            rawToken = rawToken,
            hash = hash(rawToken),
            expiresAt = now + config.tokenTtlHours.hours,
        )
    }

    fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class IssuedToken(
    val rawToken: String,
    val hash: String,
    val expiresAt: Instant,
)
