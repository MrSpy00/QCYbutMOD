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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import java.io.ByteArrayOutputStream

sealed class FitTestState {
    object Idle : FitTestState()
    object InProgress : FitTestState()
    data class Completed(
        val leftGood: Boolean,
        val rightGood: Boolean,
        val overallPassed: Boolean
    ) : FitTestState()
    data class Failed(val reason: String) : FitTestState()
}

class FitTestManager(
    private val context: Context,
    private val bleManager: BleConnectionManager
) {
    companion object {
        private const val TAG = "FitTestManager"
        private const val SAMPLE_RATE = 16000
    }

    private val _testState = MutableStateFlow<FitTestState>(FitTestState.Idle)
    val testState: StateFlow<FitTestState> = _testState.asStateFlow()

    private var testJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startTest(recoveryAttempted: Boolean = false) {
        if (_testState.value is FitTestState.InProgress) return

        val initialState = bleManager.deviceState.value
        if (!initialState.isConnected) {
            if (recoveryAttempted) {
                _testState.value = FitTestState.Failed(
                    "Kulaklık bağlı değil. Lütfen önce kulaklığınızı bağlayın ve kulağınıza takın."
                )
                AegisVoiceAssistant.getInstance(context).speak(
                    "Kulaklık bağlı değil. Lütfen önce kulaklığı bağlayın."
                )
                return
            }
            // Classic ACL can be alive while GATT dropped (buds on, brief link loss):
            // kick auto-reconnect and wait in scope (withTimeoutOrNull is suspend-only).
            // Bounded by recoveryAttempted - a second failure reports immediately.
            try {
                bleManager.reconnectSelectedDevice()
            } catch (e: Exception) {
                Log.w(TAG, "Reconnect kick failed", e)
            }
            testJob = scope.launch {
                runCatching {
                    withTimeoutOrNull(6_000L) {
                        bleManager.deviceState.map { it.isConnected }.first { it }
                    }
                }
                // recovered -> gate passes and test starts; timeout -> fail branch hits
                startTest(recoveryAttempted = true)
            }
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
                        val left = captureEar(wavPcm)
                        delay(500L)
                        val right = captureEar(wavPcm)
                        left to right
                    }
                }
                measurement = ears

                var leftGood = false
                var rightGood = false
                select<Unit> {
                    bleResult.onAwait { result ->
                        leftGood = result.leftEarGoodSeal
                        rightGood = result.rightEarGoodSeal
                        ears.cancel()
                    }
                    ears.onAwait { result ->
                        val (recLeft, recRight) = result.getOrThrow()
                        val analyzed = withContext(Dispatchers.IO) {
                            FitTestAnalyzer.isGoodFit(FitTestAnalyzer.analyze(wavPcm, recLeft)) to
                                FitTestAnalyzer.isGoodFit(FitTestAnalyzer.analyze(wavPcm, recRight))
                        }
                        leftGood = analyzed.first
                        rightGood = analyzed.second
                    }
                }

                val passed = leftGood && rightGood
                _testState.value = FitTestState.Completed(
                    leftGood = leftGood,
                    rightGood = rightGood,
                    overallPassed = passed
                )
                AegisVoiceAssistant.getInstance(context).speak(
                    if (passed) "Kulak uyum testi tamamlandı. Sol ve sağ kulaklıkta akustik yalıtım mükemmel."
                    else "Kulak uyum testi tamamlandı. Kulaklık silikon uçlarını kontrol edip tekrar deneyin."
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission missing", e)
                _testState.value = FitTestState.Failed(
                    "Mikrofon izni verilmedi. Akustik testi başlatmak için mikrofon izni gereklidir."
                )
                AegisVoiceAssistant.getInstance(context).speak(
                    "Mikrofon izni verilmedi. Lütfen izin verip tekrar deneyin."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fit test failed", e)
                _testState.value = FitTestState.Failed(e.localizedMessage ?: "Bilinmeyen hata")
            } finally {
                collector?.cancel()
                measurement?.cancel()
                if (modeSet) audioManager.mode = AudioManager.MODE_NORMAL
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                bleManager.stopFitTest()
            }
        }
    }

    private suspend fun captureEar(wavPcm: ShortArray): ShortArray {
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
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val count = bytes.size / 2
        return ShortArray(count) { i ->
            val p = i * 2
            (((bytes[p + 1].toInt() and 0xFF) shl 8) or (bytes[p].toInt() and 0xFF)).toShort()
        }
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
