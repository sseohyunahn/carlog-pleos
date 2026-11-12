package com.carlog.app.platform.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import com.carlog.app.domain.DataMode
import com.carlog.app.domain.VoiceText
import com.carlog.app.platform.VoiceInput

/**
 * Voice facade for the controller. Call every method on the main thread; every callback arrives on the main thread.
 *
 * Listening
 *  - PLEOS mode tries Pleos SpeechToText first. When it reports onError(useFallback = true) (or never starts), the
 *    Android SpeechRecognizer is started automatically for the same recording ([VoiceInput.Callback.onRestarting],
 *    then onListening fires again — restart the countdown). A capability error (or [MAX_TRANSIENT_PLEOS_FAILURES]
 *    start-up timeouts) makes Android the engine for the rest of the app session; a single start-up timeout skips
 *    Pleos for that recording only. If Pleos fails after it already heard text, that text is delivered instead.
 *    When the device has no Android recognizer, Pleos is kept for the next press.
 *    Only when no engine is left is onError(message, useFallback = true) forwarded: open manual text input then.
 *  - While recording, transient audio focus is held so media pauses and does not bleed into the microphone.
 *  - DEMO mode uses the Android engines only (Pleos SDKs are never touched).
 *  - At most [MAX_LISTEN_MS] after onListening the recording is stopped automatically; after a stop the result is
 *    awaited at most [RESULT_WAIT_MS], then the last partial text (or a short error) is delivered.
 *  - Exactly one of onFinal / onError ends a recording; nothing arrives after [cancelListening] or a newer [listen].
 *  - The countdown is the UI's job: show [MAX_LISTEN_SECONDS] from onListening (or read [listeningDeadline]).
 *
 * Speaking
 *  - PLEOS mode reads with Pleos TextToSpeech (falls back to Android TTS for the same text on engine errors);
 *    DEMO mode uses Android TTS. onDone fires once when finished *or stopped*; onError carries short Korean text.
 *  - [listen] stops speaking and [speak] cancels listening, so CarLog never records its own voice.
 */
class VoiceService(context: Context) {
    companion object {
        private const val TAG = VoiceErrors.TAG_VOICE
        const val MAX_LISTEN_SECONDS = 10
        const val MAX_LISTEN_MS = MAX_LISTEN_SECONDS * 1_000L
        const val RESULT_WAIT_MS = 8_000L
        /**
         * Last-resort backstop if an engine neither starts nor fails. It must exceed the engines' own worst case
         * (Pleos: init timeout + stale-end wait + start timeout, run one after another), so an engine that is still
         * starting up reports its own timeout first and is never cut off (which would disable Pleos STT for the session).
         */
        const val START_BACKSTOP_MS =
            PleosVoiceInput.INIT_TIMEOUT_MS + PleosVoiceInput.STALE_WAIT_MS + PleosVoiceInput.START_TIMEOUT_MS + 1_500L
        /** Start-up timeouts in a row after which Pleos STT is given up for the app session. */
        const val MAX_TRANSIENT_PLEOS_FAILURES = 2
    }

    private val app = context.applicationContext

    private var pleosInput: PleosVoiceInput? = null
    private var androidInput: AndroidVoiceInput? = null
    /** Sticky for this app session: once Pleos STT proved unusable, PLEOS mode listens with Android. */
    private var pleosInputFailed = false
    /** Pleos start-up timeouts since Pleos last opened the microphone. */
    private var pleosTransientFailures = 0

    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    /** Transient focus while recording, so radio/music pauses. Focus changes never abort a recording. */
    private val recordFocus: AudioFocusRequest? = try {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener({ change -> Log.i(TAG, "record focus change $change") }, VoiceErrors.main)
            .build()
    } catch (e: Exception) {
        Log.w(TAG, "record focus unavailable", e)
        null
    }
    private var recordFocusHeld = false
    private var pleosSpeaker: PleosSpeaker? = null
    private var androidSpeaker: AndroidSpeaker? = null
    private var lastMode = DataMode.DEMO
    private var speakEpoch = 0

    private class Recording(val mode: DataMode, val callback: VoiceInput.Callback) {
        var engine: VoiceInput? = null
        /** Pleos failed to start for this recording: use Android for the rest of it. */
        var skipPleos = false
        var attempt = 0
        var listening = false
        var stopRequested = false
        var done = false
        var lastPartial = ""
        var deadline: Long? = null
        val timers = mutableListOf<Runnable>()
        var autoStop: Runnable? = null
    }

    private var recording: Recording? = null

    /** Engine that the current/last recording uses (or would use next), e.g. "Pleos 음성 인식". */
    val inputEngineName: String
        get() = recording?.engine?.name ?: inputEngineNameFor(lastMode)

    fun inputEngineNameFor(mode: DataMode): String =
        if (mode == DataMode.PLEOS && !pleosInputFailed) PleosVoiceInput.ENGINE_NAME else AndroidVoiceInput.ENGINE_NAME

    /** True from [listen] until onFinal/onError (or cancel). */
    val listening: Boolean get() = recording != null

    /** SystemClock.uptimeMillis() at which the running recording is auto-stopped; null before onListening. */
    val listeningDeadline: Long? get() = recording?.deadline

    /**
     * Creates the engines early so their initialisation is done before the first use: Android TTS always (it reads
     * in DEMO mode and is the PLEOS fallback), the Pleos engines in PLEOS mode.
     */
    fun prepare(mode: DataMode) {
        runCatching { sharedAndroidSpeaker() }.onFailure { Log.w(TAG, "Android TTS warm-up failed", it) }
        if (mode != DataMode.PLEOS) return
        if (!pleosInputFailed && pleosInput == null) pleosInput = PleosVoiceInput(app)
        if (pleosSpeaker == null) pleosSpeaker = PleosSpeaker(app) { sharedAndroidSpeaker() }
    }

    fun listen(mode: DataMode, callback: VoiceInput.Callback) {
        stopSpeaking()
        cancelListening()
        lastMode = mode
        val rec = Recording(mode, callback)
        recording = rec
        // Before the engine starts, so a vehicle voice service that takes focus itself comes after (and wins).
        requestRecordFocus()
        startEngine(rec)
    }

    /** "말하기 끝": stop recording; onFinal (last partial if needed) or onError follows within [RESULT_WAIT_MS]. */
    fun stopListening() {
        val rec = recording ?: return
        if (rec.done || rec.stopRequested) return
        rec.stopRequested = true
        VoiceErrors.cancel(rec.autoStop)
        rec.autoStop = null
        schedule(rec, RESULT_WAIT_MS) {
            if (recording !== rec || rec.done) return@schedule
            Log.w(TAG, "result wait expired on ${rec.engine?.name}")
            val engine = rec.engine
            finish(rec)
            engine?.cancel()
            deliverPartialOrError(rec, VoiceText.message(VoiceText.RESULT_TIMEOUT))
        }
        rec.engine?.stop()
    }

    /** Cancel without delivering anything. */
    fun cancelListening() {
        val rec = recording ?: return
        val engine = rec.engine
        finish(rec)
        engine?.cancel()
    }

    fun speak(mode: DataMode, text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        cancelListening()
        stopSpeaking()
        val token = speakEpoch
        val speaker = if (mode == DataMode.PLEOS) {
            pleosSpeaker ?: PleosSpeaker(app) { sharedAndroidSpeaker() }.also { pleosSpeaker = it }
        } else sharedAndroidSpeaker()
        speaker.speak(
            text,
            onDone = { if (token == speakEpoch) VoiceErrors.guard(TAG) { onDone() } },
            onError = { message -> if (token == speakEpoch) VoiceErrors.guard(TAG) { onError(message) } }
        )
    }

    /** Stops reading; the running speak() gets its onDone now, nothing of it arrives later. */
    fun stopSpeaking() {
        pleosSpeaker?.stop()
        androidSpeaker?.stop()
        speakEpoch++
    }

    val speaking: Boolean get() = pleosSpeaker?.speaking == true || androidSpeaker?.speaking == true

    /** Stops everything (mode switch, driving restriction, driver change, onStop). */
    fun stop() {
        cancelListening()
        stopSpeaking()
    }

    fun release() {
        stop()
        pleosInput?.release(); pleosInput = null
        androidInput?.release(); androidInput = null
        pleosSpeaker?.release(); pleosSpeaker = null
        androidSpeaker?.release(); androidSpeaker = null
    }

    // ---- internals -------------------------------------------------------------------------------------------

    private fun sharedAndroidSpeaker(): AndroidSpeaker = androidSpeaker ?: AndroidSpeaker(app).also { androidSpeaker = it }

    private fun pickInput(rec: Recording): VoiceInput =
        if (rec.mode == DataMode.PLEOS && !pleosInputFailed && !rec.skipPleos) {
            pleosInput ?: PleosVoiceInput(app).also { pleosInput = it }
        } else {
            sharedAndroidInput()
        }

    private fun sharedAndroidInput(): AndroidVoiceInput = androidInput ?: AndroidVoiceInput(app).also { androidInput = it }

    private fun startEngine(rec: Recording) {
        clearTimers(rec)
        val engine = pickInput(rec)
        rec.engine = engine
        rec.listening = false
        rec.deadline = null
        val attempt = ++rec.attempt
        schedule(rec, START_BACKSTOP_MS) {
            if (!live(rec, attempt) || rec.listening) return@schedule
            Log.w(TAG, "${engine.name} did not start")
            engine.cancel()
            onEngineError(rec, attempt, engine, VoiceText.message(VoiceText.START_TIMEOUT), true, VoiceText.START_TIMEOUT)
        }
        Log.i(TAG, "listening with ${engine.name}")
        engine.start(object : VoiceInput.Callback {
            override fun onListening() {
                if (!live(rec, attempt)) return
                rec.listening = true
                if (engine is PleosVoiceInput) pleosTransientFailures = 0
                VoiceErrors.cancel(rec.autoStop)
                // A late start after "말하기 끝" is not forwarded: the UI already shows the result wait.
                if (rec.stopRequested) return
                rec.deadline = android.os.SystemClock.uptimeMillis() + MAX_LISTEN_MS
                rec.autoStop = schedule(rec, MAX_LISTEN_MS) { if (live(rec, attempt)) stopListening() }
                VoiceErrors.guard(TAG) { rec.callback.onListening() }
            }

            override fun onPartial(text: String) {
                if (!live(rec, attempt)) return
                val value = text.trim()
                if (value.isEmpty()) return
                rec.lastPartial = value
                VoiceErrors.guard(TAG) { rec.callback.onPartial(value) }
            }

            override fun onLevel(level: Float) {
                if (!live(rec, attempt)) return
                VoiceErrors.guard(TAG) { rec.callback.onLevel(if (level.isFinite()) level.coerceIn(0f, 1f) else 0f) }
            }

            override fun onFinal(text: String) {
                if (!live(rec, attempt)) return
                finish(rec)
                val value = text.trim().ifEmpty { rec.lastPartial }
                VoiceErrors.guard(TAG) { rec.callback.onFinal(value) }
            }

            override fun onError(message: String, useFallback: Boolean) {
                if (!live(rec, attempt)) return
                onEngineError(rec, attempt, engine, message, useFallback, (engine as? PleosVoiceInput)?.lastErrorName)
            }
        })
    }

    private fun onEngineError(
        rec: Recording, attempt: Int, engine: VoiceInput, message: String, useFallback: Boolean, reason: String?
    ) {
        if (!live(rec, attempt)) return
        if (useFallback && engine is PleosVoiceInput) {
            if (!sharedAndroidInput().isAvailable()) {
                // Nothing to fall back to: keep Pleos for the next press; the UI opens manual input now.
                Log.w(TAG, "Pleos STT failed ($reason) and there is no Android recognizer")
                finish(rec)
                if (rec.lastPartial.isNotEmpty()) VoiceErrors.guard(TAG) { rec.callback.onFinal(rec.lastPartial) }
                else VoiceErrors.guard(TAG) { rec.callback.onError(message, true) }
                return
            }
            val transient = VoiceText.isTransientStartFailure(reason)
            if (transient) pleosTransientFailures++
            if (!transient || pleosTransientFailures >= MAX_TRANSIENT_PLEOS_FAILURES) {
                pleosInputFailed = true
                Log.w(TAG, "Pleos STT unusable for this session ($reason); switching to Android recognizer")
            } else {
                Log.w(TAG, "Pleos STT did not start ($reason); Android recognizer for this recording")
            }
            rec.skipPleos = true
            if (rec.stopRequested) {
                // The driver already said "말하기 끝": do not open the microphone again.
                finish(rec)
                deliverPartialOrError(rec, VoiceText.message(VoiceText.RESULT_TIMEOUT))
            } else if (rec.listening && rec.lastPartial.isNotEmpty()) {
                // Pleos already heard something: keep it rather than starting a new 10 s window from scratch.
                finish(rec)
                VoiceErrors.guard(TAG) { rec.callback.onFinal(rec.lastPartial) }
            } else {
                VoiceErrors.guard(TAG) { rec.callback.onRestarting(AndroidVoiceInput.ENGINE_NAME) }
                if (live(rec, attempt)) startEngine(rec)
            }
            return
        }
        finish(rec)
        if (useFallback) {
            // No engine left: the UI opens manual text input.
            VoiceErrors.guard(TAG) { rec.callback.onError(message, true) }
        } else {
            deliverPartialOrError(rec, message)
        }
    }

    private fun deliverPartialOrError(rec: Recording, message: String) {
        if (rec.lastPartial.isNotEmpty()) VoiceErrors.guard(TAG) { rec.callback.onFinal(rec.lastPartial) }
        else VoiceErrors.guard(TAG) { rec.callback.onError(message, false) }
    }

    private fun live(rec: Recording, attempt: Int) = recording === rec && !rec.done && rec.attempt == attempt

    private fun finish(rec: Recording) {
        rec.done = true
        rec.deadline = null
        clearTimers(rec)
        if (recording === rec) {
            recording = null
            abandonRecordFocus()
        }
    }

    /** Pauses other media while recording. A denial is not fatal: the recording runs anyway. */
    private fun requestRecordFocus() {
        if (recordFocusHeld) return
        val am = audio ?: return
        val request = recordFocus ?: return
        try {
            recordFocusHeld = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!recordFocusHeld) Log.i(TAG, "record focus not granted")
        } catch (e: Exception) {
            Log.w(TAG, "record focus request failed", e)
        }
    }

    private fun abandonRecordFocus() {
        if (!recordFocusHeld) return
        recordFocusHeld = false
        try { recordFocus?.let { audio?.abandonAudioFocusRequest(it) } } catch (e: Exception) {
            Log.w(TAG, "record focus release failed", e)
        }
    }

    private fun schedule(rec: Recording, delayMs: Long, block: () -> Unit): Runnable {
        lateinit var runnable: Runnable
        runnable = VoiceErrors.postDelayed(TAG, delayMs) { rec.timers.remove(runnable); block() }
        rec.timers += runnable
        return runnable
    }

    private fun clearTimers(rec: Recording) {
        rec.timers.forEach { VoiceErrors.cancel(it) }
        rec.timers.clear()
        rec.autoStop = null
    }
}
