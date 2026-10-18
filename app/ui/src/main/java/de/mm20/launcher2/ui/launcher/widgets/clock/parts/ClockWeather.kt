package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.base.LocalTimeState
import de.mm20.launcher2.ui.component.weather.WeatherIcon
import de.mm20.launcher2.ui.component.weather.WeatherIconDefaults
import de.mm20.launcher2.ui.launcher.widgets.weather.weatherIconById
import de.mm20.launcher2.ui.locals.LocalMeasurementSystem
import de.mm20.launcher2.ui.utils.formatTemperature
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherRepository
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * The current weather (icon and temperature), shown next to the date. Nothing if there is no
 * current forecast, e.g. if no weather provider has been set up.
 */
@Composable
internal fun ClockWeather(compactLayout: Boolean, modifier: Modifier = Modifier) {
    val weatherRepository: WeatherRepository = koinInject()
    val forecasts by remember(weatherRepository) { weatherRepository.getForecasts() }
        .collectAsState(emptyList())

    // Only looked up again every few minutes, not every second
    val timeState = LocalTimeState.current
    val minutes by remember(timeState) {
        derivedStateOf { timeState.value / (5 * 60 * 1000L) }
    }
    val forecast = remember(forecasts, minutes) {
        currentForecast(forecasts, System.currentTimeMillis())
    } ?: return

    val context = LocalContext.current
    val measurementSystem = LocalMeasurementSystem.current

    TextButton(
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = LocalContentColor.current
        ),
        onClick = {
            if (forecast.providerUrl.isNotBlank()) {
                context.tryStartActivity(
                    Intent(Intent.ACTION_VIEW, forecast.providerUrl.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
    ) {
        WeatherIcon(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(if (compactLayout) 28.dp else 24.dp)
                .semantics {
                    contentDescription = forecast.condition
                },
            icon = weatherIconById(forecast.icon),
            night = forecast.night,
            // The clock is drawn on the wallpaper: light text on dark wallpapers and vice versa
            colors = WeatherIconDefaults.colors(
                background = if (LocalContentColor.current.luminance() > 0.5f) Color.Black
                else Color.White
            ),
        )
        Text(
            text = formatTemperature(context, forecast.temperature.toFloat(), measurementSystem),
            style = if (compactLayout) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * The forecast for the current hour, null if there is none (forecasts that are more than a few
 * hours away don't say much about the current weather).
 *
 * @param forecasts in chronological order
 */
private fun currentForecast(forecasts: List<Forecast>, now: Long): Forecast? {
    val forecast = forecasts.lastOrNull { it.timestamp <= now } ?: forecasts.firstOrNull()
    return forecast?.takeIf { abs(it.timestamp - now) < 3 * 60 * 60 * 1000L }
}
