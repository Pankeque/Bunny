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
        var request = chain.request()
        val baseHost = request.url.host

        return if (baseHost.equals("localhost", ignoreCase = true)
            || baseHost.equals("127.0.0.1", ignoreCase = true)
        ) {
            val ip = backendDiscovery.resolveBackendIpSync()
                ?: backendDiscovery.fallbackLocalGateway()
                ?: "127.0.0.1"

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
