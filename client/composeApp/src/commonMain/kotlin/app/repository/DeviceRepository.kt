package app.repository

import app.network.FamilyMessengerApiClient

class DeviceRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun updatePushToken(pushToken: String?) {
        apiClient.updatePushToken(pushToken)
    }
}
