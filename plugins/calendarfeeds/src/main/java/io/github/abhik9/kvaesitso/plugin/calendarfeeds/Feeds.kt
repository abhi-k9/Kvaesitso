package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A calendar subscription
 */
internal data class Feed(
    val id: String,
    val name: String,
    val url: String,
    /**
     * 0xAARRGGBB
     */
    val color: Int,
)

/**
 * The feeds that the user added
 */
internal class FeedStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("feeds", Context.MODE_PRIVATE)

    fun getFeeds(): List<Feed> {
        val json = preferences.getString(Key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val feed = array.optJSONObject(i) ?: return@mapNotNull null
                Feed(
                    id = feed.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                    name = feed.optString("name"),
                    url = feed.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                    color = feed.optInt("color", Colors[0]),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun setFeeds(feeds: List<Feed>) {
        val array = JSONArray()
        for (feed in feeds) {
            array.put(
                JSONObject()
                    .put("id", feed.id)
                    .put("name", feed.name)
                    .put("url", feed.url)
                    .put("color", feed.color)
            )
        }
        preferences.edit().putString(Key, array.toString()).apply()
    }

    /**
     * A color for a new feed: one that the other feeds don't have yet, if possible
     */
    fun nextColor(): Int {
        val used = getFeeds().map { it.color }
        return Colors.firstOrNull { it !in used } ?: Colors[used.size % Colors.size]
    }

    companion object {
        private const val Key = "feeds"

        private val Colors = listOf(
            0xFF1E88E5.toInt(), // Blue
            0xFF43A047.toInt(), // Green
            0xFFE53935.toInt(), // Red
            0xFFFB8C00.toInt(), // Orange
            0xFF8E24AA.toInt(), // Purple
            0xFF00ACC1.toInt(), // Cyan
            0xFF6D4C41.toInt(), // Brown
            0xFF546E7A.toInt(), // Blue grey
        )
    }
}
