package de.mm20.launcher2.ui.launcher.search.favorites

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.search.FavoritesCardSettingsData
import de.mm20.launcher2.preferences.ui.GridSettings
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.services.favorites.FavoritesCardItems
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.launcher.search.ListItemViewModel
import de.mm20.launcher2.ui.launcher.search.ListItemViewModelStore
import de.mm20.launcher2.ui.launcher.search.LocalListItemViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class FavoritesCardGridTest {
    @get:Rule val compose = createComposeRule()
    private val groups = FavoritesCardItems(listOf(Tag("pin")), listOf(Tag("recent")), listOf(Tag("frequent")))

    @Test
    fun cardDensityIsScopedToTheCardAndGroupHeadingsAreVisible() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalGridSettings provides GridSettings(columnCount = 5, iconSize = 48)) {
                    Column(Modifier.width(320.dp)) {
                        FavoritesCardGrid(groups.all, groups, FavoritesCardSettingsData(columns = 6, iconSize = 32), null, false) { items ->
                            val grid = LocalGridSettings.current
                            Text("${items.single().label}: ${grid.columnCount}/${grid.iconSize}")
                        }
                        val outside = LocalGridSettings.current
                        Text("App drawer: ${outside.columnCount}/${outside.iconSize}")
                    }
                }
            }
        }
        compose.onNodeWithText("Pinned").assertIsDisplayed()
        compose.onNodeWithText("Recently used").assertIsDisplayed()
        compose.onNodeWithText("Frequently used").assertIsDisplayed()
        compose.onNodeWithText("pin: 6/32").assertIsDisplayed()
        compose.onNodeWithText("App drawer: 5/48").assertIsDisplayed()
    }

    @Test
    fun hidingHeadingsCombinesGroupsIntoOneDenseGrid() {
        compose.setContent {
            MaterialTheme {
                FavoritesCardGrid(groups.all, groups, FavoritesCardSettingsData(showGroupLabels = false), null, false) { items ->
                    Text(items.joinToString { it.label })
                }
            }
        }
        compose.onNodeWithText("pin, recent, frequent").assertIsDisplayed()
        compose.onNodeWithText("Recently used").assertDoesNotExist()
    }

    @Test
    fun tagSelectionShowsAllTagMembersInsteadOfApplyingFavoriteGroupLimits() {
        compose.setContent {
            MaterialTheme {
                FavoritesCardGrid(listOf(Tag("member")), groups, FavoritesCardSettingsData(), "chosen tag", false) { items ->
                    Text(items.joinToString { it.label })
                }
            }
        }
        compose.onNodeWithText("member").assertIsDisplayed()
        compose.onNodeWithText("Recently used").assertDoesNotExist()
    }

    @Test
    fun narrowColumnsFitTheirIconsAndWidthChangesCanFitMoreColumns() {
        val requested = FavoritesCardSettingsData(columns = 10, iconSize = 64)
        val narrow = favoritesCardGridSettings(requested, 360f)
        val wide = favoritesCardGridSettings(requested, 800f)
        assertEquals(8, narrow.columnCount)
        assertEquals(28, narrow.iconSize)
        assertEquals(10, wide.columnCount)
        assertEquals(63, wide.iconSize)
        assertEquals(1, favoritesCardGridSettings(requested, 30f).columnCount)
    }

    @Test
    fun cardIconModelsAreIsolatedFromTheDrawerAndReleasedOnDisposal() {
        val shown = mutableStateOf(true)
        val outside = ListItemViewModelStore()
        var inside: ListItemViewModelStore? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalListItemViewModelStore provides outside) {
                    if (shown.value) {
                        FavoritesCardGrid(groups.all, groups, FavoritesCardSettingsData(showGroupLabels = false), null, false) {
                            inside = LocalListItemViewModelStore.current
                            Text("Card")
                        }
                    }
                }
            }
        }
        assertNotSame(outside, inside)
        lateinit var model: PresentationModel
        compose.runOnIdle {
            model = inside!!["card", PresentationModel::class.java]
            shown.value = false
        }
        compose.waitForIdle()
        assertTrue(model.cleared)
    }

    class PresentationModel : ListItemViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }
}
