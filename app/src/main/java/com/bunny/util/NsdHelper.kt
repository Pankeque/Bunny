package com.bunny.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NsdHelper(private val context: Context) {

    companion object {
        private const val TAG = "NsdHelper"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME = "Bunny Backend"
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    suspend fun discoverBackend(timeoutMs: Long = 5000): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}")
                if (service.serviceName.contains(SERVICE_NAME, ignoreCase = true)) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
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
                cont.resumeWithException(Exception("NSD discovery start failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        cont.invokeOnCancellation {
            nsdManager.stopServiceDiscovery(listener)
        }

        ContextCompat.getMainExecutor(context).execute {
            Thread.sleep(timeoutMs)
            if (!cont.isCompleted) {
                nsdManager.stopServiceDiscovery(listener)
                cont.resume(null)
            }
        }
    }
}
