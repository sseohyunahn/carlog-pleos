package com.carlog.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Reference components matching design/web/components.css and the PNG mockups.
 * Motion rule (tokens.motion): no automatic animation while driving. Only the press response is allowed,
 * and it is applied instantly here (no animate*AsState, no ripple).
 */

// ---------------------------------------------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------------------------------------------

enum class CarLogButtonStyle {
    /** Sand at night, Charcoal by day. One per screen area. */
    Primary,
    /** Raised fill. */
    Secondary,
    /** Background fill + 2dp outline. */
    Ghost,
    /** Inside the handover tag: onTag fill, tag-coloured text. */
    OnTag,
    /** Destructive: transparent + danger outline. */
    Danger,
}

/** L 100 / M 80 / S 76 dp tall. All sizes satisfy the 76dp touch minimum. */
enum class CarLogButtonSize { L, M, S }

/**
 * CarLog button. Height = size token, min width 296 for L and 76 (touchMin) otherwise.
 * Pressed: scale 0.95 + 8% darker fill, applied without animation.
 *
 * @param leadingIcon e.g. `{ Icon(Icons.Outlined.PlayArrow, null) }`; it is sized to iconM (iconS for size S)
 *   and tinted with the button's content colour.
 */
@Composable
fun CarLogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CarLogButtonStyle = CarLogButtonStyle.Secondary,
    size: CarLogButtonSize = CarLogButtonSize.M,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val c = CarLog.colors
    val d = CarLog.dimens
    val t = CarLog.type
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val (height, shape, padding) = when (size) {
        CarLogButtonSize.L -> Triple(d.buttonLHeight, CarLogShapes.buttonL, d.buttonLPadding)
        CarLogButtonSize.M -> Triple(d.buttonMHeight, CarLogShapes.buttonM, d.buttonMPadding)
        CarLogButtonSize.S -> Triple(d.buttonSHeight, CarLogShapes.buttonS, d.buttonSPadding)
    }
    val textStyle = when (size) {
        CarLogButtonSize.L -> t.buttonL
        CarLogButtonSize.M -> t.buttonM
        CarLogButtonSize.S -> t.buttonS
    }
    val iconSize = if (size == CarLogButtonSize.S) d.iconS else d.iconM

    var fill: Color
    val content: Color
    var border: Color? = null
    when {
        !enabled -> { fill = Color.Transparent; content = c.disabled; border = c.divider }
        style == CarLogButtonStyle.Primary -> { fill = c.primary; content = c.onPrimary }
        style == CarLogButtonStyle.Secondary -> { fill = c.secondaryButton; content = c.onSecondaryButton }
        style == CarLogButtonStyle.Ghost -> { fill = c.bg; content = c.text; border = c.outline }
        style == CarLogButtonStyle.OnTag -> { fill = c.onTag; content = c.tag }
        else -> { fill = Color.Transparent; content = c.danger; border = c.danger }
    }
    if (pressed && enabled) fill = if (fill == Color.Transparent) c.press else lerp(fill, Color.Black, 0.08f)
    val scale = if (pressed && enabled) 0.95f else 1f

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .widthIn(min = if (size == CarLogButtonSize.L) d.buttonLMinWidth else d.touchMin)
            .clip(shape)
            .background(fill, shape)
            .then(if (border != null) Modifier.border(d.borderWidth, border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = padding),
        horizontalArrangement = Arrangement.spacedBy(d.buttonIconGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            if (leadingIcon != null) Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { leadingIcon() }
            Text(text, style = textStyle, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Handover tag (signature element)
// ---------------------------------------------------------------------------------------------------------------

private val TagPadStart = 44.dp
private val TagPadTop = 40.dp
private val TagPadBottom = 36.dp
private val TagHoleCenter = 64.dp   // from the tag's top-left corner
private val TagHoleRadius = 22.dp
private val TagRingRadius = 30.dp
private val TagRingWidth = 3.dp

/**
 * The received-handover "key tag / claim ticket": the one high-contrast object on a screen.
 * Night: Sand on Charcoal. Day: inverted (Charcoal tag).
 *
 * Layout: [head] row (indented past the punched hole, labelM, onTagSecondary) -> [content] (onTag, bodyS)
 * -> optional dashed perforation across the full width -> [foot] row.
 *
 * The hole is a real cut-out in an even-odd background path, so the background shows through.
 *
 * Example (01-board):
 * ```
 * HandoverTag(
 *     head = { Text("아버지가 남긴 인계"); Text("어제 19:20") },
 *     foot = {
 *         Column(Modifier.weight(1f)) { Text("확인할 메모 2건", style = CarLog.type.titleM) }
 *         CarLogButton("읽어주기", onClick = {}, style = CarLogButtonStyle.OnTag)
 *     },
 * ) {
 *     Text("B3층 C17", style = CarLog.type.feature, modifier = Modifier.padding(top = 28.dp))
 *     Text("아버지가 19:18에 직접 기록한 위치", color = CarLog.colors.onTagSecondary)
 * }
 * ```
 */
@Composable
fun HandoverTag(
    modifier: Modifier = Modifier,
    head: @Composable RowScope.() -> Unit,
    foot: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CarLog.colors
    val t = CarLog.type
    val d = CarLog.dimens
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Draw a real hole with an even-odd path. An offscreen Clear layer can lose
                // cached text when the day/night theme changes on the Pleos renderer.
                val center = TagHoleCenter.toPx()
                val radius = TagHoleRadius.toPx()
                val tagPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(36.dp.toPx())))
                    addOval(Rect(center - radius, center - radius, center + radius, center + radius))
                }
                drawPath(tagPath, c.tag)
                val ring = TagRingWidth.toPx()
                drawCircle(
                    color = c.onTag.copy(alpha = 0.22f),
                    radius = TagRingRadius.toPx() - ring / 2,
                    center = Offset(TagHoleCenter.toPx(), TagHoleCenter.toPx()),
                    style = Stroke(width = ring),
                )
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides c.onTag, LocalTextStyle provides t.bodyS) {
            Column(Modifier.padding(start = TagPadStart, end = TagPadStart, top = TagPadTop)) {
                Row(
                    Modifier.padding(start = TagHoleCenter).heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(d.spaceS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides c.onTagSecondary, LocalTextStyle provides t.labelM) {
                        head()
                    }
                }
                content()
            }
            if (foot != null) {
                TagPerforation(Modifier.padding(top = 32.dp, bottom = 28.dp))
                Row(
                    Modifier.fillMaxWidth().padding(start = TagPadStart, end = TagPadStart, bottom = TagPadBottom),
                    horizontalArrangement = Arrangement.spacedBy(d.spaceM),
                    verticalAlignment = Alignment.CenterVertically,
                    content = foot,
                )
            } else {
                Spacer(Modifier.height(TagPadBottom))
            }
        }
    }
}

/** Dashed tear line across the full tag width (3dp, onTag 28%). */
@Composable
private fun TagPerforation(modifier: Modifier = Modifier) {
    val color = CarLog.colors.onTag.copy(alpha = 0.28f)
    Canvas(modifier.fillMaxWidth().height(TagRingWidth)) {
        val w = size.height
        drawLine(
            color = color,
            start = Offset(0f, w / 2),
            end = Offset(size.width, w / 2),
            strokeWidth = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2)),
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Link status (app bar)
// ---------------------------------------------------------------------------------------------------------------

enum class LinkDotState {
    /** Blue mark dot + textSecondary label ("차량 연결됨"). */
    Connected,
    /** Warning dot and label (connecting failed, stale data). */
    Warning,
    /** Hollow ring: demo / not connected to a vehicle. */
    Demo,
}

/** "● 차량 연결됨". The dot carries no meaning on its own: the label always says the state. */
@Composable
fun LinkDot(label: String, state: LinkDotState, modifier: Modifier = Modifier) {
    val c = CarLog.colors
    val d = CarLog.dimens
    val textColor = if (state == LinkDotState.Warning) c.warning else c.textSecondary
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val dot = Modifier.size(d.linkDot)
        when (state) {
            LinkDotState.Connected -> Box(dot.background(c.accentMark, CircleShape))
            LinkDotState.Warning -> Box(dot.background(c.warning, CircleShape))
            LinkDotState.Demo -> Box(dot.border(2.dp, c.textTertiary, CircleShape))
        }
        Text(label, style = CarLog.type.labelM, color = textColor)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Category badge (memo rows)
// ---------------------------------------------------------------------------------------------------------------

/**
 * 76dp rounded square in front of a memo. Important memos tint the glyph with warning.
 * [content] is an icon (40dp) or a single letter; LocalContentColor is set for it.
 */
@Composable
fun CategoryBadge(
    important: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = CarLog.colors
    Box(
        modifier.size(CarLog.dimens.categoryBadge).background(c.raised, CarLogShapes.badge),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (important) c.warning else c.textSecondary, content = content)
    }
}

/** Text-only variant (the current app draws the first letter of the category label). */
@Composable
fun CategoryBadge(letter: String, important: Boolean, modifier: Modifier = Modifier) {
    CategoryBadge(important, modifier) {
        Text(letter.take(1), style = CarLog.type.titleS, color = LocalContentColor.current)
    }
}
