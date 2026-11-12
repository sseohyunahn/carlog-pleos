package com.carlog.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.carlog.app.CarLogController
import com.carlog.app.VoicePhase
import com.carlog.app.VoicePurpose
import com.carlog.app.VoiceSheet
import com.carlog.app.domain.*
import kotlinx.coroutines.delay

/**
 * Returns a starter for the in-app recording sheet: asks for RECORD_AUDIO first, ignores taps while a request or a
 * sheet is already open (no double dialogs).
 */
@Composable
internal fun rememberVoiceStarter(c: CarLogController): (VoicePurpose) -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val purpose = pending?.let { runCatching { VoicePurpose.valueOf(it) }.getOrNull() }
        pending = null
        if (purpose != null) { if (granted) c.startVoice(purpose) else c.voicePermissionDenied(purpose) }
    }
    return remember(c, launcher, context) {
        { purpose: VoicePurpose ->
            if (!c.voiceActive && pending == null && c.canInteract) {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) c.startVoice(purpose)
                else { pending = purpose.name; launcher.launch(Manifest.permission.RECORD_AUDIO) }
            }
        }
    }
}

@Composable
internal fun EditorScreen(c: CarLogController) {
    val startVoice = rememberVoiceStarter(c)
    val parkingFocus = remember { FocusRequester() }
    val memoFocus = remember { FocusRequester() }
    LaunchedEffect(c.focusTarget) {
        val target = c.focusTarget ?: return@LaunchedEffect
        c.focusTarget = null
        runCatching { (if (target == VoicePurpose.PARKING) parkingFocus else memoFocus).requestFocus() }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("handover-editor"),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("이용을 마치셨나요?", "다음 운전자가 알아야 할 것만 짧게 남겨 주세요.")
        PanelBox {
            Label("01", "누구에게 전달할까요?")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState())) {
                FilterChip(selected = c.editorRecipient == null, onClick = { c.updateRecipient(null) }, label = { Text("다음 운전자 모두") }, enabled = !c.busy)
                drivers.filter { it.id != c.driver }.forEach { d ->
                    FilterChip(selected = c.editorRecipient == d.id, onClick = { c.updateRecipient(d.id) }, label = { Text(d.name) }, enabled = !c.busy)
                }
            }
            Text("현재 작성자: ${c.currentDriver.name} · 받는 사람을 고르면 그 프로필의 인계 보드에 보여요.", color = Muted, fontSize = 24.sp)
        }
        ParkingSection(c, startVoice, parkingFocus)
        MemoSection(c, startVoice, memoFocus)
        Column {
            Button(onClick = { c.save() }, enabled = !c.busy && c.hasDraftContent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text("인계 카드 저장", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text("입력한 메모와 위치는 같은 차량을 쓰는 지정 프로필에 전달돼요. 민감한 내용은 남기지 말아 주세요.",
                color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable private fun ParkingSection(c: CarLogController, startVoice: (VoicePurpose) -> Unit, focus: FocusRequester) {
    PanelBox {
        Label("02", "주차 위치와 차량 상태")
        OutlinedTextField(value = c.editorParking, onValueChange = { c.updateParking(it) }, label = { Text("층·구역 직접 입력") },
            placeholder = { Text("예: B3층 C17구역") }, singleLine = true, enabled = !c.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).focusRequester(focus))
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { startVoice(VoicePurpose.PARKING) }, enabled = !c.busy && !c.voiceActive) { Text("음성으로 말하기") }
            val last = c.lastParking
            if (last != null && c.editorParking.isBlank()) OutlinedButton(onClick = { c.useLastParking() }, enabled = !c.busy) { Text("지난 주차 위치 사용") }
        }
        c.parkingReuse?.takeIf { it.text == c.editorParking }?.let { reuse ->
            Text("${name(reuse.by)}님이 ${reuse.at?.let(::time) ?: "이전에"} 기록한 위치예요 · 이번 주차와 다를 수 있으니 확인해 주세요",
                color = Amber, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
        }
        val s = c.snapshot
        val now = rememberNow()
        Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, null, tint = Lime, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(when {
                s == null -> "차량 정보를 준비하고 있어요"
                s.hasFreshLocation(now) -> if (s.mode == DataMode.DEMO) "가상 위치가 준비됐어요 · 층·구역은 직접 입력" else "최근 좌표 확보 · 층·구역은 직접 입력"
                else -> "정확한 현재 위치가 없어요 · 층·구역을 직접 입력해 주세요"
            }, color = Muted, fontSize = 24.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { c.refreshVehicle() }, enabled = !c.suspended) { Text("다시 읽기") }
        }
        val energy = s?.battery?.let { "배터리 $it%" } ?: s?.fuel?.let { "연료 $it%" } ?: "에너지 수신 없음"
        Text("저장할 상태: $energy · ${s?.let { SnapshotFreshness.lastReceived(it) }?.let { "마지막 수신 ${time(it)}" } ?: "아직 수신 없음"}", color = Muted, fontSize = 24.sp)
        Text("차량 상태는 저장하는 순간의 값으로 남아요. 2분이 지났다면 저장할 때 다시 읽어요.", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun MemoSection(c: CarLogController, startVoice: (VoicePurpose) -> Unit, focus: FocusRequester) {
    PanelBox {
        Label("03", "전달사항")
        Button(onClick = { startVoice(VoicePurpose.MEMO) }, enabled = !c.busy && !c.voiceActive,
            modifier = Modifier.padding(top = 14.dp).fillMaxWidth().heightIn(min = 80.dp).testTag("voice-memo")) {
            Text("음성으로 남기기", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text("10초 안에 말해 주세요. 예: “트렁크에 우산 있어요”, “내일 충전이 필요해요”", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 6.dp))
        c.voiceNotice?.let { Text(it, color = Lime, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        OutlinedTextField(value = c.editorText, onValueChange = { c.updateText(it) }, label = { Text("전달할 내용") },
            placeholder = { Text("예: 트렁크에 회수할 장비가 있어요.") }, minLines = 2, maxLines = 4, enabled = !c.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(focus),
            supportingText = { Text("${c.editorText.length}/${HandoverRules.MAX_MEMO_LENGTH}") })
        Text(if (c.editorFromVoice) "분류 · 내용을 보고 골랐어요. 바꿀 수 있어요" else "분류", color = Muted, fontSize = 24.sp)
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { item -> FilterChip(selected = c.editorCategory == item, onClick = { c.updateCategory(item) }, label = { Text(item.label) }, enabled = !c.busy) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = c.editorImportant, onCheckedChange = { c.updateImportant(it) }, enabled = !c.busy); Text("중요한 전달사항", fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { c.addDraft() }, enabled = c.canAddDraft) { Text("목록에 추가") }
        }
        c.drafts.forEachIndexed { i, draft ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(PanelLight, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${draft.category.label}${if (draft.priority == Priority.IMPORTANT) " · 중요" else ""}${if (draft.fromVoice) " · 음성" else ""} · ${draft.text}",
                    modifier = Modifier.weight(1f), fontSize = 26.sp)
                IconButton(onClick = { c.removeDraft(i) }, enabled = !c.busy) { Icon(Icons.Outlined.Close, "목록에서 빼기") }
            }
        }
    }
}

/** In-app 10 s recording sheet: live caption, level bar, countdown, [말하기 끝] [취소] [직접 입력]. */
@Composable
internal fun VoiceSheetDialog(c: CarLogController, sheet: VoiceSheet) {
    // The state itself is keyed on the deadline (produceState would keep the previous value across keys), so a new
    // recording (retry, engine switch) never shows the previous countdown in its first frame.
    val deadline = sheet.deadline
    var secondsLeft by remember(deadline) { mutableIntStateOf(secondsUntil(deadline)) }
    LaunchedEffect(deadline) {
        if (deadline == null) return@LaunchedEffect
        while (true) {
            val left = secondsUntil(deadline)
            secondsLeft = left
            if (left == 0) break
            delay(200)
        }
    }
    val captionHeight = with(LocalDensity.current) { (CaptionLineHeight * CaptionLines).toDp() }
    val title = when (sheet.phase) {
        VoicePhase.PREPARING -> "준비하고 있어요"
        VoicePhase.LISTENING -> "듣고 있어요"
        VoicePhase.PROCESSING -> "받아 적는 중이에요"
        VoicePhase.NO_SPEECH -> "말씀이 들리지 않았어요"
        VoicePhase.FAILED -> "음성 입력을 잠시 쓸 수 없어요"
    }
    val retry = sheet.phase == VoicePhase.NO_SPEECH || sheet.phase == VoicePhase.FAILED
    AlertDialog(
        onDismissRequest = { c.cancelVoice() },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 980.dp).padding(40.dp).testTag("voice-sheet"),
        title = {
            Column {
                Text(sheet.engine, color = Muted, fontSize = 24.sp)
                Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(if (sheet.purpose == VoicePurpose.PARKING) "주차한 층과 구역을 말해 주세요. 예: 지하 3층 C17 구역"
                    else "다음 운전자에게 전할 말을 10초 안에 말해 주세요. 예: 트렁크에 우산 있어요", color = Muted, fontSize = 26.sp)
                Spacer(Modifier.height(14.dp))
                val bar = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)
                when (sheet.phase) {
                    // The level is read inside the lambda only, so a level change redraws the bar and nothing else.
                    VoicePhase.LISTENING -> LinearProgressIndicator(progress = { c.voiceLevel.coerceIn(0.02f, 1f) },
                        modifier = bar, color = Lime, trackColor = Border)
                    // Waiting for the engine or the result can take seconds: show that something is happening.
                    VoicePhase.PREPARING, VoicePhase.PROCESSING ->
                        LinearProgressIndicator(modifier = bar, color = Lime, trackColor = Border)
                    else -> LinearProgressIndicator(progress = { 0f }, modifier = bar, color = Lime, trackColor = Border)
                }
                // Fixed height (3 lines, newest words visible), so the dialog and its buttons never move while dictating.
                Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(captionHeight).clipToBounds()) {
                    Text(sheet.caption, fontSize = 30.sp, lineHeight = CaptionLineHeight,
                        modifier = Modifier.wrapContentHeight(align = Alignment.Bottom, unbounded = true))
                }
                Text(when {
                    retry -> sheet.error ?: ""
                    sheet.phase == VoicePhase.LISTENING -> "${secondsLeft}초 남음"
                    sheet.phase == VoicePhase.PROCESSING -> "정리하고 있어요"
                    else -> "마이크를 켜고 있어요"
                }, color = if (retry) Amber else Lime, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    minLines = 2, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { c.voiceToManual() }) { Text("직접 입력") }
                TextButton(onClick = { c.cancelVoice() }) { Text("취소") }
                if (retry) Button(onClick = { c.retryVoice() }) { Text("다시 말하기") }
                else Button(onClick = { c.finishSpeaking() }, enabled = sheet.phase == VoicePhase.LISTENING || sheet.phase == VoicePhase.PREPARING) { Text("말하기 끝") }
            }
        }
    )
}

private const val VoiceServiceSeconds = com.carlog.app.platform.voice.VoiceService.MAX_LISTEN_SECONDS
private val CaptionLineHeight = 44.sp
private const val CaptionLines = 3

/** Whole seconds until [deadline] (rounded up), or the full window before the engine has started. */
private fun secondsUntil(deadline: Long?): Int =
    if (deadline == null) VoiceServiceSeconds
    else ((deadline - SystemClock.uptimeMillis() + 999) / 1000).toInt().coerceIn(0, VoiceServiceSeconds)
