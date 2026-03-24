package app.storage

import app.KeyValueStore
import app.dto.LocalDatabaseSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val LOCAL_DB_KEY = "client.local.db.v1"

class LocalDatabase(
    private val settingsStore: KeyValueStore,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(loadInitial())

    val snapshots: StateFlow<LocalDatabaseSnapshot> = state.asStateFlow()

    suspend fun snapshot(): LocalDatabaseSnapshot = state.value

    suspend fun update(transform: (LocalDatabaseSnapshot) -> LocalDatabaseSnapshot): LocalDatabaseSnapshot =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            persist(updated)
            updated
        }

    private fun loadInitial(): LocalDatabaseSnapshot =
        settingsStore.getString(LOCAL_DB_KEY)
            ?.let { runCatching { json.decodeFromString<LocalDatabaseSnapshot>(it) }.getOrNull() }
            ?: LocalDatabaseSnapshot()

    private fun persist(snapshot: LocalDatabaseSnapshot) {
        settingsStore.putString(LOCAL_DB_KEY, json.encodeToString(LocalDatabaseSnapshot.serializer(), snapshot))
    }
}
