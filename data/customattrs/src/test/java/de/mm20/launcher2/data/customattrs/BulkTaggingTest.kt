package de.mm20.launcher2.data.customattrs

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.searchable.SavableSearchableRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BulkTaggingTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: CustomAttributesRepositoryImpl
    private val searchables = mockk<SavableSearchableRepository>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        ).setDriver(AndroidSQLiteDriver()).build()
        coEvery { searchables.insertAll(any()) } returns Unit
        repository = CustomAttributesRepositoryImpl(database, searchables, mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun item(itemKey: String) = mockk<SavableSearchable> {
        every { key } returns itemKey
    }

    private suspend fun seed(vararg attributes: CustomAttributeEntity) {
        database.customAttrsDao().insertCustomAttributes(attributes.toList())
    }

    private suspend fun tags(key: String) = database.customAttrsDao()
        .getCustomAttributes(listOf(key), "tag").first().map { it.value }.toSet()

    @Test
    fun addingATagPreservesOtherTagsExistingMembersAndCustomLabels() = runBlocking {
        seed(
            CustomAttributeEntity("app://a", "tag", "Work"),
            CustomAttributeEntity("app://b", "tag", "Travel"),
            CustomAttributeEntity("app://outside", "tag", "Games"),
            CustomAttributeEntity("app://a", "label", "My app"),
        )

        repository.addTagToItems(listOf(item("app://a"), item("app://b")), "Games").join()

        assertEquals(setOf("Work", "Games"), tags("app://a"))
        assertEquals(setOf("Travel", "Games"), tags("app://b"))
        assertEquals(setOf("Games"), tags("app://outside"))
        assertEquals(
            "My app", database.customAttrsDao().getCustomAttribute("app://a", "label").first()?.value,
        )
    }

    @Test
    fun duplicateSelectionsAndRepeatedWritesAddTheTagOnlyOnce() = runBlocking {
        val selected = item("app://a")

        repository.addTagToItems(listOf(selected, selected, item("app://a")), " Games ").join()
        repository.addTagToItems(listOf(selected), "Games").join()

        assertEquals(listOf("Games"), database.customAttrsDao()
            .getCustomAttributes(listOf(selected.key), "tag").first().map { it.value })
        coVerify(exactly = 2) { searchables.insertAll(listOf(selected)) }
    }

    @Test
    fun tagWriteWaitsForSavedItemBatchBeforeCompleting() = runBlocking {
        var saved = false
        coEvery { searchables.insertAll(any()) } answers {
            saved = true
            // No tag may appear before the saved-item batch has completed.
            assertEquals(emptySet<String>(), runBlocking { tags("app://a") })
        }

        repository.addTagToItems(listOf(item("app://a")), "Games").join()

        assertEquals(true, saved)
        assertEquals(setOf("Games"), tags("app://a"))
    }

    @Test
    fun emptySelectionOrBlankTagDoesNotWriteAnything() = runBlocking {
        repository.addTagToItems(emptyList(), "Games").join()
        repository.addTagToItems(listOf(item("app://a")), "  ").join()

        assertEquals(emptySet<String>(), repository.getTaggedKeys().first())
        coVerify(exactly = 0) { searchables.insertAll(any()) }
    }

    @Test
    fun untaggedFilterKeysIgnoreLabelsAndReactToRemovingTheLastTag() = runBlocking {
        seed(
            CustomAttributeEntity("app://a", "tag", "Work"),
            CustomAttributeEntity("app://a", "tag", "Games"),
            CustomAttributeEntity("app://b", "label", "My app"),
        )
        assertEquals(setOf("app://a"), repository.getTaggedKeys().first())

        database.customAttrsDao().removeTag("app://a", "Work")
        assertEquals(setOf("app://a"), repository.getTaggedKeys().first())
        database.customAttrsDao().removeTag("app://a", "Games")

        assertEquals(emptySet<String>(), repository.getTaggedKeys().first())
    }
}
