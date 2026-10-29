package de.mm20.launcher2.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    private fun score(query: String, label: String, layout: KeyboardLayout): Float =
        ResultScore.from(
            query = query,
            primaryFields = listOf(label),
            keyboardLayout = layout,
        ).score

    private fun matches(query: String, label: String, layout: KeyboardLayout): Boolean =
        score(query, label, layout) >= ResultScore.MatchThreshold

    @Test
    fun qwertyAndAzertyUseDifferentNeighbors() {
        assertTrue(matches("swtt", "settings", KeyboardLayout.Qwerty))
        assertFalse(matches("swtt", "settings", KeyboardLayout.Azerty))
        // Q and S share a row in AZERTY, but are not neighbors in QWERTY.
        assertTrue(matches("qett", "settings", KeyboardLayout.Azerty))
        assertFalse(matches("qett", "settings", KeyboardLayout.Qwerty))
    }

    @Test
    fun qwertzUsesTheSwappedZPosition() {
        assertTrue(matches("zest", "testcase", KeyboardLayout.Qwertz))
        assertFalse(matches("zest", "testcase", KeyboardLayout.Qwerty))
    }

    @Test
    fun dvorakUsesItsOwnLetterPositions() {
        assertTrue(matches("sutt", "settings", KeyboardLayout.Dvorak))
        assertFalse(matches("sutt", "settings", KeyboardLayout.Qwerty))
        assertFalse(matches("swtt", "settings", KeyboardLayout.Dvorak))
    }

    @Test
    fun colemakUsesItsOwnLetterPositions() {
        assertTrue(matches("fett", "settings", KeyboardLayout.Colemak))
        assertFalse(matches("fett", "settings", KeyboardLayout.Qwerty))
    }

    @Test
    fun disablingProximityKeepsGenericTyposAndLiteralMatches() {
        assertFalse(matches("swtt", "settings", KeyboardLayout.Disabled))
        assertTrue(matches("swtti", "settings", KeyboardLayout.Disabled))
        assertTrue(matches("settimgs", "settings", KeyboardLayout.Disabled))
        assertEquals(1f, score("sett", "settings", KeyboardLayout.Disabled), 0f)
        assertTrue(
            score("swtti", "settings", KeyboardLayout.Qwerty) >
                score("swtti", "settings", KeyboardLayout.Disabled)
        )
    }

    @Test
    fun explicitLayoutSelectionsAreIsolatedAndDefaultRemainsQwerty() {
        assertTrue(matches("qett", "settings", KeyboardLayout.Azerty))
        assertFalse(matches("qett", "settings", KeyboardLayout.Qwerty))
        assertTrue(matches("qett", "settings", KeyboardLayout.Azerty))
        assertEquals(
            score("swtt", "settings", KeyboardLayout.Qwerty),
            ResultScore.from("swtt", primaryFields = listOf("settings")).score,
            0f,
        )
    }

    @Test
    fun layoutIdentifiersRoundTripAndUnknownIdentifiersFallBack() {
        for (layout in KeyboardLayout.entries) {
            assertEquals(layout, KeyboardLayout.fromId(layout.name))
        }
        assertEquals(KeyboardLayout.Qwerty, KeyboardLayout.fromId("FutureKeyboardLayout"))
        assertEquals(KeyboardLayout.Qwerty, KeyboardLayout.fromId(""))
    }

    @Test
    fun enabledLayoutNeighborsAreSymmetricAndCoverEveryLatinLetter() {
        for (layout in KeyboardLayout.entries.filter { it != KeyboardLayout.Disabled }) {
            for (a in 'a'..'z') {
                assertFalse("$layout: a key is not its own neighbor", layout.areNeighbors(a, a))
                assertTrue("$layout: $a should have a neighbor", ('a'..'z').any { b ->
                    layout.areNeighbors(a, b)
                })
                for (b in 'a'..'z') {
                    assertEquals(
                        "$layout: $a/$b adjacency should be symmetric",
                        layout.areNeighbors(a, b),
                        layout.areNeighbors(b, a),
                    )
                }
            }
            assertFalse(layout.areNeighbors('é', 'e'))
            assertFalse(layout.areNeighbors('?', 'm'))
        }
        for (a in 'a'..'z') {
            for (b in 'a'..'z') assertFalse(KeyboardLayout.Disabled.areNeighbors(a, b))
        }
    }
}
