package io.github.n_a_monterocarvajal.frankupdater.play

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aurora.gplayapi.data.providers.DeviceInfoProvider
import io.github.n_a_monterocarvajal.frankupdater.MainActivity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayIntegrationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nativeProfileAndAnonymousScreenLoadWithoutNetwork() {
        val properties = playDeviceProperties(compose.activity)
        val provider = DeviceInfoProvider(properties, Locale.getDefault().toString())
        assertEquals(Build.VERSION.SDK_INT, provider.sdkVersion)
        assertEquals(Build.SUPPORTED_ABIS.toList(), properties.getProperty("Platforms").split(','))
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithText("Acceso anónimo").assertIsDisplayed()
        compose.onNodeWithText("Conectar").assertIsDisplayed()
    }
}
