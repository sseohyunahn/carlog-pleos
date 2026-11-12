package com.carlog.app.platform.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.carlog.app.domain.VoiceText
import com.carlog.app.platform.Speaker
import java.util.Locale

/**
 * Android platform TTS (ko-KR) with a readiness state, navigation-guidance speech attributes and transient
 * may-duck audio focus. Each utterance has its own id; a late onDone/onStop of a stopped utterance never ends
 * the next one. onDone or onError is delivered exactly once per speak().
 */
internal class AndroidSpeaker(context: Context) : Speaker {
    companion object {
        private const val TAG = VoiceErrors.TAG_SPEECH
        const val ENGINE_NAME = "Android 음성 읽기"
        const val INIT_TIMEOUT_MS = 5_000L
    }

    private enum class State { INITIALIZING, READY, UNAVAILABLE }

    private class Utterance(val id: String, val text: String, val onDone: () -> Unit, val onError: (String) -> Unit) {
        var closed = false
    }

    override val name: String = ENGINE_NAME

    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    /**
     * A call or another prompt took the audio: stop reading and say why (onError with [VoiceText.TTS_INTERRUPTED]),
     * so the read does not end silently. Ducking needs no action (the car audio system applies it).
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            VoiceErrors.guard(TAG) {
                if (current != null || pending != null) {
                    Log.i(TAG, "audio focus lost ($change); reading stopped")
                    interrupt()
                }
            }
        }
    }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(focusListener, android.os.Handler(android.os.Looper.getMainLooper()))
        .build()
    private var focusHeld = false

    private var tts: TextToSpeech? = null
    private var state = State.INITIALIZING
    private var unavailableName = VoiceText.TTS_NO_KOREAN
    private var current: Utterance? = null
    private var pending: Utterance? = null
    private var counter = 0
    private var initTimer: Runnable? = null

    init {
        try {
            tts = TextToSpeech(app) { status -> VoiceErrors.post(TAG) { onInit(status) } }
            initTimer = VoiceErrors.postDelayed(TAG, INIT_TIMEOUT_MS) {
                initTimer = null
                // Engine did not answer: fail the waiting request but keep listening for a late init.
                if (state == State.INITIALIZING) pending?.let { close(it, VoiceText.TTS_START_FAILED) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Android TTS unavailable", e)
            state = State.UNAVAILABLE
            unavailableName = VoiceText.TTS_START_FAILED
        }
    }

    private fun onInit(status: Int) {
        VoiceErrors.cancel(initTimer)
        initTimer = null
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Android TTS init failed $status")
            becomeUnavailable(VoiceText.TTS_START_FAILED)
            return
        }
        val language = try { engine.setLanguage(Locale.KOREA) } catch (e: Exception) {
            Log.w(TAG, "Android TTS setLanguage failed", e)
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Android TTS has no Korean ($language)")
            becomeUnavailable(VoiceText.TTS_NO_KOREAN)
            return
        }
        try {
            engine.setAudioAttributes(attributes)
            engine.setOnUtteranceProgressListener(progress)
        } catch (e: Exception) {
            Log.w(TAG, "Android TTS setup failed", e)
            becomeUnavailable(VoiceText.TTS_START_FAILED)
            return
        }
        state = State.READY
        pending?.let { pending = null; speakNow(it) }
    }

    private fun becomeUnavailable(errorName: String) {
        state = State.UNAVAILABLE
        unavailableName = errorName
        pending?.let { close(it, errorName) }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) { VoiceErrors.post(TAG) { finished(utteranceId, null) } }
        override fun onStop(utteranceId: String?, interrupted: Boolean) { VoiceErrors.post(TAG) { finished(utteranceId, null) } }
        @Deprecated("Deprecated in Android API")
        override fun onError(utteranceId: String?) { VoiceErrors.post(TAG) { finished(utteranceId, "error") } }
        override fun onError(utteranceId: String?, errorCode: Int) {
            VoiceErrors.post(TAG) { finished(utteranceId, "error $errorCode") }
        }
    }

    override fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit) {
        stop()
        val utterance = Utterance("carlog-${++counter}", text, onDone, onError)
        when (state) {
            State.UNAVAILABLE -> {
                pending = utterance
                val reason = unavailableName
                VoiceErrors.post(TAG) { close(utterance, reason) }
            }
            State.INITIALIZING -> {
                pending = utterance
                // Each waiting request gets its own init timeout, so none waits forever on an engine that never inits.
                VoiceErrors.postDelayed(TAG, INIT_TIMEOUT_MS) {
                    if (state == State.INITIALIZING && pending === utterance) close(utterance, VoiceText.TTS_START_FAILED)
                }
            }
            State.READY -> speakNow(utterance)
        }
    }

    private fun speakNow(utterance: Utterance) {
        val engine = tts
        if (engine == null) { close(utterance, VoiceText.TTS_START_FAILED); return }
        current = utterance
        if (!requestFocus()) { close(utterance, VoiceText.TTS_START_FAILED); return }
        val result = try {
            engine.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utterance.id)
        } catch (e: Exception) {
            Log.w(TAG, "Android TTS speak failed", e)
            TextToSpeech.ERROR
        }
        if (result != TextToSpeech.SUCCESS) close(utterance, VoiceText.TTS_START_FAILED)
    }

    /** Only the utterance in progress may end; anything else is a late event of a stopped one. */
    private fun finished(id: String?, rawError: String?) {
        val utterance = current ?: return
        if (utterance.id != id) return
        if (rawError != null) Log.w(TAG, "Android TTS $rawError")
        close(utterance, if (rawError == null) null else VoiceText.TTS_FAILED)
    }

    /** Ends [utterance] once: onDone when [errorName] is null, otherwise onError with the Korean message. */
    private fun close(utterance: Utterance, errorName: String?) {
        if (utterance.closed) return
        utterance.closed = true
        if (current === utterance) current = null
        if (pending === utterance) pending = null
        if (current == null) abandonFocus()
        if (errorName == null) VoiceErrors.guard(TAG) { utterance.onDone() }
        else {
            val message = VoiceErrors.report(TAG, "Android TTS", errorName)
            VoiceErrors.guard(TAG) { utterance.onError(message) }
        }
    }

    override fun stop() {
        val speakingNow = current
        val waiting = pending
        current = null
        pending = null
        if (speakingNow != null) {
            try { tts?.stop() } catch (e: Exception) { Log.w(TAG, "Android TTS stop failed", e) }
        }
        abandonFocus()
        speakingNow?.let { close(it, null) }
        waiting?.let { close(it, null) }
    }

    /** Like [stop], but the running utterance ends with onError (interrupted by another prompt), not onDone. */
    private fun interrupt() {
        val speakingNow = current
        val waiting = pending
        current = null
        pending = null
        if (speakingNow != null) {
            try { tts?.stop() } catch (e: Exception) { Log.w(TAG, "Android TTS stop failed", e) }
        }
        abandonFocus()
        speakingNow?.let { close(it, VoiceText.TTS_INTERRUPTED) }
        waiting?.let { close(it, VoiceText.TTS_INTERRUPTED) }
    }

    override val speaking: Boolean get() = current != null || pending != null

    override fun release() {
        stop()
        VoiceErrors.cancel(initTimer)
        initTimer = null
        val engine = tts
        tts = null
        state = State.UNAVAILABLE
        unavailableName = VoiceText.TTS_START_FAILED
        try { engine?.shutdown() } catch (e: Exception) { Log.w(TAG, "Android TTS shutdown failed", e) }
    }

    /** False only when the system refused focus (e.g. during a call); then nothing is read. */
    private fun requestFocus(): Boolean {
        if (focusHeld) return true
        val am = audio ?: return true
        return try {
            val granted = am.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusHeld = granted
            if (!granted) Log.w(TAG, "audio focus denied")
            granted
        } catch (e: Exception) {
            Log.w(TAG, "audio focus failed", e)
            true
        }
    }

    private fun abandonFocus() {
        if (!focusHeld) return
        focusHeld = false
        try { audio?.abandonAudioFocusRequest(focus) } catch (e: Exception) { Log.w(TAG, "audio focus release failed", e) }
    }
}
