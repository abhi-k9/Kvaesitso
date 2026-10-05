package de.mm20.launcher2.ktx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class ReaderTest {

    @Test
    fun readsTextUpToTheLimit() {
        assertEquals("hello", StringReader("hello").readTextOrNull(5))
        assertEquals("", StringReader("").readTextOrNull(0))
    }

    @Test
    fun returnsNullAboveTheLimit() {
        assertNull(StringReader("hello!").readTextOrNull(5))
    }

    // Theme files and Breezy Weather data come from other apps. A small compressed payload can
    // expand to more text than fits into memory, so reading has to stop at the limit.
    @Test
    fun stopsReadingEndlessInput() {
        var charsRead = 0L
        val endless = object : Reader() {
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                cbuf.fill('a', off, off + len)
                charsRead += len
                return len
            }

            override fun close() {}
        }
        assertNull(endless.readTextOrNull(1_000_000))
        assertTrue("read $charsRead chars", charsRead < 2_000_000)
    }
}
