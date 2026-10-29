package com.karelisio.canif.tools.qr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.karelisio.canif.core.i18n.tr
import java.io.File

/** Writes files to cache/shared and hands them to other apps through the app-wide FileProvider. */
object SharedFiles {
    private fun authority(context: Context) = context.packageName + ".files"

    fun writePng(context: Context, name: String, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    fun sharePng(context: Context, bitmap: Bitmap, name: String, title: String) {
        runCatching {
            val uri = writePng(context, name, bitmap)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(context, tr("Partage impossible", "Could not share"), Toast.LENGTH_SHORT).show()
        }
    }

    /** API 29+: saves into Pictures/Canif without any storage permission. */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Canif")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Writes into a document picked by the user (Storage Access Framework, API < 29). */
    fun writeToUri(context: Context, uri: Uri, bitmap: Bitmap): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("no stream")
        true
    }.getOrDefault(false)
}
