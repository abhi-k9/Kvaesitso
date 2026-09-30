package de.mm20.launcher2.files.providers

import java.io.FilterInputStream
import java.io.InputStream

/**
 * An [InputStream] that ends after [limit] bytes of [input], to limit how much of a file a parser
 * can read (and possibly keep in memory).
 */
internal class LimitedInputStream(input: InputStream, limit: Long) : FilterInputStream(input) {
    private var remaining = limit

    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = super.read()
        if (b >= 0) remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (remaining <= 0) return -1
        val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(minOf(n, remaining))
        if (skipped > 0) remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()

    override fun markSupported(): Boolean = false
}
