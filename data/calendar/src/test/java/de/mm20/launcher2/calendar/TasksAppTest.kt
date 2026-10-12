package de.mm20.launcher2.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import de.mm20.launcher2.tasks.TasksApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class TasksAppTest {

    /**
     * Stands in for the API of the Tasks app, recording the changes it is asked for.
     */
    class FakeTasksApi : ContentProvider() {
        override fun onCreate() = true

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int {
            error?.let { throw it }
            updates += uri to values!!
            return 1
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            error?.let { throw it }
            inserts += uri to values!!
            return Uri.parse("$uri/1")
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

        companion object {
            val updates = mutableListOf<Pair<Uri, ContentValues>>()
            val inserts = mutableListOf<Pair<Uri, ContentValues>>()
            var error: RuntimeException? = null
        }
    }

    @Before
    fun setUp() {
        Robolectric.buildContentProvider(FakeTasksApi::class.java).create("org.tasks.api")
        FakeTasksApi.updates.clear()
        FakeTasksApi.inserts.clear()
        FakeTasksApi.error = null
    }

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun completingATaskSetsItsCompletionTime() {
        val before = System.currentTimeMillis()
        assertTrue(TasksApp.setCompleted(context, 42, true))
        val (uri, values) = FakeTasksApi.updates.single()
        assertEquals("content://org.tasks.api/v0/tasks/42", uri.toString())
        assertTrue(values.getAsLong("completed_at") >= before)
    }

    @Test
    fun reopeningATaskClearsItsCompletionTime() {
        assertTrue(TasksApp.setCompleted(context, 42, false))
        assertEquals(0L, FakeTasksApi.updates.single().second.getAsLong("completed_at"))
    }

    @Test
    fun addingATaskSendsItsTitle() {
        assertTrue(TasksApp.addTask(context, "Buy milk"))
        val (uri, values) = FakeTasksApi.inserts.single()
        assertEquals("content://org.tasks.api/v0/tasks", uri.toString())
        assertEquals("Buy milk", values.getAsString("title"))
    }

    @Test
    fun refusedChangesAreReportedAsFailed() {
        FakeTasksApi.error = SecurityException("Permission denied")
        assertFalse(TasksApp.setCompleted(context, 42, true))
        FakeTasksApi.error = UnsupportedOperationException("Read-only list")
        assertFalse(TasksApp.setCompleted(context, 42, true))
        FakeTasksApi.error = IllegalArgumentException("Unknown column")
        assertFalse(TasksApp.addTask(context, "Buy milk"))
    }
}
