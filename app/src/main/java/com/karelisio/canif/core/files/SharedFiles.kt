package com.karelisio.canif.core.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.karelisio.canif.core.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Files produced by tools: cache for sharing, MediaStore for saving. No storage permission needed. */
object SharedFiles {
    fun dir(context: Context): File = File(context.cacheDir, "shared").apply { mkdirs() }

    fun newFile(context: Context, prefix: String, ext: String, index: Int? = null): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir(context), "${prefix}_$stamp${index?.let { "_$it" } ?: ""}.$ext")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun share(context: Context, files: List<File>, mime: String) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, tr("Partager", "Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    val canSaveToMediaStore: Boolean get() = Build.VERSION.SDK_INT >= 29

    /** Copies [file] to Pictures/Canif or Download/Canif (Android 10+). */
    suspend fun saveToMediaStore(context: Context, file: File, mime: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        val isImage = mime.startsWith("image/")
        val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/Canif")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return@withContext false
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.isSuccess
    }

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "?"

    fun size(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull() ?: runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: 0L

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f Mo", bytes / 1_048_576.0).let { if (com.karelisio.canif.core.i18n.Lang.isFr) it else it.replace("Mo", "MB") }
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f Ko", bytes / 1024.0).let { if (com.karelisio.canif.core.i18n.Lang.isFr) it else it.replace("Ko", "KB") }
        else -> "$bytes " + tr("o", "B")
    }
}

/** Bitmap decoding with EXIF orientation and down-sampling to [maxSide] pixels. */
object Bitmaps {
    fun decode(context: Context, uri: Uri, maxSide: Int = Int.MAX_VALUE): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val side = maxOf(w, h)
                if (side > maxSide) {
                    val s = maxSide.toFloat() / side
                    decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val rotated = if (degrees != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true) else bmp
            if (maxOf(rotated.width, rotated.height) > maxSide) {
                val s = maxSide.toFloat() / maxOf(rotated.width, rotated.height)
                Bitmap.createScaledBitmap(rotated, (rotated.width * s).toInt(), (rotated.height * s).toInt(), true)
            } else rotated
        }
    }.getOrNull()
}

/** Asynchronously loaded thumbnail. */
@Composable
fun rememberThumbnail(uri: Uri, maxSide: Int = 512): State<ImageBitmap?> {
    val context = androidx.compose.ui.platform.LocalContext.current
    return produceState<ImageBitmap?>(null, uri, maxSide) {
        value = withContext(Dispatchers.IO) { Bitmaps.decode(context, uri, maxSide)?.asImageBitmap() }
    }
}
