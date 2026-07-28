package com.bunny.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendDiscovery @Inject constructor(
    private val context: Context,
    private val nsdHelper: NsdHelper
) {

    companion object {
        const val PREFS_NAME = "backend_discovery_prefs"
        const val KEY_BACKEND_IP = "backend_ip"
        const val KEY_LAST_SUCCESS = "last_success"
        const val DEFAULT_PORT = 8080
        private const val TAG = "BackendDiscovery"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun resolveBaseUrl(): String = withContext(Dispatchers.IO) {
        val ip = resolveBackendIp()
        "http://$ip:$DEFAULT_PORT/"
    }

    suspend fun resolveSocketUrl(): String = withContext(Dispatchers.IO) {
        val ip = resolveBackendIp()
        "http://$ip:$DEFAULT_PORT"
    }

    suspend fun resolveBackendIp(): String = withContext(Dispatchers.IO) {
        val cachedIp = readCachedIp()
        if (!cachedIp.isNullOrBlank()) {
            return@withContext cachedIp
        }

        val discoveredIp = nsdHelper.discoverBackend()?.host?.hostAddress
        if (!discoveredIp.isNullOrBlank()) {
            cacheIp(discoveredIp)
            return@withContext discoveredIp
        }

        Log.w(TAG, "Backend not found via mDNS and no cached IP available")
        return@withContext null
    }

    fun resolveBaseUrlSync(): String? {
        val ip = resolveBackendIpSync()
        return ip?.let { "http://$it:$DEFAULT_PORT/" }
    }

    fun resolveSocketUrlSync(): String? {
        val ip = resolveBackendIpSync()
        return ip?.let { "http://$it:$DEFAULT_PORT" }
    }

    fun resolveBackendIpSync(): String? {
        return try {
            val cachedIp = readCachedIp()
            if (!cachedIp.isNullOrBlank()) {
                return cachedIp
            }
            Log.w(TAG, "No cached backend IP available for sync resolution")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving backend IP sync", e)
            null
        }
    }

    private fun readCachedIp(): String? {
        val cached = prefs.getString(KEY_BACKEND_IP, null)
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        if (!cached.isNullOrBlank() && lastSuccess > 0) {
            val ageMillis = System.currentTimeMillis() - lastSuccess
            if (ageMillis < TimeUnit.HOURS.toMillis(12)) return cached
            Log.w(TAG, "Cached backend IP expired")
        }
        return null
    }

    private fun cacheIp(ip: String) {
        prefs.edit()
            .putString(KEY_BACKEND_IP, ip)
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Cached backend IP: $ip")
    }
}
