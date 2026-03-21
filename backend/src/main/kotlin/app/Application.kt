package app

import app.config.AppConfig
import app.plugins.configureAuthentication
import app.plugins.configureCors
import app.plugins.configureDocumentation
import app.plugins.configureKoin
import app.plugins.configureMonitoring
import app.plugins.configureRouting
import app.plugins.configureSerialization
import app.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.koin.ktor.ext.get

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val appConfig = environment.config.toAppConfig()

    configureKoin(appConfig)
    get<app.db.DatabaseFactory>().connectAndBootstrap()
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureAuthentication()
    configureDocumentation(appConfig)
    configureRouting(appConfig)
}

private fun ApplicationConfig.toAppConfig(): AppConfig = AppConfig.from(this)
