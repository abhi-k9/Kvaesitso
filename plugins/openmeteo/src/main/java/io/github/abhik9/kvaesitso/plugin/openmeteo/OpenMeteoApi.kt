package io.github.abhik9.kvaesitso.plugin.openmeteo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * The weather of one hour, as Open-Meteo forecasts it. Values that it doesn't have are null.
 */
internal data class HourlyWeather(
    /**
     * Unix time in seconds
     */
    val time: Long,
    /**
     * °C
     */
    val temperature: Double?,
    /**
     * %
     */
    val humidity: Double?,
    /**
     * %
     */
    val precipitationProbability: Double?,
    /**
     * mm
     */
    val precipitation: Double?,
    /**
     * WMO weather interpretation code
     */
    val weatherCode: Int?,
    /**
     * hPa, at sea level
     */
    val pressure: Double?,
    /**
     * %
     */
    val cloudCover: Double?,
    /**
     * m/s
     */
    val windSpeed: Double?,
    /**
     * Degrees
     */
    val windDirection: Double?,
    val uvIndex: Double?,
    val isDay: Boolean?,
)

/**
 * A place found by the geocoding API
 */
internal data class GeocodingResult(
    /**
     * The name of the place with its region and country, e.g. "Springfield, Illinois, United States"
     */
    val name: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Open-Meteo's forecast and geocoding APIs. They're free for non-commercial use and don't need an
 * API key.
 */
internal object OpenMeteoApi {
    private const val UserAgent = "KvaesitsoOpenMeteoPlugin/1.0 (+https://github.com/abhi-k9/Kvaesitso)"

    /**
     * The next days' weather, by hour, for [lat], [lon]
     */
    fun getHourlyForecast(lat: Double, lon: Double): List<HourlyWeather> {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${formatCoordinate(lat)}" +
                "&longitude=${formatCoordinate(lon)}" +
                "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability," +
                "precipitation,weather_code,pressure_msl,cloud_cover,wind_speed_10m," +
                "wind_direction_10m,uv_index,is_day" +
                "&wind_speed_unit=ms" +
                "&timeformat=unixtime" +
                "&forecast_days=7"
        return parseHourlyForecast(get(url))
    }

    /**
     * Places whose name matches [query], with their names in [lang] if possible
     */
    fun searchLocations(query: String, lang: String): List<GeocodingResult> {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(query, "UTF-8")}" +
                "&count=10" +
                "&language=${URLEncoder.encode(lang, "UTF-8")}" +
                "&format=json"
        return parseGeocodingResults(get(url))
    }

    private fun formatCoordinate(value: Double): String {
        // Not in the user's locale: that could use a decimal comma
        return String.format(Locale.ROOT, "%.4f", value)
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", UserAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Open-Meteo returned HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Reads the hourly forecast of a forecast API response that was requested with
 * `timeformat=unixtime`.
 * @throws IllegalArgumentException if [body] isn't such a response
 */
internal fun parseHourlyForecast(body: String): List<HourlyWeather> {
    val root = Json.parseToJsonElement(body).jsonObject
    val hourly = root["hourly"] as? JsonObject
        ?: throw IllegalArgumentException("The response has no hourly forecast")
    val times = hourly["time"] as? JsonArray
        ?: throw IllegalArgumentException("The response has no times")

    fun column(name: String): JsonArray? = hourly[name] as? JsonArray
    val temperature = column("temperature_2m")
    val humidity = column("relative_humidity_2m")
    val precipitationProbability = column("precipitation_probability")
    val precipitation = column("precipitation")
    val weatherCode = column("weather_code")
    val pressure = column("pressure_msl")
    val cloudCover = column("cloud_cover")
    val windSpeed = column("wind_speed_10m")
    val windDirection = column("wind_direction_10m")
    val uvIndex = column("uv_index")
    val isDay = column("is_day")

    return times.indices.mapNotNull { i ->
        val time = times[i].jsonPrimitive.longOrNull ?: return@mapNotNull null
        HourlyWeather(
            time = time,
            temperature = temperature.doubleAt(i),
            humidity = humidity.doubleAt(i),
            precipitationProbability = precipitationProbability.doubleAt(i),
            precipitation = precipitation.doubleAt(i),
            weatherCode = weatherCode.doubleAt(i)?.toInt(),
            pressure = pressure.doubleAt(i),
            cloudCover = cloudCover.doubleAt(i),
            windSpeed = windSpeed.doubleAt(i),
            windDirection = windDirection.doubleAt(i),
            uvIndex = uvIndex.doubleAt(i),
            isDay = isDay.doubleAt(i)?.let { it != 0.0 },
        )
    }
}

/**
 * Reads the places of a geocoding API response. A response without results (there's no
 * "results" then) is an empty list.
 */
internal fun parseGeocodingResults(body: String): List<GeocodingResult> {
    val root = Json.parseToJsonElement(body).jsonObject
    val results = root["results"] as? JsonArray ?: return emptyList()
    return results.mapNotNull { element ->
        val place = element as? JsonObject ?: return@mapNotNull null
        val name = place.string("name") ?: return@mapNotNull null
        val lat = place["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val lon = place["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        val label = listOfNotNull(name, place.string("admin1"), place.string("country"))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        GeocodingResult(name = label, lat = lat, lon = lon)
    }
}

private fun JsonArray?.doubleAt(index: Int): Double? {
    return (this?.getOrNull(index) ?: return null).jsonPrimitive.doubleOrNull
}

private fun JsonObject.string(key: String): String? {
    return (this[key] ?: return null).jsonPrimitive.contentOrNull
}
