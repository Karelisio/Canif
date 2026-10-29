package com.karelisio.canif.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.karelisio.canif.MainActivity
import com.karelisio.canif.core.tools.Tool
import com.karelisio.canif.core.tools.ToolRegistry

/** Pinned and dynamic launcher shortcuts to tools. */
object Shortcuts {
    fun toolUri(id: String): Uri = Uri.parse("canif://tool/$id")

    fun toolIntent(context: Context, id: String): Intent =
        Intent(Intent.ACTION_VIEW, toolUri(id), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun canPin(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    private fun info(context: Context, tool: Tool, rank: Int = 0) =
        ShortcutInfoCompat.Builder(context, "tool-${tool.id}")
            .setShortLabel(tool.name.text)
            .setLongLabel(tool.name.text)
            .setIcon(IconCompat.createWithAdaptiveBitmap(iconBitmap(context, tool.icon)))
            .setIntent(toolIntent(context, tool.id))
            .setRank(rank)
            .build()

    fun requestPin(context: Context, tool: Tool) {
        ShortcutManagerCompat.requestPinShortcut(context, info(context, tool), null)
    }

    /** Mirrors the first favorites as dynamic shortcuts (long-press on the launcher icon). */
    fun updateDynamic(context: Context, favoriteIds: List<String>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
        val list = favoriteIds.mapNotNull { ToolRegistry.get(it) }.take(max).mapIndexed { i, t -> info(context, t, i) }
        ShortcutManagerCompat.setDynamicShortcuts(context, list)
    }

    /** Renders a Material icon on a tinted adaptive-icon background (108dp canvas, 72dp safe zone). */
    fun iconBitmap(context: Context, icon: ImageVector, sizePx: Int = 216): Bitmap {
        val bg = if (Build.VERSION.SDK_INT >= 31) ContextCompat.getColor(context, android.R.color.system_accent1_100) else 0xFFCBE6FF.toInt()
        val fg = if (Build.VERSION.SDK_INT >= 31) ContextCompat.getColor(context, android.R.color.system_accent1_800) else 0xFF194966.toInt()
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; style = Paint.Style.FILL }
        val iconSize = sizePx * 0.40f
        canvas.translate((sizePx - iconSize) / 2f, (sizePx - iconSize) / 2f)
        canvas.scale(iconSize / icon.viewportWidth, iconSize / icon.viewportHeight)
        drawNode(canvas, icon.root, paint)
        return bmp
    }

    private fun drawNode(canvas: Canvas, node: VectorNode, paint: Paint) {
        when (node) {
            is VectorGroup -> node.forEach { drawNode(canvas, it, paint) }
            is VectorPath -> canvas.drawPath(node.pathData.toPath().asAndroidPath(), paint)
        }
    }
}
