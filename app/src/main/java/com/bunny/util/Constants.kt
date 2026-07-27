package com.bunny.util

object Constants {
    val BASE_URL: String
        get() = NetworkEnvironment.requireBaseUrl()
    val SOCKET_URL: String
        get() = NetworkEnvironment.requireSocketUrl()
    const val PREFS_NAME = "bunny_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USERNAME = "username"
    const val KEY_THEME = "theme"
}
