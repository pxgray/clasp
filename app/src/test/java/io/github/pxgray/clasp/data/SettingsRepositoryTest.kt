package io.github.pxgray.clasp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.pxgray.clasp.domain.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") },
    )

    @Test
    fun `defaults follow the system with dynamic color`() = runTest {
        assertEquals(AppSettings(ThemeMode.SYSTEM, dynamicColor = true), repository().settings.first())
    }

    @Test
    fun `changes are persisted`() = runTest {
        val repository = repository()
        repository.setTheme(ThemeMode.DARK)
        repository.setDynamicColor(false)
        assertEquals(AppSettings(ThemeMode.DARK, dynamicColor = false), repository.settings.first())
    }

    @Test
    fun `theme mode resolves against the system`() {
        assertTrue(AppSettings(ThemeMode.SYSTEM).isDark(systemDark = true))
        assertFalse(AppSettings(ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(AppSettings(ThemeMode.DARK).isDark(systemDark = false))
    }

    @Test
    fun `barcode brightness is off by default and can be turned on`() = runTest {
        val repository = repository()
        assertFalse(repository.settings.first().maxBrightness)
        repository.setMaxBrightness(true)
        assertTrue(repository.settings.first().maxBrightness)
    }

    @Test
    fun `sort order defaults to recently used and is persisted`() = runTest {
        val repository = repository()
        assertEquals(SortOrder.RECENTLY_USED, repository.settings.first().sortOrder)
        repository.setSortOrder(SortOrder.NAME)
        assertEquals(SortOrder.NAME, repository.settings.first().sortOrder)
    }

    @Test
    fun `an unknown sort order reads as the default`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") }
        store.edit { it[stringPreferencesKey("sort_order")] = "SHUFFLE" }
        assertEquals(SortOrder.RECENTLY_USED, SettingsRepository(store).settings.first().sortOrder)
    }
}
