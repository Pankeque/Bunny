package com.bunny.util

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendUrlInterceptor @Inject constructor(
    private val backendDiscovery: BackendDiscovery
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        return if (request.url.host.equals("bunny.local", ignoreCase = true)) {
            val ip = runBlocking { backendDiscovery.resolveBackendIp() }
            if (ip == null) {
                return chain.proceed(request)
            }
            val newUrl = request.url.newBuilder()
                .host(ip)
                .port(BackendDiscovery.Companion.DEFAULT_PORT)
                .build()
            val newRequest = request.newBuilder().url(newUrl).build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}

