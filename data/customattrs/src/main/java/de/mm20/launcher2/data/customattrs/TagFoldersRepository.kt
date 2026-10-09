package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tags that are shown as folders on the search screen. A folder is a tag with a "folder" custom
 * attribute, so folders are included in backups and don't need a database migration.
 */
interface TagFoldersRepository {
    /**
     * The names of the tags that are shown as folders. Tags without items aren't included.
     */
    val folderTags: Flow<Set<String>>

    /**
     * The items of each folder, by tag name, like [getItems].
     */
    val folders: Flow<Map<String, List<SavableSearchable>>>

    /**
     * The items in the folder [tag]. Like in the favorites, items that are set to hidden are left
     * out.
     */
    fun getItems(tag: String): Flow<List<SavableSearchable>>

    fun isFolder(tag: String): Flow<Boolean>

    /**
     * Shows [tag] as a folder or not. If the tag has been renamed from [oldTag], that tag isn't
     * a folder anymore.
     */
    fun setFolder(tag: String, isFolder: Boolean, oldTag: String? = null)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class TagFoldersRepositoryImpl(
    private val appDatabase: AppDatabase,
    private val customAttributesRepository: CustomAttributesRepository,
    searchableRepository: SavableSearchableRepository,
) : TagFoldersRepository {
    // One at a time, so that changes are written in order
    private val scope = CoroutineScope(Job() + Dispatchers.IO.limitedParallelism(1))

    private val hiddenKeys: Flow<Set<String>> = searchableRepository.getKeys(
        minVisibility = VisibilityLevel.Hidden,
        maxVisibility = VisibilityLevel.Hidden,
    ).map { it.toSet() }

    override val folderTags: Flow<Set<String>> = customAttributesRepository.getAllTags()
        .flatMapLatest { tags ->
            if (tags.isEmpty()) return@flatMapLatest flowOf(emptySet())
            val dao = appDatabase.customAttrsDao()
            // SQLite < 3.32 (Android 11 and older) allows at most 999 variables per query, and
            // the query has one more (the type)
            combine(tags.chunked(998).map { chunk ->
                dao.getCustomAttributes(chunk.map { Tag(it).key }, FolderAttribute)
            }) { results ->
                results.flatMap { list -> list.map { it.key.removePrefix(TagKeyPrefix) } }.toSet()
            }
        }
        .distinctUntilChanged()

    override val folders: Flow<Map<String, List<SavableSearchable>>> = folderTags
        .flatMapLatest { tags ->
            if (tags.isEmpty()) return@flatMapLatest flowOf(emptyMap())
            combine(tags.map { tag ->
                customAttributesRepository.getItemsForTag(tag).map { tag to it }
            }) { it.toMap() }
                .combine(hiddenKeys) { folders, hidden ->
                    folders.mapValues { (_, items) -> items.withoutHidden(hidden) }
                }
        }

    override fun getItems(tag: String): Flow<List<SavableSearchable>> {
        return customAttributesRepository.getItemsForTag(tag)
            .combine(hiddenKeys) { items, hidden -> items.withoutHidden(hidden) }
    }

    private fun List<SavableSearchable>.withoutHidden(hidden: Set<String>): List<SavableSearchable> {
        if (hidden.isEmpty()) return this
        return filterNot { it.key in hidden }
    }

    override fun isFolder(tag: String): Flow<Boolean> {
        return appDatabase.customAttrsDao().getCustomAttribute(Tag(tag).key, FolderAttribute)
            .map { it != null }
            .distinctUntilChanged()
    }

    override fun setFolder(tag: String, isFolder: Boolean, oldTag: String?) {
        scope.launch {
            val dao = appDatabase.customAttrsDao()
            if (oldTag != null && oldTag != tag) {
                dao.clearCustomAttribute(Tag(oldTag).key, FolderAttribute)
            }
            val key = Tag(tag).key
            dao.clearCustomAttribute(key, FolderAttribute)
            if (isFolder) {
                dao.setCustomAttribute(
                    CustomAttributeEntity(key = key, type = FolderAttribute, value = "true")
                )
            }
        }
    }

    companion object {
        private const val FolderAttribute = "folder"
        private const val TagKeyPrefix = "${Tag.Domain}://"
    }
}

/**
 * Items of a list that aren't in any folder, and the folders that have items.
 */
data class FolderGrouping<T : SavableSearchable>(
    val ungrouped: List<T>,
    val folders: List<Tag>,
)

/**
 * Groups this list into [folders] (by tag name, as from [TagFoldersRepository.folders]): items in
 * a folder are only shown in the folder.
 */
fun <T : SavableSearchable> List<T>.groupIntoFolders(
    folders: Map<String, List<SavableSearchable>>,
): FolderGrouping<T> {
    val nonEmptyFolders = folders.filterValues { it.isNotEmpty() }
    if (nonEmptyFolders.isEmpty()) return FolderGrouping(this, emptyList())
    val groupedKeys = nonEmptyFolders.values.flatMapTo(HashSet()) { items -> items.map { it.key } }
    return FolderGrouping(
        ungrouped = filterNot { it.key in groupedKeys },
        folders = nonEmptyFolders.keys.map { Tag(it) },
    )
}

/**
 * [items] with [folders] sorted in by label, like the app list is sorted.
 */
fun mergeFolders(items: List<SavableSearchable>, folders: List<Tag>): List<SavableSearchable> {
    if (folders.isEmpty()) return items
    return (items + folders).sorted()
}
