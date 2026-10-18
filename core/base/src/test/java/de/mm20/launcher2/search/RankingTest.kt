package de.mm20.launcher2.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {

    private data class Item(val name: String, val weight: Double = 0.0)

    private fun rank(query: String, vararg items: Item): List<String> {
        return rankSearchResults(
            items.toList(),
            score = { ResultScore.from(query, primaryFields = listOf(it.name)).score },
            weight = { it.weight },
        ).map { it.name }
    }

    @Test
    fun aRealMatchRanksAboveATypoGuessHoweverOftenThatIsUsed() {
        assertEquals(
            listOf("notes", "notion"),
            rank("note", Item("notion", weight = 1.0), Item("notes")),
        )
    }

    @Test
    fun shortQueriesPreferNamesStartingWithThem() {
        assertEquals(
            listOf("calendar", "camera", "music", "discord"),
            rank(
                "c",
                Item("music", weight = 1.0),
                Item("discord", weight = 0.5),
                Item("calendar"),
                Item("camera"),
            ),
        )
        assertEquals(
            listOf("calendar", "authenticator"),
            rank("ca", Item("authenticator", weight = 1.0), Item("calendar")),
        )
    }

    @Test
    fun usageDecidesWithinTheSameKindOfMatch() {
        assertEquals(
            listOf("camera", "calendar"),
            rank("c", Item("calendar"), Item("camera", weight = 0.5)),
        )
        assertEquals(
            listOf("telegram x", "telegram"),
            rank("telegram", Item("telegram"), Item("telegram x", weight = 0.8)),
        )
    }

    @Test
    fun resultsThatDontMatchComeLastByUsage() {
        assertEquals(
            listOf("chrome", "work email", "work chat"),
            rank(
                "chr",
                Item("work chat", weight = 0.1),
                Item("work email", weight = 0.9),
                Item("chrome"),
            ),
        )
    }

    @Test
    fun equalResultsKeepTheirOrder() {
        assertEquals(
            listOf("chrome", "chrome beta", "chrome dev"),
            rank("chrome", Item("chrome"), Item("chrome beta"), Item("chrome dev")),
        )
    }
}
