package de.mm20.launcher2.searchactions

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TextClassifierTest {
    private fun classify(query: String) = runBlocking {
        TextClassifierImpl().classify(RuntimeEnvironment.getApplication(), query)
    }

    @Test
    fun `bare domain becomes an HTTPS URL with a host`() {
        val url = classify("example.com").url!!
        assertEquals("https://example.com", url.toString())
        assertEquals("example.com", url.host)
        assertEquals(true, url.isHierarchical)
    }

    @Test
    fun `domain with port path query and fragment stays intact`() {
        val url = classify("  example.com:8080/a?q=test#section  ").url!!
        assertEquals("https://example.com:8080/a?q=test#section", url.toString())
        assertEquals("example.com", url.host)
        assertEquals(8080, url.port)
        assertEquals("/a", url.path)
        assertEquals("q=test", url.query)
        assertEquals("section", url.fragment)
    }

    @Test
    fun `explicit HTTP and HTTPS schemes are preserved`() {
        for (input in listOf("http://example.com/path", "https://example.com/path")) {
            assertEquals(input, classify(input).url.toString())
        }
    }

    @Test
    fun `ordinary search text is not turned into a URL`() {
        assertNull(classify("find my apps").url)
    }
}
