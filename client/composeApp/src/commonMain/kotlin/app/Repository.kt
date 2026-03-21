package app

import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.PlatformType
import com.familymessenger.contract.RegisterDeviceRequest

class SessionRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun registerDevice(inviteCode: String, deviceName: String, platform: PlatformType) =
        apiClient.registerDevice(
            RegisterDeviceRequest(
                inviteCode = inviteCode,
                deviceName = deviceName,
                platform = platform,
            ),
        )
}

class ContactsRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun loadContacts(): List<ContactSummary> =
        apiClient.contacts().data?.contacts.orEmpty()
}
