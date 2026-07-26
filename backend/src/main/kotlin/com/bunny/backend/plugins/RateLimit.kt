package com.bunny.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*

fun Application.configureRateLimit() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 100, refillMs = 60_000)
        }
    }
}
