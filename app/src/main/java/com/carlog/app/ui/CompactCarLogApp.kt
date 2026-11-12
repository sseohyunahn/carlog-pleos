package com.carlog.app.ui

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.carlog.app.*
import com.carlog.app.domain.*
import com.carlog.app.ui.theme.CarLog
import com.carlog.app.ui.theme.CarLogStaticColors
import kotlinx.coroutines.delay

private val compactTabs = listOf("인계 보드", "인계 남기기", "지난 기록", "설정")

/** Small windows share the existing controller, persistence, validation and platform adapters. */
@Composable
internal fun CompactCarLogApp(c: CarLogController) {
    var picker by rememberSaveable { mutableStateOf(false) }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Surface(Modifier.fillMaxSize().testTag("app-surface"), color = Bg) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CarLog", style = CarLog.type.wordmark)
                        Text(c.snapshot?.link?.label ?: if (c.mode == DataMode.DEMO) "시연 데이터" else "차량 연결 확인 중",
                            style = CarLog.type.labelS, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { picker = true }, enabled = c.canInteract && !c.busy,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("driver-button")) {
                        Text(c.currentDriver.name); Icon(Icons.Outlined.KeyboardArrowDown, "운전자 바꾸기", Modifier.size(20.dp))
                    }
                }
                if (c.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                c.message?.let {
                    Row(Modifier.fillMaxWidth().background(PanelLight).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, style = CarLog.type.bodyS, modifier = Modifier.weight(1f))
                        IconButton(onClick = { c.message = null }) { Icon(Icons.Outlined.Close, "안내 닫기") }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (c.tab) {
                        0 -> CompactBoard(c)
                        1 -> CompactEditor(c)
                        2 -> CompactHistory(c)
                        else -> CompactSettings(c)
                    }
                }
                if (!ime) {
                    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Edit, Icons.Outlined.DateRange, Icons.Outlined.Settings)
                    Surface(color = Panel) {
                        Row(Modifier.fillMaxWidth()) {
                            compactTabs.forEachIndexed { i, title ->
                                Column(Modifier.weight(1f).heightIn(min = 64.dp).clickable(enabled = c.canInteract) { c.selectTab(i) }
                                    .testTag("tab-$i").padding(horizontal = 4.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icons[i], null, Modifier.size(24.dp), tint = if (c.tab == i) Lime else Muted)
                                    Text(title, style = CarLog.type.nav, color = if (c.tab == i) Lime else Muted,
                                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (!c.canInteract) CompactLock(c)
        }
    }
    if (c.canInteract) {
        if (c.askDriver || picker) CompactDriverDialog(c) { if (c.askDriver) c.keepDriver(); picker = false }
        c.deleteCandidate?.let { memo ->
            AlertDialog(onDismissRequest = { c.dismissDelete() }, title = { Text("메모를 삭제할까요?") },
                text = { Text("${memo.text}\n\n저장된 인계 카드에서도 지워지고 되돌릴 수 없어요.") },
                confirmButton = { TextButton(onClick = { c.confirmDelete() }, modifier = Modifier.testTag("confirm-delete")) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { c.dismissDelete() }) { Text("취소") } })
        }
    }
    c.voiceSheet?.let { CompactVoiceDialog(c,it) }
}

@Composable private fun CompactHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = CarLog.type.titleL)
        Text(description, style = CarLog.type.bodyS, color = Muted)
    }
}

@Composable private fun CompactPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun CompactBoard(c: CarLogController) {
    LazyColumn(Modifier.fillMaxSize().testTag("board"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { CompactHeading("출발 전, 이어받을 이야기", "안녕하세요, ${c.currentDriver.name}") }
        item { CompactIncoming(c) }
        item { Text("확인할 전달사항 ${c.openMemos.size}건", style = CarLog.type.titleS) }
        if (c.openMemos.isEmpty()) item { CompactPanel { Text("이어받을 메모가 없어요"); Text("다른 운전자가 인계를 남기면 이곳에 보여요.", style = CarLog.type.bodyS, color = Muted) } }
        items(c.openMemos.sortedByDescending { it.second.priority == Priority.IMPORTANT }, key = { "in-${it.second.id}" }) { (h,m) ->
            CompactPanel {
                Text(listOfNotNull(m.category.label, if(m.priority == Priority.IMPORTANT) "중요" else null, if(m.fromVoice) "음성" else null).joinToString(" · "), style = CarLog.type.labelM,
                    color = if(m.priority == Priority.IMPORTANT) Amber else Muted)
                if(c.driver in m.readBy) Text("확인함 · 미처리",style = CarLog.type.labelS,color = Lime)
                Text(m.text, style = CarLog.type.bodyM)
                Text("${name(h.author)} · ${time(h.createdAt)}", style = CarLog.type.labelS, color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if(c.driver !in m.readBy) TextButton(onClick = { c.markRead(m) }, enabled = !c.busy, modifier = Modifier.weight(1f)) { Text("확인했어요") }
                    FilledTonalButton(onClick = { c.resolve(m) }, enabled = !c.busy, modifier = Modifier.weight(1f)) { Text("조치 완료") }
                }
            }
        }
        if(c.myOpenMemos.isNotEmpty()) item { Text("내가 남긴 메모", style = CarLog.type.titleS) }
        items(c.myOpenMemos, key = { "mine-${it.second.id}" }) { (h,m) ->
            CompactPanel {
                Text(m.text)
                Text("${name(h.recipient)}에게 · ${time(h.createdAt)}", style = CarLog.type.labelS, color = Muted)
                if(HandoverRules.canDelete(h,c.driver)) TextButton(onClick = { c.askDelete(m) },enabled = !c.busy) { Text("삭제",color = Amber) }
            }
        }
        item { OutlinedButton(onClick = { c.selectTab(1) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("다음 운전자에게 인계 남기기") } }
    }
}

@Composable private fun CompactIncoming(c: CarLogController) {
    val colors=CarLog.colors
    val record=c.latestIncoming
    Column(Modifier.fillMaxWidth().testTag("incoming-card").background(colors.tag,RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(20.dp)) { drawCircle(colors.bg); drawCircle(colors.onTag.copy(alpha=.25f),style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())) }
            Text(record?.let { "${name(it.handover.author)}님이 남긴 인계" } ?: "받은 인계", style = CarLog.type.labelM, color = colors.onTagSecondary)
        }
        if(record == null) {
            Text("아직 받은 인계가 없어요", style = CarLog.type.titleM, color = colors.onTag)
            Text("다른 운전자가 인계를 남기면 이곳에 먼저 보여요.", style = CarLog.type.bodyS, color = colors.onTagSecondary)
        } else {
            val h=record.handover
            Text(h.parking.ifBlank { "주차 구역 미입력" }, style = CarLog.type.feature, color = colors.onTag)
            Text("${name(h.parkingBy)} · ${time(h.parkingAt ?: h.createdAt)}에 기록한 위치", style = CarLog.type.labelS,
                color = if(h.parkingBy == null || h.parkingBy != h.author) colors.onTagWarning else colors.onTagSecondary)
            if(h.parking.isNotBlank() && h.parkingBy != h.author) Text("이번 주차와 다를 수 있으니 위치를 확인해 주세요.", style = CarLog.type.bodyS, color = colors.onTagWarning)
            Text(vehicleSummary(h.snapshot).ifEmpty { "기록 당시 차량 상태 수신 없음" }, style = CarLog.type.bodyS, color = colors.onTag)
            vehicleAlerts(h.snapshot).forEach { Text(it,style = CarLog.type.bodyS,color = colors.onTagWarning) }
        }
        Canvas(Modifier.fillMaxWidth().height(1.dp)) {
            drawLine(colors.onTag.copy(alpha=.3f),Offset.Zero,Offset(size.width,0f),pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx())))
        }
        Text("확인할 메모 ${record?.memos?.count { it.resolvedAt == null } ?: 0}건",style = CarLog.type.labelM,color = colors.onTag)
        if(record != null) Button(onClick = { c.toggleReading() },enabled = !c.suspended,
            colors = ButtonDefaults.buttonColors(containerColor = colors.onTag,contentColor = colors.tag),
            modifier = Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("read-aloud")) {
            Text(if(c.reading) "■ 읽기 중지" else "▶ 인계 읽어주기")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CompactEditor(c: CarLogController) {
    val startVoice=rememberVoiceStarter(c)
    val parkingFocus=remember { FocusRequester() }; val memoFocus=remember { FocusRequester() }
    LaunchedEffect(c.focusTarget) {
        val target=c.focusTarget ?: return@LaunchedEffect;c.focusTarget=null
        runCatching { (if(target == VoicePurpose.PARKING) parkingFocus else memoFocus).requestFocus() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("handover-editor"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompactHeading("이용을 마치셨나요?","다음 운전자가 알아야 할 것만 짧게 남겨 주세요.")
        CompactPanel {
            Text("01  누구에게 전달할까요?",style = CarLog.type.titleS)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=c.editorRecipient==null,onClick={c.updateRecipient(null)},label={Text("다음 운전자 모두")},enabled=!c.busy)
                drivers.filter { it.id!=c.driver }.forEach { d -> FilterChip(selected=c.editorRecipient==d.id,onClick={c.updateRecipient(d.id)},label={Text(d.name)},enabled=!c.busy) }
            }
            Text("현재 작성자: ${c.currentDriver.name}",style=CarLog.type.bodyS,color=Muted)
        }
        CompactPanel {
            Text("02  주차 위치와 차량 상태",style=CarLog.type.titleS)
            OutlinedTextField(c.editorParking,{c.updateParking(it)},label={Text("층·구역 직접 입력")},placeholder={Text("예: B2층 C21구역")},
                modifier=Modifier.fillMaxWidth().focusRequester(parkingFocus),singleLine=true,enabled=!c.busy)
            OutlinedButton(onClick={startVoice(VoicePurpose.PARKING)},enabled=!c.busy&&!c.voiceActive,modifier=Modifier.fillMaxWidth()) { Text("음성으로 말하기") }
            if(c.lastParking!=null && c.editorParking.isBlank()) TextButton(onClick={c.useLastParking()},enabled=!c.busy) { Text("지난 주차 위치 사용") }
            c.parkingReuse?.takeIf { it.text==c.editorParking }?.let {
                Text("${name(it.by)}님이 ${it.at?.let(::time) ?: "이전에"} 기록한 위치예요 · 이번 주차와 다를 수 있으니 확인해 주세요",style=CarLog.type.bodyS,color=Amber)
            }
            Text(c.snapshot?.let(::vehicleSummary)?.ifEmpty { "차량 상태 수신 없음" } ?: "차량 정보를 준비하고 있어요",style=CarLog.type.bodyS,color=Muted)
            TextButton(onClick={c.refreshVehicle()},enabled=!c.suspended) { Text("차량 상태 다시 읽기") }
        }
        CompactPanel {
            Text("03  전달사항",style=CarLog.type.titleS)
            Button(onClick={startVoice(VoicePurpose.MEMO)},enabled=!c.busy&&!c.voiceActive,modifier=Modifier.fillMaxWidth().testTag("voice-memo")) { Text("음성으로 남기기") }
            Text("10초 안에 말해 주세요. 음성을 쓸 수 없으면 직접 입력할 수 있어요.",style=CarLog.type.bodyS,color=Muted)
            c.voiceNotice?.let { Text(it,style=CarLog.type.bodyS,color=Lime) }
            OutlinedTextField(c.editorText,{c.updateText(it)},label={Text("전달할 내용")},minLines=2,maxLines=5,enabled=!c.busy,
                modifier=Modifier.fillMaxWidth().focusRequester(memoFocus),supportingText={Text("${c.editorText.length}/${HandoverRules.MAX_MEMO_LENGTH}")})
            Text(if(c.editorFromVoice) "내용에 맞춰 분류했어요. 바꿀 수 있어요." else "분류",style=CarLog.type.bodyS,color=Muted)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { item -> FilterChip(c.editorCategory==item,{c.updateCategory(item)},label={Text(item.label)},enabled=!c.busy) }
            }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(c.editorImportant,{c.updateImportant(it)},enabled=!c.busy);Text("중요한 전달사항",modifier=Modifier.weight(1f)) }
            OutlinedButton(onClick={c.addDraft()},enabled=c.canAddDraft,modifier=Modifier.fillMaxWidth()) { Text("목록에 추가") }
            c.drafts.forEachIndexed { i,d ->
                Row(Modifier.fillMaxWidth().background(PanelLight,RoundedCornerShape(12.dp)).padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("${d.category.label} · ${d.text}",modifier=Modifier.weight(1f),style=CarLog.type.bodyS)
                    IconButton(onClick={c.removeDraft(i)},enabled=!c.busy) { Icon(Icons.Outlined.Close,"목록에서 빼기") }
                }
            }
        }
        Button(onClick={c.save()},enabled=!c.busy&&c.hasDraftContent,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("save-handover")) { Text("인계 카드 저장") }
        Text("기록은 이 차량 안에 저장돼요. 민감한 내용은 남기지 말아 주세요.",style=CarLog.type.labelS,color=Muted)
    }
}

@Composable private fun CompactHistory(c: CarLogController) {
    val records=c.records.filter { it.handover.author==c.driver || HandoverRules.visibleTo(it.handover,c.driver) }
    LazyColumn(Modifier.fillMaxSize().testTag("history-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { CompactHeading("차량에 쌓인 기억","내가 남기거나 전달받은 기록을 확인하세요.") }
        if(records.isEmpty()) item { CompactPanel { Text("아직 인계 기록이 없어요") } }
        items(records,key={it.handover.id}) { r ->
            CompactPanel {
                val h=r.handover
                Text("${name(h.author)} → ${name(h.recipient)}",style=CarLog.type.titleS)
                Text(time(h.createdAt),style=CarLog.type.labelS,color=Muted)
                Text(h.parking.ifBlank { "주차 위치 미입력" },color=Lime)
                if(h.parking.isNotBlank() && h.parkingBy!=null && h.parkingBy!=h.author) Text("${name(h.parkingBy)}님이 기록한 구역 · 이번 주차와 다를 수 있어요",style=CarLog.type.bodyS,color=Amber)
                Text(vehicleSummary(h.snapshot).ifEmpty { "기록 당시 차량 상태 수신 없음" },style=CarLog.type.bodyS,color=Muted)
                vehicleAlerts(h.snapshot).forEach { Text(it,style=CarLog.type.bodyS,color=Amber) }
                r.memos.forEach { m ->
                    HorizontalDivider(color=CarLog.colors.divider)
                    Text(m.text)
                    Text(if(m.resolvedAt!=null) "${name(m.resolvedBy)} 조치 완료 · ${time(m.resolvedAt)}" else "미처리 · 확인 ${m.readBy.size}명",style=CarLog.type.bodyS,color=Muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(m.resolvedBy==c.driver) TextButton(onClick={c.resolve(m,true)},enabled=!c.busy) { Text("완료 취소") }
                        if(HandoverRules.canDelete(h,c.driver)) TextButton(onClick={c.askDelete(m)},enabled=!c.busy) { Text("삭제",color=Amber) }
                    }
                }
            }
        }
    }
}

@Composable private fun CompactSettings(c: CarLogController) {
    val context=LocalContext.current
    var consent by rememberSaveable { mutableStateOf(false) };var delete by rememberSaveable { mutableStateOf(false) }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        c.switchMode(DataMode.PLEOS)
        if(grants.values.any { !it }) c.message="일부 권한이 허용되지 않았어요. 읽을 수 있는 정보만 보여드려요."
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { CompactHeading("함께 쓰는 방법","시연 환경과 차량 연결 상태를 확인하세요.") }
        item { CompactPanel {
            Text("데이터 모드",style=CarLog.type.titleS)
            Text("시연 데이터와 실제 차량 기록은 각각 따로 저장돼요.",style=CarLog.type.bodyS,color=Muted)
            OutlinedButton(onClick={c.switchMode(DataMode.DEMO)},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) { Text(if(c.mode==DataMode.DEMO) "✓ 시연 모드" else "시연 모드") }
            OutlinedButton(onClick={consent=true},enabled=!c.busy,modifier=Modifier.fillMaxWidth()) { Text(if(c.mode==DataMode.PLEOS) "✓ Pleos 연결" else "Pleos 연결") }
            if(c.mode==DataMode.DEMO) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("GPS 음영 상황",modifier=Modifier.weight(1f));Switch(c.gpsUnavailable,{c.simulateGps()}) }
                OutlinedButton(onClick={c.simulateMoving()},modifier=Modifier.fillMaxWidth()) { Text("주행 중 입력 잠금 시연") }
                TextButton(onClick={c.resetDemo()}) { Text("처음 시연 상황으로 복원") }
            }
        } }
        item { CompactPanel {
            Text("연결 진단",style=CarLog.type.titleS)
            Fields.ALL.forEach { field ->
                Column { Text(Fields.labels[field] ?: field,style=CarLog.type.labelM);Text(c.snapshot?.fieldStatus?.get(field)?.label ?: FieldStatus.NOT_READ.label,style=CarLog.type.bodyS,color=Muted) }
            }
            c.snapshot?.diagnostics?.forEach { (k,v) -> Text("$k · $v",style=CarLog.type.bodyS,color=Muted) }
            TextButton(onClick={c.refreshVehicle()},enabled=!c.suspended) { Text("연결 다시 확인") }
        } }
        item { CompactPanel {
            Text("음성",style=CarLog.type.titleS)
            Text("음성 입력 · ${c.voice.inputEngineNameFor(c.mode)}",style=CarLog.type.bodyS)
            Text("음성 인식을 쓸 수 없을 때는 직접 입력할 수 있어요.",style=CarLog.type.bodyS,color=Muted)
        } }
        item { CompactPanel {
            Text("기록과 개인정보",style=CarLog.type.titleS)
            Text("기록은 앱 내부에 저장되고 외부 서버로 보내지 않아요. 앱을 삭제하면 함께 지워져요.\n\n프로필은 작성자 구분용이며 본인 인증이 아니에요.",style=CarLog.type.bodyS,color=Muted)
            OutlinedButton(onClick={delete=true},enabled=c.canInteract&&!c.busy,modifier=Modifier.fillMaxWidth()) { Text("현재 모드 기록 전체 삭제") }
            Text("CarLog ${BuildConfig.VERSION_NAME}",style=CarLog.type.labelS,color=Muted)
        } }
    }
    LaunchedEffect(c.canInteract) { if(!c.canInteract) { consent=false;delete=false } }
    if(consent && c.canInteract) AlertDialog(onDismissRequest={consent=false},title={Text("Pleos 차량 연결")},
        text={Text("차량 상태와 마지막 위치를 읽고, 인계 저장 시 이 기기 안에 보관해요. Pleos 음성은 ON_DEVICE 모드를 사용해요. 서비스와 권한이 없는 값은 시연값으로 채우지 않아요. 이 차량을 이용할 권한과 데이터 사용에 동의하면 연결해 주세요.",modifier=Modifier.verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={
            consent=false
            val requested=pleosPermissions.filter { p -> runCatching { context.packageManager.getPermissionInfo(p,0).protection==PermissionInfo.PROTECTION_DANGEROUS && context.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED }.getOrDefault(false) }.toTypedArray()
            if(requested.isEmpty()) c.switchMode(DataMode.PLEOS) else permissions.launch(requested)
        }) { Text("동의하고 연결") }},dismissButton={TextButton(onClick={consent=false}) { Text("취소") }})
    if(delete && c.canInteract) AlertDialog(onDismissRequest={delete=false},title={Text("기록을 모두 삭제할까요?")},text={Text("현재 ${c.mode.label}의 모든 프로필 기록이 지워져요. 되돌릴 수 없어요.")},
        confirmButton={TextButton(onClick={delete=false;c.clearCurrentData()}) { Text("모두 삭제") }},dismissButton={TextButton(onClick={delete=false}) { Text("취소") }})
}

@Composable private fun CompactDriverDialog(c: CarLogController, close: () -> Unit) {
    AlertDialog(onDismissRequest=close,properties=DialogProperties(dismissOnClickOutside=!c.askDriver,usePlatformDefaultWidth=false),modifier=Modifier.widthIn(max=440.dp).padding(16.dp),
        title={Text("이번 운전자는 누구인가요?")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("운전자를 고르면 이전 운전자의 인계를 보여드려요.",style=CarLog.type.bodyS,color=Muted)
            drivers.forEach { d -> OutlinedButton(onClick={c.selectDriver(d.id);close()},enabled=!c.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("driver-option-${d.id}")) { Text(d.name+if(c.driver==d.id) " · 마지막 운전자" else "") } }
            Text("프로필은 작성자 구분용이며 본인 인증이 아니에요.",style=CarLog.type.labelS,color=Muted)
        }},confirmButton={},dismissButton={TextButton(onClick=close) { Text(if(c.askDriver) "${c.currentDriver.name}로 계속하기" else "닫기") }})
}

@Composable private fun CompactLock(c: CarLogController) {
    Surface(Modifier.fillMaxSize().testTag("driving-lock"),color=CarLogStaticColors.OverlayBg,contentColor=CarLogStaticColors.OverlayText) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock,null,Modifier.size(40.dp));Spacer(Modifier.height(16.dp))
            Text(if(c.mode==DataMode.DEMO&&c.demoMoving) "주행 중에는 잠시 쉬어갈게요" else "정차 상태를 확인하고 있어요",style=CarLog.type.headlineS)
            Text("인계 확인과 입력은 안전하게 정차한 뒤 이용해 주세요.",style=CarLog.type.bodyS,color=CarLogStaticColors.OverlayTextSecondary,modifier=Modifier.padding(vertical=16.dp))
            if(c.latestIncoming!=null&&!c.suspended&&(!c.askDriver||c.reading)) Button(onClick={c.toggleReading()},colors=ButtonDefaults.buttonColors(containerColor=CarLogStaticColors.OverlayButton,contentColor=CarLogStaticColors.OnOverlayButton)) { Text(if(c.reading) "■ 읽기 중지" else "▶ 인계 읽어주기") }
            if(c.mode==DataMode.DEMO&&c.demoMoving) TextButton(onClick={c.simulateMoving()}) { Text("정차 상태로 돌아가기",color=CarLogStaticColors.OverlayText) }
            else if(c.mode==DataMode.PLEOS&&!c.busy) TextButton(onClick={c.switchMode(DataMode.DEMO)}) { Text("시연 모드로 돌아가기",color=CarLogStaticColors.OverlayText) }
        }
    }
}

@Composable private fun CompactVoiceDialog(c: CarLogController, sheet: VoiceSheet) {
    var seconds by remember(sheet.deadline) { mutableIntStateOf(10) }
    LaunchedEffect(sheet.deadline) {
        val end=sheet.deadline ?: return@LaunchedEffect
        while(true) { seconds=((end-SystemClock.uptimeMillis()+999)/1000).toInt().coerceIn(0,10);if(seconds==0)break;delay(200) }
    }
    val retry=sheet.phase==VoicePhase.NO_SPEECH||sheet.phase==VoicePhase.FAILED
    AlertDialog(onDismissRequest={c.cancelVoice()},modifier=Modifier.testTag("voice-sheet"),title={Text(when(sheet.phase) {
        VoicePhase.PREPARING->"준비하고 있어요";VoicePhase.LISTENING->"듣고 있어요";VoicePhase.PROCESSING->"받아 적는 중이에요";VoicePhase.NO_SPEECH->"말씀이 들리지 않았어요";VoicePhase.FAILED->"음성 입력을 잠시 쓸 수 없어요"
    })},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(sheet.engine,style=CarLog.type.labelS,color=Muted)
        Text(if(sheet.purpose==VoicePurpose.PARKING) "주차한 층과 구역을 말해 주세요." else "다음 운전자에게 전할 말을 10초 안에 말해 주세요.",style=CarLog.type.bodyS)
        if(sheet.phase==VoicePhase.LISTENING) LinearProgressIndicator(progress={c.voiceLevel.coerceIn(.02f,1f)},modifier=Modifier.fillMaxWidth())
        Text(sheet.caption,style=CarLog.type.bodyM,minLines=3,maxLines=3)
        Text(if(retry) sheet.error ?: "" else if(sheet.phase==VoicePhase.LISTENING) "${seconds}초 남음" else "잠시 기다려 주세요",style=CarLog.type.bodyS,color=if(retry) Amber else Lime)
    }},confirmButton={Column {
        if(retry) Button(onClick={c.retryVoice()}) { Text("다시 말하기") }
        else Button(onClick={c.finishSpeaking()},enabled=sheet.phase==VoicePhase.LISTENING||sheet.phase==VoicePhase.PREPARING) { Text("말하기 끝") }
        TextButton(onClick={c.voiceToManual()}) { Text("직접 입력") }
    }},dismissButton={TextButton(onClick={c.cancelVoice()}) { Text("취소") }})
}
