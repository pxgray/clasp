package io.github.pxgray.clasp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.pxgray.clasp.domain.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Only has an effect on Android 12+. */
    val dynamicColor: Boolean = true,
    /** Turn the screen to full brightness while a card is open. Off by default, like Google Wallet. */
    val maxBrightness: Boolean = false,
    /** How the home list is ordered; chosen from the home screen's sort menu. */
    val sortOrder: SortOrder = SortOrder.RECENTLY_USED,
) {
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
                maxBrightness = prefs[MAX_BRIGHTNESS] ?: false,
                sortOrder = prefs[SORT_ORDER]?.let { name -> SortOrder.entries.firstOrNull { it.name == name } }
                    ?: SortOrder.RECENTLY_USED,
            )
        }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setMaxBrightness(enabled: Boolean) {
        store.edit { it[MAX_BRIGHTNESS] = enabled }
    }

    suspend fun setSortOrder(order: SortOrder) {
        store.edit { it[SORT_ORDER] = order.name }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val MAX_BRIGHTNESS = booleanPreferencesKey("max_brightness")
        private val SORT_ORDER = stringPreferencesKey("sort_order")

        /** Call once per process: DataStore allows a single instance per file. */
        fun create(context: Context) = SettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
        )
    }
}
