package app.backend.service

import app.backend.repository.SetupRepository
import com.familymessenger.contract.SetupBootstrapRequest
import com.familymessenger.contract.SetupBootstrapResponse
import com.familymessenger.contract.SetupStatusResponse
import kotlinx.datetime.Clock
import org.mindrot.jbcrypt.BCrypt

class SetupService(
    private val repository: SetupRepository,
) {
    suspend fun status(): SetupStatusResponse = repository.status()

    suspend fun bootstrap(request: SetupBootstrapRequest): SetupBootstrapResponse {
        validateMasterPassword(request.masterPassword)
        validateFamilyName(request.familyName)
        validateSetupMembers(request.members)
        val passwordHash = BCrypt.hashpw(request.masterPassword, BCrypt.gensalt(12))
        return repository.bootstrap(
            masterPasswordHash = passwordHash,
            familyName = request.familyName.trim(),
            members = request.members.map { it.copy(displayName = it.displayName.trim()) },
            now = Clock.System.now(),
        )
    }
}
