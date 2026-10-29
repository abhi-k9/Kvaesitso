package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.LauncherSettingsData
import de.mm20.launcher2.search.KeyboardLayout
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeyboardLayoutSettingsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun existingSettingsDefaultToQwertyWithoutLosingOtherPreferences() {
        val settings = json.decodeFromString<LauncherSettingsData>(
            """{"searchAllApps":false,"rankingLearnFromLaunches":false}""",
        )
        assertEquals(KeyboardLayout.Qwerty, KeyboardLayout.fromId(settings.searchKeyboardLayout))
        assertFalse(settings.searchAllApps)
        assertFalse(settings.rankingLearnFromLaunches)
    }

    @Test
    fun everyLayoutSurvivesSettingsSerialization() {
        for (layout in KeyboardLayout.entries) {
            val original = LauncherSettingsData(searchKeyboardLayout = layout.name, searchBarBottom = true)
            val restored = json.decodeFromString<LauncherSettingsData>(json.encodeToString(original))
            assertEquals(layout, KeyboardLayout.fromId(restored.searchKeyboardLayout))
            assertTrue(restored.searchBarBottom)
        }
    }

    @Test
    fun aFutureLayoutFallsBackWithoutDiscardingTheUnknownIdOrOtherSettings() {
        val settings = json.decodeFromString<LauncherSettingsData>(
            """{"searchKeyboardLayout":"FutureLayout","searchBarBottom":true}""",
        )
        assertEquals(KeyboardLayout.Qwerty, KeyboardLayout.fromId(settings.searchKeyboardLayout))
        val restored = json.decodeFromString<LauncherSettingsData>(json.encodeToString(settings))
        assertEquals("FutureLayout", restored.searchKeyboardLayout)
        assertTrue(restored.searchBarBottom)
    }

    @Test
    fun changingTheLayoutIsReflectedByTheSameSettingsInstance() = runBlocking {
        val saved = MutableStateFlow(LauncherSettingsData(searchAllApps = false))
        val dataStore = mockk<LauncherDataStore>()
        every { dataStore.data } returns saved
        every { dataStore.update(any()) } answers {
            saved.value = firstArg<(LauncherSettingsData) -> LauncherSettingsData>()(saved.value)
        }
        val settings = SearchUiSettings(dataStore)
        assertEquals(KeyboardLayout.Qwerty, settings.keyboardLayout.first())

        settings.setKeyboardLayout(KeyboardLayout.Azerty)
        assertEquals("Azerty", saved.value.searchKeyboardLayout)
        assertEquals(KeyboardLayout.Azerty, settings.keyboardLayout.first())

        settings.setKeyboardLayout(KeyboardLayout.Disabled)
        assertEquals(KeyboardLayout.Disabled, settings.keyboardLayout.first())
        assertFalse(saved.value.searchAllApps)
    }
}
