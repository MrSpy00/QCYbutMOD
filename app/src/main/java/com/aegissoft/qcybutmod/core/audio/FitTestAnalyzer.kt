package com.aegissoft.qcybutmod.core.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object FitTestAnalyzer {

    private const val MIN_SRC_MAGNITUDE = 1e-6

    fun isGoodFit(meanAbsDb: Double): Boolean = meanAbsDb < 5.0

    fun analyze(srcPcm: ShortArray, recPcm: ShortArray, sampleRate: Int = 16000): Double {
        val len = minOf(srcPcm.size, recPcm.size)
        if (len < 2) return Double.POSITIVE_INFINITY
        var n = 1
        while (n < len) n = n shl 1
        val nt = n.countTrailingZeroBits()
        val src = spectrum(srcPcm, len, n, nt)
        val rec = spectrum(recPcm, len, n, nt)
        val m = minOf(src.first.size, rec.first.size)
        val h = DoubleArray(m)
        for (i in 0 until m) {
            val sr = src.first[i]
            val si = src.second[i]
            val den = sr * sr + si * si
            if (sqrt(den) < MIN_SRC_MAGNITUDE) continue
            val rr = rec.first[i]
            val ri = rec.second[i]
            val hRe = (rr * sr + ri * si) / den
            val hIm = (ri * sr - rr * si) / den
            h[i] = sqrt(hRe * hRe + hIm * hIm)
        }
        val fLast = m * sampleRate.toDouble() / n
        val smoothed = smooth(h, fLast)
        var sum = 0.0
        var count = 0
        for (i in 0 until m) {
            val freq = (i + 1) * sampleRate.toDouble() / n
            if (freq > 20.0 && freq < 100.0) {
                count++
                // shortcut: official keeps a zero reference curve (defaultResult.array never written) - our flat-ref math matches it.
                sum += abs(20.0 * log10(smoothed[i]))
            }
        }
        return sum / count
    }

    fun decodeWavPcm16(data: ByteArray): ShortArray {
        var i = 0
        while (i + 8 <= data.size) {
            if (data[i] == 0x64.toByte() && data[i + 1] == 0x61.toByte() &&
                data[i + 2] == 0x74.toByte() && data[i + 3] == 0x61.toByte()
            ) {
                val start = i + 8
                val size = (data[i + 4].toInt() and 0xFF) or
                    ((data[i + 5].toInt() and 0xFF) shl 8) or
                    ((data[i + 6].toInt() and 0xFF) shl 16) or
                    ((data[i + 7].toInt() and 0xFF) shl 24)
                val end = if (size in 1..data.size - start) start + size else data.size
                return ShortArray((end - start) / 2) { k ->
                    val p = start + k * 2
                    (((data[p + 1].toInt() and 0xFF) shl 8) or (data[p].toInt() and 0xFF)).toShort()
                }
            }
            i++
        }
        return ShortArray(0)
    }

    internal fun kfft(
        pr: DoubleArray,
        pi: DoubleArray,
        n: Int,
        nt: Int,
        fr: DoubleArray,
        fi: DoubleArray
    ) {
        var idx = 0
        while (idx < n) {
            var rev = 0
            var v = idx
            repeat(nt) {
                rev = rev * 2 + (v - (v / 2) * 2)
                v /= 2
            }
            fr[idx] = pr[rev]
            fi[idx] = pi[rev]
            idx++
        }
        pr[0] = 1.0
        pi[0] = 0.0
        val w = 6.283185306 / n
        pr[1] = cos(w)
        pi[1] = -sin(w)
        for (r8 in 2 until n) {
            val r9 = r8 - 1
            val d2 = pr[r9]
            val d3 = pr[1]
            val d4 = d2 * d3
            val d5 = pi[r9]
            val d6 = pi[1]
            val d7 = d5 * d6
            pr[r8] = d4 - d7
            pi[r8] = ((d2 + d5) * (d3 + d6)) - d4 - d7
        }
        var r10 = 0
        while (r10 <= n - 2) {
            val d8 = fr[r10]
            val d9 = fi[r10]
            val r11 = r10 + 1
            fr[r10] = fr[r11] + d8
            fi[r10] = fi[r11] + d9
            fr[r11] = d8 - fr[r11]
            fi[r11] = d9 - fi[r11]
            r10 += 2
        }
        var r0 = n / 2
        var r12 = 2
        for (r13 in nt - 2 downTo 0) {
            r0 /= 2
            r12 *= 2
            var r14 = 0
            while (r14 <= (r0 - 1) * r12) {
                var r15 = 0
                while (r15 <= r12 / 2 - 1) {
                    val r17 = r0 * r15
                    val d10 = pr[r17]
                    val d13 = pi[r17]
                    val r18 = r14 + r15
                    val r19 = r12 / 2 + r18
                    val d11 = fr[r19]
                    val d14 = fi[r19]
                    val d12 = d10 * d11
                    val d15 = d13 * d14
                    val d16 = (d10 + d13) * (d11 + d14)
                    val d17 = d12 - d15
                    val d18 = d16 - d12 - d15
                    fr[r19] = fr[r18] - d17
                    fi[r19] = fi[r18] - d18
                    fr[r18] = fr[r18] + d17
                    fi[r18] = fi[r18] + d18
                    r15++
                }
                r14 += r12
            }
        }
        for (r1 in 0 until n) {
            pr[r1] = sqrt(fr[r1] * fr[r1] + fi[r1] * fi[r1])
        }
    }

    private fun spectrum(
        pcm: ShortArray,
        len: Int,
        n: Int,
        nt: Int
    ): Pair<DoubleArray, DoubleArray> {
        val pr = DoubleArray(n)
        val pi = DoubleArray(n)
        val fr = DoubleArray(n)
        val fi = DoubleArray(n)
        for (i in 0 until len) pr[i] = pcm[i].toDouble()
        kfft(pr, pi, n, nt, fr, fi)
        val m = n / 2
        val re = DoubleArray(m)
        val im = DoubleArray(m)
        for (i in 0 until m) {
            val bin = i + 1
            var r = fr[bin]
            var v = fi[bin]
            if (bin == m && n % 2 == 0) {
                r /= 2.0
                v /= 2.0
            }
            re[i] = r
            im[i] = v
        }
        return re to im
    }

    private fun smooth(arr: DoubleArray, d: Double): DoubleArray {
        val out = DoubleArray(arr.size)
        val df = d / arr.size
        for (i in arr.indices) {
            val d2 = (i + 1) * df
            val base = when {
                d2 > 1000.0 -> 2.0.pow(0.125)
                d2 > 100.0 -> 2.0.pow(1.0 / 6.0)
                else -> 2.0.pow(0.25)
            }
            var end = (base * d2 / df).toInt()
            var start = (d2 / base / df).toInt()
            if (start < 0) start = 0
            if (end >= arr.size) end = arr.size - 1
            var sum = 0.0
            for (j in start..end) sum += arr[j]
            out[i] = sum / (end + 1 - start)
        }
        return out
    }
}
