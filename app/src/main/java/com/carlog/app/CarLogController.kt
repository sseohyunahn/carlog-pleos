package com.carlog.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carlog.app.data.CarLogDatabase
import com.carlog.app.data.LastParking
import com.carlog.app.domain.*
import com.carlog.app.platform.DemoVehicleFeed
import com.carlog.app.platform.PleosVehicleFeed
import com.carlog.app.platform.VehicleFeed
import com.carlog.app.platform.VehicleUxGate
import com.carlog.app.platform.VoiceInput
import com.carlog.app.platform.voice.VoiceService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** What the in-app recording sheet is filling in. */
enum class VoicePurpose { MEMO, PARKING }

enum class VoicePhase { PREPARING, LISTENING, PROCESSING, NO_SPEECH, FAILED }

/**
 * State of the 10 s recording sheet. [deadline] is SystemClock.uptimeMillis() of the automatic stop.
 * The microphone level is not part of it (see [CarLogController.voiceLevel]): it changes many times per second and
 * would otherwise recompose the whole sheet and the editor behind it.
 */
data class VoiceSheet(
    val purpose: VoicePurpose,
    val phase: VoicePhase,
    val engine: String,
    val caption: String = "",
    val deadline: Long? = null,
    val error: String? = null
)

/**
 * Screen state owner. A ViewModel, so the mode, driver, tab, editor draft and recording sheet survive Activity
 * recreation (window resize, rotation). SDK, voice and database resources are released only in [onCleared], i.e.
 * when the Activity really finishes; [suspend] (onStop) pauses the vehicle feed, voice and the UX gate polling.
 */
class CarLogController(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("carlog_preferences", Context.MODE_PRIVATE)
    private val scope get() = viewModelScope
    private val mutex = Mutex()
    private val databases = DataMode.entries.associateWith { CarLogDatabase(application, it) }
    private val gate = VehicleUxGate(application)
    val voice = VoiceService(application)

    var mode by mutableStateOf(DataMode.DEMO); private set
    var driver by mutableStateOf(prefs.getString(KEY_DRIVER, "mom")?.takeIf { id -> drivers.any { it.id == id } } ?: "mom"); private set
    var tab by mutableIntStateOf(0); private set
    var records by mutableStateOf<List<HandoverWithMemos>>(emptyList()); private set
    var lastParking by mutableStateOf<LastParking?>(null); private set
    var snapshot by mutableStateOf<VehicleSnapshot?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var demoMoving by mutableStateOf(false); private set
    var gpsUnavailable by mutableStateOf(false); private set
    var restrictionsRequired by mutableStateOf<Boolean?>(null); private set
    var parkedCandidate by mutableStateOf(false); private set
    var suspended by mutableStateOf(false); private set
    /** "이번 운전자는 누구인가요?" is pending (first launch, or back after more than 20 minutes). */
    var askDriver by mutableStateOf(false); private set
    var reading by mutableStateOf(false); private set
    var voiceSheet by mutableStateOf<VoiceSheet?>(null); private set
    /** Smoothed microphone level 0..1 of the open sheet. Read it only where it is drawn (progress lambda). */
    var voiceLevel by mutableFloatStateOf(0f); private set
    /** A recording sheet is open. Changes only when the sheet opens/closes, not on every caption or phase change. */
    val voiceActive by derivedStateOf { voiceSheet != null }
    var deleteCandidate by mutableStateOf<Memo?>(null); private set
    /** One-shot request for the editor to focus a manual input field (consumed by the UI). */
    var focusTarget by mutableStateOf<VoicePurpose?>(null)

    // Editor draft: kept here so it survives Activity recreation.
    var editorParking by mutableStateOf(""); private set
    var parkingReuse by mutableStateOf<LastParking?>(null); private set
    var editorText by mutableStateOf(""); private set
    var editorRecipient by mutableStateOf<String?>(null); private set
    var editorCategory by mutableStateOf(Category.OTHER); private set
    /** The driver chose the category by hand: a later dictation must not reclassify it. */
    private var categoryPicked = false
    var editorImportant by mutableStateOf(false); private set
    var editorFromVoice by mutableStateOf(false); private set
    var voiceNotice by mutableStateOf<String?>(null); private set
    val drafts = mutableStateListOf<MemoDraft>()

    private var epoch = 0
    private var feed: VehicleFeed? = null
    private var feedToken = 0
    private var voiceToken = 0
    private var readToken = 0
    private var restrictionJob: Job? = null
    private var actionJob: Job? = null
    private var autoStopJob: Job? = null
    /** "말하기 끝" was pressed for the open sheet. */
    private var voiceStopPressed = false
    /** Unthrottled smoothed level; [voiceLevel] follows it in steps of at least [LEVEL_STEP]. */
    private var smoothedLevel = 0f

    val canInteract get() = !suspended && HandoverRules.canInteract(mode, demoMoving, restrictionsRequired)
    val currentDriver get() = drivers.first { it.id == driver }
    val incoming get() = records.filter { HandoverRules.visibleTo(it.handover, driver) }
    val latestIncoming get() = HandoverRules.latestIncoming(records, driver)
    val openMemos get() = incoming.flatMap { r -> r.memos.filter { it.resolvedAt == null }.map { r.handover to it } }
    /** Unresolved memos this driver wrote (shown on the board so the author can delete a mistake). */
    val myOpenMemos get() = records.filter { it.handover.author == driver }
        .flatMap { r -> r.memos.filter { it.resolvedAt == null }.map { r.handover to it } }

    init {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(DataMode.DEMO).seedDemo() } }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Demo seed failed", e) }
            reload()
        }
        // Warm up the speech engines so the first "읽어주기" does not wait for TTS initialisation.
        runCatching { voice.prepare(mode) }.onFailure { Log.w(TAG, "Voice prepare failed", it) }
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    /** Activity onStart. */
    fun resume() {
        suspended = false
        val last = prefs.getLong(KEY_LAST_STOPPED, 0L).takeIf { it > 0L }
        if (HandoverRules.shouldAskDriver(last, System.currentTimeMillis())) askDriver = true
        if (feed == null) startFeed()
        startGatePolling()
    }

    /** Activity onStop. A configuration change keeps everything running: the ViewModel survives it. */
    fun suspend(changingConfigurations: Boolean) {
        prefs.edit().putLong(KEY_LAST_STOPPED, System.currentTimeMillis()).apply()
        if (changingConfigurations) return
        suspended = true
        cancelVoice(); stopReading(); voice.stop()
        stopFeed()
        val polling = restrictionJob
        restrictionJob = null
        polling?.cancel()
        scope.launch(Dispatchers.Default) {
            polling?.join()
            if (suspended) gate.close()
        }
        restrictionsRequired = null
    }

    override fun onCleared() {
        voiceToken++; readToken++
        runCatching { voice.release() }.onFailure { Log.w(TAG, "Voice release failed", it) }
        stopFeed()
        restrictionJob?.cancel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { gate.close() }
            mutex.withLock { databases.values.forEach { db -> runCatching { db.close() } } }
        }
        super.onCleared()
    }

    private fun startGatePolling() {
        restrictionJob?.cancel()
        restrictionJob = scope.launch {
            while (isActive) {
                // Reflection on android.car runs off the main thread; the gate caches the manager.
                val value = withContext(Dispatchers.Default) { gate.restrictionsRequired() }
                val old = restrictionsRequired
                restrictionsRequired = value
                if (old == true && value == false) parkedCandidate = true
                if (!canInteract) onInteractionBlocked()
                delay(GATE_POLL_MS)
            }
        }
    }

    /** Typing and recording stop while driving; reading aloud continues (it is eyes-free). */
    private fun onInteractionBlocked() {
        if (voiceSheet != null) cancelVoice()
        deleteCandidate = null
    }

    // ---- vehicle feed -----------------------------------------------------------------------------------------

    private fun startFeed() {
        stopFeed()
        val token = ++feedToken
        val activeMode = mode
        val next: VehicleFeed = if (activeMode == DataMode.DEMO) DemoVehicleFeed() else PleosVehicleFeed(getApplication())
        feed = next
        next.start { value -> if (token == feedToken && value.mode == mode) onSnapshot(value) }
        if (next is DemoVehicleFeed && gpsUnavailable) next.setGpsUnavailable(true)
    }

    private fun stopFeed() {
        feedToken++
        val old = feed
        feed = null
        runCatching { old?.stop() }.onFailure { Log.w(TAG, "Feed stop failed", it) }
    }

    private fun onSnapshot(value: VehicleSnapshot) {
        try {
            // The Pleos feed merges inside one session; merging again keeps last valid values across onStop/onStart.
            // Demo values are never merged so the "GPS 음영" demo really clears the location.
            snapshot = if (value.mode == DataMode.PLEOS) {
                val prev = snapshot?.takeIf { it.mode == DataMode.PLEOS }
                SnapshotFreshness.keepKnownStatus(prev, SnapshotMerge.merge(prev, value))
            } else value
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot update failed", e)
        }
    }

    fun refreshVehicle() {
        if (suspended) return
        feed?.refresh() ?: startFeed()
    }

    // ---- navigation / profile ---------------------------------------------------------------------------------

    fun selectTab(index: Int) {
        if (!canInteract || index !in 0..3) return
        if (index != 0) stopReading()
        tab = index
    }

    fun selectDriver(id: String) {
        if (!canInteract || busy || actionJob?.isActive == true || drivers.none { it.id == id }) return
        askDriver = false
        if (id == driver) return
        cancelVoice(); stopReading(); voice.stop()
        driver = id
        prefs.edit().putString(KEY_DRIVER, id).apply()
        clearEditor()
        deleteCandidate = null
        message = "${currentDriver.name} 프로필로 바꿨어요"
    }

    /** "그대로 ○○": keep the current driver. */
    fun keepDriver() { askDriver = false }

    fun switchMode(next: DataMode) {
        if (busy || actionJob?.isActive == true || next == mode) return
        cancelVoice(); stopReading(); voice.stop()
        epoch++
        mode = next
        records = emptyList(); lastParking = null; snapshot = null
        demoMoving = false; gpsUnavailable = false; parkedCandidate = false; message = null
        deleteCandidate = null
        clearEditor()
        runCatching { voice.prepare(next) }.onFailure { Log.w(TAG, "Voice prepare failed", it) }
        scope.launch { reload() }
        if (suspended) stopFeed() else startFeed()
    }

    fun simulateMoving() {
        if (mode != DataMode.DEMO) return
        demoMoving = !demoMoving
        if (demoMoving) onInteractionBlocked()
    }

    fun simulateGps() {
        if (mode != DataMode.DEMO || !canInteract) return
        gpsUnavailable = !gpsUnavailable
        (feed as? DemoVehicleFeed)?.setGpsUnavailable(gpsUnavailable)
    }

    // ---- editor -----------------------------------------------------------------------------------------------

    // While a save runs (it may wait for a vehicle refresh) the editor is frozen, so clearEditor() afterwards only
    // clears what was actually saved.

    fun updateParking(value: String) {
        if (busy) return
        editorParking = VoiceText.takeSafe(value, HandoverRules.MAX_PARKING_LENGTH)
        if (parkingReuse?.text != editorParking) parkingReuse = null
    }

    fun useLastParking() {
        if (busy) return
        val last = lastParking ?: return
        editorParking = last.text.take(HandoverRules.MAX_PARKING_LENGTH)
        parkingReuse = last.copy(text = editorParking)
    }

    fun updateText(value: String) {
        if (busy) return
        editorText = VoiceText.takeSafe(value, HandoverRules.MAX_MEMO_LENGTH)
        if (editorText.isBlank()) { editorFromVoice = false; voiceNotice = null }
        // v1 behaviour: typed text is re-classified live until the driver picks a tag by hand.
        if (!categoryPicked) editorCategory = NoteClassifier.classify(editorText)
    }

    fun updateRecipient(id: String?) { if (!busy) editorRecipient = id?.takeIf { it != driver } }
    fun updateCategory(value: Category) { if (busy) return; editorCategory = value; categoryPicked = true }
    fun updateImportant(value: Boolean) { if (!busy) editorImportant = value }

    val canAddDraft get() = !busy && editorText.isNotBlank() && drafts.size < HandoverRules.MAX_MEMOS_PER_CARD - 1

    fun addDraft() {
        if (!canAddDraft) return
        drafts += currentDraft() ?: return
        editorText = ""; editorImportant = false; editorFromVoice = false; voiceNotice = null; editorCategory = Category.OTHER
        categoryPicked = false
    }

    fun removeDraft(index: Int) { if (!busy && index in drafts.indices) drafts.removeAt(index) }

    private fun currentDraft(): MemoDraft? = editorText.trim().takeIf { it.isNotEmpty() }?.let {
        MemoDraft(it, editorCategory, if (editorImportant) Priority.IMPORTANT else Priority.NORMAL, editorFromVoice)
    }

    private fun clearEditor() {
        editorParking = ""; parkingReuse = null; editorText = ""; editorRecipient = null
        editorCategory = Category.OTHER; editorImportant = false; editorFromVoice = false; voiceNotice = null
        categoryPicked = false
        drafts.clear()
    }

    val hasDraftContent get() = editorParking.isNotBlank() || editorText.isNotBlank() || drafts.isNotEmpty()

    fun save() {
        if (!canInteract || busy || actionJob?.isActive == true) return
        val parking = editorParking.trim()
        val recipient = editorRecipient
        val pending = drafts.toList() + listOfNotNull(currentDraft())
        if (!HandoverRules.validDraft(parking, pending) || recipient == driver || (parking.isBlank() && pending.isEmpty())) {
            message = "주차 위치나 전달사항을 입력해 주세요"; return
        }
        val activeMode = mode
        val author = driver
        val token = epoch
        val reuse = parkingReuse?.takeIf { it.text == parking }
        actionJob = scope.launch {
            busy = true
            try {
                val state = freshSnapshot(activeMode, token) ?: run {
                    if (token == epoch) message = "차량 상태를 새로 받지 못했어요 · 잠시 후 다시 저장해 주세요"
                    return@launch
                }
                val handover = Handover(
                    author = author, recipient = recipient, createdAt = System.currentTimeMillis(), parking = parking,
                    snapshot = state, parkingBy = reuse?.by, parkingAt = reuse?.at
                )
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(activeMode).save(handover, pending) } }
                if (token != epoch) return@launch
                reload()
                clearEditor()
                parkedCandidate = false
                message = "인계 카드를 저장했어요 · 다음 운전자에게 보여요"
                tab = 0
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "Handover persistence failed", e)
                if (token == epoch) message = "저장하지 못했어요 · 입력한 내용은 그대로 있어요"
            } finally { busy = false }
        }
    }

    /**
     * The state to store with a handover. Freshness is judged from each field's receive time, not from capturedAt
     * (the Pleos feed stamps capturedAt on every publish, even when nothing new arrived). When a held value is older
     * than [HandoverRules.SNAPSHOT_MAX_AGE_MS], the feed reads again and a round that completes after the tap is
     * awaited for a short while. Whatever is returned has every value older than the limit removed, so an old reading
     * is never saved as the state at handover time; a car that answers nothing can still save. Null only when there
     * is no snapshot of [activeMode] at all.
     */
    private suspend fun freshSnapshot(activeMode: DataMode, token: Int): VehicleSnapshot? {
        val requestedAt = System.currentTimeMillis()
        fun current(): VehicleSnapshot? {
            val now = System.currentTimeMillis()
            return snapshot?.takeIf {
                it.mode == activeMode && now - it.capturedAt <= HandoverRules.SNAPSHOT_MAX_AGE_MS &&
                    (SnapshotFreshness.allFresh(it, now) ||
                        (it.capturedAt >= requestedAt && it.link != LinkState.CONNECTING && !it.link.problem))
            }
        }
        current()?.let { return SnapshotFreshness.dropStale(it, System.currentTimeMillis()) }
        message = REFRESHING
        refreshVehicle()
        val value = withTimeoutOrNull(REFRESH_WAIT_MS) {
            var found = current()
            while (found == null && token == epoch) { delay(250); found = current() }
            found
        }
        if (token != epoch) return null
        if (message == REFRESHING) message = null
        if (value == null) Log.w(TAG, "Vehicle refresh before save timed out; saving without old values")
        val state = value ?: snapshot?.takeIf { it.mode == activeMode } ?: return null
        return SnapshotFreshness.dropStale(state, System.currentTimeMillis())
    }

    // ---- memo actions -----------------------------------------------------------------------------------------

    fun markRead(memo: Memo) = mutate { db, person -> db.markRead(memo.id, person) }
    fun resolve(memo: Memo, undo: Boolean = false) = mutate { db, person -> db.resolve(memo.id, person, undo) }

    fun askDelete(memo: Memo) { if (canInteract && !busy) deleteCandidate = memo }
    fun dismissDelete() { deleteCandidate = null }

    fun confirmDelete() {
        val memo = deleteCandidate ?: return
        deleteCandidate = null
        if (!canInteract || busy || actionJob?.isActive == true) return
        val activeMode = mode; val person = driver; val token = epoch
        actionJob = scope.launch {
            busy = true
            try {
                val removed = withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(activeMode).deleteMemo(memo.id, person) } }
                if (token != epoch) return@launch
                reload()
                message = if (removed) "메모를 삭제했어요" else "내가 남긴 메모만 삭제할 수 있어요"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "Memo delete failed", e)
                if (token == epoch) message = "삭제하지 못했어요 · 다시 시도해 주세요"
            } finally { busy = false }
        }
    }

    private fun mutate(action: (CarLogDatabase, String) -> Unit) {
        if (!canInteract || busy || actionJob?.isActive == true) return
        val activeMode = mode; val person = driver; val token = epoch
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { action(databases.getValue(activeMode), person) } }
                if (token == epoch) reload()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "Memo update failed", e)
                if (token == epoch) message = "변경하지 못했어요 · 다시 시도해 주세요"
            } finally { busy = false }
        }
    }

    fun clearCurrentData() {
        if (!canInteract) { message = "안전하게 정차한 뒤 다시 시도해 주세요"; return }
        if (busy || actionJob?.isActive == true) return
        val activeMode = mode; val token = epoch
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(activeMode).deleteAll() } }
                if (token == epoch) { reload(); message = "현재 모드의 인계 기록을 삭제했어요" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Delete all failed", e); message = "삭제하지 못했어요 · 다시 시도해 주세요" }
            finally { busy = false }
        }
    }

    fun resetDemo() {
        if (mode != DataMode.DEMO || !canInteract || busy || actionJob?.isActive == true) return
        val token = epoch
        actionJob = scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { mutex.withLock { databases.getValue(DataMode.DEMO).apply { deleteAll(); seedDemo() } } }
                if (token == epoch) { reload(); message = "처음 시연 상황으로 돌아왔어요" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Demo reset failed", e); message = "시연 상황을 복원하지 못했어요" }
            finally { busy = false }
        }
    }

    private suspend fun reload() {
        val activeMode = mode; val token = epoch
        try {
            val (all, parking) = withContext(Dispatchers.IO) {
                mutex.withLock { databases.getValue(activeMode).let { it.all() to it.lastParking() } }
            }
            if (token == epoch) { records = all; lastParking = parking }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e(TAG, "Reload failed", e)
            if (token == epoch) message = "기록을 불러오지 못했어요 · 잠시 후 다시 열어 주세요"
        }
    }

    // ---- reading aloud ----------------------------------------------------------------------------------------

    /** "▶ 인계 읽어주기 / ■ 읽기 중지". Allowed while driving: listening needs no screen. */
    fun toggleReading() {
        if (suspended) return
        if (reading) { stopReading(); return }
        // The driver choice is still pending: the handover of the previous profile must not be read out.
        if (askDriver) { message = "정차 후 이번 운전자를 먼저 골라 주세요"; return }
        val record = latestIncoming ?: run { message = "읽어드릴 받은 인계가 없어요"; return }
        cancelVoice()
        val token = ++readToken
        reading = true
        val script = SpeechScript.build(record, System.currentTimeMillis())
        voice.speak(mode, script,
            onDone = { if (token == readToken) reading = false },
            onError = { text -> if (token == readToken) { reading = false; message = text } })
    }

    fun stopReading() {
        readToken++
        if (reading) voice.stopSpeaking()
        reading = false
    }

    // ---- recording sheet --------------------------------------------------------------------------------------

    /** Starts the in-app recording sheet. Call only after RECORD_AUDIO was granted. A second tap is ignored. */
    fun startVoice(purpose: VoicePurpose) {
        if (!canInteract || suspended || voiceSheet != null) return
        stopReading()
        val activeMode = mode
        val token = ++voiceToken
        voiceSheet = VoiceSheet(purpose, VoicePhase.PREPARING, voice.inputEngineNameFor(activeMode))
        voiceStopPressed = false
        resetLevel()
        fun live() = token == voiceToken && voiceSheet != null
        voice.listen(activeMode, object : VoiceInput.Callback {
            override fun onListening() {
                // A late start after "말하기 끝" must not bring the sheet back to LISTENING. A new engine that took over
                // after the old countdown ran out may: the microphone is open again and "말하기 끝" must work.
                if (!live() || voiceStopPressed) return
                val deadline = voice.listeningDeadline ?: (SystemClock.uptimeMillis() + VoiceService.MAX_LISTEN_MS)
                voiceSheet = voiceSheet?.copy(phase = VoicePhase.LISTENING, engine = voice.inputEngineName, deadline = deadline)
                resetLevel()
                // The service stops on its own at the deadline: show "정리하고 있어요" then, not "0초 남음".
                autoStopJob?.cancel()
                autoStopJob = scope.launch {
                    delay((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                    // During an engine switch the service has no deadline: the sheet waits for the new one.
                    if (live() && voiceSheet?.phase == VoicePhase.LISTENING && voiceSheet?.deadline == deadline &&
                        voice.listeningDeadline == deadline) {
                        voiceSheet = voiceSheet?.copy(phase = VoicePhase.PROCESSING, deadline = null)
                        resetLevel()
                    }
                }
            }
            override fun onRestarting(engine: String) {
                if (!live()) return
                autoStopJob?.cancel()
                voiceSheet = voiceSheet?.copy(phase = VoicePhase.PREPARING, engine = engine, deadline = null)
                resetLevel()
            }
            override fun onPartial(text: String) { if (live()) voiceSheet = voiceSheet?.copy(caption = text) }
            override fun onLevel(level: Float) { if (live()) updateLevel(level) }
            override fun onFinal(text: String) {
                if (!live()) return
                val value = VoiceText.tidyTranscript(text)
                if (value.isEmpty()) {
                    voiceSheet = voiceSheet?.copy(phase = VoicePhase.NO_SPEECH, deadline = null,
                        error = "말씀이 들리지 않았어요 · 다시 말하거나 직접 입력해 주세요")
                    resetLevel()
                    return
                }
                voiceToken++
                voiceSheet = null
                resetLevel()
                applyVoiceText(purpose, value)
            }
            override fun onError(message: String, useFallback: Boolean) {
                if (!live()) return
                resetLevel()
                if (useFallback) {
                    // No engine left: manual input is always possible.
                    voiceToken++
                    voiceSheet = null
                    this@CarLogController.message = message
                    focusTarget = purpose
                } else {
                    voiceSheet = voiceSheet?.copy(phase = VoicePhase.FAILED, deadline = null, error = message)
                }
            }
        })
    }

    /** Fast attack, slow release; the observable state only moves in visible steps (fewer redraws). */
    private fun updateLevel(level: Float) {
        smoothedLevel = VoiceText.smoothLevel(smoothedLevel, level)
        val shown = if (smoothedLevel < LEVEL_STEP) 0f else smoothedLevel
        if (abs(shown - voiceLevel) >= LEVEL_STEP || (shown == 0f && voiceLevel != 0f)) voiceLevel = shown
    }

    private fun resetLevel() {
        smoothedLevel = 0f
        voiceLevel = 0f
    }

    /** "말하기 끝". */
    fun finishSpeaking() {
        val sheet = voiceSheet ?: return
        if (sheet.phase != VoicePhase.LISTENING && sheet.phase != VoicePhase.PREPARING) return
        voiceStopPressed = true
        autoStopJob?.cancel()
        voiceSheet = sheet.copy(phase = VoicePhase.PROCESSING, deadline = null)
        resetLevel()
        voice.stopListening()
    }

    /** "취소" (also back / outside tap). Nothing is delivered afterwards. */
    fun cancelVoice() {
        if (voiceSheet == null) return
        voiceToken++
        voiceSheet = null
        resetLevel()
        autoStopJob?.cancel()
        voice.cancelListening()
    }

    /** "직접 입력": keeps what was heard so far and focuses the text field. */
    fun voiceToManual() {
        val sheet = voiceSheet ?: return
        cancelVoice()
        val heard = sheet.caption.trim()
        if (heard.isNotEmpty()) {
            when (sheet.purpose) {
                VoicePurpose.MEMO -> if (editorText.isBlank()) updateText(heard)
                VoicePurpose.PARKING -> if (editorParking.isBlank()) updateParking(heard)
            }
        }
        focusTarget = sheet.purpose
    }

    /** "다시 말하기" after nothing was heard or a recoverable error. */
    fun retryVoice() {
        val sheet = voiceSheet ?: return
        cancelVoice()
        startVoice(sheet.purpose)
    }

    fun voicePermissionDenied(purpose: VoicePurpose) {
        message = "마이크 권한이 없어도 직접 입력으로 남길 수 있어요"
        focusTarget = purpose
    }

    private fun applyVoiceText(purpose: VoicePurpose, text: String) {
        when (purpose) {
            VoicePurpose.MEMO -> {
                // A second dictation starts a new sentence, so the read-aloud pauses between them.
                val (merged, truncated) = VoiceText.appendTranscript(editorText, text, HandoverRules.MAX_MEMO_LENGTH)
                editorText = merged
                editorFromVoice = true
                if (!categoryPicked) editorCategory = NoteClassifier.classify(editorText)
                voiceNotice = if (truncated) "${HandoverRules.MAX_MEMO_LENGTH}자까지만 담았어요 · 나머지는 직접 고쳐 주세요"
                    else "음성으로 받아 적었어요 · 저장 전에 고칠 수 있어요"
            }
            VoicePurpose.PARKING -> {
                updateParking(text)
                message = "주차 위치를 받아 적었어요 · 저장 전에 고칠 수 있어요"
            }
        }
    }

    companion object {
        private const val TAG = "CarLog"
        private const val KEY_DRIVER = "driver"
        private const val KEY_LAST_STOPPED = "last_stopped_at"
        private const val GATE_POLL_MS = 700L
        private const val REFRESH_WAIT_MS = 12_000L
        private const val REFRESHING = "차량 상태를 새로 읽고 있어요"
        /** Smallest level change worth a redraw of the meter. */
        private const val LEVEL_STEP = 0.02f
    }
}
