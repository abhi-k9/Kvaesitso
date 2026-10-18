package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.ui.WidgetWidth.Full
import de.mm20.launcher2.preferences.ui.WidgetWidth.Half
import de.mm20.launcher2.preferences.ui.WidgetWidth.Quarter
import de.mm20.launcher2.preferences.ui.WidgetWidth.Third
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetWidthsTest {

    @Test
    fun fullWidthWidgetsHaveARowEach() {
        assertEquals(listOf(listOf(0), listOf(1)), widgetRows(listOf(Full, Full)))
    }

    @Test
    fun narrowWidgetsShareARow() {
        assertEquals(listOf(listOf(0, 1)), widgetRows(listOf(Half, Half)))
        assertEquals(listOf(listOf(0, 1, 2)), widgetRows(listOf(Third, Third, Third)))
        assertEquals(listOf(listOf(0, 1, 2, 3)), widgetRows(listOf(Quarter, Quarter, Quarter, Quarter)))
        assertEquals(listOf(listOf(0, 1, 2)), widgetRows(listOf(Half, Quarter, Quarter)))
    }

    @Test
    fun aWidgetThatDoesNotFitStartsANewRow() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), widgetRows(listOf(Half, Third, Half)))
        assertEquals(
            listOf(listOf(0, 1, 2, 3), listOf(4)),
            widgetRows(listOf(Quarter, Quarter, Quarter, Quarter, Quarter)),
        )
    }

    @Test
    fun fullWidthWidgetsSplitNarrowOnes() {
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3)),
            widgetRows(listOf(Half, Full, Half, Half)),
        )
    }

    @Test
    fun unknownWidthsAreFullWidth() {
        assertEquals(Full, WidgetWidth.ofFraction(null))
        assertEquals(Full, WidgetWidth.ofFraction(5))
        assertEquals(Third, WidgetWidth.ofFraction(3))
    }

    @Test
    fun noWidgetsNoRows() {
        assertEquals(emptyList<List<Int>>(), widgetRows(emptyList()))
    }
}
