package com.carlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.carlog.app.BuildConfig
import com.carlog.app.CarLogController
import com.carlog.app.domain.*
import com.carlog.app.ui.theme.*

private val tabTitles = listOf("인계 보드", "인계 남기기", "지난 기록", "설정")

@Composable
fun CarLogApp(c: CarLogController, darkTheme: Boolean = isSystemInDarkTheme()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 840.dp || maxHeight < 600.dp) {
            CarLogTheme(darkTheme = darkTheme, compact = true) { CompactCarLogApp(c) }
        } else VehicleCarLogApp(c, darkTheme)
    }
}

@Composable
private fun VehicleCarLogApp(c: CarLogController, darkTheme: Boolean) {
    CarLogTheme(darkTheme = darkTheme) {
        var profilePicker by rememberSaveable { mutableStateOf(false) }
        Surface(Modifier.fillMaxSize().testTag("app-surface"), color = Bg) {
            BoxWithConstraints {
                val wide = maxWidth >= CarLog.dimens.fullLayoutBreakpoint
                Row(Modifier.fillMaxSize()) {
                    if (wide) Rail(c)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (wide) 56.dp else 40.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            if (!wide) Text("CarLog", style = CarLog.type.wordmark)
                            Spacer(Modifier.weight(1f))
                            LinkChip(c.snapshot, c.mode)
                            FilledTonalButton(onClick = { profilePicker = true }, enabled = c.canInteract && !c.busy,
                                modifier = Modifier.heightIn(min = 80.dp).testTag("driver-button")) {
                                Box(Modifier.size(56.dp).background(PanelLight, CircleShape), contentAlignment = Alignment.Center) {
                                    Text(c.currentDriver.name.take(1), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(16.dp))
                                Text(c.currentDriver.name)
                                Icon(Icons.Outlined.KeyboardArrowDown, "운전자 바꾸기", Modifier.size(32.dp))
                            }
                        }
                        if (!wide) Row(Modifier.fillMaxWidth()) {
                            tabTitles.forEachIndexed { i, title ->
                                val selected = c.tab == i
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = c.canInteract) { c.selectTab(i) }.height(80.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(title, style = CarLog.type.nav, maxLines = 1,
                                        color = if (selected) White else Muted,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
                                    Spacer(Modifier.height(12.dp))
                                    if (selected) Box(Modifier.width(48.dp).height(4.dp).background(Lime, CircleShape))
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        if (c.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime)
                        c.message?.let { msg ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).background(PanelLight, RoundedCornerShape(24.dp)).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(msg, style = CarLog.type.labelM, modifier = Modifier.weight(1f))
                                IconButton(onClick = { c.message = null }, modifier = Modifier.size(76.dp)) {
                                    Icon(Icons.Outlined.Close, "안내 닫기", Modifier.size(32.dp))
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            when (c.tab) {
                                0 -> BoardScreen(c, wide)
                                1 -> EditorScreen(c)
                                2 -> HistoryScreen(c)
                                else -> SettingsScreen(c)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("차에 남기는 기록, 다음 사람에게.", style = CarLog.type.labelS, color = Muted)
                            Text(if (c.mode == DataMode.DEMO) "PROTOTYPE / ${BuildConfig.VERSION_NAME}" else "기록은 이 차량 안에 저장돼요",
                                style = CarLog.type.labelS, color = Muted)
                        }
                    }
                }
                if (!c.canInteract) DrivingOverlay(c)
            }
        }
        if (c.canInteract) {
            if (c.askDriver || profilePicker) DriverDialog(c, c.askDriver) { profilePicker = false }
            c.deleteCandidate?.let { DeleteMemoDialog(c, it) }
        }
        c.voiceSheet?.let { VoiceSheetDialog(c, it) }
    }
}

@Composable private fun Rail(c: CarLogController) {
    Column(Modifier.width(320.dp).fillMaxHeight().background(Bg).padding(24.dp)) {
        Text("CarLog", style = CarLog.type.wordmark, modifier = Modifier.padding(vertical = 32.dp))
        Spacer(Modifier.height(24.dp))
        val icons = listOf(Icons.Outlined.Home, Icons.Outlined.Edit, Icons.Outlined.DateRange, Icons.Outlined.Settings)
        tabTitles.forEachIndexed { i, title ->
            val selected = c.tab == i
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(24.dp))
                .background(if (selected) PanelLight else Bg).clickable(enabled = c.canInteract) { c.selectTab(i) }
                .heightIn(min = 88.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selected) Box(Modifier.width(4.dp).height(40.dp).background(Lime, CircleShape))
                Icon(icons[i], null, tint = if (selected) White else Muted, modifier = Modifier.size(32.dp))
                Text(title, style = CarLog.type.nav, color = if (selected) White else Muted)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("함께 타는 차의 기억", style = CarLog.type.labelM, color = Muted)
    }
}

@Composable private fun DrivingOverlay(c: CarLogController) {
    Surface(Modifier.fillMaxSize().testTag("driving-lock"), color = CarLogStaticColors.OverlayBg,
        contentColor = CarLogStaticColors.OverlayText) {
        Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(64.dp), tint = CarLogStaticColors.OverlayTextSecondary)
            Spacer(Modifier.height(32.dp))
            Text(if (c.mode == DataMode.DEMO && c.demoMoving) "주행 중에는 잠시 쉬어갈게요" else "정차 상태를 확인하고 있어요",
                style = CarLog.type.headlineL)
            Spacer(Modifier.height(24.dp))
            Text(if (c.mode == DataMode.PLEOS) "차량의 화면 사용 제한을 확인할 수 있어야 입력할 수 있어요.\n호환되는 Pleos 에뮬레이터에서 실행해 주세요."
                else "인계 확인과 입력은 안전하게 정차한 뒤 이용해 주세요.", color = CarLogStaticColors.OverlayTextSecondary)
            if (c.latestIncoming != null && !c.suspended && (!c.askDriver || c.reading)) {
                Spacer(Modifier.height(32.dp))
                Button(onClick = { c.toggleReading() }, modifier = Modifier.height(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CarLogStaticColors.OverlayButton,
                        contentColor = CarLogStaticColors.OnOverlayButton)) {
                    Text(if (c.reading) "■  읽기 중지" else "▶  인계 읽어주기")
                }
            }
            Spacer(Modifier.height(32.dp))
            if (c.mode == DataMode.DEMO && c.demoMoving) TextButton(onClick = { c.simulateMoving() }) {
                Text("정차 상태로 돌아가기", color = CarLogStaticColors.OverlayText)
            } else if (c.mode == DataMode.PLEOS && !c.busy) TextButton(onClick = { c.switchMode(DataMode.DEMO) }) {
                Text("시연 모드로 돌아가기", color = CarLogStaticColors.OverlayText)
            }
        }
    }
}

@Composable private fun DriverDialog(c: CarLogController, atLaunch: Boolean, onClose: () -> Unit) {
    val close = { if (atLaunch) c.keepDriver(); onClose() }
    AlertDialog(onDismissRequest = close,
        properties = DialogProperties(dismissOnClickOutside = !atLaunch, usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 980.dp).padding(40.dp),
        title = { Text("이번 운전자는 누구인가요?", style = CarLog.type.titleL) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(if (atLaunch) "운전자를 고르면 이전 운전자가 남긴 인계를 먼저 보여드려요." else "메모와 인계 카드의 작성자가 바뀌어요.", color = Muted)
                drivers.forEach { d ->
                    val current = d.id == c.driver
                    Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(28.dp))
                        .background(PanelLight).border(if (current) 3.dp else 1.dp, if (current) CarLog.colors.textSecondary else Border, RoundedCornerShape(28.dp))
                        .clickable(enabled = !c.busy) { c.selectDriver(d.id); onClose() }
                        .testTag("driver-option-${d.id}").padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.size(76.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                            Text(d.name.take(1), style = CarLog.type.titleM)
                        }
                        Text(d.name, style = CarLog.type.titleS)
                        if (current) Text("마지막 운전자", style = CarLog.type.labelM, color = Muted)
                    }
                }
                Text("프로필은 작성자 구분용이며 본인 인증이 아니에요.", style = CarLog.type.labelM, color = Muted)
            }
        }, confirmButton = {}, dismissButton = {
            TextButton(onClick = close) { Text(if (atLaunch) "${c.currentDriver.name}로 계속하기" else "닫기") }
        })
}

@Composable private fun DeleteMemoDialog(c: CarLogController, memo: Memo) {
    AlertDialog(onDismissRequest = { c.dismissDelete() }, title = { Text("메모를 삭제할까요?") },
        text = { Text("${memo.text}\n\n저장된 인계 카드에서도 지워지고 되돌릴 수 없어요.") },
        confirmButton = { TextButton(onClick = { c.confirmDelete() }, modifier = Modifier.testTag("confirm-delete")) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { c.dismissDelete() }) { Text("취소") } })
}
