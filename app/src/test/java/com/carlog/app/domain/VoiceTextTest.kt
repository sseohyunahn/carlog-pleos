package com.carlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTextTest {
    private val pleosNames = listOf(
        "AUDIO_ERROR", "MIC_ERROR", "SERVER_INTERNAL_ERROR", "NETWORK_ERROR", "INVALID_REQUEST_ERROR", "TIMEOUT",
        "UNAUTHENTICATED", "PERMISSION_DENIED", "ON_DEVICE_ENGINE_ERROR", "UNKNOWN", "UNAVAILABLE_LANGUAGE", "NOT_SUPPORTED_API"
    )
    private val carlogNames = listOf(
        VoiceText.INIT_TIMEOUT, VoiceText.START_TIMEOUT, VoiceText.START_FAILED, VoiceText.ENGINE_MISSING,
        VoiceText.NO_RECOGNIZER, VoiceText.RESULT_TIMEOUT, VoiceText.NO_ENGINE_LEFT, VoiceText.TTS_NO_KOREAN,
        VoiceText.TTS_FAILED, VoiceText.TTS_START_FAILED, VoiceText.TTS_INTERRUPTED
    )
    private val androidNames = (0..15).map { VoiceText.androidErrorName(it) }

    @Test fun engineLevelFailuresSwitchToAnotherInputPath() {
        listOf("PERMISSION_DENIED", "NOT_SUPPORTED_API", "UNAVAILABLE_LANGUAGE", "ON_DEVICE_ENGINE_ERROR", "MIC_ERROR",
            "UNAUTHENTICATED", null, VoiceText.START_TIMEOUT, VoiceText.INIT_TIMEOUT, VoiceText.NO_RECOGNIZER,
            "ANDROID_INSUFFICIENT_PERMISSIONS", "ANDROID_LANGUAGE_NOT_SUPPORTED", "ANDROID_LANGUAGE_UNAVAILABLE")
            .forEach { assertTrue(it.toString(), VoiceText.isEngineUnusable(it)) }
    }

    @Test fun transientFailuresKeepTheEngineForARetry() {
        listOf("TIMEOUT", "AUDIO_ERROR", "NETWORK_ERROR", "UNKNOWN", "SOMETHING_NEW", "ANDROID_NO_MATCH",
            "ANDROID_SPEECH_TIMEOUT", "ANDROID_NETWORK", "ANDROID_RECOGNIZER_BUSY", VoiceText.RESULT_TIMEOUT)
            .forEach { assertFalse(it, VoiceText.isEngineUnusable(it)) }
    }

    @Test fun everyErrorHasPoliteKoreanTextWithoutRawNames() {
        (pleosNames + carlogNames + androidNames + listOf("FUTURE_VALUE", null)).forEach { name ->
            val message = VoiceText.message(name)
            assertTrue(name.toString(), message.isNotBlank())
            assertFalse("raw text leaked for $name: $message", message.any { it == '_' || it in 'A'..'Z' || it in 'a'..'z' })
            assertTrue("not 해요체 for $name: $message", message.endsWith("요"))
        }
    }

    @Test fun androidCodesMapToNames() {
        assertEquals("ANDROID_NO_MATCH", VoiceText.androidErrorName(7))
        assertEquals("ANDROID_INSUFFICIENT_PERMISSIONS", VoiceText.androidErrorName(9))
        assertEquals("ANDROID_LANGUAGE_UNAVAILABLE", VoiceText.androidErrorName(13))
        assertTrue(VoiceText.androidErrorName(99).startsWith("ANDROID_UNKNOWN"))
    }

    @Test fun noSpeechErrorsAreRecognised() {
        assertTrue(VoiceText.isNoSpeech("ANDROID_NO_MATCH"))
        assertTrue(VoiceText.isNoSpeech("ANDROID_SPEECH_TIMEOUT"))
        assertTrue(VoiceText.isNoSpeech("TIMEOUT"))
        assertFalse(VoiceText.isNoSpeech("PERMISSION_DENIED"))
    }

    @Test fun linearMeterReadsNoiseAsSilenceAndVoiceAsLevel() {
        val meter = LinearLevelMeter()
        // The first sample is the cabin noise floor, not "full".
        assertEquals(0f, meter.next(100.0), 0f)
        repeat(60) { assertTrue(meter.next(100.0) < 0.05f) }
        // Speech clearly above the floor fills the bar.
        assertTrue(meter.next(400.0) > 0.8f)
        assertTrue(meter.next(1_000.0) > 0.9f)
        // Back to noise after speech: near zero again, also after the peak has decayed for a while.
        repeat(200) { meter.next(100.0) }
        assertTrue(meter.next(100.0) < 0.05f)
        // Scale-free: the same shape works for tiny values.
        val small = LinearLevelMeter()
        small.next(0.001)
        repeat(30) { small.next(0.001) }
        assertTrue(small.next(0.004) > 0.8f)
        // Invalid input reads 0 and does not disturb the state.
        assertEquals(0f, meter.next(Double.NaN), 0f)
        assertEquals(0f, meter.next(-1.0), 0f)
        assertTrue(meter.next(400.0) > 0.8f)
    }

    @Test fun levelSmoothingRisesAtOnceAndFallsSlowly() {
        assertEquals(0.9f, VoiceText.smoothLevel(0.2f, 0.9f), 0f)
        val fall = VoiceText.smoothLevel(0.9f, 0f)
        assertTrue(fall > 0.7f && fall < 0.9f)
        assertEquals(0f, VoiceText.smoothLevel(Float.NaN, Float.NaN), 0f)
        assertEquals(1f, VoiceText.smoothLevel(0f, 5f), 0f)
    }

    @Test fun startUpFailuresAreTransientButCapabilityErrorsAreNot() {
        listOf(VoiceText.INIT_TIMEOUT, VoiceText.START_TIMEOUT, VoiceText.START_FAILED)
            .forEach { assertTrue(it, VoiceText.isTransientStartFailure(it)) }
        listOf("PERMISSION_DENIED", "NOT_SUPPORTED_API", "UNAVAILABLE_LANGUAGE", "UNAUTHENTICATED", VoiceText.ENGINE_MISSING,
            "MIC_ERROR", "TIMEOUT", null).forEach { assertFalse(it.toString(), VoiceText.isTransientStartFailure(it)) }
    }

    @Test fun readAloudErrorsReferToTheScreenOnlyAfterStopping() {
        listOf(VoiceText.TTS_NO_KOREAN, VoiceText.TTS_FAILED, VoiceText.TTS_START_FAILED).forEach {
            assertTrue(it, VoiceText.message(it).contains("정차 후 화면에서"))
        }
        assertTrue(VoiceText.message(VoiceText.TTS_INTERRUPTED).startsWith("다른 안내 때문에 읽기를 멈췄어요"))
    }

    @Test fun dictationIsAppendedAsANewSentence() {
        assertEquals("우산 있어요" to false, VoiceText.appendTranscript("", " 우산 있어요 ", 300))
        assertEquals("트렁크에 우산 있어요. 내일 충전 부탁해요" to false,
            VoiceText.appendTranscript("트렁크에 우산 있어요 ", "내일 충전 부탁해요", 300))
        assertEquals("우산 있어요. 충전 부탁" to false, VoiceText.appendTranscript("우산 있어요.", "충전 부탁", 300))
        assertEquals("고마워요~ 충전 부탁" to false, VoiceText.appendTranscript("고마워요~", "충전 부탁", 300))
        // Exactly at the limit: nothing is cut.
        val base = "가".repeat(290)
        assertEquals("$base. 나다라마바" to false, VoiceText.appendTranscript(base, "나다라마바", 297))
    }

    @Test fun overflowingDictationIsCutAtAWordAndReported() {
        val base = "가".repeat(280)
        val (text, truncated) = VoiceText.appendTranscript(base, "브레이크 경고등이 켜졌어요 정비소에 꼭 가 주세요", 300)
        assertTrue(truncated)
        assertTrue(text.length <= 300)
        assertEquals("$base. 브레이크 경고등이 켜졌어요", text)
        // Existing words near the limit are never removed to make room.
        val words = (1..99).joinToString(" ") { "가나" } // 296 chars
        assertEquals("$words." to true, VoiceText.appendTranscript(words, "브레이크", 300))
        // A full memo keeps its text and reports that the new words were dropped.
        val full = "나".repeat(300)
        assertEquals(full to true, VoiceText.appendTranscript(full, "더 할 말", 300))
        // No word boundary nearby: hard cut at the limit.
        val (hard, cut) = VoiceText.appendTranscript("", "다".repeat(350), 300)
        assertTrue(cut)
        assertEquals(300, hard.length)
    }

    @Test fun cutsNeverSplitASurrogatePair() {
        val emoji = "🚗" // car emoji, two UTF-16 units
        val text = "가".repeat(299) + emoji
        assertEquals("가".repeat(299), VoiceText.takeSafe(text, 300))
        assertEquals(text, VoiceText.takeSafe(text, 301))
        val (appended, truncated) = VoiceText.appendTranscript("", text, 300)
        assertTrue(truncated)
        assertFalse(appended.last().isHighSurrogate())
    }

    @Test fun transcriptWhitespaceIsCollapsed() {
        assertEquals("트렁크에 우산 있어요", VoiceText.tidyTranscript("  트렁크에\n우산\t\t있어요  "))
        assertEquals("", VoiceText.tidyTranscript(" \n "))
    }

    @Test fun androidDbLevelsAreNormalised() {
        assertEquals(0f, VoiceText.normaliseDb(-10f), 0f)
        assertEquals(1f, VoiceText.normaliseDb(20f), 0f)
        assertEquals(0f, VoiceText.normaliseDb(Float.NaN), 0f)
    }
}
