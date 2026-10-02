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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Only has an effect on Android 12+. */
    val dynamicColor: Boolean = true,
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
            )
        }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        /** Call once per process: DataStore allows a single instance per file. */
        fun create(context: Context) = SettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
        )
    }
}
