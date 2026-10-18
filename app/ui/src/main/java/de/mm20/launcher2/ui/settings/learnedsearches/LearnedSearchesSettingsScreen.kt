package de.mm20.launcher2.ui.settings.learnedsearches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.serialization.Serializable

@Serializable
data object LearnedSearchesSettingsRoute : NavKey

@Composable
fun LearnedSearchesSettingsScreen() {
    val viewModel: LearnedSearchesSettingsScreenVM = viewModel()
    val density = LocalDensity.current

    val learnFromLaunches by viewModel.learnFromLaunches.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var confirmForgetAll by remember { mutableStateOf(false) }

    PreferenceScreen(
        title = stringResource(R.string.preference_learned_searches),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            PreferenceCategory {
                SwitchPreference(
                    title = stringResource(R.string.preference_learn_from_launches),
                    summary = stringResource(R.string.preference_learn_from_launches_summary),
                    value = learnFromLaunches == true,
                    onValueChanged = { viewModel.setLearnFromLaunches(it) },
                )
                Preference(
                    title = stringResource(R.string.learned_searches_forget_all),
                    icon = R.drawable.delete_24px,
                    enabled = !results.isNullOrEmpty(),
                    onClick = { confirmForgetAll = true },
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
        val list = results ?: return@PreferenceScreen
        if (list.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.learned_searches_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        itemsIndexed(
            list,
            key = { _, it -> "${it.search.query}|${it.search.key}" }
        ) { i, result ->
            val icon by remember(result.item.key) {
                viewModel.getIcon(result.item, with(density) { 32.dp.roundToPx() })
            }.collectAsState(null)

            val xs = MaterialTheme.shapes.extraSmall
            val md = MaterialTheme.shapes.medium

            Row(
                modifier = Modifier
                    .clip(
                        xs.copy(
                            topStart = if (i == 0) md.topStart else xs.topStart,
                            topEnd = if (i == 0) md.topEnd else xs.topEnd,
                            bottomStart = if (i == list.lastIndex) md.bottomStart else xs.bottomStart,
                            bottomEnd = if (i == list.lastIndex) md.bottomEnd else xs.bottomEnd
                        )
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedLauncherIcon(
                    size = 32.dp,
                    icon = { icon },
                    modifier = Modifier.padding(end = 20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.item.labelOverride ?: result.item.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.learned_search_query, result.search.query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { viewModel.remove(result.search) }) {
                    Icon(
                        painterResource(R.drawable.delete_24px),
                        contentDescription = stringResource(R.string.learned_search_forget),
                    )
                }
            }
        }
    }

    if (confirmForgetAll) {
        AlertDialog(
            onDismissRequest = { confirmForgetAll = false },
            title = { Text(stringResource(R.string.learned_searches_forget_all_title)) },
            text = { Text(stringResource(R.string.learned_searches_forget_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmForgetAll = false
                }) {
                    Text(stringResource(R.string.learned_searches_forget_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetAll = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
