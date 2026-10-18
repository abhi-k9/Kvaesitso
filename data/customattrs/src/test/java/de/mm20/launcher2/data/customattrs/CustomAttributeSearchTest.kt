package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.CustomAttrsDao
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.StringNormalizer
import de.mm20.launcher2.searchable.SavableSearchableRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomAttributeSearchTest {

    private val dao = mockk<CustomAttrsDao> {
        every { getLabelsAndTags() } returns flowOf(
            listOf(
                CustomAttributeEntity("app://bank", "label", "My Bank"),
                CustomAttributeEntity("app://maps", "tag", "Navigation"),
                CustomAttributeEntity("app://chess", "tag", "Games"),
                CustomAttributeEntity("app://cafe", "label", "Café"),
            )
        )
    }
    private val database = mockk<AppDatabase> {
        every { customAttrsDao() } returns dao
    }

    // Returns an item for every key it's asked for, so that the result shows which keys matched
    private val searchables = mockk<SavableSearchableRepository> {
        every { getByKeys(any()) } answers {
            flowOf(firstArg<List<String>>().map { key ->
                mockk<SavableSearchable> { every { this@mockk.key } returns key }
            })
        }
    }

    private val normalizer = object : StringNormalizer {
        override val id = "test"
        override fun normalize(input: String) = input.lowercase().replace('é', 'e')
    }

    private val repository = CustomAttributesRepositoryImpl(database, searchables, normalizer)

    private fun search(query: String): List<String> = runBlocking {
        repository.search(query).first().map { it.key }
    }

    @Test
    fun customLabelsMatchLikeNames() {
        assertEquals(listOf("app://bank"), search("bank"))
        assertEquals(listOf("app://bank"), search("mbnk"))
        assertEquals(listOf("app://bank"), search("my bnak"))
        assertEquals(listOf("app://cafe"), search("cafe"))
        assertEquals(listOf("app://cafe"), search("CAFÉ"))
    }

    @Test
    fun tagsOnlyMatchStrongly() {
        assertEquals(listOf("app://maps"), search("navi"))
        assertEquals(listOf("app://chess"), search("game"))
        // In the middle of the tag
        assertEquals(emptyList<String>(), search("ga").filter { it == "app://maps" })
        // A typo
        assertEquals(emptyList<String>(), search("gmaes"))
    }

    @Test
    fun blankQueriesFindNothing() {
        assertEquals(emptyList<String>(), search(" "))
    }
}
