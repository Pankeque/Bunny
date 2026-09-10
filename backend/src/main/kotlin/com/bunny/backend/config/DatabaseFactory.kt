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
                        com.bunny.backend.model.Roles,
                        com.bunny.backend.model.Channels,
                        com.bunny.backend.model.Messages,
                        com.bunny.backend.model.RefreshTokens,
                        com.bunny.backend.model.Friendships,
                        com.bunny.backend.model.DirectConversations,
                        com.bunny.backend.model.DirectMessages
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
        // Supabase exposes the connection string via SUPABASE_DATABASE_URL
        // (Settings > Database > Connection string). It is a postgres:// URL
        // with the project reference and password embedded, e.g.
        //   postgres://postgres.abc123:secret@aws-0-us-east-1.supabase.co:5432/postgres
        // For direct connections the actual PostgreSQL user is just "postgres",
        // so we strip the project ref. For pooler connections the full
        // "postgres.<project_ref>" username is required as the tenant identifier.
        // The DATABASE_URL env var (used by docker-compose) is checked first
        // so the same code path works for both local Docker and Supabase.
        val rawUrl = env["SUPABASE_DATABASE_URL"] ?: env["DATABASE_URL"]
        if (rawUrl != null) {
            val parsed = parseUrl(rawUrl)
            val isPooler = parsed.url.contains("pooler.supabase.com")
            val username = parsed.username?.let { u ->
                if (!isPooler && u.startsWith("postgres.")) "postgres" else u
            }
            if (username != null) return parsed.copy(username = username)
            return parsed.copy(
                username = env["SUPABASE_DATABASE_USER"] ?: env["DATABASE_USER"] ?: env["PGUSER"] ?: "postgres",
                password = env["SUPABASE_DATABASE_PASSWORD"] ?: env["DATABASE_PASSWORD"] ?: env["PGPASSWORD"] ?: "postgres"
            )
        }

        val host = env["PGHOST"]
        if (host != null) {
            return JdbcInfo(
                url = "jdbc:postgresql://$host:${env["PGPORT"] ?: "5432"}/${env["PGDATABASE"] ?: "postgres"}",
                username = env["DATABASE_USER"] ?: env["PGUSER"] ?: "postgres",
                password = env["DATABASE_PASSWORD"] ?: env["PGPASSWORD"] ?: "postgres"
            )
        }

        throw IllegalStateException(
            "Database configuration missing. Set SUPABASE_DATABASE_URL or PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD."
        )
    }

    private fun parseUrl(rawUrl: String): JdbcInfo {
        val normalized = normalizeJdbcUrl(rawUrl)
        val rest = normalized.substringAfter("://")
        val at = rest.indexOf('@')
        if (at < 0) return JdbcInfo(normalized, null, null)
        val credentials = rest.substring(0, at)
        val hostPart = rest.substring(at + 1)
        val url = if (hostPart.contains("/")) {
            "jdbc:postgresql://$hostPart"
        } else {
            "jdbc:postgresql://$hostPart/postgres"
        }
        return JdbcInfo(
            url = url,
            username = decodeUserInfo(credentials.substringBefore(':')),
            password = decodeUserInfo(credentials.substringAfter(':', ""))
        )
    }

    private fun normalizeJdbcUrl(url: String): String =
        if (url.startsWith("jdbc:postgresql:")) url
        else "jdbc:postgresql://" + url.substringAfter("://")

    private fun decodeUserInfo(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    out.append(hex.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
