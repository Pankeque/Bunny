package com.bunny.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private const val MAX_RETRIES = 10
    private const val RETRY_DELAY_MS = 3_000L

    private data class JdbcInfo(val url: String, val username: String?, val password: String?)

    fun init() {
        val info = jdbcInfo(System.getenv())
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) {
            try {
                val config = HikariConfig().apply {
                    jdbcUrl = info.url
                    driverClassName = "org.postgresql.Driver"
                    username = info.username
                    password = info.password
                    maximumPoolSize = 10
                    connectionTimeout = 5_000
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
                return
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(RETRY_DELAY_MS)
            }
        }
        throw IllegalStateException("Failed to connect to database at ${info.url}", lastError)
    }

    private fun jdbcInfo(env: Map<String, String>): JdbcInfo {
        val rawUrl = env["DATABASE_URL"]
        if (rawUrl != null) {
            val normalized = normalizeJdbcUrl(rawUrl)
            val hasUserInfo = normalized.substringAfter("://").contains("@")
            return JdbcInfo(
                url = normalized,
                username = if (hasUserInfo) null else env["DATABASE_USER"] ?: env["PGUSER"] ?: "postgres",
                password = if (hasUserInfo) null else env["DATABASE_PASSWORD"] ?: env["PGPASSWORD"] ?: "postgres"
            )
        }

        val host = env["PGHOST"]
        if (host != null) {
            return JdbcInfo(
                url = "jdbc:postgresql://$host:${env["PGPORT"] ?: "5432"}/${env["PGDATABASE"] ?: "bunny"}",
                username = env["DATABASE_USER"] ?: env["PGUSER"] ?: "postgres",
                password = env["DATABASE_PASSWORD"] ?: env["PGPASSWORD"] ?: "postgres"
            )
        }

        return JdbcInfo(
            url = "jdbc:postgresql://localhost:5432/bunny",
            username = env["DATABASE_USER"] ?: "postgres",
            password = env["DATABASE_PASSWORD"] ?: "postgres"
        )
    }

    private fun normalizeJdbcUrl(url: String): String =
        if (url.startsWith("jdbc:postgresql:")) url
        else "jdbc:postgresql://" + url.substringAfter("://")
}
