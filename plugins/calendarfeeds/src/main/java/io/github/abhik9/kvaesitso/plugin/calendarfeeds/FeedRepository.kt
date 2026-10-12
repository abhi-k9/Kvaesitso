package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads the feeds and keeps a copy of each, so that the calendar doesn't wait for the network
 * and works offline. A copy that's older than [MaxAge] is updated in the background when it's used.
 */
internal object FeedRepository {
    private const val Tag = "CalendarFeeds"
    private const val MaxAge = 6 * 60 * 60 * 1000L
    private const val MaxSize = 20 * 1024 * 1024
    private const val UserAgent = "KvaesitsoCalendarFeeds/1.0 (+https://github.com/abhi-k9/Kvaesitso)"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Parsed copies by feed ID, with the time when the copy was saved
    private val parsed = ConcurrentHashMap<String, Pair<Long, IcsCalendar>>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val refreshing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * The calendar of [feed]. It's downloaded first if there's no copy of it yet.
     */
    suspend fun getCalendar(context: Context, feed: Feed): IcsCalendar? {
        val file = fileOf(context, feed)
        if (!file.exists()) {
            refresh(context, feed)
        } else if (System.currentTimeMillis() - file.lastModified() > MaxAge) {
            refreshInBackground(context, feed)
        }
        return withContext(Dispatchers.IO) { readCopy(file, feed) }
    }

    /**
     * Downloads [feed] now
     * @return null if it worked, the error otherwise
     */
    suspend fun refresh(context: Context, feed: Feed): String? {
        val lock = locks.getOrPut(feed.id) { Mutex() }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val text = download(feed.url)
                    if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                        return@withContext context.getString(R.string.error_not_a_calendar)
                    }
                    val calendar = IcsParser.parse(text)
                    val file = fileOf(context, feed)
                    file.parentFile?.mkdirs()
                    val temp = File(file.path + ".tmp")
                    temp.writeText(text)
                    if (!temp.renameTo(file)) {
                        temp.copyTo(file, overwrite = true)
                        temp.delete()
                    }
                    parsed[feed.id] = file.lastModified() to calendar
                    null
                } catch (e: IOException) {
                    Log.w(Tag, "Couldn't download ${feed.url}", e)
                    e.message ?: context.getString(R.string.error_network)
                } catch (e: IllegalArgumentException) {
                    // An invalid URL
                    Log.w(Tag, "Couldn't download ${feed.url}", e)
                    context.getString(R.string.error_invalid_url)
                } catch (e: RuntimeException) {
                    // A file that the parser can't handle must not crash the launcher's queries
                    Log.e(Tag, "Couldn't read ${feed.url}", e)
                    context.getString(R.string.error_not_a_calendar)
                }
            }
        }
    }

    fun refreshInBackground(context: Context, feed: Feed) {
        val appContext = context.applicationContext
        if (!refreshing.add(feed.id)) return
        scope.launch {
            try {
                refresh(appContext, feed)
            } finally {
                refreshing.remove(feed.id)
            }
        }
    }

    /**
     * When [feed] was last downloaded, null if it never was
     */
    fun lastUpdated(context: Context, feed: Feed): Long? {
        val file = fileOf(context, feed)
        return if (file.exists()) file.lastModified() else null
    }

    /**
     * Forgets the copy of a feed that was removed
     */
    fun delete(context: Context, feed: Feed) {
        parsed.remove(feed.id)
        fileOf(context, feed).delete()
    }

    private fun readCopy(file: File, feed: Feed): IcsCalendar? {
        if (!file.exists()) return null
        val modified = file.lastModified()
        parsed[feed.id]?.let { (time, calendar) ->
            if (time == modified) return calendar
        }
        val calendar = try {
            IcsParser.parse(file.readText())
        } catch (e: IOException) {
            Log.w(Tag, "Couldn't read the copy of ${feed.url}", e)
            return null
        } catch (e: RuntimeException) {
            Log.e(Tag, "Couldn't read the copy of ${feed.url}", e)
            return null
        }
        parsed[feed.id] = modified to calendar
        return calendar
    }

    private fun fileOf(context: Context, feed: Feed): File {
        return File(context.filesDir, "feeds/${feed.id}.ics")
    }

    /**
     * webcal:// links are calendar subscriptions over https
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("webcals://", ignoreCase = true) -> "https://" + trimmed.substring(10)
            trimmed.startsWith("webcal://", ignoreCase = true) -> "https://" + trimmed.substring(9)
            else -> trimmed
        }
    }

    fun isValidUrl(url: String): Boolean {
        val normalized = normalizeUrl(url)
        if (!normalized.startsWith("https://", ignoreCase = true) &&
            !normalized.startsWith("http://", ignoreCase = true)
        ) return false
        return try {
            URL(normalized).host.isNotEmpty()
        } catch (e: IOException) {
            false
        }
    }

    private fun download(url: String): String {
        var location = URL(normalizeUrl(url))
        // Redirects are followed by hand: HttpURLConnection doesn't follow them from http to https
        repeat(5) {
            val connection = location.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", UserAgent)
                connection.setRequestProperty("Accept", "text/calendar, */*")
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return readLimited(connection)
                    HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER, 307, 308 -> {
                        val next = connection.getHeaderField("Location")
                            ?: throw IOException("HTTP $code without a location")
                        location = URL(location, next)
                    }

                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun readLimited(connection: HttpURLConnection): String {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > MaxSize) throw IOException("The calendar is too large")
            }
        }
        // iCalendar files are UTF-8
        return output.toString("UTF-8")
    }
}
