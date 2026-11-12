package com.carlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import com.carlog.app.ui.theme.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlog.app.domain.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Bg: Color @Composable get() = CarLog.colors.bg
internal val Panel: Color @Composable get() = CarLog.colors.surface
internal val PanelLight: Color @Composable get() = CarLog.colors.raised
internal val Lime: Color @Composable get() = CarLog.colors.accent
internal val Muted: Color @Composable get() = CarLog.colors.textTertiary
internal val White: Color @Composable get() = CarLog.colors.text
internal val Amber: Color @Composable get() = CarLog.colors.warning
internal val Border: Color @Composable get() = CarLog.colors.outline
internal val ParkingGreen: Color @Composable get() = CarLog.colors.tag

internal fun name(id: String?) = drivers.firstOrNull { it.id == id }?.name ?: "다음 운전자"
/** Shows the year when [ms] is not in the current year. */
internal fun time(ms: Long): String = TimeText.format(ms)
internal fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(ms))
internal fun km(value: Float, decimals: Int = 0): String = String.format(Locale.KOREA, "%,.${decimals}f km", value)

/** " · 3분 전 값" when [field] was last received more than two minutes ago, otherwise "". */
internal fun staleNote(s: VehicleSnapshot?, field: String, now: Long): String {
    val at = s?.fieldTimes?.get(field) ?: return ""
    val age = now - at
    if (age <= HandoverRules.SNAPSHOT_MAX_AGE_MS) return ""
    val minutes = age / 60_000L
    return when {
        minutes < 60 -> " · ${minutes}분 전 값"
        minutes < 24 * 60 -> " · ${minutes / 60}시간 전 값"
        else -> " · ${minutes / (24 * 60)}일 전 값"
    }
}

internal fun doorText(unlocked: Int?) = when {
    unlocked == null -> "수신 없음"
    unlocked == 0 -> "모두 잠김"
    else -> "${unlocked}개 잠기지 않음"
}

internal fun windowText(open: Int?) = when {
    open == null -> "수신 없음"
    open == 0 -> "모두 닫힘"
    else -> "${open}개 열림"
}

internal fun washerText(warning: Boolean?) = when (warning) { null -> "수신 없음"; true -> "보충이 필요해요"; false -> "충분해요" }

/** "배터리 64% · 주행 가능 251 km" for a stored snapshot; "" when nothing was received. */
internal fun vehicleSummary(s: VehicleSnapshot): String = listOfNotNull(
    s.battery?.let { "배터리 $it%" } ?: s.fuel?.let { "연료 $it%" },
    s.rangeKm?.let { "주행 가능 ${km(it)}" }
).joinToString(" · ")

/** Door/window/washer items that need attention, e.g. "창문 1개 열림". */
internal fun vehicleAlerts(s: VehicleSnapshot): List<String> = listOfNotNull(
    s.unlockedDoors?.takeIf { it > 0 }?.let { "문 ${it}개 잠기지 않음" },
    s.openWindows?.takeIf { it > 0 }?.let { "창문 ${it}개 열림" },
    if (s.washerWarning == true) "워셔액 보충 필요" else null
)

/** Current wall-clock time, re-read every [periodMs] so "n분 전 값" labels age while the screen is open. */
@Composable
internal fun rememberNow(periodMs: Long = 15_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) { delay(periodMs); now = System.currentTimeMillis() }
    }
    return now
}

@Composable internal fun PageTitle(title: String, description: String) {
    Column {
        Text(title, style = CarLog.type.titleL)
        Text(description, color = Muted, fontSize = 26.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable internal fun PanelBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) { Column(Modifier.padding(24.dp), content = content) }
}

@Composable internal fun Label(step: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(step, color = CarLog.colors.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 24.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.background(color.copy(alpha = .10f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable internal fun InfoBox(text: String, color: Color) {
    Text(text, color = color, fontSize = 26.sp,
        modifier = Modifier.fillMaxWidth().background(PanelLight, RoundedCornerShape(12.dp)).padding(16.dp))
}

@Composable internal fun EmptyCard(title: String, description: String) {
    PanelBox {
        Icon(Icons.Outlined.CheckCircle, null, tint = Lime, modifier = Modifier.size(30.dp))
        Text(title, fontSize = 30.sp, modifier = Modifier.padding(top = 12.dp))
        Text(description, color = Muted, fontSize = 26.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Category badge and link status use the first ZIP's reference components. */
@Composable internal fun CategoryBadge(category: Category, important: Boolean) {
    com.carlog.app.ui.theme.CategoryBadge(letter = category.label, important = important)
}

@Composable internal fun LinkChip(s: VehicleSnapshot?, mode: DataMode) {
    val label = s?.link?.label ?: if (mode == DataMode.DEMO) LinkState.DEMO.label else LinkState.CONNECTING.label
    val state = when {
        mode == DataMode.DEMO -> LinkDotState.Demo
        s == null || s.link.problem -> LinkDotState.Warning
        else -> LinkDotState.Connected
    }
    LinkDot(label, state)
}
