package de.mm20.launcher2.search

/**
 * Moves everything that is stored for an item (favorite, visibility, launch count, custom label,
 * tags and icon) to another key, when the same item gets a new key. For example, some apps change
 * their icon by switching to another launcher activity.
 */
interface SearchableKeyMigrator {
    fun migrate(oldKey: String, newSearchable: SavableSearchable)

    /**
     * Like [migrate], but what's stored for the new key already is kept, e.g. if both keys are in
     * a folder, the item is in it once. Returns when it's done.
     */
    suspend fun merge(oldKey: String, newSearchable: SavableSearchable)
}
