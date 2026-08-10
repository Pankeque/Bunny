package com.bunny.data.remote.auth

import android.content.SharedPreferences
import com.bunny.data.remote.dto.AuthResponseDto
import com.bunny.util.Constants
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : Authenticator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = prefs.getString(Constants.KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrEmpty()) return null
        if (response.request().header("Authorization") == null) return null

        return try {
            val body = "{\"refresh_token\":\"$refreshToken\"}"
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder()
                .url(Constants.BASE_URL + "api/auth/refresh")
                .post(body)
                .build()

            client.newCall(refreshRequest).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) return null
                val text = refreshResponse.body?.string() ?: return null
                val authResponse = gson.fromJson(text, AuthResponseDto::class.java)
                prefs.edit()
                    .putString(Constants.KEY_ACCESS_TOKEN, authResponse.accessToken)
                    .apply()
                response.request().newBuilder()
                    .header("Authorization", "Bearer ${authResponse.accessToken}")
                    .build()
            }
        } catch (e: Exception) {
            null
        }
    }
}
