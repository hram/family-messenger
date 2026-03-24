package app.repository

import app.network.FamilyMessengerApiClient
import app.GeolocationService
import com.familymessenger.contract.LocationPayload

class PresenceRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val geolocationService: GeolocationService,
) {
    suspend fun ping() {
        apiClient.ping()
    }

    suspend fun currentLocation(): LocationPayload? = geolocationService.currentLocation()

    suspend fun recordLocationEvent(location: LocationPayload) {
        apiClient.shareLocation(location)
    }
}
