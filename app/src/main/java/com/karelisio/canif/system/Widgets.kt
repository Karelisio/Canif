package com.karelisio.canif.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.karelisio.canif.CanifApp
import com.karelisio.canif.MainActivity
import com.karelisio.canif.R
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.Tool
import com.karelisio.canif.core.tools.ToolRegistry
import com.karelisio.canif.ui.theme.SteelDark
import com.karelisio.canif.ui.theme.SteelLight
import kotlinx.coroutines.launch

private val fallbackColors = ColorProviders(light = SteelLight, dark = SteelDark)

/** Dynamic colors on Android 12+, the steel & copper palette before. */
@Composable
private fun CanifGlanceTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= 31) GlanceTheme(content = content) else GlanceTheme(colors = fallbackColors, content = content)
}

private fun GlanceModifier.widgetShape(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= 31) cornerRadius(android.R.dimen.system_app_widget_background_radius) else cornerRadius(24.dp)

object Widgets {
    fun refreshFavorites(context: Context) {
        CanifApp.instance.appScope.launch { runCatching { FavoritesWidget().updateAll(context) } }
    }

    fun refreshLamp(context: Context) {
        CanifApp.instance.appScope.launch { runCatching { LampWidget().updateAll(context) } }
    }

    /** White glyph on transparent background, tinted by Glance. */
    fun glyph(icon: ImageVector, sizePx: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(sizePx / icon.viewportWidth, sizePx / icon.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        fun draw(node: VectorNode) {
            when (node) {
                is VectorGroup -> node.forEach { draw(it) }
                is VectorPath -> canvas.drawPath(node.pathData.toPath().asAndroidPath(), paint)
            }
        }
        draw(icon.root)
        return bmp
    }
}

/** Resizable grid of favorite tools. */
class FavoritesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CanifApp
        val tools = app.db.favorites().all().mapNotNull { ToolRegistry.get(it.toolId) }
        val glyphs = tools.associate { it.id to Widgets.glyph(it.icon) }
        provideContent { CanifGlanceTheme { FavoritesContent(context, tools, glyphs) } }
    }
}

@Composable
private fun FavoritesContent(context: Context, tools: List<Tool>, glyphs: Map<String, Bitmap>) {
    val size = LocalSize.current
    val cell = 72.dp
    val columns = ((size.width.value - 16) / cell.value).toInt().coerceAtLeast(1)
    val rows = ((size.height.value - 44) / 76f).toInt().coerceAtLeast(1)
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .widgetShape()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(10.dp),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp).clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Canif", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurface))
        }
        if (tools.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    tr("Ajoutez des favoris dans Canif", "Add favorites in Canif"),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center),
                )
            }
            return@Column
        }
        tools.take(columns * rows).chunked(columns).forEach { line ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                line.forEach { tool ->
                    Column(
                        GlanceModifier
                            .defaultWeight()
                            .cornerRadius(16.dp)
                            .clickable(actionStartActivity(Shortcuts.toolIntent(context, tool.id)))
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            GlanceModifier.size(44.dp).cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            glyphs[tool.id]?.let {
                                Image(
                                    ImageProvider(it), tool.name.text,
                                    GlanceModifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                                )
                            }
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            tool.name.text,
                            maxLines = 1,
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface, textAlign = TextAlign.Center),
                        )
                    }
                }
                repeat(columns - line.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}

/** 1×1 flashlight toggle. */
class LampWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        TorchControl.observe(context.applicationContext)
        val on = TorchControl.isOn.value
        provideContent {
            CanifGlanceTheme {
                Box(
                    GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .widgetShape()
                        .background(if (on) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
                        .clickable(actionRunCallback<ToggleLampAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            ImageProvider(R.drawable.tile_flashlight), tr("Lampe", "Flashlight"),
                            GlanceModifier.size(30.dp),
                            colorFilter = ColorFilter.tint(if (on) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer),
                        )
                        Spacer(GlanceModifier.width(0.dp).height(2.dp))
                        Text(
                            if (on) tr("Allumée", "On") else tr("Lampe", "Light"),
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (on) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer),
                        )
                    }
                }
            }
        }
    }
}

class ToggleLampAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TorchControl.observe(context.applicationContext)
        TorchControl.toggle(context)
        LampWidget().updateAll(context)
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

class LampWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LampWidget()
}
