package io.github.abhik9.kvaesitso.plugin.calendarfeeds

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The plugin's settings: the list of calendar feeds, where feeds are added, changed and removed
 */
class FeedsActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: FeedStore
    private lateinit var feedList: LinearLayout
    private lateinit var updateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FeedStore(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, dp(8), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = getString(R.string.feeds_intro)
            setPadding(0, 0, 0, dp(16))
        })
        feedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(feedList)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(16), 0, 0)
        }
        updateButton = Button(this).apply {
            text = getString(R.string.update_now)
            setOnClickListener { updateAll() }
        }
        buttons.addView(updateButton)
        buttons.addView(Button(this).apply {
            text = getString(R.string.add_feed)
            setOnClickListener { showFeedDialog(null) }
        })
        content.addView(buttons)

        val scrollView = ScrollView(this).apply {
            clipToPadding = false
            addView(content)
        }
        // The window is edge to edge on newer Android versions
        scrollView.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
        setContentView(scrollView)
        showFeeds()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showFeeds() {
        feedList.removeAllViews()
        val feeds = store.getFeeds()
        updateButton.isEnabled = feeds.isNotEmpty()
        if (feeds.isEmpty()) {
            feedList.addView(TextView(this).apply {
                text = getString(R.string.no_feeds)
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        for (feed in feeds) {
            feedList.addView(feedRow(feed))
        }
    }

    private fun feedRow(feed: Feed): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            isFocusable = true
            selectableBackground()?.let { background = it }
            setOnClickListener { showFeedDialog(feed) }
        }
        row.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(feed.color)
            }
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(16) }
        })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = feed.name.ifEmpty { feed.url }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })
        val updated = FeedRepository.lastUpdated(this, feed)
        texts.addView(TextView(this).apply {
            text = if (updated != null) {
                getString(
                    R.string.feed_updated,
                    DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                )
            } else {
                getString(R.string.feed_not_updated)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            alpha = 0.7f
        })
        row.addView(texts)
        return row
    }

    private fun showFeedDialog(feed: Feed?) {
        val urlField = EditText(this).apply {
            hint = getString(R.string.feed_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(feed?.url)
        }
        val nameField = EditText(this).apply {
            hint = getString(R.string.feed_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(feed?.name)
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(urlField)
            addView(nameField)
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(if (feed == null) R.string.add_feed else R.string.edit_feed)
            .setView(fields)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (feed != null) {
            builder.setNeutralButton(R.string.delete) { _, _ -> confirmDelete(feed) }
        }
        val dialog = builder.create()
        // The keyboard only opens when a field is tapped
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val url = urlField.text.toString().trim()
                if (!FeedRepository.isValidUrl(url)) {
                    urlField.error = getString(R.string.error_invalid_url)
                    return@setOnClickListener
                }
                save.isEnabled = false
                save.text = getString(R.string.checking)
                saveFeed(feed, nameField.text.toString().trim(), url) { error ->
                    if (error == null) {
                        dialog.dismiss()
                    } else {
                        save.isEnabled = true
                        save.text = getString(R.string.save)
                        urlField.error = getString(R.string.error_could_not_load, error)
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Downloads the feed to check it, then saves it
     */
    private fun saveFeed(old: Feed?, name: String, url: String, onDone: (error: String?) -> Unit) {
        val normalized = FeedRepository.normalizeUrl(url)
        val feed = Feed(
            id = old?.id ?: UUID.randomUUID().toString(),
            name = name,
            url = normalized,
            color = old?.color ?: store.nextColor(),
        )
        scope.launch {
            val error = FeedRepository.refresh(this@FeedsActivity, feed)
            if (error != null) {
                onDone(error)
                return@launch
            }
            // Without a name, the one that the publisher gave the calendar, or the site's
            val defaultName = FeedRepository.getCalendar(this@FeedsActivity, feed)?.name
                ?: Uri.parse(normalized).host
                ?: normalized
            val saved = feed.copy(name = name.ifEmpty { defaultName })
            val feeds = store.getFeeds()
            store.setFeeds(
                if (feeds.any { it.id == saved.id }) feeds.map { if (it.id == saved.id) saved else it }
                else feeds + saved
            )
            showFeeds()
            onDone(null)
        }
    }

    private fun confirmDelete(feed: Feed) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_feed_message, feed.name.ifEmpty { feed.url }))
            .setPositiveButton(R.string.delete) { _, _ ->
                store.setFeeds(store.getFeeds().filter { it.id != feed.id })
                FeedRepository.delete(this, feed)
                showFeeds()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateAll() {
        val feeds = store.getFeeds()
        if (feeds.isEmpty()) return
        updateButton.isEnabled = false
        scope.launch {
            val failed = feeds.filter { FeedRepository.refresh(this@FeedsActivity, it) != null }
            updateButton.isEnabled = true
            showFeeds()
            val message = if (failed.isEmpty()) getString(R.string.feeds_updated)
            else getString(R.string.feeds_not_updated, failed.joinToString(", ") { it.name.ifEmpty { it.url } })
            Toast.makeText(this@FeedsActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun selectableBackground() = TypedValue().let {
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)) {
            getDrawable(it.resourceId)
        } else null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
