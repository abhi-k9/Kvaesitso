package de.mm20.launcher2.ui.launcher.sheets

import de.mm20.launcher2.services.widgets.WidgetsService
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.KeyboardLayout
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.search.StringNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.text.Collator

class WidgetPickerSheetVM(
    private val widgetsService: WidgetsService,
    private val packageManager: PackageManager,
    private val stringNormalizer: StringNormalizer,
    private val keyboardLayouts: Flow<KeyboardLayout> = flowOf(KeyboardLayout.Qwerty),
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val enabledWidgets = widgetsService.getWidgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(100), emptyList())

    private val allBuiltInWidgets =
        widgetsService.getAvailableBuiltInWidgets()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val matchingQuery = combine(searchQuery, keyboardLayouts) { query, layout -> query to layout }

    val builtInWidgets = allBuiltInWidgets
        .combine(matchingQuery) { widgets, (query, layout) ->
            if (query.isBlank()) return@combine widgets
            withContext(Dispatchers.IO) {
                val normalizedQuery = stringNormalizer.normalize(query)
                val scorer = ResultScore.prepare(normalizedQuery, layout)
                widgets.filter {
                    scorer.score(
                        primaryFields = listOf(
                            stringNormalizer.normalize(it.label),
                            it.type
                        ),
                    ).score >= 0.8f
                }
            }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val allAppWidgets = flow {
        val widgets = widgetsService.getAppWidgetProviders()
        // Resolve Android/package-manager labels once per provider snapshot. Sorting and each
        // keystroke then use strings, without repeatedly asking Android about the same package.
        emit(withContext(Dispatchers.IO) {
            resolveWidgetProviderLabels(
                widgets,
                widgetLabel = { it.loadLabel(packageManager) },
                appLabel = { pkg ->
                    try {
                        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                },
            )
        })
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    private val filteredAppWidgets = allAppWidgets
        .combine(matchingQuery) { widgets, (query, layout) ->
            if (query.isBlank()) return@combine widgets
            withContext(Dispatchers.IO) {
                val normalizedQuery = stringNormalizer.normalize(query)
                val scorer = ResultScore.prepare(normalizedQuery, layout)
                widgets.filter {
                    val widgetNormalizedLabel = stringNormalizer.normalize(it.widgetLabel)
                    if (widgetNormalizedLabel.contains(normalizedQuery)) {
                        return@filter true
                    }
                    val appLabel = it.appLabel ?: return@filter false
                    val normalizedAppLabel = stringNormalizer.normalize(appLabel)

                    scorer.score(
                        primaryFields = listOf(
                            widgetNormalizedLabel,
                            normalizedAppLabel,
                        ),
                    ).score >= 0.8f
                }
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val expandAllGroups = filteredAppWidgets.map {
        it.size < 10
    }

    val appWidgetGroups = filteredAppWidgets.map { widgets ->
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        withContext(Dispatchers.Default) {
            widgets
                .sortedWith { el1, el2 ->
                    collator.compare(el1.widgetLabel, el2.widgetLabel)
                }
                .groupBy {
                    it.info.provider.packageName
                }
                .map {
                    val pkg = it.key
                    val appLabel = it.value.firstOrNull()?.appLabel
                        ?: return@map AppWidgetGroup("", pkg, emptyList())
                    AppWidgetGroup(appLabel, pkg, it.value.map { entry -> entry.info })
                }
                .sortedWith { el1, el2 ->
                    collator.compare(el1.appName, el2.appName)
                }
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(100))

    val expandedGroup = mutableStateOf<String?>(null)

    fun toggleGroup(group: String) {
        expandedGroup.value = if (expandedGroup.value == group) null else group
    }

    fun search(query: String) {
        searchQuery.value = query
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                WidgetPickerSheetVM(get(), get(), get(), get<SearchUiSettings>().keyboardLayout)
            }
        }
    }

}

data class AppWidgetGroup(
    val appName: String,
    val packageName: String,
    val widgets: List<AppWidgetProviderInfo>
)

data class BuiltInWidgetInfo(
    val type: String,
    @StringRes val label: Int,
    val icon: ImageVector
)
