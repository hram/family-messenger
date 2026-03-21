package app.plugins

import app.config.AppConfig
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureDocumentation(appConfig: AppConfig) {
    install(OpenApi) {
        info {
            title = "Family Messenger Backend API"
            version = appConfig.version
            description = "REST API for Family Messenger MVP backend"
        }
        schemas {
            generator = SchemaGenerator.kotlinx()
        }
        security {
            securityScheme("bearerAuth") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "Bearer"
                description = "Bearer token issued by /api/auth/register-device or /api/auth/login"
            }
            defaultSecuritySchemeNames("bearerAuth")
        }
    }

    routing {
        route("/api-docs") {
            // smiley4 v5 treats openApi(...) argument as spec name, not a URL path.
            route("/openapi.json") {
                openApi("api")
            }
            route("/swagger") {
                swaggerUI("/api-docs/openapi.json")
            }
        }
    }
}
