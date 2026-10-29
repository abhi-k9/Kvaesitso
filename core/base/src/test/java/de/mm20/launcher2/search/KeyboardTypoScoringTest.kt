package de.mm20.launcher2.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardTypoScoringTest {
    private fun score(query: String, label: String): Float =
        ResultScore.from(query, primaryFields = listOf(label)).score

    private fun matches(query: String, label: String): Boolean =
        score(query, label) >= ResultScore.MatchThreshold

    @Test
    fun settingsDoesNotDisappearWhenAnAdjacentKeyTypoIsOnlyFourCharactersLong() {
        for (query in listOf("swtt", "swtti")) {
            for (label in listOf("settings", "bixby settings", "binary eye settings")) {
                assertTrue("$query should find $label", matches(query, label))
                assertTrue("$query is a correction, not a literal match", score(query, label) < 0.9f)
            }
        }
    }

    @Test
    fun adjacentKeyCanBeMistypedAtEitherEndOfThePrefix() {
        assertTrue(matches("aett", "settings")) // A is next to S.
        assertTrue(matches("setr", "settings")) // R is next to T.
    }

    @Test
    fun fourCharactersDoNotPermitAnArbitrarySubstitutionOrTwoWrongLetters() {
        assertFalse(matches("sptt", "settings")) // P is far from E.
        assertFalse(matches("swxt", "settings"))
    }

    @Test
    fun oneAndTwoCharacterQueriesStillRequireLiteralEvidence() {
        assertFalse(matches("w", "settings"))
        assertFalse(matches("sw", "settings"))
        assertTrue(matches("se", "settings"))
    }

    @Test
    fun keyboardGuessDoesNotCrossWordBoundariesOrCompleteShorterWords() {
        assertFalse(matches("swtt", "set things"))
        assertFalse(matches("swtt", "set"))
        assertFalse(matches("swtt", "mysettings"))
        assertTrue(matches("swtt", "my settings"))
    }

    @Test
    fun nearbySubstitutionRanksAboveAnEquallySizedDistantSubstitution() {
        val nearby = score("swtti", "settings")
        val distant = score("swtti", "sottings")
        assertTrue(distant >= ResultScore.MatchThreshold)
        assertTrue("nearby $nearby should outrank distant $distant", nearby > distant)
        assertTrue(nearby < ResultScore.WeakLiteralBand)
    }

    @Test
    fun aGenericCorrectionInAnEarlierWordDoesNotHideABetterKeyboardCorrection() {
        val nearby = score("swtti", "settings")
        assertEquals(nearby, score("swtti", "sottings settings"), 0.0001f)
        assertEquals(nearby, score("swtti", "settings sottings"), 0.0001f)
    }

    @Test
    fun discountedKeyboardCostsDoNotPermitTooManyActualEdits() {
        assertFalse(matches("swtyi", "settings")) // W/E and Y/T: two edits in five characters.
        // A longer word keeps whole-word similarity from supplying an independent match.
        assertTrue(matches("calxulatp", "calculatoradvanced")) // X/C and P/O.
        assertFalse(matches("calxulstp", "calculatoradvanced")) // Also S/A: three edits.
    }

    @Test
    fun literalMatchRanksAboveAKeyboardCorrectionEvenIfTheCorrectionIsUsedMore() {
        val ranked = rankSearchResults(
            listOf("settings", "swtt helper"),
            score = { score("swtt", it) },
            weight = { if (it == "settings") 1.0 else 0.0 },
        )
        assertEquals(listOf("swtt helper", "settings"), ranked)
        assertTrue(score("sett", "settings") > score("swtt", "settings"))
    }

    @Test
    fun explicitLearnedChoiceStillComesBeforeAnUnlearnedLiteralMatch() {
        val ranked = rankSearchResults(
            listOf("swtt helper", "settings"),
            score = { score("swtt", it) },
            weight = { 0.0 },
            learnedRank = { if (it == "settings") 0 else null },
        )
        assertEquals(listOf("settings", "swtt helper"), ranked)
    }

    @Test
    fun nonLatinQueriesKeepGenericTypoMatchingWithoutQwertyGuesses() {
        assertTrue(matches("првиет", "привет"))
        assertTrue(score("првиет", "привет") < ResultScore.WeakLiteralBand)
        assertFalse(matches("абвг", "абдгдежз"))
    }
}
