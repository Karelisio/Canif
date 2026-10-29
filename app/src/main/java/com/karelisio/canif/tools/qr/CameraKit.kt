package com.karelisio.canif.tools.qr

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karelisio.canif.ui.components.rememberHaptics
import java.util.concurrent.Executors

/**
 * Back camera preview + live frame analysis, bound to the screen lifecycle.
 * Everything (use cases, analysis thread, torch) is released when the composable leaves.
 */
@Composable
fun CameraFeed(
    analyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier,
    torch: Boolean = false,
    analysisSize: Size = Size(1280, 720),
    onFlashUnit: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val currentAnalyzer by rememberUpdatedState(analyzer)
    val flashCallback by rememberUpdatedState(onFlashUnit)

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(analysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
        analysis.setAnalyzer(executor) { image -> currentAnalyzer.analyze(image) }
        future.addListener({
            if (disposed) return@addListener
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            preview.setSurfaceProvider(previewView.surfaceProvider)
            runCatching {
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera = cam
                flashCallback(cam.cameraInfo.hasFlashUnit())
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            camera?.cameraControl?.enableTorch(false)
            camera = null
            analysis.clearAnalyzer()
            runCatching { provider?.unbind(preview, analysis) }
            executor.shutdown()
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Round expressive toggle used over camera previews: it squares itself when checked
 * and bounces on press.
 */
@Composable
fun CameraToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconOn: ImageVector,
    iconOff: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val c = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val corner by animateDpAsState(if (checked) size * 0.3f else size / 2, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "corner")
    val bg by animateColorAsState(if (checked) c.primary else c.surfaceContainerHigh.copy(alpha = 0.85f), label = "bg")
    val fg by animateColorAsState(if (checked) c.onPrimary else c.onSurface, label = "fg")
    Surface(
        checked = checked,
        onCheckedChange = { haptics.click(); onCheckedChange(it) },
        modifier = modifier.size(size),
        shape = RoundedCornerShape(corner),
        color = bg,
        contentColor = fg,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(checked, transitionSpec = {
                scaleIn(spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) togetherWith scaleOut()
            }, label = "icon") { on ->
                Icon(if (on) iconOn else iconOff, contentDescription, tint = fg)
            }
        }
    }
}

/** Plain round button with the same look as [CameraToggleButton]. */
@Composable
fun CameraActionButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)) {
    val haptics = rememberHaptics()
    Surface(onClick = { haptics.click(); onClick() }, modifier = modifier.size(size), shape = RoundedCornerShape(size / 2), color = color) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription) }
    }
}
