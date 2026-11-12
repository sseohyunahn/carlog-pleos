package com.carlog.app.ui

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlog.app.BuildConfig
import com.carlog.app.CarLogController
import com.carlog.app.domain.*

@Composable
internal fun HistoryScreen(c: CarLogController) {
    val visible = c.records.filter { it.handover.author == c.driver || HandoverRules.visibleTo(it.handover, c.driver) }
    LazyColumn(Modifier.fillMaxSize().testTag("history-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("차량에 쌓인 기억", "내가 남기거나 전달받은 기록을 확인하세요.") }
        if (visible.isEmpty()) item { EmptyCard("아직 인계 기록이 없어요", "새 인계 카드를 저장하면 이곳에 남아요.") }
        items(visible, key = { it.handover.id }) { record ->
            PanelBox {
                val h = record.handover
                val mine = HandoverRules.canDelete(h, c.driver)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${name(h.author)} → ${name(h.recipient)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(time(h.createdAt), color = Muted, fontSize = 24.sp)
                }
                Text(h.parking.ifBlank { "주차 위치 미입력" }, color = Lime, modifier = Modifier.padding(top = 10.dp))
                if (h.parking.isNotBlank() && h.parkingBy != null && h.parkingBy != h.author) {
                    Text("${name(h.parkingBy)}님이 ${time(h.parkingAt ?: h.createdAt)}에 기록한 구역", color = Amber, fontSize = 24.sp, modifier = Modifier.padding(top = 4.dp))
                }
                val summary = vehicleSummary(h.snapshot)
                Text("기록 당시 ${summary.ifEmpty { "차량 상태 수신 없음" }} · ${if (h.snapshot.mode == DataMode.DEMO) "시연 데이터" else "Pleos 조회값"}",
                    color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 5.dp))
                vehicleAlerts(h.snapshot).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), color = Amber, fontSize = 24.sp, modifier = Modifier.padding(top = 3.dp)) }
                record.memos.forEach { m ->
                    HorizontalDivider(Modifier.padding(vertical = 13.dp), color = Border)
                    Text(m.text, fontSize = 26.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (m.resolvedAt != null) "${name(m.resolvedBy)} 조치 완료 · ${time(m.resolvedAt)}" else "미처리 · 확인 ${m.readBy.size}명") +
                            if (m.fromVoice) " · 음성" else "",
                            color = if (m.resolvedAt != null) Lime else Muted, fontSize = 24.sp, modifier = Modifier.weight(1f).padding(top = 8.dp))
                        if (m.resolvedBy == c.driver) TextButton(onClick = { c.resolve(m, true) }, enabled = !c.busy) { Text("완료 취소") }
                        if (mine) TextButton(onClick = { c.askDelete(m) }, enabled = !c.busy) { Text("삭제", color = Amber) }
                    }
                }
            }
        }
    }
}

internal val pleosPermissions = listOf(
    "pleos.car.permission.FUSED_LOCATION", "pleos.car.permission.CAR_ENERGY", "pleos.car.permission.CAR_DRIVE",
    "pleos.car.permission.CAR_SAFETY", "pleos.car.permission.CAR_DOORS", "pleos.car.permission.CAR_WINDOWS"
)

@Composable
internal fun SettingsScreen(c: CarLogController) {
    val context = LocalContext.current
    var consent by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        c.switchMode(DataMode.PLEOS)
        if (grants.values.any { !it }) c.message = "일부 권한이 허용되지 않았어요. 읽을 수 있는 정보만 보여드려요."
    }
    LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("함께 쓰는 방법", "시연 환경과 차량 연결 상태를 확인하세요.") }
        item {
            PanelBox {
                Text("데이터 모드", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("시연 데이터와 실제 차량 기록은 각각 따로 저장돼요.", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                    FilterChip(selected = c.mode == DataMode.DEMO, onClick = { c.switchMode(DataMode.DEMO) }, label = { Text("시연 모드") }, enabled = !c.busy)
                    FilterChip(selected = c.mode == DataMode.PLEOS, onClick = { consent = true }, label = { Text("Pleos 연결") }, enabled = !c.busy)
                }
                if (c.mode == DataMode.DEMO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GPS 음영 상황", modifier = Modifier.weight(1f)); Switch(c.gpsUnavailable, onCheckedChange = { c.simulateGps() })
                    }
                    OutlinedButton(onClick = { c.simulateMoving() }, modifier = Modifier.fillMaxWidth()) { Text("주행 중 입력 잠금 시연") }
                    TextButton(onClick = { c.resetDemo() }) { Text("처음 시연 상황으로 복원") }
                }
            }
        }
        item {
            PanelBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("연결 진단", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    LinkChip(c.snapshot, c.mode)
                }
                Text("${c.mode.label} · Android API 28+ · CarLog ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
                val s = c.snapshot
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fields.ALL.forEach { field ->
                        val status = s?.fieldStatus?.get(field) ?: FieldStatus.NOT_READ
                        val at = s?.fieldTimes?.get(field)
                        Row {
                            Text(Fields.labels[field] ?: field, fontSize = 26.sp, modifier = Modifier.width(180.dp))
                            Text(status.label, color = if (status == FieldStatus.OK) Lime else if (status == FieldStatus.NOT_READ || status == FieldStatus.NO_VALUE) Muted else Amber,
                                fontSize = 26.sp, modifier = Modifier.width(200.dp))
                            Text(at?.let { "받은 시각 ${clock(it)}" + staleNote(s, field, now) } ?: "", color = Muted, fontSize = 24.sp)
                        }
                    }
                }
                s?.diagnostics?.forEach { (key, value) -> Text("$key  /  $value", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 9.dp)) }
                s?.let {
                    if (it.latitude != null) Text("마지막 좌표: ${it.latitude}, ${it.longitude}\n위치 받은 시각: ${it.locationTime?.let(::time) ?: "확인 안 됨"}\n신뢰도: ${it.confidence ?: "제공 안 됨"} · SDK 원시값",
                        color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
                }
                TextButton(onClick = { c.refreshVehicle() }, enabled = !c.suspended) { Text("연결 다시 확인") }
            }
        }
        item {
            PanelBox {
                Text("음성", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("음성 입력 · ${c.voice.inputEngineNameFor(c.mode)}" + if (c.mode == DataMode.PLEOS) " (차량 안에서 처리, 녹음 파일은 남기지 않아요)" else "",
                    fontSize = 26.sp, modifier = Modifier.padding(top = 10.dp))
                Text("음성 읽기 · " + if (c.mode == DataMode.PLEOS) "Pleos 음성 안내 (안 되면 Android 음성 안내)" else "Android 음성 안내",
                    fontSize = 26.sp, modifier = Modifier.padding(top = 6.dp))
                Text("음성으로 쓸 수 없을 때는 언제든 직접 입력할 수 있어요.", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            PanelBox {
                Text("기록과 개인정보", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("기록은 앱 내부 데이터베이스에 저장되고 외부 서버로 보내지 않아요. 앱을 삭제하면 함께 지워져요.\n\n" +
                    "이 버전은 공유 프로필 선택 방식이에요. 프로필은 작성자 구분용이며 본인 인증이 아니에요.\n\n" +
                    "정차 후보는 화면 사용 제한이 풀린 상황을 알려주는 보조 정보예요. 시동 OFF나 운전자 교대를 자동으로 확정하지 않아요.",
                    color = Muted, fontSize = 26.sp, lineHeight = 38.sp, modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { delete = true }, modifier = Modifier.padding(top = 16.dp), enabled = c.canInteract && !c.busy) {
                    Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(7.dp)); Text("현재 모드 기록 전체 삭제")
                }
            }
        }
    }
    // Dialogs are separate windows above the driving overlay: close them when the screen may not be used.
    LaunchedEffect(c.canInteract) { if (!c.canInteract) { consent = false; delete = false } }
    if (consent && c.canInteract) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Pleos 차량 연결") },
        text = { Text("차량 상태와 마지막 위치를 읽고, 인계 카드를 저장할 때 이 기기 안에 보관해요.\n\n음성은 Pleos ON_DEVICE 모드를 써요. 해당 차량의 서비스·권한·언어 지원이 필요해요.\n\n읽지 못한 값은 비워 두고 시연값으로 채우지 않아요. 이 차량을 이용할 권한이 있고 위 데이터 사용에 동의하면 연결해 주세요.") },
        confirmButton = {
            TextButton(onClick = {
                consent = false
                val required = pleosPermissions.filter { permission ->
                    runCatching {
                        val info = context.packageManager.getPermissionInfo(permission, 0)
                        info.protection == PermissionInfo.PROTECTION_DANGEROUS &&
                            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
                    }.getOrDefault(false)
                }.toTypedArray()
                if (required.isEmpty()) c.switchMode(DataMode.PLEOS) else permissions.launch(required)
            }) { Text("동의하고 연결") }
        },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("취소") } })
    if (delete && c.canInteract) AlertDialog(onDismissRequest = { delete = false }, title = { Text("기록을 모두 삭제할까요?") },
        text = { Text("현재 ${c.mode.label}의 모든 프로필 인계 기록이 지워져요. 되돌릴 수 없어요.") },
        confirmButton = { TextButton(onClick = { delete = false; c.clearCurrentData() }) { Text("모두 삭제") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("취소") } })
}
