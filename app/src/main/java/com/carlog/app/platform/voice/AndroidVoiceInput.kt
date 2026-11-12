package com.carlog.app.platform.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.carlog.app.domain.VoiceText
import com.carlog.app.platform.VoiceInput

/**
 * Android SpeechRecognizer used in-app (no external activity), ko-KR with partial results and an RMS level meter.
 * One recognizer per recording; every listener is bound to its [session] token so late callbacks are ignored.
 * Needs RECORD_AUDIO granted at runtime (otherwise onError(useFallback = true)).
 */
internal class AndroidVoiceInput(context: Context) : VoiceInput {
    companion object {
        private const val TAG = VoiceErrors.TAG_VOICE
        const val ENGINE_NAME = "Android 음성 인식"
        const val START_TIMEOUT_MS = 6_000L
        const val STOP_RESULT_WAIT_MS = 3_000L
    }

    private val app = context.applicationContext
    override val name: String = ENGINE_NAME

    private var recognizer: SpeechRecognizer? = null
    private var callback: VoiceInput.Callback? = null
    private var session = 0
    private var delivered = true
    private var heard = false
    private var lastPartial = ""
    private val timers = mutableListOf<Runnable>()

    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(app)
    } catch (e: Exception) {
        Log.w(TAG, "recognizer check failed", e)
        false
    }

    override fun start(callback: VoiceInput.Callback) {
        cancel()
        this.callback = callback
        delivered = false
        heard = false
        lastPartial = ""
        val s = session
        if (!isAvailable()) {
            schedule(0L) { if (s == session) fail(VoiceText.NO_RECOGNIZER, true) }
            return
        }
        val engine = try {
            SpeechRecognizer.createSpeechRecognizer(app)
        } catch (e: Exception) {
            Log.w(TAG, "recognizer create failed", e)
            null
        }
        if (engine == null) {
            schedule(0L) { if (s == session) fail(VoiceText.NO_RECOGNIZER, true) }
            return
        }
        recognizer = engine
        try {
            engine.setRecognitionListener(listenerFor(s))
            engine.startListening(recognizerIntent())
        } catch (e: Exception) {
            Log.w(TAG, "recognizer start failed", e)
            schedule(0L) { if (s == session) fail(VoiceText.START_FAILED, true) }
            return
        }
        schedule(START_TIMEOUT_MS) {
            if (s == session && !delivered && !heard) fail(VoiceText.START_TIMEOUT, true)
        }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
    }

    private fun listenerFor(s: Int) = object : RecognitionListener {
        private fun live() = s == session && !delivered

        override fun onReadyForSpeech(params: Bundle?) = VoiceErrors.guard(TAG) {
            if (!live()) return@guard
            heard = true
            callback?.onListening()
        }

        override fun onBeginningOfSpeech() = VoiceErrors.guard(TAG) { if (live()) heard = true }

        override fun onRmsChanged(rmsdB: Float) = VoiceErrors.guard(TAG) {
            if (live()) callback?.onLevel(VoiceText.normaliseDb(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) = VoiceErrors.guard(TAG) {
            if (!live()) return@guard
            val text = firstText(partialResults)
            if (text.isNotEmpty()) {
                heard = true
                lastPartial = text
                callback?.onPartial(text)
            }
        }

        override fun onResults(results: Bundle?) = VoiceErrors.guard(TAG) {
            if (!live()) return@guard
            deliverFinal(firstText(results).ifEmpty { lastPartial })
        }

        override fun onError(error: Int) = VoiceErrors.guard(TAG) {
            val errorName = VoiceText.androidErrorName(error)
            if (!live()) { Log.i(TAG, "late recognizer error $errorName"); return@guard }
            if (VoiceText.isNoSpeech(errorName) && lastPartial.isNotEmpty()) deliverFinal(lastPartial)
            else fail(errorName, VoiceText.isEngineUnusable(errorName))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstText(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { !it.isNullOrBlank() }?.trim() ?: ""

    override fun stop() {
        if (delivered) return
        val engine = recognizer
        if (engine == null || !heard) {
            // Recognizer not listening yet: end now with what we have instead of waiting.
            deliverFinal(lastPartial)
            return
        }
        try { engine.stopListening() } catch (e: Exception) { Log.w(TAG, "recognizer stop failed", e) }
        val s = session
        schedule(STOP_RESULT_WAIT_MS) { if (s == session && !delivered) deliverFinal(lastPartial) }
    }

    override fun cancel() {
        session++
        delivered = true
        callback = null
        clearTimers()
        destroyRecognizer(now = true)
    }

    override fun release() = cancel()

    private fun deliverFinal(text: String) {
        if (delivered) return
        delivered = true
        clearTimers()
        destroyRecognizer()
        val done = callback
        callback = null
        if (done != null) VoiceErrors.guard(TAG) { done.onFinal(text) }
    }

    private fun fail(errorName: String, useFallback: Boolean) {
        if (delivered) return
        delivered = true
        clearTimers()
        destroyRecognizer()
        val message = VoiceErrors.report(TAG, "recognizer error", errorName)
        val failed = callback
        callback = null
        if (failed != null) VoiceErrors.guard(TAG) { failed.onError(message, useFallback) }
    }

    /**
     * Frees the microphone. After a result/error it is destroyed on a later main-loop turn so we never destroy the
     * recognizer inside its own callback; cancel() destroys at once so a new recording never overlaps the old one.
     */
    private fun destroyRecognizer(now: Boolean = false) {
        val engine = recognizer ?: return
        recognizer = null
        val destroy: () -> Unit = {
            try { engine.cancel() } catch (e: Exception) { Log.w(TAG, "recognizer cancel failed", e) }
            try { engine.destroy() } catch (e: Exception) { Log.w(TAG, "recognizer destroy failed", e) }
        }
        if (now) destroy() else VoiceErrors.post(TAG, destroy)
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
