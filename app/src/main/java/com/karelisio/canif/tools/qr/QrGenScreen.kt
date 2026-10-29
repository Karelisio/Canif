package com.karelisio.canif.tools.qr

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.WriterException
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.core.tools.ToolScope
import com.karelisio.canif.ui.components.ChoiceRow
import com.karelisio.canif.ui.components.MorphingBlob
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GenType { TEXT, LINK, WIFI, CONTACT }

/** Escapes the reserved characters of WIFI: and MECARD: payloads. */
private fun esc(s: String) = buildString { s.forEach { if (it in "\\;,:\"") append('\\'); append(it) } }

private fun vEsc(s: String) = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

@Composable
fun ToolScope.QrGenScreen() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    var type by rememberSaveable { mutableStateOf(GenType.TEXT) }
    var text by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var wifiPass by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf("WPA") }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var vcard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(incoming) {
        incoming?.text?.takeIf { it.isNotBlank() }?.let { t ->
            if (t.trim().startsWith("http", true) && ' ' !in t.trim()) { type = GenType.LINK; link = t.trim() } else { type = GenType.TEXT; text = t }
        }
    }

    val payload = when (type) {
        GenType.TEXT -> text
        GenType.LINK -> link.trim().let { if (it.isBlank() || it.contains("://")) it else "https://$it" }
        GenType.WIFI -> if (ssid.isBlank()) "" else buildString {
            append("WIFI:T:").append(if (security == "nopass") "nopass" else security).append(';')
            append("S:").append(esc(ssid)).append(';')
            if (security != "nopass") append("P:").append(esc(wifiPass)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
        GenType.CONTACT -> if (listOf(name, phone, email, org).all { it.isBlank() }) "" else if (vcard) buildString {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
            append("N:;").append(vEsc(name)).append(";;;\r\n")
            append("FN:").append(vEsc(name)).append("\r\n")
            if (org.isNotBlank()) append("ORG:").append(vEsc(org)).append("\r\n")
            if (phone.isNotBlank()) append("TEL:").append(vEsc(phone)).append("\r\n")
            if (email.isNotBlank()) append("EMAIL:").append(vEsc(email)).append("\r\n")
            append("END:VCARD")
        } else buildString {
            append("MECARD:")
            if (name.isNotBlank()) append("N:").append(esc(name)).append(';')
            if (phone.isNotBlank()) append("TEL:").append(esc(phone)).append(';')
            if (email.isNotBlank()) append("EMAIL:").append(esc(email)).append(';')
            if (org.isNotBlank()) append("ORG:").append(esc(org)).append(';')
            append(';')
        }
    }

    var matrix by remember { mutableStateOf<QrMatrix?>(null) }
    var tooLong by remember { mutableStateOf(false) }
    LaunchedEffect(payload) {
        if (payload.isBlank()) { matrix = null; tooLong = false; return@LaunchedEffect }
        delay(220)
        val m = withContext(Dispatchers.Default) { runCatching { QrMatrix.encode(payload) } }
        m.onSuccess { matrix = it; tooLong = false }
            .onFailure { if (it is WriterException || it is IllegalArgumentException) { matrix = null; tooLong = true } }
    }

    // Theme colors, keeping dark modules on a light background so every scanner reads it.
    val dark = c.surface.luminance() < 0.5f
    val qrColors = if (dark) QrColors(c.inverseSurface, c.inverseOnSurface, c.inversePrimary)
    else QrColors(c.surfaceContainerLowest, c.onPrimaryContainer, c.primary)

    fun bitmap(): Bitmap? = matrix?.let { renderQrBitmap(it, qrColors) }
    fun fileName() = "canif-qr-${System.currentTimeMillis()}.png"

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val b = bitmap()
        if (uri != null && b != null) {
            val ok = SharedFiles.writeToUri(context, uri, b)
            Toast.makeText(context, if (ok) tr("Image enregistrée", "Image saved") else tr("Échec de l'enregistrement", "Saving failed"), Toast.LENGTH_SHORT).show()
            if (ok) haptics.confirm() else haptics.reject()
        }
    }

    ToolColumn {
        ChoiceRow(
            GenType.entries, type,
            label = {
                when (it) {
                    GenType.TEXT -> tr("Texte", "Text"); GenType.LINK -> tr("Lien", "Link")
                    GenType.WIFI -> "Wi-Fi"; GenType.CONTACT -> tr("Contact", "Contact")
                }
            },
            onSelect = { type = it },
        )

        // Preview
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = qrColors.background,
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f),
            ) {
                AnimatedContent(
                    targetState = matrix,
                    transitionSpec = {
                        (fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f))
                            .togetherWith(fadeOut())
                    },
                    contentKey = { it == null },
                    label = "qr",
                ) { m ->
                    if (m != null) {
                        QrCanvas(m, qrColors, Modifier.padding(14.dp).fillMaxWidth().aspectRatio(1f))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
                            MorphingBlob(size = 120.dp, from = MaterialShapes.Cookie12Sided, to = MaterialShapes.Clover4Leaf, color = c.primaryContainer) {
                                Icon(Icons.Rounded.QrCode2, null, tint = c.onPrimaryContainer, modifier = Modifier.size(52.dp))
                            }
                            Spacer(Modifier.size(16.dp))
                            Text(
                                if (tooLong) tr("Contenu trop long pour un QR code", "Too much content for a QR code")
                                else tr("Remplissez les champs pour créer le code", "Fill in the fields to create the code"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (tooLong) c.error else qrColors.modules,
                            )
                        }
                    }
                }
            }
        }
        matrix?.let { m ->
            Text(
                tr("Version ${m.version} · ${m.size}×${m.size} modules · correction ${m.ecLevel}", "Version ${m.version} · ${m.size}×${m.size} modules · ${m.ecLevel} error correction"),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    haptics.click()
                    val b = bitmap() ?: return@Button
                    SharedFiles.sharePng(context, b, fileName(), tr("Partager le QR code", "Share QR code"))
                },
                enabled = matrix != null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text(tr("Partager", "Share"))
            }
            FilledTonalButton(
                onClick = {
                    haptics.click()
                    if (Build.VERSION.SDK_INT >= 29) {
                        val b = bitmap() ?: return@FilledTonalButton
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { SharedFiles.saveToGallery(context, b, fileName()) }
                            if (ok) haptics.confirm() else haptics.reject()
                            Toast.makeText(
                                context,
                                if (ok) tr("Enregistré dans Images/Canif", "Saved to Pictures/Canif") else tr("Échec de l'enregistrement", "Saving failed"),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        createDoc.launch(fileName())
                    }
                },
                enabled = matrix != null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Rounded.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Enregistrer", "Save"))
            }
        }

        SectionTitle(tr("Contenu", "Content"))
        val fieldShape = RoundedCornerShape(20.dp)
        AnimatedContent(type, transitionSpec = {
            (fadeIn() + expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))) togetherWith (fadeOut() + shrinkVertically())
        }, label = "fields") { t ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (t) {
                    GenType.TEXT -> OutlinedTextField(
                        text, { text = it }, label = { Text(tr("Texte", "Text")) },
                        minLines = 3, shape = fieldShape, modifier = Modifier.fillMaxWidth(),
                    )
                    GenType.LINK -> OutlinedTextField(
                        link, { link = it }, label = { Text(tr("Adresse web", "Web address")) },
                        placeholder = { Text("https://…") }, singleLine = true, shape = fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                    )
                    GenType.WIFI -> {
                        OutlinedTextField(ssid, { ssid = it }, label = { Text(tr("Nom du réseau (SSID)", "Network name (SSID)")) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                        ChoiceRow(listOf("WPA", "WEP", "nopass"), security, label = { if (it == "nopass") tr("Aucune", "None") else it }, onSelect = { security = it })
                        AnimatedVisibility(security != "nopass", enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            OutlinedTextField(
                                wifiPass, { wifiPass = it }, label = { Text(tr("Mot de passe", "Password")) }, singleLine = true, shape = fieldShape,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showPass = !showPass }) {
                                        Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, tr("Afficher", "Show"))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("Réseau masqué", "Hidden network"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(hidden, { haptics.tick(); hidden = it })
                        }
                    }
                    GenType.CONTACT -> {
                        OutlinedTextField(name, { name = it }, label = { Text(tr("Nom", "Name")) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            phone, { phone = it }, label = { Text(tr("Téléphone", "Phone")) }, singleLine = true, shape = fieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            email, { email = it }, label = { Text(tr("E-mail", "Email")) }, singleLine = true, shape = fieldShape,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(org, { org = it }, label = { Text(tr("Organisation", "Organization")) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
                        ChoiceRow(listOf(false, true), vcard, label = { if (it) "vCard" else "MeCard" }, onSelect = { vcard = it })
                        Text(
                            if (vcard) tr("vCard : format standard, plus long.", "vCard: standard format, longer.")
                            else tr("MeCard : plus compact, code plus simple à scanner.", "MeCard: more compact, easier to scan."),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(16.dp))
    }
}
