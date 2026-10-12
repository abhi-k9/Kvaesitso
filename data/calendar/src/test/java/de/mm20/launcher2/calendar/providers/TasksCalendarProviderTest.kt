package de.mm20.launcher2.calendar.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric's Android 16 environment needs internal JDK classes that Java 21 doesn't export
@Config(sdk = [35])
class TasksCalendarProviderTest {

    /**
     * Stands in for Tasks.org's (legacy) content provider. It doesn't run the query; it returns
     * [rows] and records the selection it was given. Like Tasks.org, it returns every value as text.
     */
    class FakeTasksProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            selections += selection
            return MatrixCursor(Columns).apply {
                rows.forEach { row -> addRow(Columns.map { row[it]?.toString() }) }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ) = 0

        companion object {
            val Columns = arrayOf(
                "_id", "title", "hideUntil", "dueDate", "completed", "notes", "deleted",
                "cd_deleted", "cdl_id", "cdl_name", "cdl_color",
            )
            var rows = listOf<Map<String, Any?>>()
            val selections = mutableListOf<String?>()
        }
    }

    private lateinit var provider: TasksCalendarProvider

    @Before
    fun setUp() {
        provider = TasksCalendarProvider(RuntimeEnvironment.getApplication())
        Robolectric.buildContentProvider(FakeTasksProvider::class.java).create("org.tasks")
        FakeTasksProvider.rows = emptyList()
        FakeTasksProvider.selections.clear()
    }

    private fun task(
        id: Long,
        list: String? = "Tasks",
        movedAway: Boolean = false,
        due: Long = Due,
    ) = mapOf(
        "_id" to id,
        "title" to "Task $id",
        "hideUntil" to 0L,
        "dueDate" to due,
        "completed" to 0L,
        "deleted" to 0L,
        "cd_deleted" to if (movedAway) 1_700_000_000_000L else 0L,
        "cdl_id" to 1L,
        "cdl_name" to list,
    )

    private fun search() = runBlocking {
        provider.search(
            query = null,
            from = Due - 1000L,
            to = Due + 1000L,
            excludedCalendars = emptyList(),
            excludeAllDayEvents = false,
            allowNetwork = false,
        )
    }

    @Test
    fun aTaskThatWasMovedToAnotherListComesUpOnce() {
        FakeTasksProvider.rows = listOf(
            task(1, list = "Old list", movedAway = true),
            task(1, list = "Tasks"),
        )
        val tasks = search()
        assertEquals(1, tasks.size)
        assertEquals("Tasks", tasks[0].calendarName)
    }

    @Test
    fun theCurrentListWinsWhicheverRowComesFirst() {
        FakeTasksProvider.rows = listOf(
            task(1, list = "Tasks"),
            task(1, list = null, movedAway = true),
        )
        val tasks = search()
        assertEquals(1, tasks.size)
        assertEquals("Tasks", tasks[0].calendarName)
    }

    @Test
    fun differentTasksAreAllKept() {
        FakeTasksProvider.rows = listOf(
            task(1),
            task(2, list = null).plus("cd_deleted" to null),
            task(3, movedAway = true),
        )
        assertEquals(listOf(1L, 2L, 3L), search().map { (it as TasksCalendarEvent).id })
    }

    @Test
    fun deletedTasksAreLeftOut() {
        search()
        assertTrue(FakeTasksProvider.selections.single()!!.contains("tasks.deleted = 0"))

        runBlocking { provider.get(1) }
        assertTrue(FakeTasksProvider.selections.last()!!.contains("tasks.deleted = 0"))
    }

    companion object {
        // A due date with a time of day: Tasks.org marks those with a non-zero seconds part
        private const val Due = 1_800_000_001_000L
    }
}
