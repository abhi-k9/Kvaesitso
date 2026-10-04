package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalTimeFormat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

class AlarmPartProvider : PartProvider {

    private val nextAlarmTime = mutableStateOf<Long?>(null)

    private val time = MutableStateFlow(System.currentTimeMillis())

    override fun setTime(time: Long) {
        this.time.value = time
    }

    override fun getRanking(context: Context): Flow<Int> = channelFlow {
        val nextAlarm = getNextAlarmTime(context)
        nextAlarm.collectLatest { alarm ->
            nextAlarmTime.value = alarm
            if (alarm == null) {
                send(0)
            } else {
                time.collectLatest {
                    if (alarm > it + 8 * 60 * 60 * 1000) {
                        send(0)
                    } else {
                        send(60)
                    }
                }
            }
        }
    }

    private fun getNextAlarmTime(context: Context): Flow<Long?> = callbackFlow {
        val alarmManager: AlarmManager = context.getSystemService() ?: return@callbackFlow
        trySendBlocking(alarmManager.nextAlarmClock?.triggerTime)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(alarmManager.nextAlarmClock?.triggerTime)
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        })
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current

        val alarmTime by nextAlarmTime
        val time by this.time.collectAsState(System.currentTimeMillis())
        val timeFormat = LocalTimeFormat.current

        alarmTime?.let {

            if (!compactLayout) {

                TextButton(
                    onClick = {
                        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.alarm_24px),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = formatAlarmTime(context, it, time, timeFormat),
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current
                    )
                ) {
                    Icon(
                        modifier = Modifier.padding(end = 8.dp).size(32.dp),
                        painter = painterResource(R.drawable.alarm_24px),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = formatAlarmTime(context, it, time, timeFormat),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * “in 25 minutes” for alarms within the next hour. Later alarms would be rounded down to full
 * hours (“in 1 hour” for 1 h 59 min), so show the time of the alarm instead.
 */
private fun formatAlarmTime(context: Context, alarm: Long, now: Long, timeFormat: TimeFormat): String {
    if (alarm - now < DateUtils.HOUR_IN_MILLIS) {
        return DateUtils.getRelativeTimeSpanString(alarm, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val timeFormatFlag = when (timeFormat) {
        TimeFormat.TwelveHour -> DateUtils.FORMAT_12HOUR
        TimeFormat.TwentyFourHour -> DateUtils.FORMAT_24HOUR
        else -> 0
    }
    return DateUtils.formatDateTime(context, alarm, DateUtils.FORMAT_SHOW_TIME or timeFormatFlag)
}
