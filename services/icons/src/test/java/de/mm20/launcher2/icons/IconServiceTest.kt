package de.mm20.launcher2.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.preferences.ui.IconSettings
import de.mm20.launcher2.preferences.ui.IconSettingsData
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IconServiceTest {

    /**
     * Records the registered receivers instead of registering them, so that the test can deliver
     * the package broadcasts itself.
     */
    private class TestContext(base: Context) : ContextWrapper(base) {
        val receivers = mutableListOf<BroadcastReceiver>()

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            receiver?.let { receivers += it }
            return null
        }
    }

    private class FakeApp(override val key: String) : SavableSearchable {
        @Volatile
        var icon: LauncherIcon? = null

        @Volatile
        var loadError: Exception? = null
        val loads = AtomicInteger()

        override suspend fun loadIcon(context: Context, size: Int, themed: Boolean): LauncherIcon? {
            loads.incrementAndGet()
            loadError?.let { throw it }
            return icon
        }

        override fun getPlaceholderIcon(context: Context) = Placeholder
        override val label = "Fake app"
        override fun overrideLabel(label: String) = this
        override fun launch(context: Context, options: Bundle?) = false
        override val preferDetailsOverLaunch = false
        override val domain = "fake"
        override fun getSerializer(): SearchableSerializer = throw UnsupportedOperationException()
    }

    private val context = TestContext(RuntimeEnvironment.getApplication())

    private val settings = mockk<IconSettings> {
        coEvery { collect(any()) } coAnswers {
            firstArg<FlowCollector<IconSettingsData>>().emit(
                IconSettingsData(
                    themedIcons = false,
                    forceThemed = false,
                    adaptify = false,
                    iconPack = null,
                )
            )
            awaitCancellation()
        }
    }

    private val customAttributes = mockk<CustomAttributesRepository> {
        every { getCustomIcon(any()) } returns flowOf(null)
    }

    private val iconService =
        IconService(context, mockk(relaxed = true), settings, customAttributes)

    @Before
    fun setUp() {
        mockkObject(CrashReporter)
        every { CrashReporter.logException(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(CrashReporter)
    }

    private fun sendPackageBroadcast(action: String, packageName: String, replacing: Boolean) {
        val intent = Intent(action, Uri.fromParts("package", packageName, null))
            .putExtra(Intent.EXTRA_REPLACING, replacing)
        for (receiver in context.receivers) receiver.onReceive(context, intent)
    }

    private fun runIconTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        block()
        coroutineContext.cancelChildren()
    }

    /**
     * The icon that is shown for [app], like the UI would collect it
     */
    private fun CoroutineScope.shownIcon(app: FakeApp): MutableStateFlow<LauncherIcon?> {
        val shown = MutableStateFlow<LauncherIcon?>(null)
        launch(Dispatchers.Default) {
            iconService.getIcon(app, 48).collect { shown.value = it }
        }
        return shown
    }

    private suspend fun MutableStateFlow<LauncherIcon?>.awaitIcon(icon: LauncherIcon) {
        withTimeout(5000) { first { it == icon } }
    }

    @Test
    fun showsTheAppIcon() = runIconTest {
        val app = FakeApp("app://com.example:com.example.Main").apply { icon = IconV1 }
        shownIcon(app).awaitIcon(IconV1)
    }

    // fork.14 (upstream #118): the old icon was shown until the launcher restarted
    @Test
    fun showsTheNewIconAfterAnUpdate() = runIconTest {
        val app = FakeApp("app://com.example:com.example.Main").apply { icon = IconV1 }
        val otherApp = FakeApp("app://org.other:org.other.Main").apply { icon = IconV1 }
        val shown = shownIcon(app)
        val otherShown = shownIcon(otherApp)
        shown.awaitIcon(IconV1)
        otherShown.awaitIcon(IconV1)
        delay(200)
        val otherLoads = otherApp.loads.get()

        app.icon = IconV2
        sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, "com.example", replacing = true)
        shown.awaitIcon(IconV2)

        // The icons of other apps come from the cache
        delay(200)
        assertEquals(otherLoads, otherApp.loads.get())
        assertEquals(IconV1, otherShown.value)
    }

    // fork.16: while an app is updated, its old APK is already gone, and loading its icon only
    // gave the system's default icon, which was then shown until the launcher restarted
    @Test
    fun keepsTheIconWhileTheAppIsUpdated() = runIconTest {
        val app = FakeApp("app://com.example:com.example.Main").apply { icon = IconV1 }
        val shown = shownIcon(app)
        shown.awaitIcon(IconV1)

        app.icon = DefaultIcon
        sendPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, "com.example", replacing = true)
        delay(300)
        assertEquals(IconV1, shown.value)

        app.icon = IconV2
        sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, "com.example", replacing = true)
        sendPackageBroadcast(Intent.ACTION_PACKAGE_REPLACED, "com.example", replacing = false)
        shown.awaitIcon(IconV2)
    }

    // fork.13: an icon that failed to load crashed the launcher
    @Test
    fun showsThePlaceholderIfTheIconFailsToLoad() = runIconTest {
        val app = FakeApp("app://com.example:com.example.Main").apply {
            loadError = IllegalStateException("broken icon")
        }
        shownIcon(app).awaitIcon(Placeholder)
    }

    companion object {
        private val IconV1 = StaticLauncherIcon(ColorLayer(1), ColorLayer(1))
        private val IconV2 = StaticLauncherIcon(ColorLayer(2), ColorLayer(2))
        private val DefaultIcon = StaticLauncherIcon(ColorLayer(3), ColorLayer(3))
        private val Placeholder = StaticLauncherIcon(ColorLayer(4), TransparentLayer)
    }
}
