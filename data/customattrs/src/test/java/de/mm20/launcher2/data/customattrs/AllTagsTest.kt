package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.CustomAttrsDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AllTagsTest {

    private val dao = mockk<CustomAttrsDao>()
    private val database = mockk<AppDatabase> {
        every { customAttrsDao() } returns dao
    }
    private val repository = CustomAttributesRepositoryImpl(database, mockk(relaxed = true))

    // The database sorts capital letters first
    @Test
    fun tagsAreSortedAlphabeticallyIgnoringCase() {
        every { dao.getAllTags() } returns flowOf(listOf("Games", "Work", "apps", "banking"))
        assertEquals(
            listOf("apps", "banking", "Games", "Work"),
            runBlocking { repository.getAllTags().first() },
        )
    }

    @Test
    fun suggestionsAreSortedAlphabeticallyIgnoringCase() {
        every { dao.getAllTagsLike("w%") } returns flowOf(listOf("Work", "Wallet", "weather"))
        assertEquals(
            listOf("Wallet", "weather", "Work"),
            runBlocking { repository.getAllTags(startsWith = "w").first() },
        )
    }
}
