package com.aegissoft.qcybutmod.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitTestAnalyzerTest {

    @Test fun identicalRecordingMeansPerfectFit() {
        val src = ShortArray(16384) { (kotlin.math.sin(2 * kotlin.math.PI * 50 * it / 16000.0) * 12000).toInt().toShort() }
        assertTrue(FitTestAnalyzer.isGoodFit(FitTestAnalyzer.analyze(src, src)))   // H=1 → 0 dB
    }

    @Test fun halfAmplitudeMeansPoorFit() {
        val src = ShortArray(16384) { (kotlin.math.sin(2 * kotlin.math.PI * 50 * it / 16000.0) * 12000).toInt().toShort() }
        val rec = ShortArray(16384) { (src[it] / 2).toShort() }                    // H=0.5 → -6.02 dB ≥ 5.0
        assertFalse(FitTestAnalyzer.isGoodFit(FitTestAnalyzer.analyze(src, rec)))
    }

    @Test fun decodeWavSkipsHeader() {
        // 44-byte RIFF/WAVE header + 'data' + 4 örnek (0x0100, 0x0200) → 2 short
        val wav = buildMinimalWav(byteArrayOf(0x00, 0x01, 0x00, 0x02))
        assertArrayEquals(shortArrayOf(256, 512), FitTestAnalyzer.decodeWavPcm16(wav))
    }

    @Test fun kfftMatchesNaiveDft() {
        val x = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val n = x.size
        val pr = x.copyOf()
        val pi = DoubleArray(n)
        val fr = DoubleArray(n)
        val fi = DoubleArray(n)
        FitTestAnalyzer.kfft(pr, pi, n, n.countTrailingZeroBits(), fr, fi)
        for (k in 0 until n) {
            var re = 0.0
            var im = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * kotlin.math.PI * k * t / n
                re += x[t] * kotlin.math.cos(angle)
                im += x[t] * kotlin.math.sin(angle)
            }
            assertEquals(re, fr[k], 1e-7)
            assertEquals(im, fi[k], 1e-7)
        }
    }

    private fun buildMinimalWav(payload: ByteArray): ByteArray {
        val header = ByteArray(44)
        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        putLe32(header, 4, 36 + payload.size)
        "WAVE".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> header[12 + i] = c.code.toByte() }
        putLe32(header, 16, 16)
        putLe16(header, 20, 1)
        putLe16(header, 22, 1)
        putLe32(header, 24, 16000)
        putLe32(header, 28, 32000)
        putLe16(header, 32, 2)
        putLe16(header, 34, 16)
        "data".forEachIndexed { i, c -> header[36 + i] = c.code.toByte() }
        putLe32(header, 40, payload.size)
        return header + payload
    }

    private fun putLe16(a: ByteArray, off: Int, v: Int) {
        a[off] = (v and 0xFF).toByte()
        a[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun putLe32(a: ByteArray, off: Int, v: Int) {
        a[off] = (v and 0xFF).toByte()
        a[off + 1] = ((v shr 8) and 0xFF).toByte()
        a[off + 2] = ((v shr 16) and 0xFF).toByte()
        a[off + 3] = ((v shr 24) and 0xFF).toByte()
    }
}
