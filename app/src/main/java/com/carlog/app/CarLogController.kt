package com.carlog.app

import android.content.Context
import androidx.compose.runtime.*
import com.carlog.app.data.CarLogDatabase
import com.carlog.app.domain.*
import com.carlog.app.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CarLogController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("carlog_preferences", Context.MODE_PRIVATE)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val databases = DataMode.entries.associateWith { CarLogDatabase(appContext, it) }
    private val gate = VehicleUxGate(appContext)
    val voice = VoiceService(appContext)
    var mode by mutableStateOf(DataMode.DEMO); private set
    var driver by mutableStateOf(prefs.getString("driver", "mom") ?: "mom"); private set
    var tab by mutableIntStateOf(0)
    var records by mutableStateOf<List<HandoverWithMemos>>(emptyList()); private set
    var snapshot by mutableStateOf<VehicleSnapshot?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var demoMoving by mutableStateOf(false); private set
    var gpsUnavailable by mutableStateOf(false); private set
    var restrictionsRequired by mutableStateOf<Boolean?>(null); private set
    var parkedCandidate by mutableStateOf(false); private set
    var suspended by mutableStateOf(false); private set
    private var epoch = 0
    private var restrictionJob: Job? = null
    private var captureJob: Job? = null
    private var actionJob: Job? = null
    val canInteract get() = !suspended && HandoverRules.canInteract(mode, demoMoving, restrictionsRequired)
    val currentDriver get() = drivers.first { it.id == driver }
    val incoming get() = records.filter { HandoverRules.visibleTo(it.handover, driver) }
    val openMemos get() = incoming.flatMap { r -> r.memos.filter { it.resolvedAt == null }.map { r.handover to it } }

    init {
        scope.launch {
            withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(DataMode.DEMO).seedDemo() } }
            reload()
        }
        capture()
    }
    fun resume() {
        suspended = false
        if(snapshot == null && !busy) capture()
        restrictionJob?.cancel()
        restrictionJob = scope.launch {
            while(isActive) {
                val old = restrictionsRequired
                restrictionsRequired = gate.restrictionsRequired()
                if(old == true && restrictionsRequired == false) parkedCandidate = true
                if(!canInteract) voice.stop()
                delay(700)
            }
        }
    }
    fun suspend() {
        suspended = true; voice.stop(); restrictionJob?.cancel(); gate.close()
        captureJob?.cancel(); busy = false
        restrictionsRequired = null
    }
    fun selectDriver(id: String) {
        if(!canInteract || busy || actionJob?.isActive == true || id !in drivers.map { it.id }) return
        voice.stop(); driver = id; prefs.edit().putString("driver", id).apply()
        message = "${currentDriver.name} 프로필로 전환했어요"
    }
    fun switchMode(next: DataMode) {
        if(busy || actionJob?.isActive == true || next == mode) return
        voice.stop(); epoch++; mode = next; records = emptyList(); snapshot = null
        demoMoving = false; gpsUnavailable = false; parkedCandidate = false; message = null
        scope.launch { reload() }; capture()
    }
    fun simulateMoving() {
        if(mode != DataMode.DEMO) return
        demoMoving = !demoMoving
        if(demoMoving) voice.stop()
    }
    fun simulateGps() {
        if(mode != DataMode.DEMO || !canInteract) return
        gpsUnavailable = !gpsUnavailable; capture()
    }
    fun capture() {
        if(busy || suspended) return
        val token = epoch; val activeMode = mode
        captureJob = scope.launch {
            busy = true
            try {
                val value = if(activeMode == DataMode.DEMO) {
                    delay(350)
                    val now = System.currentTimeMillis()
                    VehicleSnapshot(DataMode.DEMO, now, battery=32, odometer=12480f, washerWarning=false,
                        latitude=if(gpsUnavailable) null else 37.4967,
                        longitude=if(gpsUnavailable) null else 127.0282,
                        locationTime=if(gpsUnavailable) null else now,
                        confidence=if(gpsUnavailable) null else 0.9f,
                        diagnostics=mapOf("데이터" to "시연용 가상 데이터", "위치" to if(gpsUnavailable) "GPS 음영 시연 · 직접 입력" else "시연 좌표 · 실차 위치 아님"))
                } else PleosDataSource(appContext).capture()
                if(token == epoch) snapshot = value
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if(token == epoch) message = "상태를 가져오지 못했어요 · 다시 시도해 주세요" }
            finally { if(token == epoch) busy = false }
        }
    }
    fun save(parking: String, recipient: String?, memos: List<MemoDraft>, onSaved: () -> Unit) {
        if(!canInteract || busy || actionJob?.isActive == true) return
        val state = snapshot ?: return
        if(System.currentTimeMillis() - state.capturedAt > 120_000) {
            message = "차량 상태 조회 후 2분이 지났어요. 다시 조회한 뒤 저장해 주세요."
            return
        }
        if(!HandoverRules.validDraft(parking, memos) || recipient == driver || (parking.isBlank() && memos.isEmpty())) {
            message = "주차 위치 또는 전달사항을 입력해 주세요"; return
        }
        val activeMode = mode; val author = driver
        val handover = Handover(author=author, recipient=recipient, createdAt=System.currentTimeMillis(), parking=parking.trim(), snapshot=state)
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(activeMode).save(handover, memos) } }
                reload(); parkedCandidate = false; message = "인계 카드를 저장했어요"; onSaved(); tab = 0
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.e("CarLog", "Handover persistence failed",e); message = "저장하지 못했어요 · 입력 내용은 유지됩니다" }
            finally { busy = false }
        }
    }
    fun markRead(memo: Memo) = mutate { db, person -> db.markRead(memo.id, person) }
    fun resolve(memo: Memo, undo: Boolean = false) = mutate { db, person -> db.resolve(memo.id, person, undo) }
    private fun mutate(action: (CarLogDatabase, String) -> Unit) {
        if(!canInteract || busy || actionJob?.isActive == true) return
        val activeMode = mode; val person = driver
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { action(databases.getValue(activeMode), person) } }
                reload()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message = "변경하지 못했어요 · 다시 시도해 주세요" }
            finally { busy = false }
        }
    }
    fun clearCurrentData() {
        if(!canInteract || busy || actionJob?.isActive == true) return
        val activeMode = mode
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(activeMode).deleteAll() } }
                reload(); message = "현재 모드의 인계 기록을 삭제했어요"
            } catch(e: Exception) { message = "삭제하지 못했어요" }
            finally { busy = false }
        }
    }
    fun resetDemo() {
        if(mode != DataMode.DEMO || !canInteract || busy || actionJob?.isActive == true) return
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(mode).apply { deleteAll(); seedDemo() } } }
                reload(); message = "처음 시연 상황으로 돌아왔어요"
            } finally { busy = false }
        }
    }
    fun speakIncoming() {
        if(!canInteract) return
        val text = openMemos.take(5).joinToString(". ") { (_, m) -> m.text }
        if(text.isBlank()) { message = "읽어드릴 미처리 메모가 없어요"; return }
        voice.speak(mode, "확인할 전달사항 ${openMemos.size}건입니다. $text") { message = it }
    }
    private suspend fun reload() {
        val activeMode = mode; val token = epoch
        val value = withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(activeMode).all() } }
        if(token == epoch) records = value
    }
    fun close() {
        voice.stop(); gate.close(); job.cancel()
        CoroutineScope(Dispatchers.IO).launch { job.join(); databases.values.forEach { it.close() } }
    }
}
