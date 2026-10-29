package com.karelisio.canif.tools.qr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.google.mlkit.vision.barcode.common.Barcode
import com.karelisio.canif.core.i18n.Txt
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.ui.components.copyToClipboard

/** What a scanned code means, parsed from its raw text. */
sealed interface QrContent {
    val raw: String

    data class Url(override val raw: String, val url: String) : QrContent
    data class Wifi(override val raw: String, val ssid: String, val password: String, val security: String, val hidden: Boolean) : QrContent
    data class Contact(
        override val raw: String,
        val name: String,
        val phones: List<String>,
        val emails: List<String>,
        val org: String,
        val address: String,
        val url: String,
        val note: String,
    ) : QrContent
    data class Email(override val raw: String, val to: String, val subject: String, val body: String) : QrContent
    data class Phone(override val raw: String, val number: String) : QrContent
    data class Sms(override val raw: String, val number: String, val body: String) : QrContent
    data class Geo(override val raw: String, val lat: Double, val lon: Double, val query: String) : QrContent
    data class Text(override val raw: String) : QrContent
}

enum class QrKind(val label: Txt, val icon: ImageVector, val shape: RoundedPolygon) {
    URL(Txt("Lien", "Link"), Icons.Rounded.Link, MaterialShapes.Cookie9Sided),
    WIFI(Txt("Réseau Wi-Fi", "Wi-Fi network"), Icons.Rounded.Wifi, MaterialShapes.Sunny),
    CONTACT(Txt("Contact", "Contact"), Icons.Rounded.Person, MaterialShapes.Flower),
    EMAIL(Txt("E-mail", "Email"), Icons.Rounded.Email, MaterialShapes.Cookie6Sided),
    PHONE(Txt("Téléphone", "Phone"), Icons.Rounded.Phone, MaterialShapes.Clover4Leaf),
    SMS(Txt("SMS", "SMS"), Icons.AutoMirrored.Rounded.Message, MaterialShapes.Pill),
    GEO(Txt("Lieu", "Location"), Icons.Rounded.Place, MaterialShapes.SoftBurst),
    TEXT(Txt("Texte", "Text"), Icons.AutoMirrored.Rounded.Notes, MaterialShapes.Cookie4Sided),
}

val QrContent.kind: QrKind
    get() = when (this) {
        is QrContent.Url -> QrKind.URL
        is QrContent.Wifi -> QrKind.WIFI
        is QrContent.Contact -> QrKind.CONTACT
        is QrContent.Email -> QrKind.EMAIL
        is QrContent.Phone -> QrKind.PHONE
        is QrContent.Sms -> QrKind.SMS
        is QrContent.Geo -> QrKind.GEO
        is QrContent.Text -> QrKind.TEXT
    }

/** One line summary, for history rows. */
val QrContent.summary: String
    get() = when (this) {
        is QrContent.Url -> url
        is QrContent.Wifi -> ssid
        is QrContent.Contact -> name.ifBlank { phones.firstOrNull() ?: emails.firstOrNull() ?: raw }
        is QrContent.Email -> to
        is QrContent.Phone -> number
        is QrContent.Sms -> number
        is QrContent.Geo -> "%.5f, %.5f".format(java.util.Locale.US, lat, lon)
        is QrContent.Text -> raw.lineSequence().firstOrNull().orEmpty()
    }

object QrParser {
    fun parse(raw: String): QrContent {
        val s = raw.trim()
        return runCatching {
            when {
                s.startsWith("WIFI:", true) -> wifi(raw, s)
                s.startsWith("MECARD:", true) -> mecard(raw, s)
                s.startsWith("BEGIN:VCARD", true) -> vcard(raw, s)
                s.startsWith("MATMSG:", true) -> matmsg(raw, s)
                s.startsWith("mailto:", true) -> mailto(raw, s)
                s.startsWith("tel:", true) -> QrContent.Phone(raw, s.substring(4))
                s.startsWith("smsto:", true) || s.startsWith("sms:", true) || s.startsWith("mmsto:", true) -> sms(raw, s)
                s.startsWith("geo:", true) -> geo(raw, s)
                s.startsWith("http://", true) || s.startsWith("https://", true) -> QrContent.Url(raw, s)
                s.startsWith("www.", true) && ' ' !in s -> QrContent.Url(raw, "https://$s")
                else -> null
            }
        }.getOrNull() ?: QrContent.Text(raw)
    }

    /** Splits on [sep] not preceded by a backslash. */
    private fun splitEscaped(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var esc = false
        for (ch in s) {
            when {
                esc -> { cur.append('\\').append(ch); esc = false }
                ch == '\\' -> esc = true
                ch == sep -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun unescape(s: String): String {
        val b = StringBuilder()
        var esc = false
        for (ch in s) {
            if (esc) { b.append(ch); esc = false } else if (ch == '\\') esc = true else b.append(ch)
        }
        return b.toString()
    }

    /** "KEY:value;KEY:value;;" → multimap of unescaped values. */
    private fun fields(body: String): Map<String, List<String>> {
        val m = LinkedHashMap<String, MutableList<String>>()
        for (part in splitEscaped(body, ';')) {
            val i = part.indexOf(':')
            if (i <= 0) continue
            m.getOrPut(part.substring(0, i).uppercase()) { mutableListOf() } += unescape(part.substring(i + 1))
        }
        return m
    }

    private fun wifi(raw: String, s: String): QrContent {
        val f = fields(s.substring(5))
        val sec = f["T"]?.firstOrNull().orEmpty().uppercase()
        return QrContent.Wifi(
            raw,
            ssid = f["S"]?.firstOrNull().orEmpty(),
            password = f["P"]?.firstOrNull().orEmpty(),
            security = when {
                sec.isBlank() || sec == "NOPASS" -> "nopass"
                sec.startsWith("WEP") -> "WEP"
                sec.contains("SAE") || sec.contains("WPA3") -> "SAE"
                else -> "WPA"
            },
            hidden = f["H"]?.firstOrNull()?.equals("true", true) == true,
        )
    }

    private fun mecard(raw: String, s: String): QrContent {
        val f = fields(s.substring(7))
        val n = f["N"]?.firstOrNull().orEmpty()
        val name = if (',' in n) n.split(',').map { it.trim() }.reversed().joinToString(" ") else n
        return QrContent.Contact(
            raw, name,
            phones = f["TEL"].orEmpty(),
            emails = f["EMAIL"].orEmpty(),
            org = f["ORG"]?.firstOrNull().orEmpty(),
            address = f["ADR"]?.firstOrNull().orEmpty(),
            url = f["URL"]?.firstOrNull().orEmpty(),
            note = f["NOTE"]?.firstOrNull().orEmpty(),
        )
    }

    private fun vcard(raw: String, s: String): QrContent {
        // Unfold continuation lines, then read "NAME;PARAMS:value".
        val lines = s.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        var fn = ""; var n = ""; var org = ""; var adr = ""; var url = ""; var note = ""
        val tels = mutableListOf<String>(); val mails = mutableListOf<String>()
        for (line in lines) {
            val i = line.indexOf(':')
            if (i <= 0) continue
            val key = line.substring(0, i).substringBefore(';').substringAfter('.').uppercase()
            val v = line.substring(i + 1).replace("\\,", ",").replace("\\n", "\n").trim()
            when (key) {
                "FN" -> fn = v
                "N" -> n = v.split(';').let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ") }
                "TEL" -> tels += v.removePrefix("tel:")
                "EMAIL" -> mails += v
                "ORG" -> org = v.split(';').filter { it.isNotBlank() }.joinToString(" ")
                "ADR" -> adr = v.split(';').filter { it.isNotBlank() }.joinToString(", ")
                "URL" -> url = v
                "NOTE" -> note = v
            }
        }
        return QrContent.Contact(raw, fn.ifBlank { n }, tels, mails, org, adr, url, note)
    }

    private fun matmsg(raw: String, s: String): QrContent {
        val f = fields(s.substring(7))
        return QrContent.Email(raw, f["TO"]?.firstOrNull().orEmpty(), f["SUB"]?.firstOrNull().orEmpty(), f["BODY"]?.firstOrNull().orEmpty())
    }

    private fun mailto(raw: String, s: String): QrContent {
        val rest = s.substring(7)
        val to = Uri.decode(rest.substringBefore('?'))
        val q = if ('?' in rest) Uri.parse("x://y?" + rest.substringAfter('?')) else null
        return QrContent.Email(raw, to, q?.getQueryParameter("subject").orEmpty(), q?.getQueryParameter("body").orEmpty())
    }

    private fun sms(raw: String, s: String): QrContent {
        val rest = s.substringAfter(':')
        return if ('?' in rest) {
            val q = Uri.parse("x://y?" + rest.substringAfter('?'))
            QrContent.Sms(raw, rest.substringBefore('?'), q.getQueryParameter("body").orEmpty())
        } else {
            QrContent.Sms(raw, rest.substringBefore(':'), rest.substringAfter(':', ""))
        }
    }

    private fun geo(raw: String, s: String): QrContent? {
        val rest = s.substring(4)
        val coords = rest.substringBefore('?').split(',')
        val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return null
        val q = if ('?' in rest) Uri.parse("x://y?" + rest.substringAfter('?')).getQueryParameter("q").orEmpty() else ""
        return QrContent.Geo(raw, lat, lon, q)
    }

    fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        else -> tr("Code-barres", "Barcode")
    }
}

/** Intents behind the result sheet's buttons. */
object QrActions {
    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, tr("Aucune appli ne peut ouvrir ceci", "No app can open this"), Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, tr("Action refusée par le système", "Action refused by the system"), Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun dial(context: Context, number: String) = start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun sms(context: Context, number: String, body: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body))

    fun email(context: Context, to: String, subject: String, body: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to)))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        start(context, i)
    }

    fun map(context: Context, lat: Double, lon: Double, label: String) {
        val q = if (label.isBlank()) "$lat,$lon" else "$lat,$lon(${Uri.encode(label)})"
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$q")))
    }

    fun webSearch(context: Context, text: String) =
        start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, text))

    fun addContact(context: Context, c: QrContent.Contact) {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
        if (c.name.isNotBlank()) i.putExtra(ContactsContract.Intents.Insert.NAME, c.name)
        c.phones.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        c.phones.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
        c.emails.getOrNull(0)?.let { i.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        c.emails.getOrNull(1)?.let { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it) }
        if (c.org.isNotBlank()) i.putExtra(ContactsContract.Intents.Insert.COMPANY, c.org)
        if (c.address.isNotBlank()) i.putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
        val notes = listOf(c.url, c.note).filter { it.isNotBlank() }.joinToString("\n")
        if (notes.isNotBlank()) i.putExtra(ContactsContract.Intents.Insert.NOTES, notes)
        start(context, i)
    }

    /**
     * Android 11+: system "add network" dialog with a network suggestion.
     * Older versions (or WEP, not supported by suggestions): copy the password and open Wi-Fi settings.
     */
    fun connectWifi(context: Context, w: QrContent.Wifi) {
        if (Build.VERSION.SDK_INT >= 30 && w.security != "WEP") {
            val ok = runCatching {
                val b = WifiNetworkSuggestion.Builder().setSsid(w.ssid).setIsHiddenSsid(w.hidden)
                when (w.security) {
                    "WPA" -> b.setWpa2Passphrase(w.password)
                    "SAE" -> b.setWpa3Passphrase(w.password)
                }
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(b.build()))
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return
        }
        if (w.password.isNotEmpty()) {
            copyToClipboard(context, w.password, "Wi-Fi")
            Toast.makeText(context, tr("Mot de passe copié : collez-le dans les réglages", "Password copied: paste it in the settings"), Toast.LENGTH_LONG).show()
        }
        start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
