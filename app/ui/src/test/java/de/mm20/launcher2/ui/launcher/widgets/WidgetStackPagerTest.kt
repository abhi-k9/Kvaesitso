package de.mm20.launcher2.ui.launcher.widgets

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.widgets.SpacerWidget
import de.mm20.launcher2.widgets.SpacerWidgetConfig
import de.mm20.launcher2.widgets.Widget
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class WidgetStackPagerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun arrowsExposeTheFirstAndLastPageBoundaries() {
        showStack()
        compose.onNodeWithText("1 of 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous widget").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next widget").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Next widget").performClick()

        compose.onNodeWithText("3 of 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next widget").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous widget").assertIsEnabled().performClick()
        compose.onNodeWithText("2 of 3").assertIsDisplayed()
    }

    @Test
    fun manualSwipeChangesTheVisibleWidget() {
        showStack()

        compose.onNodeWithTag("stack").performTouchInput { swipeLeft() }

        compose.onNodeWithText("2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Widget 180").assertIsDisplayed()
    }

    @Test
    fun changingWidgetMeasurementsPreservesTheSelectedPageAndFrameHeight() {
        val widgets = showStack()
        compose.onNodeWithContentDescription("Next widget").performClick()
        compose.runOnIdle {
            widgets.value = widgets.value.mapIndexed { index, widget ->
                if (index == 1) (widget as SpacerWidget).copy(config = SpacerWidgetConfig(600)) else widget
            }
        }

        compose.onNodeWithText("2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Widget 600").assertIsDisplayed()
        compose.onNodeWithTag("stack").assertHeightIsEqualTo(288.dp)
    }

    @Test
    fun reorderingKeepsTheSameWidgetVisible() {
        val widgets = showStack()
        compose.onNodeWithContentDescription("Next widget").performClick()
        compose.runOnIdle {
            widgets.value = listOf(widgets.value[2], widgets.value[0], widgets.value[1])
        }

        compose.onNodeWithText("Widget 180").assertIsDisplayed()
        compose.onNodeWithText("3 of 3").assertIsDisplayed()
    }

    @Test
    fun deletingTheLastPageClampsToTheRemainingWidgets() {
        val widgets = showStack()
        compose.onNodeWithContentDescription("Next widget").performClick()
        compose.onNodeWithContentDescription("Next widget").performClick()
        compose.runOnIdle { widgets.value = widgets.value.dropLast(1) }

        compose.onNodeWithText("2 of 2").assertIsDisplayed()
        compose.onNodeWithText("Widget 180").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next widget").assertIsNotEnabled()
    }

    @Test
    fun emptyStackRetainsItsFrameAndCanReceiveAWidget() {
        val widgets = showStack(emptyList())
        compose.onNodeWithText("Add widgets to this stack").assertIsDisplayed()
        compose.onNodeWithTag("stack").assertHeightIsEqualTo(240.dp)

        compose.runOnIdle { widgets.value = listOf(spacer(100)) }

        compose.onNodeWithText("Widget 100").assertIsDisplayed()
        compose.onNodeWithText("1 of 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous widget").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next widget").assertIsNotEnabled()
    }

    private fun showStack(
        initial: List<Widget> = listOf(spacer(100), spacer(180), spacer(400)),
    ): MutableState<List<Widget>> {
        val widgets = mutableStateOf(initial)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    WidgetStackPager(widgets.value, 240.dp, Modifier.testTag("stack")) { child ->
                        val spacer = child as SpacerWidget
                        Text("Widget ${spacer.config.height}", Modifier.height(spacer.config.height.dp))
                    }
                }
            }
        }
        return widgets
    }

    private fun spacer(height: Int) = SpacerWidget(UUID.randomUUID(), SpacerWidgetConfig(height))
}
