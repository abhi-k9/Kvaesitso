package io.github.abhik9.kvaesitso.plugin.openmeteo

import de.mm20.launcher2.weather.WeatherIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoApiTest {

    // Shortened response of the forecast API, as it's requested by OpenMeteoApi
    private val forecast = """
        {
          "latitude": 40.710335, "longitude": -73.99307, "generationtime_ms": 0.25,
          "utc_offset_seconds": 0, "timezone": "GMT", "timezone_abbreviation": "GMT",
          "elevation": 32.0,
          "hourly_units": { "time": "unixtime", "temperature_2m": "°C" },
          "hourly": {
            "time": [1760140800, 1760144400, 1760148000],
            "temperature_2m": [14.3, 13.9, null],
            "relative_humidity_2m": [70, 72, 75],
            "precipitation_probability": [0, 15, null],
            "precipitation": [0.0, 0.2, 1.4],
            "weather_code": [0, 61, 95],
            "pressure_msl": [1016.2, 1015.8, 1015.1],
            "cloud_cover": [5, 80, 100],
            "wind_speed_10m": [3.1, 4.2, 6.0],
            "wind_direction_10m": [250, 260, 270],
            "uv_index": [0.0, 0.0, 1.5],
            "is_day": [0, 0, 1]
          }
        }
    """.trimIndent()

    @Test
    fun hoursAreRead() {
        val hours = parseHourlyForecast(forecast)
        assertEquals(3, hours.size)
        val first = hours[0]
        assertEquals(1760140800L, first.time)
        assertEquals(14.3, first.temperature!!, 0.001)
        assertEquals(70.0, first.humidity!!, 0.001)
        assertEquals(0, first.weatherCode)
        assertEquals(1016.2, first.pressure!!, 0.001)
        assertEquals(3.1, first.windSpeed!!, 0.001)
        assertEquals(250.0, first.windDirection!!, 0.001)
        assertEquals(false, first.isDay)
        assertEquals(true, hours[2].isDay)
        assertEquals(95, hours[2].weatherCode)
    }

    @Test
    fun missingValuesAreNull() {
        val hours = parseHourlyForecast(forecast)
        assertNull(hours[2].temperature)
        assertNull(hours[2].precipitationProbability)
        assertEquals(1.4, hours[2].precipitation!!, 0.001)
    }

    @Test
    fun missingColumnsAreNull() {
        val hours = parseHourlyForecast("""{"hourly": {"time": [1760140800], "temperature_2m": [10]}}""")
        assertEquals(1, hours.size)
        assertEquals(10.0, hours[0].temperature!!, 0.001)
        assertNull(hours[0].weatherCode)
        assertNull(hours[0].isDay)
    }

    @Test(expected = IllegalArgumentException::class)
    fun errorResponseIsRejected() {
        parseHourlyForecast("""{"error": true, "reason": "Latitude must be in range of -90 to 90°."}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidJsonIsRejected() {
        parseHourlyForecast("<html>Bad gateway</html>")
    }

    @Test
    fun placesAreNamedWithRegionAndCountry() {
        val places = parseGeocodingResults(
            """
            {"results": [
              {"id": 4951788, "name": "Springfield", "latitude": 42.10148, "longitude": -72.58981,
               "country_code": "US", "country": "United States", "admin1": "Massachusetts"},
              {"id": 2988507, "name": "Paris", "latitude": 48.85341, "longitude": 2.3488,
               "country": "France", "admin1": "Île-de-France"},
              {"id": 1, "name": "Singapore", "latitude": 1.29, "longitude": 103.85,
               "country": "Singapore", "admin1": "Singapore"}
            ], "generationtime_ms": 0.9}
            """.trimIndent()
        )
        assertEquals(3, places.size)
        assertEquals("Springfield, Massachusetts, United States", places[0].name)
        assertEquals(42.10148, places[0].lat, 0.00001)
        assertEquals(-72.58981, places[0].lon, 0.00001)
        assertEquals("Paris, Île-de-France, France", places[1].name)
        // The same name isn't repeated
        assertEquals("Singapore", places[2].name)
    }

    @Test
    fun noPlacesFound() {
        assertTrue(parseGeocodingResults("""{"generationtime_ms": 0.5}""").isEmpty())
    }

    @Test
    fun placesWithoutCoordinatesAreSkipped() {
        val places = parseGeocodingResults("""{"results": [{"name": "Nowhere"}]}""")
        assertTrue(places.isEmpty())
    }

    @Test
    fun weatherCodesHaveIcons() {
        assertEquals(WeatherIcon.Clear, weatherIcon(0))
        assertEquals(WeatherIcon.PartlyCloudy, weatherIcon(2))
        assertEquals(WeatherIcon.Overcast, weatherIcon(3))
        assertEquals(WeatherIcon.Fog, weatherIcon(45))
        assertEquals(WeatherIcon.LightRain, weatherIcon(51))
        assertEquals(WeatherIcon.Rain, weatherIcon(63))
        assertEquals(WeatherIcon.HeavyRain, weatherIcon(82))
        assertEquals(WeatherIcon.Sleet, weatherIcon(66))
        assertEquals(WeatherIcon.Snow, weatherIcon(75))
        assertEquals(WeatherIcon.Thunderstorm, weatherIcon(99))
        assertEquals(WeatherIcon.Unknown, weatherIcon(null))
        assertEquals(WeatherIcon.Unknown, weatherIcon(42))
    }
}
