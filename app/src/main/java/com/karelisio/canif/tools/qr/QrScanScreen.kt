package com.karelisio.canif.tools.qr

import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.AnimatedCheck
import com.karelisio.canif.ui.components.MorphShape
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.cascade
import com.karelisio.canif.ui.components.copyToClipboard
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.components.shareText
import com.karelisio.canif.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ScanHit(val raw: String, val format: String, val serial: Int) {
    val content: QrContent = QrParser.parse(raw)
}

/** Feeds camera frames to ML Kit; stops at the first readable code until resumed. */
private class BarcodeAnalyzer(private val scanner: BarcodeScanner, private val onFound: (Barcode) -> Unit) : ImageAnalysis.Analyzer {
    @Volatile var paused = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (paused || media == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { list ->
                val hit = list.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (hit != null && !paused) {
                    paused = true
                    onFound(hit)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}

@Composable
fun ToolScope.QrScanScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
    }
    DisposableEffect(Unit) { onDispose { scanner.close() } }

    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var hit by remember { mutableStateOf<ScanHit?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    var showHistory by remember { mutableStateOf(false) }
    val found = remember { Animatable(0f) }
    var lastRaw by remember { mutableStateOf<String?>(null) }
    var lastAt by remember { mutableStateOf(0L) }

    // The analyzer lives as long as the screen; it forwards hits to the latest deliver() through this sink.
    val sink = remember { arrayOfNulls<(Barcode) -> Unit>(1) }
    val realAnalyzer = remember { BarcodeAnalyzer(scanner) { b -> b.pauseAndForward(sink) } }

    fun deliver(raw: String, format: String) {
        val now = System.currentTimeMillis()
        if (raw == lastRaw && now - lastAt < 2500 && hit == null) {
            realAnalyzer.paused = false
            return
        }
        haptics.confirm()
        serial++
        hit = ScanHit(raw, format, serial)
        QrHistory.add(raw, format)
        scope.launch { found.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
    }
    sink[0] = { b -> deliver(b.rawValue.orEmpty(), QrParser.formatName(b.format)) }

    fun dismissResult() {
        hit?.let { lastRaw = it.raw; lastAt = System.currentTimeMillis() }
        hit = null
        scope.launch {
            found.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
            delay(250)
            realAnalyzer.paused = false
        }
    }

    fun scanImage(uri: Uri) {
        realAnalyzer.paused = true
        val input = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (input == null) {
            realAnalyzer.paused = false
            return
        }
        scanner.process(input)
            .addOnSuccessListener { list ->
                val b = list.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (b != null) {
                    lastRaw = null
                    deliver(b.rawValue.orEmpty(), QrParser.formatName(b.format))
                } else {
                    haptics.reject()
                    Toast.makeText(context, tr("Aucun code trouvé dans l'image", "No code found in the image"), Toast.LENGTH_SHORT).show()
                    realAnalyzer.paused = false
                }
            }
            .addOnFailureListener { realAnalyzer.paused = false }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { scanImage(it) } }
    LaunchedEffect(incoming) { incoming?.uris?.firstOrNull()?.let { scanImage(it) } }

    Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).navigationBarsPadding()) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(c.surfaceContainerHighest),
        ) {
            CameraFeed(realAnalyzer, Modifier.fillMaxSize(), torch = torch, onFlashUnit = { hasFlash = it })
            Viewfinder(found = { found.value }, Modifier.fillMaxSize())
            FoundBadge(progress = { found.value }, Modifier.align(Alignment.Center))

            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                shape = CircleShape,
                color = c.surfaceContainerHigh.copy(alpha = 0.88f),
            ) {
                Text(
                    tr("Visez un QR code ou un code-barres", "Aim at a QR code or a barcode"),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraActionButton(Icons.Rounded.Image, tr("Scanner une image", "Scan an image"), {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                if (hasFlash) {
                    CameraToggleButton(torch, { torch = it }, Icons.Rounded.FlashlightOn, Icons.Rounded.FlashlightOff, tr("Lampe", "Torch"), size = 68.dp)
                }
                CameraActionButton(Icons.Rounded.History, tr("Historique", "History"), {
                    realAnalyzer.paused = true
                    showHistory = true
                })
            }
        }
    }

    hit?.let { h -> QrResultSheet(h.content, h.format, h.serial, onDismiss = { dismissResult() }) }

    if (showHistory) {
        QrHistorySheet(
            onDismiss = { showHistory = false; if (hit == null) realAnalyzer.paused = false },
            onOpen = { item ->
                showHistory = false
                serial++
                hit = ScanHit(item.raw, item.format, serial)
            },
        )
    }
}

/** Hands a detected code to the UI. */
private fun Barcode.pauseAndForward(sink: Array<((Barcode) -> Unit)?>) {
    sink[0]?.invoke(this)
}

@Composable
private fun Viewfinder(found: () -> Float, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "finder")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    val brackets = remember { Path() }
    val dim = remember { Path().apply { fillType = PathFillType.EvenOdd } }
    val scrim = c.scrim
    val accent = c.primary
    val idle = Color.White
    Canvas(modifier) {
        val f = found()
        val b = if (reduce) 0.5f else breath
        val side = size.minDimension * 0.66f * (1f + 0.035f * b - 0.08f * f)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val right = left + side
        val bottom = top + side
        val r = 34.dp.toPx()

        dim.reset()
        dim.fillType = PathFillType.EvenOdd
        dim.addRect(Rect(Offset.Zero, size))
        dim.addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(r)))
        drawPath(dim, scrim.copy(alpha = 0.42f + 0.18f * f))

        val len = lerp(side * 0.15f * (1f + 0.4f * b), side / 2f, f).coerceAtLeast(r)
        brackets.reset()
        brackets.moveTo(left, top + len); brackets.lineTo(left, top + r)
        brackets.arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false); brackets.lineTo(left + len, top)
        brackets.moveTo(right - len, top); brackets.lineTo(right - r, top)
        brackets.arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f, 90f, false); brackets.lineTo(right, top + len)
        brackets.moveTo(right, bottom - len); brackets.lineTo(right, bottom - r)
        brackets.arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false); brackets.lineTo(right - len, bottom)
        brackets.moveTo(left + len, bottom); brackets.lineTo(left + r, bottom)
        brackets.arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false); brackets.lineTo(left, bottom - len)
        drawPath(
            brackets,
            lerp(idle, accent, f.coerceIn(0f, 1f)),
            style = Stroke(width = (5f + 2f * f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        if (!reduce && f < 0.99f) {
            val y = top + side * (0.12f + 0.76f * sweep)
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent), startX = left + r, endX = right - r),
                Offset(left + r, y), Offset(right - r, y),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round, alpha = 1f - f,
            )
        }
    }
}

@Composable
private fun FoundBadge(progress: () -> Float, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Circle) }
    val p = progress()
    if (p <= 0.01f) return
    Box(
        modifier
            .size(96.dp)
            .graphicsLayer { scaleX = p; scaleY = p; alpha = p.coerceIn(0f, 1f) }
            .clip(MorphShape(morph, p.coerceIn(0f, 1f), rotation = 120f * (1f - p)))
            .background(c.primary),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedCheck(visible = p > 0.5f, size = 64.dp, color = c.primary, onColor = c.onPrimary)
    }
}

/** Big pill button used by result sheets. */
@Composable
private fun PrimaryAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Button(
        onClick = { haptics.click(); onClick() },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shapes = ButtonDefaults.shapes(),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    OutlinedButton(onClick = { haptics.tick(); onClick() }, shapes = ButtonDefaults.shapes()) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun QrResultSheet(content: QrContent, format: String, serial: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val kind = content.kind
    var revealed by remember { mutableStateOf(false) }

    val details: List<Pair<String, String>> = when (content) {
        is QrContent.Url -> listOf(tr("Adresse", "Address") to content.url)
        is QrContent.Wifi -> listOf(
            tr("Nom du réseau", "Network name") to content.ssid,
            tr("Sécurité", "Security") to when (content.security) {
                "nopass" -> tr("Aucune", "None"); "SAE" -> "WPA3"; "WEP" -> "WEP"; else -> "WPA/WPA2"
            },
        ) + (if (content.hidden) listOf(tr("Réseau", "Network") to tr("Masqué", "Hidden")) else emptyList())
        is QrContent.Contact -> buildList {
            if (content.name.isNotBlank()) add(tr("Nom", "Name") to content.name)
            content.phones.forEach { add(tr("Téléphone", "Phone") to it) }
            content.emails.forEach { add(tr("E-mail", "Email") to it) }
            if (content.org.isNotBlank()) add(tr("Organisation", "Organization") to content.org)
            if (content.address.isNotBlank()) add(tr("Adresse", "Address") to content.address)
            if (content.url.isNotBlank()) add(tr("Site", "Website") to content.url)
            if (content.note.isNotBlank()) add(tr("Note", "Note") to content.note)
        }
        is QrContent.Email -> buildList {
            add(tr("À", "To") to content.to)
            if (content.subject.isNotBlank()) add(tr("Objet", "Subject") to content.subject)
            if (content.body.isNotBlank()) add(tr("Message", "Message") to content.body)
        }
        is QrContent.Phone -> listOf(tr("Numéro", "Number") to content.number)
        is QrContent.Sms -> listOf(tr("Numéro", "Number") to content.number) +
            (if (content.body.isNotBlank()) listOf(tr("Message", "Message") to content.body) else emptyList())
        is QrContent.Geo -> listOf(
            tr("Coordonnées", "Coordinates") to "%.6f, %.6f".format(java.util.Locale.US, content.lat, content.lon),
        ) + (if (content.query.isNotBlank()) listOf(tr("Lieu", "Place") to content.query) else emptyList())
        is QrContent.Text -> listOf(tr("Contenu", "Content") to content.raw)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.cascade("qr-res-$serial-0", 0)) {
                Box(
                    Modifier.size(60.dp).clip(PolygonShape(kind.shape)).background(c.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(kind.icon, null, tint = c.onPrimaryContainer, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.label.text, style = MaterialTheme.typography.headlineSmall)
                    Text(format, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }

            Surface(shape = RoundedCornerShape(24.dp), color = c.surfaceContainerHigh, modifier = Modifier.cascade("qr-res-$serial-1", 1)) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        details.forEachIndexed { i, (label, value) ->
                            if (i > 0) HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = c.primary)
                            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 12, overflow = TextOverflow.Ellipsis)
                        }
                        if (content is QrContent.Wifi && content.password.isNotEmpty()) {
                            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f))
                            Text(tr("Mot de passe", "Password"), style = MaterialTheme.typography.labelMedium, color = c.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (revealed) content.password else "•".repeat(content.password.length.coerceAtMost(16)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { revealed = !revealed }) {
                                    Icon(if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, tr("Afficher", "Show"))
                                }
                            }
                        }
                    }
                }
            }

            Box(Modifier.cascade("qr-res-$serial-2", 2)) {
                when (content) {
                    is QrContent.Url -> PrimaryAction(Icons.AutoMirrored.Rounded.OpenInNew, tr("Ouvrir le lien", "Open link"), { QrActions.openUrl(context, content.url) })
                    is QrContent.Wifi -> PrimaryAction(Icons.Rounded.Wifi, tr("Se connecter", "Connect"), { QrActions.connectWifi(context, content) })
                    is QrContent.Contact -> PrimaryAction(Icons.Rounded.PersonAdd, tr("Ajouter aux contacts", "Add to contacts"), { QrActions.addContact(context, content) })
                    is QrContent.Email -> PrimaryAction(Icons.Rounded.Email, tr("Écrire un e-mail", "Write an email"), { QrActions.email(context, content.to, content.subject, content.body) })
                    is QrContent.Phone -> PrimaryAction(Icons.Rounded.Phone, tr("Appeler", "Call"), { QrActions.dial(context, content.number) })
                    is QrContent.Sms -> PrimaryAction(Icons.Rounded.Sms, tr("Envoyer un SMS", "Send SMS"), { QrActions.sms(context, content.number, content.body) })
                    is QrContent.Geo -> PrimaryAction(Icons.Rounded.Map, tr("Ouvrir la carte", "Open map"), { QrActions.map(context, content.lat, content.lon, content.query) })
                    is QrContent.Text -> PrimaryAction(Icons.Rounded.ContentCopy, tr("Copier le texte", "Copy text"), { copyToClipboard(context, content.raw) })
                }
            }

            FlowRow(
                Modifier.fillMaxWidth().cascade("qr-res-$serial-3", 3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (content) {
                    is QrContent.Wifi -> if (content.password.isNotEmpty()) {
                        SecondaryAction(Icons.Rounded.ContentCopy, tr("Copier le mot de passe", "Copy password")) { copyToClipboard(context, content.password, "Wi-Fi") }
                    }
                    is QrContent.Text -> {
                        SecondaryAction(Icons.Rounded.Search, tr("Rechercher", "Search")) { QrActions.webSearch(context, content.raw) }
                    }
                    is QrContent.Url -> SecondaryAction(Icons.Rounded.ContentCopy, tr("Copier le lien", "Copy link")) { copyToClipboard(context, content.url) }
                    is QrContent.Phone -> SecondaryAction(Icons.Rounded.ContentCopy, tr("Copier le numéro", "Copy number")) { copyToClipboard(context, content.number) }
                    is QrContent.Email -> SecondaryAction(Icons.Rounded.ContentCopy, tr("Copier l'adresse", "Copy address")) { copyToClipboard(context, content.to) }
                    else -> {}
                }
                if (content !is QrContent.Text) {
                    SecondaryAction(Icons.Rounded.ContentCopy, tr("Copier le contenu", "Copy content")) { copyToClipboard(context, content.raw) }
                }
                SecondaryAction(Icons.Rounded.Share, tr("Partager", "Share")) { shareText(context, content.raw, tr("Partager", "Share")) }
            }
        }
    }
}

@Composable
private fun QrHistorySheet(onDismiss: () -> Unit, onOpen: (QrHistoryItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val items by QrHistory.items.collectAsStateWithLifecycle(emptyList())
    val enabled by QrHistory.enabled.collectAsStateWithLifecycle(true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Historique", "History"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    TextButton(onClick = { haptics.reject(); QrHistory.clear() }) {
                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Effacer", "Clear"))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Text(tr("Garder les derniers scans", "Keep recent scans"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { haptics.tick(); QrHistory.setEnabled(it) })
            }
            if (items.isEmpty()) {
                Text(
                    tr("Aucun scan pour l'instant.", "No scans yet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                    val kind = item.content.kind
                    Row(
                        Modifier
                            .animateItem()
                            .cascade("qr-hist-${item.id}", i)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.surfaceContainerHigh)
                            .clickable { haptics.click(); onOpen(item) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(PolygonShape(kind.shape)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
                            Icon(kind.icon, null, tint = c.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.content.summary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                kind.label.text + " · " + DateUtils.getRelativeTimeSpanString(item.at).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { haptics.tick(); QrHistory.delete(item.id) }) { Icon(Icons.Rounded.Close, tr("Supprimer", "Delete")) }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
