package com.bunny.util

import android.os.Build
import com.bunny.BuildConfig

object NetworkEnvironment {

    fun isEmulator(): Boolean {
        return ((Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MODEL.contains("VirtualBox")
                || Build.MODEL.contains("VMware")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator"))
    }

    fun requireBaseUrl(): String {
        val emulatorUrl = "http://10.0.2.2:8080/"
        val configured = BuildConfig.BASE_URL.trim()

        return when {
            isEmulator() -> emulatorUrl
            configured.isNotBlank() -> configured
            else -> emulatorUrl
        }
    }

    fun requireSocketUrl(): String {
        val emulatorUrl = "http://10.0.2.2:8080"
        val configured = BuildConfig.SOCKET_URL.trim()

        return when {
            isEmulator() -> emulatorUrl
            configured.isNotBlank() -> configured
            else -> emulatorUrl
        }
    }

    fun backendIp(): String {
        return BuildConfig.BACKEND_IP.trim().ifBlank { "10.0.2.2" }
    }
}
