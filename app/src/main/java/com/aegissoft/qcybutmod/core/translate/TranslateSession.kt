package com.aegissoft.qcybutmod.core.translate

import android.content.Context
import android.util.Log
import com.aegissoft.qcybutmod.core.audio.AegisVoiceAssistant
import com.aegissoft.qcybutmod.core.audio.VoiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val originalText: String,
    val translatedText: String,
    val sourceLang: SupportedLanguage,
    val targetLang: SupportedLanguage,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromHeadsetMic: Boolean = false
)

enum class EngineType(val title: String, val subtitle: String) {
    OFFLINE_MLKIT("ML Kit (Cihaz İçi)", "İnternet gerektirmez, tamamen çevrimdışı ve gizlilik odaklı"),
    GEMINI_CLOUD("Gemini AI (Bulut)", "Yüksek doğruluklu LLM çevirisi, internet ve API anahtarı gerektirir")
}

data class TranslateUiState(
    val sourceLang: SupportedLanguage = LanguageCatalog.DEFAULT_SOURCE,
    val targetLang: SupportedLanguage = LanguageCatalog.DEFAULT_TARGET,
    val engineType: EngineType = EngineType.OFFLINE_MLKIT,
    val micSource: MicSource = MicSource.PHONE_MIC,
    val isListening: Boolean = false,
    val ttsRoute: String = VoiceSettings.ROUTE_HEADSET,
    val transcriptOrder: String = VoiceSettings.ORDER_SOURCE_FIRST,
    val transcriptRotate: Boolean = false,
    val prepareState: PrepareState = PrepareState.Idle,
    val transcriptItems: List<TranscriptItem> = emptyList(),
    val currentPartialOriginal: String = "",
    val audioRms: Float = 0f,
    val errorMessage: String? = null
)

class TranslateSession(private val context: Context) {

    companion object {
        private const val TAG = "TranslateSession"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val mlKitEngine = MlKitEngine()
    private val geminiEngine = GeminiLiveEngine(context)
    private val speechInput = SpeechInput(context)
    private val voiceAssistant = AegisVoiceAssistant.getInstance(context)

    private val _uiState = MutableStateFlow(
        TranslateUiState(
            ttsRoute = VoiceSettings.getTranslateTtsRoute(context),
            transcriptOrder = VoiceSettings.getTranscriptOrder(context),
            transcriptRotate = VoiceSettings.getTranscriptRotate(context)
        )
    )
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    init {
        speechInput.setListener(object : SpeechInputListener {
            override fun onReady() {
                _uiState.update { it.copy(errorMessage = null) }
            }

            override fun onBeginningOfSpeech() {
                // Speech started
            }

            override fun onRmsChanged(rmsdB: Float) {
                _uiState.update { it.copy(audioRms = rmsdB) }
            }

            override fun onPartialResult(partialText: String) {
                _uiState.update { it.copy(currentPartialOriginal = partialText) }
            }

            override fun onFinalResult(text: String) {
                _uiState.update { it.copy(currentPartialOriginal = "") }
                processSpeechInput(text)
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                _uiState.update { it.copy(errorMessage = errorMessage) }
            }

            override fun onStateChanged(isListening: Boolean) {
                _uiState.update { it.copy(isListening = isListening) }
            }
        })

        // Warm up / prepare ML Kit for default languages
        scope.launch {
            prepareEngine()
        }
    }

    private suspend fun prepareEngine() {
        val state = _uiState.value
        val engine = getActiveEngine()
        _uiState.update { it.copy(prepareState = PrepareState.Downloading(0)) }
        val prep = engine.prepare(state.sourceLang, state.targetLang)
        _uiState.update { it.copy(prepareState = prep) }
    }

    private fun getActiveEngine(): TranslationEngine {
        return when (_uiState.value.engineType) {
            EngineType.OFFLINE_MLKIT -> mlKitEngine
            EngineType.GEMINI_CLOUD -> geminiEngine
        }
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        val state = _uiState.value
        scope.launch {
            val engine = getActiveEngine()
            val prep = engine.prepare(state.sourceLang, state.targetLang)
            _uiState.update { it.copy(prepareState = prep) }
            if (prep is PrepareState.Error) {
                _uiState.update { it.copy(errorMessage = prep.message) }
                return@launch
            }
            speechInput.start(state.sourceLang.code, state.micSource)
        }
    }

    fun stopListening() {
        speechInput.stop()
        _uiState.update { it.copy(isListening = false, currentPartialOriginal = "", audioRms = 0f) }
    }

    fun swapLanguages() {
        val current = _uiState.value
        val newSource = current.targetLang
        val newTarget = current.sourceLang
        _uiState.update { it.copy(sourceLang = newSource, targetLang = newTarget) }

        if (current.isListening) {
            speechInput.stop()
            scope.launch {
                prepareEngine()
                speechInput.start(newSource.code, current.micSource)
            }
        } else {
            scope.launch { prepareEngine() }
        }
    }

    fun setSourceLang(lang: SupportedLanguage) {
        if (lang.code == _uiState.value.sourceLang.code) return
        _uiState.update { it.copy(sourceLang = lang) }
        restartListeningIfActive()
    }

    fun setTargetLang(lang: SupportedLanguage) {
        if (lang.code == _uiState.value.targetLang.code) return
        _uiState.update { it.copy(targetLang = lang) }
        restartListeningIfActive()
    }

    fun setMicSource(source: MicSource) {
        if (source == _uiState.value.micSource) return
        _uiState.update { it.copy(micSource = source) }
        restartListeningIfActive()
    }

    fun setEngineType(type: EngineType) {
        if (type == _uiState.value.engineType) return
        _uiState.update { it.copy(engineType = type) }
        restartListeningIfActive()
    }

    fun cycleTranslateTtsRoute() {
        val route = VoiceSettings.cycleTranslateTtsRoute(context)
        _uiState.update { it.copy(ttsRoute = route) }
    }

    fun setTranscriptOrder(order: String) {
        VoiceSettings.setTranscriptOrder(context, order)
        _uiState.update { it.copy(transcriptOrder = order) }
    }

    fun setTranscriptRotate(rotate: Boolean) {
        VoiceSettings.setTranscriptRotate(context, rotate)
        _uiState.update { it.copy(transcriptRotate = rotate) }
    }

    fun clearTranscripts() {
        _uiState.update { it.copy(transcriptItems = emptyList()) }
    }

    fun deleteTranscript(id: String) {
        _uiState.update { it.copy(transcriptItems = it.transcriptItems.filterNot { item -> item.id == id }) }
    }

    private fun restartListeningIfActive() {
        val state = _uiState.value
        if (state.isListening) {
            speechInput.stop()
            scope.launch {
                prepareEngine()
                speechInput.start(state.sourceLang.code, state.micSource)
            }
        } else {
            scope.launch { prepareEngine() }
        }
    }

    fun processSpeechInput(originalText: String) {
        if (originalText.isBlank()) return
        val state = _uiState.value
        val source = state.sourceLang
        val target = state.targetLang
        val isHeadsetMic = state.micSource == MicSource.HEADSET_MIC

        translationJob?.cancel()
        translationJob = scope.launch(Dispatchers.IO) {
            try {
                val engine = getActiveEngine()
                val translated = engine.translate(originalText, source, target)

                val item = TranscriptItem(
                    originalText = originalText,
                    translatedText = translated,
                    sourceLang = source,
                    targetLang = target,
                    isFromHeadsetMic = isHeadsetMic
                )

                _uiState.update { current ->
                    current.copy(
                        transcriptItems = current.transcriptItems + item,
                        errorMessage = null
                    )
                }

                // Spoken output to the selected route (headset / phone speaker) if not off
                if (state.ttsRoute != VoiceSettings.ROUTE_OFF && translated.isNotBlank()) {
                    voiceAssistant.speakTranslation(translated, target.code)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation processing failed", e)
                _uiState.update { it.copy(errorMessage = "Çeviri hatası: ${e.message}") }
            }
        }
    }

    fun replaySpeech(item: TranscriptItem) {
        if (item.translatedText.isNotBlank()) {
            voiceAssistant.speakTranslation(item.translatedText, item.targetLang.code)
        }
    }

    fun destroy() {
        speechInput.destroy()
        mlKitEngine.close()
        geminiEngine.close()
    }
}
