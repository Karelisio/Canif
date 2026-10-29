package com.karelisio.canif.tools.qr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.permissions.AppPermission
import com.karelisio.canif.core.tools.Category
import com.karelisio.canif.core.tools.Tool

val qrTools: List<Tool> = listOf(
    Tool(
        id = "qr-scan",
        name = Txt("Scanner QR", "QR scanner"),
        category = Category.QR,
        icon = Icons.Rounded.QrCodeScanner,
        keywords = listOf("qr", "code", "scanner", "scan", "lire", "read", "code-barres", "barcode", "ean", "wifi", "lien", "link", "datamatrix"),
        description = Txt("Lit QR codes et codes-barres, hors ligne", "Reads QR codes and barcodes, offline"),
        features = listOf(Tool.FEATURE_CAMERA),
        permissions = listOf(AppPermission.CAMERA),
        accepts = listOf("image/*"),
    ) { QrScanScreen() },
    Tool(
        id = "qr-gen",
        name = Txt("Générateur QR", "QR generator"),
        category = Category.QR,
        icon = Icons.Rounded.QrCode2,
        keywords = listOf("qr", "code", "générer", "generate", "créer", "create", "wifi", "contact", "vcard", "mecard", "lien", "link"),
        description = Txt("Texte, lien, Wi-Fi ou contact en QR code", "Text, link, Wi-Fi or contact as a QR code"),
        accepts = listOf("text/plain"),
    ) { QrGenScreen() },
)
