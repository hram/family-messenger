package app.backend.service

import app.backend.model.SessionPrincipal
import app.backend.repository.ClientLogRepository
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.ClientLogsRequest
import kotlinx.datetime.Clock

class ClientLogService(
    private val repository: ClientLogRepository,
) {
    suspend fun ingest(principal: SessionPrincipal, request: ClientLogsRequest): AckResponse {
        validateClientLogs(request)
        repository.store(principal, request.entries, Clock.System.now())
        return AckResponse(true)
    }
}
