package com.bunny.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
        }
        exception<Throwable> { call, cause ->
            call.respondText("Internal Server Error: ${cause.localizedMessage}", status = io.ktor.http.HttpStatusCode.InternalServerError)
        }
    }
}
