package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SearchableKeyMigrator
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.StringNormalizer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface AppRepository : SearchableRepository<Application> {
    fun findOne(
        packageName: String,
        user: UserHandle,
    ): Flow<Application?>

    fun findMany(): Flow<ImmutableList<Application>>

    /**
     * The apps of [packageName] for [user] from the apps that have been loaded already, without
     * asking Android, or null if the apps of [user] haven't been loaded yet
     */
    fun getLoadedApps(packageName: String, user: UserHandle): List<Application>?
}

internal class AppRepositoryImpl(
    private val context: Context,
    private val profileManager: ProfileManager,
    private val stringNormalizer: StringNormalizer,
    private val keyMigrator: SearchableKeyMigrator,
) : AppRepository {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val installedApps = MutableStateFlow<List<LauncherApp>>(emptyList())

    private val profiles = profileManager.unlockedProfiles

    private val mutex = Mutex()

    init {
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageNames.contains(it.componentName.packageName) && it.user == user }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        val before = apps.filter { packageName == it.componentName.packageName && it.user == user }
                        val after = getApplications(packageName, user)
                        migrateReplacedActivity(packageName, user, before, after)
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        apps.addAll(after)
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        // Remove existing entries first to avoid duplicates, e.g. when the
                        // packages were already available
                        apps.removeAll { packageNames.contains(it.componentName.packageName) && it.user == user }
                        for (packageName in packageNames) {
                            apps.addAll(getApplications(packageName, user))
                        }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        apps.addAll(getApplications(packageName, user))
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.removeAll { packageName == it.componentName.packageName && it.user == user }
                        installedApps.value = apps

                    }
                }
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle
            ) {
                onPackageChanged(packageName, user)
            }

            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) {
                packageNames ?: return
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.replaceAll {
                            if (packageNames.contains(it.componentName.packageName) && it.user == user) {
                                it.copy(isSuspended = true)
                            } else {
                                it
                            }
                        }
                        installedApps.value = apps
                    }
                }
            }

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>?,
                user: UserHandle?
            ) {
                packageNames ?: return
                scope.launch {
                    mutex.withLock {
                        val apps = installedApps.value.toMutableList()
                        apps.replaceAll {
                            if (packageNames.contains(it.componentName.packageName) && it.user == user) {
                                it.copy(isSuspended = false)
                            } else {
                                it
                            }
                        }
                        installedApps.value = apps
                    }
                }
            }

        }, Handler(Looper.getMainLooper()))
        scope.launch {
            profiles.runningFold<List<Profile>, Pair<List<Profile>?, List<Profile>?>>(null to null) { acc, value ->
                acc.second to value
            }.collectLatest { (prev, curr) ->
                if (curr == null) return@collectLatest
                if (prev == null) {
                    curr.forEach { addProfile(it) }
                } else {
                    val added = curr - prev
                    val removed = prev - curr
                    added.forEach { addProfile(it) }
                    removed.forEach { removeProfile(it) }
                }
            }
        }
    }

    private suspend fun addProfile(profile: Profile) {
        mutex.withLock {
            val apps = installedApps.value.toMutableList()
            // onPackagesAvailable may already have added apps of this profile after it was unlocked
            apps.removeAll { it.user == profile.userHandle }
            apps.addAll(getApplications(null, profile.userHandle))
            installedApps.value = apps
        }
    }

    private fun removeProfile(profile: Profile) {
        scope.launch {
            mutex.withLock {
                val apps = installedApps.value.toMutableList()
                apps.removeAll { it.user == profile.userHandle }
                installedApps.value = apps
            }
        }
    }

    private class ActivityChange(
        val removed: List<LauncherApp>,
        val added: List<LauncherApp>,
        val time: Long,
    )

    /**
     * Launcher activities that were only removed or only added, per package and user, in case an
     * app switches to another activity in two steps.
     */
    private val pendingActivityChanges = mutableMapOf<Pair<String, UserHandle>, ActivityChange>()

    /**
     * Some apps (e.g. Duolingo) change their icon by switching to another launcher activity, and
     * app updates can rename the launcher activity. Its key changes with it, so if exactly one
     * launcher activity of a package was replaced by another one, move its favorite, visibility,
     * custom label, tags etc. to the new one. Must be called with [mutex] held.
     */
    private fun migrateReplacedActivity(
        packageName: String,
        user: UserHandle,
        before: List<LauncherApp>,
        after: List<LauncherApp>,
    ) {
        val removedNow = before.filter { b -> after.none { it.componentName == b.componentName } }
        val addedNow = after.filter { a -> before.none { it.componentName == a.componentName } }
        if (removedNow.isEmpty() && addedNow.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        val pending = pendingActivityChanges.remove(packageName to user)
            ?.takeIf { now - it.time < 60_000L }
        val allRemoved = removedNow + pending?.removed.orEmpty()
        val allAdded = addedNow + pending?.added.orEmpty()
        // An activity that was disabled and enabled again is not a replacement
        val removed = allRemoved.filter { r -> allAdded.none { it.componentName == r.componentName } }
        val added = allAdded.filter { a -> allRemoved.none { it.componentName == a.componentName } }

        if (removed.size == 1 && added.size == 1) {
            keyMigrator.migrate(removed[0].key, added[0])
        } else if (removed.isEmpty() != added.isEmpty()) {
            pendingActivityChanges[packageName to user] = ActivityChange(removed, added, now)
        }
    }

    private fun getApplications(packageName: String?, userHandle: UserHandle): List<LauncherApp> {
        if (packageName == context.packageName) return emptyList()

        return try {
            val activities = launcherApps.getActivityList(packageName, userHandle)
            // When all apps are loaded, getting all version names at once is faster than getting
            // them one app at a time
            val versionNames = if (packageName == null) getVersionNames() else null
            activities.mapNotNull { getApplication(it, versionNames) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * The version names of the packages that are installed for the launcher's user, by package
     * name, or null if they can't be listed
     */
    @Suppress("DEPRECATION")
    private fun getVersionNames(): Map<String, String?>? {
        return try {
            context.packageManager.getInstalledPackages(0)
                .associate { it.packageName to it.versionName }
        } catch (e: Exception) {
            // e.g. the list is too large to be sent on some devices
            null
        }
    }

    private fun getApplication(
        launcherActivityInfo: LauncherActivityInfo,
        versionNames: Map<String, String?>? = null,
    ): LauncherApp? {
        val packageName = launcherActivityInfo.applicationInfo.packageName
        if (packageName == context.packageName && !context.packageName.endsWith(
                ".debug"
            )
        ) return null
        if (versionNames != null && packageName in versionNames) {
            return LauncherApp(context, launcherActivityInfo, versionName = versionNames[packageName])
        }
        return LauncherApp(context, launcherActivityInfo)
    }

    override fun findOne(
        packageName: String,
        user: UserHandle,
    ): Flow<Application?> {
        return installedApps.map {
            it.firstOrNull {
                it.componentName.packageName == packageName && it.user == user
            }
        }
    }

    override fun findMany(): Flow<ImmutableList<Application>> {
        return installedApps.map { it.toImmutableList() }
    }

    override fun getLoadedApps(packageName: String, user: UserHandle): List<Application>? {
        val apps = installedApps.value
        if (apps.none { it.user == user }) return null
        return apps.filter { it.componentName.packageName == packageName && it.user == user }
    }

    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<LauncherApp>> {
        val normalizedQuery = stringNormalizer.normalize(query)

        return installedApps.map { apps ->
            withContext(Dispatchers.Default) {
                val normalizerId = stringNormalizer.id
                val appResults = mutableListOf<LauncherApp>()
                if (query.isEmpty()) {
                    appResults.addAll(apps)
                } else {
                    appResults.addAll(apps.mapNotNull { app ->
                        val cachedLabel = app.cachedNormalizerResult
                        val labelScore = ResultScore.from(
                            query = normalizedQuery,
                            primaryFields = listOf(
                                if (cachedLabel?.first == normalizerId) {
                                    cachedLabel.second
                                } else {
                                    stringNormalizer.normalize(app.label).also {
                                        app.cachedNormalizerResult = normalizerId to it
                                    }
                                }
                            ),
                        )
                        val score = maxOf(
                            labelScore,
                            packageNameScore(query, app.componentName.packageName),
                        )
                        if (score.score < 0.8f) return@mapNotNull null
                        app.copy(
                            score = score
                        )
                    })

                    val componentName = ComponentName.unflattenFromString(query)
                    getActivityByComponentName(componentName)?.let { appResults.add(it) }
                }
                appResults.sort()
                appResults.toImmutableList()
            }
        }
    }

    private fun getActivityByComponentName(componentName: ComponentName?): LauncherApp? {
        componentName ?: return null
        val intent = Intent().setComponent(componentName)
        val lai = launcherApps.resolveActivity(intent, Process.myUserHandle())
        return lai?.let {
            LauncherApp(context, lai)
        }
    }
}

/**
 * How well [query] matches the package name of an app, e.g. "org.fdroid" for F-Droid. Only
 * queries with a dot are compared, others like "com" would match nearly every app.
 */
internal fun packageNameScore(query: String, packageName: String): ResultScore {
    val trimmedQuery = query.trim()
    if ('.' !in trimmedQuery) return ResultScore.Zero
    return when {
        packageName.equals(trimmedQuery, ignoreCase = true) -> ResultScore(1f)
        packageName.contains(trimmedQuery, ignoreCase = true) -> ResultScore(0.9f)
        else -> ResultScore.Zero
    }
}
