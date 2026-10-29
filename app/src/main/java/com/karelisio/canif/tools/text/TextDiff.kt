package com.karelisio.canif.tools.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal enum class DiffOp { EQ, DEL, INS }
internal class DiffEdit(val op: DiffOp, val token: String)

private val WordTokens = Regex("\\s+|[\\p{L}\\p{N}_]+|[^\\s\\p{L}\\p{N}_]")

internal fun tokenizeWords(s: String): List<String> = WordTokens.findAll(s).map { it.value }.toList()
internal fun tokenizeLines(s: String): List<String> = if (s.isEmpty()) emptyList() else s.replace("\r\n", "\n").split('\n')

/**
 * Shortest edit script between two token lists. Common prefix/suffix are stripped, then a
 * full LCS table is used for small inputs and Myers' O((N+M)·D) algorithm for larger ones.
 */
internal fun diff(a: List<String>, b: List<String>): List<DiffEdit> {
    var pre = 0
    while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
    var suf = 0
    while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) suf++
    val ma = a.subList(pre, a.size - suf)
    val mb = b.subList(pre, b.size - suf)
    val out = ArrayList<DiffEdit>(a.size + b.size)
    for (i in 0 until pre) out += DiffEdit(DiffOp.EQ, a[i])
    out += when {
        ma.isEmpty() -> mb.map { DiffEdit(DiffOp.INS, it) }
        mb.isEmpty() -> ma.map { DiffEdit(DiffOp.DEL, it) }
        ma.size.toLong() * mb.size <= 4_000_000L -> lcs(ma, mb)
        else -> myers(ma, mb) ?: (ma.map { DiffEdit(DiffOp.DEL, it) } + mb.map { DiffEdit(DiffOp.INS, it) })
    }
    for (i in a.size - suf until a.size) out += DiffEdit(DiffOp.EQ, a[i])
    return out
}

private fun lcs(a: List<String>, b: List<String>): List<DiffEdit> {
    val n = a.size
    val m = b.size
    val w = m + 1
    // t[i][j] = LCS length of a[i..] and b[j..]
    val t = IntArray((n + 1) * w)
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
        t[i * w + j] = if (a[i] == b[j]) t[(i + 1) * w + j + 1] + 1 else maxOf(t[(i + 1) * w + j], t[i * w + j + 1])
    }
    val out = ArrayList<DiffEdit>(n + m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j] -> { out += DiffEdit(DiffOp.EQ, a[i]); i++; j++ }
            t[(i + 1) * w + j] >= t[i * w + j + 1] -> { out += DiffEdit(DiffOp.DEL, a[i]); i++ }
            else -> { out += DiffEdit(DiffOp.INS, b[j]); j++ }
        }
    }
    while (i < n) out += DiffEdit(DiffOp.DEL, a[i++])
    while (j < m) out += DiffEdit(DiffOp.INS, b[j++])
    return out
}

/** Myers greedy diff with a memory budget; returns null when the texts are too different. */
private fun myers(a: List<String>, b: List<String>): List<DiffEdit>? {
    val n = a.size
    val m = b.size
    val max = n + m
    val off = max + 1
    val v = IntArray(2 * max + 3)
    val trace = ArrayList<IntArray>()
    var budget = 6_000_000L
    for (d in 0..max) {
        // Only the band [-d-1, d+1] is needed for backtracking step d.
        val snap = v.copyOfRange(off - d - 1, off + d + 2)
        budget -= snap.size
        if (budget < 0) return null
        trace += snap
        var k = -d
        while (k <= d) {
            var x = if (k == -d || (k != d && v[off + k - 1] < v[off + k + 1])) v[off + k + 1] else v[off + k - 1] + 1
            var y = x - k
            while (x < n && y < m && a[x] == b[y]) { x++; y++ }
            v[off + k] = x
            if (x >= n && y >= m) return backtrack(a, b, trace)
            k += 2
        }
    }
    return null
}

private fun backtrack(a: List<String>, b: List<String>, trace: List<IntArray>): List<DiffEdit> {
    var x = a.size
    var y = b.size
    val out = ArrayList<DiffEdit>()
    for (d in trace.lastIndex downTo 0) {
        val band = trace[d]
        fun v(k: Int) = band[k + d + 1]
        val k = x - y
        val prevK = if (k == -d || (k != d && v(k - 1) < v(k + 1))) k + 1 else k - 1
        val prevX = v(prevK)
        val prevY = prevX - prevK
        while (x > prevX && y > prevY) { out += DiffEdit(DiffOp.EQ, a[x - 1]); x--; y-- }
        if (d > 0) {
            if (x == prevX) out += DiffEdit(DiffOp.INS, b[y - 1]) else out += DiffEdit(DiffOp.DEL, a[x - 1])
            x = prevX
            y = prevY
        }
    }
    out.reverse()
    return out
}

internal class DiffView(val styled: AnnotatedString, val plain: String, val added: Int, val removed: Int, val similarity: Int)

internal fun renderDiff(edits: List<DiffEdit>, lines: Boolean, c: ColorScheme): DiffView {
    val ins = SpanStyle(background = c.primaryContainer, color = c.onPrimaryContainer)
    val del = SpanStyle(background = c.errorContainer, color = c.onErrorContainer, textDecoration = TextDecoration.LineThrough)
    var added = 0
    var removed = 0
    var same = 0
    var total = 0
    val plain = StringBuilder()
    val styled = buildAnnotatedString {
        edits.forEach { e ->
            val counts = lines || e.token.isNotBlank()
            when (e.op) {
                DiffOp.EQ -> { same += e.token.length * 2; total += e.token.length * 2 }
                DiffOp.INS -> { if (counts) added++; total += e.token.length }
                DiffOp.DEL -> { if (counts) removed++; total += e.token.length }
            }
            if (lines) {
                val prefix = when (e.op) { DiffOp.EQ -> "  "; DiffOp.INS -> "+ "; DiffOp.DEL -> "− " }
                val line = prefix + e.token + "\n"
                plain.append(line.replace('−', '-'))
                when (e.op) {
                    DiffOp.EQ -> append(line)
                    DiffOp.INS -> withStyle(ins) { append(line) }
                    DiffOp.DEL -> withStyle(del) { append(line) }
                }
            } else {
                when (e.op) {
                    DiffOp.EQ -> { append(e.token); plain.append(e.token) }
                    DiffOp.INS -> { withStyle(ins) { append(e.token) }; plain.append("{+").append(e.token).append("+}") }
                    DiffOp.DEL -> { withStyle(del) { append(e.token) }; plain.append("[-").append(e.token).append("-]") }
                }
            }
        }
    }
    val sim = if (total == 0) 100 else (same * 100L / total).toInt()
    return DiffView(styled, plain.toString().trimEnd('\n'), added, removed, sim)
}

@Composable
internal fun ToolScope.TextDiffScreen() {
    var left by rememberSaveable { mutableStateOf(incoming?.text ?: "") }
    var right by rememberSaveable { mutableStateOf("") }
    var byLines by rememberSaveable { mutableStateOf(false) }
    var view by remember { mutableStateOf<DiffView?>(null) }
    var busy by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    LaunchedEffect(left, right, byLines, c) {
        delay(220) // debounce typing
        busy = true
        view = withContext(Dispatchers.Default) {
            val a = if (byLines) tokenizeLines(left) else tokenizeWords(left)
            val b = if (byLines) tokenizeLines(right) else tokenizeWords(right)
            renderDiff(diff(a, b), byLines, c)
        }
        busy = false
    }
    ToolColumn {
        TextInput(left, { left = it }, tr("Texte original", "Original text"), minHeight = 120, maxHeight = 260)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceRow(listOf(false, true), byLines, label = { if (it) tr("Lignes", "Lines") else tr("Mots", "Words") }, onSelect = { byLines = it }, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { haptics.click(); val t = left; left = right; right = t }) {
                Icon(Icons.Rounded.SwapVert, tr("Permuter", "Swap"))
            }
        }
        TextInput(right, { right = it }, tr("Texte modifié", "Modified text"), minHeight = 120, maxHeight = 260)
        val v = view
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatBlock(tr("Ajouts", "Added"), "+${v?.added ?: 0}", Modifier.weight(1f), c.primaryContainer)
            StatBlock(tr("Suppressions", "Removed"), "−${v?.removed ?: 0}", Modifier.weight(1f), c.errorContainer)
            StatBlock(tr("Similarité", "Similarity"), "${v?.similarity ?: 100} %", Modifier.weight(1f))
        }
        if (busy) LoadingIndicator(Modifier.size(40.dp).align(Alignment.CenterHorizontally))
        TextResult(
            v?.styled ?: AnnotatedString(""),
            caption = tr("Différences", "Differences"),
            mono = byLines,
        )
        ResultActions(v?.plain ?: "")
    }
}
