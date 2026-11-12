package com.carlog.app.platform.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import com.carlog.app.domain.VoiceText
import com.carlog.app.platform.Speaker
import java.util.Locale

/**
 * Pleos TextToSpeech 3.3.1, ON_DEVICE, ko-KR. If the vehicle engine fails (init failure/timeout, speak() throwing,
 * an engine error, or no onStart within [START_TIMEOUT_MS]) the *same text* is handed to the shared [AndroidSpeaker]
 * so reading still works. Engine-level failures make the Android path sticky for this app session.
 *
 * Pleos events carry no utterance id. A stopped utterance owes one terminal event ([staleTerminal]); terminal events
 * that arrive before the new utterance's onStart and within [EARLY_TERMINAL_MS] of speak() are treated as leftovers.
 * So a late onDone/onStop after stop() never ends the next utterance (v1 cross-review fix).
 */
internal class PleosSpeaker(context: Context, private val fallbackProvider: () -> AndroidSpeaker) : Speaker {
    companion object {
        private const val TAG = VoiceErrors.TAG_SPEECH
        const val ENGINE_NAME = "Pleos 음성 읽기"
        const val INIT_TIMEOUT_MS = 5_000L
        const val START_TIMEOUT_MS = 6_000L
        const val EARLY_TERMINAL_MS = 1_500L
        const val STALE_RESET_MS = 4_000L
        /** Safety net if the engine never reports the end: generous per-character reading time. */
        const val MAX_SPEAK_BASE_MS = 15_000L
        const val MAX_SPEAK_PER_CHAR_MS = 250L
    }

    private class Request(val text: String, val onDone: () -> Unit, val onError: (String) -> Unit) {
        var closed = false
        var waitingInit = false
        var sent = false
        var sentAt = 0L
        var started = false
        var earlyTerminal = false
        var onFallback = false
    }

    override val name: String get() = if (unusable) AndroidSpeaker.ENGINE_NAME else ENGINE_NAME

    private var tts: TextToSpeech? = null
    private var ready = false
    private var unusable = false
    private var listenerAdded = false
    private var current: Request? = null
    private var staleTerminal = 0
    private var staleGeneration = 0
    private var fallback: AndroidSpeaker? = null

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() {
            VoiceErrors.post(TAG) {
                val request = current ?: return@post
                if (request.sent && !request.onFallback) request.started = true
            }
        }
        override fun onDone() { VoiceErrors.post(TAG) { terminal("done") } }
        override fun onStop() { VoiceErrors.post(TAG) { terminal("stop") } }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: ErrorType) {
            val errorName = errorType.name
            VoiceErrors.post(TAG) {
                if (staleTerminal > 0) { staleTerminal--; Log.i(TAG, "TTS stale error $errorName"); return@post }
                val request = current
                if (request == null || request.closed || request.onFallback || !request.sent) {
                    Log.i(TAG, "TTS late error $errorName")
                    return@post
                }
                Log.w(TAG, "TTS error $errorName -> Android TTS")
                if (VoiceText.isEngineUnusable(errorName)) unusable = true
                // The engine already gave up on this utterance, so no stop() and no owed terminal event.
                handToFallback(request, abortEngine = false)
            }
        }
    }

    init {
        try {
            val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() { VoiceErrors.post(TAG) { onInitialized() } }
                override fun onFailure(errorType: ErrorType) {
                    val errorName = errorType.name
                    VoiceErrors.post(TAG) {
                        Log.w(TAG, "TTS initialize failed $errorName")
                        becomeUnusable()
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "TTS unavailable", e)
            tts = null
            unusable = true
        } catch (e: LinkageError) {
            Log.w(TAG, "TTS classes unavailable", e)
            tts = null
            unusable = true
        }
    }

    private fun onInitialized() {
        val engine = tts ?: return
        try {
            if (!listenerAdded) { engine.addEventListener(events); listenerAdded = true }
            engine.setLocale(Locale.KOREA)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ready = true
            val request = current
            if (request != null && request.waitingInit && !request.closed) speakNow(request)
        } catch (e: Exception) {
            Log.w(TAG, "TTS setup failed", e)
            becomeUnusable()
        }
    }

    private fun becomeUnusable() {
        unusable = true
        ready = false
        val request = current
        if (request != null && request.waitingInit && !request.closed) handToFallback(request)
    }

    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        stop()
        val request = Request(text, onDone, onError)
        current = request
        if (unusable || tts == null) { handToFallback(request); return }
        if (!ready) {
            request.waitingInit = true
            VoiceErrors.postDelayed(TAG, INIT_TIMEOUT_MS) {
                if (current === request && request.waitingInit && !request.closed) {
                    Log.w(TAG, "TTS init timeout -> Android TTS")
                    unusable = true
                    handToFallback(request)
                }
            }
            return
        }
        speakNow(request)
    }

    private fun speakNow(request: Request) {
        request.waitingInit = false
        val engine = tts
        if (engine == null) { unusable = true; handToFallback(request); return }
        try {
            engine.speak(request.text, true)
            request.sent = true
            request.sentAt = SystemClock.uptimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed -> Android TTS", e)
            unusable = true
            handToFallback(request)
            return
        }
        VoiceErrors.postDelayed(TAG, START_TIMEOUT_MS) {
            if (current !== request || request.closed || request.onFallback || request.started) return@postDelayed
            if (request.earlyTerminal) {
                // The engine finished without ever reporting onStart: accept that terminal event now.
                close(request, null)
            } else {
                Log.w(TAG, "TTS start timeout -> Android TTS")
                unusable = true
                handToFallback(request, abortEngine = true)
            }
        }
        VoiceErrors.postDelayed(TAG, MAX_SPEAK_BASE_MS + MAX_SPEAK_PER_CHAR_MS * request.text.length) {
            if (current !== request || request.closed || request.onFallback) return@postDelayed
            Log.w(TAG, "TTS end never reported; finishing")
            abortPleos()
            close(request, null)
        }
    }

    private fun terminal(kind: String) {
        if (staleTerminal > 0) { staleTerminal--; Log.i(TAG, "TTS stale $kind"); return }
        val request = current ?: return
        if (request.closed || request.onFallback || !request.sent) return
        if (!request.started && SystemClock.uptimeMillis() - request.sentAt < EARLY_TERMINAL_MS) {
            // Most likely a leftover of a stopped utterance; remember it in case this request never starts.
            request.earlyTerminal = true
            Log.i(TAG, "TTS early $kind ignored")
            return
        }
        close(request, null)
    }

    /** Hands [request]'s text to Android TTS; its outcome becomes this request's outcome. */
    private fun handToFallback(request: Request, abortEngine: Boolean = false) {
        if (request.closed || request.onFallback) return
        if (abortEngine && request.sent) abortPleos()
        request.onFallback = true
        request.waitingInit = false
        val speaker = try {
            fallback ?: fallbackProvider().also { fallback = it }
        } catch (e: Exception) {
            Log.w(TAG, "Android TTS create failed", e)
            null
        }
        if (speaker == null) { close(request, VoiceText.TTS_START_FAILED); return }
        speaker.speak(
            request.text,
            onDone = { close(request, null) },
            onError = { message -> closeWithMessage(request, message) }
        )
    }

    /** Stops Pleos playback of the current request; the terminal event it owes is swallowed. */
    private fun abortPleos() {
        staleTerminal++
        val generation = ++staleGeneration
        // Do not wait forever for a terminal event the engine may never send.
        VoiceErrors.postDelayed(TAG, STALE_RESET_MS) { if (generation == staleGeneration) staleTerminal = 0 }
        try { tts?.stop() } catch (e: Exception) { Log.w(TAG, "TTS stop failed", e) }
    }

    private fun close(request: Request, errorName: String?) {
        if (errorName == null) closeWithMessage(request, null)
        else closeWithMessage(request, VoiceErrors.report(TAG, "TTS", errorName))
    }

    private fun closeWithMessage(request: Request, message: String?) {
        if (request.closed) return
        request.closed = true
        if (current === request) current = null
        if (message == null) VoiceErrors.guard(TAG) { request.onDone() }
        else VoiceErrors.guard(TAG) { request.onError(message) }
    }

    override fun stop() {
        val request = current ?: return
        current = null
        if (request.onFallback) {
            fallback?.stop() // fires the fallback's onDone -> close(request, null)
        } else if (request.sent && !request.closed) {
            abortPleos()
        }
        close(request, null)
    }

    override val speaking: Boolean get() = current != null

    override fun release() {
        stop()
        val engine = tts ?: return
        tts = null
        ready = false
        unusable = true
        try {
            if (listenerAdded) engine.removeEventListener(events)
            engine.release()
        } catch (e: Exception) {
            Log.w(TAG, "TTS release failed", e)
        }
        listenerAdded = false
        // The AndroidSpeaker belongs to VoiceService, which releases it.
    }
}
