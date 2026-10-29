package com.karelisio.canif.core.tools

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission

/** Tool families. Each one has its own Material shape and color role. */
enum class Category(val title: Txt, val icon: ImageVector) {
    CALC(Txt("Calculer et convertir", "Calculate & convert"), Icons.Rounded.Calculate),
    INSTRUMENTS(Txt("Instruments", "Instruments"), Icons.Rounded.Straighten),
    AUDIO(Txt("Audio", "Audio"), Icons.Rounded.GraphicEq),
    TEXT(Txt("Texte", "Text"), Icons.Rounded.TextFields),
    MEDIA(Txt("Images et PDF", "Images & PDF"), Icons.Rounded.Image),
    RANDOM(Txt("Hasard et jeux", "Chance & games"), Icons.Rounded.Casino),
    QR(Txt("QR codes", "QR codes"), Icons.Rounded.QrCode2),
    COLOR(Txt("Couleurs", "Colors"), Icons.Rounded.Palette),
    TIME(Txt("Temps", "Time"), Icons.Rounded.Timer),
    OUTDOOR(Txt("Extérieur et urgence", "Outdoor & emergency"), Icons.Rounded.Hiking);

    val shape: RoundedPolygon
        get() = when (this) {
            CALC -> MaterialShapes.Cookie4Sided
            INSTRUMENTS -> MaterialShapes.Gem
            AUDIO -> MaterialShapes.Sunny
            TEXT -> MaterialShapes.Arch
            MEDIA -> MaterialShapes.Clover4Leaf
            RANDOM -> MaterialShapes.Pentagon
            QR -> MaterialShapes.Cookie12Sided
            COLOR -> MaterialShapes.Flower
            TIME -> MaterialShapes.Cookie9Sided
            OUTDOOR -> MaterialShapes.SoftBurst
        }

    fun container(c: ColorScheme): Color = when (ordinal % 3) {
        0 -> c.primaryContainer
        1 -> c.tertiaryContainer
        else -> c.secondaryContainer
    }

    fun onContainer(c: ColorScheme): Color = when (ordinal % 3) {
        0 -> c.onPrimaryContainer
        1 -> c.onTertiaryContainer
        else -> c.onSecondaryContainer
    }
}

/** Content shared to Canif from another app (Share menu). */
@Immutable
data class SharedPayload(val text: String? = null, val uris: List<Uri> = emptyList(), val mimeType: String? = null)

/** What a tool screen receives. */
class ToolScope(
    val tool: Tool,
    val onBack: () -> Unit,
    val incoming: SharedPayload?,
    val navigate: (toolId: String) -> Unit,
)

/**
 * A tool of the toolbox. Adding a tool = adding one of these to its category list in [ToolRegistry].
 *
 * @param sensors sensors that must all be present, otherwise the tool is hidden.
 * @param features system features (PackageManager.FEATURE_*) that must all be present.
 * @param permissions runtime permissions the tool needs; a friendly gate is shown until granted.
 * @param fullscreen the tool draws edge to edge without the standard top bar.
 */
@Immutable
class Tool(
    val id: String,
    val name: Txt,
    val category: Category,
    val icon: ImageVector,
    val keywords: List<String> = emptyList(),
    val description: Txt? = null,
    val sensors: List<Int> = emptyList(),
    val features: List<String> = emptyList(),
    val permissions: List<AppPermission> = emptyList(),
    val fullscreen: Boolean = false,
    /** MIME patterns this tool can receive from the Share menu, e.g. text/plain or image/any. */
    val accepts: List<String> = emptyList(),
    val content: @Composable ToolScope.() -> Unit,
) {
    fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val pm = context.packageManager
        return sensors.all { sm.getDefaultSensor(it) != null } && features.all { pm.hasSystemFeature(it) }
    }

    fun accepts(mime: String?): Boolean {
        val m = mime ?: return false
        return accepts.any { p -> p == m || p == "*/*" || (p.endsWith("/*") && m.startsWith(p.removeSuffix("*"))) || (m.endsWith("/*") && p.startsWith(m.removeSuffix("*"))) }
    }

    companion object {
        const val SENSOR_ACCEL = Sensor.TYPE_ACCELEROMETER
        const val FEATURE_FLASH = PackageManager.FEATURE_CAMERA_FLASH
        const val FEATURE_CAMERA = PackageManager.FEATURE_CAMERA_ANY
    }
}
