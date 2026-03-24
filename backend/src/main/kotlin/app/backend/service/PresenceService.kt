package app.backend.service

import app.backend.model.SessionPrincipal
import app.backend.repository.PresenceRepository
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.PresencePingRequest
import com.familymessenger.contract.ShareLocationRequest
import kotlinx.datetime.Clock

class PresenceService(
    private val repository: PresenceRepository,
) {
    suspend fun ping(principal: SessionPrincipal, request: PresencePingRequest): AckResponse {
        repository.ping(principal, Clock.System.now())
        return AckResponse(true)
    }

    suspend fun shareLocation(principal: SessionPrincipal, request: ShareLocationRequest): AckResponse {
        validateLocation(request.latitude, request.longitude, request.accuracy, request.label)
        repository.shareLocation(principal, request.latitude, request.longitude, request.accuracy, request.label?.trim(), Clock.System.now())
        return AckResponse(true)
    }
}
