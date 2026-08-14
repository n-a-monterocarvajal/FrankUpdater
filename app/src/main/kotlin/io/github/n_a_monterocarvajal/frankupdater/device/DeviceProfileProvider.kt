/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted from Aurora Store's NativeDeviceInfoProvider at
 * f1bb85ff9dcbcc5cae07779d4e13f77b6b7f245b (source file GPL-2.0-or-later).
 */
package io.github.n_a_monterocarvajal.frankupdater.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.model.PlayDeviceProfile

fun interface GenericDeviceProfileProvider {
    fun getDeviceProfile(): GenericDeviceProfile
}

fun interface PlayDeviceProfileProvider {
    fun getDeviceProfile(): PlayDeviceProfile
}

class AndroidGenericDeviceProfileProvider(context: Context) : GenericDeviceProfileProvider {
    private val applicationContext = context.applicationContext

    @Suppress("DEPRECATION", "UNNECESSARY_SAFE_CALL")
    override fun getDeviceProfile(): GenericDeviceProfile {
        val resources = applicationContext.resources
        val configuration = resources.configuration
        val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            List(configuration.locales.size()) { index ->
                configuration.locales[index].toLanguageTag()
            }
        } else {
            listOf(configuration.locale.toLanguageTag())
        }
        val features = applicationContext.packageManager.systemAvailableFeatures
            ?.mapNotNull { it.name }
            ?.toSortedSet()
            .orEmpty()
        val sharedLibraries = applicationContext.packageManager.systemSharedLibraryNames
            ?.toSortedSet()
            .orEmpty()
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
            as? ActivityManager

        return GenericDeviceProfile(
            sdk = Build.VERSION.SDK_INT,
            codename = Build.VERSION.CODENAME,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            densityDpi = resources.displayMetrics.densityDpi,
            locales = locales,
            systemFeatures = features,
            androidRelease = Build.VERSION.RELEASE,
            screenWidthPixels = resources.displayMetrics.widthPixels,
            screenHeightPixels = resources.displayMetrics.heightPixels,
            sharedLibraries = sharedLibraries,
            glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0,
        )
    }
}

class AndroidPlayDeviceProfileProvider(
    private val genericProvider: GenericDeviceProfileProvider,
) : PlayDeviceProfileProvider {
    override fun getDeviceProfile(): PlayDeviceProfile = PlayDeviceProfile(
        generic = genericProvider.getDeviceProfile(),
        fingerprint = Build.FINGERPRINT,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        brand = Build.BRAND,
        product = Build.PRODUCT,
        device = Build.DEVICE,
        buildId = Build.ID,
    )
}
