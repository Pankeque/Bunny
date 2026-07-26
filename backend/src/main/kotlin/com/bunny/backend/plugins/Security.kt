package com.bunny.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bunny.backend.service.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

fun Application.configureSecurity() {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "your-secret-key-change-in-production"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "bunny"
    val jwtRealm = System.getenv("JWT_REALM") ?: "bunny"

    authentication {
        jwt("auth") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asInt()
                if (userId != null) {
                    UserService.findById(userId)
                } else {
                    null
                }
            }
        }
    }
}

fun generateToken(userId: Int): String {
    val jwtSecret = System.getenv("JWT_SECRET") ?: "your-secret-key-change-in-production"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "bunny"
    return JWT.create()
        .withIssuer(jwtIssuer)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + 900000))
        .sign(Algorithm.HMAC256(jwtSecret))
}
