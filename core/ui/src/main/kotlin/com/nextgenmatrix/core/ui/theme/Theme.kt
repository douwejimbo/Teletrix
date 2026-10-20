package com.nextgenmatrix.core.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
enum class AppContrastMode {
    Standard,
    Amoled,
}

/** Dark is the default; System follows the device setting. */
@Immutable
enum class AppThemeMode {
    Dark,
    Light,
    System,
}

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEFF),
    onPrimaryContainer = Color(0xFF0E1B45),
    secondary = BrandPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1EAFF),
    onSecondaryContainer = Color(0xFF2A1146),
    tertiary = BrandPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEAF3),
    onTertiaryContainer = Color(0xFF4A1230),
    background = Color(0xFFF3F7FF),
    onBackground = Color(0xFF10182C),
    surface = Color(0xFFFBFDFF),
    onSurface = Color(0xFF151C2E),
    surfaceVariant = Color(0xFFE2E8F8),
    onSurfaceVariant = Color(0xFF44506B),
    outline = Color(0xFF6A7693),
    outlineVariant = Color(0xFFC1C9DE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF13255F),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = BrandPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF332152),
    onSecondaryContainer = Color(0xFFE8DDFF),
    tertiary = BrandCyan,
    onTertiary = Color(0xFF002633),
    tertiaryContainer = Color(0xFF003547),
    onTertiaryContainer = Color(0xFFBAEBFF),
    background = Color(0xFF060913),
    onBackground = Color(0xFFDCE5FF),
    surface = SurfaceDark,
    onSurface = Color(0xFFD9E2FF),
    surfaceVariant = Color(0xFF1A2238),
    onSurfaceVariant = Color(0xFFBBC6E8),
    outline = Color(0xFF8793B3),
    outlineVariant = Color(0xFF2E3650),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private fun amoledColorScheme(base: ColorScheme) =
    base.copy(
        background = AmoledBlack,
        surface = AmoledBlack,
        surfaceVariant = Color(0xFF05070D),
        surfaceContainer = Color(0xFF04060D),
        surfaceContainerHigh = Color(0xFF0A1120),
        surfaceContainerHighest = Color(0xFF10192E),
    )

@Composable
fun TeletrixTheme(
    darkTheme: Boolean = true,
    contrastMode: AppContrastMode = AppContrastMode.Standard,
    dynamicColor: Boolean = false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }.let { scheme ->
        if (darkTheme && contrastMode == AppContrastMode.Amoled) {
            amoledColorScheme(scheme)
        } else {
            scheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TeletrixTypography,
        content = content,
    )
}
