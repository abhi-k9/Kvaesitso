package de.mm20.launcher2.widgets

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.WidgetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetStackRepositoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: WidgetRepositoryImpl
    private val page = UUID.randomUUID()
    private val otherPage = UUID.randomUUID()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        ).setDriver(AndroidSQLiteDriver()).build()
        repository = WidgetRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun app(hostId: Int, height: Int = 180) = AppWidget(
        UUID.randomUUID(), AppWidgetConfig(widgetId = hostId, height = height, width = 150),
    )

    private suspend fun seed(parent: UUID?, vararg widgets: Widget) {
        database.widgetDao().insert(widgets.mapIndexed { position, widget ->
            widget.toDatabaseEntity(position, parent)
        })
    }

    private suspend fun children(parent: UUID?): List<WidgetEntity> =
        database.widgetDao().getChildren(parent)

    @Test
    fun groupingKeepsOrderConfigurationsAndHostIdsAndLeavesOtherPagesAlone() = runBlocking {
        val before = NotesWidget(UUID.randomUUID())
        val first = app(31)
        val second = app(32, height = 410)
        val after = WeatherWidget(UUID.randomUUID())
        val other = app(90)
        seed(page, before, first, second, after)
        seed(otherPage, other)

        val stack = repository.createStack(page, listOf(first.id, second.id))!!

        assertEquals(listOf(before.id, stack.id, after.id), children(page).map { it.id })
        assertEquals(listOf(0, 1, 2), children(page).map { it.position })
        assertEquals(listOf(first, second), repository.get(stack.id).first())
        assertEquals(410, stack.config.height)
        assertEquals(listOf(other.id), children(otherPage).map { it.id })
        assertEquals(3, repository.count(AppWidget.Type).first())
    }

    @Test
    fun existingStackCanAppendAnotherWidgetAfterAChildWasRemoved() = runBlocking {
        val first = app(31)
        val second = app(32)
        val third = app(33)
        val fourth = app(34)
        seed(page, first, second, third, fourth)
        val stack = repository.createStack(page, listOf(first.id, second.id, third.id))!!
        repository.removeWithChildren(second.id)

        assertTrue(repository.addToStack(stack.id, fourth.id))

        assertEquals(listOf(first, third, fourth), repository.get(stack.id).first())
        assertEquals(listOf(0, 1, 2), children(stack.id).map { it.position })
        assertEquals(listOf(stack.id), children(page).map { it.id })
    }

    @Test
    fun unstackRestoresChildrenAtTheStackPositionInTheirEditedOrder() = runBlocking {
        val before = app(30)
        val first = app(31)
        val second = app(32)
        val after = app(33)
        seed(page, before, first, second, after)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        repository.set(listOf(second, first), stack.id)
        withTimeout(5_000) {
            repository.get(stack.id).first { it.map { it.id } == listOf(second.id, first.id) }
        }

        assertTrue(repository.unstack(stack.id))

        assertEquals(listOf(before.id, second.id, first.id, after.id), children(page).map { it.id })
        assertEquals(listOf(0, 1, 2, 3), children(page).map { it.position })
        assertTrue(children(stack.id).isEmpty())
        assertNull(database.widgetDao().get(stack.id))
    }

    @Test
    fun groupingRejectsDuplicateMissingCrossPageAndNestedWidgetsWithoutChangingRows() = runBlocking {
        val first = app(31)
        val second = app(32)
        val other = app(99)
        seed(page, first, second)
        seed(otherPage, other)
        val original = database.widgetDao().getAll()
        assertNull(repository.createStack(page, listOf(first.id, first.id)))
        assertNull(repository.createStack(page, listOf(first.id, UUID.randomUUID())))
        assertNull(repository.createStack(page, listOf(first.id, other.id)))
        assertEquals(original, database.widgetDao().getAll())

        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        assertNull(repository.createStack(stack.id, listOf(first.id, second.id)))
        assertNull(repository.createStack(page, listOf(stack.id, other.id)))
        assertFalse(repository.addToStack(stack.id, other.id))
        assertFalse(repository.addToStack(stack.id, stack.id))
        assertEquals(listOf(first, second), repository.get(stack.id).first())
    }

    @Test
    fun removalAndUndoPreserveTheEntireStackAndItsHostIds() = runBlocking {
        val first = app(31)
        val second = app(32)
        val outside = app(33)
        seed(page, first, second, outside)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        val original = database.widgetDao().getAll().associateBy { it.id }

        val removed = repository.removeWithChildren(stack.id)

        assertEquals(listOf(stack, first, second), removed.map { it.widget })
        assertEquals(listOf(outside.id), database.widgetDao().getAll().map { it.id })
        assertEquals(listOf(31, 32), removed.mapNotNull { (it.widget as? AppWidget)?.config?.widgetId })

        repository.restore(removed)
        assertEquals(original, database.widgetDao().getAll().associateBy { it.id })
        assertEquals(listOf(first, second), repository.get(stack.id).first())
    }

    @Test
    fun undoAfterAnInterveningReorderAndAdditionPreservesUniquePositions() = runBlocking {
        val before = app(30)
        val first = app(31)
        val second = app(32)
        val after = app(33)
        val added = app(34)
        seed(page, before, first, second, after)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        val removed = repository.removeWithChildren(stack.id)
        repository.set(listOf(after, added, before), page)
        withTimeout(5_000) {
            repository.get(page).first { it.map { it.id } == listOf(after.id, added.id, before.id) }
        }

        assertTrue(repository.restore(removed))

        assertEquals(listOf(after.id, stack.id, added.id, before.id), children(page).map { it.id })
        assertEquals(listOf(0, 1, 2, 3), children(page).map { it.position })
        assertEquals(listOf(first, second), repository.get(stack.id).first())
        assertTrue(repository.restore(removed)) // Repeated Undo cannot duplicate rows/host IDs.
        assertEquals(6, database.widgetDao().getAll().size)
    }

    @Test
    fun undoOfAChildAfterItsStackWasUnstackedDoesNotCreateAnOrphan() = runBlocking {
        val first = app(31)
        val second = app(32)
        seed(page, first, second)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        val removed = repository.removeWithChildren(second.id)
        assertTrue(removed.single().parentIsStack)
        assertTrue(repository.unstack(stack.id))

        assertFalse(repository.restore(removed))

        assertEquals(listOf(first), repository.get(page).first())
        assertTrue(children(stack.id).isEmpty())
    }

    @Test
    fun stackInsertionAndMovementUseCurrentRowsAndRejectMissingParents() = runBlocking {
        val first = app(31)
        val second = app(32)
        val added = app(33)
        val next = app(34)
        seed(page, first, second)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!

        assertTrue(repository.insertIntoStack(stack.id, added))
        assertTrue(repository.insertIntoStack(stack.id, next))
        assertTrue(repository.moveInStack(stack.id, next.id, -1))
        assertTrue(repository.moveInStack(stack.id, next.id, -1))
        assertTrue(repository.insertIntoStack(stack.id, added)) // Duplicate delivery is a safe no-op.
        assertFalse(repository.insertIntoStack(stack.id, WidgetStack(UUID.randomUUID())))
        assertFalse(repository.moveInStack(stack.id, first.id, -1))
        assertEquals(listOf(first, next, second, added), repository.get(stack.id).first())
        assertEquals(listOf(0, 1, 2, 3), children(stack.id).map { it.position })

        repository.unstack(stack.id)
        assertTrue(repository.insertIntoStack(stack.id, added)) // Its host ID is now used on the page.
        assertFalse(repository.insertIntoStack(stack.id, app(35)))
        assertFalse(repository.moveInStack(stack.id, next.id, 1))
        assertTrue(children(stack.id).isEmpty())
    }

    @Test
    fun replacingAListRetainsKeptStackChildrenAndRemovesDiscardedStackChildren() = runBlocking {
        val first = app(31)
        val second = app(32)
        val outside = app(33)
        seed(page, first, second, outside)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        repository.set(listOf(outside, stack), page)
        withTimeout(5_000) {
            repository.get(page).first { it.map { it.id } == listOf(outside.id, stack.id) }
        }
        assertEquals(listOf(first, second), repository.get(stack.id).first())

        repository.set(listOf(outside), page)
        withTimeout(5_000) { repository.get(page).first { it == listOf(outside) } }

        assertTrue(children(stack.id).isEmpty())
        assertEquals(listOf(outside.id), database.widgetDao().getAll().map { it.id })
    }

    @Test
    fun missingRemovalAndEmptyUndoAreNoOps() = runBlocking {
        val first = app(31)
        seed(page, first)

        assertTrue(repository.removeWithChildren(UUID.randomUUID()).isEmpty())
        repository.restore(emptyList())
        assertEquals(listOf(first), repository.get(page).first())
        assertFalse(repository.unstack(first.id))
    }

    @Test
    fun backupRestoreRoundTripRetainsStacksChildrenAndLegacyRootWidgets() = runBlocking {
        val first = app(31)
        val second = app(32)
        val legacy = app(40)
        seed(page, first, second)
        seed(null, legacy)
        val stack = repository.createStack(page, listOf(first.id, second.id))!!
        val original = database.widgetDao().getAll().associateBy { it.id }
        val directory = temporary.newFolder()

        repository.backup(directory)
        database.backupDao().wipeWidgets()
        repository.restore(directory)

        assertEquals(original, database.widgetDao().getAll().associateBy { it.id })
        assertEquals(listOf(first, second), repository.get(stack.id).first())
        assertEquals(listOf(legacy), repository.get().first())
    }

    @Test
    fun repeatedBackupDropsObsoletePagesAndMalformedRestoreKeepsCurrentWidgets() = runBlocking {
        val widgets = List(101) { app(100 + it) }
        seed(page, *widgets.toTypedArray())
        val directory = temporary.newFolder()
        repository.backup(directory)
        assertTrue(directory.resolve("widgets2.0001").exists())
        database.widgetDao().delete(widgets.drop(1).map { it.id })

        repository.backup(directory)

        assertFalse(directory.resolve("widgets2.0001").exists())
        repository.restore(directory)
        assertEquals(listOf(widgets.first()), repository.get(page).first())
        directory.resolve("widgets2.0000").writeText("not json")
        val failure = runCatching { repository.restore(directory) }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(listOf(widgets.first()), repository.get(page).first())
        repository.restore(temporary.newFolder()) // Missing widget data is not an empty snapshot.
        assertEquals(listOf(widgets.first()), repository.get(page).first())
    }

    @Test
    fun stackConfigurationRoundTripsAndMalformedConfigurationHasASafeDefault() {
        val stack = WidgetStack(UUID.randomUUID(), WidgetStackConfig(height = 480))
        assertEquals(stack, Widget.fromDatabaseEntity(stack.toDatabaseEntity(0, page)))
        val malformed = stack.toDatabaseEntity(0, page).copy(config = "not json")
        assertEquals(WidgetStack(stack.id), Widget.fromDatabaseEntity(malformed))
        assertNotNull(Widget.fromDatabaseEntity(malformed.copy(config = null)))
    }
}
