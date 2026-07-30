package com.bunny.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class DiscoveryState {
    object Unknown : DiscoveryState()
    object Discovering : DiscoveryState()
    data class Found(val ip: String) : DiscoveryState()
    object NotFound : DiscoveryState()
}

@Singleton
class BackendDiscovery @Inject constructor(
    private val context: Context,
    private val nsdHelper: NsdHelper
) {

    companion object {
        const val PREFS_NAME = "backend_discovery_prefs"
        const val KEY_BACKEND_IP = "backend_ip"
        const val KEY_LAST_SUCCESS = "last_success"
        const val DEFAULT_PORT = 443
        private const val TAG = "BackendDiscovery"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Unknown)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState

    suspend fun resolveBaseUrl(): String? = withContext(Dispatchers.IO) {
        val ip = resolveBackendIp()
        ip?.let { "https://$it:$DEFAULT_PORT/" }
    }

    suspend fun resolveSocketUrl(): String? = withContext(Dispatchers.IO) {
        val ip = resolveBackendIp()
        ip?.let { "wss://$it:$DEFAULT_PORT" }
    }

    suspend fun resolveBackendIp(): String? = withContext(Dispatchers.IO) {
        val cachedIp = readCachedIp()
        if (!cachedIp.isNullOrBlank() && isValidIp(cachedIp)) {
            _discoveryState.value = DiscoveryState.Found(cachedIp)
            return@withContext cachedIp
        }

        _discoveryState.value = DiscoveryState.Discovering
        val discoveredIp = nsdHelper.discoverBackend()?.host?.hostAddress
        if (!discoveredIp.isNullOrBlank() && isValidIp(discoveredIp)) {
            cacheIp(discoveredIp)
            _discoveryState.value = DiscoveryState.Found(discoveredIp)
            return@withContext discoveredIp
        }

        _discoveryState.value = DiscoveryState.NotFound
        Log.w(TAG, "Backend not found via mDNS and no cached IP available")
        return@withContext null
    }

    fun resolveBaseUrlSync(): String? {
        val ip = readCachedIp()
        return ip?.takeIf { isValidIp(it) }?.let { "https://$it:$DEFAULT_PORT/" }
    }

    fun resolveSocketUrlSync(): String? {
        val ip = readCachedIp()
        return ip?.takeIf { isValidIp(it) }?.let { "wss://$it:$DEFAULT_PORT" }
    }

    fun resolveCachedIpSync(): String? {
        val cachedIp = readCachedIp()
        return if (!cachedIp.isNullOrBlank() && isValidIp(cachedIp)) cachedIp else null
    }

    suspend fun warmupDiscovery() {
        try {
            Log.d(TAG, "Starting startup backend discovery")
            _discoveryState.value = DiscoveryState.Discovering
            val ip = resolveBackendIp()
            if (!ip.isNullOrBlank()) {
                Log.d(TAG, "Startup discovery successful: $ip")
            } else {
                Log.w(TAG, "Startup discovery failed, will retry on demand")
            }
        } catch (e: Exception) {
            _discoveryState.value = DiscoveryState.NotFound
            Log.e(TAG, "Startup discovery error", e)
        }
    }

    private fun isValidIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        if (ip == "127.0.0.1") return false
        if (ip == "0.0.0.0") return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        if (!parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }) return false
        val addr = InetAddress.getByName(ip)
        if (addr.isAnyLocalAddress) return false
        if (addr.isLoopbackAddress) return false
        if (addr.isLinkLocalAddress) return false
        return true
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
