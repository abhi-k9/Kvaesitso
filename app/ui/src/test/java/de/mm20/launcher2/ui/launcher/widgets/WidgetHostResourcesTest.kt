package de.mm20.launcher2.ui.launcher.widgets

import android.app.Application
import android.appwidget.AppWidgetHost
import de.mm20.launcher2.widgets.AppWidget
import de.mm20.launcher2.widgets.AppWidgetConfig
import de.mm20.launcher2.widgets.SpacerWidget
import de.mm20.launcher2.widgets.WidgetPlacement
import de.mm20.launcher2.widgets.WidgetStack
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetHostResourcesTest {
    @Test
    fun releasingAStackReleasesEveryExternalWidgetAndSkipsBuiltInWidgets() {
        val stack = WidgetStack(UUID.randomUUID())
        val host = RecordingHost()
        val removed = listOf(
            WidgetPlacement(stack, null, 0),
            WidgetPlacement(SpacerWidget(UUID.randomUUID()), stack.id, 0),
            WidgetPlacement(AppWidget(UUID.randomUUID(), AppWidgetConfig(41, 240)), stack.id, 1),
            WidgetPlacement(AppWidget(UUID.randomUUID(), AppWidgetConfig(42, 240)), stack.id, 2),
        )

        releaseWidgetIds(host, removed)

        assertEquals(listOf(41, 42), host.releasedIds)
    }

    private class RecordingHost : AppWidgetHost(RuntimeEnvironment.getApplication(), 44203) {
        val releasedIds = mutableListOf<Int>()
        override fun deleteAppWidgetId(appWidgetId: Int) {
            releasedIds.add(appWidgetId)
        }
    }
}
