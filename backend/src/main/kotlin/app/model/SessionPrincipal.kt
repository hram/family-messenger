package app.model

import io.ktor.server.auth.Principal

data class SessionPrincipal(
    val userId: Long,
    val deviceId: Long,
    val familyId: Long,
    val isAdmin: Boolean,
) : Principal
