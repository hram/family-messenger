package app.storage

import app.PlatformInfo
import app.dto.LocalSettings

class ClientSettingsRepository(
    private val database: LocalDatabase,
    private val platformInfo: PlatformInfo,
) {
    suspend fun settings(): LocalSettings = database.snapshot().settings.normalized(platformInfo)

    suspend fun updateServerBaseUrl(baseUrl: String) {
        database.update { snapshot ->
            snapshot.copy(settings = snapshot.settings.copy(serverBaseUrl = normalizeBaseUrl(baseUrl)))
        }
    }

    suspend fun updatePollingEnabled(enabled: Boolean) {
        database.update { snapshot ->
            snapshot.copy(settings = snapshot.settings.copy(pollingEnabled = enabled))
        }
    }

    suspend fun updatePushEnabled(enabled: Boolean) {
        database.update { snapshot ->
            snapshot.copy(settings = snapshot.settings.copy(pushEnabled = enabled))
        }
    }

    private fun LocalSettings.normalized(platformInfo: PlatformInfo): LocalSettings {
        val baseUrl = serverBaseUrl.ifBlank { platformInfo.defaultBaseUrl }
        return copy(serverBaseUrl = normalizeBaseUrl(baseUrl))
    }
}

internal fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trim().trimEnd('/')
