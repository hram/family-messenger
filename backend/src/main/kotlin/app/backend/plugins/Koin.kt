package app.backend.plugins

import app.backend.config.AppConfig
import app.backend.di.backendModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin(appConfig: AppConfig) {
    install(Koin) {
        slf4jLogger()
        modules(backendModule(appConfig))
    }
}
