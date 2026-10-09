package de.mm20.launcher2.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomAttrsDao {
    @Query("SELECT * FROM CustomAttributes WHERE type = :type AND `key` = :key LIMIT 1")
    fun getCustomAttribute(key: String, type: String) : Flow<CustomAttributeEntity?>

    @Query("DELETE FROM CustomAttributes WHERE type = :type AND `key` = :key")
    fun clearCustomAttribute(key: String, type: String)

    @Insert
    fun setCustomAttribute(entity: CustomAttributeEntity)

    @Insert
    suspend fun insertCustomAttributes(entities: List<CustomAttributeEntity>)

    @Query("SELECT * FROM CustomAttributes WHERE type = :type AND `key` IN (:keys)")
    fun getCustomAttributes(keys: List<String>, type: String) : Flow<List<CustomAttributeEntity>>

    @Query("SELECT DISTINCT `key` FROM CustomAttributes WHERE (type = 'label' OR type = 'tag') AND value LIKE :query")
    fun search(query: String): Flow<List<String>>

    @Transaction
    suspend fun setTags(key: String, tags: List<CustomAttributeEntity>) {
        clearCustomAttribute(key, "tag")
        insertCustomAttributes(tags)
    }

    @Query("SELECT DISTINCT value FROM CustomAttributes WHERE type = 'tag' AND value LIKE :like ORDER BY value")
    fun getAllTagsLike(like: String): Flow<List<String>>

    @Query("SELECT DISTINCT value FROM CustomAttributes WHERE type = 'tag' ORDER BY value")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT `key` FROM CustomAttributes WHERE type = 'tag' AND value = :tag")
    fun getItemsWithTag(tag: String): Flow<List<String>>

    @Transaction
    suspend fun setItemsWithTag(tag: String, items: List<String>) {
        deleteTag(tag)
        insertCustomAttributes(items.map { CustomAttributeEntity(it, "tag", tag) })
    }

    @Transaction
    suspend fun addTag(key: String, tag: String) {
        removeTag(key, tag)
        insertTag(key, tag)
    }

    @Query("DELETE FROM CustomAttributes WHERE type = 'tag' AND `key` = :key AND value = :tag")
    suspend fun removeTag(key: String, tag: String)

    @Query("INSERT INTO CustomAttributes (`key`, value, type) VALUES (:key, :tag, 'tag')")
    suspend fun insertTag(key: String, tag: String)

    @Query("UPDATE CustomAttributes SET value = :newName WHERE value = :oldName AND type = 'tag'")
    suspend fun renameTag(oldName: String, newName: String)

    @Query("DELETE FROM CustomAttributes WHERE type = 'tag' AND value = :tag")
    suspend fun deleteTag(tag: String)

    /**
     * Removes the folder flags of tags that no item has anymore
     */
    @Query("DELETE FROM CustomAttributes WHERE type = 'folder' AND `key` NOT IN (SELECT 'tag://' || value FROM CustomAttributes WHERE type = 'tag')")
    suspend fun deleteUnusedFolders(): Int

    /**
     * Moves all attributes of an item to a new key. They replace the attributes already stored
     * for the new key, if any.
     */
    @Transaction
    suspend fun replaceKey(oldKey: String, newKey: String) {
        if (oldKey == newKey || !hasAttributes(oldKey)) return
        deleteAttributes(newKey)
        moveAttributes(oldKey, newKey)
    }

    /**
     * Moves the attributes of an item to a new key, like [replaceKey], but keeps those that are
     * already stored for the new key: tags are added, and other attributes are only moved if the
     * new key has none of the same type. The rest is removed.
     */
    @Transaction
    suspend fun mergeKey(oldKey: String, newKey: String) {
        if (oldKey == newKey) return
        moveMissingTags(oldKey, newKey)
        moveMissingAttributes(oldKey, newKey)
        deleteAttributes(oldKey)
    }

    @Query("UPDATE CustomAttributes SET `key` = :newKey WHERE `key` = :oldKey AND type = 'tag' AND value NOT IN (SELECT value FROM CustomAttributes WHERE `key` = :newKey AND type = 'tag')")
    suspend fun moveMissingTags(oldKey: String, newKey: String)

    @Query("UPDATE CustomAttributes SET `key` = :newKey WHERE `key` = :oldKey AND type != 'tag' AND type NOT IN (SELECT type FROM CustomAttributes WHERE `key` = :newKey)")
    suspend fun moveMissingAttributes(oldKey: String, newKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM CustomAttributes WHERE `key` = :key)")
    suspend fun hasAttributes(key: String): Boolean

    @Query("DELETE FROM CustomAttributes WHERE `key` = :key")
    suspend fun deleteAttributes(key: String)

    @Query("UPDATE CustomAttributes SET `key` = :newKey WHERE `key` = :oldKey")
    suspend fun moveAttributes(oldKey: String, newKey: String)

}