package com.bunny.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NsdHelper(private val context: Context) {

    companion object {
        private const val TAG = "NsdHelper"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME = "Bunny Backend"
        private const val DEFAULT_DISCOVERY_TIMEOUT_MS = 4000L
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 500L
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val discoveryMutex = Mutex()

    suspend fun discoverBackend(timeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS): NsdServiceInfo? {
        if (!discoveryMutex.tryLock()) {
            Log.w(TAG, "Discovery already in progress, skipping concurrent request")
            return null
        }
        try {
            var lastError: Exception? = null
            repeat(MAX_RETRIES + 1) { attempt ->
                try {
                    Log.d(TAG, "Discovery attempt ${attempt + 1}/$MAX_RETRIES")
                    doDiscover(timeoutMs)?.let { return it }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Discovery timeout on attempt ${attempt + 1}")
                    lastError = e
                } catch (e: Exception) {
                    Log.e(TAG, "Discovery failed on attempt ${attempt + 1}", e)
                    lastError = e
                }
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS)
                }
            }
            Log.e(TAG, "All discovery attempts failed", lastError)
            return null
        } finally {
            discoveryMutex.unlock()
        }
    }

    private suspend fun doDiscover(timeoutMs: Long): NsdServiceInfo? = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "Discovery started")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "Service found: ${service.serviceName}")
                    if (service.serviceName.contains(SERVICE_NAME, ignoreCase = true)) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "Resolve failed for ${service.serviceName}: $errorCode")
                            }

                            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                                Log.d(TAG, "Resolved: ${resolvedService.host.hostAddress}:${resolvedService.port}")
                                if (!cont.isCompleted) {
                                    cont.resume(resolvedService)
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.w(TAG, "Service lost: ${service.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "Discovery stopped")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery start failed: $errorCode")
                    if (!cont.isCompleted) {
                        cont.resumeWithException(Exception("NSD discovery start failed: $errorCode"))
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery stop failed: $errorCode")
                }
            }

            try {
                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start discovery", e)
                if (!cont.isCompleted) {
                    cont.resume(null)
                }
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to stop discovery", e)
                }
            }
        }
    }
}
