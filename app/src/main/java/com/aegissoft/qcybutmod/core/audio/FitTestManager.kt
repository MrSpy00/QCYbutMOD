package com.aegissoft.qcybutmod.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.aegissoft.qcybutmod.R
import com.aegissoft.qcybutmod.core.bluetooth.BleConnectionManager
import com.aegissoft.qcybutmod.core.bluetooth.QcyProtocol
import com.aegissoft.qcybutmod.core.models.QcyDeviceState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import java.io.ByteArrayOutputStream

enum class FitTestError { NOT_CONNECTED, PERMISSION_DENIED, SCO_UNAVAILABLE, LOW_CONFIDENCE, UNKNOWN }

sealed class FitTestState {
    object Idle : FitTestState()
    object InProgress : FitTestState()
    data class Completed(
        val leftGood: Boolean,
        val rightGood: Boolean,
        val overallPassed: Boolean
    ) : FitTestState()
    data class Failed(
        val reason: String,
        val kind: FitTestError = FitTestError.UNKNOWN
    ) : FitTestState()
}

class FitTestManager(
    private val context: Context,
    private val bleManager: BleConnectionManager
) {
    companion object {
        private const val TAG = "FitTestManager"
        private const val SAMPLE_RATE = 16000

        /** Bağlı değilse test başlatılamaz; JVM-test edilebilir. */
        fun gateFailureFor(state: QcyDeviceState): FitTestState.Failed? =
            if (state.isConnected) null
            else FitTestState.Failed(
                reason = "Kulaklık bağlı değil — akustik test başlatılamaz.",
                kind = FitTestError.NOT_CONNECTED
            )
    }

    private class ScoUnavailableException : Exception("Bluetooth SCO ses profili kullanılamıyor")

    private class LowConfidenceException : Exception("LOW_CONFIDENCE")

    private val _testState = MutableStateFlow<FitTestState>(FitTestState.Idle)
    val testState: StateFlow<FitTestState> = _testState.asStateFlow()

    private var testJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startTest() {
        if (_testState.value is FitTestState.InProgress) return

        val initialState = bleManager.deviceState.value
        gateFailureFor(initialState)?.let { failed ->
            _testState.value = failed
            AegisVoiceAssistant.getInstance(context).speak(
                "Kulaklık bağlı değil. Lütfen önce kulaklığı bağlayın."
            )
            return
        }

        _testState.value = FitTestState.InProgress

        testJob = scope.launch {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            var focusRequest: AudioFocusRequest? = null
            var modeSet = false
            var collector: Job? = null
            var measurement: Deferred<Result<Pair<ShortArray, ShortArray>>>? = null
            try {
                val bleResult = CompletableDeferred<QcyProtocol.FitTestResult>()
                collector = launch {
                    bleManager.fitTestResults.collect { result ->
                        if (result.isCompleted) bleResult.complete(result)
                    }
                }

                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioManager.requestAudioFocus(focusRequest)
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                modeSet = true

                bleManager.startFitTest()
                delay(3000L)

                val wavPcm = FitTestAnalyzer.decodeWavPcm16(
                    context.resources.openRawResource(R.raw.compactness).use { it.readBytes() }
                )

                val ears = async(Dispatchers.IO) {
                    runCatching {
                        val left = captureEar(wavPcm, audioManager)
                        delay(500L)
                        val right = captureEar(wavPcm, audioManager)
                        left to right
                    }
                }
                measurement = ears

                var leftGood = false
                var rightGood = false
                var lowConfidence = false
                select<Unit> {
                    bleResult.onAwait { result ->
                        leftGood = result.leftEarGoodSeal
                        rightGood = result.rightEarGoodSeal
                        ears.cancel()
                    }
                    ears.onAwait { result ->
                        val (recLeft, recRight) = result.getOrThrow()
                        if (hasSignal(recLeft) && hasSignal(recRight)) {
                            val analyzed = withContext(Dispatchers.IO) {
                                FitTestAnalyzer.isGoodFit(FitTestAnalyzer.analyze(wavPcm, recLeft)) to
                                    FitTestAnalyzer.isGoodFit(FitTestAnalyzer.analyze(wavPcm, recRight))
                            }
                            leftGood = analyzed.first
                            rightGood = analyzed.second
                        } else {
                            lowConfidence = true
                        }
                    }
                }
                if (lowConfidence) {
                    _testState.value = FitTestState.Failed(
                        reason = "Mikrofon zayıf sinyal aldı. Ortamın sessiz olduğundan ve kulaklıkların takılı olduğundan emin olup tekrar deneyin.",
                        kind = FitTestError.LOW_CONFIDENCE
                    )
                    recordAction(false, "zayıf sinyal")
                    AegisVoiceAssistant.getInstance(context).speak("Sonuç güvenilir değil. Tekrar deneyin.")
                } else {
                val passed = leftGood && rightGood
                _testState.value = FitTestState.Completed(
                    leftGood = leftGood,
                    rightGood = rightGood,
                    overallPassed = passed
                )
                recordAction(true, if (passed) "sol+sağ iyi" else "yalıtım zayıf")
                AegisVoiceAssistant.getInstance(context).speak(
                    if (passed) "Kulak uyum testi tamamlandı. Sol ve sağ kulaklıkta akustik yalıtım mükemmel."
                    else "Kulak uyum testi tamamlandı. Kulaklık silikon uçlarını kontrol edip tekrar deneyin."
                )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission missing", e)
                _testState.value = FitTestState.Failed(
                    reason = "Mikrofon izni verilmedi. Akustik testi başlatmak için mikrofon izni gereklidir.",
                    kind = FitTestError.PERMISSION_DENIED
                )
                recordAction(false, "mikrofon izni yok")
                AegisVoiceAssistant.getInstance(context).speak(
                    "Mikrofon izni verilmedi. Lütfen izin verip tekrar deneyin."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fit test failed", e)
                val kind = if (e is ScoUnavailableException) FitTestError.SCO_UNAVAILABLE else FitTestError.UNKNOWN
                _testState.value = FitTestState.Failed(
                    reason = e.localizedMessage ?: "Bilinmeyen hata",
                    kind = kind
                )
                recordAction(false, e.localizedMessage ?: "bilinmeyen hata")
            } finally {
                collector?.cancel()
                measurement?.cancel()
                runCatching { audioManager.stopBluetoothSco() }
                if (modeSet) audioManager.mode = AudioManager.MODE_NORMAL
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                bleManager.stopFitTest()
            }
        }
    }

    private suspend fun startSco(audioManager: AudioManager) {
        if (!audioManager.isBluetoothScoAvailableOffCall) throw ScoUnavailableException()
        if (audioManager.isBluetoothScoOn) audioManager.stopBluetoothSco()
        audioManager.startBluetoothSco()
        var attempts = 100
        while (!audioManager.isBluetoothScoOn) {
            val next = attempts - 1
            if (attempts <= 0) break
            delay(10)
            if (next == 50) audioManager.startBluetoothSco()
            attempts = next
        }
    }

    private suspend fun captureEar(wavPcm: ShortArray, audioManager: AudioManager): ShortArray {
        val recBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val trackBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (recBuf <= 0 || trackBuf <= 0) {
            throw IllegalStateException("Audio buffer init failed: rec=$recBuf track=$trackBuf")
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            recBuf * 2
        )
        val track = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            trackBuf,
            AudioTrack.MODE_STREAM
        )
        try {
            return coroutineScope {
                startSco(audioManager)
                val captured = ByteArrayOutputStream()
                val buf = ByteArray(recBuf)
                record.startRecording()
                val reader = launch(Dispatchers.IO) {
                    while (isActive) {
                        val r = record.read(buf, 0, buf.size)
                        if (r <= 0) break
                        captured.write(buf, 0, r)
                    }
                }
                try {
                    track.play()
                    var offset = 0
                    while (offset < wavPcm.size) {
                        val chunk = minOf(8192, wavPcm.size - offset)
                        val written = track.write(wavPcm, offset, chunk)
                        if (written <= 0) throw IllegalStateException("AudioTrack write failed: $written")
                        offset += written
                        ensureActive()
                    }
                } finally {
                    runCatching { track.stop() }
                }
                record.stop()
                reader.cancelAndJoin()
                bytesToShorts(captured.toByteArray())
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { track.stop() }
            track.release()
            runCatching { audioManager.stopBluetoothSco() }
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val count = bytes.size / 2
        return ShortArray(count) { i ->
            val p = i * 2
            (((bytes[p + 1].toInt() and 0xFF) shl 8) or (bytes[p].toInt() and 0xFF)).toShort()
        }
    }

    private fun recordAction(success: Boolean, detail: String) {
        runCatching { bleManager.recordFeatureAction("Uyum testi", success, detail) }
    }

    /** Boş/zayıf kayıt = güvenilmez sonuç (ör. SCO hiç başlamadıysa). */
    private fun hasSignal(pcm: ShortArray): Boolean {
        if (pcm.size < 800) return false
        var nonZero = 0
        for (s in pcm) if (s != 0.toShort()) nonZero++
        return nonZero > pcm.size / 100
    }

    fun cancelTest() {
        testJob?.cancel()
        bleManager.stopFitTest()
        _testState.value = FitTestState.Idle
    }

    fun reset() {
        cancelTest()
    }
}
