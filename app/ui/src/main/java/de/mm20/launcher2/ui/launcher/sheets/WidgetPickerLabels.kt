package de.mm20.launcher2.ui.launcher.sheets

import android.appwidget.AppWidgetProviderInfo

internal data class WidgetProviderLabels(
    val info: AppWidgetProviderInfo,
    val widgetLabel: String,
    val appLabel: String?,
)

/** Resolve labels for one provider snapshot; no package lookups remain in filtering or sorting. */
internal fun resolveWidgetProviderLabels(
    providers: List<AppWidgetProviderInfo>,
    widgetLabel: (AppWidgetProviderInfo) -> String,
    appLabel: (String) -> String?,
): List<WidgetProviderLabels> {
    val appLabels = mutableMapOf<String, String?>()
    return providers.map { info ->
        val pkg = info.provider.packageName
        if (!appLabels.containsKey(pkg)) appLabels[pkg] = appLabel(pkg)
        WidgetProviderLabels(info, widgetLabel(info), appLabels[pkg])
    }
}
