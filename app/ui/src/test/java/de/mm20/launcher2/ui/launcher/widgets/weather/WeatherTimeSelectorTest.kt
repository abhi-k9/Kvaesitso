package de.mm20.launcher2.ui.launcher.widgets.weather

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.TimeFormat
import de.mm20.launcher2.weather.Forecast
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WeatherTimeSelectorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun opensAtCurrentForecastInsteadOfMidnight() {
        showForecasts()
        assertFirstVisibleForecast(12)
    }

    @Test
    fun followsAChangedSelection() {
        val selectedIndex = mutableIntStateOf(12)
        showForecasts(selectedIndex = { selectedIndex.intValue })
        compose.runOnIdle { selectedIndex.intValue = 7 }
        assertFirstVisibleForecast(7)
    }

    @Test
    fun refreshingWeatherAndChangingTimeFormatPreserveManualScroll() {
        val forecasts = mutableStateOf(hourlyForecasts())
        val timeFormat = mutableStateOf(TimeFormat.TwentyFourHour)
        showForecasts(forecasts = { forecasts.value }, timeFormat = { timeFormat.value })
        compose.onNodeWithTag("hours").performScrollToIndex(18)
        assertFirstVisibleForecast(18)
        compose.runOnIdle {
            forecasts.value = forecasts.value.map { it.copy(temperature = 290.0) }
            timeFormat.value = TimeFormat.TwelveHour
        }
        assertFirstVisibleForecast(18)
    }

    @Test
    fun returningToLauncherRealignsCurrentForecast() {
        val owner = ForecastLifecycleOwner()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        showForecasts(lifecycleOwner = owner)
        compose.onNodeWithTag("hours").performScrollToIndex(18)
        assertFirstVisibleForecast(18)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        assertFirstVisibleForecast(12)
    }

    @Test
    fun dayChangeFollowsTheSelectedHourInTheNewForecast() {
        val forecasts = mutableStateOf(hourlyForecasts())
        showForecasts(forecasts = { forecasts.value })
        compose.onNodeWithTag("hours").performScrollToIndex(18)
        compose.runOnIdle {
            forecasts.value = forecasts.value.map { it.copy(timestamp = it.timestamp + 86_400_000L) }
        }
        assertFirstVisibleForecast(12)
    }

    private fun showForecasts(
        forecasts: () -> List<Forecast> = { hourlyForecasts() },
        selectedIndex: () -> Int = { 12 },
        timeFormat: () -> TimeFormat = { TimeFormat.TwentyFourHour },
        lifecycleOwner: LifecycleOwner? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides (lifecycleOwner ?: LocalLifecycleOwner.current),
            ) {
                MaterialTheme {
                    Box(Modifier.size(320.dp, 160.dp)) {
                        val hours = forecasts()
                        WeatherTimeSelector(
                            modifier = Modifier.testTag("hours"),
                            forecasts = hours,
                            selectedForecast = hours[selectedIndex()],
                            measurementSystem = MeasurementSystem.Metric,
                            timeFormat = timeFormat(),
                            onTimeSelected = {},
                        )
                    }
                }
            }
        }
    }

    private fun assertFirstVisibleForecast(index: Int) {
        // Let the day transition finish before inspecting the visible row.
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Forecast $index")
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(8.dp)
    }

    private fun hourlyForecasts() = List(24) { hour ->
        Forecast(
            timestamp = 1_798_675_200_000L + hour * 3_600_000L,
            temperature = 280.0,
            icon = Forecast.CLEAR,
            condition = "Forecast $hour",
            location = "Test location",
            provider = "Test provider",
            updateTime = 1_798_675_200_000L,
        )
    }

    private class ForecastLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
