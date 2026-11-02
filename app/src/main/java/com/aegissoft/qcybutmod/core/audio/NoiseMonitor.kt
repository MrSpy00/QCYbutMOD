package com.aegissoft.qcybutmod.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.aegissoft.qcybutmod.core.utils.AegisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "NoiseMonitor"
private const val SAMPLE_RATE = 16_000
private const val CHUNK_SAMPLES = 1_600 // 100 ms @ 16 kHz mono
private const val HISTORY_SIZE = 100 // ≈10 sn
private const val MIN_DBFS = -90f

// rms→dBFS dönüşümü: sessizlikte -90 (insan kulağının ayırt edemeyeceği taban), tavan 0
internal fun rmsToDbfs(rms: Double): Float =
    (20.0 * log10(rms / 32_767.0)).coerceIn(MIN_DBFS.toDouble(), 0.0).toFloat()

/**
 * Telefon mikrofonuyla ortam gürültü seviyesini ölçer (yaklaşık, anlık dB + geçmiş).
 *
 * Not (PROJECT.md'ye): firmware ANC-mic ham akışını BLE'ye açmaz; gösterge
 * yalnızca telefon mikrofonuna dayandığından yaklaşık ölçüm verir.
 */
class NoiseMonitor {

    private val _dbfs = MutableStateFlow(MIN_DBFS)
    val dbfs: StateFlow<Float> = _dbfs.asStateFlow()

    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history: StateFlow<List<Float>> = _history.asStateFlow()

    private var scope: CoroutineScope? = null
    private var record: AudioRecord? = null

    @Synchronized
    fun start() {
        if (scope != null) return

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            AegisLogger.w(TAG, "AudioRecord desteklenmiyor (minBufferSize=$minBuf), start no-op")
            return
        }

        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, CHUNK_SAMPLES * 2))
                .build()
        } catch (t: Throwable) {
            AegisLogger.w(TAG, "AudioRecord oluşturulamadı (izin/mikrofon yok), start no-op", t)
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            AegisLogger.w(TAG, "AudioRecord başlatılamadı (izin/mikrofon yok), start no-op")
            try {
                rec.release()
            } catch (_: Throwable) {
            }
            return
        }

        try {
            rec.startRecording()
        } catch (t: Throwable) {
            AegisLogger.w(TAG, "startRecording başarısız (izin/mikrofon yok), start no-op", t)
            try {
                rec.release()
            } catch (_: Throwable) {
            }
            return
        }

        record = rec
        val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = monitorScope
        monitorScope.launch {
            val buffer = ShortArray(CHUNK_SAMPLES)
            try {
                // read() gerçek zamanlı blokladığı için döngü kendiliğinden ~100ms ritimde
                while (isActive) {
                    val read = rec.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        if (isActive) AegisLogger.w(TAG, "AudioRecord.read hata kodu: $read")
                        break
                    }
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = buffer[i].toDouble()
                        sumSq += s * s
                    }
                    val db = rmsToDbfs(sqrt(sumSq / read))
                    _dbfs.value = db
                    _history.value = (_history.value + db).takeLast(HISTORY_SIZE)
                }
            } finally {
                try {
                    rec.stop()
                } catch (_: Throwable) {
                }
                try {
                    rec.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        // Bloke haldeki read()'i serbest bırak; release döngünün finally'sinde yapılır
        val rec = record
        record = null
        try {
            rec?.stop()
        } catch (_: Throwable) {
        }
        _dbfs.value = MIN_DBFS
        _history.value = emptyList()
    }
}
