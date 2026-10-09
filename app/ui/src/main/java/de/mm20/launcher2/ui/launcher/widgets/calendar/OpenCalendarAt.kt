package de.mm20.launcher2.ui.launcher.widgets.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.CalendarContract
import de.mm20.launcher2.ktx.tryStartActivity

/**
 * Opens the calendar app at [timeMillis].
 */
fun openCalendarAt(context: Context, timeMillis: Long) {
    val uri = ContentUris.appendId(
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time"),
        timeMillis
    ).build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Apps that open any file, such as file managers, can open this link too. Only calendar
    // apps should, i.e. apps that ask for calendar links, or that are calendar apps.
    val packageManager = context.packageManager
    val calendarApps = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), 0
    ).mapTo(mutableSetOf()) { it.activityInfo.packageName }
    val handlers = packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
    val calendarHandlers = handlers.filter {
        it.activityInfo.packageName in calendarApps ||
                (it.filter?.matchDataAuthority(uri) ?: IntentFilter.NO_MATCH_DATA) >= 0
    }

    if (calendarHandlers.isEmpty()) {
        // No calendar app can open the date, open the calendar app at least
        context.tryStartActivity(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val calendarPackages = calendarHandlers.mapTo(mutableSetOf()) { it.activityInfo.packageName }
    if (calendarHandlers.size < handlers.size && calendarPackages.size == 1) {
        // Otherwise other apps would be offered too
        intent.setPackage(calendarPackages.first())
    }
    context.tryStartActivity(intent)
}
