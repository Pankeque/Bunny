package com.bunny.backend.util

import org.mindrot.jbcrypt.BCrypt

object PasswordUtils {
    fun hash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    fun verify(password: String, hash: String): Boolean {
        return BCrypt.checkpw(password, hash)
    }
}
