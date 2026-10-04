package de.mm20.launcher2.search

/**
 * Moves everything that is stored for an item (favorite, visibility, launch count, custom label,
 * tags and icon) to another key, when the same item gets a new key. For example, some apps change
 * their icon by switching to another launcher activity.
 */
interface SearchableKeyMigrator {
    fun migrate(oldKey: String, newSearchable: SavableSearchable)
}
