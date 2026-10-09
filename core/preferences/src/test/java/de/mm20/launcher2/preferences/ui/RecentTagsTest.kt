package de.mm20.launcher2.preferences.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTagsTest {

    @Test
    fun theLastAddedTagComesFirst() {
        assertEquals(
            listOf("Work", "Games", "Social"),
            listOf("Social").withRecent(listOf("Games", "Work")),
        )
    }

    @Test
    fun aTagUsedAgainMovesToTheTop() {
        assertEquals(
            listOf("Social", "Games", "Work"),
            listOf("Games", "Social", "Work").withRecent(listOf("Social")),
        )
    }

    @Test
    fun onlyTheLastThreeAreKept() {
        assertEquals(listOf("D", "C", "B"), listOf("C", "B", "A").withRecent(listOf("D")))
        assertEquals(listOf("E", "D", "C"), listOf("A").withRecent(listOf("B", "C", "D", "E")))
    }
}
