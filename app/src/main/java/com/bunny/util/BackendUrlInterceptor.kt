package com.bunny.util

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
        val request = chain.request()
        val cachedIp = backendDiscovery.resolveCachedIpSync()

        return if (!cachedIp.isNullOrBlank() && request.url.host.equals("bunny.local", ignoreCase = true)) {
            val newUrl = request.url.newBuilder()
                .host(cachedIp)
                .port(BackendDiscovery.Companion.DEFAULT_PORT)
                .build()
            val newRequest = request.newBuilder().url(newUrl).build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}
