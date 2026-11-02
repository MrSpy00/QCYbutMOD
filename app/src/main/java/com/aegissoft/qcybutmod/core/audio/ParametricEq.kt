package com.aegissoft.qcybutmod.core.audio

import kotlin.math.*

data class EqBand(
    val frequencyHz: Int,
    var gainDb: Float, // -10.0f to +10.0f
    val qFactor: Float = 1.0f,
    val filterType: Int = 0 // 0 = Peaking EQ, 4 = High-Pass Filter (sys_eq), 255 = Disabled Slot
)

data class EqPreset(
    val id: String,
    val name: String,
    val gains: List<Float>
)

object ParametricEq {
    val STANDARD_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val PRESETS = listOf(
        EqPreset("flat", "Düz (Referans)", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("bass_boost", "Derin Bas", listOf(6f, 5f, 4f, 2f, 0f, -1f, 0f, 1f, 2f, 3f)),
        EqPreset("pop", "Pop / Vokal", listOf(-1f, 1f, 2f, 4f, 5f, 4f, 3f, 2f, 1f, 0f)),
        EqPreset("rock", "Rock / Canlı", listOf(5f, 4f, 2f, -1f, -2f, 0f, 2f, 4f, 5f, 6f)),
        EqPreset("vocal", "Net Konuşma", listOf(-3f, -2f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f)),
        EqPreset("electronic", "Elektronik / EDM", listOf(6f, 5f, 3f, 0f, -2f, 1f, 3f, 5f, 6f, 5f)),
        EqPreset("treble", "Tiz / Detay", listOf(-2f, -1f, 0f, 0f, 1f, 2f, 4f, 6f, 7f, 8f))
    )

    fun createDefaultBands(): List<EqBand> {
        return STANDARD_FREQUENCIES.map { freq ->
            EqBand(frequencyHz = freq, gainDb = 0f)
        }
    }

    /**
     * Serializes 10-band gains for QCY MCU.
     * Official Jerry/Wuqi protocol uses signed bytes or offset byte (gain + 10).
     */
    fun serializeGainsToBytes(bands: List<EqBand>): ByteArray {
        val bytes = ByteArray(bands.size)
        bands.forEachIndexed { index, band ->
            val clamped = band.gainDb.roundToInt().coerceIn(-10, 10)
            bytes[index] = clamped.toByte()
        }
        return bytes
    }

    /**
     * Calculates the frequency response curve magnitude in dB at target frequency.
     * Uses peaked Biquad filter frequency response formula.
     */
    fun calculateResponseAt(freqHz: Float, bands: List<EqBand>, sampleRate: Float = 48000f): Float {
        var totalDb = 0f
        for (band in bands) {
            val gain = band.gainDb
            if (abs(gain) < 0.1f) continue

            val f0 = band.frequencyHz.toFloat()
            val q = band.qFactor
            val w0 = 2f * Math.PI.toFloat() * f0 / sampleRate
            val alpha = sin(w0) / (2f * q)
            val a = 10f.pow(gain / 40f)

            // Biquad peaking EQ coefficients
            val b0 = 1f + alpha * a
            val b1 = -2f * cos(w0)
            val b2 = 1f - alpha * a
            val a0 = 1f + alpha / a
            val a1 = -2f * cos(w0)
            val a2 = 1f - alpha / a

            // Normalized coefficients
            val nb0 = b0 / a0
            val nb1 = b1 / a0
            val nb2 = b2 / a0
            val na1 = a1 / a0
            val na2 = a2 / a0

            // Digital frequency response evaluation: H(e^jw)
            val w = 2f * Math.PI.toFloat() * freqHz / sampleRate
            val cosW = cos(w)
            val cos2W = cos(2f * w)
            val sinW = sin(w)
            val sin2W = sin(2f * w)

            val numReal = nb0 + nb1 * cosW + nb2 * cos2W
            val numImag = -(nb1 * sinW + nb2 * sin2W)
            val denReal = 1f + na1 * cosW + na2 * cos2W
            val denImag = -(na1 * sinW + na2 * sin2W)

            val numMagSq = numReal * numReal + numImag * numImag
            val denMagSq = denReal * denReal + denImag * denImag

            val mag = sqrt(numMagSq / max(denMagSq, 1e-9f))
            totalDb += 20f * log10(max(mag, 1e-4f))
        }
        return totalDb.coerceIn(-18f, 18f)
    }

    /**
     * Generates 80 log-spaced frequency points between 20Hz and 20kHz
     * for plotting a smooth curve on the canvas.
     */
    fun generateCurvePoints(bands: List<EqBand>, pointCount: Int = 80): List<Pair<Float, Float>> {
        val minFreq = 20f
        val maxFreq = 20000f
        val logMin = log10(minFreq)
        val logMax = log10(maxFreq)
        val step = (logMax - logMin) / (pointCount - 1)

        val points = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until pointCount) {
            val freq = 10f.pow(logMin + i * step)
            val responseDb = calculateResponseAt(freq, bands)
            points.add(Pair(freq, responseDb))
        }
        return points
    }
}
