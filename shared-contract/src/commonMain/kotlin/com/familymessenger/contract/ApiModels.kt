package com.familymessenger.contract

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: ErrorCode,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
enum class ErrorCode {
    @SerialName("INVALID_REQUEST")
    INVALID_REQUEST,

    @SerialName("UNAUTHORIZED")
    UNAUTHORIZED,

    @SerialName("FORBIDDEN")
    FORBIDDEN,

    @SerialName("NOT_FOUND")
    NOT_FOUND,

    @SerialName("CONFLICT")
    CONFLICT,

    @SerialName("RATE_LIMITED")
    RATE_LIMITED,

    @SerialName("INTERNAL_ERROR")
    INTERNAL_ERROR,
}

@Serializable
enum class PlatformType {
    @SerialName("android")
    ANDROID,

    @SerialName("ios")
    IOS,

    @SerialName("web")
    WEB,

    @SerialName("desktop")
    DESKTOP,
}

@Serializable
enum class UserRole {
    @SerialName("parent")
    PARENT,

    @SerialName("child")
    CHILD,
}

@Serializable
enum class MessageType {
    @SerialName("text")
    TEXT,

    @SerialName("quick_action")
    QUICK_ACTION,

    @SerialName("location")
    LOCATION,
}

@Serializable
enum class MessageStatus {
    @SerialName("local_pending")
    LOCAL_PENDING,

    @SerialName("sent")
    SENT,

    @SerialName("delivered")
    DELIVERED,

    @SerialName("read")
    READ,
}

@Serializable
enum class QuickActionCode {
    @SerialName("IM_OUT")
    IM_OUT,

    @SerialName("AT_SCHOOL")
    AT_SCHOOL,

    @SerialName("PICK_ME_UP")
    PICK_ME_UP,

    @SerialName("ALL_OK")
    ALL_OK,
}

@Serializable
data class FamilySummary(
    val id: Long,
    val name: String,
)

@Serializable
data class UserProfile(
    val id: Long,
    val familyId: Long,
    val displayName: String,
    val role: UserRole,
    val lastSeenAt: Instant? = null,
)

@Serializable
data class DeviceSession(
    val token: String,
    val expiresAt: Instant? = null,
)

@Serializable
data class AuthPayload(
    val user: UserProfile,
    val family: FamilySummary,
    val session: DeviceSession,
)

@Serializable
data class ContactSummary(
    val user: UserProfile,
    val isOnline: Boolean,
)

@Serializable
data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val label: String? = null,
)

@Serializable
data class MessagePayload(
    val id: Long? = null,
    val clientMessageUuid: String,
    val familyId: Long,
    val senderUserId: Long,
    val recipientUserId: Long,
    val type: MessageType,
    val body: String? = null,
    val quickActionCode: QuickActionCode? = null,
    val location: LocationPayload? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val createdAt: Instant? = null,
)

@Serializable
data class MessageReceiptPayload(
    val messageId: Long,
    val userId: Long,
    val deliveredAt: Instant? = null,
    val readAt: Instant? = null,
)

@Serializable
data class SystemEventPayload(
    val type: String,
    val createdAt: Instant,
    val message: String,
)

@Serializable
data class SyncPayload(
    val nextSinceId: Long,
    val messages: List<MessagePayload>,
    val receipts: List<MessageReceiptPayload>,
    val events: List<SystemEventPayload>,
)
