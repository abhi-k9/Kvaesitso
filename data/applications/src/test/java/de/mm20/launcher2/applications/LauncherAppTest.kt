package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Test

class LauncherAppTest {

    private val user = mockk<UserHandle>()
    private val component = mockk<ComponentName>(relaxed = true)

    private val launcherApps = mockk<LauncherApps>()
    private val context = mockk<Context>(relaxed = true) {
        every { getSystemService(Context.LAUNCHER_APPS_SERVICE) } returns launcherApps
        every { getSystemService(LauncherApps::class.java) } returns launcherApps
    }

    private val oldIcon = mockk<Drawable>()
    private val newIcon = mockk<Drawable>()

    private fun activityInfo(icon: Drawable): LauncherActivityInfo = mockk {
        every { label } returns "App"
        every { user } returns this@LauncherAppTest.user
        every { componentName } returns component
        every { applicationInfo } returns ApplicationInfo()
        every { getIcon(any()) } returns icon
    }

    /**
     * An app as it was loaded before an update, e.g. a favorite
     */
    private val app = LauncherApp(
        launcherActivityInfo = activityInfo(oldIcon),
        versionName = "1.0",
        userSerialNumber = 0L,
    )

    private fun loadedIcon() = runBlocking {
        val icon = app.loadIcon(context, 48, themed = false) as StaticLauncherIcon
        (icon.foregroundLayer as StaticIconLayer).icon
    }

    // fork.16: after an update, the activity info the app was loaded with points to the deleted
    // APK, which only gives the system's default icon
    @Test
    fun loadsTheIconOfTheInstalledVersion() {
        every { launcherApps.resolveActivity(any(), any()) } returns activityInfo(newIcon)
        assertSame(newIcon, loadedIcon())
    }

    @Test
    fun fallsBackToTheLoadedActivityInfo() {
        // e.g. while the work profile is paused
        every { launcherApps.resolveActivity(any(), any()) } returns null
        assertSame(oldIcon, loadedIcon())
    }

    @Test
    fun fallsBackIfTheProfileIsInaccessible() {
        every { launcherApps.resolveActivity(any(), any()) } throws SecurityException()
        assertSame(oldIcon, loadedIcon())
    }
}
