package de.mm20.launcher2.unitconverter

import de.mm20.launcher2.unitconverter.FeetAndInches.Length
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeetAndInchesTest {

    @Test
    fun heightsAsTheyAreCommonlyWritten() {
        for (query in listOf(
            "5'9\"", "5' 9\"", "5'9", "5′9″", "5’9”", "5'9''", "5 ft 9 in", "5ft9in",
            "5 feet 9 inches", "5 foot 9", "5 ft. 9 in.", " 5'9\" ",
        )) {
            assertEquals(query, Length(69.0, false, null), FeetAndInches.parse(query))
        }
    }

    @Test
    fun fractionsOfInches() {
        assertEquals(Length(69.5, false, null), FeetAndInches.parse("5'9.5\""))
        assertEquals(Length(69.5, false, null), FeetAndInches.parse("5'9,5\""))
    }

    @Test
    fun feetOnly() {
        assertEquals(Length(6.0, true, null), FeetAndInches.parse("6'"))
        assertEquals(Length(6.0, true, null), FeetAndInches.parse("6 ft"))
    }

    @Test
    fun theUnitToConvertTo() {
        for (query in listOf(
            "5'9\" cm", "5'9\" >> cm", "5'9\">cm", "5'9\" -> cm", "5'9\" to cm", "5'9\" in cm",
            "5 ft 9 in cm", "5'9 in cm",
        )) {
            assertEquals(query, Length(69.0, false, "cm"), FeetAndInches.parse(query))
        }
        assertEquals(Length(6.0, true, "m"), FeetAndInches.parse("6' in m"))
    }

    @Test
    fun otherQueriesAreLeftAlone() {
        for (query in listOf(
            "5", "5 cm", "9\"", "5 USD", "it's 5", "5'9\" cm m", "'9", "rock'n'roll",
        )) {
            assertNull(query, FeetAndInches.parse(query))
        }
    }
}
