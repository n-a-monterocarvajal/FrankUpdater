package io.github.n_a_monterocarvajal.frankupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.n_a_monterocarvajal.frankupdater.device.AndroidGenericDeviceProfileProvider
import io.github.n_a_monterocarvajal.frankupdater.inventory.AndroidInstalledAppRepository
import io.github.n_a_monterocarvajal.frankupdater.installer.SystemSessionInstaller
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackageLibrary
import io.github.n_a_monterocarvajal.frankupdater.storage.LocalPackagePipeline
import io.github.n_a_monterocarvajal.frankupdater.storage.RetentionPreferences
import io.github.n_a_monterocarvajal.frankupdater.ui.FrankUpdaterApp
import io.github.n_a_monterocarvajal.frankupdater.ui.theme.FrankUpdaterTheme

class MainActivity : ComponentActivity() {
    private val installedAppRepository by lazy {
        AndroidInstalledAppRepository(applicationContext)
    }
    private val genericDeviceProfileProvider by lazy {
        AndroidGenericDeviceProfileProvider(applicationContext)
    }
    private val packagePipeline by lazy { LocalPackagePipeline(applicationContext) }
    private val packageLibrary by lazy { LocalPackageLibrary(applicationContext) }
    private val retentionPreferences by lazy { RetentionPreferences(applicationContext) }
    private val sessionInstaller by lazy { SystemSessionInstaller(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrankUpdaterTheme {
                FrankUpdaterApp(
                    installedAppRepository = installedAppRepository,
                    deviceProfileProvider = genericDeviceProfileProvider,
                    packagePipeline = packagePipeline,
                    packageLibrary = packageLibrary,
                    retentionPreferences = retentionPreferences,
                    sessionInstaller = sessionInstaller,
                )
            }
        }
    }
}
