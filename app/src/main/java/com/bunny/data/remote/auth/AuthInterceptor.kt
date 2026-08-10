package com.bunny.data.remote.auth

import android.content.SharedPreferences
import com.bunny.util.Constants
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val prefs: SharedPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = prefs.getString(Constants.KEY_ACCESS_TOKEN, null)
        val request = chain.request().newBuilder()
            .apply {
                if (!token.isNullOrEmpty()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()
        return chain.proceed(request)
    }
}
