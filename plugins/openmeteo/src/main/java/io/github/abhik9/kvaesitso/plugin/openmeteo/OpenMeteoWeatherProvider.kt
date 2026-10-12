package io.github.abhik9.kvaesitso.plugin.openmeteo

import android.util.Log
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.weather.C
import de.mm20.launcher2.sdk.weather.Forecast
import de.mm20.launcher2.sdk.weather.WeatherLocation
import de.mm20.launcher2.sdk.weather.WeatherProvider
import de.mm20.launcher2.sdk.weather.hPa
import de.mm20.launcher2.sdk.weather.m_s
import de.mm20.launcher2.sdk.weather.mm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

class OpenMeteoWeatherProvider : WeatherProvider(
    WeatherPluginConfig(
        // Open-Meteo's models are updated every hour at most
        minUpdateInterval = 30 * 60 * 1000L,
    )
) {

    override suspend fun getPluginState(): PluginState {
        return PluginState.Ready(context?.getString(R.string.plugin_state_ready))
    }

    override suspend fun getWeatherData(lat: Double, lon: Double, lang: String?): List<Forecast>? {
        return getForecasts(lat, lon, getLocationName(lat, lon))
    }

    override suspend fun getWeatherData(location: WeatherLocation, lang: String?): List<Forecast>? {
        return when (location) {
            is WeatherLocation.LatLon -> getForecasts(location.lat, location.lon, location.name)
            else -> null
        }
    }

    /**
     * Places are found with Open-Meteo's geocoding, which also works on phones without Google's
     * geocoder. Coordinates, and searches while it can't be reached, are left to the default
     * implementation.
     */
    override suspend fun findLocations(query: String, lang: String): List<WeatherLocation> {
        val parts = query.trim().split(" ", limit = 3)
        if (parts.size >= 2 && parts[0].toDoubleOrNull() != null && parts[1].toDoubleOrNull() != null) {
            return super.findLocations(query, lang)
        }
        val results = withContext(Dispatchers.IO) {
            try {
                OpenMeteoApi.searchLocations(query.trim(), lang)
            } catch (e: IOException) {
                Log.w(Tag, "Couldn't search places", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(Tag, "Invalid geocoding response", e)
                null
            }
        } ?: return super.findLocations(query, lang)
        return results.map { WeatherLocation.LatLon(name = it.name, lat = it.lat, lon = it.lon) }
    }

    private suspend fun getForecasts(lat: Double, lon: Double, locationName: String): List<Forecast>? {
        val context = context ?: return null
        val hours = withContext(Dispatchers.IO) {
            try {
                OpenMeteoApi.getHourlyForecast(lat, lon)
            } catch (e: IOException) {
                Log.w(Tag, "Couldn't get the forecast", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(Tag, "Invalid forecast response", e)
                null
            }
        } ?: return null

        val now = System.currentTimeMillis()
        val provider = context.getString(R.string.plugin_label)
        return hours
            // The forecast starts at midnight, the hours before the current one aren't needed
            .filter { it.time * 1000 > now - 60 * 60 * 1000 }
            .mapNotNull {
                Forecast(
                    timestamp = it.time * 1000,
                    createdAt = now,
                    temperature = (it.temperature ?: return@mapNotNull null).C,
                    condition = context.getString(conditionText(it.weatherCode)),
                    icon = weatherIcon(it.weatherCode),
                    night = it.isDay == false,
                    pressure = it.pressure?.hPa,
                    humidity = it.humidity?.roundToInt(),
                    windSpeed = it.windSpeed?.m_s,
                    windDirection = it.windDirection,
                    precipitation = it.precipitation?.mm,
                    rainProbability = it.precipitationProbability?.roundToInt(),
                    clouds = it.cloudCover?.roundToInt(),
                    uvIndex = it.uvIndex,
                    location = locationName,
                    provider = provider,
                    providerUrl = "https://open-meteo.com/",
                )
            }
            .takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val Tag = "OpenMeteoProvider"
    }
}
