package com.karelisio.canif.tools.qr

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlin.math.hypot

/** A QR code as a square boolean grid (no quiet zone). */
class QrMatrix(val size: Int, private val bits: BooleanArray, val version: Int, val ecLevel: String) {
    operator fun get(x: Int, y: Int) = bits[y * size + x]

    /** True for modules belonging to one of the three finder patterns ("eyes"). */
    fun inEye(x: Int, y: Int): Boolean =
        (x < 7 && y < 7) || (x >= size - 7 && y < 7) || (x < 7 && y >= size - 7)

    companion object {
        fun encode(text: String, level: ErrorCorrectionLevel = ErrorCorrectionLevel.M): QrMatrix {
            val qr = Encoder.encode(text, level, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
            val m = qr.matrix
            val n = m.width
            val bits = BooleanArray(n * n) { i -> m.get(i % n, i / n).toInt() == 1 }
            return QrMatrix(n, bits, qr.version.versionNumber, level.name)
        }
    }
}

/** Colors of a rendered QR code. */
data class QrColors(val background: Color, val modules: Color, val eyes: Color)

/** Data modules with their distance to the center, precomputed once per matrix. */
private class QrModules(m: QrMatrix) {
    val xs: IntArray
    val ys: IntArray
    val dist: FloatArray

    init {
        val lx = ArrayList<Int>(); val ly = ArrayList<Int>(); val ld = ArrayList<Float>()
        val c = (m.size - 1) / 2f
        val maxD = hypot(c, c)
        for (y in 0 until m.size) for (x in 0 until m.size) {
            if (m[x, y] && !m.inEye(x, y)) {
                lx += x; ly += y; ld += hypot(x - c, y - c) / maxD
            }
        }
        xs = lx.toIntArray(); ys = ly.toIntArray(); dist = ld.toFloatArray()
    }
}

/**
 * Draws [matrix] with rounded modules and soft eyes. Modules grow from the center outwards
 * every time the matrix changes.
 */
@Composable
fun QrCanvas(matrix: QrMatrix, colors: QrColors, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val modules = remember(matrix) { QrModules(matrix) }
    val appear = remember(matrix) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(matrix) {
        if (!reduce) appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow))
    }
    Canvas(modifier) {
        drawQr(matrix, modules, colors, appear.value)
    }
}

private fun DrawScope.drawQr(m: QrMatrix, mods: QrModules, colors: QrColors, p: Float) {
    val quiet = 2f
    val cell = size.minDimension / (m.size + quiet * 2)
    val origin = Offset((size.width - cell * m.size) / 2, (size.height - cell * m.size) / 2)
    val dot = cell * 0.92f
    for (i in mods.xs.indices) {
        val local = ((p * 1.7f) - mods.dist[i] * 0.7f).coerceIn(0f, 1f)
        if (local <= 0f) continue
        val s = dot * local
        val cx = origin.x + (mods.xs[i] + 0.5f) * cell
        val cy = origin.y + (mods.ys[i] + 0.5f) * cell
        drawRoundRect(
            colors.modules,
            topLeft = Offset(cx - s / 2, cy - s / 2),
            size = Size(s, s),
            cornerRadius = CornerRadius(s * 0.4f),
        )
    }
    val eyeP = (p * 1.4f).coerceIn(0f, 1f)
    if (eyeP <= 0f) return
    for ((ex, ey) in listOf(0 to 0, m.size - 7 to 0, 0 to m.size - 7)) {
        val cx = origin.x + (ex + 3.5f) * cell
        val cy = origin.y + (ey + 3.5f) * cell
        val outer = 6f * cell * eyeP
        drawRoundRect(
            colors.eyes,
            topLeft = Offset(cx - outer / 2, cy - outer / 2),
            size = Size(outer, outer),
            cornerRadius = CornerRadius(outer * 0.32f),
            style = Stroke(width = cell * eyeP),
        )
        val inner = 3f * cell * eyeP
        drawRoundRect(
            colors.eyes,
            topLeft = Offset(cx - inner / 2, cy - inner / 2),
            size = Size(inner, inner),
            cornerRadius = CornerRadius(inner * 0.34f),
        )
    }
}

/** High resolution PNG-ready bitmap with a 4-module quiet zone. */
fun renderQrBitmap(m: QrMatrix, colors: QrColors, px: Int = 1536): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val quiet = 4
    val cell = px.toFloat() / (m.size + quiet * 2)
    val o = (px - cell * m.size) / 2
    canvas.drawColor(colors.background.toArgb())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.modules.toArgb() }
    val dot = cell * 0.92f
    val r = RectF()
    for (y in 0 until m.size) for (x in 0 until m.size) {
        if (!m[x, y] || m.inEye(x, y)) continue
        val cx = o + (x + 0.5f) * cell
        val cy = o + (y + 0.5f) * cell
        r.set(cx - dot / 2, cy - dot / 2, cx + dot / 2, cy + dot / 2)
        canvas.drawRoundRect(r, dot * 0.4f, dot * 0.4f, paint)
    }
    paint.color = colors.eyes.toArgb()
    for ((ex, ey) in listOf(0 to 0, m.size - 7 to 0, 0 to m.size - 7)) {
        val cx = o + (ex + 3.5f) * cell
        val cy = o + (ey + 3.5f) * cell
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = cell
        val outer = 6f * cell
        r.set(cx - outer / 2, cy - outer / 2, cx + outer / 2, cy + outer / 2)
        canvas.drawRoundRect(r, outer * 0.32f, outer * 0.32f, paint)
        paint.style = Paint.Style.FILL
        val inner = 3f * cell
        r.set(cx - inner / 2, cy - inner / 2, cx + inner / 2, cy + inner / 2)
        canvas.drawRoundRect(r, inner * 0.34f, inner * 0.34f, paint)
    }
    return bmp
}
