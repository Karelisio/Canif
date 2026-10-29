package com.karelisio.canif.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class UnitSystem { METRIC, IMPERIAL }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val tintedSurfaces: Boolean = true,
    val haptics: Boolean = true,
    val referenceA4: Int = 440,
    val units: UnitSystem = UnitSystem.METRIC,
    val language: String = "system",
    val onboardingDone: Boolean = false,
)

/** All preferences of the app. Tool-specific values (calibrations, best scores…) use the generic accessors. */
class SettingsRepository(private val context: Context) {
    private val store get() = context.dataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val tinted = booleanPreferencesKey("tinted_surfaces")
        val haptics = booleanPreferencesKey("haptics")
        val a4 = intPreferencesKey("reference_a4")
        val units = stringPreferencesKey("units")
        val language = stringPreferencesKey("language")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: true,
            tintedSurfaces = p[Keys.tinted] ?: true,
            haptics = p[Keys.haptics] ?: true,
            referenceA4 = p[Keys.a4] ?: 440,
            units = p[Keys.units]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.METRIC,
            language = p[Keys.language] ?: "system",
            onboardingDone = p[Keys.onboarding] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(v: ThemeMode) = store.edit { it[Keys.theme] = v.name }
    suspend fun setDynamicColor(v: Boolean) = store.edit { it[Keys.dynamic] = v }
    suspend fun setTintedSurfaces(v: Boolean) = store.edit { it[Keys.tinted] = v }
    suspend fun setHaptics(v: Boolean) = store.edit { it[Keys.haptics] = v }
    suspend fun setReferenceA4(v: Int) = store.edit { it[Keys.a4] = v.coerceIn(432, 445) }
    suspend fun setUnits(v: UnitSystem) = store.edit { it[Keys.units] = v.name }
    suspend fun setLanguage(v: String) = store.edit { it[Keys.language] = v }
    suspend fun setOnboardingDone(v: Boolean) = store.edit { it[Keys.onboarding] = v }

    // Generic typed accessors for tool state.
    fun float(key: String, default: Float): Flow<Float> = store.data.map { it[floatPreferencesKey(key)] ?: default }
    suspend fun setFloat(key: String, value: Float) = store.edit { it[floatPreferencesKey(key)] = value }
    fun long(key: String, default: Long): Flow<Long> = store.data.map { it[longPreferencesKey(key)] ?: default }
    suspend fun setLong(key: String, value: Long) = store.edit { it[longPreferencesKey(key)] = value }
    fun string(key: String, default: String): Flow<String> = store.data.map { it[stringPreferencesKey(key)] ?: default }
    suspend fun setString(key: String, value: String) = store.edit { it[stringPreferencesKey(key)] = value }
    fun bool(key: String, default: Boolean): Flow<Boolean> = store.data.map { it[booleanPreferencesKey(key)] ?: default }
    suspend fun setBool(key: String, value: Boolean) = store.edit { it[booleanPreferencesKey(key)] = value }
}
