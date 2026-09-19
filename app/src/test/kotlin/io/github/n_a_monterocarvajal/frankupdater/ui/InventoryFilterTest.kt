package io.github.n_a_monterocarvajal.frankupdater.ui

import io.github.n_a_monterocarvajal.frankupdater.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryFilterTest {
    private val apps = listOf(
        app("Calculator", "org.example.calc", system = false),
        app("System UI", "com.android.systemui", system = true),
        app("Maps", "com.example.maps", system = false, installer = "com.android.vending"),
        app("Guardian", "com.samsung.guardian", system = false, installer = "com.sec.android.app.samsungapps"),
        app("Old", "org.example.old", system = false, enabled = false),
    )

    @Test fun `filters app type and matches name or package`() {
        assertEquals(listOf("org.example.calc"), filterInstalledApps(apps, "calc", InventoryFilter.User).map { it.packageName })
        assertEquals(listOf("com.android.systemui"), filterInstalledApps(apps, "SYSTEMUI", InventoryFilter.System).map { it.packageName })
    }

    @Test fun `hides disabled and Play Store apps but keeps Galaxy Store ones`() {
        assertEquals(listOf("org.example.calc", "com.samsung.guardian"),
            filterInstalledApps(apps, "", InventoryFilter.User, hideDisabled = true, hidePlayStore = true).map { it.packageName })
    }

    private fun app(name: String, packageName: String, system: Boolean, installer: String? = null, enabled: Boolean = true) = InstalledApp(
        displayName = name,
        packageName = packageName,
        versionName = "1",
        versionCode = 1,
        signingCertificateHistory = emptyList(),
        splitSourceDirs = emptyList(),
        installerSource = installer,
        isSystemApp = system,
        isEnabled = enabled,
    )
}
