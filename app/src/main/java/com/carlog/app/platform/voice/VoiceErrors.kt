package com.carlog.app.platform.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carlog.app.domain.VoiceText

/**
 * Android-side helpers shared by the voice engines: logging of raw SDK/engine errors (Logcat only) and
 * main-thread marshalling that never lets an exception escape an SDK callback.
 * The driver-facing wording lives in [VoiceText] (pure, unit-tested).
 */
internal object VoiceErrors {
    const val TAG_VOICE = "CarLogVoice"
    const val TAG_SPEECH = "CarLogSpeech"

    val main: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** Logs [rawName] (SDK enum name / Android code name) and returns the Korean message for the screen. */
    fun report(tag: String, what: String, rawName: String?): String {
        Log.w(tag, "$what: ${rawName ?: "null"}")
        return VoiceText.message(rawName)
    }

    /** Runs [block] on the main thread; anything it throws is logged, never rethrown into the SDK. */
    fun post(tag: String, block: () -> Unit) {
        try {
            main.post { guard(tag, block) }
        } catch (t: Exception) {
            Log.w(tag, "post failed", t)
        }
    }

    fun postDelayed(tag: String, delayMs: Long, block: () -> Unit): Runnable {
        val runnable = Runnable { guard(tag, block) }
        main.postDelayed(runnable, delayMs)
        return runnable
    }

    fun cancel(runnable: Runnable?) {
        if (runnable != null) main.removeCallbacks(runnable)
    }

    fun guard(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Exception) {
            Log.w(tag, "voice callback failed", t)
        }
    }
}
