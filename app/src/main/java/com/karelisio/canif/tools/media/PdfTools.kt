package com.karelisio.canif.tools.media

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.files.Bitmaps
import com.karelisio.canif.core.files.SharedFiles
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.ChipChoice
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.ResultCard
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private enum class PageSize { A4, LETTER, IMAGE }
private enum class Margin(val pt: Float) { NONE(0f), SMALL(18f), NORMAL(36f) }

private object Pdf {
    fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)

    fun imagesToPdf(context: Context, uris: List<Uri>, size: PageSize, margin: Margin, quality: Float, out: File): Int {
        init(context)
        PDDocument().use { doc ->
            uris.forEach { uri ->
                val bmp = Bitmaps.decode(context, uri, 2400) ?: return@forEach
                val landscape = bmp.width > bmp.height
                val base = when (size) {
                    PageSize.A4 -> PDRectangle.A4
                    PageSize.LETTER -> PDRectangle.LETTER
                    PageSize.IMAGE -> PDRectangle(bmp.width * 0.75f + margin.pt * 2, bmp.height * 0.75f + margin.pt * 2)
                }
                val rect = if (size != PageSize.IMAGE && landscape) PDRectangle(base.height, base.width) else base
                val page = PDPage(rect)
                doc.addPage(page)
                val image = JPEGFactory.createFromImage(doc, bmp, quality)
                val aw = rect.width - margin.pt * 2
                val ah = rect.height - margin.pt * 2
                val scale = minOf(aw / bmp.width, ah / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(image, (rect.width - w) / 2f, (rect.height - h) / 2f, w, h)
                }
                bmp.recycle()
            }
            doc.save(out)
            return doc.numberOfPages
        }
    }

    fun pageCount(context: Context, uri: Uri): Int? = runCatching {
        init(context)
        context.contentResolver.openInputStream(uri)?.use { input -> PDDocument.load(input).use { it.numberOfPages } }
    }.getOrNull()

    fun merge(context: Context, uris: List<Uri>, out: File) {
        init(context)
        val streams = uris.mapNotNull { context.contentResolver.openInputStream(it) }
        try {
            PDFMergerUtility().apply {
                streams.forEach { addSource(it) }
                destinationFileName = out.absolutePath
                mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            }
        } finally {
            streams.forEach { runCatching { it.close() } }
        }
    }
}

/** Reorderable list of files with move up / down / remove. */
@Composable
private fun FileOrderList(uris: MutableList<Uri>, supporting: (Uri) -> String?, leading: @Composable (Uri) -> Unit) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uris.forEachIndexed { i, uri ->
            key(uri.toString()) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.cascade(uri.toString(), i)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp).padding(start = 8.dp))
                        leading(uri)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(remember(uri) { SharedFiles.displayName(context, uri) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            supporting(uri)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { haptics.tick(); uris.add(i - 1, uris.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, tr("Monter", "Move up")) }
                        IconButton(onClick = { haptics.tick(); uris.add(i + 1, uris.removeAt(i)) }, enabled = i < uris.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, tr("Descendre", "Move down")) }
                        IconButton(onClick = { uris.removeAt(i) }) { Icon(Icons.Rounded.Close, tr("Retirer", "Remove")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfResult(file: File, pages: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResultCard(
            tr("PDF prêt", "PDF ready"),
            if (pages > 1) tr("$pages pages", "$pages pages") else tr("1 page", "1 page"),
            supporting = "${file.name} · ${SharedFiles.formatSize(file.length())}",
            copyable = false,
        )
        ResultActions(
            onShare = { SharedFiles.share(context, listOf(file), "application/pdf") },
            onSave = if (SharedFiles.canSaveToMediaStore) {
                {
                    scope.launch {
                        val ok = SharedFiles.saveToMediaStore(context, file, "application/pdf")
                        Toast.makeText(context, if (ok) tr("Enregistré dans Téléchargements/Canif", "Saved to Download/Canif") else tr("Échec de l'enregistrement", "Save failed"), Toast.LENGTH_SHORT).show()
                    }
                }
            } else null,
        )
    }
}

@Composable
fun ImagesToPdfScreen(initial: List<Uri>) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val uris = remember { mutableStateListOf<Uri>().apply { addAll(initial) } }
    var size by remember { mutableStateOf(PageSize.A4) }
    var margin by remember { mutableStateOf(Margin.SMALL) }
    var quality by remember { mutableFloatStateOf(85f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { picked ->
        picked.forEach { if (it !in uris) uris += it }
        result = null
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolColumn {
        if (uris.isEmpty()) {
            PickPrompt(
                tr("Vos images en un PDF", "Your images as one PDF"),
                tr("Une image par page, dans l'ordre choisi.", "One image per page, in the order you choose."),
                tr("Choisir des images", "Pick images"),
                pick,
            )
            return@ToolColumn
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("${uris.size} page(s)", "${uris.size} page(s)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = pick) { Icon(Icons.Rounded.Add, null); Text("  " + tr("Ajouter", "Add")) }
        }
        FileOrderList(uris, supporting = { null }) { uri -> Thumbnail(uri, Modifier.size(52.dp)) }
        CanifCard {
            SectionTitle(tr("Format de page", "Page size"))
            ChoiceRow(PageSize.entries, size, { when (it) { PageSize.A4 -> "A4"; PageSize.LETTER -> "Letter"; PageSize.IMAGE -> tr("Image", "Image") } }, { size = it; result = null })
            SectionTitle(tr("Marges", "Margins"))
            ChipChoice(Margin.entries, margin, { when (it) { Margin.NONE -> tr("Aucune", "None"); Margin.SMALL -> tr("Fines", "Thin"); Margin.NORMAL -> tr("Normales", "Normal") } }, { margin = it; result = null })
            SectionTitle(tr("Qualité des images : ${quality.roundToInt()}", "Image quality: ${quality.roundToInt()}"))
            Slider(quality, { quality = it; result = null }, valueRange = 40f..100f)
        }
        Button(
            onClick = {
                busy = true; error = false
                scope.launch {
                    val out = SharedFiles.newFile(context, "Canif", "pdf")
                    val pages = withContext(Dispatchers.Default) {
                        runCatching { Pdf.imagesToPdf(context, uris.toList(), size, margin, quality / 100f, out) }.getOrNull()
                    }
                    busy = false
                    if (pages != null) { result = out to pages; haptics.confirm() } else { error = true; haptics.reject() }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularWavyProgressIndicator(Modifier.size(20.dp)) else Text(tr("Créer le PDF", "Create PDF"))
        }
        if (error) Text(tr("Impossible de créer le PDF.", "Could not create the PDF."), color = MaterialTheme.colorScheme.error)
        AnimatedVisibility(result != null) { result?.let { (f, p) -> PdfResult(f, p) } }
    }
}

@Composable
fun PdfMergeScreen(initial: List<Uri>) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val uris = remember { mutableStateListOf<Uri>().apply { addAll(initial) } }
    val pages = remember { mutableStateMapOf<Uri, Int?>() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<File, Int>?>(null) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        picked.forEach { if (it !in uris) uris += it }
        result = null
    }
    val pick = { picker.launch(arrayOf("application/pdf")) }
    LaunchedEffect(uris.toList()) {
        uris.filter { it !in pages }.forEach { u -> pages[u] = withContext(Dispatchers.IO) { Pdf.pageCount(context, u) } }
    }

    ToolColumn {
        if (uris.isEmpty()) {
            PickPrompt(
                tr("Fusionner des PDF", "Merge PDFs"),
                tr("Choisissez au moins deux fichiers, puis ordonnez-les.", "Pick at least two files, then order them."),
                tr("Choisir des PDF", "Pick PDFs"),
                pick,
                icon = Icons.Rounded.PictureAsPdf,
            )
            return@ToolColumn
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val total = uris.sumOf { pages[it] ?: 0 }
            Text(tr("${uris.size} fichiers · $total pages", "${uris.size} files · $total pages"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = pick) { Icon(Icons.Rounded.Add, null); Text("  " + tr("Ajouter", "Add")) }
        }
        FileOrderList(
            uris,
            supporting = { u ->
                when (val n = pages[u]) {
                    null -> if (u in pages) tr("Illisible ou protégé", "Unreadable or protected") else "…"
                    else -> if (n > 1) tr("$n pages", "$n pages") else tr("1 page", "1 page")
                }
            },
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = {
                busy = true; error = false
                scope.launch {
                    val out = SharedFiles.newFile(context, "Canif", "pdf")
                    val ok = withContext(Dispatchers.IO) { runCatching { Pdf.merge(context, uris.toList(), out) }.isSuccess }
                    busy = false
                    if (ok) {
                        result = out to (withContext(Dispatchers.IO) { Pdf.pageCount(context, SharedFiles.uriFor(context, out)) } ?: 0)
                        haptics.confirm()
                    } else {
                        error = true; haptics.reject()
                    }
                }
            },
            enabled = !busy && uris.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularWavyProgressIndicator(Modifier.size(20.dp)) else Text(tr("Fusionner", "Merge"))
        }
        if (error) Text(tr("Échec de la fusion (fichier protégé ?).", "Merge failed (protected file?)."), color = MaterialTheme.colorScheme.error)
        AnimatedVisibility(result != null) { result?.let { (f, p) -> PdfResult(f, p) } }
    }
}
