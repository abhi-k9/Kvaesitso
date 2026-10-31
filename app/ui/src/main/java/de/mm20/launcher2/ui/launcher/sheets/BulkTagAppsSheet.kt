package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheet

/** A drawer entry point for tagging a selection of apps without customizing them one by one. */
@Composable
fun BulkTagAppsSheet(initialApp: Application?, onDismiss: () -> Unit) {
    val viewModel: BulkTagAppsSheetVM = viewModel()
    BottomSheet(
        state = initialApp,
        expanded = { it != null },
        onDismissRequest = { if (!viewModel.saving) onDismiss() },
    ) { app ->
        if (app == null) return@BottomSheet
        DisposableEffect(viewModel) { onDispose { viewModel.stopLoading() } }
        LaunchedEffect(app.key) { viewModel.init(app) }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tagging_bulk_action)) })
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                value = viewModel.tagName,
                onValueChange = { viewModel.tagName = it },
                enabled = !viewModel.saving,
                singleLine = true,
                label = { Text(stringResource(R.string.tag_name)) },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (tag in viewModel.suggestedTags) {
                    AssistChip(
                        onClick = { viewModel.tagName = tag },
                        enabled = !viewModel.saving,
                        label = { Text(tag) },
                    )
                }
            }
            if (viewModel.loading || viewModel.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (viewModel.saveFailed) {
                Text(
                    stringResource(R.string.tagging_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                TagItemPicker(
                    apps = viewModel.visibleApps,
                    query = viewModel.query,
                    onQueryChange = { viewModel.query = it },
                    onlyUntagged = viewModel.onlyUntagged,
                    onOnlyUntaggedChange = { viewModel.onlyUntagged = it },
                    selectedCount = viewModel.selection.items.size,
                    getIcon = viewModel::getIcon,
                    onSelectionChange = viewModel::select,
                    enabled = !viewModel.saving,
                )
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !viewModel.saving) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    enabled = !viewModel.loading && !viewModel.saving &&
                        viewModel.tagName.isNotBlank() && viewModel.selection.items.isNotEmpty(),
                    onClick = { viewModel.applyTag(onDismiss) },
                ) { Text(stringResource(R.string.tagging_apply_tag)) }
            }
        }
    }
}
