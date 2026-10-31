package de.mm20.launcher2.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedQueryTest {
    @Test
    fun reusableQueryKeepsWordMatchingAndCandidateScoresIndependent() {
        val query = ResultScore.prepare(" \t maps  google\n")

        val first = query.score(primaryFields = listOf("google maps"))
        assertTrue(first.isStrongMatch)
        assertFalse(query.score(primaryFields = listOf("calendar")).isMatch)
        assertEquals(first, query.score(primaryFields = listOf("google maps")))
    }

    @Test
    fun preparedKeyboardSelectionsRemainIndependentWhenScorersAreInterleaved() {
        val qwerty = ResultScore.prepare("swtt", KeyboardLayout.Qwerty)
        val azerty = ResultScore.prepare("swtt", KeyboardLayout.Azerty)

        assertTrue(qwerty.score(primaryFields = listOf("settings")).isMatch)
        assertFalse(azerty.score(primaryFields = listOf("settings")).isMatch)
        assertTrue(qwerty.score(primaryFields = listOf("settings")).isMatch)
    }

    @Test
    fun primaryFieldsStillOutrankIdenticalSecondaryFieldsAcrossCandidates() {
        val query = ResultScore.prepare("chrome")
        val secondary = query.score(secondaryFields = listOf("chrome"))
        val primary = query.score(primaryFields = listOf("chrome"))

        assertEquals(1f, primary.score, 0f)
        assertTrue(primary > secondary)
        assertEquals(secondary, query.score(secondaryFields = listOf("chrome")))
    }

    @Test
    fun whitespaceOnlyPreparedQueryMatchesEmptyAndNonemptyCandidates() {
        val query = ResultScore.prepare(" \t\n ")
        assertEquals(1f, query.score().score, 0f)
        assertEquals(1f, query.score(primaryFields = listOf("settings")).score, 0f)
    }
}
