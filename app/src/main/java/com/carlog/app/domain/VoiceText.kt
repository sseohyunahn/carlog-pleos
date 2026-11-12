package com.carlog.app.domain

/**
 * Driver-facing Korean text for voice errors, keyed by error-name strings so it stays pure Kotlin (JVM-testable).
 *
 * Names come from three places:
 *  - Pleos SpeechToText / TextToSpeech 3.3.1 `ErrorType.name` (e.g. "PERMISSION_DENIED", "TIMEOUT")
 *  - Android SpeechRecognizer error codes, converted with [androidErrorName] (e.g. "ANDROID_NO_MATCH")
 *  - CarLog's own conditions (the constants below, e.g. [START_TIMEOUT])
 *
 * Raw names never reach the screen: they are logged, and only [message] is shown or spoken.
 */
object VoiceText {
    // CarLog-level conditions.
    const val INIT_TIMEOUT = "CARLOG_INIT_TIMEOUT"
    const val START_TIMEOUT = "CARLOG_START_TIMEOUT"
    const val START_FAILED = "CARLOG_START_FAILED"
    const val ENGINE_MISSING = "CARLOG_ENGINE_MISSING"
    const val NO_RECOGNIZER = "CARLOG_NO_RECOGNIZER"
    const val RESULT_TIMEOUT = "CARLOG_RESULT_TIMEOUT"
    const val NO_ENGINE_LEFT = "CARLOG_NO_ENGINE_LEFT"
    const val TTS_NO_KOREAN = "CARLOG_TTS_NO_KOREAN"
    const val TTS_FAILED = "CARLOG_TTS_FAILED"
    const val TTS_START_FAILED = "CARLOG_TTS_START_FAILED"
    /** Another app's prompt or a call took the audio focus while reading. */
    const val TTS_INTERRUPTED = "CARLOG_TTS_INTERRUPTED"

    /** Errors after which the same engine should not be retried in this app session (switch engine / manual input). */
    fun isEngineUnusable(errorName: String?): Boolean = when (errorName) {
        null,
        // Pleos
        "PERMISSION_DENIED", "NOT_SUPPORTED_API", "UNAVAILABLE_LANGUAGE", "ON_DEVICE_ENGINE_ERROR", "UNAUTHENTICATED", "MIC_ERROR",
        // Android SpeechRecognizer
        "ANDROID_INSUFFICIENT_PERMISSIONS", "ANDROID_LANGUAGE_NOT_SUPPORTED", "ANDROID_LANGUAGE_UNAVAILABLE",
        // CarLog
        INIT_TIMEOUT, START_TIMEOUT, START_FAILED, ENGINE_MISSING, NO_RECOGNIZER, NO_ENGINE_LEFT, TTS_NO_KOREAN -> true
        else -> false
    }

    /**
     * Start-up failures that may clear on the next try (cold vehicle service bind, slow engine start). The engine is
     * skipped for the current recording only, not marked unusable for the app session.
     */
    fun isTransientStartFailure(errorName: String?): Boolean =
        errorName == INIT_TIMEOUT || errorName == START_TIMEOUT || errorName == START_FAILED

    /** Short, polite Korean (해요체). Never contains the raw error name. */
    fun message(errorName: String?): String = when (errorName) {
        null -> "음성 기능을 시작하지 못했어요. 직접 입력으로 남길 수 있어요"
        // Pleos ErrorType
        "PERMISSION_DENIED" -> "이 차량에서는 음성 기능 사용 허용이 필요해요. 직접 입력으로 남길 수 있어요"
        "MIC_ERROR" -> "마이크를 사용할 수 없어요. 직접 입력으로 남길 수 있어요"
        "AUDIO_ERROR" -> "소리를 받지 못했어요. 다시 말씀해 주세요"
        "TIMEOUT" -> "말씀이 들리지 않았어요. 다시 시도해 주세요"
        "NETWORK_ERROR", "SERVER_INTERNAL_ERROR" -> "음성 서비스 연결이 불안정해요. 잠시 후 다시 시도해 주세요"
        "UNAVAILABLE_LANGUAGE" -> "한국어 음성 엔진이 준비되지 않았어요. 직접 입력으로 남길 수 있어요"
        "NOT_SUPPORTED_API" -> "이 차량은 음성 기능을 지원하지 않아요. 직접 입력으로 남길 수 있어요"
        "ON_DEVICE_ENGINE_ERROR" -> "차량 음성 엔진이 준비되지 않았어요. 다른 방법으로 이어갈게요"
        "UNAUTHENTICATED" -> "음성 서비스 인증이 필요해요. 직접 입력으로 남길 수 있어요"
        "INVALID_REQUEST_ERROR" -> "음성 요청을 처리하지 못했어요. 다시 시도해 주세요"
        // Android SpeechRecognizer
        "ANDROID_NETWORK_TIMEOUT", "ANDROID_NETWORK", "ANDROID_SERVER", "ANDROID_SERVER_DISCONNECTED" ->
            "음성 인식 연결이 불안정해요. 잠시 후 다시 시도해 주세요"
        "ANDROID_AUDIO" -> "소리를 받지 못했어요. 다시 말씀해 주세요"
        "ANDROID_CLIENT" -> "음성 인식을 마치지 못했어요. 다시 시도해 주세요"
        "ANDROID_SPEECH_TIMEOUT", "ANDROID_NO_MATCH" -> "말씀이 들리지 않았어요. 다시 시도해 주세요"
        "ANDROID_RECOGNIZER_BUSY", "ANDROID_TOO_MANY_REQUESTS" -> "음성 인식이 잠시 바빠요. 조금 뒤 다시 시도해 주세요"
        "ANDROID_INSUFFICIENT_PERMISSIONS" -> "마이크 권한이 필요해요. 직접 입력으로 남길 수 있어요"
        "ANDROID_LANGUAGE_NOT_SUPPORTED", "ANDROID_LANGUAGE_UNAVAILABLE" ->
            "한국어 음성 인식이 준비되지 않았어요. 직접 입력으로 남길 수 있어요"
        // CarLog
        INIT_TIMEOUT -> "차량 음성 서비스가 준비되지 않았어요. 다른 방법으로 이어갈게요"
        START_TIMEOUT -> "음성 인식이 시작되지 않았어요. 다른 방법으로 이어갈게요"
        START_FAILED -> "음성 인식을 시작하지 못했어요. 다른 방법으로 이어갈게요"
        ENGINE_MISSING -> "이 차량에서는 차량 음성 인식을 쓸 수 없어요. 다른 방법으로 이어갈게요"
        NO_RECOGNIZER -> "이 기기에는 음성 인식기가 없어요. 직접 입력으로 남길 수 있어요"
        RESULT_TIMEOUT -> "인식 결과를 받지 못했어요. 다시 말씀하시거나 직접 입력해 주세요"
        NO_ENGINE_LEFT -> "지금은 음성 입력을 쓸 수 없어요. 직접 입력으로 남길 수 있어요"
        TTS_NO_KOREAN -> "한국어 읽기 엔진이 없어요. 정차 후 화면에서 확인해 주세요"
        TTS_FAILED -> "읽어주기를 마치지 못했어요. 정차 후 화면에서 확인해 주세요"
        TTS_START_FAILED -> "읽어주기를 시작하지 못했어요. 정차 후 화면에서 확인해 주세요"
        TTS_INTERRUPTED -> "다른 안내 때문에 읽기를 멈췄어요. 다시 누르면 처음부터 읽어요"
        else -> "음성 기능을 잠시 쓸 수 없어요. 다시 시도해 주세요"
    }

    /**
     * android.speech.SpeechRecognizer error code -> name. Literal values so this stays JVM-only
     * (ERROR_NETWORK_TIMEOUT=1 ... ERROR_LANGUAGE_UNAVAILABLE=13).
     */
    fun androidErrorName(code: Int): String = when (code) {
        1 -> "ANDROID_NETWORK_TIMEOUT"
        2 -> "ANDROID_NETWORK"
        3 -> "ANDROID_AUDIO"
        4 -> "ANDROID_SERVER"
        5 -> "ANDROID_CLIENT"
        6 -> "ANDROID_SPEECH_TIMEOUT"
        7 -> "ANDROID_NO_MATCH"
        8 -> "ANDROID_RECOGNIZER_BUSY"
        9 -> "ANDROID_INSUFFICIENT_PERMISSIONS"
        10 -> "ANDROID_TOO_MANY_REQUESTS"
        11 -> "ANDROID_SERVER_DISCONNECTED"
        12 -> "ANDROID_LANGUAGE_NOT_SUPPORTED"
        13 -> "ANDROID_LANGUAGE_UNAVAILABLE"
        else -> "ANDROID_UNKNOWN_$code"
    }

    /** "Nothing was heard" style Android errors: deliver the last partial text if there is one. */
    fun isNoSpeech(errorName: String?): Boolean =
        errorName == "ANDROID_NO_MATCH" || errorName == "ANDROID_SPEECH_TIMEOUT" || errorName == "ANDROID_CLIENT" ||
            errorName == "TIMEOUT"

    /**
     * Level-meter smoothing for the screen: rises at once (fast attack), falls slowly (release), so the bar follows
     * the voice without flickering at the engine's callback rate.
     */
    fun smoothLevel(previous: Float, next: Float): Float {
        val x = if (next.isFinite()) next.coerceIn(0f, 1f) else 0f
        val p = if (previous.isFinite()) previous.coerceIn(0f, 1f) else 0f
        return if (x >= p) x else p * 0.85f + x * 0.15f
    }

    /** Android onRmsChanged dB (roughly -2..10) -> 0..1. */
    fun normaliseDb(rmsDb: Float): Float = if (!rmsDb.isFinite()) 0f else ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)

    private val WHITESPACE = Regex("\\s+")
    private const val SENTENCE_ENDS = ".!?…~。"
    /** When an appended dictation would overflow, a word boundary this close to the limit is preferred. */
    private const val WORD_CUT_WINDOW = 40

    /** Recognizer text for the editor: whitespace runs (incl. newlines) collapsed to one space, trimmed. */
    fun tidyTranscript(text: String): String = text.replace(WHITESPACE, " ").trim()

    /**
     * Appends a new dictation to the memo text. Recognizers add no punctuation, so a sentence break (". ") is
     * inserted unless [existing] already ends a sentence; TTS then pauses between the two utterances.
     * Returns the text (at most [max] chars) and whether part of [heard] had to be cut. An over-long result is cut at
     * a word boundary near the limit when there is one, and never between the halves of a surrogate pair.
     */
    fun appendTranscript(existing: String, heard: String, max: Int): Pair<String, Boolean> {
        val base = existing.trim()
        val add = heard.trim()
        val joined = when {
            base.isEmpty() -> add
            add.isEmpty() -> base
            base.last() in SENTENCE_ENDS -> "$base $add"
            else -> "$base. $add"
        }
        if (joined.length <= max) return joined to false
        // The text that was already there is never shortened to make room.
        if (base.length >= max) return takeSafe(base, max) to true
        val low = maxOf(base.length, max - WORD_CUT_WINDOW + 1, 1)
        val space = (max downTo low).firstOrNull { joined[it].isWhitespace() }
        val cut = takeSafe(joined, space ?: max)
        return cut.trimEnd() to true
    }

    /** [String.take] that never leaves a lone high surrogate (half an emoji) at the end. */
    fun takeSafe(text: String, max: Int): String {
        if (text.length <= max) return text
        var cut = max.coerceAtLeast(0)
        if (cut > 0 && text[cut - 1].isHighSurrogate()) cut--
        return text.substring(0, cut)
    }
}

/**
 * Scale-free level meter for linear RMS values with no documented range (Pleos UpdateRms). It tracks a noise floor
 * (falls at once, rises slowly) and a decaying peak, so steady cabin noise reads near 0 and only sound clearly above
 * the floor fills the bar. Create one per recording.
 */
class LinearLevelMeter {
    private var floor = -1.0
    private var peak = 0.0

    fun next(rms: Double): Float {
        if (!rms.isFinite() || rms < 0) return 0f
        if (floor < 0) { floor = rms; peak = rms; return 0f }
        floor = if (rms < floor) rms else floor + (rms - floor) * 0.02
        peak = maxOf(peak * 0.98, rms)
        val range = maxOf(peak - floor, floor * 3.0, 1e-6)
        return ((rms - floor) / range).toFloat().coerceIn(0f, 1f)
    }
}
