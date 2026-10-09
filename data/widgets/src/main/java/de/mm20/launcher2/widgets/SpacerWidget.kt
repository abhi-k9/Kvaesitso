package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class SpacerWidgetConfig(
    /**
     * Height in dp
     */
    val height: Int = 64,
)

/**
 * Empty space between widgets
 */
data class SpacerWidget(
    override val id: UUID,
    val config: SpacerWidgetConfig = SpacerWidgetConfig(),
) : Widget() {
    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    override fun getLabel(context: Context): String {
        return context.getString(R.string.widget_name_spacer)
    }

    companion object {
        const val Type = "spacer"
    }
}
