package app.storage

import app.KeyValueStore
import app.dto.StoredSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

private const val SESSION_KEY = "client.session.v1"

class SessionStore(
    private val secureStore: KeyValueStore,
    private val json: Json,
) {
    private val state = MutableStateFlow(loadInitial())

    val session: StateFlow<StoredSession?> = state.asStateFlow()

    fun currentSession(): StoredSession? = state.value

    fun currentToken(): String? = state.value?.auth?.session?.token

    fun save(session: StoredSession) {
        state.value = session
        secureStore.putString(SESSION_KEY, json.encodeToString(StoredSession.serializer(), session))
    }

    fun clear() {
        state.value = null
        secureStore.remove(SESSION_KEY)
    }

    private fun loadInitial(): StoredSession? =
        secureStore.getString(SESSION_KEY)
            ?.let { runCatching { json.decodeFromString<StoredSession>(it) }.getOrNull() }
}
