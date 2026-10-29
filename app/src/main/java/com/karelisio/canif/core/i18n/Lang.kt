package com.karelisio.canif.core.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Tiny bilingual layer: every user-facing string is written as a FR/EN pair next to the code
 * that uses it. The current language is snapshot state, so composables reading [tr] recompose
 * instantly when the user switches language in the settings.
 */
object Lang {
    /** "system", "fr" or "en" as stored in the settings. */
    var preference by mutableStateOf("system")

    val isFr: Boolean
        get() = when (preference) {
            "fr" -> true
            "en" -> false
            else -> Locale.getDefault().language == "fr"
        }

    val locale: Locale get() = if (isFr) Locale.FRENCH else Locale.ENGLISH
}

/** A translatable text, resolved lazily against the current language. */
data class Txt(val fr: String, val en: String) {
    val text: String get() = if (Lang.isFr) fr else en
    override fun toString(): String = text
}

fun tr(fr: String, en: String): String = if (Lang.isFr) fr else en
