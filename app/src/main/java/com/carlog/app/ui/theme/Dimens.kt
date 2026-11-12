package com.carlog.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes from carlog-tokens.json `space`, `size`, `layout` and components.css.
 * Units are dp at the Pleos emulator's assumed 160 dpi (1dp = 1px), so values look large on a phone preview.
 */
@Immutable
data class CarLogDimens(
    // space (8 dp grid)
    val spaceXs: Dp = 8.dp,
    val spaceS: Dp = 16.dp,
    val spaceM: Dp = 24.dp,
    val spaceL: Dp = 32.dp,
    val spaceXl: Dp = 48.dp,
    val spaceXxl: Dp = 64.dp,
    /** Side padding in the half window (map 1:1 split, default target). */
    val gutterHalf: Dp = 40.dp,
    /** Side padding in the full-width window. */
    val gutterFull: Dp = 56.dp,
    /** Minimum gap between two touch targets. */
    val targetGap: Dp = 24.dp,

    // touch & buttons
    /** Android for Cars minimum touch target. Nothing tappable is smaller. */
    val touchMin: Dp = 76.dp,
    val buttonLHeight: Dp = 100.dp,
    val buttonLMinWidth: Dp = 296.dp,
    val buttonMHeight: Dp = 80.dp,
    /** Larger than Pleos Small (64) on purpose: Android for Cars minimum is 76. */
    val buttonSHeight: Dp = 76.dp,
    val buttonLPadding: Dp = 48.dp,
    val buttonMPadding: Dp = 36.dp,
    val buttonSPadding: Dp = 28.dp,
    val buttonIconGap: Dp = 14.dp,

    // icons (stroke 3.6 on a 48 grid, round caps)
    val iconL: Dp = 48.dp,
    val iconM: Dp = 40.dp,
    val iconS: Dp = 32.dp,
    val avatarL: Dp = 120.dp,
    val avatarM: Dp = 76.dp,
    /** Avatar inside the app-bar driver button. */
    val avatarS: Dp = 56.dp,

    // components
    val appBarHeight: Dp = 112.dp,
    val tabHeight: Dp = 80.dp,
    val railWidth: Dp = 320.dp,
    val railItemHeight: Dp = 88.dp,
    val rowMinHeight: Dp = 112.dp,
    val inputMinHeight: Dp = 96.dp,
    val categoryBadge: Dp = 76.dp,
    val linkDot: Dp = 12.dp,
    val recordDot: Dp = 16.dp,
    val focusRing: Dp = 3.dp,
    val borderWidth: Dp = 2.dp,

    // layout (assumed Pleos Connect emulator 2560x1440 @160dpi; verify with `adb shell wm size` / `wm density`)
    val windowHalfWidth: Dp = 1060.dp,
    val windowFullWidth: Dp = 2120.dp,
    val windowHeight: Dp = 1256.dp,
    /** Switch from tabs (half) to left rail (full) at or above this width. */
    val fullLayoutBreakpoint: Dp = 1600.dp,
)
