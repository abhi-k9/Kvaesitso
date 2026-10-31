package de.mm20.launcher2.ui.launcher.search

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchStateListTest {
    @Test
    fun unchangedProviderResultsKeepTheirSnapshot() {
        val results = mutableStateListOf("Settings", "Camera")
        val snapshot = results.toList()

        repeat(20) { results.updateItems(listOf("Settings", "Camera")) }

        assertSame(snapshot, results.toList())
    }

    @Test
    fun updatesReplaceMetadataEvenWhenKeysStayTheSame() {
        val results = mutableStateListOf(Item("app", "Old name"))

        results.updateItems(listOf(Item("app", "New name")))

        assertEquals("New name", results.single().label)
    }

    @Test
    fun changedOrderAndRepeatedItemsArePreserved() {
        val results = mutableStateListOf("Settings", "Camera")

        results.updateItems(listOf("Camera", "Camera", "Settings"))

        assertEquals(listOf("Camera", "Camera", "Settings"), results.toList())
    }

    @Test
    fun nullClearsStaleResults() {
        val results = mutableStateListOf("Settings")

        results.updateItems(null)

        assertEquals(emptyList<String>(), results.toList())
    }

    private data class Item(val key: String, val label: String)
}
