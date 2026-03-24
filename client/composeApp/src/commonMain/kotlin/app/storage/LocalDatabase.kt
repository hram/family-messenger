package app.storage

import app.KeyValueStore
import app.dto.LocalDatabaseSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

private const val LOCAL_DB_KEY = "client.local.db.v1"

class LocalDatabase(
    private val settingsStore: KeyValueStore,
    private val json: Json,
) {
    private val state = MutableStateFlow(loadInitial())

    val snapshots: StateFlow<LocalDatabaseSnapshot> = state.asStateFlow()

    fun snapshot(): LocalDatabaseSnapshot = state.value

    fun update(transform: (LocalDatabaseSnapshot) -> LocalDatabaseSnapshot) {
        state.update(transform)
        persist(state.value)
    }

    private fun loadInitial(): LocalDatabaseSnapshot =
        settingsStore.getString(LOCAL_DB_KEY)
            ?.let { runCatching { json.decodeFromString<LocalDatabaseSnapshot>(it) }.getOrNull() }
            ?: LocalDatabaseSnapshot()

    private fun persist(snapshot: LocalDatabaseSnapshot) {
        settingsStore.putString(LOCAL_DB_KEY, json.encodeToString(LocalDatabaseSnapshot.serializer(), snapshot))
    }
}
