package app.backend.repository

import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupMemberDraft
import com.familymessenger.contract.SetupStatusResponse
import kotlinx.datetime.Instant

interface SetupRepository {
    suspend fun status(): SetupStatusResponse
    suspend fun bootstrap(
        masterPasswordHash: String,
        familyName: String,
        members: List<SetupMemberDraft>,
        now: Instant,
    ): SetupBootstrapResponse
}
