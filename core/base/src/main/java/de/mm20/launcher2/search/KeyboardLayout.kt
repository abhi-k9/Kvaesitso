package de.mm20.launcher2.search

/**
 * User-selected proximity model for typo matching, independent of the active IME. Each model's
 * neighbor table is built once and reused; callers pass the selection for the current search.
 * Punctuation occupies its real key position but is not given a typo discount.
 */
enum class KeyboardLayout(private vararg val rows: String) {
    Qwerty("qwertyuiop", "asdfghjkl", "zxcvbnm"),
    Qwertz("qwertzuiop", "asdfghjkl", "yxcvbnm"),
    Azerty("azertyuiop", "qsdfghjklm", "wxcvbn"),
    Dvorak("',.pyfgcrl", "aoeuidhtns", ";qjkxbmwvz"),
    Colemak("qwfpgjluy;", "arstdhneio", "zxcvbkm,./"),
    Disabled;

    private val neighbors = IntArray(26).apply {
        val positions = buildMap {
            // Coordinates in quarter-key units; consecutive rows are one key apart vertically.
            val offsets = intArrayOf(0, 1, 3)
            for ((row, keys) in rows.withIndex()) {
                for ((column, key) in keys.withIndex()) {
                    if (key in 'a'..'z') put(key, (column * 4 + offsets[row]) to row * 4)
                }
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

    companion object {
        /** Unknown future IDs must not prevent reading the user's other settings or backups. */
        fun fromId(id: String): KeyboardLayout = entries.firstOrNull { it.name == id } ?: Qwerty
    }
}
