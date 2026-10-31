package de.mm20.launcher2.ui.settings.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.preferences.WeightFactor
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.launcher.sheets.EditFavoritesSheet
import kotlinx.serialization.Serializable

@Serializable
data object FavoritesSettingsRoute : NavKey

@Composable
fun FavoritesSettingsScreen() {
    val viewModel: FavoritesSettingsScreenVM = viewModel()
    val backStack = LocalBackStack.current
    var showEditSheet by remember { mutableStateOf(false) }
    val card by viewModel.card.collectAsState()
    val frequentEnabled by viewModel.frequentlyUsed.collectAsState()
    PreferenceScreen(
        title = stringResource(R.string.preference_search_favorites)
    ) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.menu_item_edit_favs),
                    summary = stringResource(R.string.preference_edit_favorites_summary),
                    icon = R.drawable.sort_24px,
                    onClick = {
                        showEditSheet = true
                    }
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.favorites_card_layout)) {
                SliderPreference(
                    title = stringResource(R.string.preference_grid_icon_size),
                    value = card.iconSize, min = 24, max = 96, step = 4,
                    onValueChanged = viewModel::setCardIconSize,
                )
                SliderPreference(
                    title = stringResource(R.string.preference_grid_column_count),
                    value = card.columns, min = 1, max = 10,
                    onValueChanged = viewModel::setCardColumns,
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_grid_labels),
                    value = card.showLabels,
                    onValueChanged = viewModel::setCardLabels,
                )
                SwitchPreference(
                    title = stringResource(R.string.favorites_card_group_labels),
                    value = card.showGroupLabels,
                    onValueChanged = viewModel::setCardGroupLabels,
                )
                Preference(
                    title = stringResource(R.string.favorites_card_reset_layout),
                    summary = stringResource(R.string.favorites_card_layout_summary),
                    onClick = viewModel::resetCardLayout,
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.favorites_card_contents)) {
                Text(stringResource(R.string.favorites_card_counts_summary), Modifier.padding(16.dp))
                SliderPreference(
                    title = stringResource(R.string.favorites_card_pinned),
                    value = card.pinnedCount, min = -1, max = 60,
                    onValueChanged = viewModel::setCardPinnedCount,
                    label = { Text(if (it < 0) stringResource(R.string.favorites_card_all) else it.toString()) },
                )
                SliderPreference(
                    title = stringResource(R.string.favorites_card_recent),
                    value = card.recentCount, min = 0, max = 60,
                    onValueChanged = viewModel::setCardRecentCount,
                )
                SliderPreference(
                    title = stringResource(R.string.favorites_card_frequent),
                    value = card.frequentCount, min = -1, max = 60,
                    enabled = frequentEnabled != false,
                    onValueChanged = viewModel::setCardFrequentCount,
                    label = { Text(if (it < 0) stringResource(R.string.favorites_card_row_limit) else it.toString()) },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.preference_category_favorites_frequently_used)) {
                val frequentlyUsed by viewModel.frequentlyUsed.collectAsState()
                SwitchPreference(
                    title = stringResource(R.string.frequently_used_show_in_favorites),
                    summary = stringResource(R.string.preference_favorites_frequently_used_summary),
                    value = frequentlyUsed == true,
                    onValueChanged = {
                        viewModel.setFrequentlyUsed(it)
                    },
                    icon = R.drawable.show_chart_24px,
                )
                val frequentlyUsedRows by viewModel.frequentlyUsedRows.collectAsState()
                SliderPreference(
                    title = stringResource(R.string.frequently_used_rows),
                    value = frequentlyUsedRows,
                    enabled = frequentlyUsed == true,
                    min = 1,
                    max = 8,
                    onValueChanged = {
                        viewModel.setFrequentlyUsedRows(it)
                    },
                    icon = R.drawable.table_rows_24px,
                )
                Preference(
                    title = stringResource(R.string.suggestions_excluded_title),
                    summary = stringResource(R.string.suggestions_excluded_summary),
                    icon = R.drawable.visibility_off_24px,
                    onClick = { backStack.add(ExcludedSuggestionsSettingsRoute) },
                )
                val searchResultWeightFactor by viewModel.searchResultWeightFactor.collectAsState()
                ListPreference(
                    title = stringResource(R.string.preference_search_result_ordering_weight_factor),
                    icon = R.drawable.swap_vert_24px,
                    value = searchResultWeightFactor,
                    items = listOf(
                        stringResource(R.string.preference_search_result_ordering_weight_factor_low) to WeightFactor.Low,
                        stringResource(R.string.preference_search_result_ordering_weight_factor_default) to WeightFactor.Default,
                        stringResource(R.string.preference_search_result_ordering_weight_factor_high) to WeightFactor.High
                    ),
                    onValueChanged = { viewModel.setSearchResultWeightFactor(it) }
                )
            }
        }
        item {
            val editButton by viewModel.editButton.collectAsState()
            val compactTags by viewModel.compactTags.collectAsState()
            val tagsFixed by viewModel.tagsFixed.collectAsState()
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_edit_button),
                    summary = stringResource(R.string.preference_favorites_edit_button_summary),
                    value = editButton == true,
                    onValueChanged = {
                        viewModel.setEditButton(it)
                    },
                    icon = R.drawable.edit_24px,
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_compact_tags),
                    summary = stringResource(R.string.preference_compact_tags_summary),
                    value = compactTags == true,
                    onValueChanged = {
                        viewModel.setCompactTags(it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.preference_fixed_tags),
                    summary = stringResource(R.string.preference_fixed_tags_summary),
                    value = tagsFixed == true,
                    onValueChanged = {
                        viewModel.setTagsFixed(it)
                    },
                )
            }
        }
    }

    EditFavoritesSheet(
        expanded = showEditSheet,
        onDismiss = { showEditSheet = false }
    )
}
