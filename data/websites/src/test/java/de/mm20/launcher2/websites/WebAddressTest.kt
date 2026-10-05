package de.mm20.launcher2.websites

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAddressTest {

    @Test
    fun recognizesWebAddresses() {
        for (query in listOf(
            "example.com",
            "example.com/path?q=1#top",
            "example.com:8080/path",
            "sub.example.co.uk",
            "xn--bcher-kva.de",
            "192.168.1.1",
        )) {
            assertTrue(query, looksLikeWebAddress(query))
        }
    }

    @Test
    fun ignoresSearchQueries() {
        for (query in listOf(
            "hello world",
            "kvaesitso",
            "3.14",
            "example.c",
            // Only a host is requested, never a user's address or credentials
            "user@example.com",
        )) {
            assertFalse(query, looksLikeWebAddress(query))
        }
    }

    // Characters that aren't allowed in host names made OkHttp throw on its own thread, which
    // crashed the launcher (upstream #1847)
    @Test
    fun ignoresInvalidHostNames() {
        for (query in listOf("exa_mple.com", "example..com", "ex!ample.com", ".example.com")) {
            assertFalse(query, looksLikeWebAddress(query))
        }
    }
}
