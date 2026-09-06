/*
 * Copyright (C) 2021 Rahul Kumar Patel <whyorean@gmail.com>
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Adapted from Aurora Store NativeDeviceInfoProvider at 660670a35cd6980afaf1f9667b7df2144ddc435c.
 */
package io.github.n_a_monterocarvajal.frankupdater.play

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import io.github.n_a_monterocarvajal.frankupdater.device.AndroidGenericDeviceProfileProvider
import java.util.Properties
import java.util.TimeZone

internal fun playDeviceProperties(context: Context): Properties {
    val generic = AndroidGenericDeviceProfileProvider(context).getDeviceProfile()
    val configuration = context.resources.configuration
    // Protocol client versions come from the dependency; device targeting is always native.
    val protocol = Properties().apply {
        context.resources.openRawResource(com.aurora.gplayapi.R.raw.gplayapi_px_9a).use(::load)
    }
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    return Properties().apply {
        setProperty("UserReadableName", "${Build.MANUFACTURER} ${Build.MODEL}")
        setProperty("Build.HARDWARE", Build.HARDWARE)
        setProperty("Build.RADIO", Build.getRadioVersion() ?: "unknown")
        setProperty("Build.BOOTLOADER", Build.BOOTLOADER)
        setProperty("Build.FINGERPRINT", Build.FINGERPRINT)
        setProperty("Build.BRAND", Build.BRAND)
        setProperty("Build.DEVICE", Build.DEVICE)
        setProperty("Build.VERSION.SDK_INT", generic.sdk.toString())
        setProperty("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
        setProperty("Build.MODEL", Build.MODEL)
        setProperty("Build.MANUFACTURER", Build.MANUFACTURER)
        setProperty("Build.PRODUCT", Build.PRODUCT)
        setProperty("Build.ID", Build.ID)
        setProperty("TouchScreen", configuration.touchscreen.toString())
        setProperty("Keyboard", configuration.keyboard.toString())
        setProperty("Navigation", configuration.navigation.toString())
        setProperty("ScreenLayout", (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK).toString())
        setProperty("HasHardKeyboard", (configuration.keyboard == Configuration.KEYBOARD_QWERTY).toString())
        setProperty("HasFiveWayNavigation", (configuration.navigation == Configuration.NAVIGATION_DPAD).toString())
        setProperty("Screen.Density", generic.densityDpi.toString())
        setProperty("Screen.Width", generic.screenWidthPixels.toString())
        setProperty("Screen.Height", generic.screenHeightPixels.toString())
        setProperty("Platforms", generic.supportedAbis.joinToString(","))
        setProperty("Features", generic.systemFeatures.joinToString(","))
        setProperty("SharedLibraries", generic.sharedLibraries.joinToString(","))
        setProperty("Locales", context.assets.locales.joinToString(",") { it.replace('-', '_') })
        setProperty("GL.Version", generic.glEsVersion.toString())
        // ponytail: no EGL context solely for login; add extensions if delivery needs them.
        setProperty("GL.Extensions", "")
        setProperty("LowRamDevice", if (activityManager.isLowRamDevice) "1" else "0")
        setProperty("MaxNumOfCPUCores", Runtime.getRuntime().availableProcessors().toString())
        setProperty("TotalMemoryBytes", memory.totalMem.toString())
        for (key in listOf("GSF.version", "Vending.version", "Vending.versionString")) {
            setProperty(key, requireNotNull(protocol.getProperty(key)))
        }
        setProperty("Client", "android-google")
        setProperty("Roaming", "mobile-notroaming")
        setProperty("TimeZone", TimeZone.getDefault().id)
        setProperty("CellOperator", "")
        setProperty("SimOperator", "")
    }
}
