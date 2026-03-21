rootProject.name = "family-messenger"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":backend")
include(":shared-contract")
include(":client")
include(":client:composeApp")

project(":client").projectDir = file("client")
project(":client:composeApp").projectDir = file("client/composeApp")
