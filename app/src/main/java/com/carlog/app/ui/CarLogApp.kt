package com.carlog.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlog.app.CarLogController
import com.carlog.app.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private val Bg = Color(0xFF101719)
private val Panel = Color(0xFF1B2528)
private val PanelLight = Color(0xFF232F33)
private val Lime = Color(0xFFBCF57A)
private val Muted = Color(0xFF9BAEAF)
private val White = Color(0xFFF2F5EE)
private val Amber = Color(0xFFFFC77A)
private val Border = Color(0xFF344145)
private fun name(id: String?) = drivers.firstOrNull { it.id == id }?.name ?: "다음 운전자"
private fun time(ms: Long) = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(ms))
private fun categoryIcon(c: Category): ImageVector = when(c) {
    Category.ITEM -> Icons.Outlined.Inventory2; Category.CHARGE -> Icons.Outlined.EvStation
    Category.CARE -> Icons.Outlined.Build; Category.OTHER -> Icons.Outlined.Notes
}

@Composable
fun CarLogApp(c: CarLogController) {
    val scheme = darkColorScheme(primary=Lime, onPrimary=Bg, surface=Panel, background=Bg,
        onSurface=White, onBackground=White, secondary=Muted, outline=Border,
        secondaryContainer=Color(0xFF304535),onSecondaryContainer=White)
    MaterialTheme(colorScheme=scheme, typography=Typography()) {
        var profilePicker by remember { mutableStateOf(false) }
        val screenStates = rememberSaveableStateHolder()
        Surface(Modifier.fillMaxSize(), color=Bg) {
            BoxWithConstraints {
                val wide = maxWidth >= 900.dp
                Row(Modifier.fillMaxSize()) {
                    if(wide) Rail(c)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=if(wide) 28.dp else 16.dp, vertical=16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                            if(!wide) Text("CarLog", fontSize=24.sp, fontWeight=FontWeight.Bold, color=Lime)
                            Text(if(wide) "OUR SHARED DRIVE" else "", color=Muted, fontSize=12.sp, letterSpacing=2.sp, modifier=Modifier.weight(1f))
                            Pill(if(c.mode == DataMode.DEMO) "시연 데이터" else "Pleos 조회", if(c.mode == DataMode.DEMO) Amber else Lime)
                            Spacer(Modifier.width(12.dp))
                            FilledTonalButton(onClick={profilePicker=true}, enabled=c.canInteract && !c.busy) {
                                Text("${c.currentDriver.name}  "); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                            }
                        }
                        if(!wide) ScrollableTabRow(selectedTabIndex=c.tab, containerColor=Bg, edgePadding=0.dp) {
                            listOf("인계 보드", "인계 남기기", "지난 기록", "설정").forEachIndexed { i, title ->
                                Tab(c.tab == i, onClick={ if(c.canInteract) { c.voice.stop(); c.tab=i } }, text={Text(title)})
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        if(c.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color=Lime)
                        c.message?.let { msg ->
                            Row(Modifier.fillMaxWidth().padding(bottom=12.dp).background(PanelLight, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                                Text(msg, color=White, fontSize=14.sp, modifier=Modifier.weight(1f))
                                IconButton(onClick={c.message=null}, modifier=Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "안내 닫기") }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            screenStates.SaveableStateProvider("${c.tab}-${c.mode}-${c.driver}") {
                                when(c.tab) {
                                    0 -> Dashboard(c, wide)
                                    1 -> Editor(c)
                                    2 -> History(c)
                                    else -> Settings(c)
                                }
                            }
                            if(!c.canInteract) {
                                Surface(Modifier.fillMaxSize(), color=Bg.copy(alpha=.97f), shape=RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                                        Icon(Icons.Outlined.Shield, null, Modifier.size(48.dp), tint=Lime)
                                        Spacer(Modifier.height(20.dp))
                                        Text(if(c.mode == DataMode.DEMO && c.demoMoving) "주행 중에는 잠시 쉬어갈게요" else "정차 상태를 확인하고 있어요", fontSize=25.sp, fontWeight=FontWeight.Bold)
                                        Spacer(Modifier.height(12.dp))
                                        Text(if(c.mode == DataMode.PLEOS) "차량의 화면 사용 제한을 확인할 수 있어야 입력할 수 있습니다.\n호환되는 Pleos 에뮬레이터에서 실행해 주세요." else "인계 확인과 입력은 안전하게 정차한 뒤 이용해 주세요.", color=Muted)
                                        Spacer(Modifier.height(24.dp))
                                        if(c.mode == DataMode.DEMO && c.demoMoving) Button(onClick={c.simulateMoving()}) { Text("정차 상태로 돌아가기") }
                                        else if(c.mode == DataMode.PLEOS && !c.busy) OutlinedButton(onClick={c.switchMode(DataMode.DEMO)}) { Text("시연 모드로 돌아가기") }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("차에 남기는 기록, 다음 사람에게.", fontSize=11.sp, color=Muted)
                            Text(if(c.mode == DataMode.DEMO) "PROTOTYPE  /  0.1" else "기록은 이 차량 안에 저장됩니다", fontSize=11.sp, color=Muted)
                        }
                    }
                }
            }
        }
        if(profilePicker) AlertDialog(onDismissRequest={profilePicker=false}, title={Text("누가 운전하시나요?")}, text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("공유 프로필을 선택해 인계사항을 확인하세요.\n프로토타입의 프로필 선택은 본인 인증이 아닙니다.", color=Muted)
                drivers.forEach { d -> OutlinedButton(onClick={c.selectDriver(d.id); profilePicker=false}, modifier=Modifier.fillMaxWidth().height(56.dp)) { Text(d.name) } }
            }
        }, confirmButton={}, dismissButton={TextButton(onClick={profilePicker=false}){Text("닫기")}})
    }
}

@Composable private fun Rail(c: CarLogController) {
    Column(Modifier.width(200.dp).fillMaxHeight().background(Color(0xFF141E21)).padding(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Forum, null, tint=Lime, modifier=Modifier.size(30.dp))
            Spacer(Modifier.width(9.dp)); Text("CarLog", color=White, fontSize=27.sp, fontWeight=FontWeight.Bold)
        }
        Text("함께 타는 차의 기억", color=Muted, fontSize=12.sp, modifier=Modifier.padding(top=9.dp))
        Spacer(Modifier.height(48.dp))
        val tabs = listOf("인계 보드" to Icons.Outlined.Dashboard, "인계 남기기" to Icons.Outlined.EditNote, "지난 기록" to Icons.Outlined.History, "설정" to Icons.Outlined.Tune)
        tabs.forEachIndexed { i, pair ->
            val selected = c.tab == i
            Row(Modifier.fillMaxWidth().padding(bottom=9.dp).clip(RoundedCornerShape(14.dp))
                .background(if(selected) Lime else Color.Transparent).clickable(enabled=c.canInteract){c.voice.stop(); c.tab=i}.padding(horizontal=16.dp, vertical=18.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Icon(pair.second, null, tint=if(selected) Bg else Muted, modifier=Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp)); Text(pair.first, color=if(selected) Bg else Muted, fontSize=15.sp, fontWeight=if(selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color=Border)
        Spacer(Modifier.height(20.dp))
        Icon(Icons.Outlined.DirectionsCar, null, tint=Muted)
        Text(if(c.mode == DataMode.DEMO) "우리 가족의 차" else "연결된 차량", fontSize=15.sp, modifier=Modifier.padding(top=9.dp))
        Text(if(c.mode == DataMode.DEMO) "가상 차량 · 시연용" else "Pleos Connect", color=Muted, fontSize=12.sp, modifier=Modifier.padding(top=5.dp))
    }
}

@Composable private fun Dashboard(c: CarLogController, wide: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("안녕하세요, ${c.currentDriver.name}", color=Muted, fontSize=15.sp)
                    Text("출발 전, 이어받을 이야기", fontSize=if(wide) 30.sp else 24.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=6.dp))
                }
                if(wide) OutlinedButton(onClick={c.speakIncoming()}, enabled=c.openMemos.isNotEmpty()) { Icon(Icons.Outlined.VolumeUp,null,Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text("읽어주기") }
            }
        }
        if(c.parkedCandidate) item {
            InfoBox("화면 사용 제한이 해제됐어요. 이용을 마쳤다면 인계 카드를 남겨 주세요.", Lime)
        }
        item {
            if(wide) Row(horizontalArrangement=Arrangement.spacedBy(18.dp), modifier=Modifier.height(IntrinsicSize.Min)) {
                ParkingCard(c, Modifier.weight(1.15f).fillMaxHeight())
                VehicleCard(c, Modifier.weight(1f).fillMaxHeight())
            } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) { ParkingCard(c, Modifier.fillMaxWidth()); VehicleCard(c, Modifier.fillMaxWidth()) }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("확인할 전달사항", fontSize=21.sp, fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(10.dp)); Pill("${c.openMemos.size}건", Lime)
                Spacer(Modifier.weight(1f))
                if(!wide) IconButton(onClick={c.speakIncoming()}) { Icon(Icons.Outlined.VolumeUp,"전달사항 읽어주기") }
                Text("읽어도 미처리 항목은 남아요", color=Muted, fontSize=12.sp)
            }
        }
        if(c.openMemos.isEmpty()) item { EmptyCard("이어받을 메모가 없어요", "인계 남기기에서 기록한 뒤 다른 프로필로 전환해 보세요.") }
        items(c.openMemos.sortedByDescending { it.second.priority == Priority.IMPORTANT }, key={it.second.id}) { (h, m) -> MemoCard(c, h, m) }
        item {
            OutlinedButton(onClick={c.tab=1}, modifier=Modifier.fillMaxWidth().height(58.dp)) {
                Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text("다음 운전자에게 인계 남기기")
            }
        }
    }
}

@Composable private fun ParkingCard(c: CarLogController, modifier: Modifier) {
    val latest = c.incoming.firstOrNull()?.handover
    Surface(modifier, color=Color(0xFF263A31), shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalParking, null, tint=Lime, modifier=Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text("마지막 인계 위치", color=Lime, fontSize=14.sp)
                Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.NorthEast,null,tint=Lime,modifier=Modifier.size(18.dp))
            }
            Text(latest?.parking?.ifBlank { "위치 미입력" } ?: "아직 기록이 없어요", fontSize=29.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(top=22.dp), maxLines=2, overflow=TextOverflow.Ellipsis)
            Text(if(latest != null) "${name(latest.author)}의 기록  ·  ${time(latest.createdAt)}" else "이용을 마친 뒤 위치를 남겨 주세요", color=Color(0xFFBBCCC1), fontSize=13.sp, modifier=Modifier.padding(top=9.dp))
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color=Color(0xFF47604F))
            Text("층·구역은 사용자가 입력한 정보입니다.\n차량 밖 위치 공유는 이번 버전에 포함되지 않습니다.", color=Color(0xFFA7BDAF), fontSize=12.sp, modifier=Modifier.padding(top=13.dp))
        }
    }
}

@Composable private fun VehicleCard(c: CarLogController, modifier: Modifier) {
    val s = c.snapshot
    Surface(modifier, color=Panel, shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("현재 차량 상태", fontSize=14.sp, color=Muted, modifier=Modifier.weight(1f))
                IconButton(onClick={c.capture()}, enabled=!c.busy, modifier=Modifier.size(26.dp)) { Icon(Icons.Outlined.Refresh,"차량 상태 새로고침",Modifier.size(21.dp)) }
            }
            Row(Modifier.padding(top=16.dp), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s?.battery?.let{"$it%"} ?: "—", fontSize=38.sp, fontWeight=FontWeight.Bold, color=White)
                    Text(if(s?.battery == null) "배터리 조회 불가" else "배터리 잔량", color=Muted, fontSize=13.sp)
                }
                CarDrawing(Modifier.width(130.dp).height(72.dp))
            }
            Spacer(Modifier.height(15.dp))
            LinearProgressIndicator(progress={ (s?.battery ?: 0)/100f }, modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color=Lime, trackColor=Border)
            Text(s?.odometer?.let { "누적 ${String.format(Locale.KOREA,"%,.0f",it)} km" } ?: "주행거리 조회 불가", color=Muted, fontSize=12.sp, modifier=Modifier.padding(top=13.dp))
            Text(if(s == null) "상태를 준비하고 있어요" else "${if(s.mode == DataMode.DEMO) "가상 데이터" else "마지막 조회"} · ${time(s.capturedAt)}", color=Muted, fontSize=11.sp, modifier=Modifier.padding(top=4.dp))
        }
    }
}

@Composable private fun CarDrawing(modifier: Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val p=Path().apply {
            moveTo(.06f*w,.66f*h); lineTo(.10f*w,.46f*h); lineTo(.29f*w,.42f*h)
            lineTo(.43f*w,.17f*h); lineTo(.72f*w,.17f*h); lineTo(.88f*w,.44f*h)
            lineTo(.96f*w,.51f*h); lineTo(.97f*w,.70f*h); lineTo(.06f*w,.70f*h); close()
        }
        drawPath(p, Muted, style=Stroke(2.dp.toPx(), cap=StrokeCap.Round))
        drawLine(Muted,Offset(.35f*w,.43f*h),Offset(.79f*w,.43f*h),2.dp.toPx())
        drawLine(Muted,Offset(.58f*w,.20f*h),Offset(.58f*w,.43f*h),2.dp.toPx())
        listOf(.26f,.78f).forEach { x -> drawCircle(Panel,.12f*h,Offset(x*w,.73f*h)); drawCircle(Lime,.10f*h,Offset(x*w,.73f*h),style=Stroke(2.dp.toPx())) }
        drawLine(Lime,Offset(.1f*w,.54f*h),Offset(.2f*w,.54f*h),3.dp.toPx())
    }
}

@Composable private fun MemoCard(c: CarLogController, h: Handover, m: Memo) {
    val important = m.priority == Priority.IMPORTANT
    val read = c.driver in m.readBy
    Surface(Modifier.fillMaxWidth(), color=Panel, shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp,if(important) Color(0xFF665339) else Border)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(if(important) Color(0xFF3B3227) else PanelLight,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
                    Icon(categoryIcon(m.category),null,tint=if(important) Amber else Lime)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(m.category.label,color=if(important) Amber else Muted,fontSize=12.sp)
                        if(important) Text("중요", color=Amber, fontSize=12.sp)
                        if(read) Text("확인함 · 미처리",color=Lime,fontSize=12.sp)
                    }
                    Text(m.text,fontSize=17.sp,lineHeight=25.sp,modifier=Modifier.padding(top=5.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${name(h.author)} → ${name(h.recipient)}  ·  ${time(h.createdAt)}",color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                if(!read) TextButton(onClick={c.markRead(m)},enabled=!c.busy) { Text("확인했어요") }
                Spacer(Modifier.width(6.dp))
                FilledTonalButton(onClick={c.resolve(m)},enabled=!c.busy) { Icon(Icons.Outlined.Check,null,Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("조치 완료") }
            }
        }
    }
}

@Composable private fun Editor(c: CarLogController) {
    var parking by rememberSaveable(c.mode.name, c.driver) { mutableStateOf("") }
    var text by rememberSaveable(c.mode.name, c.driver) { mutableStateOf("") }
    var recipient by rememberSaveable(c.mode.name,c.driver) { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(Category.OTHER.name) }
    var important by rememberSaveable { mutableStateOf(false) }
    val drafts = rememberSaveable(c.mode,c.driver,saver=listSaver(
        save={list: androidx.compose.runtime.snapshots.SnapshotStateList<MemoDraft> -> list.map { d -> JSONObject().put("text",d.text).put("category",d.category.name).put("priority",d.priority.name).toString() }},
        restore={saved -> saved.map { raw -> val j=JSONObject(raw);MemoDraft(j.getString("text"),Category.valueOf(j.getString("category")),Priority.valueOf(j.getString("priority"))) }.toMutableStateList()}
    )) { mutableStateListOf<MemoDraft>() }
    var voiceStatus by remember { mutableStateOf("음성 또는 글로 전달사항을 남겨 주세요") }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted && c.canInteract) c.voice.listen(c.mode, { if(c.canInteract) text=it.take(300) }, {voiceStatus=it})
        else voiceStatus="마이크 권한 없이 글로 입력할 수 있어요"
    }
    DisposableEffect(Unit) { onDispose { c.voice.stop() } }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("handover-editor"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { PageTitle("이용을 마치셨나요?", "다음 운전자가 알아야 할 것만 짧게 남겨 주세요.") }
        item {
            PanelBox {
                Label("01", "누구에게 전달할까요?")
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(top=12.dp).horizontalScroll(rememberScrollState())) {
                    FilterChip(selected=recipient == null,onClick={recipient=null},label={Text("다음 운전자 모두")})
                    drivers.filter { it.id != c.driver }.forEach { d -> FilterChip(selected=recipient == d.id,onClick={recipient=d.id},label={Text(d.name)}) }
                }
                Text("현재 작성자: ${c.currentDriver.name} · 수신 대상을 지정하면 해당 프로필의 인계 보드에 표시됩니다.",color=Muted,fontSize=12.sp)
            }
        }
        item {
            PanelBox {
                Label("02", "주차 위치와 차량 상태")
                OutlinedTextField(value=parking,onValueChange={parking=it.take(80)},label={Text("층·구역 직접 입력")},placeholder={Text("예: B3층 C17구역")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=14.dp))
                val s=c.snapshot
                Row(Modifier.fillMaxWidth().padding(top=15.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn,null,tint=Lime,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(when { s == null -> "차량 정보를 준비 중입니다"
                        s.hasFreshLocation(System.currentTimeMillis()) -> if(s.mode == DataMode.DEMO) "가상 위치가 준비됐어요 · 층·구역은 직접 입력" else "최근 좌표 확보 · 층·구역은 직접 입력"
                        else -> "정확한 현재 위치 없음 · 층·구역을 직접 입력해 주세요" }, color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick={c.capture()},enabled=!c.busy){Text("다시 조회")}
                }
                Text("저장할 상태: 배터리 ${s?.battery?.let{"$it%"} ?: "조회 불가"} · ${s?.capturedAt?.let(::time) ?: "—"}",color=Muted,fontSize=12.sp)
                Text("차량 상태는 위 조회 시점의 스냅샷으로 저장됩니다.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))
            }
        }
        item {
            PanelBox {
                Label("03", "전달사항")
                OutlinedTextField(value=text,onValueChange={text=it.take(300)},label={Text("전달할 내용")},placeholder={Text("예: 트렁크에 회수할 장비가 있어요.")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth().padding(top=14.dp),supportingText={Text("${text.length}/300")})
                Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedButton(onClick={if(c.canInteract) mic.launch(Manifest.permission.RECORD_AUDIO)},enabled=!c.busy){Icon(Icons.Outlined.Mic,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("10초 음성 입력")}
                    Spacer(Modifier.width(12.dp)); Text(voiceStatus,color=Muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                    IconButton(onClick={c.voice.stop(); voiceStatus="음성 입력을 중지했어요"}){Icon(Icons.Outlined.StopCircle,"음성 중지")}
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { item -> FilterChip(selected=category == item.name,onClick={category=item.name},label={Text(item.label)}) }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=important,onCheckedChange={important=it}); Text("중요한 전달사항",fontSize=14.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={
                        drafts += MemoDraft(text.trim(),Category.valueOf(category),if(important) Priority.IMPORTANT else Priority.NORMAL)
                        text=""; important=false
                    },enabled=text.isNotBlank() && drafts.size < 9){Text("목록에 추가")}
                }
                drafts.forEachIndexed { i,draft ->
                    Row(Modifier.fillMaxWidth().padding(top=8.dp).background(PanelLight,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("${draft.category.label} · ${draft.text}",modifier=Modifier.weight(1f),fontSize=14.sp)
                        IconButton(onClick={drafts.removeAt(i)}){Icon(Icons.Outlined.Close,"메모 삭제")}
                    }
                }
            }
        }
        item {
            Button(onClick={
                val pending=drafts.toList() + if(text.isNotBlank()) listOf(MemoDraft(text.trim(),Category.valueOf(category),if(important) Priority.IMPORTANT else Priority.NORMAL)) else emptyList()
                c.save(parking,recipient,pending){parking="";text="";drafts.clear();important=false;c.voice.stop()}
            },enabled=!c.busy && c.snapshot != null && (parking.isNotBlank() || text.isNotBlank() || drafts.isNotEmpty()),modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(15.dp)) {
                Icon(Icons.Outlined.Send,null,Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text("인계 카드 저장",fontSize=17.sp,fontWeight=FontWeight.Bold)
            }
            Text("입력한 메모와 위치는 같은 차량을 쓰는 지정 프로필에 전달됩니다. 민감한 내용은 남기지 마세요.",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
        }
    }
}

@Composable private fun History(c: CarLogController) {
    val visible = c.records.filter { it.handover.author == c.driver || HandoverRules.visibleTo(it.handover,c.driver) }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("차량에 쌓인 기억", "내가 남기거나 전달받은 기록을 확인하세요.") }
        if(visible.isEmpty()) item { EmptyCard("아직 인계 기록이 없어요", "새 인계 카드를 저장하면 이곳에 남습니다.") }
        items(visible,key={it.handover.id}) { record ->
            PanelBox {
                val h=record.handover
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("${name(h.author)} → ${name(h.recipient)}",fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    Text(time(h.createdAt),color=Muted,fontSize=12.sp)
                }
                Text(h.parking.ifBlank { "주차 위치 미입력" },color=Lime,modifier=Modifier.padding(top=10.dp))
                Text("기록 당시 배터리 ${h.snapshot.battery?.let{"$it%"} ?: "조회 불가"} · ${if(h.snapshot.mode == DataMode.DEMO) "시연 데이터" else "Pleos 조회값"}",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=5.dp))
                record.memos.forEach { m ->
                    HorizontalDivider(Modifier.padding(vertical=13.dp),color=Border)
                    Text(m.text,fontSize=15.sp)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(if(m.resolvedAt != null) "${name(m.resolvedBy)} 조치 완료 · ${time(m.resolvedAt)}" else "미처리 · 확인 ${m.readBy.size}명",color=if(m.resolvedAt != null) Lime else Muted,fontSize=12.sp,modifier=Modifier.weight(1f).padding(top=8.dp))
                        if(m.resolvedBy == c.driver) TextButton(onClick={c.resolve(m,true)}){Text("완료 취소")}
                    }
                }
            }
        }
    }
}

@Composable private fun Settings(c: CarLogController) {
    val context = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        c.switchMode(DataMode.PLEOS)
        if(grants.values.any { !it }) c.message="일부 권한이 허용되지 않았어요. 조회 가능한 정보만 표시합니다."
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle("함께 쓰는 방법", "시연 환경과 차량 연결 상태를 확인하세요.") }
        item {
            PanelBox {
                Text("데이터 모드",fontSize=19.sp,fontWeight=FontWeight.Bold)
                Text("시연 데이터와 실제 차량 기록은 각각 따로 저장됩니다.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(top=14.dp)) {
                    FilterChip(selected=c.mode == DataMode.DEMO,onClick={c.switchMode(DataMode.DEMO)},label={Text("시연 모드")},enabled=!c.busy)
                    FilterChip(selected=c.mode == DataMode.PLEOS,onClick={consent=true},label={Text("Pleos 연결")},enabled=!c.busy)
                }
                if(c.mode == DataMode.DEMO) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("GPS 음영 상황",modifier=Modifier.weight(1f)); Switch(c.gpsUnavailable,onCheckedChange={c.simulateGps()})
                    }
                    OutlinedButton(onClick={c.simulateMoving()},modifier=Modifier.fillMaxWidth()){Text("주행 중 입력 잠금 시연")}
                    TextButton(onClick={c.resetDemo()}){Text("처음 시연 상황으로 복원")}
                }
            }
        }
        item {
            PanelBox {
                Text("연결 진단",fontSize=19.sp,fontWeight=FontWeight.Bold)
                Text("${c.mode.label} · Android API 28+ · CarLog 0.1.0",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
                c.snapshot?.diagnostics?.forEach { (key,value) ->
                    Text("$key  /  $value",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=9.dp))
                }
                c.snapshot?.let { s -> if(s.latitude != null) Text("마지막 좌표: ${s.latitude}, ${s.longitude}\n위치 수신 시각: ${s.locationTime?.let(::time) ?: "미확인"}\n신뢰도: ${s.confidence ?: "미제공"} · SDK 원시값",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=12.dp)) }
                TextButton(onClick={c.capture()},enabled=!c.busy){Text("연결 다시 확인")}
            }
        }
        item {
            PanelBox {
                Text("기록과 개인정보",fontSize=19.sp,fontWeight=FontWeight.Bold)
                Text("기록은 앱 내부 데이터베이스에 저장되며 외부 서버로 전송하지 않습니다. 앱 삭제 시 함께 삭제됩니다.\n\n이 버전은 공유 프로필 선택 방식입니다. 프로필별 본인 인증과 모바일 사전 공유는 아직 제공하지 않습니다.\n\n정차 후보는 화면 사용 제한 해제 상황을 알려주는 보조 정보입니다. 시동 OFF나 운전자 교대를 자동 확정하지 않습니다.",color=Muted,fontSize=14.sp,lineHeight=23.sp,modifier=Modifier.padding(top=12.dp))
                OutlinedButton(onClick={delete=true},modifier=Modifier.padding(top=16.dp)){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(7.dp));Text("현재 모드 기록 전체 삭제")}
            }
        }
    }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("Pleos 차량 연결")},text={Text("차량 상태와 마지막 위치를 조회하고, 인계 카드 저장 시 이 기기 안에 보관합니다.\n\n음성은 Pleos ON_DEVICE 모드를 사용합니다. 해당 차량의 서비스·권한·언어 지원이 필요합니다.\n\n조회 불가능한 값은 비워 두며 시연값으로 대신하지 않습니다. 이 차량을 이용할 권한이 있고 위 데이터 사용에 동의할 때 연결해 주세요.")},
        confirmButton={TextButton(onClick={
            consent=false
            val declared=listOf("pleos.car.permission.FUSED_LOCATION","pleos.car.permission.CAR_ENERGY","pleos.car.permission.CAR_DRIVE","pleos.car.permission.CAR_SAFETY")
            val required=declared.filter { permission -> runCatching {
                val info=context.packageManager.getPermissionInfo(permission,0)
                (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS &&
                    context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false) }.toTypedArray()
            if(required.isEmpty()) c.switchMode(DataMode.PLEOS) else permissions.launch(required)
        }){Text("동의하고 연결")}},dismissButton={TextButton(onClick={consent=false}){Text("취소")}})
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("기록을 모두 삭제할까요?")},text={Text("현재 ${c.mode.label}의 모든 프로필 인계 기록이 삭제됩니다. 되돌릴 수 없습니다.")},confirmButton={TextButton(onClick={delete=false;c.clearCurrentData()}){Text("모두 삭제")}},dismissButton={TextButton(onClick={delete=false}){Text("취소")}})
}

@Composable private fun PageTitle(title:String,description:String) {
    Column { Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold);Text(description,color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=8.dp)) }
}
@Composable private fun PanelBox(content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),color=Panel,shape=RoundedCornerShape(20.dp)) { Column(Modifier.padding(24.dp),content=content) }
}
@Composable private fun Label(step:String,text:String) {
    Row(verticalAlignment=Alignment.CenterVertically) { Text(step,color=Lime,fontSize=13.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.width(12.dp));Text(text,fontSize=18.sp,fontWeight=FontWeight.Bold) }
}
@Composable private fun Pill(text:String,color:Color) {
    Text(text,color=color,fontSize=12.sp,fontWeight=FontWeight.Medium,modifier=Modifier.background(color.copy(alpha=.10f),CircleShape).padding(horizontal=12.dp,vertical=6.dp))
}
@Composable private fun InfoBox(text:String,color:Color) {
    Text(text,color=color,fontSize=14.sp,modifier=Modifier.fillMaxWidth().background(PanelLight,RoundedCornerShape(12.dp)).padding(16.dp))
}
@Composable private fun EmptyCard(title:String,description:String) {
    PanelBox { Icon(Icons.Outlined.TaskAlt,null,tint=Lime,modifier=Modifier.size(30.dp));Text(title,fontSize=19.sp,modifier=Modifier.padding(top=12.dp));Text(description,color=Muted,fontSize=14.sp,modifier=Modifier.padding(top=8.dp)) }
}
