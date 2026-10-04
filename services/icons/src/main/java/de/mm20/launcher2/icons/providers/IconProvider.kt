package de.mm20.launcher2.icons.providers

import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.CancellationException

interface IconProvider {
    suspend fun getIcon(searchable: SavableSearchable, size: Int): LauncherIcon?
}

internal suspend fun Iterable<IconProvider>.getFirstIcon(
    searchable: SavableSearchable,
    size: Int
): LauncherIcon? {
    for (provider in this) {
        val icon = try {
            provider.getIcon(searchable, size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An icon that fails to load (e.g. because of a bug in a device's
            // LauncherActivityInfo.getIcon()) mustn't crash the launcher, try the next provider
            CrashReporter.logException(e)
            null
        }
        if (icon != null) {
            return icon
        }
    }
    return null
}