package com.karelisio.canif.tools.qr

import com.karelisio.canif.CanifApp
import com.karelisio.canif.data.PresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

data class QrHistoryItem(val id: Long, val raw: String, val format: String, val at: Long) {
    val content: QrContent by lazy { QrParser.parse(raw) }
}

/** Short scan history stored as presets of kind "qr-history" (newest first, at most [MAX] entries). */
object QrHistory {
    private const val KIND = "qr-history"
    private const val MAX = 25
    const val ENABLED_KEY = "qr_history_enabled"

    private val dao get() = CanifApp.instance.db.presets()

    val items: Flow<List<QrHistoryItem>> = dao.observe(KIND).map { list ->
        list.mapNotNull { p ->
            runCatching {
                val o = JSONObject(p.data)
                QrHistoryItem(p.id, o.getString("raw"), o.optString("format"), p.createdAt)
            }.getOrNull()
        }.sortedByDescending { it.at }
    }

    val enabled: Flow<Boolean> get() = CanifApp.instance.settings.bool(ENABLED_KEY, true)

    fun setEnabled(v: Boolean) = CanifApp.instance.appScope.launch { CanifApp.instance.settings.setBool(ENABLED_KEY, v) }

    fun add(raw: String, format: String) = CanifApp.instance.appScope.launch {
        if (!CanifApp.instance.settings.bool(ENABLED_KEY, true).first()) return@launch
        val existing = dao.observe(KIND).first()
        existing.filter { runCatching { JSONObject(it.data).getString("raw") }.getOrNull() == raw }.forEach { dao.delete(it.id) }
        val data = JSONObject().put("raw", raw).put("format", format).toString()
        dao.insert(PresetEntity(kind = KIND, name = format, data = data))
        dao.observe(KIND).first().sortedByDescending { it.createdAt }.drop(MAX).forEach { dao.delete(it.id) }
    }

    fun delete(id: Long) = CanifApp.instance.appScope.launch { dao.delete(id) }

    fun clear() = CanifApp.instance.appScope.launch { dao.observe(KIND).first().forEach { dao.delete(it.id) } }
}
