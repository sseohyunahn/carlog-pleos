package com.carlog.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalCarLogColors = staticCompositionLocalOf { NightColors }
val LocalCarLogTypography = staticCompositionLocalOf { CarLogTypography() }
val LocalCarLogDimens = staticCompositionLocalOf { CarLogDimens() }

/**
 * CarLog theme: provides MaterialTheme (so stock M3 components follow CarLog) plus the CarLog tokens.
 *
 * darkTheme rule:
 * - Default is `true` (night). Night is the base theme and must stay dark (Android for Cars, negative polarity).
 * - The day theme is optional. To follow the car, pass `darkTheme = isSystemInDarkTheme()`: on Android
 *   Automotive the system night mode follows the vehicle's day/night signal (headlights / time of day), and
 *   isSystemInDarkTheme() reads Configuration.uiMode, so the app switches with the car. Only do this after the
 *   day theme has been checked on the Pleos emulator; until then keep the default.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CarLogTheme(
    darkTheme: Boolean = true,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) NightColors else DayColors
    val type = if (compact) CarLogTypography().forCompactScreen() else CarLogTypography()
    val dimens = CarLogDimens()
    CompositionLocalProvider(
        LocalCarLogColors provides colors,
        LocalCarLogTypography provides type,
        LocalCarLogDimens provides dimens,
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides if (compact) 48.dp else 76.dp,
        androidx.compose.material3.LocalRippleConfiguration provides null,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = type.toMaterialTypography(),
            shapes = CarLogMaterialShapes,
        ) {
            // MaterialTheme defaults Text to bodyLarge (30 Medium); the mockups' running text is bodyM (28 Regular).
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextStyle provides type.bodyM,
                content = content,
            )
        }
    }
}

/** Token accessors: `CarLog.colors.tag`, `CarLog.type.feature`, `CarLog.dimens.touchMin`. */
object CarLog {
    val colors: CarLogColors
        @Composable @ReadOnlyComposable get() = LocalCarLogColors.current
    val type: CarLogTypography
        @Composable @ReadOnlyComposable get() = LocalCarLogTypography.current
    val dimens: CarLogDimens
        @Composable @ReadOnlyComposable get() = LocalCarLogDimens.current
    /** Not theme-dependent; here only so all tokens are reachable from one place. */
    val shapes: CarLogShapes get() = CarLogShapes
}
