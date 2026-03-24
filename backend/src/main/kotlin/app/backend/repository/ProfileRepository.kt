package app.backend.repository

import com.familymessenger.contract.ContactSummary
import com.familymessenger.contract.ProfileResponse
import kotlinx.datetime.Instant

interface ProfileRepository {
    suspend fun getProfile(userId: Long, familyId: Long): ProfileResponse
    suspend fun getContacts(currentUserId: Long, familyId: Long, now: Instant): List<ContactSummary>
    suspend fun getDisplayName(userId: Long, familyId: Long): String?
}
