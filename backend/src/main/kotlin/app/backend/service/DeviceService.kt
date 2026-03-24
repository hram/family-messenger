package app.backend.service

import app.backend.model.SessionPrincipal
import app.backend.repository.DeviceRepository
import com.familymessenger.contract.AckResponse
import com.familymessenger.contract.UpdatePushTokenRequest
import kotlinx.datetime.Clock

class DeviceService(
    private val repository: DeviceRepository,
) {
    suspend fun updatePushToken(principal: SessionPrincipal, request: UpdatePushTokenRequest): AckResponse {
        validatePushToken(request.pushToken)
        repository.updatePushToken(principal, request.pushToken?.trim(), Clock.System.now())
        return AckResponse(true)
    }
}
