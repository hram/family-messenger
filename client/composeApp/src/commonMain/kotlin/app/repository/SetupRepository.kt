package app.repository

import app.network.FamilyMessengerApiClient
import app.ui.SetupMemberInputState
import com.familymessenger.contract.SetupBootstrapRequest
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupMemberDraft
import com.familymessenger.contract.SetupStatusResponse

class SetupRepository(
    private val apiClient: FamilyMessengerApiClient,
) {
    suspend fun status(): SetupStatusResponse = apiClient.setupStatus()

    suspend fun bootstrap(
        masterPassword: String,
        familyName: String,
        members: List<SetupMemberInputState>,
    ): SetupBootstrapResponse = apiClient.bootstrap(
        SetupBootstrapRequest(
            masterPassword = masterPassword,
            familyName = familyName,
            members = members.map {
                SetupMemberDraft(
                    displayName = it.displayName.trim(),
                    role = it.role,
                    isAdmin = it.isAdmin,
                )
            },
        ),
    )
}
