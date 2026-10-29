package com.karelisio.canif.tools.media

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.karelisio.canif.core.files.rememberThumbnail
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.MorphingBlob
import com.karelisio.canif.ui.components.cascade

/** Empty state inviting to pick files. */
@Composable
fun PickPrompt(title: String, body: String, button: String, onPick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Image) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MorphingBlob(size = 150.dp, from = MaterialShapes.Clover4Leaf, to = MaterialShapes.Cookie9Sided) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onPick) { Text(button) }
    }
}

@Composable
fun Thumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val bmp by rememberThumbnail(uri, 384)
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(bmp != null, enter = fadeIn() + scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}

/** Horizontal strip of selected images with remove buttons. */
@Composable
fun ThumbnailStrip(uris: List<Uri>, onRemove: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(uris, key = { _, u -> u.toString() }) { i, uri ->
            Box(Modifier.animateItem().cascade(uri.toString(), i)) {
                Thumbnail(uri, Modifier.size(96.dp))
                Surface(
                    onClick = { onRemove(i) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, tr("Retirer", "Remove"), Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun ResultActions(onShare: () -> Unit, onSave: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onShare, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Share, null); Text("  " + tr("Partager", "Share"))
        }
        if (onSave != null) {
            FilledTonalButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Save, null); Text("  " + tr("Enregistrer", "Save"))
            }
        }
    }
}
