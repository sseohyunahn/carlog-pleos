package com.carlog.app.ui

import com.carlog.app.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlog.app.CarLogController
import com.carlog.app.domain.*

@Composable
internal fun BoardScreen(c: CarLogController, wide: Boolean) {
    val now = rememberNow()
    if (wide) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        PageTitle("출발 전, 이어받을 이야기", "안녕하세요, "+c.currentDriver.name)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item { IncomingCard(c, Modifier.fillMaxWidth()) }
                item { VehicleCard(c, now, Modifier.fillMaxWidth()) }
            }
            MemoList(c, Modifier.weight(1f).fillMaxHeight(), includeTag = false)
        }
    } else MemoList(c, Modifier.fillMaxSize(), includeTag = true)
}

@Composable private fun MemoList(c: CarLogController, modifier: Modifier, includeTag: Boolean) {
    LazyColumn(modifier.testTag("board"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (includeTag) {
            item { PageTitle("출발 전, 이어받을 이야기", "안녕하세요, "+c.currentDriver.name) }
            item { IncomingCard(c, Modifier.fillMaxWidth()) }
        }
        if (c.parkedCandidate) item { InfoBox("화면 사용 제한이 해제됐어요. 이용을 마쳤다면 인계 카드를 남겨 주세요.", Lime) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("확인할 전달사항", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp)); Pill("${c.openMemos.size}건", Lime)
                Spacer(Modifier.weight(1f))
                Text("조치 완료 전까지 남아요", color = Muted, fontSize = 24.sp)
            }
        }
        if (c.openMemos.isEmpty()) item { EmptyCard("이어받을 메모가 없어요", "인계 남기기에서 기록한 뒤 다른 프로필로 바꿔 보세요.") }
        items(c.openMemos.sortedByDescending { it.second.priority == Priority.IMPORTANT }, key = { "in-${it.second.id}" }) { (h, m) -> MemoCard(c, h, m) }
        val mine = c.myOpenMemos
        if (mine.isNotEmpty()) {
            item { Text("내가 남긴 메모", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
            items(mine, key = { "mine-${it.second.id}" }) { (h, m) -> MyMemoRow(c, h, m) }
        }
        item {
            OutlinedButton(onClick = { c.selectTab(1) }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("다음 운전자에게 인계 남기기")
            }
        }
    }
}

/** Key tag from the first ZIP's design: punched hole, feature value, tear line. */
@Composable private fun IncomingCard(c: CarLogController, modifier: Modifier) {
    val record = c.latestIncoming
    val colors = CarLog.colors
    HandoverTag(
        modifier = modifier.testTag("incoming-card"),
        head = { Text(record?.let { "${name(it.handover.author)}님이 남긴 인계 · ${time(it.handover.createdAt)}" } ?: "받은 인계") },
        foot = {
            val open = record?.memos?.count { it.resolvedAt == null } ?: 0
            Column(Modifier.weight(1f)) {
                Text(if (open == 0) "확인할 메모 없음" else "확인할 메모 ${open}건", style = CarLog.type.titleS)
            }
            if (record != null) CarLogButton(
                text = if (c.reading) "■  읽기 중지" else "▶  인계 읽어주기",
                onClick = { c.toggleReading() }, enabled = !c.suspended,
                modifier = Modifier.testTag("read-aloud"), style = CarLogButtonStyle.OnTag,
                size = CarLogButtonSize.S,
            )
        },
    ) {
        if (record == null) {
            Text("아직 받은 인계가 없어요", style = CarLog.type.titleL, modifier = Modifier.padding(top = 28.dp))
            Text("다른 운전자가 인계를 남기면 이곳에 먼저 보여요.", color = colors.onTagSecondary, modifier = Modifier.padding(top = 12.dp))
        } else {
            val h = record.handover
            val parking = h.parking.ifBlank { "주차 구역 미입력" }
            val parkingLabel = buildAnnotatedString {
                if (parking.endsWith("구역")) {
                    append(parking.removeSuffix("구역").trimEnd())
                    withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium)) { append("\u00A0구\u2060역") }
                } else append(parking)
            }
            Text(parkingLabel, style = CarLog.type.feature,
                modifier = Modifier.padding(top = 28.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (h.parking.isNotBlank()) {
                val by = h.parkingBy
                val (origin, warn) = when {
                    by == null -> "주차 위치를 기록한 사람 정보가 없어요 · 위치를 확인해 주세요" to true
                    by != h.author -> "${name(by)}님이 ${time(h.parkingAt ?: h.createdAt)}에 기록한 위치예요 · 이번 주차와 다를 수 있어요" to true
                    else -> "${name(by)}님이 ${time(h.parkingAt ?: h.createdAt)}에 기록한 위치" to false
                }
                Text(origin, color = if (warn) colors.onTagWarning else colors.onTagSecondary,
                    style = CarLog.type.labelM, modifier = Modifier.padding(top = 12.dp))
            }
            Text(vehicleSummary(h.snapshot).ifEmpty { "기록 당시 차량 상태 수신 없음" },
                style = CarLog.type.bodyS, modifier = Modifier.padding(top = 20.dp))
            vehicleAlerts(h.snapshot).forEach { alert ->
                Text("△  $alert", color = colors.onTagWarning, style = CarLog.type.labelM, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable private fun VehicleCard(c: CarLogController, now: Long, modifier: Modifier) {
    val s = c.snapshot
    Surface(modifier, color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("현재 차량 상태", fontSize = 26.sp, color = Muted, modifier = Modifier.weight(1f))
                LinkChip(s, c.mode)
                IconButton(onClick = { c.refreshVehicle() }, enabled = !c.suspended, modifier = Modifier.size(76.dp)) {
                    Icon(Icons.Outlined.Refresh, "차량 상태 다시 읽기", Modifier.size(21.dp))
                }
            }
            val useFuel = s?.battery == null && s?.fuel != null
            val energy = s?.energyPercent
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(energy?.let { "$it%" } ?: "—", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = White)
                    val label = if (useFuel) "연료" else "배터리"
                    Text(if (energy == null) "$label 수신 없음" else "$label 잔량" + staleNote(s, if (useFuel) Fields.FUEL else Fields.BATTERY, now),
                        color = Muted, fontSize = 24.sp)
                }
                CarDrawing(Modifier.width(130.dp).height(96.dp))
            }
            Spacer(Modifier.height(15.dp))
            LinearProgressIndicator(progress = { (energy ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = Lime, trackColor = Border)
            Spacer(Modifier.height(10.dp))
            Metric("주행 가능", s?.rangeKm?.let { km(it) }, s, Fields.RANGE, now)
            Metric("누적 주행", s?.odometer?.let { km(it) }, s, Fields.ODOMETER, now)
            Metric("이번 주행", s?.trip?.let { km(it, 1) }, s, Fields.TRIP, now)
            Metric("문", s?.unlockedDoors?.let { doorText(it) }, s, Fields.DOORS, now, warn = (s?.unlockedDoors ?: 0) > 0)
            Metric("창문", s?.openWindows?.let { windowText(it) }, s, Fields.WINDOWS, now, warn = (s?.openWindows ?: 0) > 0)
            Metric("워셔액", s?.washerWarning?.let { washerText(it) }, s, Fields.WASHER, now, warn = s?.washerWarning == true)
            val note = s?.diagnostics?.get("차량")
            Text(when {
                s == null -> "상태를 준비하고 있어요"
                note != null -> note
                s.mode == DataMode.DEMO -> "가상 데이터 · ${time(s.capturedAt)}"
                else -> SnapshotFreshness.lastReceived(s)?.let { "마지막 수신 · ${time(it)}" } ?: "아직 수신 없음"
            }, color = if (s?.link?.problem == true) Amber else Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable private fun Metric(label: String, value: String?, s: VehicleSnapshot?, field: String, now: Long, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, color = Muted, fontSize = 24.sp, modifier = Modifier.width(160.dp))
        Text((value ?: "수신 없음") + if (value != null) staleNote(s, field, now) else "",
            color = if (warn) Amber else if (value == null) Muted else White, fontSize = 24.sp, modifier = Modifier.weight(1f))
    }
}

@Composable internal fun CarDrawing(modifier: Modifier) {
    val muted = Muted; val panel = Panel; val accent = Lime
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(.06f * w, .66f * h); lineTo(.10f * w, .46f * h); lineTo(.29f * w, .42f * h)
            lineTo(.43f * w, .17f * h); lineTo(.72f * w, .17f * h); lineTo(.88f * w, .44f * h)
            lineTo(.96f * w, .51f * h); lineTo(.97f * w, .70f * h); lineTo(.06f * w, .70f * h); close()
        }
        drawPath(p, muted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawLine(muted, Offset(.35f * w, .43f * h), Offset(.79f * w, .43f * h), 2.dp.toPx())
        drawLine(muted, Offset(.58f * w, .20f * h), Offset(.58f * w, .43f * h), 2.dp.toPx())
        listOf(.26f, .78f).forEach { x ->
            drawCircle(panel, .12f * h, Offset(x * w, .73f * h))
            drawCircle(accent, .10f * h, Offset(x * w, .73f * h), style = Stroke(2.dp.toPx()))
        }
        drawLine(accent, Offset(.1f * w, .54f * h), Offset(.2f * w, .54f * h), 3.dp.toPx())
    }
}

@Composable private fun MemoCard(c: CarLogController, h: Handover, m: Memo) {
    val important = m.priority == Priority.IMPORTANT
    val read = c.driver in m.readBy
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, CarLog.colors.divider)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(m.category, important)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(m.category.label, color = if (important) Amber else Muted, fontSize = 24.sp)
                        if (important) Text("중요", color = Amber, fontSize = 24.sp)
                        if (m.fromVoice) Text("음성", color = Muted, fontSize = 24.sp)
                        if (read) Text("확인함 · 미처리", color = Lime, fontSize = 24.sp)
                    }
                    Text(m.text, fontSize = 28.sp, lineHeight = 42.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${name(h.author)} · ${time(h.createdAt)}", color = Muted, fontSize = 24.sp, modifier = Modifier.weight(1f))
                if (!read) TextButton(onClick = { c.markRead(m) }, enabled = !c.busy) { Text("확인했어요") }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick = { c.resolve(m) }, enabled = !c.busy) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("조치 완료")
                }
            }
        }
    }
}

@Composable private fun MyMemoRow(c: CarLogController, h: Handover, m: Memo) {
    PanelBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.text, fontSize = 26.sp)
                Text("${name(h.recipient)}에게 · ${time(h.createdAt)} · 확인 ${m.readBy.size}명" + if (m.fromVoice) " · 음성" else "",
                    color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (HandoverRules.canDelete(h, c.driver)) TextButton(onClick = { c.askDelete(m) }, enabled = !c.busy) { Text("삭제", color = Amber) }
        }
    }
}
