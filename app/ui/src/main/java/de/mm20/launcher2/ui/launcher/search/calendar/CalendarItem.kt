package de.mm20.launcher2.ui.launcher.search.calendar

import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.ULocale
import android.text.Html
import android.text.format.DateUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.calendar.CalendarRepository
import de.mm20.launcher2.calendar.FormattedText
import de.mm20.launcher2.calendar.Markdown
import de.mm20.launcher2.calendar.TaskChangeResult
import de.mm20.launcher2.calendar.providers.TasksCalendarEvent
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DefaultToolbarAction
import de.mm20.launcher2.ui.component.Toolbar
import de.mm20.launcher2.ui.component.ToolbarAction
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalCalendarSystemIds
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import de.mm20.launcher2.ui.locals.LocalFavoritesEnabled
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import de.mm20.launcher2.ui.utils.isTwentyFourHours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import palettes.TonalPalette
import kotlin.time.Duration.Companion.seconds

@Composable
fun CalendarItem(
    modifier: Modifier = Modifier,
    calendar: CalendarEvent,
    showDetails: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SearchableItemVM = listItemViewModel(key = "search-${calendar.key}")
    val iconSize = LocalGridSettings.current.iconSize.dp.toPixels()
    val timeFormat = LocalTimeFormat.current

    LaunchedEffect(calendar) {
        viewModel.init(calendar, iconSize.toInt())
    }

    val calendarRepository: CalendarRepository = koinInject()
    val permissionsManager: PermissionsManager = koinInject()
    val scope = rememberCoroutineScope()
    // Shown until the changed task is loaded again
    var completedOverride by remember(calendar) { mutableStateOf<Boolean?>(null) }
    val isCompleted = completedOverride ?: calendar.isCompleted
    val canChangeCompletion = remember(calendar) {
        calendarRepository.canChangeCompletion(calendar)
    }

    fun setCompleted(completed: Boolean) {
        completedOverride = completed
        scope.launch {
            var result = calendarRepository.setTaskCompleted(calendar, completed)
            if (result == TaskChangeResult.NoPermission) {
                val activity = context as? AppCompatActivity
                if (activity != null) {
                    permissionsManager.requestPermission(activity, PermissionGroup.TasksWrite)
                    // Finish what the user wanted to do once the permission has been granted
                    val granted = withTimeoutOrNull(60.seconds) {
                        permissionsManager.hasPermission(PermissionGroup.TasksWrite).first { it }
                    }
                    if (granted == true) {
                        result = calendarRepository.setTaskCompleted(calendar, completed)
                    }
                }
            }
            when (result) {
                TaskChangeResult.Changed -> {}
                TaskChangeResult.NoPermission -> completedOverride = null
                TaskChangeResult.NotSupported -> {
                    completedOverride = null
                    Toast.makeText(context, R.string.task_change_unsupported, Toast.LENGTH_LONG)
                        .show()
                    viewModel.launch(context)
                }
                TaskChangeResult.Failed -> {
                    completedOverride = null
                    Toast.makeText(context, R.string.task_change_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val completionToggle = if (canChangeCompletion && isCompleted != null) {
        Modifier
            .clip(CircleShape)
            .toggleable(
                value = isCompleted,
                role = Role.Checkbox,
                onValueChange = { setCompleted(it) },
            )
    } else {
        Modifier
    }

    val darkMode = LocalDarkTheme.current
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val eventColor = Color(
        TonalPalette
            .fromInt(calendar.color ?: secondaryColor.toArgb())
            .tone(
                if (darkMode) 80 else 40
            )
    )

    SharedTransitionLayout {
        AnimatedContent(showDetails) { showDetails ->
            if (showDetails) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(
                                when (isCompleted) {
                                    true -> R.drawable.check_circle_24px_filled
                                    false -> R.drawable.radio_button_unchecked_24px
                                    null -> R.drawable.circle_24px_filled
                                }
                            ),
                            null,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 20.dp)
                                .size(24.dp)
                                .then(completionToggle)
                                .sharedElement(
                                    rememberSharedContentState("color"),
                                    this@AnimatedContent
                                ),
                            tint = eventColor
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 4.dp, end = 16.dp)
                        ) {
                            Text(
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("label"),
                                        this@AnimatedContent
                                    ),
                                text = calendar.labelOverride ?: calendar.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                textDecoration = if (isCompleted == true) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            )
                            if (calendar.calendarName != null) {
                                Text(
                                    modifier = Modifier.animateEnterExit(
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ),
                                    text = calendar.calendarName!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = eventColor,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            painter = painterResource(R.drawable.schedule_24px),
                            contentDescription = null
                        )
                        Column {
                            val primaryCalendar = LocalCalendarSystemIds.current.first()
                            val secondaryCalendar = LocalCalendarSystemIds.current.last()

                            Text(
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState("date"),
                                        this@AnimatedContent
                                    ),
                                text = calendar.formatTime(context, timeFormat, primaryCalendar),
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (secondaryCalendar != null) {
                                Text(
                                    modifier = modifier.padding(top = 2.dp),
                                    text = calendar.formatTime(
                                        context,
                                        timeFormat,
                                        secondaryCalendar
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!calendar.description.isNullOrBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                painter = painterResource(R.drawable.notes_24px),
                                contentDescription = null
                            )
                            val linkColor = MaterialTheme.colorScheme.primary
                            val description = remember(calendar.description, linkColor) {
                                if (calendar is TasksCalendarEvent) {
                                    // Tasks stores notes as Markdown
                                    Markdown.parse(calendar.description!!)
                                        .toAnnotatedString(linkColor) { url ->
                                            context.tryStartActivity(
                                                Intent(Intent.ACTION_VIEW, url.toUri())
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                } else {
                                    AnnotatedString(
                                        Html.fromHtml(
                                            calendar.description!!,
                                            Html.FROM_HTML_MODE_COMPACT
                                        ).toString()
                                    )
                                }
                            }
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (calendar.attendees.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                painter = painterResource(R.drawable.group_24px),
                                contentDescription = null
                            )
                            Text(
                                text = calendar.attendees.joinToString(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!calendar.location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp, end = 16.dp)
                                .clickable {
                                    calendar.openLocation(context)
                                }
                        ) {
                            Icon(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                                painter = painterResource(R.drawable.location_on_24px),
                                contentDescription = null
                            )
                            Text(
                                text = calendar.location!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    val toolbarActions = mutableListOf<ToolbarAction>()

                    if (LocalFavoritesEnabled.current) {
                        val isPinned by viewModel.isPinned.collectAsState(false)
                        val favAction = if (isPinned) {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_unpin),
                                icon = R.drawable.star_24px_filled,
                                action = {
                                    viewModel.unpin()
                                }
                            )
                        } else {
                            DefaultToolbarAction(
                                label = stringResource(R.string.menu_favorites_pin),
                                icon = R.drawable.star_24px,
                                action = {
                                    viewModel.pin()
                                })
                        }
                        toolbarActions.add(favAction)
                    }

                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_calendar_open_externally),
                            icon = R.drawable.open_in_new_24px,
                            action = {
                                viewModel.launch(context)
                                onBack()
                            }
                        )
                    )

                    val sheetManager = LocalBottomSheetManager.current
                    toolbarActions.add(
                        DefaultToolbarAction(
                            label = stringResource(R.string.menu_customize),
                            icon = R.drawable.tune_24px,
                            action = { sheetManager.showCustomizeSearchableModal(calendar) }
                        ))

                    Toolbar(
                        leftActions = listOf(
                            DefaultToolbarAction(
                                label = stringResource(id = R.string.menu_back),
                                icon = R.drawable.arrow_back_24px,
                            ) {
                                onBack()
                            }
                        ),
                        rightActions = toolbarActions
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                        .padding(16.dp)
                ) {
                    Icon(
                        painterResource(
                            when (isCompleted) {
                                true -> R.drawable.check_circle_24px_filled
                                false -> R.drawable.radio_button_unchecked_24px
                                null -> R.drawable.circle_24px_filled
                            }
                        ),
                        null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp)
                            .then(completionToggle)
                            .sharedElement(
                                rememberSharedContentState("color"),
                                this@AnimatedContent
                            ),
                        tint = eventColor
                    )
                    Column {
                        Text(
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState("label"),
                                this@AnimatedContent
                            ),
                            text = calendar.labelOverride ?: calendar.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            textDecoration = if (isCompleted == true) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }
                        )
                        Text(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .sharedBounds(
                                    rememberSharedContentState("date"),
                                    this@AnimatedContent
                                ),
                            text = calendar.getSummary(context, timeFormat, LocalCalendarSystemIds.current.first()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCompleted == false && calendar.isOverdue()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                }
            }
        }
    }
}

@Composable
fun CalendarItemGridPopup(
    calendar: CalendarEvent,
    show: MutableTransitionState<Boolean>,
    animationProgress: Float,
    origin: IntRect,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        show,
        enter = expandIn(
            animationSpec = tween(300),
            expandFrom = Alignment.Center,
        ) { origin.size },
        exit = shrinkOut(
            animationSpec = tween(300),
            shrinkTowards = Alignment.Center,
        ) { origin.size },
    ) {
        CalendarItem(
            modifier = Modifier
                .fillMaxWidth(),
            calendar = calendar,
            showDetails = true,
            onBack = onDismiss
        )
    }
}

private fun FormattedText.toAnnotatedString(
    linkColor: Color,
    onOpenLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)
    for (span in spans) {
        when (val style = span.style) {
            FormattedText.Style.Bold ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)

            FormattedText.Style.Italic ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)

            FormattedText.Style.Strikethrough ->
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), span.start, span.end)

            FormattedText.Style.Code ->
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace), span.start, span.end)

            is FormattedText.Style.Link -> addLink(
                LinkAnnotation.Url(
                    url = style.url,
                    styles = TextLinkStyles(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    ),
                    linkInteractionListener = { onOpenLink(style.url) },
                ),
                span.start,
                span.end,
            )
        }
    }
}

private fun CalendarEvent.isOverdue(): Boolean {
    val endTime = endTime ?: return false
    return isTask && endTime < System.currentTimeMillis()
}

private fun CalendarEvent.formatTime(
    context: Context,
    timeFormat: TimeFormat,
    calendarSystem: String?,
): String {
    val locale = ULocale.getDefault().setKeywordValue("calendar", calendarSystem)

    val time = if (timeFormat.isTwentyFourHours(context)) {
        "HH:mm"
    } else {
        "hh:mm a"
    }

    val startTime = startTime
    val endTime = endTime ?: return context.getString(R.string.task_no_due_date)
    if (startTime == null || isTask) {
        if (allDay) {
            return DateFormat.getInstanceForSkeleton("EEE, MMMM d yyyy", locale)
                .format(endTime)
        }
        return DateFormat.getInstanceForSkeleton("EEE, MMMM d yyyy, $time", locale)
            .format(endTime)
    }

    if (allDay) {
        return DateIntervalFormat.getInstance("EEE, MMMM d yyyy", locale)
            .format(DateInterval(startTime, endTime))
    }

    return DateIntervalFormat.getInstance("EEE, MMMM d yyyy, $time", locale)
        .format(DateInterval(startTime, endTime))

}

private fun CalendarEvent.getSummary(
    context: Context,
    timeFormat: TimeFormat,
    calendarSystem: String?,
): String {
    val locale = ULocale.getDefault().setKeywordValue("calendar", calendarSystem)

    val time = if (timeFormat.isTwentyFourHours(context)) {
        "HH:mm"
    } else {
        "hh:mm a"
    }

    val startTime = startTime
    val endTime = endTime ?: return context.getString(R.string.task_no_due_date)
    if (isTask || startTime == null) {
        val isToday = DateUtils.isToday(endTime)
        if (isToday) {
            if (allDay) {
                return context.getString(R.string.task_due_today)
            }
            return context.getString(
                R.string.task_due_time,
                DateIntervalFormat.getInstance(time, locale)
                    .format(DateInterval(endTime, endTime))
            )
        }
        if (allDay) {
            return context.getString(
                R.string.task_due_date,
                DateFormat.getInstanceForSkeleton("EEE, MMMM d", locale)
                    .format(endTime)
            )
        }
        return context.getString(
            R.string.task_due_date,
            DateFormat.getInstanceForSkeleton("EEE, MMMM d, $time", locale)
                .format(endTime)
        )
    }

    val isToday =
        DateUtils.isToday(startTime) && DateUtils.isToday(endTime)
    return if (isToday) {
        if (allDay) {
            context.getString(R.string.calendar_event_allday)
        } else {
            DateIntervalFormat.getInstance(time, locale)
                .format(DateInterval(startTime, endTime))
        }
    } else {
        if (allDay) {
            DateIntervalFormat.getInstance("MMMM d", locale)
                .format(DateInterval(startTime, endTime))
        } else {

            DateIntervalFormat.getInstance("MMMM d, $time", locale)
                .format(DateInterval(startTime, endTime))
        }
    }
}