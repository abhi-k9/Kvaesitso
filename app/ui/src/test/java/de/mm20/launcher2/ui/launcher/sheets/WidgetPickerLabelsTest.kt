package de.mm20.launcher2.ui.launcher.sheets

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetPickerLabelsTest {
    @Test
    fun packageLabelsAreLoadedOnceForSeveralWidgets() {
        val widgets = listOf(widget("calendar", "Agenda"), widget("calendar", "Month"), widget("weather", "Forecast"))
        val appCalls = mutableListOf<String>()
        var widgetCalls = 0

        val labels = resolveWidgetProviderLabels(widgets, {
            widgetCalls++
            it.provider.className
        }, {
            appCalls += it
            it.uppercase()
        })

        // Filtering/sorting the snapshot for multiple queries needs no more Android lookups.
        for (query in listOf("a", "ag", "agenda")) {
            labels.filter { query in it.widgetLabel.lowercase() }.sortedBy { it.appLabel }
        }
        assertEquals(listOf("calendar", "weather"), appCalls)
        assertEquals(3, widgetCalls)
        assertEquals(listOf("CALENDAR", "CALENDAR", "WEATHER"), labels.map { it.appLabel })
    }

    @Test
    fun missingPackageIsNotQueriedAgainWithinTheSnapshot() {
        var calls = 0
        val labels = resolveWidgetProviderLabels(listOf(widget("missing", "One"), widget("missing", "Two")), {
            it.provider.className
        }, {
            calls++
            null
        })
        assertEquals(1, calls)
        assertNull(labels.first().appLabel)
    }

    @Test
    fun aNewProviderSnapshotRefreshesLabels() {
        val widgets = listOf(widget("calendar", "Agenda"))
        fun snapshot(label: String) = resolveWidgetProviderLabels(widgets, { label }, { label })
        val first = snapshot("Old label")
        val second = snapshot("Updated label")
        assertEquals("Old label", first.single().appLabel)
        assertEquals("Updated label", second.single().appLabel)
        assertEquals("Updated label", second.single().widgetLabel)
    }

    private fun widget(pkg: String, name: String) = AppWidgetProviderInfo().apply {
        provider = ComponentName(pkg, name)
    }
}
