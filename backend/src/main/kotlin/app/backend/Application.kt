package app.backend

import app.backend.config.AppConfig
import app.backend.plugins.configureAuthentication
import app.backend.plugins.configureCors
import app.backend.plugins.configureDocumentation
import app.backend.plugins.configureKoin
import app.backend.plugins.configureMonitoring
import app.backend.plugins.configureRouting
import app.backend.plugins.configureSerialization
import app.backend.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.ktor.ext.get

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val appConfig = environment.config.toAppConfig()

    configureKoin(appConfig)
    get<app.backend.db.DatabaseFactory>().connectAndBootstrap()
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureAuthentication()
    configureDocumentation(appConfig)
    configureRouting(appConfig)
}

private fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig.from(this)
