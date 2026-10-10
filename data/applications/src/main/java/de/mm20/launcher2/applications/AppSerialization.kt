package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableKeyMigrator
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.StringNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class LockedPrivateProfileAppSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as LockedPrivateProfileApp
        val json = JSONObject()
        json.put("package", searchable.componentName.packageName)
        json.put("activity", searchable.componentName.className)
        json.put("user", searchable.userSerialNumber)
        return json.toString()
    }

    override val typePrefix: String
        get() = "app"
}

class LauncherAppSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as LauncherApp
        val json = JSONObject()
        json.put("package", searchable.componentName.packageName)
        json.put("activity", searchable.componentName.className)
        json.put("user", searchable.userSerialNumber)
        return json.toString()
    }

    override val typePrefix: String
        get() = "app"
}

class LauncherAppDeserializer(
    val context: Context,
    private val keyMigrator: SearchableKeyMigrator,
    private val appRepository: AppRepository,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        try {
            val json = JSONObject(serialized)
            val launcherApps = context.getSystemService<LauncherApps>()!!
            val userManager = context.getSystemService<UserManager>()!!
            val userSerial = json.optLong("user", -1L)
            val user = if (userSerial == -1L) Process.myUserHandle() else (userManager.getUserForSerialNumber(userSerial) ?: return null)

            val pkg = json.getString("package")
            val activity = json.getString("activity")

            val componentName = ComponentName(pkg, activity)

            if (isAtLeastApiLevel(35)) {
                val launcherUser = launcherApps.getLauncherUserInfo(user) ?: return null
                if (launcherUser.userType == UserManager.USER_TYPE_PROFILE_PRIVATE && userManager.isQuietModeEnabled(
                        user
                    )
                ) {
                    return LockedPrivateProfileApp(
                        label = context.getString(R.string.app_label_locked_profile),
                        componentName = componentName,
                        user = user,
                        userSerialNumber = userSerial
                    )
                }
            }

            val intent = Intent().also {
                it.component = componentName
            }
            val launcherActivityInfo = launcherApps.resolveActivity(intent, user) ?: return null

            // resolveActivity also finds activities that aren't shown in launchers (anymore), e.g.
            // if the app switched to another launcher activity in an update while the launcher
            // wasn't running. Such an item can't be launched, and it would be shown next to the
            // app's current activity, e.g. twice in a folder. What's stored for it is moved to the
            // current activity instead.
            // The apps that are loaded already are used if possible, to not ask Android again
            val launcherActivities = appRepository.getLoadedApps(pkg, user)
                ?.filterIsInstance<LauncherApp>()
                ?: withContext(Dispatchers.IO) {
                    launcherApps.getActivityList(pkg, user).map { LauncherApp(context, it) }
                }
            if (launcherActivities.isNotEmpty() &&
                launcherActivities.none { it.componentName == componentName }
            ) {
                val current = launcherActivities.singleOrNull()
                if (current != null) {
                    keyMigrator.merge(LauncherApp(context, launcherActivityInfo).key, current)
                }
                return null
            }
            return LauncherApp(context, launcherActivityInfo)
        } catch (e: SecurityException) {
            Log.e("MM20", "Failed to deserialize app: $serialized", e)
            // e.g. the profile is currently not accessible. Temporarily unavailable, don't remove it
            throw e
        }
    }

}
