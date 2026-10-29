package com.karelisio.canif.tools.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.i18n.tr
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Color conversions and naming, all pure functions on 0..1 floats (hue in degrees). */
object ColorMath {
    fun hex(c: Color): String = "#%02X%02X%02X".format(c.r8, c.g8, c.b8)

    fun parseHex(input: String): Color? {
        var s = input.trim().removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (s.length == 8) s = s.substring(2)
        if (s.length != 6 || !s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val v = s.toLong(16)
        return Color(((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    }

    /** RGB → HSL: h 0..360, s 0..1, l 0..1. */
    fun toHsl(c: Color): FloatArray {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d < 1e-6f) return floatArrayOf(0f, 0f, l)
        val s = d / (1 - abs(2 * l - 1))
        return floatArrayOf(hue(r, g, b, mx, d), s.coerceIn(0f, 1f), l)
    }

    fun toHsv(c: Color): FloatArray {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val d = mx - mn
        val s = if (mx <= 0f) 0f else d / mx
        return floatArrayOf(if (d < 1e-6f) 0f else hue(r, g, b, mx, d), s, mx)
    }

    private fun hue(r: Float, g: Float, b: Float, mx: Float, d: Float): Float {
        val h = when (mx) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60f
        return if (h < 0) h + 360f else h
    }

    fun fromHsl(h: Float, s: Float, l: Float): Color = Color.hsl(norm(h), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

    fun fromHsv(h: Float, s: Float, v: Float): Color = Color.hsv(norm(h), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

    fun norm(h: Float): Float = ((h % 360f) + 360f) % 360f

    fun rgbText(c: Color) = "rgb(${c.r8}, ${c.g8}, ${c.b8})"

    fun hslText(c: Color): String = toHsl(c).let { "hsl(${it[0].roundToInt()}, ${(it[1] * 100).roundToInt()}%, ${(it[2] * 100).roundToInt()}%)" }

    fun hsvText(c: Color): String = toHsv(c).let { "hsv(${it[0].roundToInt()}, ${(it[1] * 100).roundToInt()}%, ${(it[2] * 100).roundToInt()}%)" }

    /** Readable content color over an arbitrary user color. */
    fun onColor(c: Color): Color = if (c.luminance() > 0.42f) Color(0xDD000000) else Color(0xF2FFFFFF)

    // --- Naming -------------------------------------------------------------------------------

    private class Named(val name: Txt, val lab: FloatArray)

    private val names: List<Named> by lazy {
        listOf(
            Txt("Noir", "Black") to 0x000000, Txt("Blanc", "White") to 0xFFFFFF,
            Txt("Gris", "Grey") to 0x808080, Txt("Gris clair", "Light grey") to 0xC8C8C8, Txt("Anthracite", "Charcoal") to 0x3A3A3A,
            Txt("Rouge", "Red") to 0xE32626, Txt("Bordeaux", "Burgundy") to 0x7A1027, Txt("Rose", "Pink") to 0xF7A1C4,
            Txt("Fuchsia", "Magenta") to 0xE01CB4, Txt("Framboise", "Raspberry") to 0xC2185B, Txt("Saumon", "Salmon") to 0xFA8072,
            Txt("Corail", "Coral") to 0xFF7F50, Txt("Orange", "Orange") to 0xFF8C1A, Txt("Pêche", "Peach") to 0xFFCBA4,
            Txt("Jaune", "Yellow") to 0xFFE11F, Txt("Moutarde", "Mustard") to 0xC9A21B, Txt("Doré", "Gold") to 0xD4AF37,
            Txt("Beige", "Beige") to 0xE8DCC0, Txt("Crème", "Cream") to 0xFFF8DC, Txt("Marron", "Brown") to 0x7B4A2D,
            Txt("Chocolat", "Chocolate") to 0x4A2A18, Txt("Kaki", "Khaki") to 0x8A8450, Txt("Olive", "Olive") to 0x6B6B1E,
            Txt("Vert", "Green") to 0x2EA043, Txt("Vert clair", "Light green") to 0x9CD66B, Txt("Vert foncé", "Dark green") to 0x1B4D2A,
            Txt("Menthe", "Mint") to 0xA8E6C8, Txt("Turquoise", "Turquoise") to 0x30D5C8, Txt("Bleu canard", "Teal") to 0x0E7C7B,
            Txt("Cyan", "Cyan") to 0x18C6E6, Txt("Bleu ciel", "Sky blue") to 0x87CEEB, Txt("Bleu", "Blue") to 0x1E6FE0,
            Txt("Bleu marine", "Navy") to 0x13235B, Txt("Indigo", "Indigo") to 0x3F3DA8, Txt("Violet", "Purple") to 0x8A2BC2,
            Txt("Lavande", "Lavender") to 0xC3B1E1, Txt("Prune", "Plum") to 0x5E2750,
        ).map { (n, v) -> Named(n, lab(Color(0xFF000000.toInt() or v))) }
    }

    fun name(c: Color): String {
        val l = lab(c)
        return names.minBy { dist2(it.lab, l) }.name.text
    }

    private fun dist2(a: FloatArray, b: FloatArray): Float {
        val dl = a[0] - b[0]; val da = a[1] - b[1]; val db = a[2] - b[2]
        return dl * dl + da * da + db * db
    }

    /** sRGB → CIELAB (D65). */
    fun lab(c: Color): FloatArray {
        fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
        val x = (r * 0.4124f + g * 0.3576f + b * 0.1805f) / 0.95047f
        val y = r * 0.2126f + g * 0.7152f + b * 0.0722f
        val z = (r * 0.0193f + g * 0.1192f + b * 0.9505f) / 1.08883f
        fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
        val fx = f(x); val fy = f(y); val fz = f(z)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    // --- Harmonies -----------------------------------------------------------------------------

    enum class Harmony(val label: Txt) {
        COMPLEMENTARY(Txt("Complémentaire", "Complementary")),
        ANALOGOUS(Txt("Analogue", "Analogous")),
        TRIADIC(Txt("Triadique", "Triadic")),
        TETRADIC(Txt("Tétradique", "Tetradic")),
        MONOCHROME(Txt("Monochrome", "Monochrome")),
        SPLIT(Txt("Complémentaire divisée", "Split complementary")),
    }

    fun palette(base: Color, harmony: Harmony): List<Color> {
        val (h, s, l) = toHsl(base).let { Triple(it[0], it[1], it[2]) }
        fun c(dh: Float, dl: Float = 0f, ds: Float = 0f) = fromHsl(h + dh, (s + ds).coerceIn(0f, 1f), (l + dl).coerceIn(0.06f, 0.96f))
        val lift = if (l > 0.7f) -0.2f else 0.18f
        return when (harmony) {
            Harmony.COMPLEMENTARY -> listOf(c(0f, -0.16f), base, c(0f, lift), c(180f), c(180f, lift))
            Harmony.ANALOGOUS -> listOf(c(-40f), c(-20f), base, c(20f), c(40f))
            Harmony.TRIADIC -> listOf(base, c(120f), c(240f), c(0f, lift), c(120f, lift))
            Harmony.TETRADIC -> listOf(base, c(90f), c(180f), c(270f))
            Harmony.MONOCHROME -> {
                val levels = listOf(0.16f, 0.32f, 0.5f, 0.68f, 0.84f)
                val nearest = levels.indices.minBy { abs(levels[it] - l) }
                levels.mapIndexed { i, lv -> if (i == nearest) base else fromHsl(h, s, lv) }
            }
            Harmony.SPLIT -> listOf(base, c(150f), c(210f), c(0f, lift), c(150f, lift))
        }
    }

    fun random(rnd: kotlin.random.Random = kotlin.random.Random.Default): Color =
        fromHsl(rnd.nextFloat() * 360f, 0.45f + rnd.nextFloat() * 0.45f, 0.38f + rnd.nextFloat() * 0.3f)
}

val Color.r8: Int get() = (red * 255f).roundToInt().coerceIn(0, 255)
val Color.g8: Int get() = (green * 255f).roundToInt().coerceIn(0, 255)
val Color.b8: Int get() = (blue * 255f).roundToInt().coerceIn(0, 255)

/** Label used by copy feedback. */
fun copiedLabel(what: String) = tr("$what copié", "$what copied")
