package com.carlog.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/*
 * CarLog type scale. Source: carlog-tokens.json `typography` = the Pleos type scale + one hero step (feature).
 * Sizes are dp in the tokens; here they are sp (1sp = 1dp at fontScale 1.0).
 */

/** Asta Sans variable font, bundled under the SIL OFL; no network font request at runtime. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val AstaSans = FontFamily(
    Font(com.carlog.app.R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(com.carlog.app.R.font.asta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(com.carlog.app.R.font.asta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(com.carlog.app.R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(com.carlog.app.R.font.asta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)
/** Tabular figures so km / % / times do not jump while values update. */
private const val TABULAR_NUMS = "tnum"

/** Centre glyphs vertically in the line height, like CSS line-height (keeps the mockup's half-leading). */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, lineHeight: Int, weight: Int, letterSpacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = AstaSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing,
    fontFeatureSettings = TABULAR_NUMS,
    lineHeightStyle = TightLineHeight,
)

@Immutable
data class CarLogTypography(
    /** 88/96 ExtraBold. One per screen: the key value (parking spot, battery, recording seconds). */
    // v1.1: Pleos sizes kept; line heights opened up for Korean reading text (~1.45-1.5x),
    // tracking tightens as size grows (display -0.03em, headline -0.02em, title -0.01em, label +0.01em).
    val feature: TextStyle = style(88, 96, 800, letterSpacing = (-2.64).sp),
    val headlineL: TextStyle = style(60, 72, 800, letterSpacing = (-1.2).sp),
    val headlineM: TextStyle = style(56, 68, 800, letterSpacing = (-1.12).sp),
    val headlineS: TextStyle = style(48, 60, 700, letterSpacing = (-0.96).sp),
    val titleL: TextStyle = style(40, 52, 700, letterSpacing = (-0.4).sp),
    val titleM: TextStyle = style(36, 48, 700, letterSpacing = (-0.36).sp),
    val titleS: TextStyle = style(32, 44, 700, letterSpacing = (-0.32).sp),
    val bodyL: TextStyle = style(30, 44, 500),
    val bodyM: TextStyle = style(28, 42, 400),
    val bodyS: TextStyle = style(26, 38, 400),
    /** 24/32 Medium. The smallest size a driver is asked to read. */
    val labelM: TextStyle = style(24, 32, 500, letterSpacing = 0.24.sp),
    /** 20/28 Medium. Only for information unrelated to driving (version number etc.). */
    val labelS: TextStyle = style(20, 28, 500, letterSpacing = 0.2.sp),

    // Component text (components.css)
    val buttonL: TextStyle = style(32, 32, 700, letterSpacing = (-0.32).sp),
    val buttonM: TextStyle = style(28, 28, 700),
    val buttonS: TextStyle = style(26, 26, 700),
    /** Tabs and rail items: 28 SemiBold, selected item ExtraBold. */
    val nav: TextStyle = style(28, 28, 600),
    /** App bar wordmark "CarLog". */
    val wordmark: TextStyle = style(40, 40, 800, letterSpacing = (-1.2).sp),
)

/**
 * Material 3 mapping so stock components get CarLog sizes.
 * Note: Material Buttons use labelLarge, so labelLarge = buttonM (28 Bold), not a label token.
 */
fun CarLogTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = feature,
    displayMedium = headlineL,
    displaySmall = headlineM,
    headlineLarge = headlineL,
    headlineMedium = headlineM,
    headlineSmall = headlineS,
    titleLarge = titleL,
    titleMedium = titleM,
    titleSmall = titleS,
    bodyLarge = bodyL,
    bodyMedium = bodyM,
    bodySmall = bodyS,
    labelLarge = buttonM,
    labelMedium = labelM,
    labelSmall = labelS,
)

/** Phone typography respects the system font scale without shrinking the whole vehicle interface. */
fun CarLogTypography.forCompactScreen() = copy(
    feature = style(36, 44, 800), headlineL = style(28, 36, 800),
    headlineM = style(26, 34, 800), headlineS = style(24, 32, 700),
    titleL = style(22, 30, 700), titleM = style(20, 28, 700), titleS = style(18, 26, 700),
    bodyL = style(18, 27, 500), bodyM = style(16, 24, 400), bodyS = style(14, 21, 400),
    labelM = style(14, 20, 500), labelS = style(12, 18, 500),
    buttonL = style(16, 22, 700), buttonM = style(14, 20, 700), buttonS = style(14, 20, 700),
    nav = style(12, 18, 600), wordmark = style(26, 32, 800),
)
