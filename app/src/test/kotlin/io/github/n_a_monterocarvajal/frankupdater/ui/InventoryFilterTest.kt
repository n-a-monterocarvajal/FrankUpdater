package io.github.n_a_monterocarvajal.frankupdater.ui

import io.github.n_a_monterocarvajal.frankupdater.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryFilterTest {
    private val apps = listOf(
        app("Calculator", "org.example.calc", system = false),
        app("System UI", "com.android.systemui", system = true),
    )

    @Test fun `filters app type and matches name or package`() {
        assertEquals(listOf("org.example.calc"), filterInstalledApps(apps, "calc", InventoryFilter.User).map { it.packageName })
        assertEquals(listOf("com.android.systemui"), filterInstalledApps(apps, "SYSTEMUI", InventoryFilter.System).map { it.packageName })
    }

    private fun app(name: String, packageName: String, system: Boolean) = InstalledApp(
        displayName = name,
        packageName = packageName,
        versionName = "1",
        versionCode = 1,
        signingCertificateHistory = emptyList(),
        splitSourceDirs = emptyList(),
        installerSource = null,
        isSystemApp = system,
        isEnabled = true,
    )
}
