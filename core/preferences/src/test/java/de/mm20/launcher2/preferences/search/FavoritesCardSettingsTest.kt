package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.LauncherSettingsData
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

class FavoritesCardSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val saved = MutableStateFlow(LauncherSettingsData())
    private val store = mockk<LauncherDataStore> {
        every { data } returns saved
        every { update(any()) } answers {
            saved.value = firstArg<(LauncherSettingsData) -> LauncherSettingsData>()(saved.value)
        }
    }
    private val settings = FavoritesSettings(store)

    @Test
    fun existingPreferencesInheritTheAppGridAndRowLimit() = runBlocking {
        saved.value = json.decodeFromString("""{"gridColumnCount":6,"gridIconSize":40,"gridLabels":false,"favoritesFrequentlyUsedRows":2}""")
        val card = settings.card.first()
        assertEquals(6, card.columns)
        assertEquals(40, card.iconSize)
        assertFalse(card.showLabels)
        assertEquals(-1, card.pinnedCount)
        assertEquals(4, card.recentCount)
        assertEquals(-1, card.frequentCount)
        assertEquals(2, card.frequentlyUsedRows)
    }

    @Test
    fun cardLayoutChangesDoNotChangeTheAppGridOrOtherFavorites() = runBlocking {
        settings.setCardColumns(7)
        settings.setCardIconSize(32)
        settings.setCardLabels(false)
        settings.setCardGroupLabels(false)
        assertEquals(7, settings.card.first().columns)
        assertEquals(32, settings.card.first().iconSize)
        assertFalse(settings.card.first().showLabels)
        assertEquals(5, saved.value.gridColumnCount)
        assertEquals(48, saved.value.gridIconSize)
        assertTrue(saved.value.gridLabels)
        assertEquals(5, settings.first().columns)
    }

    @Test
    fun independentLimitsAndDensitySurviveBackupSerialization() = runBlocking {
        settings.setCardPinnedCount(8)
        settings.setCardRecentCount(6)
        settings.setCardFrequentCount(12)
        settings.setCardColumns(8)
        settings.setCardIconSize(28)
        settings.setCardLabels(false)
        val before = settings.card.first()
        saved.value = json.decodeFromString(json.encodeToString(saved.value))
        assertEquals(before, settings.card.first())
    }

    @Test
    fun resettingDensityFollowsTheCurrentGridWithoutLosingQuotas() = runBlocking {
        settings.setCardColumns(8)
        settings.setCardIconSize(24)
        settings.setCardRecentCount(9)
        saved.value = saved.value.copy(gridColumnCount = 4, gridIconSize = 56)
        settings.resetCardLayout()
        assertEquals(4, settings.card.first().columns)
        assertEquals(56, settings.card.first().iconSize)
        assertEquals(9, settings.card.first().recentCount)
    }

    @Test
    fun invalidRestoredValuesAreBoundedAndGroupsCanBeDisabled() = runBlocking {
        saved.value = saved.value.copy(
            favoritesCardColumns = 1000, favoritesCardIconSize = 1,
            favoritesCardPinnedCount = -200, favoritesCardRecentCount = -10, favoritesCardFrequentCount = 999,
        )
        val card = settings.card.first()
        assertEquals(10, card.columns)
        assertEquals(24, card.iconSize)
        assertEquals(-1, card.pinnedCount)
        assertEquals(0, card.recentCount)
        assertEquals(60, card.frequentCount)
        settings.setCardPinnedCount(0)
        settings.setCardFrequentCount(0)
        assertEquals(0, settings.card.first().pinnedCount)
        assertEquals(0, settings.card.first().frequentCount)
    }

    @Test
    fun theFrequentlyUsedSwitchDoesNotDisableRecentsOrPins() = runBlocking {
        settings.setFrequentlyUsed(false)
        val card = settings.card.first()
        assertEquals(0, card.frequentCount)
        assertEquals(4, card.recentCount)
        assertEquals(-1, card.pinnedCount)
    }
}
