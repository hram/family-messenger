package app.backend.service

import app.backend.error.ForbiddenException
import app.backend.error.ValidationException
import app.backend.model.SessionPrincipal
import com.familymessenger.contract.ClientLogsRequest
import com.familymessenger.contract.FAMILY_GROUP_CHAT_ID
import com.familymessenger.contract.MessageType
import com.familymessenger.contract.SendMessageRequest
import com.familymessenger.contract.SetupMemberDraft
import com.familymessenger.contract.UserRole
import java.util.UUID

internal fun validateInviteCode(inviteCode: String) {
    if (inviteCode.isBlank() || inviteCode.length > 64) {
        throw ValidationException("inviteCode must be between 1 and 64 characters")
    }
}

internal fun validateMasterPassword(masterPassword: String) {
    if (masterPassword.length < 8 || masterPassword.length > 256) {
        throw ValidationException("masterPassword must be between 8 and 256 characters")
    }
}

internal fun validateFamilyName(familyName: String) {
    val normalized = familyName.trim()
    if (normalized.isBlank() || normalized.length > 120) {
        throw ValidationException("familyName must be between 1 and 120 characters")
    }
}

internal fun validateSetupMembers(members: List<SetupMemberDraft>) {
    if (members.isEmpty() || members.size > 20) {
        throw ValidationException("members must contain between 1 and 20 entries")
    }
    if (members.any { it.role == UserRole.FAMILY }) {
        throw ValidationException("members cannot use family role")
    }
    if (members.none { it.role == UserRole.PARENT }) {
        throw ValidationException("At least one parent is required")
    }
    if (members.any { it.isAdmin && it.role != UserRole.PARENT }) {
        throw ValidationException("Administrator flag is allowed only for parents")
    }
    if (members.none { it.role == UserRole.PARENT && it.isAdmin }) {
        throw ValidationException("At least one parent administrator is required")
    }

    val names = members.map { it.displayName.trim() }
    if (names.any { it.isBlank() || it.length > 120 }) {
        throw ValidationException("Each member displayName must be between 1 and 120 characters")
    }
    if (names.map { it.lowercase() }.distinct().size != names.size) {
        throw ValidationException("Member displayName values must be unique")
    }
}

internal fun validateAdminPrincipal(principal: SessionPrincipal) {
    if (!principal.isAdmin) {
        throw ForbiddenException("Administrator access required")
    }
}

internal fun validateMemberDraft(displayName: String, role: UserRole, isAdmin: Boolean) {
    val normalized = displayName.trim()
    if (normalized.isBlank() || normalized.length > 120) {
        throw ValidationException("displayName must be between 1 and 120 characters")
    }
    if (role == UserRole.FAMILY) {
        throw ValidationException("role must be parent or child")
    }
    if (isAdmin && role != UserRole.PARENT) {
        throw ValidationException("Administrator flag is allowed only for parents")
    }
}

internal fun validatePushToken(pushToken: String?) {
    if (pushToken != null && pushToken.trim().length > 512) {
        throw ValidationException("pushToken must be at most 512 characters")
    }
}

internal fun validateMessageRequest(request: SendMessageRequest) {
    if (request.recipientUserId < FAMILY_GROUP_CHAT_ID) {
        throw ValidationException("recipientUserId must be >= 0")
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

internal fun validateMessageIds(ids: List<Long>) {
    if (ids.isEmpty() || ids.size > 200 || ids.any { it <= 0 }) {
        throw ValidationException("messageIds must contain 1..200 positive ids")
    }
}

internal fun validateClientLogs(request: ClientLogsRequest) {
    if (request.entries.isEmpty() || request.entries.size > 200) {
        throw ValidationException("entries must contain between 1 and 200 log records")
    }
    request.entries.forEach { entry ->
        if (entry.eventId.isBlank() || entry.eventId.length > 64) {
            throw ValidationException("eventId must be between 1 and 64 characters")
        }
        if (entry.tag.isBlank() || entry.tag.length > 128) {
            throw ValidationException("tag must be between 1 and 128 characters")
        }
        if (entry.message.isBlank() || entry.message.length > 8000) {
            throw ValidationException("message must be between 1 and 8000 characters")
        }
        val details = entry.details
        if (details != null && details.length > 16000) {
            throw ValidationException("details must be at most 16000 characters")
        }
    }
}

internal fun validateLocation(latitude: Double, longitude: Double, accuracy: Double?, label: String?) {
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

internal fun ensureNull(value: Any?, fieldName: String) {
    if (value != null) {
        throw ValidationException("$fieldName must be omitted for this message type")
    }
}
