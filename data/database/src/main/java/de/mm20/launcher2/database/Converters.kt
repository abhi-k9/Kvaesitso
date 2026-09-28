package de.mm20.launcher2.database

import android.content.ComponentName
import androidx.room3.ColumnTypeConverter

class ComponentNameConverter {
    @ColumnTypeConverter
    fun toString(componentName: ComponentName?): String? {
        return componentName?.flattenToString()
    }

    @ColumnTypeConverter
    fun toComponentName(string: String?) : ComponentName? {
        string ?: return null
        return ComponentName.unflattenFromString(string)
    }

}
