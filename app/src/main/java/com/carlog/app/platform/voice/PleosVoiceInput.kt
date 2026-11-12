package com.carlog.app.platform.voice

import android.content.Context
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import com.carlog.app.domain.LinearLevelMeter
import com.carlog.app.domain.VoiceText
import com.carlog.app.platform.VoiceInput
import java.util.Locale

/**
 * Pleos SpeechToText 3.3.1 in ON_DEVICE mode (vehicle microphone, no network, no app registration), ko-KR.
 *
 * Ported from v1 PleosVoiceInput.java including the cross-review fixes:
 *  - SDK events carry no request id. A request that is cancelled/superseded while the engine is still busy owes one
 *    end/error event; those are swallowed ([staleEnds]) so they never reach the next recording.
 *  - The SDK has no cancel: stop() finalises, so an aborted request that had heard speech may also still send its
 *    completed result. That result is owed separately ([staleResults]); events that arrive before this session's
 *    request() was issued ([requestIssued]) can never belong to it and are dropped.
 *  - SDK event meaning (from the 3.3.1 bytecode): onReady = ReadyToStartRecognition (microphone open),
 *    onStartedRecognition = EPD_ON (speech onset), onEndedRecognition = EPD_OFF (end of speech, NOT end of the
 *    request), onUpdated(text, true) = UpdateSttCompleted (final text).
 *  - Neither microphone-ready (onReady / RMS) nor speech within [START_TIMEOUT_MS] -> onError(useFallback = true).
 *    Silence after the microphone opened is not an engine failure: it ends through the 10 s cap or the engine's own
 *    TIMEOUT (no speech), never through the fallback.
 *  - Start-up timeouts are transient: they skip Pleos for this recording only ([VoiceText.isTransientStartFailure]).
 *  - stop() while the engine is still initialising/starting ends right away with the last partial (never stuck);
 *    stop() after end of speech waits for the completed result that is on its way.
 *  - A completed result that is empty, or an end without a completed result, delivers the last partial text.
 * Every SDK callback is posted to the main thread and guarded by the [session] epoch; nothing is thrown back.
 */
internal class PleosVoiceInput(context: Context) : VoiceInput {
    companion object {
        private const val TAG = VoiceErrors.TAG_VOICE
        const val ENGINE_NAME = "Pleos 음성 인식"
        const val INIT_TIMEOUT_MS = 6_000L
        const val START_TIMEOUT_MS = 6_000L
        /** After onEndedRecognition, wait this long for a completed result before using the last partial. */
        const val FINAL_GRACE_MS = 2_000L
        /** After stop(), wait this long for the engine's result before using the last partial. */
        const val STOP_RESULT_WAIT_MS = 3_000L
        /** How long a new request waits for the cancelled request's end event before starting anyway. */
        const val STALE_WAIT_MS = 1_500L
    }

    override val name: String = ENGINE_NAME

    private var stt: SpeechToText? = null
    private var ready = false
    private var unusable = false
    private var unusableName: String = VoiceText.ENGINE_MISSING
    private var listenerAdded = false

    private var callback: VoiceInput.Callback? = null
    private var session = 0
    private var pendingStart = false
    private var delivered = true
    private var heard = false
    /** onListening was forwarded for this session (from onStartedRecognition or the first partial/level). */
    private var listeningSignalled = false
    private var stopRequested = false
    private var lastPartial = ""
    private var meter = LinearLevelMeter()
    /** The microphone of this session's request is open (onReady, RMS or speech seen). */
    private var micOpen = false
    /** This session's engine.request() was issued; earlier events belong to an older request. */
    private var requestIssued = false
    /** End of speech (EPD_OFF) was seen and the completed result is still awaited. */
    private var awaitingFinal = false
    /** The current request heard speech and has not sent its completed result yet. */
    private var resultOwed = false

    /** Raw name of the last error this engine reported (read by [VoiceService] to decide on the fallback). */
    var lastErrorName: String? = null
        private set

    /** True from request() until the engine reports end/error for that request. */
    private var engineBusy = false
    private var staleEnds = 0
    /** Completed results still owed by aborted requests (SpeechToText.stop() finalises instead of cancelling). */
    private var staleResults = 0
    private var staleGeneration = 0
    private val timers = mutableListOf<Runnable>()

    private val listener = object : ResultListener {
        /** ReadyToStartRecognition: the microphone is open. */
        override fun onReady() {
            VoiceErrors.post(TAG) {
                if (staleEnds > 0 || delivered || !engineBusy || !requestIssued) return@post
                micOpen = true
                signalListening()
            }
        }

        /** EPD_ON: speech onset. */
        override fun onStartedRecognition() {
            VoiceErrors.post(TAG) {
                if (staleEnds > 0 || delivered || !requestIssued) return@post
                heard = true
                micOpen = true
                resultOwed = true
                signalListening()
            }
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            VoiceErrors.post(TAG) {
                if (completed && staleResults > 0) { staleResults--; Log.i(TAG, "STT stale result dropped"); return@post }
                if (staleEnds > 0 || delivered || !requestIssued) return@post
                val value = stt.trim()
                // An empty completed result before this request heard anything (and without a stop) is most likely
                // the finalised result of an aborted silent request (stop() finalises, no request id). Ending the new
                // recording with it would show "no speech" at once; silence still ends through TIMEOUT or the 10 s cap.
                if (completed && value.isEmpty() && !heard && !stopRequested) {
                    Log.i(TAG, "STT empty result before speech ignored")
                    return@post
                }
                heard = true
                // onStartedRecognition depends on a separate EPD event: live text also means the session started.
                if (!completed && value.isNotEmpty()) { micOpen = true; resultOwed = true; signalListening() }
                if (completed) {
                    resultOwed = false
                    deliverFinal(value.ifEmpty { lastPartial })
                } else if (value.isNotEmpty()) {
                    lastPartial = value
                    callback?.let { cb -> VoiceErrors.guard(TAG) { cb.onPartial(value) } }
                }
            }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit

        override fun onUpdatedRms(rms: Double) {
            VoiceErrors.post(TAG) {
                if (staleEnds > 0 || delivered || !requestIssued) return@post
                // RMS from a request in progress means the microphone is open.
                if (engineBusy) { micOpen = true; signalListening() }
                val level = meter.next(rms)
                callback?.let { cb -> VoiceErrors.guard(TAG) { cb.onLevel(level) } }
            }
        }

        /** EPD_OFF: end of speech. The completed result (UpdateSttCompleted) arrives separately. */
        override fun onEndedRecognition() {
            VoiceErrors.post(TAG) {
                if (staleEnds > 0) { staleEnds--; return@post }
                if (!requestIssued) return@post
                engineBusy = false
                if (delivered) return@post
                // An end before this request ever started is a leftover of an earlier request; the start
                // timeout covers a request that really ends without starting.
                if (!heard && !stopRequested) { Log.i(TAG, "STT end before start ignored"); return@post }
                awaitingFinal = true
                val s = session
                schedule(FINAL_GRACE_MS) { if (s == session && !delivered) deliverFinal(lastPartial) }
            }
        }

        override fun onError(errorType: ErrorType) {
            VoiceErrors.post(TAG) {
                val errorName = errorType.name
                if (staleEnds > 0) { staleEnds--; Log.i(TAG, "STT stale error $errorName"); return@post }
                if (staleResults > 0) { staleResults--; Log.i(TAG, "STT stale error $errorName"); return@post }
                if (!requestIssued) { Log.i(TAG, "STT error before request $errorName"); return@post }
                engineBusy = false
                resultOwed = false
                if (delivered) { Log.i(TAG, "STT late error $errorName"); return@post }
                if (VoiceText.isNoSpeech(errorName) && lastPartial.isNotEmpty()) {
                    Log.i(TAG, "STT $errorName with partial text")
                    deliverFinal(lastPartial)
                } else {
                    fail(errorName, VoiceText.isEngineUnusable(errorName))
                }
            }
        }
    }

    init {
        try {
            val engine = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
            stt = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() { VoiceErrors.post(TAG) { onInitialized() } }
                override fun onFailure(errorType: ErrorType) {
                    val errorName = errorType.name
                    VoiceErrors.post(TAG) { markUnusable(errorName) }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "STT unavailable", e)
            stt = null
            unusable = true
        } catch (e: LinkageError) {
            Log.w(TAG, "STT classes unavailable", e)
            stt = null
            unusable = true
        }
    }

    private fun onInitialized() {
        val engine = stt ?: return
        try {
            if (!listenerAdded) { engine.addListener(listener); listenerAdded = true }
            engine.setLocale(Locale.KOREA) // ko-KR (Locale.KOREAN would be just "ko")
            ready = true
            // A slow service bind that timed out earlier is not a lasting failure.
            if (unusable && unusableName == VoiceText.INIT_TIMEOUT) unusable = false
            if (pendingStart) { pendingStart = false; begin(session, 0L) }
        } catch (e: Exception) {
            Log.w(TAG, "STT setup failed", e)
            markUnusable(VoiceText.ENGINE_MISSING)
        }
    }

    private fun markUnusable(errorName: String) {
        Log.w(TAG, "STT initialize failed $errorName")
        unusable = true
        ready = false
        unusableName = errorName
        if (pendingStart) { pendingStart = false; fail(errorName, true) }
    }

    override fun start(callback: VoiceInput.Callback) {
        cancel()
        this.callback = callback
        delivered = false
        heard = false
        listeningSignalled = false
        stopRequested = false
        lastPartial = ""
        meter = LinearLevelMeter()
        micOpen = false
        requestIssued = false
        awaitingFinal = false
        resultOwed = false
        lastErrorName = null
        val s = session
        if (unusable || stt == null) {
            val reason = unusableName
            schedule(0L) { if (s == session) fail(reason, true) }
            return
        }
        if (!ready) {
            pendingStart = true
            schedule(INIT_TIMEOUT_MS) {
                if (s == session && pendingStart) { pendingStart = false; fail(VoiceText.INIT_TIMEOUT, true) }
            }
            return
        }
        begin(s, 0L)
    }

    private fun begin(s: Int, waitedMs: Long) {
        if (s != session || delivered) return
        if ((staleEnds > 0 || staleResults > 0) && waitedMs < STALE_WAIT_MS) {
            // Let the cancelled request report its end (and owed result) before starting a new one.
            schedule(100L) { begin(s, waitedMs + 100L) }
            return
        }
        staleEnds = 0
        staleResults = 0
        staleGeneration++
        if (stopRequested) { deliverFinal(lastPartial); return }
        val engine = stt
        if (engine == null) { fail(VoiceText.ENGINE_MISSING, true); return }
        try {
            engineBusy = true
            requestIssued = true
            awaitingFinal = false
            resultOwed = false
            engine.request()
        } catch (e: Exception) {
            Log.w(TAG, "STT request failed", e)
            engineBusy = false
            fail(VoiceText.START_FAILED, true)
            return
        }
        // Only an engine that never opened the microphone has failed to start; silence is handled elsewhere.
        schedule(START_TIMEOUT_MS) {
            if (s == session && !delivered && !heard && !micOpen) fail(VoiceText.START_TIMEOUT, true)
        }
    }

    override fun stop() {
        if (delivered) return
        stopRequested = true
        // Speech already ended: the completed result (or the grace timer) is on its way and ends the session.
        if (awaitingFinal) return
        if (pendingStart || !ready || stt == null || !engineBusy) {
            // Not recording yet (engine initialising or waiting for an old request): end now with what we have.
            pendingStart = false
            deliverFinal(lastPartial)
            return
        }
        try { stt?.stop() } catch (e: Exception) { Log.w(TAG, "STT stop failed", e) }
        val s = session
        schedule(STOP_RESULT_WAIT_MS) { if (s == session && !delivered) deliverFinal(lastPartial) }
    }

    override fun cancel() {
        session++
        pendingStart = false
        callback = null
        delivered = true
        clearTimers()
        abortEngine()
    }

    override fun release() {
        cancel()
        val engine = stt ?: return
        stt = null
        ready = false
        unusable = true
        unusableName = VoiceText.ENGINE_MISSING
        try {
            if (listenerAdded) engine.removeListener(listener)
            engine.release()
        } catch (e: Exception) {
            Log.w(TAG, "STT release failed", e)
        }
        listenerAdded = false
    }

    /**
     * Stops a request the engine is still working on; its end/error event is then owed and swallowed. A request that
     * heard speech may also still send its completed result (stop() finalises): that is owed separately.
     */
    private fun abortEngine() {
        val owesEnd = engineBusy
        val owesResult = requestIssued && resultOwed
        resultOwed = false
        awaitingFinal = false
        if (!owesEnd && !owesResult) return
        if (owesEnd) { engineBusy = false; staleEnds++ }
        if (owesResult) staleResults = 1
        val generation = ++staleGeneration
        // If the engine never reports the end/result of the aborted request, stop waiting for it.
        VoiceErrors.postDelayed(TAG, STALE_WAIT_MS * 2) {
            if (generation == staleGeneration) { staleEnds = 0; staleResults = 0 }
        }
        if (owesEnd) try { stt?.stop() } catch (e: Exception) { Log.w(TAG, "STT abort failed", e) }
    }

    /** Forwards onListening at most once per session, so a late EPD event never restarts the countdown. */
    private fun signalListening() {
        if (listeningSignalled) return
        listeningSignalled = true
        callback?.let { cb -> VoiceErrors.guard(TAG) { cb.onListening() } }
    }

    private fun deliverFinal(text: String) {
        if (delivered) return
        delivered = true
        pendingStart = false
        awaitingFinal = false
        clearTimers()
        val done = callback
        callback = null
        if (done != null) VoiceErrors.guard(TAG) { done.onFinal(text) }
    }

    private fun fail(errorName: String, useFallback: Boolean) {
        if (delivered) return
        delivered = true
        pendingStart = false
        clearTimers()
        lastErrorName = errorName
        // Start-up timeouts may clear on the next press; only capability/engine errors disable Pleos STT.
        if (useFallback && !VoiceText.isTransientStartFailure(errorName)) { unusable = true; unusableName = errorName }
        abortEngine()
        val message = VoiceErrors.report(TAG, "STT error", errorName)
        val failed = callback
        callback = null
        if (failed != null) VoiceErrors.guard(TAG) { failed.onError(message, useFallback) }
    }

    private fun schedule(delayMs: Long, block: () -> Unit) {
        lateinit var runnable: Runnable
        runnable = VoiceErrors.postDelayed(TAG, delayMs) { timers.remove(runnable); block() }
        timers += runnable
    }

    private fun clearTimers() {
        timers.forEach { VoiceErrors.cancel(it) }
        timers.clear()
    }
}
