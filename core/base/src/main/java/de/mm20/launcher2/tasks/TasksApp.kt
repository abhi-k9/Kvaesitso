package de.mm20.launcher2.tasks

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.tryStartActivity

/**
 * Changes tasks in the Tasks app (Tasks.org).
 *
 * Reading is done through its older content provider, which every version has. Changing tasks
 * needs its newer API (Tasks 15.12 and later), see
 * https://github.com/tasks/tasks/blob/main/CONTENT_PROVIDER.md
 */
object TasksApp {
    const val PackageName = "org.tasks"
    const val ReadPermission = "org.tasks.permission.READ_TASKS"
    const val WritePermission = "org.tasks.permission.WRITE_TASKS"

    private const val ApiAuthority = "org.tasks.api"

    /**
     * The API notifies this URI (and those below it) whenever a task changes, in the app too.
     */
    val ApiUri = "content://$ApiAuthority/v0".toUri()
    private val ApiTasksUri = "content://$ApiAuthority/v0/tasks".toUri()

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PackageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Whether the installed version of Tasks can change tasks for other apps
     */
    fun hasApi(context: Context): Boolean {
        return context.packageManager.resolveContentProvider(ApiAuthority, 0) != null
    }

    fun canChangeTasks(context: Context): Boolean {
        return hasApi(context) &&
                context.checkSelfPermission(WritePermission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Completes or reopens the task with the given ID. A repeating task moves on to its next
     * occurrence instead of staying completed. Makes a binder call: don't call this on the main
     * thread.
     *
     * @return whether the task was changed
     */
    fun setCompleted(context: Context, taskId: Long, completed: Boolean): Boolean {
        return tryChange {
            val values = ContentValues().apply {
                put("completed_at", if (completed) System.currentTimeMillis() else 0L)
            }
            context.contentResolver.update("$ApiTasksUri/$taskId".toUri(), values, null, null) > 0
        }
    }

    /**
     * Adds a task with the given title to the default list. Makes a binder call: don't call this
     * on the main thread.
     *
     * @return whether the task was added
     */
    fun addTask(context: Context, title: String): Boolean {
        return tryChange {
            val values = ContentValues().apply {
                put("title", title)
            }
            context.contentResolver.insert(ApiTasksUri, values) != null
        }
    }

    /**
     * Opens the editor of a new task, with the given title. Works with every version of Tasks and
     * doesn't need a permission. Tasks reads due dates, tags etc. from the title then.
     */
    fun openNewTask(context: Context, title: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND)
            .setPackage(PackageName)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return context.tryStartActivity(intent)
    }

    private inline fun tryChange(block: () -> Boolean): Boolean {
        return try {
            block()
        } catch (e: SecurityException) {
            // Permission missing or revoked
            Log.e("TasksApp", "Couldn't change tasks", e)
            false
        } catch (e: IllegalArgumentException) {
            // Unknown URI or column: the API (v0) may have changed
            Log.e("TasksApp", "Couldn't change tasks", e)
            false
        } catch (e: UnsupportedOperationException) {
            // The task is on a read-only list
            Log.e("TasksApp", "Couldn't change tasks", e)
            false
        } catch (e: IllegalStateException) {
            // A remote operation failed
            Log.e("TasksApp", "Couldn't change tasks", e)
            false
        }
    }
}
