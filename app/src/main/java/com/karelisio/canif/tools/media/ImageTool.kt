package com.karelisio.canif.tools.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.files.Bitmaps
import com.karelisio.canif.core.files.SharedFiles
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.StatBlock
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

enum class OutFormat(val label: String, val ext: String, val mime: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
}

enum class Resize(val label: () -> String, val percent: Float = 1f, val maxSide: Int = 0) {
    ORIGINAL({ tr("Original", "Original") }),
    P75({ "75 %" }, 0.75f),
    P50({ "50 %" }, 0.5f),
    P25({ "25 %" }, 0.25f),
    MAX2560({ "2560 px" }, maxSide = 2560),
    MAX1920({ "1920 px" }, maxSide = 1920),
    MAX1280({ "1280 px" }, maxSide = 1280),
}

data class ImageOptions(val format: OutFormat, val quality: Int, val resize: Resize)

object ImageProcessor {
    private fun originalMaxSide(context: Context, uri: Uri): Int {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        return maxOf(o.outWidth, o.outHeight).coerceAtLeast(1)
    }

    fun decodeFor(context: Context, uri: Uri, resize: Resize): Bitmap? {
        val side = originalMaxSide(context, uri)
        val target = when {
            resize.maxSide > 0 -> minOf(resize.maxSide, side)
            else -> (side * resize.percent).roundToInt()
        }.coerceIn(1, 8192)
        return Bitmaps.decode(context, uri, target)
    }

    fun encode(bitmap: Bitmap, o: ImageOptions): ByteArray {
        val src = if (o.format == OutFormat.JPG && bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            }
        } else bitmap
        val format = when (o.format) {
            OutFormat.JPG -> Bitmap.CompressFormat.JPEG
            OutFormat.PNG -> Bitmap.CompressFormat.PNG
            OutFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) {
                if (o.quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
        }
        return ByteArrayOutputStream().use { out -> src.compress(format, o.quality, out); out.toByteArray() }
    }
}

private class Preview(val before: ImageBitmap, val after: ImageBitmap, val beforeSize: Long, val afterSize: Long, val w: Int, val h: Int)

@Composable
fun ImageToolScreen(initial: List<Uri>) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val uris = remember { mutableStateListOf<Uri>().apply { addAll(initial.take(50)) } }
    var format by remember { mutableStateOf(OutFormat.JPG) }
    var quality by remember { mutableFloatStateOf(80f) }
    var resize by remember { mutableStateOf(Resize.ORIGINAL) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var totals by remember { mutableStateOf(0L to 0L) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { picked ->
        picked.forEach { if (it !in uris) uris += it }
        results = emptyList()
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val options = ImageOptions(format, quality.roundToInt(), resize)

    // Live before/after preview of the first image, debounced while sliders move.
    LaunchedEffect(uris.firstOrNull(), options) {
        val first = uris.firstOrNull() ?: run { preview = null; return@LaunchedEffect }
        delay(220)
        preview = withContext(Dispatchers.Default) {
            runCatching {
                val bmp = ImageProcessor.decodeFor(context, first, resize) ?: return@runCatching null
                val bytes = ImageProcessor.encode(bmp, options)
                val after = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val beforeDisp = Bitmaps.decode(context, first, 1400) ?: return@runCatching null
                Preview(beforeDisp.asImageBitmap(), after.asImageBitmap(), SharedFiles.size(context, first), bytes.size.toLong(), bmp.width, bmp.height)
            }.getOrNull()
        }
    }

    ToolColumn {
        if (uris.isEmpty()) {
            PickPrompt(
                tr("Compresser, redimensionner, convertir", "Compress, resize, convert"),
                tr("JPG, PNG ou WebP. Rien ne quitte votre téléphone.", "JPG, PNG or WebP. Nothing leaves your phone."),
                tr("Choisir des images", "Pick images"),
                pick,
            )
            return@ToolColumn
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("${uris.size} image(s)", "${uris.size} image(s)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = pick) { Icon(Icons.Rounded.AddPhotoAlternate, null); Text("  " + tr("Ajouter", "Add")) }
        }
        ThumbnailStrip(uris) { uris.removeAt(it); results = emptyList() }

        CanifCard {
            SectionTitle(tr("Format", "Format"))
            ChoiceRow(OutFormat.entries, format, { it.label }, { format = it; results = emptyList() })
            AnimatedVisibility(format != OutFormat.PNG) {
                androidx.compose.foundation.layout.Column {
                    SectionTitle(tr("Qualité : ${quality.roundToInt()}", "Quality: ${quality.roundToInt()}"))
                    Slider(quality, { if (it.roundToInt() / 5 != quality.roundToInt() / 5) haptics.tick(); quality = it; results = emptyList() }, valueRange = 10f..100f)
                }
            }
            SectionTitle(tr("Taille", "Size"))
            ChipChoice(Resize.entries, resize, { it.label() }, { resize = it; results = emptyList() })
        }

        AnimatedContent(preview, contentKey = { it != null }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "preview") { p ->
            if (p == null) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() }
            } else {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BeforeAfter(p.before, p.after)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBlock(tr("Avant", "Before"), SharedFiles.formatSize(p.beforeSize), Modifier.weight(1f))
                        StatBlock(tr("Après", "After"), SharedFiles.formatSize(p.afterSize), Modifier.weight(1f))
                        StatBlock(tr("Pixels", "Pixels"), "${p.w}×${p.h}", Modifier.weight(1f))
                    }
                }
            }
        }

        Button(
            onClick = {
                busy = true
                scope.launch {
                    val out = mutableListOf<File>()
                    var before = 0L
                    var after = 0L
                    withContext(Dispatchers.Default) {
                        uris.forEachIndexed { i, uri ->
                            val bmp = ImageProcessor.decodeFor(context, uri, resize) ?: return@forEachIndexed
                            val bytes = ImageProcessor.encode(bmp, options)
                            val f = SharedFiles.newFile(context, "Canif", format.ext, i + 1)
                            f.writeBytes(bytes)
                            out += f
                            before += SharedFiles.size(context, uri)
                            after += bytes.size
                        }
                    }
                    results = out
                    totals = before to after
                    busy = false
                    haptics.confirm()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularWavyProgressIndicator(Modifier.size(20.dp)) else Text(tr("Traiter ${uris.size} image(s)", "Process ${uris.size} image(s)"))
        }

        AnimatedVisibility(results.isNotEmpty()) {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (b, a) = totals
                val saved = if (b > 0) ((1 - a.toDouble() / b) * 100).roundToInt() else 0
                ResultCard(
                    tr("Poids gagné", "Space saved"),
                    if (saved >= 0) "−$saved %" else "+${-saved} %",
                    supporting = "${SharedFiles.formatSize(b)} → ${SharedFiles.formatSize(a)}",
                    copyable = false,
                )
                ResultActions(
                    onShare = { SharedFiles.share(context, results, format.mime) },
                    onSave = if (SharedFiles.canSaveToMediaStore) {
                        {
                            scope.launch {
                                val ok = results.all { SharedFiles.saveToMediaStore(context, it, format.mime) }
                                Toast.makeText(context, if (ok) tr("Enregistré dans Images/Canif", "Saved to Pictures/Canif") else tr("Échec de l'enregistrement", "Save failed"), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else null,
                )
            }
        }
    }
}

/** Draggable before/after comparison. */
@Composable
private fun BeforeAfter(before: ImageBitmap, after: ImageBitmap) {
    var split by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(before.width.toFloat() / before.height.coerceAtLeast(1))
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    split = (split + drag.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        Image(before, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Image(
            after, null,
            Modifier
                .fillMaxSize()
                .drawWithContent { clipRect(left = size.width * split) { this@drawWithContent.drawContent() } },
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .offset { IntOffset((widthPx * split).roundToInt() - 1, 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((widthPx * split).roundToInt() - with(density) { 18.dp.roundToPx() }, 0) }
                .size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.UnfoldMore, null, Modifier.rotate(90f), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Text(tr("Avant", "Before"), Modifier.align(Alignment.TopStart).padding(12.dp).chip(), style = MaterialTheme.typography.labelMedium)
        Text(tr("Après", "After"), Modifier.align(Alignment.TopEnd).padding(12.dp).chip(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Modifier.chip() = this
    .clip(CircleShape)
    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
    .padding(horizontal = 10.dp, vertical = 4.dp)
