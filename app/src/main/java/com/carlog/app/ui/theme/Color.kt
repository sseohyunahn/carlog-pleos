package com.carlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Semantic roles from ZIP 1; blue/navy values from ZIP 2's Common.kt. */
@Immutable
data class CarLogColors(
    val isNight: Boolean,
    val bg: Color, val surface: Color, val raised: Color,
    val divider: Color, val outline: Color,
    val text: Color, val textSecondary: Color, val textTertiary: Color, val disabled: Color,
    val primary: Color, val onPrimary: Color,
    val secondaryButton: Color, val onSecondaryButton: Color,
    val tag: Color, val onTag: Color, val onTagSecondary: Color, val onTagWarning: Color, val tagHole: Color,
    val accent: Color, val accentMark: Color,
    val warning: Color, val danger: Color, val success: Color,
    val scrim: Color, val press: Color, val edge: Color,
    val overlayBg: Color, val overlayText: Color, val overlayTextSecondary: Color,
)

object CarLogStaticColors {
    val OverlayBg = Color(0xFF091522)
    val OverlayText = Color(0xFFF1F6FC)
    val OverlayTextSecondary = Color(0xFFB1C2D4)
    val OverlayButton = Color(0xFF8ACFFF)
    val OnOverlayButton = Color(0xFF091522)
}

val NightColors = CarLogColors(
    isNight = true,
    bg = Color(0xFF091522), surface = Color(0xFF142638), raised = Color(0xFF1E354C),
    divider = Color(0xFF38516A), outline = Color(0xFF52677C),
    text = Color(0xFFF1F6FC), textSecondary = Color(0xFFB7DFFF), textTertiary = Color(0xFFB1C2D4),
    disabled = Color(0xFF52677C),
    primary = Color(0xFF8ACFFF), onPrimary = Color(0xFF091522),
    secondaryButton = Color(0xFF1E354C), onSecondaryButton = Color(0xFFF1F6FC),
    tag = Color(0xFF002C5F), onTag = Color.White, onTagSecondary = Color(0xFFB7DFFF),
    onTagWarning = Color(0xFFFFD08A), tagHole = Color(0xFF091522),
    accent = Color(0xFF8ACFFF), accentMark = Color(0xFF8ACFFF),
    warning = Color(0xFFFFD08A), danger = Color(0xFFE18383), success = Color(0xFF8AE1BF),
    scrim = Color(0xB8091522), press = Color(0x14F1F6FC), edge = Color(0x0FF1F6FC),
    overlayBg = CarLogStaticColors.OverlayBg, overlayText = CarLogStaticColors.OverlayText,
    overlayTextSecondary = CarLogStaticColors.OverlayTextSecondary,
)

val DayColors = NightColors.copy(
    isNight = false,
    bg = Color(0xFFF2F5F8), surface = Color.White, raised = Color(0xFFE8EFF6),
    divider = Color(0xFFD4DFE9), outline = Color(0xFF52677C),
    text = Color(0xFF10263F), textSecondary = Color(0xFF52677C), textTertiary = Color(0xFF52677C),
    disabled = Color(0xFF6D8DA9),
    primary = Color(0xFF0057B8), onPrimary = Color.White,
    secondaryButton = Color(0xFFE8EFF6), onSecondaryButton = Color(0xFF10263F),
    tagHole = Color(0xFFF2F5F8),
    accent = Color(0xFF0057B8), accentMark = Color(0xFF0057B8),
    warning = Color(0xFF955000), danger = Color(0xFFA12F3A), success = Color(0xFF126C4D),
    scrim = Color(0x8F10263F), press = Color(0x0F10263F), edge = Color(0xFFD4DFE9),
)

fun CarLogColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isNight) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = raised, onPrimaryContainer = text,
        secondary = textSecondary, onSecondary = bg,
        secondaryContainer = secondaryButton, onSecondaryContainer = onSecondaryButton,
        tertiary = accent, onTertiary = onPrimary, tertiaryContainer = raised, onTertiaryContainer = text,
        background = bg, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = raised, onSurfaceVariant = textSecondary, surfaceTint = Color.Transparent,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = primary,
        error = danger, onError = bg, errorContainer = raised, onErrorContainer = danger,
        outline = outline, outlineVariant = divider, scrim = scrim,
        surfaceBright = raised, surfaceDim = bg, surfaceContainerLowest = bg,
        surfaceContainerLow = bg, surfaceContainer = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = raised,
    )
}
