package com.carlog.app.platform

import com.carlog.app.domain.VehicleSnapshot

/*
 * CarLog v2 platform contracts. Implementations live next to this file:
 *   VehicleFeeds.kt  -> DemoVehicleFeed, PleosVehicleFeed (+ SnapshotMerge in domain/)
 *   voice/ (Kotlin)  -> PleosVoiceInput, AndroidVoiceInput, PleosSpeaker, AndroidSpeaker, VoiceService
 * All callbacks are delivered on the main thread. Implementations must ignore late callbacks from a
 * stopped/cancelled session (use an epoch/token), and must never throw out of a callback.
 */

/** Continuous vehicle state source. Owns its SDK connection until [stop]. */
interface VehicleFeed {
    /** Begin polling (Pleos: every 5 s in rounds; demo: every 5 s). Emits merged last-valid snapshots. */
    fun start(onSnapshot: (VehicleSnapshot) -> Unit)
    /** Read now (also reconnects if initialisation failed). */
    fun refresh()
    /** Stop polling and release the SDK (after at most a short grace period for in-flight callbacks). */
    fun stop()
}

/** In-app speech recognition. */
interface VoiceInput {
    interface Callback {
        fun onListening()
        fun onPartial(text: String)
        /** 0..1 */
        fun onLevel(level: Float)
        /** Final text; may be empty when nothing was recognised. */
        fun onFinal(text: String)
        /** [useFallback] = the engine itself is unusable here (permission/unsupported/engine failure). */
        fun onError(message: String, useFallback: Boolean)
        /**
         * The current engine failed mid-recording and [engine] takes over the same recording. The old countdown is
         * void: onListening (with a new deadline) follows when the new engine is ready.
         */
        fun onRestarting(engine: String) {}
    }
    /** Short engine label for the UI, e.g. "Pleos 음성 인식". */
    val name: String
    fun start(callback: Callback)
    /** Stop listening; the engine then delivers onFinal or onError. */
    fun stop()
    /** Cancel without delivering anything. */
    fun cancel()
    fun release()
}

/** Text-to-speech. */
interface Speaker {
    val name: String
    /** Speaks [text] (ko-KR). [onDone] is called once when finished/stopped; [onError] with a short Korean message. */
    fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit)
    fun stop()
    fun release()
    val speaking: Boolean
}
