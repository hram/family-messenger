package app.service

import app.config.AuthConfig
import app.config.RateLimitConfig
import app.error.RateLimitedException
import app.error.UnauthorizedException
import app.error.ValidationException
import app.model.SessionPrincipal
import app.repository.AuthRepository
import app.repository.DeviceRepository
import app.repository.MessageRepository
import app.repository.PresenceRepository
import app.repository.ProfileRepository
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.LoginRequest
import com.familymessenger.contract.MarkDeliveredRequest
import com.familymessenger.contract.MarkReadRequest
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.PresencePingRequest
import com.familymessenger.contract.ProfileResponse
import com.familymessenger.contract.RegisterDeviceRequest
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SendMessageResponse
import com.familymessenger.contract.ShareLocationRequest
import com.familymessenger.contract.SyncPayload
import com.familymessenger.contract.UpdatePushTokenRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

class AuthService(
    private val repository: AuthRepository,
    private val tokenService: TokenService,
    private val rateLimitService: RateLimitService,
) {
    suspend fun registerDevice(request: RegisterDeviceRequest, clientKey: String): AuthPayload {
        validateInviteCode(request.inviteCode)
        validateDeviceName(request.deviceName)
        validatePushToken(request.pushToken)
        rateLimitService.checkAuth(clientKey)

        val issuedToken = tokenService.issueToken()
        val now = Clock.System.now()
        return repository.registerDevice(
            inviteCode = request.inviteCode,
            deviceName = request.deviceName.trim(),
            platform = request.platform.name,
            pushToken = request.pushToken?.trim(),
            tokenHash = issuedToken.hash,
            rawToken = issuedToken.rawToken,
            expiresAt = issuedToken.expiresAt,
            now = now,
        )
    }

    suspend fun login(request: LoginRequest, clientKey: String): AuthPayload {
        validateInviteCode(request.inviteCode)
        validateDeviceName(request.deviceName)
        rateLimitService.checkAuth(clientKey)

        val issuedToken = tokenService.issueToken()
        val now = Clock.System.now()
        return repository.login(
            inviteCode = request.inviteCode,
            deviceName = request.deviceName.trim(),
            platform = request.platform.name,
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

class ProfileService(
    private val repository: ProfileRepository,
) {
    suspend fun getProfile(principal: SessionPrincipal): ProfileResponse =
        repository.getProfile(principal.userId, principal.familyId)

    suspend fun getContacts(principal: SessionPrincipal): ContactsResponse =
        ContactsResponse(repository.getContacts(principal.userId, principal.familyId, Clock.System.now()))
}

class MessageService(
    private val repository: MessageRepository,
) {
    suspend fun sendMessage(principal: SessionPrincipal, request: SendMessageRequest): SendMessageResponse {
        validateMessageRequest(request)
        val payload = repository.sendMessage(
            principal = principal,
            recipientUserId = request.recipientUserId,
            clientMessageUuid = request.clientMessageUuid.trim(),
            type = request.type.name,
            body = request.body?.trim(),
            quickActionCode = request.quickActionCode?.name,
            location = request.location,
            now = Clock.System.now(),
        )
        return SendMessageResponse(payload)
    }

    suspend fun sync(principal: SessionPrincipal, sinceId: Long): SyncPayload {
        if (sinceId < 0) {
            throw ValidationException("since_id must be >= 0")
        }
        return repository.sync(principal.familyId, sinceId)
    }

    suspend fun markDelivered(principal: SessionPrincipal, request: MarkDeliveredRequest): AckResponse {
        validateMessageIds(request.messageIds)
        return AckResponse(repository.markDelivered(principal, request.messageIds.distinct(), Clock.System.now()))
    }

    suspend fun markRead(principal: SessionPrincipal, request: MarkReadRequest): AckResponse {
        validateMessageIds(request.messageIds)
        return AckResponse(repository.markRead(principal, request.messageIds.distinct(), Clock.System.now()))
    }
}

class PresenceService(
    private val repository: PresenceRepository,
) {
    suspend fun ping(principal: SessionPrincipal, request: PresencePingRequest): AckResponse {
        request.deviceName?.let(::validateDeviceName)
        repository.ping(principal, request.deviceName?.trim(), Clock.System.now())
        return AckResponse(true)
    }

    suspend fun shareLocation(principal: SessionPrincipal, request: ShareLocationRequest): AckResponse {
        validateLocation(request.latitude, request.longitude, request.accuracy, request.label)
        repository.shareLocation(principal, request.latitude, request.longitude, request.accuracy, request.label?.trim(), Clock.System.now())
        return AckResponse(true)
    }
}

class DeviceService(
    private val repository: DeviceRepository,
) {
    suspend fun updatePushToken(principal: SessionPrincipal, request: UpdatePushTokenRequest): AckResponse {
        validatePushToken(request.pushToken)
        repository.updatePushToken(principal, request.pushToken?.trim(), Clock.System.now())
        return AckResponse(true)
    }
}

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

class RateLimitService(
    private val config: RateLimitConfig,
) {
    private val authWindowHits = ConcurrentHashMap<String, MutableList<Long>>()

    fun checkAuth(clientKey: String, nowMillis: Long = System.currentTimeMillis()) {
        if (!config.enabled) {
            return
        }

        val windowStart = nowMillis - (config.authWindowSeconds * 1000)
        val hits = authWindowHits.computeIfAbsent(clientKey) { mutableListOf() }
        synchronized(hits) {
            hits.removeAll { it < windowStart }
            if (hits.size >= config.authMaxRequestsPerWindow) {
                throw RateLimitedException("Too many authentication attempts")
            }
            hits += nowMillis
        }
    }
}

private fun validateInviteCode(inviteCode: String) {
    if (inviteCode.isBlank() || inviteCode.length > 64) {
        throw ValidationException("inviteCode must be between 1 and 64 characters")
    }
}

private fun validateDeviceName(deviceName: String) {
    val normalized = deviceName.trim()
    if (normalized.length !in 2..120) {
        throw ValidationException("deviceName must be between 2 and 120 characters")
    }
}

private fun validatePushToken(pushToken: String?) {
    if (pushToken != null && pushToken.trim().length > 512) {
        throw ValidationException("pushToken must be at most 512 characters")
    }
}

private fun validateMessageRequest(request: SendMessageRequest) {
    if (request.recipientUserId <= 0) {
        throw ValidationException("recipientUserId must be positive")
    }

    val uuid = request.clientMessageUuid.trim()
    if (uuid.isBlank()) {
        throw ValidationException("clientMessageUuid is required")
    }
    runCatching { UUID.fromString(uuid) }.getOrElse {
        throw ValidationException("clientMessageUuid must be a valid UUID")
    }

    when (request.type) {
        MessageType.TEXT -> {
            val body = request.body?.trim().orEmpty()
            if (body.isBlank() || body.length > 4000) {
                throw ValidationException("Text messages require body with 1..4000 characters")
            }
            ensureNull(request.quickActionCode, "quickActionCode")
            ensureNull(request.location, "location")
        }

        MessageType.QUICK_ACTION -> {
            if (request.quickActionCode == null) {
                throw ValidationException("Quick action messages require quickActionCode")
            }
            if (!request.body.isNullOrBlank()) {
                throw ValidationException("Quick action messages must not include body")
            }
            ensureNull(request.location, "location")
        }

        MessageType.LOCATION -> {
            val location = request.location ?: throw ValidationException("Location messages require location payload")
            validateLocation(location.latitude, location.longitude, location.accuracy, location.label)
            if (!request.body.isNullOrBlank()) {
                throw ValidationException("Location messages must not include body")
            }
            ensureNull(request.quickActionCode, "quickActionCode")
        }
    }
}

private fun validateMessageIds(ids: List<Long>) {
    if (ids.isEmpty() || ids.size > 200 || ids.any { it <= 0 }) {
        throw ValidationException("messageIds must contain 1..200 positive ids")
    }
}

private fun validateLocation(latitude: Double, longitude: Double, accuracy: Double?, label: String?) {
    if (latitude !in -90.0..90.0) {
        throw ValidationException("latitude must be between -90 and 90")
    }
    if (longitude !in -180.0..180.0) {
        throw ValidationException("longitude must be between -180 and 180")
    }
    if (accuracy != null && accuracy < 0) {
        throw ValidationException("accuracy must be non-negative")
    }
    if (label != null && label.trim().length > 128) {
        throw ValidationException("label must be at most 128 characters")
    }
}

private fun ensureNull(value: Any?, fieldName: String) {
    if (value != null) {
        throw ValidationException("$fieldName must be omitted for this message type")
    }
}
