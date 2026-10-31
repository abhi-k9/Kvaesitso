package de.mm20.launcher2.searchable

import android.app.Application as AndroidApplication
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SearchableDeserializer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AndroidApplication::class)
class AppSuggestionRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SavableSearchableRepositoryImpl
    private val deserializer = mockk<SearchableDeserializer>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .setDriver(AndroidSQLiteDriver()).build()
        startKoin { modules(module { single<SearchableDeserializer>(named("apps")) { deserializer } }) }
        repository = SavableSearchableRepositoryImpl(database, mockk())
    }

    @After
    fun tearDown() {
        database.close()
        stopKoin()
    }

    private fun app(id: String, isPrivateApp: Boolean = false) = mockk<Application> {
        every { key } returns id
        every { isPrivate } returns isPrivateApp
    }

    private suspend fun save(id: String, time: Long, count: Int = 1) {
        database.searchableDao().upsert(SavedSearchableEntity(id, "apps", id, count, 0, 0, 0.5, lastLaunchedAt = time))
    }

    @Test
    fun unavailableAndPrivateAppsDoNotConsumeSlotsOrGetDeleted() = runBlocking {
        save("private", 400)
        save("unavailable", 300)
        save("visible1", 200)
        save("visible2", 100)
        coEvery { deserializer.deserialize("private") } returns app("private", isPrivateApp = true)
        coEvery { deserializer.deserialize("unavailable") } throws SecurityException("Profile paused")
        coEvery { deserializer.deserialize("visible1") } returns app("visible1")
        coEvery { deserializer.deserialize("visible2") } returns app("visible2")

        assertEquals(listOf("visible1", "visible2"), repository.getAppSuggestions(true, 2).first().map { it.key })
        assertTrue(database.searchableDao().exists("private"))
        assertTrue(database.searchableDao().exists("unavailable"))
    }

    @Test
    fun zeroQuotaDoesNotResolveAnyAppsAndFrequencyUsesTheSavedCount() = runBlocking {
        save("recent", 200, count = 1)
        save("frequent", 100, count = 9)
        // No deserializer answers yet: a disabled group must not load anything.
        assertTrue(repository.getAppSuggestions(true, 0).first().isEmpty())
        coEvery { deserializer.deserialize("recent") } returns app("recent")
        coEvery { deserializer.deserialize("frequent") } returns app("frequent")
        assertEquals("frequent", repository.getAppSuggestions(false, 1).first().single().key)
        assertEquals("recent", repository.getAppSuggestions(true, 1).first().single().key)
    }
}
