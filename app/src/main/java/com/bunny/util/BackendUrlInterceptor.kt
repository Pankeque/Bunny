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
        val baseHost = Constants.EMULATOR_HOST

        return if (request.url.host == baseHost || request.url.host.equals("localhost", ignoreCase = true)) {
            val ip = backendDiscovery.resolveBackendIpSync() ?: Constants.EMULATOR_HOST
            val newUrl = request.url.newBuilder()
                .host(ip)
                .port(Constants.DEFAULT_PORT)
                .build()
            val newRequest = request.newBuilder().url(newUrl).build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}
