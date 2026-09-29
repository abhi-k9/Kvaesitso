package de.mm20.launcher2.search

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticLauncherIcon
import java.text.Collator
import java.util.Locale

interface SavableSearchable : Searchable, Comparable<SavableSearchable>  {
    val key: String

    val label: String
    val labelOverride: String?
        get() = null

    fun overrideLabel(label: String): SavableSearchable

    fun launch(context: Context, options: Bundle?): Boolean

    /**
     * If this is true, tapping the item will open the details popup instead of launching it
     */
    val preferDetailsOverLaunch: Boolean

    fun getPlaceholderIcon(context: Context): StaticLauncherIcon

    suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean
    ): LauncherIcon? = null

    suspend fun getProviderIcon(context: Context): Drawable? = null

    override fun compareTo(other: SavableSearchable): Int {
        val label1 = labelOverride ?: label
        val label2 = other.labelOverride ?: other.label
        return labelCollator().compare(label1, label2)
    }

    val domain: String
    fun getSerializer(): SearchableSerializer

    interface Companion {
        val Domain: String
    }

}

private val labelCollators = ThreadLocal<Pair<Locale, Collator>>()

/**
 * Collator.getInstance() returns a new copy each time, which is expensive when sorting long lists
 * (i.e. all apps), so reuse one per thread (collators aren't thread safe).
 */
private fun labelCollator(): Collator {
    val locale = Locale.getDefault()
    labelCollators.get()?.let { (cachedLocale, collator) ->
        if (cachedLocale == locale) return collator
    }
    return Collator.getInstance(locale).apply { strength = Collator.SECONDARY }.also {
        labelCollators.set(locale to it)
    }
}
