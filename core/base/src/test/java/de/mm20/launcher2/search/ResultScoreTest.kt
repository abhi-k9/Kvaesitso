package de.mm20.launcher2.search

import org.junit.Assert
import org.junit.Test

class ResultScoreTest {

    private fun score(query: String, term: String): Float =
        ResultScore.from(query, primaryFields = listOf(term)).score

    private fun matches(query: String, term: String): Boolean =
        score(query, term) >= ResultScore.MatchThreshold

    @Test
    fun exactMatchScoresOne() {
        Assert.assertEquals(1f, score("chrome", "chrome"), 0f)
    }

    @Test
    fun emptyQueryMatchesEverything() {
        Assert.assertEquals(1f, score("", "chrome"), 0f)
    }

    @Test
    fun matchesSubstrings() {
        Assert.assertTrue(matches("chr", "chrome"))
        Assert.assertTrue(matches("hrom", "chrome"))
        Assert.assertTrue(matches("music", "yt music"))
    }

    @Test
    fun matchesAbbreviations() {
        Assert.assertTrue(matches("ytm", "yt music"))
        Assert.assertTrue(matches("pstr", "play store"))
        Assert.assertTrue(matches("ktso", "kvaesitso"))
        Assert.assertTrue(matches("ggl", "google"))
        Assert.assertTrue(matches("cnt", "contacts"))
    }

    @Test
    fun toleratesTypos() {
        // Neither query is a subsequence of its label, so these can only match by similarity.
        Assert.assertTrue(matches("settimgs", "settings"))
        Assert.assertTrue(matches("chorme", "chrome"))
    }

    @Test
    fun toleratesTyposInOneWordOfTheName() {
        Assert.assertTrue(matches("photso", "google photos"))
        Assert.assertTrue(matches("fotos", "google photos"))
        Assert.assertTrue(matches("gallary", "simple gallery"))
        Assert.assertTrue(matches("teams", "microsoft teams"))
        // Still a guess: below a literal match
        Assert.assertTrue(score("photso", "google photos") < score("phts", "google photos"))
    }

    @Test
    fun wordTyposNeedLongerQueries() {
        Assert.assertFalse(matches("fot", "google photos"))
        Assert.assertFalse(matches("maps", "google photos"))
        Assert.assertFalse(matches("music", "google photos"))
        // Not a typo of "music" just because it starts with it
        Assert.assertFalse(matches("music maps", "youtube music"))
    }

    @Test
    fun toleratesDroppedCharacters() {
        Assert.assertTrue(matches("chrme", "chrome"))
        Assert.assertTrue(matches("settngs", "settings"))
    }

    @Test
    fun rejectsUnrelatedLabels() {
        Assert.assertFalse(matches("chrome", "calendar"))
        Assert.assertFalse(matches("maps", "settings"))
    }

    @Test
    fun noTypoCorrectionForShortQueries() {
        // Similar enough by Jaro-Winkler, but two characters are too little to guess a typo from.
        Assert.assertFalse(matches("ab", "a"))
    }

    @Test
    fun ranksPrefixOverScatteredOverTypo() {
        // Only the matched window is scored, so an exact match and a prefix match tie, as they
        // did when every substring match saturated to 1. Usage weights decide between them.
        val exact = score("chrome", "chrome")
        val prefix = score("chrome", "chrome beta")
        val scattered = score("chrome", "cash register over media")
        val typo = score("chorme", "chrome")
        Assert.assertEquals(exact, prefix, 0f)
        Assert.assertTrue("prefix $prefix > scattered $scattered", prefix > scattered)
        Assert.assertTrue("scattered $scattered > typo $typo", scattered > typo)
    }

    @Test
    fun literalMatchOutranksCloseTypo() {
        // A near-perfect typo still ranks below a loose literal match.
        Assert.assertTrue(score("settimgs", "settings") < score("stg", "settings"))
    }

    @Test
    fun primaryFieldOutranksIdenticalSecondaryField() {
        val primary = ResultScore.from("chrome", primaryFields = listOf("chrome"))
        val secondary = ResultScore.from("chrome", secondaryFields = listOf("chrome"))
        Assert.assertTrue(primary > secondary)
    }

    @Test
    fun extraSpacesAreIgnored() {
        // Keyboards add a space after a word picked from the suggestions
        Assert.assertEquals(1f, score("maps ", "google maps"), 0f)
        Assert.assertEquals(1f, score("chrome ", "chrome"), 0f)
        Assert.assertEquals(1f, score(" chrome", "chrome"), 0f)
        Assert.assertEquals(score("keep notes", "keep notes"), score("keep  notes", "keep notes"), 0f)
        Assert.assertEquals(score("goo m", "google maps"), score("goo \tm ", "google maps"), 0f)
        Assert.assertTrue(matches("go ", "google"))
    }

    @Test
    fun onlySpacesAreLikeAnEmptyQuery() {
        Assert.assertEquals(1f, score("  ", "chrome"), 0f)
    }

    @Test
    fun wordsCanBeInAnotherOrder() {
        Assert.assertTrue(matches("maps google", "google maps"))
        Assert.assertTrue(matches("music yt", "youtube music"))
        Assert.assertTrue(matches("camera open", "open camera"))
        Assert.assertTrue(matches("store play", "google play store"))
        // Typos in the words are still tolerated
        Assert.assertTrue(matches("mpas gogle", "google maps"))
    }

    @Test
    fun everyWordHasToMatch() {
        Assert.assertFalse(matches("maps google", "google photos"))
        Assert.assertFalse(matches("camera open", "google photos"))
        Assert.assertFalse(matches("music maps", "youtube music"))
    }

    @Test
    fun theOrderOfTheNameRanksFirst() {
        val inOrder = score("google maps", "google maps")
        val otherOrder = score("maps google", "google maps")
        Assert.assertTrue("$otherOrder < $inOrder", otherOrder < inOrder)
        // But a good match in another order is still a strong match
        Assert.assertTrue(otherOrder > score("gogle", "google maps"))
        Assert.assertTrue(otherOrder >= 0.95f)
    }

    @Test
    fun noFieldsIsNotAMatch() {
        Assert.assertEquals(ResultScore.Zero, ResultScore.from("chrome"))
    }
}
