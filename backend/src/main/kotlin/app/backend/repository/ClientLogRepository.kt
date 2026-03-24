package app.backend.repository

import app.backend.model.SessionPrincipal
import com.familymessenger.contract.ClientLogEntry
import kotlinx.datetime.Instant

interface ClientLogRepository {
    suspend fun store(principal: SessionPrincipal, entries: List<ClientLogEntry>, now: Instant): Int
}
