package de.mm20.launcher2.ui.launcher.search

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SearchResultListTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sparseReversedResultsStayAboveTheBottomInset() {
        showList(reverse = true)
        compose.onNodeWithTag("results").assertHeightIsEqualTo(400.dp)
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(280.dp)
    }

    @Test
    fun normalResultsStayAtTheTopInset() {
        showList(reverse = false)
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(12.dp)
    }

    @Test
    fun reversedResultsFollowKeyboardAndFixedTagsInsets() {
        val bottomInset = mutableStateOf(80.dp)
        val viewportHeight = mutableStateOf(400.dp)
        compose.setContent {
            Box(Modifier.size(320.dp, viewportHeight.value)) {
                SearchResultList(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = bottomInset.value),
                    reverseLayout = true,
                    userScrollEnabled = true,
                ) {
                    item { Result(0) }
                }
            }
        }
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(280.dp)
        // Keyboard consumes another 120 dp of the viewport.
        compose.runOnIdle { bottomInset.value = 200.dp }
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(160.dp)
        // Closing the keyboard must return the result to the bottom, not to the top.
        compose.runOnIdle { bottomInset.value = 80.dp }
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(280.dp)
        // Reserve another 48 dp for a fixed tags bar.
        compose.runOnIdle { bottomInset.value = 128.dp }
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(232.dp)
        compose.runOnIdle { viewportHeight.value = 360.dp }
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(192.dp)
    }

    @Test
    fun narrowingResultsFromLongListToOneRetainsBottomAlignment() {
        val count = mutableIntStateOf(30)
        compose.setContent {
            Box(Modifier.size(320.dp, 400.dp)) {
                SearchResultList(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    reverseLayout = true,
                    userScrollEnabled = true,
                ) {
                    items(count.intValue) { Result(it) }
                }
            }
        }
        compose.runOnIdle { count.intValue = 1 }
        compose.onNodeWithTag("result-0").assertTopPositionInRootIsEqualTo(280.dp)
    }

    private fun showList(reverse: Boolean) {
        compose.setContent {
            Box(Modifier.size(320.dp, 400.dp)) {
                SearchResultList(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    reverseLayout = reverse,
                    userScrollEnabled = true,
                    modifier = Modifier.testTag("results"),
                ) {
                    // SearchColumn retains an empty favorites item when favorites are hidden.
                    item(key = "favorites") {}
                    item { Result(0) }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Result(index: Int) {
        Box(Modifier.fillMaxWidth().height(40.dp).testTag("result-$index"))
    }
}
