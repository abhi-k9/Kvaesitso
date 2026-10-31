package de.mm20.launcher2.ui.launcher.sheets

import android.content.Context
import android.os.Bundle
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPickerStateTest {
    private data class Item(
        override val key: String,
        override val label: String,
        override val labelOverride: String? = null,
    ) : SavableSearchable {
        override val domain = "app"
        override val preferDetailsOverLaunch = false
        override fun overrideLabel(label: String) = copy(labelOverride = label)
        override fun launch(context: Context, options: Bundle?) = false
        override fun getPlaceholderIcon(context: Context): StaticLauncherIcon = error("Unused")
        override fun getSerializer(): SearchableSerializer = error("Unused")
    }

    private val cafe = Item("app://org.example.cafe", "Café Maps", "Travel")
    private val camera = Item("app://org.example.camera", "Camera")
    private val items = listOf(
        TaggableItem(cafe, false, hasAnyTags = true),
        TaggableItem(camera, false, hasAnyTags = false),
    )

    @Test
    fun blankSearchKeepsEveryCandidate() {
        assertSame(items, filterTaggableItems(items, "  ", false))
    }

    @Test
    fun searchMatchesAccentsAliasesPackagesAndWordsInAnyOrder() {
        for (query in listOf("CAFE", "Travel", "org.example.cafe", "maps café", "  café   maps  ")) {
            assertEquals(listOf(cafe), filterTaggableItems(items, query, false).map { it.item })
        }
        assertTrue(filterTaggableItems(items, "camera travel", false).isEmpty())
    }

    @Test
    fun untaggedMeansNoSavedTagNotUncheckedInThisPicker() {
        val selectedNewItem = TaggableItem(camera, true, hasAnyTags = false)

        assertEquals(
            listOf(camera),
            filterTaggableItems(listOf(items.first(), selectedNewItem), "", true).map { it.item },
        )
        assertTrue(filterTaggableItems(items, "café", true).isEmpty())
    }

    @Test
    fun changingFiltersNeverDiscardsCheckedItems() {
        val selection = TagPickerSelection(listOf(cafe))
        selection.set(camera, true)

        assertEquals(listOf(camera), filterTaggableItems(items, "camera", true).map { it.item })
        assertTrue(filterTaggableItems(items, "no such app", false).isEmpty())

        assertEquals(listOf(cafe, camera), selection.items)
        assertEquals(setOf(cafe.key, camera.key), selection.keys)
    }

    @Test
    fun checkingAnAlreadySelectedKeyDoesNotDuplicateIt() {
        val selection = TagPickerSelection(listOf(cafe, cafe.copy(label = "Other label")))
        selection.set(cafe.copy(label = "Changed label"), true)

        assertEquals(listOf(cafe), selection.items)
    }

    @Test
    fun uncheckingUsesIdentityKeyAndRetainsOtherSelections() {
        val selection = TagPickerSelection(listOf(cafe, camera))
        selection.set(cafe.copy(label = "Changed label"), false)

        assertEquals(listOf(camera), selection.items)
    }
}
