package com.karelisio.canif.tools.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PictureAsPdf
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val mediaTools: List<Tool> = listOf(
    Tool(
        id = "images",
        name = Txt("Images", "Images"),
        category = Category.MEDIA,
        icon = Icons.Rounded.PhotoSizeSelectLarge,
        keywords = listOf("compresser", "compress", "redimensionner", "resize", "convertir", "convert", "jpg", "jpeg", "png", "webp", "photo", "poids", "taille"),
        accepts = listOf("image/*"),
        content = { ImageToolScreen(incoming?.uris.orEmpty()) },
    ),
    Tool(
        id = "img2pdf",
        name = Txt("Images vers PDF", "Images to PDF"),
        category = Category.MEDIA,
        icon = Icons.Rounded.PictureAsPdf,
        keywords = listOf("pdf", "images", "photos", "document", "scan", "convertir", "convert"),
        accepts = listOf("image/*"),
        content = { ImagesToPdfScreen(incoming?.uris.orEmpty()) },
    ),
    Tool(
        id = "pdf-merge",
        name = Txt("Fusion de PDF", "Merge PDF"),
        category = Category.MEDIA,
        icon = Icons.Rounded.CallMerge,
        keywords = listOf("pdf", "fusionner", "merge", "combiner", "combine", "assembler", "join"),
        accepts = listOf("application/pdf"),
        content = { PdfMergeScreen(incoming?.uris.orEmpty()) },
    ),
)
