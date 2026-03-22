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
    // Kotlin/Wasm and Kotlin/JS add Node.js distribution repositories during setup.
    // FAIL_ON_PROJECT_REPOS blocks those repositories and breaks wasm browser tasks.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        ivy("https://nodejs.org/dist/") {
            name = "Node.js Distributions"
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision]-[classifier].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download/") {
            name = "Yarn Distributions"
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.yarnpkg", "yarn")
            }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download/") {
            name = "Binaryen Distributions"
            patternLayout {
                artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.github.webassembly", "binaryen")
            }
        }
    }
}

include(":backend")
include(":shared-contract")
include(":client")
include(":client:composeApp")

project(":client").projectDir = file("client")
project(":client:composeApp").projectDir = file("client/composeApp")
