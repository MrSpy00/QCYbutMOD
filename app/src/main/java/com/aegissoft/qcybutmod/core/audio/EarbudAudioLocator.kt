package com.aegissoft.qcybutmod.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

/**
 * Real Acoustic Earbud Locator.
 * Generates an alternating high-frequency chirp alarm directly into the connected
 * Bluetooth earbud's speaker via stereo AudioTrack with channel isolation (Left, Right, or Both).
 * Features progressive volume escalation (starts gentle at 20% and escalates to 100% full blast).
 */
object EarbudAudioLocator {
    private const val TAG = "EarbudAudioLocator"
    private const val SAMPLE_RATE = 44100
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _currentVolumeGain = MutableStateFlow(0f)
    val currentVolumeGain: StateFlow<Float> = _currentVolumeGain.asStateFlow()

    @Volatile
    var isBeeping: Boolean = false
        private set

    fun startBeep(left: Boolean, right: Boolean, context: android.content.Context? = null) {
        stopBeep()
        if (!left && !right) return

        // Ensure system media volume is audible for finding misplaced earbuds
        if (context != null) {
            try {
                val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (am != null) {
                    val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val curVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    if (curVol < (maxVol * 0.6f)) {
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (maxVol * 0.8f).toInt(), 0)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not adjust system media volume", e)
            }
        }

        isBeeping = true
        _currentVolumeGain.value = 0.20f
        playbackJob = scope.launch {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(SAMPLE_RATE / 4)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val freq1 = 2600.0 // Hz
                val freq2 = 3300.0 // Hz
                val pulseDurationSamples = (SAMPLE_RATE * 0.15).toInt() // 150ms tone
                val silenceSamples = (SAMPLE_RATE * 0.08).toInt() // 80ms gap

                var triadIteration = 0

                while (isActive && isBeeping) {
                    // Progressive Escalation: Starts at 20% (0.20f) for hearing protection,
                    // and ramps up by 15% per triad cycle up to 100% (1.0f) maximum acoustic pressure
                    val volumeGain = (0.20f + (triadIteration * 0.15f)).coerceAtMost(1.0f)
                    _currentVolumeGain.value = volumeGain
                    triadIteration++

                    // Beep 1
                    generateToneBurst(track, freq1, pulseDurationSamples, left, right, volumeGain)
                    if (!isActive || !isBeeping) break
                    generateSilence(track, silenceSamples)
                    if (!isActive || !isBeeping) break

                    // Beep 2
                    generateToneBurst(track, freq2, pulseDurationSamples, left, right, volumeGain)
                    if (!isActive || !isBeeping) break
                    generateSilence(track, silenceSamples)
                    if (!isActive || !isBeeping) break

                    // Beep 3
                    generateToneBurst(track, freq2, pulseDurationSamples, left, right, volumeGain)
                    if (!isActive || !isBeeping) break
                    generateSilence(track, (SAMPLE_RATE * 0.35).toInt()) // 350ms pause between burst triads
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack playback error", e)
            } finally {
                _currentVolumeGain.value = 0f
                cleanupTrack()
            }
        }
    }

    private fun generateToneBurst(
        track: AudioTrack,
        freq: Double,
        samples: Int,
        left: Boolean,
        right: Boolean,
        gain: Float
    ) {
        val pcm = ShortArray(samples * 2) // Stereo (L, R)
        val angularFreq = 2.0 * Math.PI * freq / SAMPLE_RATE

        for (i in 0 until samples) {
            // Apply a slight envelope fade-in/fade-out to avoid clicks
            val envelope = when {
                i < 100 -> i / 100f
                i > samples - 100 -> (samples - i) / 100f
                else -> 1.0f
            }
            val sampleVal = (sin(i * angularFreq) * Short.MAX_VALUE * gain * envelope).toInt().toShort()

            pcm[i * 2] = if (left) sampleVal else 0
            pcm[i * 2 + 1] = if (right) sampleVal else 0
        }

        track.write(pcm, 0, pcm.size)
    }

    private fun generateSilence(track: AudioTrack, samples: Int) {
        val pcm = ShortArray(samples * 2)
        track.write(pcm, 0, pcm.size)
    }

    fun stopBeep() {
        isBeeping = false
        _currentVolumeGain.value = 0f
        playbackJob?.cancel()
        playbackJob = null
        cleanupTrack()
    }

    private fun cleanupTrack() {
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }
}
