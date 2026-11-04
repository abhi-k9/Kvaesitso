package de.mm20.launcher2.ui.launcher.search.favorites

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.search.FavoritesCardSettingsData
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.favorites.FavoritesCardItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class FavoritesCardRowTest {
    @get:Rule val compose = createComposeRule()
    private val groups = FavoritesCardItems(listOf(Tag("pin")), listOf(Tag("recent")), listOf(Tag("frequent")))

    @Test
    fun groupsShareOneRowInPinnedRecentFrequentOrder() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    FavoritesCardRow(groups.all, groups, FavoritesCardSettingsData(iconSize = 32), null) { item, modifier ->
                        Box(modifier.height(56.dp).testTag(item.key)) { Text(item.label) }
                    }
                }
            }
        }
        for (title in listOf("Pinned", "Recent", "Frequent")) compose.onNodeWithText(title).assertIsDisplayed()
        val bounds = groups.all.map { compose.onNodeWithTag(it.key).fetchSemanticsNode().boundsInRoot }
        assertEquals(bounds[0].top, bounds[1].top, 0.1f)
        assertEquals(bounds[0].top, bounds[2].top, 0.1f)
        assertTrue(bounds[0].right <= bounds[1].left)
        assertTrue(bounds[1].right <= bounds[2].left)
        // The three formerly stacked groups now occupy one icon row plus its headings.
        val row = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        val heightDp = with(compose.density) { row.height.toDp().value }
        assertTrue("Compact row was $heightDp dp tall", heightDp < 3 * 56f)
    }

    @Test
    fun overflowCanReachTheLastFrequentAppWithoutExpandingTheCard() {
        val many = FavoritesCardItems(
            (0..39).map { Tag("pin-$it") }, listOf(Tag("recent-last")), listOf(Tag("frequent-last")),
        )
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    FavoritesCardRow(many.all, many, FavoritesCardSettingsData(), null) { item, modifier ->
                        Box(modifier.height(56.dp)) { Text(item.label) }
                    }
                }
            }
        }
        compose.onNodeWithText("pin-0").assertIsDisplayed()
        compose.onNodeWithText("frequent-last").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("frequent-last"))
        compose.onNodeWithText("frequent-last").assertIsDisplayed()
    }

    @Test
    fun changingTagsResetsTheScrollAndDoesNotApplyFavoriteQuotas() {
        val tag = mutableStateOf<String?>(null)
        val members = (0..39).map { Tag("member-$it") }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    FavoritesCardRow(
                        if (tag.value == null) members else listOf(Tag("new-member")),
                        groups,
                        FavoritesCardSettingsData(pinnedCount = 0, recentCount = 0, frequentCount = 0),
                        tag.value,
                    ) { item, modifier -> Box(modifier.height(56.dp)) { Text(item.label) } }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("member-39"))
        compose.onNodeWithText("member-39").assertIsDisplayed()
        compose.runOnIdle { tag.value = "chosen" }
        compose.onNodeWithText("new-member").assertIsDisplayed()
        compose.onNodeWithText("Pinned").assertDoesNotExist()
        compose.onNodeWithText("Recent").assertDoesNotExist()
    }

    @Test
    fun hidingHeadingsDoesNotIntroduceVerticalGaps() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) {
                    FavoritesCardRow(groups.all, groups, FavoritesCardSettingsData(showGroupLabels = false), null) { item, modifier ->
                        Box(modifier.height(56.dp).testTag(item.key)) { Text(item.label) }
                    }
                }
            }
        }
        compose.onNodeWithText("Pinned").assertDoesNotExist()
        compose.onNodeWithText("Recent").assertDoesNotExist()
        val bounds = groups.all.map { compose.onNodeWithTag(it.key).fetchSemanticsNode().boundsInRoot }
        assertEquals(bounds[0].top, bounds[2].top, 0.1f)
    }

    @Test
    fun rightToLeftStartsWithPinsAtTheRightAndCanScrollToTheEnd() {
        val many = groups.copy(frequent = (0..15).map { Tag("frequent-$it") })
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(Modifier.width(320.dp)) {
                        FavoritesCardRow(many.all, many, FavoritesCardSettingsData(iconSize = 32), null) { item, modifier ->
                            Box(modifier.height(56.dp).testTag(item.key)) { Text(item.label) }
                        }
                    }
                }
            }
        }
        val pin = compose.onNodeWithTag(groups.pinned.single().key).fetchSemanticsNode().boundsInRoot
        val recent = compose.onNodeWithTag(groups.recent.single().key).fetchSemanticsNode().boundsInRoot
        assertTrue(pin.left >= recent.right)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("frequent-15"))
        compose.onNodeWithText("frequent-15").assertIsDisplayed()
    }

    @Test
    fun narrowScreensAndLargeTypeKeepLargeIconsFromOverlapping() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                    Column(Modifier.width(240.dp)) {
                        FavoritesCardRow(groups.all, groups, FavoritesCardSettingsData(columns = 10, iconSize = 96), null) { item, modifier ->
                            Box(modifier.height(112.dp).testTag(item.key)) { Text(item.label) }
                        }
                    }
                }
            }
        }
        val pin = compose.onNodeWithTag(groups.pinned.single().key).fetchSemanticsNode().boundsInRoot
        val recent = compose.onNodeWithTag(groups.recent.single().key).fetchSemanticsNode().boundsInRoot
        assertTrue(pin.width >= 112f)
        assertTrue(pin.right <= recent.left)
        assertEquals(pin.top, recent.top, 0.1f)
    }

    @Test
    fun swipingPastEitherEndDoesNotHandHorizontalMovementToTheLauncher() {
        var horizontalOverscroll = 0f
        var horizontalMovement = 0f
        val many = groups.copy(pinned = (0..19).map { Tag("pin-$it") })
        val parent = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                horizontalOverscroll += kotlin.math.abs(available.x)
                horizontalMovement += kotlin.math.abs(consumed.x)
                return Offset.Zero
            }
        }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp).nestedScroll(parent)) {
                    FavoritesCardRow(many.all, many, FavoritesCardSettingsData(), null) { item, modifier ->
                        Box(modifier.height(56.dp)) { Text(item.label) }
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeRight() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("frequent"))
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertTrue(horizontalMovement > 0f)
            assertEquals(0f, horizontalOverscroll, 0.1f)
        }
    }

    @Test
    fun verticalSwipesOverTheStripStillScrollTheAppDrawer() {
        val drawer = LazyListState()
        compose.setContent {
            MaterialTheme {
                LazyColumn(Modifier.width(320.dp).height(300.dp), state = drawer) {
                    item {
                        FavoritesCardRow(groups.all, groups, FavoritesCardSettingsData(), null) { item, modifier ->
                            Box(modifier.height(56.dp)) { Text(item.label) }
                        }
                    }
                    items(30) { Text("App row $it", Modifier.height(56.dp)) }
                }
            }
        }
        compose.onNode(hasText("Pinned")).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(drawer.firstVisibleItemIndex > 0 || drawer.firstVisibleItemScrollOffset > 0) }
    }
}
