package com.wildmusicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class ThemeMode(val title: String) {
    SYSTEM("System Default"),
    DARK("Dark (Midnight)"),
    BLACK("Black (AMOLED)"),
    LIGHT("Light")
}

/**
 * The app's main color, used for buttons, highlights and tinted cards. [WALLPAPER] takes the colors of
 * the phone's wallpaper (Android 12+); the others pick a color for the light and the dark themes.
 */
enum class AccentColor(val title: String, val light: Color, val dark: Color) {
    BLUE("Blue", PrimaryAccent, PrimaryAccent),
    PURPLE("Purple", Color(0xFF7C3AED), Color(0xFF8B5CF6)),
    GREEN("Green", Color(0xFF16A34A), Color(0xFF22C55E)),
    TEAL("Teal", Color(0xFF0D9488), Color(0xFF14B8A6)),
    ORANGE("Orange", Color(0xFFEA580C), Color(0xFFF97316)),
    RED("Red", Color(0xFFDC2626), Color(0xFFEF4444)),
    PINK("Pink", Color(0xFFDB2777), Color(0xFFEC4899)),
    WALLPAPER("Wallpaper", PrimaryAccent, PrimaryAccent)
}

// How strongly the accent tints the "container" colors (chips, tonal buttons, banners)
private const val LIGHT_TINT = 0.16f
private const val DARK_TINT = 0.28f

private fun darkScheme(accent: Color, onLightAccent: Color, black: Boolean): ColorScheme {
    val background = if (black) Color.Black else BackgroundDark
    val surfaceVariant = if (black) Color(0xFF1A1A1A) else SurfaceVariantDark
    val tinted = lerp(surfaceVariant, accent, DARK_TINT)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        // Message bars are light on the dark theme: their buttons use the deeper shade
        inversePrimary = onLightAccent,
        primaryContainer = surfaceVariant,
        onPrimaryContainer = TextPrimaryDark,
        secondary = SecondaryAccent,
        onSecondary = Color.Black,
        secondaryContainer = tinted,
        onSecondaryContainer = TextPrimaryDark,
        tertiary = TertiaryAccent,
        tertiaryContainer = tinted,
        onTertiaryContainer = TextPrimaryDark,
        background = background,
        onBackground = TextPrimaryDark,
        surface = if (black) Color(0xFF0A0A0A) else SurfaceDark,
        onSurface = TextPrimaryDark,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = TextSecondaryDark,
        // Cards, sheets and menus: our own greys instead of Material's default purple-greys
        surfaceContainerLowest = background,
        surfaceContainerLow = if (black) Color(0xFF0F0F0F) else Color(0xFF15181D),
        surfaceContainer = if (black) Color(0xFF141414) else Color(0xFF1C1F25),
        surfaceContainerHigh = if (black) Color(0xFF1F1F1F) else SurfaceContainerHighDark,
        surfaceContainerHighest = if (black) Color(0xFF262626) else Color(0xFF343845),
        outline = TextMutedDark,
        error = ErrorColor
    )
}

private fun lightScheme(accent: Color, onDarkAccent: Color): ColorScheme {
    val tinted = lerp(SurfaceVariantLight, accent, LIGHT_TINT)
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        inversePrimary = onDarkAccent,
        primaryContainer = SurfaceVariantLight,
        onPrimaryContainer = TextPrimaryLight,
        secondary = SecondaryAccent,
        onSecondary = Color.White,
        secondaryContainer = tinted,
        onSecondaryContainer = TextPrimaryLight,
        tertiary = TertiaryAccent,
        tertiaryContainer = tinted,
        onTertiaryContainer = TextPrimaryLight,
        background = BackgroundLight,
        onBackground = TextPrimaryLight,
        surface = SurfaceLight,
        onSurface = TextPrimaryLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = TextSecondaryLight,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F5F9),
        surfaceContainer = Color(0xFFEEF2F6),
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = Color(0xFFD9E0E8),
        outline = TextMutedLight,
        error = ErrorColor
    )
}

/**
 * The app theme: [themeMode] picks light, dark or black, [accent] the main color.
 * [wallpaperScheme] gives the phone's wallpaper colors (dark = true/false), where the platform has them.
 */
@Composable
fun WildMusicPlayerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.BLUE,
    wallpaperScheme: ((dark: Boolean) -> ColorScheme)? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.BLACK -> true
        ThemeMode.LIGHT -> false
    }
    val isBlack = themeMode == ThemeMode.BLACK

    val wallpaper = wallpaperScheme?.takeIf { accent == AccentColor.WALLPAPER }?.invoke(isDark)
    val colorScheme = when {
        wallpaper != null && isBlack -> wallpaper.copy(background = Color.Black, surface = Color(0xFF0A0A0A))
        wallpaper != null -> wallpaper
        isDark -> darkScheme(accent.dark, accent.light, isBlack)
        else -> lightScheme(accent.light, accent.dark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
