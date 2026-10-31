package de.mm20.launcher2.services.favorites

import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.Tag
import de.mm20.launcher2.searchable.SavableSearchableRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesCardItemsTest {
    private fun item(id: String) = mockk<SavableSearchable> { every { key } returns id }

    @Test
    fun eachGroupHasItsOwnQuotaAndEveryKeyAppearsOnlyOnce() {
        val p = item("p"); val a = item("a"); val b = item("b"); val c = item("c"); val d = item("d")
        val groups = selectFavoritesCardItems(listOf(p), listOf(p, a, b), listOf(p, a, b, c, d), 1, 2, 2)
        assertEquals(listOf(p), groups.pinned)
        assertEquals(listOf(a, b), groups.recent)
        assertEquals(listOf(c, d), groups.frequent)
        assertEquals(groups.all.size, groups.all.map { it.key }.distinct().size)
    }

    @Test
    fun allPinnedKeepsTheirOrderAndZeroHidesAnIndividualGroup() {
        val pins = listOf(item("p1"), item("p2"))
        val groups = selectFavoritesCardItems(pins, listOf(item("r")), listOf(item("f")), -1, 0, 1)
        assertEquals(pins, groups.pinned)
        assertTrue(groups.recent.isEmpty())
        assertEquals(listOf("p1", "p2", "f"), groups.all.map { it.key })
    }

    @Test
    fun duplicatesDoNotConsumeSlotsAndPinnedLimitsDoNotUnpinAnything() {
        val a = item("a"); val sameA = item("a"); val b = item("b")
        val groups = selectFavoritesCardItems(listOf(a, sameA, b), emptyList(), emptyList(), 2, 0, 0)
        assertEquals(listOf(a, b), groups.pinned)
        assertEquals(1, selectFavoritesCardItems(listOf(a, b), emptyList(), emptyList(), 1, 0, 0).all.size)
    }

    @Test
    fun serviceFetchesEnoughFrequentCandidatesToFillAfterDeduplication() = runBlocking {
        val repository = mockk<SavableSearchableRepository>()
        val a = item("a"); val b = item("b"); val c = item("c")
        every { repository.getAppSuggestions(true, 1) } returns MutableStateFlow(listOf(a))
        every { repository.getAppSuggestions(false, 3) } returns MutableStateFlow(listOf(a, b, c))
        val service = FavoritesService(repository, mockk())

        val groups = service.getCardFavorites(0, 1, 2, emptyList(), emptySet()).first()

        assertEquals(listOf(a), groups.recent)
        assertEquals(listOf(b, c), groups.frequent)
        verify(exactly = 0) { repository.get(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun existingFlowReflectsNewLaunchOrder() = runBlocking {
        val repository = mockk<SavableSearchableRepository>()
        val a = item("a"); val b = item("b")
        val recent = MutableStateFlow(listOf(a, b))
        every { repository.getAppSuggestions(true, 2) } returns recent
        every { repository.getAppSuggestions(false, 0) } returns MutableStateFlow(emptyList())
        val flow = FavoritesService(repository, mockk()).getCardFavorites(0, 2, 0, emptyList(), emptySet())
        assertEquals(listOf(a, b), flow.first().recent)
        recent.value = listOf(b, a)
        assertEquals(listOf(b, a), flow.first().recent)
    }

    @Test
    fun tagChipsDoNotConsumeThePinnedLimitButPinnedFoldersDo() = runBlocking {
        val repository = mockk<SavableSearchableRepository>()
        val folder = Tag("Folder")
        every { repository.get(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MutableStateFlow(listOf(Tag("Chip"), folder, item("app")))
        every { repository.getAppSuggestions(any(), 0) } returns MutableStateFlow(emptyList())

        val groups = FavoritesService(repository, mockk()).getCardFavorites(1, 0, 0, emptyList(), setOf("Folder")).first()

        assertEquals(listOf(folder), groups.pinned)
    }
}
