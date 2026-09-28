package de.mm20.launcher2.ui.ktx

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Like [CustomTabsIntent.launchUrl], but doesn't throw if there is no app (i.e. no browser) that
 * can open the URL.
 * @return true if the URL has been opened
 */
fun CustomTabsIntent.tryLaunchUrl(context: Context, url: Uri): Boolean {
    return try {
        launchUrl(context, url)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
