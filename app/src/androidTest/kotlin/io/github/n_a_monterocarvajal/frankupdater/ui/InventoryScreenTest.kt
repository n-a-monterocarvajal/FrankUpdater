package io.github.n_a_monterocarvajal.frankupdater.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.n_a_monterocarvajal.frankupdater.device.GenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.inventory.InstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.model.GenericDeviceProfile
import io.github.n_a_monterocarvajal.frankupdater.model.InstalledApp
import io.github.n_a_monterocarvajal.frankupdater.ui.theme.FrankUpdaterTheme
import org.junit.Rule
import org.junit.Test

class InventoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inventoryShowsDeviceCountsAndApplicationState() {
        val apps = listOf(
            installedApp(
                displayName = "Aplicación de usuario",
                packageName = "org.example.user",
                isSystemApp = false,
                isEnabled = true,
            ),
            installedApp(
                displayName = "Servicio del sistema",
                packageName = "org.example.system",
                isSystemApp = true,
                isEnabled = false,
            ),
        )
        val repository = InstalledAppRepository { apps }
        val profileProvider = GenericDeviceProfileProvider {
            GenericDeviceProfile(
                sdk = 23,
                codename = "REL",
                supportedAbis = listOf("armeabi-v7a"),
                densityDpi = 160,
                locales = listOf("es-CL"),
                systemFeatures = emptySet(),
                androidRelease = "6.0",
            )
        }

        composeRule.setContent {
            FrankUpdaterTheme {
                InventoryRoute(
                    installedAppRepository = repository,
                    deviceProfileProvider = profileProvider,
                )
            }
        }

        composeRule.onNodeWithText("2 aplicaciones · 1 de usuario · 1 de sistema")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Android 6.0 · API 23 · armeabi-v7a · 160 dpi")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Aplicación de usuario").assertIsDisplayed()
        composeRule.onNodeWithText("Servicio del sistema").assertIsDisplayed()
        composeRule.onNodeWithText("Deshabilitada").assertIsDisplayed()
    }

    private fun installedApp(
        displayName: String,
        packageName: String,
        isSystemApp: Boolean,
        isEnabled: Boolean,
    ) = InstalledApp(
        displayName = displayName,
        packageName = packageName,
        versionName = "1.0",
        versionCode = 1,
        signingCertificateHistory = emptyList(),
        splitSourceDirs = emptyList(),
        installerSource = null,
        isSystemApp = isSystemApp,
        isEnabled = isEnabled,
    )
}
