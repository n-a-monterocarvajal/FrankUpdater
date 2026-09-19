package io.github.n_a_monterocarvajal.frankupdater.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Tonal roles for the launcher icon blue (#0A65CC). Used as is below Android 12 and when dynamic color is off.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7), onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF), onTertiaryContainer = Color(0xFF251431),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9FD), onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFAF9FD), onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C7CF),
    inverseSurface = Color(0xFF2F3033), inverseOnSurface = Color(0xFFF1F0F4), inversePrimary = Color(0xFF9ECAFF),
    surfaceTint = Color(0xFF0061A4), scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDAD9DD), surfaceBright = Color(0xFFFAF9FD),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F3F7),
    surfaceContainer = Color(0xFFEEEDF1), surfaceContainerHigh = Color(0xFFE8E7EC),
    surfaceContainerHighest = Color(0xFFE3E2E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858), onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4), onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F), onTertiaryContainer = Color(0xFFF2DAFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121316), onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121316), onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE2E2E6), inverseOnSurface = Color(0xFF2F3033), inversePrimary = Color(0xFF0061A4),
    surfaceTint = Color(0xFF9ECAFF), scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF121316), surfaceBright = Color(0xFF38393C),
    surfaceContainerLowest = Color(0xFF0D0E11), surfaceContainerLow = Color(0xFF1A1B1F),
    surfaceContainer = Color(0xFF1E1F23), surfaceContainerHigh = Color(0xFF292A2D),
    surfaceContainerHighest = Color(0xFF343538),
)

/**
 * Brand theme. Dynamic color only on Android 12+ (platform API); older devices get the same brand scheme
 * statically. MaterialExpressiveTheme and its motion scheme are internal in Material 3 1.4.0 (decision D-02).
 */
@Composable
fun FrankUpdaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
