package de.mm20.launcher2.ui.launcher.widgets

import de.mm20.launcher2.widgets.SpacerWidget
import de.mm20.launcher2.widgets.WidgetPlacement
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRemovalTest {
    private val removed = listOf(WidgetPlacement(SpacerWidget(UUID.randomUUID()), null, 0))

    @Test
    fun undoRestoresBeforeProviderResourcesCanBeDiscarded() = runBlocking {
        var restoreCalled = false
        var discarded = false

        finishWidgetRemoval(removed, requestUndo = { true }, restore = {
            assertEquals(removed, it)
            assertFalse(discarded)
            restoreCalled = true
            true
        }, discard = { discarded = true })

        assertTrue(restoreCalled)
        assertFalse(discarded)
    }

    @Test
    fun dismissedUndoDiscardsTheRemovedProviderResources() = runBlocking {
        var discarded = emptyList<WidgetPlacement>()

        finishWidgetRemoval(removed, requestUndo = { false }, restore = {
            throw AssertionError("A dismissed removal must not be restored")
        }, discard = { discarded = it })

        assertEquals(removed, discarded)
    }

    @Test
    fun failedRestoreDiscardsResourcesWhenTheStackNoLongerExists() = runBlocking {
        var discarded = false

        finishWidgetRemoval(removed, requestUndo = { true }, restore = { false }, discard = { discarded = true })

        assertTrue(discarded)
    }

    @Test
    fun cancellingTheSnackbarStillDiscardsProviderResources() = runBlocking {
        var discarded = false
        try {
            finishWidgetRemoval(removed, requestUndo = { throw CancellationException("Launcher closed") },
                restore = { true }, discard = { discarded = true })
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(discarded)
        }
    }
}
