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
    primaryContainer = c(0xFFD9D5), onPrimaryContainer = c(0x410002),
    secondary = c(0x1C1C1F), onSecondary = Color.White,
    secondaryContainer = c(0x1C1C1F), onSecondaryContainer = Color.White,
    background = c(0xEEEEF0), onBackground = c(0x151517),
    surface = c(0xFFFFFF), onSurface = c(0x151517), surfaceTint = c(0xFFFFFF),
    surfaceContainerLow = c(0xFFFFFF), surfaceContainer = c(0xFFFFFF), surfaceContainerHighest = c(0xE3E3E7),
    surfaceVariant = c(0xE9E9EC), onSurfaceVariant = c(0x55555B),
    outline = c(0x7D7D84), outlineVariant = c(0xCFCFD4),
)

private val DarkScheme = darkColorScheme(
    primary = c(0xFF5449), onPrimary = Color.White,
    primaryContainer = c(0x5C1A16), onPrimaryContainer = c(0xFFDAD5),
    secondary = c(0xECECEF), onSecondary = c(0x121214),
    secondaryContainer = c(0xECECEF), onSecondaryContainer = c(0x121214),
    background = c(0x121214), onBackground = c(0xF2F2F4),
    surface = c(0x1C1C1F), onSurface = c(0xF2F2F4), surfaceTint = c(0x1C1C1F),
    surfaceContainerLow = c(0x1C1C1F), surfaceContainer = c(0x1C1C1F), surfaceContainerHighest = c(0x333338),
    surfaceVariant = c(0x2A2A2E), onSurfaceVariant = c(0xAAAAB1),
    outline = c(0x84848B), outlineVariant = c(0x44444A),
)

// NAD-inspired: anchors #0D0D0D / #212123 / #EE2E25 / #FFFFFF. Graphite and white like the front panel;
// red only for power-on, the volume slider and mute. Colors only, no logo.
private val NadScheme = darkColorScheme(
    primary = c(0xEE2E25), onPrimary = Color.White,
    primaryContainer = c(0x4A1210), onPrimaryContainer = c(0xFFDAD5),
    secondary = c(0xE8E8EA), onSecondary = c(0x0D0D0D),
    secondaryContainer = Color.White, onSecondaryContainer = c(0x0D0D0D),
    tertiary = c(0xFF6B61), onTertiary = c(0x0D0D0D),
    background = c(0x0D0D0D), onBackground = Color.White,
    surface = c(0x212123), onSurface = Color.White, surfaceTint = c(0x212123),
    surfaceContainerLowest = c(0x0D0D0D), surfaceContainerLow = c(0x18181A), surfaceContainer = c(0x212123),
    surfaceContainerHigh = c(0x2B2B2E), surfaceContainerHighest = c(0x353538),
    surfaceVariant = c(0x2B2B2E), onSurfaceVariant = c(0xA8A8AD),
    outline = c(0x8A8A90), outlineVariant = c(0x48484C),
    inverseSurface = Color.White, inverseOnSurface = c(0x0D0D0D),
)

// Dracula (draculatheme.com): bg #282A36, current line #44475A, fg #F8F8F2, purple #BD93F9, pink #FF79C6, comment #6272A4.
private val DraculaScheme = darkColorScheme(
    primary = c(0xBD93F9), onPrimary = c(0x282A36),
    primaryContainer = c(0x6B3A62), onPrimaryContainer = c(0xFFD6EE),
    secondary = c(0xBD93F9), onSecondary = c(0x282A36),
    secondaryContainer = c(0xBD93F9), onSecondaryContainer = c(0x282A36),
    tertiary = c(0xFF79C6),
    background = c(0x282A36), onBackground = c(0xF8F8F2),
    surface = c(0x3A3D4E), onSurface = c(0xF8F8F2), surfaceTint = c(0x3A3D4E),
    surfaceContainerLow = c(0x3A3D4E), surfaceContainer = c(0x3A3D4E), surfaceContainerHighest = c(0x4D5066),
    surfaceVariant = c(0x44475A), onSurfaceVariant = c(0xBCC0D6),
    outline = c(0x7B88B8), outlineVariant = c(0x555972),
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
