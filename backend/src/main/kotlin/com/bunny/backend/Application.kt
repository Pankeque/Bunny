package com.bunny.backend

import com.bunny.backend.config.DatabaseFactory
import com.bunny.backend.plugins.*
import com.bunny.backend.routes.configureRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    DatabaseFactory.init()
    configureSecurity()
    configureSerialization()
    configureCORS()
    configureRateLimit()
    configureStatusPages()
    configureMonitoring()
    configureRouting()
    configureWebSockets()
}
