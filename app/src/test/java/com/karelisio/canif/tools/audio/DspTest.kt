package com.karelisio.canif.tools.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    private fun signal(sr: Int, f: Double, n: Int, noise: Double = 0.0, phase0: Double = 0.3): FloatArray {
        val rnd = Random(1)
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            // Plucked-string-like spectrum: strong 2nd/3rd harmonics.
            val v = 0.5 * sin(2 * PI * f * t + phase0) + 0.4 * sin(2 * PI * 2 * f * t) + 0.25 * sin(2 * PI * 3 * f * t + 1.0) + 0.1 * sin(2 * PI * 4 * f * t)
            (0.3 * v + noise * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
    }

    private fun cents(a: Double, b: Double) = 1200 * ln(a / b) / ln(2.0)

    @Test
    fun yinIsAccurateAcrossTheRange() {
        for (sr in intArrayOf(44100, 48000)) {
            val yin = YinPitchDetector(sr, 4096)
            for (f in doubleArrayOf(30.87, 41.2, 82.41, 110.0, 146.83, 196.0, 246.94, 329.63, 440.0, 880.0, 1318.5)) {
                val clean = yin.detect(signal(sr, f, 4096))
                assertTrue("clean sr=$sr f=$f got=$clean (${cents(clean.toDouble(), f)} c)", abs(cents(clean.toDouble(), f)) < 0.2)
                // With ~30 dB SNR; the 5-string bass low B (1.3 period per window) is the hard case.
                val got = yin.detect(signal(sr, f, 4096, noise = 0.01))
                assertTrue("sr=$sr f=$f got=$got", got > 0)
                val tol = if (f < 40) 2.5 else 1.0
                assertTrue("sr=$sr f=$f got=$got (${cents(got.toDouble(), f)} c)", abs(cents(got.toDouble(), f)) < tol)
            }
        }
    }

    @Test
    fun yinRejectsNoise() {
        val rnd = Random(3)
        val yin = YinPitchDetector(48000, 4096)
        val x = FloatArray(4096) { (rnd.nextGaussian() * 0.1).toFloat() }
        assertEquals(-1f, yin.detect(x))
    }

    private fun gainDb(type: Weighting.Type, f: Double, sr: Int = 48000): Double {
        val w = Weighting(sr, type)
        val n = sr
        var e = 0.0
        for (i in 0 until n) {
            val y = w.process(sin(2 * PI * f * i / sr).toFloat())
            if (i > n / 2) e += y * y
        }
        val rms = sqrt(e / (n / 2 - 1))
        return 20 * log10(rms / sqrt(0.5))
    }

    @Test
    fun aWeightingMatchesIec() {
        assertEquals(0.0, gainDb(Weighting.Type.A, 1000.0), 0.1)
        assertEquals(-19.1, gainDb(Weighting.Type.A, 100.0), 0.5)
        assertEquals(-8.6, gainDb(Weighting.Type.A, 250.0), 0.5)
        assertEquals(1.2, gainDb(Weighting.Type.A, 2000.0), 0.4)
        assertEquals(0.0, gainDb(Weighting.Type.C, 1000.0), 0.1)
        assertEquals(0.0, gainDb(Weighting.Type.Z, 100.0), 0.2)
    }
}
