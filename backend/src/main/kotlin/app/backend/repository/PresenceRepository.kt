package app.backend.repository

import app.backend.model.SessionPrincipal
import kotlinx.datetime.Instant

interface PresenceRepository {
    suspend fun ping(principal: SessionPrincipal, now: Instant)
    suspend fun shareLocation(principal: SessionPrincipal, latitude: Double, longitude: Double, accuracy: Double?, label: String?, now: Instant)
}
