package com.aegissoft.qcybutmod.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aegissoft.qcybutmod.core.utils.AegisLogger
import java.util.Locale

class AegisVoiceAssistant private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AegisVoiceAssistant"

        @Volatile
        private var INSTANCE: AegisVoiceAssistant? = null

        fun getInstance(context: Context): AegisVoiceAssistant {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AegisVoiceAssistant(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var trSelectedVoice: android.speech.tts.Voice? = null
    private var phoneRouteUtteranceId: String? = null
    private var voiceInventoryLogged = false

    // Debounce guard to prevent speaking the exact same phrase within 1500ms
    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L

    val isEnabled: Boolean
        get() = VoiceSettings.getVoiceSource(context) == VoiceSource.AEGIS_TR

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioFocusRequest: AudioFocusRequest? = null

    private val speechAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(speechAudioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
            }
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val trLocale = Locale("tr", "TR")
            val langResult = tts?.setLanguage(trLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Turkish language not supported or missing data, falling back to default locale")
                tts?.setLanguage(Locale.getDefault())
            } else {
                // Pick the best available Turkish voice (highest quality = natural Google voices);
                // speakTranslation re-applies it for tr targets since setting language resets voice.
                selectTurkishVoice()
            }
            applySpeechSettings()
            tts?.setAudioAttributes(speechAudioAttributes)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Audio focus already acquired
                }

                override fun onDone(utteranceId: String?) {
                    restorePhoneRouteIfNeeded(utteranceId)
                    abandonFocusDelayed()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    restorePhoneRouteIfNeeded(utteranceId)
                    abandonFocusDelayed()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    restorePhoneRouteIfNeeded(utteranceId)
                    abandonFocusDelayed()
                }
            })

            isReady = true
            Log.i(TAG, "AegisVoiceAssistant initialized successfully")
        } else {
            Log.w(TAG, "TextToSpeech onInit failed with status: $status")
            isReady = false
        }
    }

    fun applySpeechSettings() {
        val rate = VoiceSettings.getTtsRate(context)
        tts?.setSpeechRate(rate)
        val gender = VoiceSettings.getVoiceGender(context)
        val hasFemale = hasFemaleTurkishVoice()
        tts?.setPitch(VoiceGenderResolver.pitchFor(gender, hasFemale))
        if (gender == "female" && !hasFemale) {
            AegisLogger.i(TAG, "female_missing_fallback: kadın sesi yok — perde 1.35 simülasyon uygulanıyor")
        }
        // Cinsiyet değişince ses restart beklemeden yeniden hedeflenir (idempotent)
        selectTurkishVoice()
    }

    /** Türkçe ses seçimi: onInit ve cinsiyet değişiminde aynı yerden çalışır, tekrar çağırmak güvenli. */
    private fun selectTurkishVoice() {
        try {
            val trVoices = tts?.voices?.filter { it.locale.language == "tr" } ?: emptyList()
            if (!voiceInventoryLogged) {
                voiceInventoryLogged = true
                AegisLogger.i(
                    TAG,
                    "Turkish voice inventory (${trVoices.size}): " +
                        trVoices.joinToString { "${it.name} quality=${it.quality}" }
                )
            }
            val gender = VoiceSettings.getVoiceGender(context)
            val pickedName = VoiceGenderResolver.pickVoiceName(
                trVoices.map { VoiceInfo(it.name, it.quality) },
                gender
            )
            // null (empty/no match) keeps the legacy max-quality fallback
            val best = trVoices.firstOrNull { it.name == pickedName }
                ?: trVoices.maxByOrNull { it.quality }
            if (best != null) {
                tts?.voice = best
                trSelectedVoice = best
                AegisLogger.i(TAG, "Turkish voice selected: ${best.name} quality=${best.quality} network=${best.isNetworkConnectionRequired}")
            } else {
                AegisLogger.i(TAG, "No explicit Turkish voice exposed by engine, keeping language default")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Turkish voice selection failed", e)
        }
    }

    fun hasFemaleTurkishVoice(): Boolean {
        val trVoices = tts?.voices?.filter { it.locale.language == "tr" } ?: emptyList()
        return VoiceGenderResolver.hasFemaleCandidate(trVoices.map { VoiceInfo(it.name, it.quality) })
    }

    private fun requestFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioFocus request warning", e)
        }
    }

    private fun abandonFocusDelayed() {
        mainHandler.postDelayed({
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(null)
                }
            } catch (_: Exception) {}
        }, 150L)
    }

    /**
     * Routes translation speech to the phone speaker while the headset stays connected.
     * Requires a communication-mode stream (VOICE_CALL) + communication device on API 31+;
     * falls back to the deprecated speakerphone flag below that.
     */
    private fun forcePhoneSpeaker(on: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
                    val speaker = audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null && audioManager.setCommunicationDevice(speaker)) {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        Log.i(TAG, "Phone route: communication device set to builtin speaker")
                    } else {
                        Log.w(TAG, "Phone route: setCommunicationDevice failed")
                    }
                } else if (phoneRouteUtteranceId != null || audioManager.communicationDevice != null) {
                    audioManager.clearCommunicationDevice()
                    audioManager.mode = AudioManager.MODE_NORMAL
                    Log.i(TAG, "Phone route: restored default audio routing")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            }
        } catch (e: Exception) {
            Log.w(TAG, "forcePhoneSpeaker($on) failed", e)
        }
    }

    private fun restorePhoneRouteIfNeeded(utteranceId: String?) {
        if (utteranceId != null && utteranceId == phoneRouteUtteranceId) {
            phoneRouteUtteranceId = null
            forcePhoneSpeaker(false)
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, ignoreDebounce: Boolean = false) {
        if (!isEnabled || !isReady || tts == null || text.isBlank()) return

        val now = System.currentTimeMillis()
        if (!ignoreDebounce && text.trim() == lastSpokenText && (now - lastSpokenTime) < 1500L) {
            Log.d(TAG, "Debounced duplicate speech: $text")
            return
        }
        lastSpokenText = text.trim()
        lastSpokenTime = now

        try {
            applySpeechSettings()
            requestFocus()
            val volume = VoiceSettings.getTtsVolume(context)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val utteranceId = "AEGIS_VOICE_${System.currentTimeMillis()}"
            tts?.speak(text.trim(), queueMode, params, utteranceId)
            Log.d(TAG, "Spoke: $text (vol=$volume)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to speak: $text", e)
            abandonFocusDelayed()
        }
    }

    fun testSpeech(text: String = "aegis sesli asistan hazır") {
        if (!isReady || tts == null) return
        try {
            applySpeechSettings()
            requestFocus()
            val volume = VoiceSettings.getTtsVolume(context)
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "TEST_SPEECH")
        } catch (e: Exception) {
            Log.w(TAG, "Test speech failed", e)
        }
    }

    /**
     * Speaks translation text in target language directly into headset stream.
     * Controlled by VoiceSettings.isTranslateTtsEnabled rather than general voice source.
     */
    fun speakTranslation(text: String, langCode: String = "tr") {
        val route = VoiceSettings.getTranslateTtsRoute(context)
        if (route == VoiceSettings.ROUTE_OFF || !isReady || tts == null || text.isBlank()) return
        try {
            applySpeechSettings()
            val targetLocale = if (langCode.contains("-")) {
                val parts = langCode.split("-")
                Locale(parts[0], parts[1])
            } else {
                Locale(langCode)
            }
            val avail = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (avail >= TextToSpeech.LANG_AVAILABLE) {
                tts?.language = targetLocale
                if (targetLocale.language == "tr" && trSelectedVoice != null) {
                    tts?.voice = trSelectedVoice // setting language resets voice to default
                }
            } else {
                tts?.language = Locale("tr", "TR")
            }

            requestFocus()
            val volume = VoiceSettings.getTtsVolume(context)
            val utteranceId = "AEGIS_TRANSLATE_${System.currentTimeMillis()}"
            val toPhone = route == VoiceSettings.ROUTE_PHONE
            if (toPhone) {
                phoneRouteUtteranceId = utteranceId
                forcePhoneSpeaker(true)
            } else if (phoneRouteUtteranceId != null) {
                val stale = phoneRouteUtteranceId
                phoneRouteUtteranceId = null
                forcePhoneSpeaker(false) // route switched back to headset mid-flight
                Log.d(TAG, "Cancelled pending phone route for $stale")
            }
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                // VOICE_CALL stream follows the communication device so it reaches the
                // phone speaker even while A2DP is connected to the headset.
                putInt(
                    TextToSpeech.Engine.KEY_PARAM_STREAM,
                    if (toPhone) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
                )
            }
            tts?.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            Log.d(TAG, "Spoke translation: $text ($langCode route=$route)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to speak translation: $text", e)
            abandonFocusDelayed()
        }
    }

    fun announceAnc(modeName: String) {
        speak(modeName)
    }

    fun announceFeature(featureName: String, enabled: Boolean) {
        speak(VoiceMessages.featureChanged(featureName, enabled))
    }

    fun announceConnection(modelName: String, batteryPercent: Int) {
        speak(VoiceMessages.connection(modelName, batteryPercent))
    }

    fun announceDisconnection(modelName: String) {
        speak(VoiceMessages.disconnection(modelName))
    }

    fun announceBatteryStatus(left: Int, right: Int, caseLevel: Int) {
        speak(VoiceMessages.battery(left, right, caseLevel))
    }

    fun announceKeyAction(earbud: String, gestureType: String, actionName: String) {
        speak(VoiceMessages.keyAction(earbud, gestureType, actionName))
    }

    fun shutdown() {
        try {
            abandonFocusDelayed()
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
