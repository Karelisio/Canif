package com.karelisio.canif.core.tools

import android.content.Context
import com.karelisio.canif.tools.media.mediaTools
import com.karelisio.canif.tools.instruments.instrumentTools
import com.karelisio.canif.tools.color.colorTools
import com.karelisio.canif.tools.outdoor.outdoorTools
import com.karelisio.canif.tools.qr.qrTools
import java.text.Normalizer

object ToolRegistry {
    /** Every tool of Canif, in display order. */
    val all: List<Tool> by lazy {
        listOf<List<Tool>>(
            mediaTools,
            instrumentTools,
            com.karelisio.canif.tools.time.timeTools,
            com.karelisio.canif.tools.audio.audioTools,
            qrTools,
            colorTools,
            outdoorTools,
        ).flatten()
    }

    private val byId by lazy { all.associateBy { it.id } }

    fun get(id: String): Tool? = byId[id]

    @Volatile private var availableCache: List<Tool>? = null

    /** Tools usable on this device (sensor/feature checks), cached. */
    fun available(context: Context): List<Tool> =
        availableCache ?: all.filter { it.isAvailable(context.applicationContext) }.also { availableCache = it }

    fun byCategory(context: Context): Map<Category, List<Tool>> =
        available(context).groupBy { it.category }.toSortedMap(compareBy { it.ordinal })

    /** Instant search on names (FR + EN) and keywords, accent and case insensitive. */
    fun search(context: Context, query: String): List<Tool> {
        val q = query.normalized()
        if (q.isBlank()) return emptyList()
        val terms = q.split(' ').filter { it.isNotBlank() }
        return available(context).mapNotNull { tool ->
            val names = listOf(tool.name.fr, tool.name.en).map { it.normalized() }
            val keys = tool.keywords.map { it.normalized() } + tool.category.title.fr.normalized() + tool.category.title.en.normalized()
            var score = 0
            for (t in terms) {
                score += when {
                    names.any { it.startsWith(t) } -> 100
                    names.any { n -> n.split(' ').any { it.startsWith(t) } } -> 60
                    names.any { it.contains(t) } -> 40
                    keys.any { it.startsWith(t) } -> 30
                    keys.any { it.contains(t) } -> 15
                    else -> return@mapNotNull null
                }
            }
            tool to score
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun String.normalized(): String =
        Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
