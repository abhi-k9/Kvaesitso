package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class WidgetStackConfig(
    /** Height of the shared widget frame, in dp. */
    val height: Int = 320,
)

/** A shared frame whose ordered children have this widget's ID as their parent ID. */
data class WidgetStack(
    override val id: UUID,
    val config: WidgetStackConfig = WidgetStackConfig(),
) : Widget() {
    override fun toDatabaseEntity() = PartialWidgetEntity(
        id = id,
        type = Type,
        config = Json.encodeToString(config),
    )

    override fun getLabel(context: Context): String = context.getString(R.string.stack_label)

    companion object {
        const val Type = "stack"
    }
}

/** Enough information to restore a removed widget and its children without reallocating host IDs. */
data class WidgetPlacement(
    val widget: Widget,
    val parentId: UUID?,
    val position: Int,
    /** Page IDs do not have widget rows; a stack parent must still exist when undoing. */
    val parentIsStack: Boolean = false,
)
