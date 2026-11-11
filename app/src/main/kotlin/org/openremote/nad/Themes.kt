package org.openremote.nad

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeChoice(val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Nad(R.string.theme_nad), Dracula(R.string.theme_dracula)
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val LightScheme = lightColorScheme(
    primary = c(0xC9241C), onPrimary = Color.White,
    background = c(0xF6F6F7), onBackground = c(0x151517),
    surface = c(0xFFFFFF), onSurface = c(0x151517),
    surfaceVariant = c(0xE9E9EC), onSurfaceVariant = c(0x5C5C62),
    surfaceContainerLow = c(0xFFFFFF), surfaceContainerHighest = c(0xE9E9EC),
    outline = c(0x8A8A90), outlineVariant = c(0xD3D3D8),
)

private val DarkScheme = darkColorScheme(
    primary = c(0xFF6A62), onPrimary = c(0x2A0504),
    background = c(0x121214), onBackground = c(0xF2F2F4),
    surface = c(0x1C1C1F), onSurface = c(0xF2F2F4),
    surfaceVariant = c(0x2A2A2E), onSurfaceVariant = c(0xA9A9B0),
    surfaceContainerLow = c(0x1C1C1F), surfaceContainerHighest = c(0x2A2A2E),
    outline = c(0x77777E), outlineVariant = c(0x38383D),
)

// NAD-inspired: #0D0D0D / #212123 / #EE2E25 / #FFFFFF. Red is the single hot accent, used for "on" states only. Colors only, no logo.
private val NadScheme = darkColorScheme(
    primary = c(0xEE2E25), onPrimary = Color.White,
    background = c(0x0D0D0D), onBackground = Color.White,
    surface = c(0x212123), onSurface = Color.White,
    surfaceVariant = c(0x2C2C2F), onSurfaceVariant = c(0xA8A8AD),
    surfaceContainerLow = c(0x212123), surfaceContainerHighest = c(0x2C2C2F),
    outline = c(0x7C7C82), outlineVariant = c(0x3A3A3E),
)

// Dracula (draculatheme.com): bg #282A36, current line #44475A, fg #F8F8F2, purple #BD93F9, comment #6272A4.
private val DraculaScheme = darkColorScheme(
    primary = c(0xBD93F9), onPrimary = c(0x282A36),
    secondary = c(0xFF79C6), tertiary = c(0x50FA7B),
    background = c(0x282A36), onBackground = c(0xF8F8F2),
    surface = c(0x343746), onSurface = c(0xF8F8F2),
    surfaceVariant = c(0x44475A), onSurfaceVariant = c(0xB5B9CF),
    surfaceContainerLow = c(0x343746), surfaceContainerHighest = c(0x44475A),
    outline = c(0x6272A4), outlineVariant = c(0x4B4F66),
)

@Composable
fun AmpTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when (choice) {
        ThemeChoice.System -> when {
            Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            dark -> DarkScheme else -> LightScheme
        }
        ThemeChoice.Light -> LightScheme
        ThemeChoice.Dark -> DarkScheme
        ThemeChoice.Nad -> NadScheme
        ThemeChoice.Dracula -> DraculaScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val ThemeChoice.isDark get() = this != ThemeChoice.Light
