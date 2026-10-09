package de.mm20.launcher2.applications

import de.mm20.launcher2.search.ResultScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNameSearchTest {

    @Test
    fun theWholePackageNameMatchesBest() {
        assertEquals(ResultScore(1f), packageNameScore("org.fdroid.fdroid", "org.fdroid.fdroid"))
        assertEquals(ResultScore(1f), packageNameScore(" Org.FDroid.FDroid ", "org.fdroid.fdroid"))
    }

    @Test
    fun partsOfThePackageNameMatch() {
        assertTrue(packageNameScore("org.fdroid", "org.fdroid.fdroid").score >= 0.8f)
        assertTrue(packageNameScore("whatsapp.w4b", "com.whatsapp.w4b").score >= 0.8f)
    }

    // Otherwise "com" or "google" would find nearly every app
    @Test
    fun queriesWithoutADotDontMatch() {
        assertEquals(ResultScore.Zero, packageNameScore("fdroid", "org.fdroid.fdroid"))
    }

    @Test
    fun otherPackagesDontMatch() {
        assertEquals(ResultScore.Zero, packageNameScore("google.com", "com.google.android.gm"))
    }
}
