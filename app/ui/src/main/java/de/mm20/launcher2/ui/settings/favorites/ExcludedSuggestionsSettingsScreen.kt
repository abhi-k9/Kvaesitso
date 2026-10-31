package de.mm20.launcher2.ui.settings.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object ExcludedSuggestionsSettingsRoute : NavKey

@Composable
fun ExcludedSuggestionsSettingsScreen() {
    val viewModel: ExcludedSuggestionsSettingsScreenVM = viewModel()
    val excluded by viewModel.items.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    PreferenceScreen(
        title = stringResource(R.string.suggestions_excluded_title),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.suggestions_excluded_summary),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (excluded?.isEmpty() == true) {
            item {
                Text(
                    stringResource(R.string.suggestions_excluded_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(excluded.orEmpty(), key = { it.key }) { item ->
            val icon by remember(item.key, density) {
                viewModel.getIcon(item, with(density) { 32.dp.roundToPx() })
            }.collectAsState(null)
            PreferenceCategory {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapedLauncherIcon(
                        size = 32.dp,
                        icon = { icon },
                        modifier = Modifier.padding(end = 20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.labelOverride ?: item.label, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { viewModel.restore(item) }) {
                        Icon(
                            painterResource(R.drawable.show_chart_24px),
                            contentDescription = stringResource(R.string.suggestions_allow_app),
                        )
                    }
                }
            }
        }
    }
}
