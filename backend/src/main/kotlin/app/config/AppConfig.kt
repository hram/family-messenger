package app.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val name: String,
    val version: String,
    val database: DatabaseConfig,
    val auth: AuthConfig,
    val rateLimit: RateLimitConfig,
    val firebase: FirebaseConfig,
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig = AppConfig(
            name = envOrConfig("APP_NAME", config, "app.name", "family-messenger-backend"),
            version = envOrConfig("APP_VERSION", config, "app.version", "0.1.0"),
            database = DatabaseConfig(
                jdbcUrl = envOrNull("DB_JDBC_URL")
                    ?.takeIf { it.isNotBlank() }
                    ?: config.optionalString("app.database.jdbcUrl")
                        ?.takeIf { it.isNotBlank() }
                    ?: buildJdbcUrl(
                        host = envOrConfig("DB_HOST", config, "app.database.host", "localhost"),
                        port = envOrConfig("DB_PORT", config, "app.database.port", "5432"),
                        name = envOrConfig("DB_NAME", config, "app.database.name", "family_messenger"),
                    ),
                user = envOrConfig("DB_USER", config, "app.database.user", "family"),
                password = envOrConfig("DB_PASSWORD", config, "app.database.password", "family"),
                driver = envOrConfig("DB_DRIVER", config, "app.database.driver", "org.postgresql.Driver"),
                bootstrapSchema = envOrConfig("DB_BOOTSTRAP_SCHEMA", config, "app.database.bootstrapSchema", "true").toBooleanStrictOrNull() ?: true,
                seedOnStart = envOrConfig("DB_SEED_ON_START", config, "app.database.seedOnStart", "true").toBooleanStrictOrNull() ?: true,
            ),
            auth = AuthConfig(
                tokenTtlHours = envOrConfig("AUTH_TOKEN_TTL_HOURS", config, "app.auth.tokenTtlHours", "720").toLongOrNull() ?: 720L,
            ),
            rateLimit = RateLimitConfig(
                enabled = envOrConfig("AUTH_RATE_LIMIT_ENABLED", config, "app.rateLimit.enabled", "true").toBooleanStrictOrNull() ?: true,
                authWindowSeconds = envOrConfig("AUTH_RATE_LIMIT_WINDOW_SECONDS", config, "app.rateLimit.authWindowSeconds", "60").toLongOrNull() ?: 60L,
                authMaxRequestsPerWindow = envOrConfig("AUTH_RATE_LIMIT_MAX_REQUESTS", config, "app.rateLimit.authMaxRequestsPerWindow", "10").toIntOrNull() ?: 10,
            ),
            firebase = FirebaseConfig(
                serviceAccountJson = envOrNull("FIREBASE_SERVICE_ACCOUNT_JSON")?.takeIf { it.isNotBlank() },
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

data class FirebaseConfig(
    val serviceAccountJson: String?,
) {
    val enabled: Boolean get() = !serviceAccountJson.isNullOrBlank()
}

private fun ApplicationConfig.requiredString(path: String): String = property(path).getString()

private fun ApplicationConfig.optionalString(path: String): String? = propertyOrNull(path)?.getString()

private fun buildJdbcUrl(host: String, port: String, name: String): String =
    "jdbc:postgresql://$host:$port/$name"

private fun envOrConfig(envName: String, config: ApplicationConfig, path: String, default: String): String =
    envOrNull(envName)
        ?: config.optionalString(path)
        ?: default

private fun envOrNull(envName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
