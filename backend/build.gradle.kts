plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("app.ApplicationKt")
}

dependencies {
    implementation(project(":shared-contract"))

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.coroutines.core)
    implementation(libs.ktor.openapi)
    implementation(libs.ktor.swagger.ui)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.config.yaml)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit)
}

ktor {
    fatJar {
        archiveFileName.set("family-messenger-backend-all.jar")
    }
}
