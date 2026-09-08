package com.bunny.util

object Constants {
    const val PREFS_NAME = "bunny_prefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USERNAME = "username"
    const val KEY_AVATAR_URL = "avatar_url"
    const val KEY_THEME = "theme"

    val BASE_URL: String
        get() = com.bunny.BuildConfig.BASE_URL
    val SOCKET_URL: String
        get() = com.bunny.BuildConfig.SOCKET_URL
}
