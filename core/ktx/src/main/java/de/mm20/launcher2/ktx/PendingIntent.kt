package de.mm20.launcher2.ktx

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle

fun PendingIntent.sendWithBackgroundPermission(context: Context) {
    val options = backgroundActivityStartOptions()
    if (options != null) {
        send(context, 0, null, null, null, null, options)
    } else {
        send(context, 0, null)
    }
}

/**
 * Options that let a pending intent or intent sender of another app (e.g. a widget's
 * configuration) start its activity with this app's permission to start activities. Only for
 * starts right after the user interacted with the launcher: from Android 16 on, the permission
 * only applies while the launcher is visible, which Android 17 requires.
 */
fun backgroundActivityStartOptions(): Bundle? {
    if (!isAtLeastApiLevel(34)) return null
    val mode = if (isAtLeastApiLevel(36)) {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
    } else {
        @Suppress("DEPRECATION")
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    }
    return ActivityOptions.makeBasic()
        .setPendingIntentBackgroundActivityStartMode(mode)
        .toBundle()
}
