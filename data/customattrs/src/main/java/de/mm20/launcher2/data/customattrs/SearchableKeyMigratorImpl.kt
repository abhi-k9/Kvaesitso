package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableKeyMigrator
import de.mm20.launcher2.searchable.SavableSearchableRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SearchableKeyMigratorImpl(
    private val appDatabase: AppDatabase,
    private val searchableRepository: SavableSearchableRepository,
) : SearchableKeyMigrator {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun migrate(oldKey: String, newSearchable: SavableSearchable) {
        if (oldKey == newSearchable.key) return
        searchableRepository.replace(oldKey, newSearchable)
        scope.launch {
            appDatabase.customAttrsDao().replaceKey(oldKey, newSearchable.key)
        }
    }

    override suspend fun merge(oldKey: String, newSearchable: SavableSearchable) {
        if (oldKey == newSearchable.key) return
        searchableRepository.merge(oldKey, newSearchable)
        appDatabase.customAttrsDao().mergeKey(oldKey, newSearchable.key)
    }
}
