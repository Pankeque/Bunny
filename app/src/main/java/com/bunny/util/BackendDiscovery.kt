package com.bunny.util

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendDiscovery @Inject constructor(
    private val context: Context
) {

    companion object {
        const val PREFS_NAME = "backend_discovery_prefs"
        const val KEY_BACKEND_IP = "backend_ip"
        const val KEY_LAST_SUCCESS = "last_success"
        const val DEFAULT_PORT = 8080
        const val EMULATOR_HOST = "10.0.2.2"
        private val COMMON_IPS = arrayOf(
            "192.168.0.1",
            "192.168.1.1",
            "192.168.0.100",
            "192.168.1.100",
            "192.168.1.10",
            "192.168.0.10",
            EMULATOR_HOST,
            "127.0.0.1"
        )
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
        if (isEmulator()) return@withContext "10.0.2.2"

        val cachedIp = readCachedIp()
        if (!cachedIp.isNullOrBlank() && probeBackend(cachedIp)) {
            return@withContext cachedIp
        }

        val candidates = buildCandidateIps()
        candidates.firstOrNull { probeBackend(it) }?.let { found ->
            cacheIp(found)
            return@withContext found
        }

        cachedIp ?: COMMON_IPS.first()
    }

    fun resolveBaseUrlSync(): String {
        val ip = resolveBackendIpSync() ?: EMULATOR_HOST
        return "http://$ip:$DEFAULT_PORT/"
    }

    fun resolveSocketUrlSync(): String {
        val ip = resolveBackendIpSync() ?: EMULATOR_HOST
        return "http://$ip:$DEFAULT_PORT"
    }

    fun resolveBackendIpSync(): String? {
        return try {
            val cachedIp = readCachedIp()
            if (!cachedIp.isNullOrBlank() && probeBackend(cachedIp)) {
                return cachedIp
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.contains("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MODEL.contains("VirtualBox")
                || Build.MODEL.contains("VMware")
                || Build.PRODUCT.contains("default")
                || Build.PRODUCT.contains("simulator")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
    }

    private fun readCachedIp(): String? {
        val cached = prefs.getString(KEY_BACKEND_IP, null)
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        if (!cached.isNullOrBlank() && lastSuccess > 0) {
            val ageMillis = System.currentTimeMillis() - lastSuccess
            if (ageMillis < TimeUnit.HOURS.toMillis(12)) return cached
        }
        return null
    }

    private fun cacheIp(ip: String) {
        prefs.edit()
            .putString(KEY_BACKEND_IP, ip)
            .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
            .apply()
    }

    private fun buildCandidateIps(): List<String> {
        val gateway = getWifiGateway()
        val subnet = gateway?.substringBeforeLast(".")
        val fromGateway = if (!gateway.isNullOrBlank()) {
            listOf(gateway) + (1..10).map { "$subnet.$it" }
        } else emptyList()

        val wifiIp = getWifiIp()
        val fromWifiIp = if (!wifiIp.isNullOrBlank()) {
            val subnetIp = wifiIp.substringBeforeLast(".")
            (1..10).map { "$subnetIp.$it" }
        } else emptyList()

        return (fromGateway + fromWifiIp + COMMON_IPS.toList()).distinct()
    }

    private fun getWifiGateway(): String? {
        return try {
            val wifiManager = context.getSystemService<WifiManager>()
            val dhcp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiManager?.dhcpInfo
            } else {
                wifiManager?.connectionInfo?.ipAddress?.let { raw ->
                    if (raw == 0) null else {
                        val ip = (raw and 0xFF).toString() + "." +
                                (raw shr 8 and 0xFF) + "." +
                                (raw shr 16 and 0xFF) + "." +
                                (raw shr 24 and 0xFF)
                        val subnet = ip.substringBeforeLast(".")
                        "$subnet.1"
                    }
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val raw = wifiManager?.connectionInfo?.ipAddress ?: return null
                if (raw == 0) null else {
                    val ip = (raw and 0xFF).toString() + "." +
                            (raw shr 8 and 0xFF) + "." +
                            (raw shr 16 and 0xFF) + "." +
                            (raw shr 24 and 0xFF)
                    val subnet = ip.substringBeforeLast(".")
                    "$subnet.1"
                }
            } else {
                val info = wifiManager?.dhcpInfo
                if (info == null || info.gateway == 0) null else {
                    val ip = (info.gateway and 0xFF).toString() + "." +
                            (info.gateway shr 8 and 0xFF) + "." +
                            (info.gateway shr 16 and 0xFF) + "." +
                            (info.gateway shr 24 and 0xFF)
                    ip
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getWifiIp(): String? {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            var result: String? = null
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                val addresses = Collections.list(nif.inetAddresses)
                for (address in addresses) {
                    val host = address.hostAddress
                    if (!address.isLoopbackAddress && host != null && host.contains(".")) {
                        result = host.substringBefore("%")
                    }
                }
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun probeBackend(ip: String): Boolean {
        return try {
            val url = URL("http://$ip:$DEFAULT_PORT/api/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 400
            connection.readTimeout = 400
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            code in 200..599
        } catch (e: Exception) {
            false
        }
    }
}
