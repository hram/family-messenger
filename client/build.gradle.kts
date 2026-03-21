plugins {
    base
}

tasks.register("clientInfo") {
    group = "help"
    description = "Prints client module structure information."
    doLast {
        println("Compose Multiplatform app lives in :client:composeApp")
    }
}
