package de.mm20.launcher2.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedSearchesTest {

    private fun List<LearnedSearch>.record(query: String, key: String, time: Long) =
        LearnedSearches.record(this, query, key, time)

    @Test
    fun openingAResultLearnsIt() {
        val entries = emptyList<LearnedSearch>().record("tel", "telegram", 1)
        assertEquals(listOf(LearnedSearch("tel", "telegram", 1, 1)), entries)
        val again = entries.record("tel", "telegram", 2)
        assertEquals(listOf(LearnedSearch("tel", "telegram", 2, 2)), again)
    }

    @Test
    fun aLearnedResultCountsForTheBeginningOfItsQuery() {
        val entries = emptyList<LearnedSearch>().record("tel", "telegram", 1)
        assertEquals(mapOf("telegram" to 0), LearnedSearches.ranks(entries, "t"))
        assertEquals(mapOf("telegram" to 0), LearnedSearches.ranks(entries, "te"))
        assertEquals(mapOf("telegram" to 0), LearnedSearches.ranks(entries, "tel"))
        // Typed more than before: not learned for that
        assertEquals(emptyMap<String, Int>(), LearnedSearches.ranks(entries, "tele"))
        assertEquals(emptyMap<String, Int>(), LearnedSearches.ranks(entries, "x"))
    }

    @Test
    fun theMostOpenedResultComesFirst() {
        val entries = emptyList<LearnedSearch>()
            .record("t", "twitter", 1)
            .record("tel", "telegram", 2)
            .record("tel", "telegram", 3)
            .record("tel", "telegram", 4)
            .record("tel", "telegram", 5)
        // "tel" counts a third for "t": telegram 4/3, twitter 1
        assertEquals(mapOf("telegram" to 0, "twitter" to 1), LearnedSearches.ranks(entries, "t"))
    }

    @Test
    fun aNewHabitTakesOver() {
        var entries = emptyList<LearnedSearch>()
        repeat(3) { entries = entries.record("c", "calendar", it.toLong()) }
        entries = entries.record("c", "camera", 10).record("c", "camera", 11)
        assertEquals(0, LearnedSearches.ranks(entries, "c")["camera"])
        entries = entries.record("c", "camera", 12)
        // Calendar has lost its weight for "c"
        assertEquals(mapOf("camera" to 0), LearnedSearches.ranks(entries, "c"))
    }

    @Test
    fun equalWeightsAreOrderedByLastUse() {
        val entries = emptyList<LearnedSearch>()
            .record("ca", "calendar", 1)
            .record("cam", "camera", 2)
            .record("cal", "calculator", 3)
        val ranks = LearnedSearches.ranks(entries, "ca")
        assertEquals(0, ranks["calendar"])
        assertEquals(1, ranks["calculator"])
        assertEquals(2, ranks["camera"])
    }

    @Test
    fun onlyTheMostRecentEntriesAreKept() {
        var entries = emptyList<LearnedSearch>()
        for (i in 0 until LearnedSearches.MaxEntries + 10) {
            entries = entries.record("q$i", "key$i", i.toLong())
        }
        assertEquals(LearnedSearches.MaxEntries, entries.size)
        assertTrue(entries.none { it.key == "key0" })
        assertTrue(entries.any { it.key == "key${LearnedSearches.MaxEntries + 9}" })
    }

    @Test
    fun emptyQueriesAreNotLearned() {
        assertEquals(emptyList<LearnedSearch>(), emptyList<LearnedSearch>().record("", "telegram", 1))
        val entries = emptyList<LearnedSearch>().record("tel", "telegram", 1)
        assertEquals(emptyMap<String, Int>(), LearnedSearches.ranks(entries, ""))
    }

    @Test
    fun queriesAreCleanedUp() {
        assertEquals("google maps", LearnedSearches.cleanQuery(" google  maps "))
    }

    @Test
    fun entriesOfAResultCanBeRemoved() {
        val entries = emptyList<LearnedSearch>()
            .record("tel", "telegram", 1)
            .record("t", "twitter", 2)
        assertEquals(listOf("twitter"), LearnedSearches.withoutKey(entries, "telegram").map { it.key })
    }
}
