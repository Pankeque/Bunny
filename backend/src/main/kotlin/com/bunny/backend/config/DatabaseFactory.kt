package com.bunny.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/bunny"
            driver = "org.postgresql.Driver"
            username = System.getenv("DATABASE_USER") ?: "postgres"
            password = System.getenv("DATABASE_PASSWORD") ?: "postgres"
            maximumPoolSize = 10
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(
                com.bunny.backend.model.Users,
                com.bunny.backend.model.Servers,
                com.bunny.backend.model.ServerMembers,
                com.bunny.backend.model.Channels,
                com.bunny.backend.model.Messages,
                com.bunny.backend.model.RefreshTokens
            )
        }
    }
}
