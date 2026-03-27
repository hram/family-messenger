package com.familymessenger.contract

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
)

@Serializable
data class ProfileResponse(
    val user: UserProfile,
    val family: FamilySummary,
    val serverInstanceId: String = "",
)

@Serializable
data class ContactsResponse(
    val contacts: List<ContactSummary>,
)

@Serializable
data class SendMessageResponse(
    val message: MessagePayload,
)

@Serializable
data class AckResponse(
    val accepted: Boolean,
)
