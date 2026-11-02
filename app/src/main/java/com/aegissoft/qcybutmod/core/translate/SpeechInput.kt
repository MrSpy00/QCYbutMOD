package com.aegissoft.qcybutmod.core.translate

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

enum class MicSource(val title: String, val description: String) {
    PHONE_MIC("Telefon Mikrofonu", "Karşıdaki konuşmacıyı dinlemek için telefon mikrofonunu kullanır"),
    HEADSET_MIC("Kulaklık Mikrofonu", "Kendi sesinizi kulaklık üzerinden dinlemek için Bluetooth SCO kullanır")
}

interface SpeechInputListener {
    fun onReady()
    fun onBeginningOfSpeech()
    fun onRmsChanged(rmsdB: Float)
    fun onPartialResult(partialText: String)
    fun onFinalResult(text: String)
    fun onError(errorCode: Int, errorMessage: String)
    fun onStateChanged(isListening: Boolean)
}

class SpeechInput(private val context: Context) {

    companion object {
        private const val TAG = "SpeechInput"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: SpeechInputListener? = null

    private var isListening: Boolean = false
    private var shouldKeepListening: Boolean = false
    private var currentLangCode: String = "tr"
    private var currentMicSource: MicSource = MicSource.PHONE_MIC

    fun setListener(listener: SpeechInputListener) {
        this.listener = listener
    }

    fun isListening(): Boolean = isListening

    fun start(langCode: String, micSource: MicSource = MicSource.PHONE_MIC) {
        mainHandler.post {
            currentLangCode = langCode
            currentMicSource = micSource
            shouldKeepListening = true

            configureAudioRouting(micSource)
            startInternalRecognizer()
        }
    }

    fun stop() {
        mainHandler.post {
            shouldKeepListening = false
            stopInternalRecognizer()
            restoreAudioRouting()
        }
    }

    private fun configureAudioRouting(source: MicSource) {
        try {
            if (source == MicSource.HEADSET_MIC) {
                Log.d(TAG, "Configuring audio routing for Bluetooth Headset SCO")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val btScoDevice = devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    if (btScoDevice != null) {
                        audioManager.setCommunicationDevice(btScoDevice)
                        Log.d(TAG, "Communication device set to: ${btScoDevice.productName}")
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.startBluetoothSco()
                        @Suppress("DEPRECATION")
                        audioManager.isBluetoothScoOn = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                }
            } else {
                restoreAudioRouting()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring audio routing: ${e.message}")
        }
    }

    private fun restoreAudioRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
            @Suppress("DEPRECATION")
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring audio routing: ${e.message}")
        }
    }

    private fun startInternalRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device")
            listener?.onError(-1, "Cihazda ses tanıma hizmeti bulunamadı")
            return
        }

        stopInternalRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(InternalRecognitionListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

                val bcp47 = when (currentLangCode.lowercase()) {
                    "tr" -> "tr-TR"
                    "en" -> "en-US"
                    "de" -> "de-DE"
                    "fr" -> "fr-FR"
                    "es" -> "es-ES"
                    "ru" -> "ru-RU"
                    "ar" -> "ar-SA"
                    "ja" -> "ja-JP"
                    else -> currentLangCode
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            listener?.onStateChanged(true)
            Log.d(TAG, "Started SpeechRecognizer for language: $currentLangCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isListening = false
            listener?.onStateChanged(false)
            listener?.onError(-2, e.localizedMessage ?: "Ses tanıma başlatılamadı")
        }
    }

    private fun stopInternalRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            isListening = false
            listener?.onStateChanged(false)
        }
    }

    private inner class InternalRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onReady()
        }

        override fun onBeginningOfSpeech() {
            listener?.onBeginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalized RMS from dB (-2 to 10 dB typically)
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            listener?.onRmsChanged(normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Ses anlaşılamadı"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses zaman aşımı"
                SpeechRecognizer.ERROR_NETWORK -> "Ağ bağlantı hatası"
                SpeechRecognizer.ERROR_AUDIO -> "Mikrofon kayıt hatası"
                SpeechRecognizer.ERROR_SERVER -> "Sunucu hatası"
                SpeechRecognizer.ERROR_CLIENT -> "İstemci hatası"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni gerekli"
                else -> "Ses tanıma hatası ($error)"
            }
            Log.w(TAG, "SpeechRecognizer error: $error ($message)")

            // If continuous listening is desired and it was just a timeout or no-match, restart smoothly
            if (shouldKeepListening && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                mainHandler.postDelayed({
                    if (shouldKeepListening) {
                        startInternalRecognizer()
                    }
                }, 300L)
            } else {
                listener?.onError(error, message)
                if (shouldKeepListening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    mainHandler.postDelayed({
                        if (shouldKeepListening) {
                            startInternalRecognizer()
                        }
                    }, 1000L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()
            if (!recognizedText.isNullOrBlank()) {
                Log.d(TAG, "Final speech result: $recognizedText")
                listener?.onFinalResult(recognizedText)
            }

            // Loop back if continuous listening is on
            if (shouldKeepListening) {
                mainHandler.postDelayed({
                    if (shouldKeepListening) {
                        startInternalRecognizer()
                    }
                }, 200L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim()
            if (!partialText.isNullOrBlank()) {
                listener?.onPartialResult(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun destroy() {
        stop()
    }
}
