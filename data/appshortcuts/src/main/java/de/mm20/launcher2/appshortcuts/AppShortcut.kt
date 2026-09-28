package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.search.AppShortcut


/**
 * @param allowLegacy whether to fall back to legacy shortcut extras (EXTRA_SHORTCUT_INTENT).
 * Only pass true if [pinRequestIntent] comes from a trusted source (e.g. the result of a shortcut
 * config activity that the user picked), because the embedded intent is launched as-is.
 */
fun AppShortcut(
    context: Context,
    pinRequestIntent: Intent,
    allowLegacy: Boolean = true,
): AppShortcut? {
    return LauncherShortcut.fromPinRequestIntent(context, pinRequestIntent)
        ?: if (allowLegacy) LegacyShortcut.fromPinRequestIntent(context, pinRequestIntent) else null
}