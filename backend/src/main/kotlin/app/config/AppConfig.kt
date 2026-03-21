package app.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val name: String,
    val version: String,
    val database: DatabaseConfig,
    val auth: AuthConfig,
    val rateLimit: RateLimitConfig,
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig = AppConfig(
            name = config.requiredString("app.name"),
            version = config.requiredString("app.version"),
            database = DatabaseConfig(
                jdbcUrl = config.requiredString("app.database.jdbcUrl"),
                user = config.requiredString("app.database.user"),
                password = config.requiredString("app.database.password"),
                driver = config.requiredString("app.database.driver"),
                bootstrapSchema = config.optionalString("app.database.bootstrapSchema")?.toBooleanStrictOrNull() ?: true,
                seedOnStart = config.optionalString("app.database.seedOnStart")?.toBooleanStrictOrNull() ?: true,
            ),
            auth = AuthConfig(
                tokenTtlHours = config.optionalString("app.auth.tokenTtlHours")?.toLongOrNull() ?: 720L,
            ),
            rateLimit = RateLimitConfig(
                enabled = config.optionalString("app.rateLimit.enabled")?.toBooleanStrictOrNull() ?: true,
                authWindowSeconds = config.optionalString("app.rateLimit.authWindowSeconds")?.toLongOrNull() ?: 60L,
                authMaxRequestsPerWindow = config.optionalString("app.rateLimit.authMaxRequestsPerWindow")?.toIntOrNull() ?: 10,
            ),
        )
    }
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val driver: String,
    val bootstrapSchema: Boolean,
    val seedOnStart: Boolean,
)

data class AuthConfig(
    val tokenTtlHours: Long,
)

data class RateLimitConfig(
    val enabled: Boolean,
    val authWindowSeconds: Long,
    val authMaxRequestsPerWindow: Int,
)

private fun ApplicationConfig.requiredString(path: String): String = property(path).getString()

private fun ApplicationConfig.optionalString(path: String): String? = propertyOrNull(path)?.getString()
