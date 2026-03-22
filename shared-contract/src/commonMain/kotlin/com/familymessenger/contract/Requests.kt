package com.familymessenger.contract

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val inviteCode: String,
    val platform: PlatformType,
    val pushToken: String? = null,
)

@Serializable
data class SetupBootstrapRequest(
    val masterPassword: String,
    val familyName: String,
    val members: List<SetupMemberDraft>,
)

@Serializable
data class VerifyAdminAccessRequest(
    val masterPassword: String,
)

@Serializable
data class AdminCreateMemberRequest(
    val masterPassword: String,
    val displayName: String,
    val role: UserRole,
    val isAdmin: Boolean = false,
)

@Serializable
data class AdminRemoveMemberRequest(
    val masterPassword: String,
    val inviteCode: String,
)

@Serializable
data class SendMessageRequest(
    val recipientUserId: Long,
    val clientMessageUuid: String,
    val type: MessageType,
    val body: String? = null,
    val quickActionCode: QuickActionCode? = null,
    val location: LocationPayload? = null,
)

@Serializable
data class SyncRequest(
    val sinceId: Long? = null,
)

@Serializable
data class MarkDeliveredRequest(
    val messageIds: List<Long>,
)

@Serializable
data class MarkReadRequest(
    val messageIds: List<Long>,
)

@Serializable
data class ClientLogsRequest(
    val entries: List<ClientLogEntry>,
)

@Serializable
data class ShareLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val label: String? = null,
)

@Serializable
class PresencePingRequest

@Serializable
data class UpdatePushTokenRequest(
    val pushToken: String?,
)
