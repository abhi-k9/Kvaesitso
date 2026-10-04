package de.mm20.launcher2.ktx

import java.io.Reader

/**
 * Reads the remaining text like [Reader.readText], but returns null instead if it is longer than
 * [maxLength] characters. For input from other apps, which could be arbitrarily large.
 */
fun Reader.readTextOrNull(maxLength: Int): String? {
    val text = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) return text.toString()
        text.append(buffer, 0, count)
        if (text.length > maxLength) return null
    }
}
