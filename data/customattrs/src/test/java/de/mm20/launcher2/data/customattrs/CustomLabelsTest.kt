package de.mm20.launcher2.data.customattrs

import android.content.Context
import android.os.Bundle
import de.mm20.launcher2.data.customattrs.utils.withCustomLabels
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CustomLabelsTest {
    private data class Item(
        override val key: String,
        override val label: String,
        val metadata: String,
        override val labelOverride: String? = null,
    ) : SavableSearchable {
        override val domain = "app"
        override val preferDetailsOverLaunch = false
        override fun overrideLabel(label: String) = copy(labelOverride = label)
        override fun launch(context: Context, options: Bundle?) = false
        override fun getPlaceholderIcon(context: Context): StaticLauncherIcon = error("Unused")
        override fun getSerializer(): SearchableSerializer = error("Unused")
    }

    private val app = Item("app://a", "App", "first version")
    private val other = Item("app://other", "Other", "unchanged")
    private val customAttributes = mockk<CustomAttributesRepository>()

    @Test
    fun firstDuplicateLabelWinsAndUnmodifiedItemsKeepTheirIdentity() = runBlocking {
        every { customAttributes.getCustomLabels(any()) } returns flowOf(
            listOf(CustomLabel(app.key, "Preferred"), CustomLabel(app.key, "Ignored")),
        )

        val results = flowOf(listOf(app, other)).withCustomLabels(customAttributes).first()

        assertEquals("Preferred", results.first().labelOverride)
        assertEquals(app.metadata, results.first().metadata)
        assertSame(other, results.last())
    }

    @Test
    fun changingOrRemovingACustomLabelUpdatesAnActiveList() = runBlocking {
        val labels = MutableStateFlow(listOf(CustomLabel(app.key, "First")))
        every { customAttributes.getCustomLabels(any()) } returns labels
        val overrides = mutableListOf<String?>()

        withTimeout(5_000) {
            flowOf(listOf(app)).withCustomLabels(customAttributes).onEach { results ->
                overrides += results.single().labelOverride
                when (overrides.size) {
                    1 -> labels.value = listOf(CustomLabel(app.key, "Second"))
                    2 -> labels.value = emptyList()
                }
            }.take(3).collect()
        }

        assertEquals(listOf("First", "Second", null), overrides)
    }

    @Test
    fun updatedItemMetadataIsRetainedWithAnUnchangedCustomLabel() = runBlocking {
        val items = MutableStateFlow(listOf(app))
        every { customAttributes.getCustomLabels(any()) } returns flowOf(listOf(CustomLabel(app.key, "Mine")))
        val results = mutableListOf<Item>()

        withTimeout(5_000) {
            items.withCustomLabels(customAttributes).onEach { list ->
                results += list.single()
                if (results.size == 1) items.value = listOf(app.copy(metadata = "updated version"))
            }.take(2).collect()
        }

        assertEquals(listOf("first version", "updated version"), results.map { it.metadata })
        assertEquals(listOf("Mine", "Mine"), results.map { it.labelOverride })
    }
}
