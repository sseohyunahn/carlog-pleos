package com.carlog.app.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech as AndroidTts
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import com.carlog.app.domain.DataMode
import java.util.Locale

class VoiceService(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stt: SpeechToText? = null
    private var sttListener: ResultListener? = null
    private var pleosTts: PleosTts? = null
    private var androidTts: AndroidTts? = null
    private var epoch = 0
    fun listen(mode: DataMode, onText: (String) -> Unit, onStatus: (String) -> Unit) {
        stop()
        val token = epoch
        fun status(text: String) { handler.post { if(token == epoch) onStatus(text) } }
        fun result(text: String) { handler.post { if(token == epoch) onText(text) } }
        if(mode == DataMode.PLEOS) {
            try {
                val sdk = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
                stt = sdk
                val listener = object : ResultListener {
                    override fun onReady() = Unit
                    override fun onStartedRecognition() { status("듣고 있어요 · 최대 10초") }
                    override fun onUpdated(stt: String, completed: Boolean) { if(completed && stt.isNotBlank()) result(stt) }
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onEndedRecognition() { status("음성 입력 종료 · 내용을 확인해 주세요") }
                    override fun onError(errorType: SttError) { status("Pleos 음성 입력 불가: ${errorType.name} · 직접 입력해 주세요") }
                }
                sttListener = listener
                sdk.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                    override fun onSuccess() {
                        handler.post { if(token == epoch) runCatching { sdk.addListener(listener); sdk.setLocale(Locale.KOREAN); sdk.request() }
                            .onFailure { status("음성 입력 실패 · 직접 입력해 주세요") } }
                    }
                    override fun onFailure(errorType: SttError) { status("Pleos 음성 서비스 연결 불가: ${errorType.name}") }
                })
            } catch (e: Exception) { status("Pleos 음성 서비스 없음 · 직접 입력해 주세요") }
        } else {
            if(!SpeechRecognizer.isRecognitionAvailable(context)) { status("이 기기에 음성 인식기가 없어요 · 직접 입력해 주세요"); return }
            val engine = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status("듣고 있어요 · 최대 10초") }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status("음성을 처리하고 있어요") }
                override fun onError(error: Int) { status("음성을 인식하지 못했어요 ($error) · 직접 입력 가능") }
                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::result)
                    status("인식한 내용을 확인해 주세요")
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            engine.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        }
        handler.postDelayed({ if(token == epoch) { runCatching { stt?.stop() }; runCatching { recognizer?.stopListening() }; status("녹음 종료 · 인식 결과를 기다리는 중") } }, 10_000)
        handler.postDelayed({ if(token == epoch) { stop(); onStatus("음성 입력 종료 · 직접 입력도 가능해요") } }, 18_000)
    }
    fun speak(mode: DataMode, text: String, onStatus: (String) -> Unit) {
        stop()
        val token = epoch
        if(mode == DataMode.PLEOS) {
            try {
                val engine = PleosTts(context.applicationContext, TtsMode.ON_DEVICE)
                pleosTts = engine
                engine.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                    override fun onSuccess() { handler.post { if(token == epoch) runCatching { engine.setLocale(Locale.KOREAN); engine.speak(text, true) }
                        .onFailure { onStatus("Pleos 읽어주기 실패 · 화면으로 확인해 주세요") } } }
                    override fun onFailure(errorType: TtsError) { handler.post { if(token == epoch) onStatus("Pleos 읽어주기 불가: ${errorType.name}") } }
                })
            } catch (e: Exception) { onStatus("Pleos 음성 서비스에 연결할 수 없어요") }
        } else {
            androidTts = AndroidTts(context) { code -> handler.post {
                if(token == epoch) {
                    if(code == AndroidTts.SUCCESS) {
                        val language = androidTts?.setLanguage(Locale.KOREAN)
                        if(language == AndroidTts.LANG_MISSING_DATA || language == AndroidTts.LANG_NOT_SUPPORTED) onStatus("한국어 음성 엔진이 설치되지 않았어요")
                        else androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "carlog-handover")
                    } else onStatus("읽어주기 엔진이 없어요 · 화면으로 확인해 주세요")
                }
            } }
        }
    }
    fun stop() {
        epoch++
        handler.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }; recognizer = null
        stt?.let { sdk -> runCatching { sdk.stop() }; sttListener?.let { runCatching { sdk.removeListener(it) } }; runCatching { sdk.release() } }
        stt = null; sttListener = null
        pleosTts?.let { runCatching { it.stop() }; runCatching { it.release() } }; pleosTts = null
        androidTts?.let { runCatching { it.stop(); it.shutdown() } }; androidTts = null
    }
}
