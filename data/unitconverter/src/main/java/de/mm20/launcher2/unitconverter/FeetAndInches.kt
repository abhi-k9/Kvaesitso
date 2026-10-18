package de.mm20.launcher2.unitconverter

/**
 * Lengths in feet and inches, as heights are commonly written: 5'9", 5' 9, 5 ft 9 in, 5ft9in.
 */
internal object FeetAndInches {

    data class Length(
        val value: Double,
        /**
         * Whether [value] is in feet (if no inches were given) or in inches
         */
        val inFeet: Boolean,
        /**
         * The unit to convert to, if one was given (e.g. "5'9" >> cm")
         */
        val targetUnit: String?,
    )

    fun parse(query: String): Length? {
        val match = Pattern.matchEntire(query.trim()) ?: return null
        val feet = match.groupValues[1].toNumber() ?: return null
        val targetUnit = match.groupValues[3].takeIf { it.isNotEmpty() }
        if (match.groupValues[2].isEmpty()) return Length(feet, true, targetUnit)
        val inches = match.groupValues[2].toNumber() ?: return null
        return Length(feet * 12 + inches, false, targetUnit)
    }

    private fun String.toNumber(): Double? = replace(',', '.').toDoubleOrNull()

    private const val Number = """(\d+(?:[.,]\d+)?)"""
    private const val Feet = """(?:'|′|’|ft\.?|feet|foot)"""
    private const val Inches = """(?:"|″|''|′′|”|inches|inch|in\.?)"""

    // Feet, then optionally inches, then optionally the unit to convert to, after ">", "->",
    // "to", "in" or a space
    private val Pattern = Regex(
        """$Number\s*$Feet\s*(?:$Number\s*$Inches?)?(?:\s*(?:>+|->|=>|\bto\b|\bin\b)?\s*([^\s\d'"′″’”.,][^\s]*))?""",
        RegexOption.IGNORE_CASE,
    )
}
