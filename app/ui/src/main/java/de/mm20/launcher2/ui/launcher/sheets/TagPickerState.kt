package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.mm20.launcher2.search.SavableSearchable
import java.text.Normalizer
import java.util.Locale

private val DiacriticMarks = Regex("\\p{M}+")
private val WordSeparators = Regex("\\s+")

/** Selection is independent of the visible filter, so searching never discards checked items. */
@Stable
internal class TagPickerSelection(initial: List<SavableSearchable> = emptyList()) {
    var items by mutableStateOf(initial.distinctBy { it.key })
        private set
    val keys by derivedStateOf { items.mapTo(mutableSetOf()) { it.key } }

    fun set(item: SavableSearchable, selected: Boolean) {
        items = if (selected) {
            if (item.key in keys) items else items + item
        } else items.filterNot { it.key == item.key }
    }
}

internal fun normalizeTagPickerText(text: String): String = Normalizer
    .normalize(text, Normalizer.Form.NFD)
    .replace(DiacriticMarks, "")
    .lowercase(Locale.ROOT)

internal fun filterTaggableItems(
    items: List<TaggableItem>,
    query: String,
    onlyUntagged: Boolean,
): List<TaggableItem> {
    val terms = normalizeTagPickerText(query).split(WordSeparators).filter { it.isNotEmpty() }
    if (terms.isEmpty() && !onlyUntagged) return items
    return items.filter { item ->
        (!onlyUntagged || !item.hasAnyTags) && terms.all { it in item.searchText }
    }
}
