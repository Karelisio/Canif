package com.karelisio.canif.tools.outdoor

import android.os.SystemClock
import kotlinx.coroutines.delay
import java.text.Normalizer

/** International Morse code (ITU) and timing helpers. */
object Morse {
    val table: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.",
        'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.",
        'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-",
        'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--", '/' to "-..-.",
        '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.", '$' to "...-..-", '@' to ".--.-.",
        'É' to "..-..", 'À' to ".--.-", 'È' to ".-..-", 'Ç' to "-.-..", 'Ñ' to "--.--", 'Ü' to "..--", 'Ö' to "---.", 'Ä' to ".-.-",
    )

    private val reverse: Map<String, Char> by lazy {
        table.entries.filter { it.key !in "ÉÀÈÇÑÜÖÄ" }.associate { it.value to it.key } +
            mapOf("..-.." to 'É', ".--.-" to 'À', ".-..-" to 'È')
    }

    /** One displayed unit: a character and its code ("" for unknown, " " for a word gap). */
    data class Item(val char: Char, val code: String) {
        val isSpace get() = char == ' '
    }

    fun encode(text: String): List<Item> {
        val out = ArrayList<Item>()
        for (raw in text.uppercase()) {
            when {
                raw.isWhitespace() -> if (out.isNotEmpty() && !out.last().isSpace) out += Item(' ', " ")
                table.containsKey(raw) -> out += Item(raw, table.getValue(raw))
                else -> {
                    val plain = Normalizer.normalize(raw.toString(), Normalizer.Form.NFD).firstOrNull() ?: continue
                    table[plain]?.let { out += Item(raw, it) }
                }
            }
        }
        while (out.isNotEmpty() && out.last().isSpace) out.removeAt(out.lastIndex)
        return out
    }

    /** True when the text looks like Morse itself (dots, dashes, slashes, spaces). */
    fun looksLikeMorse(text: String): Boolean =
        text.isNotBlank() && text.any { it == '.' || it == '-' || it == '·' || it == '—' } && text.all { it in ".-·—_/| \n\t" }

    fun decode(text: String): String = text
        .replace('·', '.').replace('—', '-').replace('_', '-').replace('|', '/')
        .split('/').joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { (reverse[it] ?: '?').toString() }
        }.trim()

    fun pretty(code: String) = code.map { if (it == '.') '·' else '—' }.joinToString(" ")

    /** On/off segment of a playback, in units, tagged with the item it belongs to. */
    data class Segment(val on: Boolean, val units: Int, val item: Int)

    /**
     * Standard timing: dot 1, dash 3, gap inside a letter 1, between letters 3, between words 7.
     * [trailingGap] is appended at the end (word gap when looping).
     */
    fun timeline(items: List<Item>, trailingGap: Int = 0): List<Segment> {
        val seg = ArrayList<Segment>()
        items.forEachIndexed { i, item ->
            if (item.isSpace) {
                // Replace the previous letter gap (3) by a word gap (7).
                if (seg.isNotEmpty() && !seg.last().on) seg[seg.lastIndex] = seg.last().copy(units = 7, item = i) else seg += Segment(false, 7, i)
                return@forEachIndexed
            }
            item.code.forEachIndexed { k, s ->
                seg += Segment(true, if (s == '.') 1 else 3, i)
                if (k < item.code.lastIndex) seg += Segment(false, 1, i)
            }
            if (i < items.lastIndex && !items[i + 1].isSpace) seg += Segment(false, 3, i)
        }
        if (trailingGap > 0) seg += Segment(false, trailingGap, items.lastIndex)
        return seg
    }

    /** Unit length in ms for a speed in words per minute (PARIS standard). */
    fun unitMs(wpm: Int) = 1200L / wpm.coerceIn(1, 60)

    /**
     * Plays [segments] once, drift-free, calling [onState] at every change with
     * (lit, index of the item being played; -1 at the end).
     */
    suspend fun play(segments: List<Segment>, unitMs: Long, onState: (lit: Boolean, item: Int) -> Unit) {
        val start = SystemClock.elapsedRealtime()
        var t = 0L
        for (s in segments) {
            onState(s.on, s.item)
            t += s.units * unitMs
            val wait = start + t - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
        }
        onState(false, -1)
    }
}
