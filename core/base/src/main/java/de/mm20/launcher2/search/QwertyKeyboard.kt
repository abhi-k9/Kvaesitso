package de.mm20.launcher2.search

/**
 * A conservative proximity model for staggered QWERTY keys. Android does not expose the active
 * IME's key geometry, so this is a heuristic, not detection of the user's keyboard layout.
 * Other characters still use the ordinary typo matcher.
 */
internal object QwertyKeyboard {
    private val neighbors = IntArray(26).apply {
        val positions = buildMap {
            // Coordinates in quarter-key units; consecutive rows are one key apart vertically.
            for ((row, keys) in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").withIndex()) {
                val offset = intArrayOf(0, 1, 3)[row]
                for ((column, key) in keys.withIndex()) put(key, (column * 4 + offset) to row * 4)
            }
        }
        for ((key, point) in positions) {
            for ((other, otherPoint) in positions) {
                val dx = point.first - otherPoint.first
                val dy = point.second - otherPoint.second
                if (key != other && dx * dx + dy * dy <= 25) {
                    this[key - 'a'] = this[key - 'a'] or (1 shl (other - 'a'))
                }
            }
        }
    }

    fun areNeighbors(a: Char, b: Char): Boolean {
        if (a !in 'a'..'z' || b !in 'a'..'z') return false
        return neighbors[a - 'a'] and (1 shl (b - 'a')) != 0
    }
}
