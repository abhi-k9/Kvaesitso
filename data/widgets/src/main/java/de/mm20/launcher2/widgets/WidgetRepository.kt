package de.mm20.launcher2.widgets

import androidx.room3.withWriteTransaction
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.WidgetEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.File
import java.util.UUID

interface WidgetRepository: Backupable {
    fun get(parent: UUID? = null, limit: Int = 100, offset: Int = 0): Flow<List<Widget>>
    fun update(widget: Widget)
    fun create(widget: Widget, position: Int, parentId: UUID? = null)
    fun delete(widget: Widget)
    fun set(widgets: List<Widget>, parentId: UUID? = null)

    suspend fun createStack(parentId: UUID?, widgetIds: List<UUID>): WidgetStack?
    suspend fun addToStack(stackId: UUID, widgetId: UUID): Boolean
    suspend fun insertIntoStack(stackId: UUID, widget: Widget, index: Int? = null): Boolean
    suspend fun moveInStack(stackId: UUID, widgetId: UUID, offset: Int): Boolean
    suspend fun unstack(stackId: UUID): Boolean
    suspend fun removeWithChildren(widgetId: UUID): List<WidgetPlacement>
    suspend fun restore(placements: List<WidgetPlacement>): Boolean

    fun exists(type: String): Flow<Boolean>
    fun count(type: String): Flow<Int>
}

internal class WidgetRepositoryImpl(
    private val database: AppDatabase,
) : WidgetRepository {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    override fun get(parent: UUID?, limit: Int, offset: Int): Flow<List<Widget>> {
        val dao = database.widgetDao()
        return if (parent == null) {
            dao.queryRoot(limit, offset)
        } else {
            dao.queryByParent(parent, limit, offset)
        }.map {
            it.mapNotNull { Widget.fromDatabaseEntity(it) }
        }
    }

    override fun update(widget: Widget) {
        val dao = database.widgetDao()
        scope.launch {
            dao.patch(widget.toDatabaseEntity())
        }
    }

    override fun create(widget: Widget, position: Int, parentId: UUID?) {
        val dao = database.widgetDao()
        scope.launch {
            val entity = widget.toDatabaseEntity(position = position, parentId = parentId)
            dao.insert(entity)
        }
    }

    override fun delete(widget: Widget) {
        scope.launch {
            removeWithChildren(widget.id)
        }
    }

    override suspend fun createStack(parentId: UUID?, widgetIds: List<UUID>): WidgetStack? =
        database.withWriteTransaction {
            val dao = database.widgetDao()
            val ids = widgetIds.toSet()
            if (ids.size < 2 || ids.size != widgetIds.size) return@withWriteTransaction null
            if (parentId != null && dao.get(parentId)?.type == WidgetStack.Type) {
                return@withWriteTransaction null
            }
            val siblings = dao.getChildren(parentId)
            val selected = siblings.filter { it.id in ids }
            if (selected.size != ids.size || selected.any { it.type == WidgetStack.Type }) {
                return@withWriteTransaction null
            }
            val initialHeight = selected.maxOf {
                when (val widget = Widget.fromDatabaseEntity(it)) {
                    is AppWidget -> widget.config.height
                    is SpacerWidget -> widget.config.height
                    else -> 320
                }
            }.coerceIn(120, 640)
            val stack = WidgetStack(UUID.randomUUID(), WidgetStackConfig(initialHeight))
            val firstIndex = siblings.indexOfFirst { it.id in ids }
            val remaining = siblings.filterNot { it.id in ids }.toMutableList()
            remaining.add(firstIndex, stack.toDatabaseEntity(firstIndex, parentId))
            dao.insert(stack.toDatabaseEntity(firstIndex, parentId))
            dao.update(remaining.mapIndexed { position, entity -> entity.copy(position = position) })
            dao.update(selected.mapIndexed { position, entity ->
                entity.copy(parentId = stack.id, position = position)
            })
            stack
        }

    override suspend fun addToStack(stackId: UUID, widgetId: UUID): Boolean =
        database.withWriteTransaction {
            val dao = database.widgetDao()
            val stack = dao.get(stackId) ?: return@withWriteTransaction false
            val widget = dao.get(widgetId) ?: return@withWriteTransaction false
            if (stack.type != WidgetStack.Type || widget.type == WidgetStack.Type ||
                widget.parentId != stack.parentId
            ) return@withWriteTransaction false
            val children = dao.getChildren(stackId)
            dao.update((children + widget.copy(parentId = stackId)).mapIndexed { position, entity ->
                entity.copy(position = position)
            })
            dao.update(dao.getChildren(stack.parentId).mapIndexed { position, entity ->
                entity.copy(position = position)
            })
            true
        }

    override suspend fun insertIntoStack(stackId: UUID, widget: Widget, index: Int?): Boolean =
        database.withWriteTransaction {
            val dao = database.widgetDao()
            if (widget is WidgetStack) return@withWriteTransaction false
            val existing = dao.get(widget.id)
            // A repeated provider result must not tell the caller to release an ID that is
            // already in use. Keep the existing placement, even if it has since been moved.
            if (existing != null) {
                return@withWriteTransaction existing.type == widget.toDatabaseEntity().type
            }
            if (dao.get(stackId)?.type != WidgetStack.Type) return@withWriteTransaction false
            val children = dao.getChildren(stackId).toMutableList()
            val insertion = (index ?: children.size).coerceIn(0, children.size)
            val entity = widget.toDatabaseEntity(insertion, stackId)
            children.add(insertion, entity)
            dao.insert(entity)
            dao.update(children.mapIndexed { position, child -> child.copy(position = position) })
            true
        }

    override suspend fun moveInStack(stackId: UUID, widgetId: UUID, offset: Int): Boolean =
        database.withWriteTransaction {
            val dao = database.widgetDao()
            if (dao.get(stackId)?.type != WidgetStack.Type) return@withWriteTransaction false
            val children = dao.getChildren(stackId).toMutableList()
            val index = children.indexOfFirst { it.id == widgetId }
            val destination = index.toLong() + offset
            if (index < 0 || destination !in 0L..children.lastIndex.toLong()) {
                return@withWriteTransaction false
            }
            children.add(destination.toInt(), children.removeAt(index))
            dao.update(children.mapIndexed { position, child -> child.copy(position = position) })
            true
        }

    override suspend fun unstack(stackId: UUID): Boolean = database.withWriteTransaction {
        val dao = database.widgetDao()
        val stack = dao.get(stackId) ?: return@withWriteTransaction false
        if (stack.type != WidgetStack.Type) return@withWriteTransaction false
        val children = dao.getChildren(stackId)
        val siblings = dao.getChildren(stack.parentId).toMutableList()
        val index = siblings.indexOfFirst { it.id == stackId }
        if (index < 0) return@withWriteTransaction false
        siblings.removeAt(index)
        siblings.addAll(index, children.map { it.copy(parentId = stack.parentId) })
        dao.update(siblings.mapIndexed { position, entity -> entity.copy(position = position) })
        dao.delete(stackId)
        true
    }

    override suspend fun removeWithChildren(widgetId: UUID): List<WidgetPlacement> =
        database.withWriteTransaction {
            val dao = database.widgetDao()
            val all = dao.getAll()
            val byId = all.associateBy { it.id }
            val root = byId[widgetId] ?: return@withWriteTransaction emptyList()
            val children = all.groupBy { it.parentId }
            val visited = mutableSetOf<UUID>()
            val removed = mutableListOf<WidgetEntity>()
            fun collect(entity: WidgetEntity) {
                if (!visited.add(entity.id)) return
                removed.add(entity)
                children[entity.id].orEmpty().forEach(::collect)
            }
            collect(root)
            val placements = removed.mapNotNull { entity ->
                Widget.fromDatabaseEntity(entity)?.let {
                    WidgetPlacement(
                        it, entity.parentId, entity.position,
                        parentIsStack = byId[entity.parentId]?.type == WidgetStack.Type,
                    )
                }
            }
            dao.delete(removed.map { it.id })
            placements
        }

    override suspend fun restore(placements: List<WidgetPlacement>): Boolean {
        if (placements.isEmpty()) return true
        return database.withWriteTransaction {
            val dao = database.widgetDao()
            val current = dao.getAll().associateBy { it.id }
            // An earlier Undo or a restored backup may already have brought an ID back. Keep
            // that newer row, and never allocate or overwrite Android widget host IDs here.
            val missing = placements.distinctBy { it.widget.id }.filter { it.widget.id !in current }
            val restoring = missing.associateBy { it.widget.id }
            if (missing.any { placement ->
                    placement.parentIsStack &&
                        current[placement.parentId]?.type != WidgetStack.Type &&
                        restoring[placement.parentId]?.widget !is WidgetStack
                }
            ) return@withWriteTransaction false

            val inserted = mutableListOf<WidgetEntity>()
            val reordered = mutableListOf<WidgetEntity>()
            for ((parentId, additions) in missing.groupBy { it.parentId }) {
                val siblings = dao.getChildren(parentId).toMutableList()
                for (placement in additions.sortedBy { it.position }) {
                    val insertion = placement.position.coerceIn(0, siblings.size)
                    val entity = placement.widget.toDatabaseEntity(insertion, parentId)
                    siblings.add(insertion, entity)
                    inserted.add(entity)
                }
                reordered += siblings.mapIndexed { position, child -> child.copy(position = position) }
            }
            dao.insert(inserted)
            dao.update(reordered)
            true
        }
    }

    override fun set(widgets: List<Widget>, parentId: UUID?) {
        val dao = database.widgetDao()
        scope.launch {
            database.withWriteTransaction {
                val incoming = widgets.associateBy { it.id }
                if (incoming.size != widgets.size || (parentId != null && parentId in incoming)) {
                    return@withWriteTransaction
                }
                val all = dao.getAll()
                val current = all.associateBy { it.id }
                if (current[parentId]?.type == WidgetStack.Type && widgets.any { it is WidgetStack }) {
                    return@withWriteTransaction
                }
                if (widgets.any { widget ->
                        current[widget.id]?.let { it.parentId != parentId } == true
                    }
                ) return@withWriteTransaction

                // Removing a stack through a list replacement must not leave its children
                // behind. Retained stack IDs keep their original child rows and host IDs.
                val children = all.groupBy { it.parentId }
                val removed = mutableSetOf<UUID>()
                fun collectRemoved(entity: WidgetEntity) {
                    if (entity.id in incoming || !removed.add(entity.id)) return
                    children[entity.id].orEmpty().forEach(::collectRemoved)
                }
                children[parentId].orEmpty().filter { it.id !in incoming }.forEach(::collectRemoved)
                dao.delete(removed.toList())
                val entities = widgets.mapIndexed { index, widget ->
                    widget.toDatabaseEntity(position = index, parentId = parentId)
                }
                dao.update(entities.filter { it.id in current })
                dao.insert(entities.filterNot { it.id in current })
            }
        }
    }

    override fun exists(type: String): Flow<Boolean> {
        val dao = database.widgetDao()
        return dao.exists(type = type)
    }

    override fun count(type: String): Flow<Int> {
        val dao = database.widgetDao()
        return dao.count(type = type)

    }


    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
        // Read one consistent snapshot: a stack and its children can otherwise move between
        // pages while a backup is being written.
        val pages = database.widgetDao().getAll().chunked(100).ifEmpty { listOf(emptyList()) }
        for ((page, widgets) in pages.withIndex()) {
            val jsonArray = JSONArray()
            for (widget in widgets) {
                jsonArray.put(
                    jsonObjectOf(
                        "config" to widget.config,
                        "position" to widget.position,
                        "type" to widget.type,
                        "id" to widget.id.toString(),
                        "parentId" to widget.parentId?.toString(),
                    )
                )
            }

            val file = File(toDir, "widgets2.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
        }
        // BackupManager normally supplies a fresh directory. Also make repeated direct
        // backups safe when the newer snapshot has fewer pages than the previous one.
        val retained = pages.indices.mapTo(mutableSetOf()) { "widgets2.${it.toString().padStart(4, '0')}" }
        toDir.listFiles { _, name -> name.startsWith("widgets2.") && name !in retained }
            ?.forEach { check(it.delete()) { "Could not remove an obsolete widget backup page" } }
        Unit
    }

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        val files =
            fromDir.listFiles { _, name -> name.startsWith("widgets2.") } ?: return@withContext
        if (files.isEmpty()) return@withContext
        // Validate all pages before replacing anything, then publish complete stacks atomically.
        val widgets = buildList {
            for (file in files.sortedBy { it.name }) {
                val jsonArray = JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    add(WidgetEntity(
                        type = json.getString("type"),
                        position = json.getInt("position"),
                        config = if (json.isNull("config")) null else json.getString("config"),
                        id = UUID.fromString(json.getString("id")),
                        parentId = json.optString("parentId")
                            .takeIf { it.isNotEmpty() && it != "null" }
                            ?.let(UUID::fromString),
                    ))
                }
            }
        }
        database.withWriteTransaction {
            dao.wipeWidgets()
            dao.importWidgets(widgets)
        }
    }
}
