package de.mm20.launcher2.data.customattrs

import android.content.Context
import android.os.Bundle
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.CustomAttrsDao
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.SavableSearchableRepository
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TagFoldersTest {

    private class Item(override val label: String) : SavableSearchable {
        override val key = "app://$label"
        override val domain = "app"
        override val preferDetailsOverLaunch = false
        override fun overrideLabel(label: String) = this
        override fun launch(context: Context, options: Bundle?) = false
        override fun getPlaceholderIcon(context: Context): StaticLauncherIcon =
            throw UnsupportedOperationException()

        override fun getSerializer(): SearchableSerializer = throw UnsupportedOperationException()
    }

    private val camera = Item("Camera")
    private val chat = Item("Chat")
    private val mail = Item("Mail")
    private val maps = Item("Maps")
    private val apps = listOf(camera, chat, mail, maps)

    @Test
    fun appsInFoldersAreOnlyShownInTheFolder() {
        val grouping = apps.groupIntoFolders(
            mapOf("Social" to listOf(chat, mail), "Travel" to listOf(maps))
        )
        assertEquals(listOf(camera), grouping.ungrouped)
        assertEquals(setOf(Tag("Social"), Tag("Travel")), grouping.folders.toSet())
    }

    @Test
    fun emptyFoldersAreNotShown() {
        val grouping = apps.groupIntoFolders(mapOf("Empty" to emptyList()))
        assertSame(apps, grouping.ungrouped)
        assertEquals(emptyList<Tag>(), grouping.folders)
    }

    // A folder can also contain other items, e.g. contacts or apps of the work profile
    @Test
    fun foldersWithoutListedItemsAreShown() {
        val grouping = apps.groupIntoFolders(mapOf("Work" to listOf(Item("Work mail"))))
        assertEquals(apps, grouping.ungrouped)
        assertEquals(listOf(Tag("Work")), grouping.folders)
    }

    @Test
    fun foldersAreSortedInWithTheApps() {
        val merged = mergeFolders(listOf(camera, maps), listOf(Tag("Games"), Tag("Banking")))
        assertEquals(listOf("Banking", "Camera", "Games", "Maps"), merged.map { it.label })
    }

    @Test
    fun withoutFoldersTheListIsUnchanged() {
        val list = listOf(maps, camera)
        assertSame(list, mergeFolders(list, emptyList()))
    }

    private val dao = mockk<CustomAttrsDao>(relaxed = true)
    private val database = mockk<AppDatabase> {
        every { customAttrsDao() } returns dao
    }
    private val customAttributes = mockk<CustomAttributesRepository>()
    private val searchables = mockk<SavableSearchableRepository>(relaxed = true) {
        // No hidden items
        every { getKeys(any(), any(), any(), any(), any(), any(), any()) } returns flowOf(emptyList())
    }

    @Test
    fun readsWhichTagsAreFolders() {
        every { customAttributes.getAllTags(any()) } returns flowOf(listOf("Games", "Social"))
        every { dao.getCustomAttributes(listOf("tag://Games", "tag://Social"), "folder") } returns
                flowOf(listOf(CustomAttributeEntity(key = "tag://Games", type = "folder", value = "true")))

        val repository = TagFoldersRepositoryImpl(database, customAttributes, searchables)
        assertEquals(setOf("Games"), runBlocking { repository.folderTags.first() })
    }

    @Test
    fun noTagsMeansNoFolders() {
        every { customAttributes.getAllTags(any()) } returns flowOf(emptyList())

        val repository = TagFoldersRepositoryImpl(database, customAttributes, searchables)
        assertEquals(emptySet<String>(), runBlocking { repository.folderTags.first() })
        assertEquals(emptyMap<String, List<SavableSearchable>>(), runBlocking { repository.folders.first() })
    }

    // Renaming a folder in the tag editor moves the flag to the new name
    @Test
    fun renamedFolderMovesTheFlag() {
        every { customAttributes.getAllTags(any()) } returns flowOf(emptyList())
        val repository = TagFoldersRepositoryImpl(database, customAttributes, searchables)

        repository.setFolder("New name", true, oldTag = "Old name")

        verify(timeout = 5000) {
            dao.setCustomAttribute(CustomAttributeEntity(key = "tag://New name", type = "folder", value = "true"))
        }
        verifyOrder {
            dao.clearCustomAttribute("tag://Old name", "folder")
            dao.clearCustomAttribute("tag://New name", "folder")
            dao.setCustomAttribute(any())
        }
    }

    @Test
    fun ungroupingRemovesTheFlag() {
        every { customAttributes.getAllTags(any()) } returns flowOf(emptyList())
        val repository = TagFoldersRepositoryImpl(database, customAttributes, searchables)

        repository.setFolder("Games", false)

        verify(timeout = 5000) { dao.clearCustomAttribute("tag://Games", "folder") }
        verify(exactly = 0) { dao.setCustomAttribute(any()) }
    }

    // Like in the favorites
    @Test
    fun hiddenItemsAreLeftOutOfFolders() {
        every { searchables.getKeys(any(), any(), any(), any(), any(), any(), any()) } returns
                flowOf(listOf(chat.key))
        every { customAttributes.getAllTags(any()) } returns flowOf(listOf("Social"))
        every { dao.getCustomAttributes(listOf("tag://Social"), "folder") } returns
                flowOf(listOf(CustomAttributeEntity(key = "tag://Social", type = "folder", value = "true")))
        every { customAttributes.getItemsForTag("Social") } returns flowOf(listOf(chat, mail))

        val repository = TagFoldersRepositoryImpl(database, customAttributes, searchables)
        assertEquals(listOf(mail), runBlocking { repository.getItems("Social").first() })
        assertEquals(mapOf("Social" to listOf(mail)), runBlocking { repository.folders.first() })
    }

    // A tag that was removed from its last item doesn't exist anymore, so a new tag with the same
    // name must not be a folder
    @Test
    fun foldersOfUnusedTagsAreRemovedWhenTagsChange() {
        val repository = CustomAttributesRepositoryImpl(database, searchables)
        repository.setTags(camera, listOf("Photos"))

        coVerify(timeout = 5000) { dao.deleteUnusedFolders() }
        coVerifyOrder {
            dao.setTags(camera.key, any())
            dao.deleteUnusedFolders()
        }
    }
}
