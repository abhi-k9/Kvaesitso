package de.mm20.launcher2.data.customattrs.utils

import de.mm20.launcher2.data.customattrs.CustomAttributesRepository
import de.mm20.launcher2.data.customattrs.CustomLabel
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

fun <T: SavableSearchable>Flow<List<T>>.withCustomLabels(
    customAttributesRepository: CustomAttributesRepository,
): Flow<List<T>> = channelFlow {
    this@withCustomLabels.collectLatest { items ->
        val customLabels = customAttributesRepository.getCustomLabels(items)
        customLabels.collectLatest { labels ->
            val labelsByKey = mutableMapOf<String, CustomLabel>()
            // Keep the same first match as the previous list search if old data has duplicates.
            for (label in labels) labelsByKey.putIfAbsent(label.key, label)
            send(items.map { item ->
                    val customLabel = labelsByKey[item.key]
                    if (customLabel != null) {
                        item.overrideLabel(customLabel.label) as T
                    } else {
                        item
                    }
            })
        }
    }
}
