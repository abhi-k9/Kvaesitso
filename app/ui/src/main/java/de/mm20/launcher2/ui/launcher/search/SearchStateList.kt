package de.mm20.launcher2.ui.launcher.search

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Provider updates often change only one category. Leave the other categories untouched. */
internal fun <T> SnapshotStateList<T>.updateItems(newItems: List<T>?) {
    val items = newItems ?: emptyList()
    // SnapshotStateList itself has identity equality; compare its immutable contents instead.
    if (toList() == items) return
    // Publish one replacement so observers never receive a transient empty result list.
    Snapshot.withMutableSnapshot {
        clear()
        addAll(items)
    }
}
