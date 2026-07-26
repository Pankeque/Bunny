package com.bunny.backend.routes

import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authRoutes()
        authenticate("auth") {
            serverRoutes()
            channelRoutes()
            messageRoutes()
            userRoutes()
        }
    }
}
