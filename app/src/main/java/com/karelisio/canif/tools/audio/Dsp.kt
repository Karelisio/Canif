package com.karelisio.canif.tools.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** In-place iterative radix-2 complex FFT (forward, e^-2πi), tables computed once. */
class Fft(val n: Int) {
    private val levels = Integer.numberOfTrailingZeros(n)
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val rev = IntArray(n) { Integer.reverse(it) ushr (32 - levels) }

    init {
        require(n >= 2 && n and (n - 1) == 0) { "FFT size must be a power of two" }
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val l = j + half
                    val c = cosT[k]
                    val s = sinT[k]
                    val tre = re[l] * c + im[l] * s
                    val tim = -re[l] * s + im[l] * c
                    re[l] = re[j] - tre
                    im[l] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    k += step
                }
                i += size
            }
            size *= 2
        }
    }
}

/**
 * YIN fundamental frequency estimator (de Cheveigné & Kawahara, 2002).
 *
 * The difference function d(τ) = Σ (x[j] − x[j+τ])² over an integration window W = size/2 is computed
 * as e(0) + e(τ) − 2·r(τ), where r is the cross-correlation obtained through one FFT (both real
 * signals packed into a single complex transform) and e the energies from a prefix sum of squares.
 * Then: cumulative mean normalized difference, absolute threshold, local minimum, parabolic
 * interpolation. Everything is allocated once; [detect] allocates nothing.
 */
class YinPitchDetector(
    val sampleRate: Int,
    val size: Int = 4096,
    minFreq: Float = 30f,
    maxFreq: Float = 1500f,
    private val threshold: Double = 0.12,
) {
    private val w = size / 2
    private val tauMin = floor(sampleRate / maxFreq).toInt().coerceAtLeast(2)
    private val tauMax = ceil(sampleRate / minFreq).toInt().coerceAtMost(w - 2)
    private val fft = Fft(size)
    private val zr = DoubleArray(size)
    private val zi = DoubleArray(size)
    private val pr = DoubleArray(size)
    private val pi = DoubleArray(size)
    private val prefix = DoubleArray(size + 1)
    private val d = DoubleArray(w)
    private val cmnd = DoubleArray(w)

    /** Aperiodicity of the last detection (0 = perfectly periodic, 1 = noise). */
    var aperiodicity = 1.0
        private set

    /** Returns the fundamental in Hz, or -1 when no reliable periodicity was found. [x] holds [size] samples. */
    fun detect(x: FloatArray): Float {
        val n = size
        // z = a + i·b with a = first half of the window (zero padded) and b = the whole window.
        var acc = 0.0
        prefix[0] = 0.0
        for (i in 0 until n) {
            val v = x[i].toDouble()
            zr[i] = if (i < w) v else 0.0
            zi[i] = v
            acc += v * v
            prefix[i + 1] = acc
        }
        fft.transform(zr, zi)
        // Unpack A = FFT(a), B = FFT(b), then P = conj(A)·B, stored conjugated for the inverse trick.
        for (k in 0 until n) {
            val m = (n - k) and (n - 1)
            val ar = (zr[k] + zr[m]) * 0.5
            val ai = (zi[k] - zi[m]) * 0.5
            val br = (zi[k] + zi[m]) * 0.5
            val bi = (zr[m] - zr[k]) * 0.5
            pr[k] = ar * br + ai * bi
            pi[k] = -(ar * bi - ai * br)
        }
        fft.transform(pr, pi) // = conj(IFFT(P))·n, real part is the correlation
        val inv = 1.0 / n
        val e0 = prefix[w]
        if (e0 < 1e-10) {
            aperiodicity = 1.0
            return -1f
        }
        d[0] = 0.0
        cmnd[0] = 1.0
        var running = 0.0
        val last = (tauMax + 1).coerceAtMost(w - 1)
        for (tau in 1..last) {
            val et = prefix[tau + w] - prefix[tau]
            val v = (e0 + et - 2.0 * pr[tau] * inv).coerceAtLeast(0.0)
            d[tau] = v
            running += v
            cmnd[tau] = if (running > 0) v * tau / running else 1.0
        }
        // Absolute threshold, then walk down to the local minimum.
        var tau = -1
        var t = tauMin
        while (t <= tauMax) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= tauMax && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            // No dip under the threshold: take the global minimum only if it is still fairly periodic.
            var best = tauMin
            for (i in tauMin..tauMax) if (cmnd[i] < cmnd[best]) best = i
            if (cmnd[best] > 0.25) {
                aperiodicity = cmnd[best]
                return -1f
            }
            tau = best
        }
        aperiodicity = cmnd[tau]
        // The normalization shifts the dip slightly at long periods: settle on the raw minimum first.
        while (tau + 1 < last && d[tau + 1] < d[tau]) tau++
        while (tau - 1 > 1 && d[tau - 1] < d[tau]) tau--
        // Parabolic interpolation on the raw difference function (less biased than on the normalized one).
        val s0 = d[tau - 1]
        val s1 = d[tau]
        val s2 = d[(tau + 1).coerceAtMost(last)]
        val denom = s0 - 2 * s1 + s2
        val shift = if (abs(denom) > 1e-12) (0.5 * (s0 - s2) / denom).coerceIn(-1.0, 1.0) else 0.0
        val period = tau + shift
        return if (period > 0) (sampleRate / period).toFloat() else -1f
    }
}

/**
 * Frequency weighting (A, C or Z) built from first-order bilinear sections pre-warped at the
 * standard IEC 61672 pole frequencies, normalized to 0 dB at 1 kHz. Not a class 1 meter, but close
 * enough for a phone.
 */
class Weighting(sampleRate: Int, val type: Type) {
    enum class Type { A, C, Z }

    private val b0: DoubleArray
    private val b1: DoubleArray
    private val a1: DoubleArray
    private val x1: DoubleArray
    private val y1: DoubleArray
    private val gain: Double

    init {
        val f1 = 20.598997
        val f2 = 107.65265
        val f3 = 737.86223
        val f4 = 12194.217
        // (frequency, isHighPass)
        val sections: List<Pair<Double, Boolean>> = when (type) {
            Type.A -> listOf(f1 to true, f1 to true, f2 to true, f3 to true, f4 to false, f4 to false)
            Type.C -> listOf(f1 to true, f1 to true, f4 to false, f4 to false)
            Type.Z -> listOf(10.0 to true) // DC blocker only
        }
        val n = sections.size
        b0 = DoubleArray(n); b1 = DoubleArray(n); a1 = DoubleArray(n); x1 = DoubleArray(n); y1 = DoubleArray(n)
        val nyq = sampleRate / 2.0
        sections.forEachIndexed { i, (fc, hp) ->
            val k = tan(PI * fc.coerceAtMost(nyq * 0.95) / sampleRate)
            a1[i] = (k - 1) / (k + 1)
            if (hp) {
                b0[i] = 1 / (1 + k); b1[i] = -1 / (1 + k)
            } else {
                b0[i] = k / (1 + k); b1[i] = k / (1 + k)
            }
        }
        // Normalize to unity gain at 1 kHz.
        val w = 2 * PI * 1000.0 / sampleRate
        val cw = cos(w)
        val sw = sin(w)
        var g = 1.0
        for (i in 0 until n) {
            val nr = b0[i] + b1[i] * cw
            val ni = -b1[i] * sw
            val dr = 1 + a1[i] * cw
            val di = -a1[i] * sw
            g *= hypot(nr, ni) / hypot(dr, di)
        }
        gain = if (g > 0) 1 / g else 1.0
    }

    fun reset() {
        x1.fill(0.0); y1.fill(0.0)
    }

    fun process(v: Float): Double {
        var s = v.toDouble()
        for (i in b0.indices) {
            val y = b0[i] * s + b1[i] * x1[i] - a1[i] * y1[i]
            x1[i] = s
            y1[i] = y
            s = y
        }
        return s * gain
    }
}
