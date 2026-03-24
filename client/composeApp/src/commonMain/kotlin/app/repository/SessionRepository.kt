package app.repository

import app.AppException
import app.network.FamilyMessengerApiClient
import app.storage.LocalDatabase
import app.PlatformInfo
import app.storage.SessionStore
import app.dto.StoredSession
import app.dto.SyncState
import com.familymessenger.contract.AuthPayload
import com.familymessenger.contract.LoginRequest

class SessionRepository(
    private val apiClient: FamilyMessengerApiClient,
    private val sessionStore: SessionStore,
    private val localDatabase: LocalDatabase,
    private val platformInfo: PlatformInfo,
) {
    suspend fun restore(): StoredSession? = sessionStore.currentSession()

    suspend fun refreshSessionFromServer(): StoredSession {
        val existing = sessionStore.currentSession() ?: throw AppException.Unauthorized("Please authenticate first")
        val profile = apiClient.profile()
        val refreshed = StoredSession(
            auth = AuthPayload(
                user = profile.user,
                family = profile.family,
                session = existing.auth.session,
            ),
        )
        sessionStore.save(refreshed)
        return refreshed
    }

    suspend fun login(inviteCode: String): StoredSession {
        val auth = apiClient.login(
            LoginRequest(
                inviteCode = inviteCode.trim(),
                platform = platformInfo.type,
            ),
        )
        return persistAuth(auth)
    }

    suspend fun logout() {
        sessionStore.clear()
        localDatabase.update {
            it.copy(
                contacts = emptyList(),
                messages = emptyList(),
                pendingMessages = emptyList(),
                syncState = SyncState(),
            )
        }
    }

    private fun persistAuth(auth: AuthPayload): StoredSession {
        val session = StoredSession(auth)
        sessionStore.save(session)
        return session
    }
}
