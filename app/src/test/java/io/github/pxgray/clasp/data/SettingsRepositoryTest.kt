package io.github.pxgray.clasp.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
