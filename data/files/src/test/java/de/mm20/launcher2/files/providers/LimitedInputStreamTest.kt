package de.mm20.launcher2.files.providers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

// File metadata parsers read through this stream, so that a huge file can't make them read (and
// keep) more than the limit
class LimitedInputStreamTest {

    private fun stream(size: Int, limit: Long) =
        LimitedInputStream(ByteArrayInputStream(ByteArray(size) { it.toByte() }), limit)

    @Test
    fun endsAtTheLimit() {
        assertEquals(10, stream(size = 100, limit = 10).readBytes().size)
    }

    @Test
    fun readsShorterInputCompletely() {
        assertEquals(5, stream(size = 5, limit = 10).readBytes().size)
    }

    @Test
    fun singleByteReadsEndAtTheLimit() {
        val stream = stream(size = 100, limit = 3)
        assertEquals(0, stream.read())
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun skippedBytesCountTowardsTheLimit() {
        val stream = stream(size = 100, limit = 10)
        assertEquals(4, stream.skip(4))
        assertEquals(6, stream.readBytes().size)
        assertEquals(10, stream(size = 100, limit = 10).skip(50))
    }

    @Test
    fun availableIsCapped() {
        assertEquals(10, stream(size = 100, limit = 10).available())
    }
}
