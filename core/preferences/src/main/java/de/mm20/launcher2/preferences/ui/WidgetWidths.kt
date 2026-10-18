package de.mm20.launcher2.preferences.ui

/**
 * How much of the width a widget takes: the whole width, or a half, a third or a quarter of it.
 * Narrower widgets that follow each other share a row.
 */
enum class WidgetWidth(val fraction: Int) {
    Full(1),
    Half(2),
    Third(3),
    Quarter(4);

    /**
     * Twelfths of the row that the widget takes
     */
    val twelfths: Int get() = 12 / fraction

    companion object {
        fun ofFraction(fraction: Int?): WidgetWidth = entries.find { it.fraction == fraction } ?: Full
    }
}

/**
 * Splits widgets with the given [widths] into rows: widgets that follow each other share a row
 * as long as they fit next to each other.
 *
 * @return the indices of the widgets in each row
 */
fun widgetRows(widths: List<WidgetWidth>): List<List<Int>> {
    val rows = mutableListOf<List<Int>>()
    var row = mutableListOf<Int>()
    var used = 0
    for ((i, width) in widths.withIndex()) {
        if (row.isNotEmpty() && (width == WidgetWidth.Full || used + width.twelfths > 12)) {
            rows += row
            row = mutableListOf()
            used = 0
        }
        row += i
        used += width.twelfths
        if (width == WidgetWidth.Full) {
            rows += row
            row = mutableListOf()
            used = 0
        }
    }
    if (row.isNotEmpty()) rows += row
    return rows
}
