package com.karelisio.canif.tools.calc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karelisio.canif.CanifApp
import com.karelisio.canif.core.i18n.Lang
import com.karelisio.canif.core.i18n.tr
import com.karelisio.canif.data.RateCacheEntity
import com.karelisio.canif.ui.components.CanifCard
import com.karelisio.canif.ui.components.PolygonShape
import com.karelisio.canif.ui.components.RollingText
import com.karelisio.canif.ui.components.SectionTitle
import com.karelisio.canif.ui.components.rememberHaptics
import com.karelisio.canif.ui.nav.ToolColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** ECB reference rates through the Frankfurter API, cached in Room for offline use. */
internal object Rates {
    private val endpoints = listOf(
        "https://api.frankfurter.dev/v1/latest?base=EUR",
        "https://api.frankfurter.app/latest?base=EUR",
    )

    suspend fun cached(): RateCacheEntity? = withContext(Dispatchers.IO) {
        runCatching { CanifApp.instance.db.rates().get("EUR") }.getOrNull()
    }

    suspend fun fetch(): RateCacheEntity = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (url in endpoints) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Canif")
                }
                try {
                    if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val rates = json.getJSONObject("rates")
                    if (rates.length() == 0) throw IllegalStateException("empty")
                    val entity = RateCacheEntity("EUR", json.getString("date"), rates.toString(), System.currentTimeMillis())
                    CanifApp.instance.db.rates().put(entity)
                    return@withContext entity
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("unreachable")
    }

    /** Rates relative to EUR, EUR included. */
    fun parse(e: RateCacheEntity): Map<String, Double> {
        val o = JSONObject(e.ratesJson)
        val m = HashMap<String, Double>()
        o.keys().forEach { k -> m[k] = o.getDouble(k) }
        m["EUR"] = 1.0
        return m
    }
}

internal fun currencyName(code: String, locale: Locale = Lang.locale): String =
    runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)
        .replaceFirstChar { it.titlecase(locale) }

/** Flag emoji from the currency's country prefix (EUR → EU flag). */
internal fun currencyFlag(code: String): String {
    if (code.length < 2) return ""
    val cc = code.substring(0, 2).uppercase()
    return String(Character.toChars(0x1F1E6 + (cc[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (cc[1] - 'A')))
}

private fun String.folded() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private fun fmtAmount(x: Double): String = when {
    kotlin.math.abs(x) >= 100 -> Fmt.money(x)
    kotlin.math.abs(x) >= 0.01 || x == 0.0 -> Fmt.num(x, 4, 2)
    else -> Fmt.sig(x, 4)
}

private const val STALE_MS = 6 * 3600_000L

@Composable
internal fun CurrencyScreen() {
    val settings = CanifApp.instance.settings
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var rates by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var date by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf<String?>(null) }
    var to by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("100") }
    var picking by remember { mutableStateOf<Int?>(null) } // 0 = from, 1 = to

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val e = Rates.fetch()
                rates = Rates.parse(e); date = e.date; offline = false
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (from == null) {
            from = settings.string("currency_from", "EUR").first()
            to = settings.string("currency_to", if (Locale.getDefault().country == "US") "EUR" else "USD").first()
        }
        val c = Rates.cached()
        if (c != null) { rates = Rates.parse(c); date = c.date }
        if (c == null || System.currentTimeMillis() - c.fetchedAt > STALE_MS) refresh()
    }

    fun save() = scope.launch {
        from?.let { settings.setString("currency_from", it) }
        to?.let { settings.setString("currency_to", it) }
    }

    ToolColumn {
        StatusRow(date, offline, loading, hasData = rates.isNotEmpty(), onRefresh = { haptics.click(); refresh() })

        if (rates.isEmpty()) {
            CanifCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loading) {
                        LoadingIndicator(Modifier.size(64.dp))
                        Text(tr("Récupération des taux…", "Fetching rates…"))
                    } else {
                        Icon(Icons.Rounded.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            tr("Aucun taux enregistré. Connectez-vous une première fois pour télécharger les taux du jour.", "No saved rates. Connect once to download today's rates."),
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = { refresh() }) { Text(tr("Réessayer", "Retry")) }
                    }
                }
            }
            return@ToolColumn
        }

        val f = from ?: "EUR"
        val t = to ?: "USD"
        val rf = rates[f]
        val rt = rates[t]
        val value = Fmt.parse(amount)
        val rate = if (rf != null && rt != null) rt / rf else null
        val result = if (rate != null && value != null) value * rate else null

        Box(contentAlignment = Alignment.CenterEnd) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyCard(f, onPick = { picking = 0 }) {
                    BasicTextField(
                        value = amount,
                        onValueChange = { v -> amount = v.replace(',', '.').filter { it.isDigit() || it == '.' } },
                        textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CurrencyCard(t, onPick = { picking = 1 }, highlighted = true) {
                    RollingText(
                        result?.let { fmtAmount(it) } ?: "—",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            SwapButton(
                onClick = { val a = from; from = to; to = a; save() },
                Modifier.padding(end = 20.dp),
            )
        }

        if (rate != null) {
            Text(
                "1 $f = ${Fmt.sig(rate, 6)} $t   ·   1 $t = ${Fmt.sig(1 / rate, 6)} $f",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(tr("Conversions rapides", "Quick conversions"))
            CanifCard(padding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
                listOf(1.0, 5.0, 10.0, 20.0, 50.0, 100.0, 500.0, 1000.0).forEach { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { haptics.tick(); amount = Fmt.num(v, 0).filter { it.isDigit() } }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("${Fmt.num(v, 0)} $f", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${fmtAmount(v * rate)} $t", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Hint(tr("Source : Banque centrale européenne via Frankfurter. Taux de référence indicatifs, publiés les jours ouvrés.", "Source: European Central Bank via Frankfurter. Indicative reference rates, published on working days."))
    }

    picking?.let { which ->
        CurrencyPicker(
            codes = rates.keys.sorted(),
            rates = rates,
            current = if (which == 0) from else to,
            onDismiss = { picking = null },
            onPick = { code ->
                haptics.confirm()
                if (which == 0) { if (code == to) to = from; from = code } else { if (code == from) from = to; to = code }
                save()
                picking = null
            },
        )
    }
}

@Composable
private fun StatusRow(date: String?, offline: Boolean, loading: Boolean, hasData: Boolean, onRefresh: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(
            Triple(offline, date, loading),
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) + fadeIn()) togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "status",
        ) { (off, d, load) ->
            val label = when {
                load && !hasData -> tr("Connexion…", "Connecting…")
                d == null -> tr("Aucun taux", "No rates")
                off -> tr("Hors ligne · Taux du ${formatDate(d)}", "Offline · Rates of ${formatDate(d)}")
                else -> tr("Taux du ${formatDate(d)}", "Rates of ${formatDate(d)}")
            }
            AssistChip(
                onClick = onRefresh,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(if (off) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone, null, Modifier.size(AssistChipDefaults.IconSize)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (off) c.errorContainer else c.secondaryContainer,
                    labelColor = if (off) c.onErrorContainer else c.onSecondaryContainer,
                    leadingIconContentColor = if (off) c.onErrorContainer else c.onSecondaryContainer,
                ),
                border = null,
                shape = RoundedCornerShape(16.dp),
            )
        }
        if (loading && hasData) LoadingIndicator(Modifier.size(40.dp))
        else IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, tr("Actualiser", "Refresh")) }
    }
}

private fun formatDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Lang.locale))
}.getOrDefault(iso)

@Composable
private fun CurrencyCard(code: String, onPick: () -> Unit, highlighted: Boolean = false, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (highlighted) c.primaryContainer else c.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(code, transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith fadeOut() }, label = "flag") { cd ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(PolygonShape(MaterialShapes.Cookie9Sided))
                        .background(if (highlighted) c.primary.copy(alpha = 0.16f) else c.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Text(currencyFlag(cd), style = MaterialTheme.typography.titleLarge) }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (highlighted) c.onPrimaryContainer else c.onSurface)
                Text(currencyName(code), style = MaterialTheme.typography.bodySmall, color = if (highlighted) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ExpandMore, tr("Choisir la devise", "Choose currency"), tint = if (highlighted) c.onPrimaryContainer else c.onSurfaceVariant)
        }
        Box(Modifier.padding(top = 8.dp, end = 72.dp)) { content() }
    }
}

@Composable
private fun CurrencyPicker(codes: List<String>, rates: Map<String, Double>, current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val items = remember(codes, query) {
        val q = query.trim().folded()
        if (q.isEmpty()) codes
        else codes.filter { code ->
            code.lowercase().contains(q) ||
                currencyName(code, Locale.FRENCH).folded().contains(q) ||
                currencyName(code, Locale.ENGLISH).folded().contains(q)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(tr("Rechercher (code ou nom)", "Search (code or name)")) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            AnimatedVisibility(items.isEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(tr("Aucune devise trouvée", "No currency found"), Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(12.dp)) {
                items(items, key = { it }) { code ->
                    val selected = code == current
                    val c = MaterialTheme.colorScheme
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) c.secondaryContainer else c.surfaceContainerLow)
                            .clickable { onPick(code) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(currencyFlag(code), style = MaterialTheme.typography.headlineSmall)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(currencyName(code), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        rates[code]?.let { Text("1 € = ${Fmt.sig(it, 5)}", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant) }
                    }
                    Spacer(Modifier.size(4.dp))
                }
            }
        }
    }
}
