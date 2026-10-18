package de.mm20.launcher2.activity

import android.app.Activity
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.appshortcuts.AppShortcut
import de.mm20.launcher2.data.customattrs.TagFoldersRepository
import de.mm20.launcher2.preferences.search.ShortcutSearchSettings
import de.mm20.launcher2.services.favorites.FavoritesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AddItemActivity : Activity() {

    private val favoritesService: FavoritesService by inject()
    private val shortcutSettings: ShortcutSearchSettings by inject()
    private val tagFoldersRepository: TagFoldersRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity is exported, so any app can start it. Only accept pin requests that are
        // backed by the system (LauncherApps.PinItemRequest), never raw legacy shortcut intents.
        val shortcut = AppShortcut(this, intent, allowLegacy = false)
        if (shortcut != null) {
            // The shortcut has been accepted, it's saved in the background, like before
            CoroutineScope(Dispatchers.Default).launch {
                if (shortcutSettings.pinToAppList.first()) {
                    tagFoldersRepository.setInAppList(shortcut, true)
                } else {
                    favoritesService.pinItem(shortcut)
                }
            }
        } else {
            Log.w("MM20", "Shortcut could not be added")
        }
        finish()
    }
}
