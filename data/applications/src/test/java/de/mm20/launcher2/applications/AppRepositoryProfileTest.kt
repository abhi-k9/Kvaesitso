package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.profiles.ProfileManager
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.StringNormalizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression coverage for duplicate lazy-list keys when a work profile becomes available (#2056). */
class AppRepositoryProfileTest {
    private val personalUser = mockk<UserHandle>()
    private val workUser = mockk<UserHandle>()
    private val personal = Profile(Profile.Type.Personal, personalUser, 0L)
    private val work = Profile(Profile.Type.Work, workUser, 12L)
    private val callback = slot<LauncherApps.Callback>()
    private val profiles = Channel<List<Profile>>(Channel.UNLIMITED)
    private val launcherApps = mockk<LauncherApps> {
        every { registerCallback(capture(callback), any()) } returns Unit
    }
    private val packageManager = mockk<PackageManager> {
        every { getInstalledPackages(0) } returns emptyList()
        every { getPackageInfo(any<String>(), 0) } returns PackageInfo().apply {
            versionName = "1.0"
        }
    }
    private val userManager = mockk<UserManager> {
        every { getSerialNumberForUser(personalUser) } returns 0L
        every { getSerialNumberForUser(workUser) } returns 12L
    }
    private val context = mockk<Context> {
        every { packageName } returns "launcher.test"
        every { getSystemService(Context.LAUNCHER_APPS_SERVICE) } returns launcherApps
        every { getSystemService(Context.USER_SERVICE) } returns userManager
        every { getPackageManager() } returns this@AppRepositoryProfileTest.packageManager
    }
    private val profileManager = mockk<ProfileManager> {
        every { unlockedProfiles } returns this@AppRepositoryProfileTest.profiles.receiveAsFlow()
    }
    private val normalizer = object : StringNormalizer {
        override val id = "test"
        override fun normalize(input: String) = input.lowercase()
    }

    private fun activity(userHandle: UserHandle, displayLabel: String): LauncherActivityInfo {
        val component = mockk<ComponentName> {
            every { packageName } returns "test.documents"
            every { className } returns "test.documents.Main"
        }
        return mockk {
            every { user } returns userHandle
            every { label } returns displayLabel
            every { componentName } returns component
            every { applicationInfo } returns ApplicationInfo().apply {
                packageName = "test.documents"
            }
        }
    }

    private fun repository(): AppRepositoryImpl {
        every { launcherApps.getActivityList(any(), personalUser) } returns
            listOf(activity(personalUser, "Personal documents"))
        every { launcherApps.getActivityList(any(), workUser) } returns
            listOf(activity(workUser, "Work documents"))
        return AppRepositoryImpl(context, profileManager, normalizer, mockk(relaxed = true))
    }

    private suspend fun AppRepository.awaitLabel(label: String): List<Application> =
        withTimeout(5_000) { findMany().first { apps -> apps.any { it.label == label } } }

    private fun assertOneAppPerProfile(apps: List<Application>) {
        assertEquals(2, apps.size)
        assertEquals(setOf(personalUser, workUser), apps.map { it.user }.toSet())
        assertEquals(2, apps.map { it.key }.distinct().size)
    }

    @Test
    fun packageNotificationBeforeProfileReloadDoesNotDuplicateWorkApps() = runBlocking {
        try {
            val repository = repository()
            profiles.send(listOf(personal))
            repository.awaitLabel("Personal documents")

            callback.captured.onPackagesAvailable(arrayOf("test.documents"), workUser, false)
            repository.awaitLabel("Work documents")

            // A new label ensures the assertion observes the full profile reload, not its old state.
            every { launcherApps.getActivityList(null, workUser) } returns
                listOf(activity(workUser, "Reloaded work documents"))
            profiles.send(listOf(personal, work))
            assertOneAppPerProfile(repository.awaitLabel("Reloaded work documents"))
        } finally {
            profiles.close()
        }
    }

    @Test
    fun profileReloadBeforePackageNotificationDoesNotDuplicateWorkApps() = runBlocking {
        try {
            val repository = repository()
            profiles.send(listOf(personal, work))
            repository.awaitLabel("Work documents")

            every { launcherApps.getActivityList("test.documents", workUser) } returns
                listOf(activity(workUser, "Available work documents"))
            callback.captured.onPackagesAvailable(arrayOf("test.documents"), workUser, false)
            assertOneAppPerProfile(repository.awaitLabel("Available work documents"))
        } finally {
            profiles.close()
        }
    }

    @Test
    fun repeatedPackageAddedNotificationReplacesOnlyThatProfilesApp() = runBlocking {
        try {
            val repository = repository()
            profiles.send(listOf(personal, work))
            repository.awaitLabel("Work documents")

            every { launcherApps.getActivityList("test.documents", workUser) } returns
                listOf(activity(workUser, "Added work documents"))
            callback.captured.onPackageAdded("test.documents", workUser)
            assertOneAppPerProfile(repository.awaitLabel("Added work documents"))
        } finally {
            profiles.close()
        }
    }
}
