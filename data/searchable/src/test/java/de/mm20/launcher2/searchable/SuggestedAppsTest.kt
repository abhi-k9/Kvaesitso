package de.mm20.launcher2.searchable

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdateContentEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdatePinEntity
import de.mm20.launcher2.database.migrations.Migration_34_35
import de.mm20.launcher2.database.migrations.Migration_35_36
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuggestedAppsTest {
    private lateinit var database: AppDatabase
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val dao get() = database.searchableDao()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun app(key: String, pinned: Int = 0, excluded: Boolean = false) =
        SavedSearchableEntity(key, "apps", "serialized-$key", 8, pinned, 0, 0.7, excluded)

    @Test
    fun excludesOnlyAutomaticSuggestionsAndKeepsPinnedAndSearchAccess() = runBlocking {
        dao.upsert(app("excluded", excluded = true))
        dao.upsert(app("pinned", pinned = 1, excluded = true))
        dao.upsert(app("visible"))

        val suggestions = dao.get(automaticallySorted = true, frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true)
            .first().map { it.key }
        assertEquals(setOf("pinned", "visible"), suggestions.toSet())
        assertEquals(setOf("excluded", "pinned", "visible"), dao.getKeys(automaticallySorted = true, frequentlyUsed = true, limit = 20).first().toSet())
        assertEquals("excluded", dao.getByKeys(listOf("excluded")).first().single().key)
        assertEquals(0, dao.getByKey("excluded").first()!!.visibility)
    }

    @Test
    fun typeFilteredQueriesAndKeyQueriesApplyTheSameSuggestionPolicy() = runBlocking {
        dao.upsert(app("excluded", excluded = true))
        dao.upsert(app("visible"))
        dao.upsert(app("other").copy(type = "files"))

        assertEquals(listOf("visible"), dao.getIncludeTypes(listOf("apps"), frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true).first().map { it.key })
        assertEquals(listOf("visible"), dao.getExcludeTypes(listOf("files"), frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true).first().map { it.key })
        assertEquals(listOf("visible"), dao.getKeysIncludeTypes(listOf("apps"), frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true).first())
        assertEquals(listOf("visible"), dao.getKeysExcludeTypes(listOf("files"), frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true).first())
        assertEquals(setOf("visible", "other"), dao.getKeys(frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true).first().toSet())
    }

    @Test
    fun excludedAppsDoNotConsumeTheSuggestionLimit() = runBlocking {
        dao.upsert(app("excluded", excluded = true).copy(weight = 0.99))
        dao.upsert(app("visible").copy(weight = 0.1))

        assertEquals(listOf("visible"), dao.get(frequentlyUsed = true, limit = 1, filterExcludedSuggestions = true).first().map { it.key })
    }

    @Test
    fun launchesAndPinEditsDoNotClearExclusionOrStopUsageLearning() = runBlocking {
        dao.upsert(app("excluded", excluded = true))
        dao.touch(app("excluded"), 0.03)
        dao.upsert(listOf(SavedSearchableUpdatePinEntity("excluded", "apps", "new-content", 1)))

        val saved = dao.getByKey("excluded").first()!!
        assertTrue(saved.excludeFromFrequentlyUsed)
        assertEquals(9, saved.launchCount)
        assertTrue(saved.weight > 0.7)
        assertEquals(1, saved.pinPosition)
    }

    @Test
    fun restoringSuggestionsChangesOnlyTheExclusionFlag() = runBlocking {
        val original = app("excluded", excluded = true)
        dao.upsert(original)
        dao.setExcludedFromFrequentlyUsed(app("excluded"), false)

        assertEquals(original.copy(excludeFromFrequentlyUsed = false), dao.getByKey("excluded").first())
        assertEquals(listOf("excluded"), dao.get(frequentlyUsed = true, limit = 20, filterExcludedSuggestions = true).first().map { it.key })
        assertTrue(dao.getExcludedFromFrequentlyUsed().first().isEmpty())
    }

    @Test
    fun repositoryPinAndVisibilityUpdatesPreserveIndependentExclusion() = runBlocking {
        val repository = SavableSearchableRepositoryImpl(database, mockk())
        val serializer = object : SearchableSerializer {
            override val typePrefix = "apps"
            override fun serialize(searchable: SavableSearchable) = "serialized-app"
        }
        val searchable = mockk<SavableSearchable> {
            every { key } returns "app"
            every { domain } returns "apps"
            every { getSerializer() } returns serializer
        }
        repository.setExcludedFromFrequentlyUsed(searchable, true)
        withTimeout(5_000) { dao.isExcludedFromFrequentlyUsed("app").first { it } }
        dao.recordLastLaunch("app", 1234L)
        repository.upsert(searchable, pinned = true)
        val pinned = withTimeout(5_000) { dao.getByKey("app").first { it?.pinPosition == 1 }!! }
        assertTrue(pinned.excludeFromFrequentlyUsed)
        assertEquals(1234L, pinned.lastLaunchedAt)

        repository.update(searchable, visibility = VisibilityLevel.SearchOnly, pinned = false)
        val updated = withTimeout(5_000) { dao.getByKey("app").first { it?.visibility == 1 }!! }
        assertTrue(updated.excludeFromFrequentlyUsed)
        assertEquals(0, updated.pinPosition)
        assertEquals(1234L, updated.lastLaunchedAt)
    }

    @Test
    fun savingABatchCompletesBeforeReturningAndPreservesExistingSettings() = runBlocking {
        val original = app("existing", pinned = 3, excluded = true)
            .copy(visibility = 1, launchCount = 19, weight = 0.9, lastLaunchedAt = 1234L)
        dao.upsert(original)
        val repository = SavableSearchableRepositoryImpl(database, mockk())
        val serializer = object : SearchableSerializer {
            override val typePrefix = "apps"
            override fun serialize(searchable: SavableSearchable) = "serialized-${searchable.key}"
        }
        fun searchable(itemKey: String) = mockk<SavableSearchable> {
            every { key } returns itemKey
            every { domain } returns "apps"
            every { getSerializer() } returns serializer
        }

        repository.insertAll(listOf(searchable("existing"), searchable("new"), searchable("new")))

        assertEquals(original, dao.getByKeyOnce("existing"))
        val saved = dao.getByKeyOnce("new")!!
        assertEquals("serialized-new", saved.serializedSearchable)
        assertEquals(0, saved.pinPosition)
        assertEquals(0, saved.visibility)
        assertEquals(0, saved.launchCount)
        assertFalse(saved.excludeFromFrequentlyUsed)
        assertEquals(2, dao.getByKeys(listOf("existing", "new")).first().size)
    }

    @Test
    fun renamedAndMergedAppKeysRetainTheExclusion() = runBlocking {
        dao.upsert(app("old", excluded = true).copy(lastLaunchedAt = 500L))
        dao.upsert(app("new", pinned = 1).copy(lastLaunchedAt = 300L))
        dao.merge("old", SavedSearchableUpdateContentEntity("new", "apps", "merged"))

        val merged = dao.getByKey("new").first()!!
        assertTrue(merged.excludeFromFrequentlyUsed)
        assertEquals(16, merged.launchCount)
        assertEquals(1, merged.pinPosition)
        assertEquals(500L, merged.lastLaunchedAt)
        assertEquals(listOf("new"), dao.getExcludedFromFrequentlyUsed().first().map { it.key })
    }

    @Test
    fun backupsRetainExclusionsAndOldBackupsDefaultToAllowed() = runBlocking {
        val original = app("excluded", excluded = true).copy(lastLaunchedAt = 1234L)
        dao.upsert(original)
        val repository = SavableSearchableRepositoryImpl(database, mockk())
        val directory = File(context.cacheDir, "suggested-apps-backup").apply { mkdirs() }
        try {
            repository.backup(directory)
            database.backupDao().wipeFavorites()
            repository.restore(directory)
            assertEquals(original, dao.getByKey("excluded").first())

            val file = File(directory, "favorites.0000")
            val entries = JSONArray(file.readText())
            entries.getJSONObject(0).remove("excludeFromFrequentlyUsed")
            entries.getJSONObject(0).remove("lastLaunchedAt")
            file.writeText(entries.toString())
            repository.restore(directory)
            assertFalse(dao.getByKey("excluded").first()!!.excludeFromFrequentlyUsed)
            assertEquals(0L, dao.getByKey("excluded").first()!!.lastLaunchedAt)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun migrationFromVersion34KeepsExistingSettingsAndAddsAllowedDefault() = verifyMigration(34)

    @Test
    fun migrationFromVersion35PreservesExclusionsAndStartsWithUnknownRecency() = verifyMigration(35, excluded = true)

    private fun verifyMigration(fromVersion: Int, excluded: Boolean = false) = runBlocking {
        database.close()
        val file = File(context.cacheDir, "suggestions-migration-$fromVersion.db")
        file.delete()
        try {
            AndroidSQLiteDriver().open(file.absolutePath).use { connection ->
                val schema = File(System.getProperty("kvaesitso.migration.schemas"), "de.mm20.launcher2.database.AppDatabase/$fromVersion.json")
                    .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    // Room omits the array for tables that have no indices.
                    val indices = entity.optJSONArray("indices") ?: JSONArray()
                    for (j in 0 until indices.length()) {
                        connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) connection.execSQL(setup.getString(i))
                connection.execSQL("INSERT INTO Searchable (`key`, `type`, searchable, launchCount, pinPosition, hidden, weight) VALUES ('old', 'apps', 'old-content', 12, 3, 1, 0.6)")
                if (excluded) connection.execSQL("UPDATE Searchable SET excludeFromFrequentlyUsed = 1")
                connection.execSQL("PRAGMA user_version = $fromVersion")
            }
            database = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
                .setDriver(AndroidSQLiteDriver())
                .addMigrations(Migration_34_35(), Migration_35_36())
                .build()
            val migrated = dao.getByKey("old").first()!!
            assertEquals(12, migrated.launchCount)
            assertEquals(3, migrated.pinPosition)
            assertEquals(1, migrated.visibility)
            assertEquals(0.6, migrated.weight, 0.0)
            assertEquals(excluded, migrated.excludeFromFrequentlyUsed)
            assertEquals(0L, migrated.lastLaunchedAt)
        } finally {
            file.delete()
        }
    }

    @Test
    fun recentlyUsedAndFrequentlyUsedHaveIndependentOrders() = runBlocking {
        dao.upsert(app("frequent").copy(launchCount = 100, lastLaunchedAt = 100L))
        dao.upsert(app("recent").copy(launchCount = 1, lastLaunchedAt = 300L))
        dao.upsert(app("middle").copy(launchCount = 5, lastLaunchedAt = 200L))
        assertEquals(listOf("recent", "middle", "frequent"), dao.getAppSuggestions(true).first().map { it.key })
        assertEquals(listOf("frequent", "middle", "recent"), dao.getAppSuggestions(false).first().map { it.key })
    }

    @Test
    fun automaticGroupsExcludePinsHiddenItemsAndExcludedSuggestions() = runBlocking {
        dao.upsert(app("pin", pinned = 3).copy(lastLaunchedAt = 600L))
        dao.upsert(app("hidden").copy(visibility = 2, lastLaunchedAt = 500L))
        dao.upsert(app("excluded", excluded = true).copy(lastLaunchedAt = 400L))
        dao.upsert(app("calendar").copy(type = "calendar", lastLaunchedAt = 300L))
        dao.upsert(app("visible").copy(lastLaunchedAt = 200L))
        dao.upsert(app("search-only").copy(visibility = 1, lastLaunchedAt = 100L))
        assertEquals(listOf("visible", "search-only"), dao.getAppSuggestions(true).first().map { it.key })
        assertEquals(setOf("visible", "search-only"), dao.getAppSuggestions(false).first().map { it.key }.toSet())
    }

    @Test
    fun olderLaunchHistoryStillCountsForFrequencyWithoutInventingRecency() = runBlocking {
        dao.upsert(app("old-history"))
        assertEquals(listOf("old-history"), dao.getAppSuggestions(false).first().map { it.key })
        assertTrue(dao.getAppSuggestions(true).first().isEmpty())
        dao.touch(app("old-history"), 0.03, launchedAt = 200L)
        dao.touch(app("old-history"), 0.03, launchedAt = 100L)
        val saved = dao.getByKeyOnce("old-history")!!
        assertEquals(200L, saved.lastLaunchedAt)
        assertEquals(10, saved.launchCount)
        assertEquals(listOf("old-history"), dao.getAppSuggestions(true).first().map { it.key })
    }

}
