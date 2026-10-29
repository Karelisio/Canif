package com.karelisio.canif.tools.audio

import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.Txt
import kotlin.math.ln
import kotlin.math.pow

/** Note names and equal temperament helpers. MIDI 69 = A4; octaves use scientific numbering (C4 = middle C). */
object Notes {
    private val FR_SHARP = arrayOf("Do", "Do♯", "Ré", "Ré♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
    private val FR_FLAT = arrayOf("Do", "Ré♭", "Ré", "Mi♭", "Mi", "Fa", "Sol♭", "Sol", "La♭", "La", "Si♭", "Si")
    private val EN_SHARP = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val EN_FLAT = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

    fun name(midi: Int, flats: Boolean = false): String {
        val pc = ((midi % 12) + 12) % 12
        return if (Lang.isFr) (if (flats) FR_FLAT else FR_SHARP)[pc] else (if (flats) EN_FLAT else EN_SHARP)[pc]
    }

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun full(midi: Int, flats: Boolean = false): String = name(midi, flats) + octave(midi)

    fun freq(midi: Double, a4: Double): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)

    fun midiOf(freq: Double, a4: Double): Double = 69.0 + 12.0 * ln(freq / a4) / ln(2.0)
}

/** A tuning preset; an empty [notes] list means chromatic mode. Strings are listed low to high. */
class Tuning(val id: String, val name: Txt, val notes: IntArray, val flats: Boolean = false)

val Tunings = listOf(
    Tuning("chromatic", Txt("Chromatique", "Chromatic"), intArrayOf()),
    Tuning("guitar", Txt("Guitare", "Guitar"), intArrayOf(40, 45, 50, 55, 59, 64)),
    Tuning("drop-d", Txt("Drop D (Ré)", "Drop D"), intArrayOf(38, 45, 50, 55, 59, 64)),
    Tuning("dadgad", Txt("DADGAD", "DADGAD"), intArrayOf(38, 45, 50, 55, 57, 62)),
    Tuning("open-g", Txt("Open G (Sol)", "Open G"), intArrayOf(38, 43, 50, 55, 59, 62)),
    Tuning("open-d", Txt("Open D (Ré)", "Open D"), intArrayOf(38, 45, 50, 54, 57, 62)),
    Tuning("half-down", Txt("½ ton plus bas", "Half step down"), intArrayOf(39, 44, 49, 54, 58, 63), flats = true),
    Tuning("bass4", Txt("Basse 4 cordes", "4-string bass"), intArrayOf(28, 33, 38, 43)),
    Tuning("bass5", Txt("Basse 5 cordes", "5-string bass"), intArrayOf(23, 28, 33, 38, 43)),
    Tuning("ukulele", Txt("Ukulélé", "Ukulele"), intArrayOf(67, 60, 64, 69)),
)
