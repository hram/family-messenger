package app.backend.service

import app.backend.model.SessionPrincipal
import app.backend.repository.ProfileRepository
import com.familymessenger.contract.ContactsResponse
import com.familymessenger.contract.ProfileResponse
import kotlinx.datetime.Clock

class ProfileService(
    private val repository: ProfileRepository,
) {
    suspend fun getProfile(principal: SessionPrincipal): ProfileResponse =
        repository.getProfile(principal.userId, principal.familyId)

    suspend fun getContacts(principal: SessionPrincipal): ContactsResponse =
        ContactsResponse(repository.getContacts(principal.userId, principal.familyId, Clock.System.now()))
}
