package io.github.abhik9.kvaesitso.plugin.openmeteo

import de.mm20.launcher2.weather.WeatherIcon

/**
 * The launcher's icon for a WMO weather interpretation code, as Open-Meteo uses them:
 * https://open-meteo.com/en/docs#weather_variable_documentation
 */
internal fun weatherIcon(code: Int?): WeatherIcon {
    return when (code) {
        0 -> WeatherIcon.Clear
        1, 2 -> WeatherIcon.PartlyCloudy
        3 -> WeatherIcon.Overcast
        45, 48 -> WeatherIcon.Fog
        51, 53, 55, 61, 80 -> WeatherIcon.LightRain
        63, 81 -> WeatherIcon.Rain
        65, 82 -> WeatherIcon.HeavyRain
        56, 57, 66, 67 -> WeatherIcon.Sleet
        71, 73, 75, 77, 85, 86 -> WeatherIcon.Snow
        95, 96, 99 -> WeatherIcon.Thunderstorm
        else -> WeatherIcon.Unknown
    }
}

/**
 * The string resource that describes a WMO weather interpretation code
 */
internal fun conditionText(code: Int?): Int {
    return when (code) {
        0 -> R.string.condition_clear
        1 -> R.string.condition_mainly_clear
        2 -> R.string.condition_partly_cloudy
        3 -> R.string.condition_overcast
        45 -> R.string.condition_fog
        48 -> R.string.condition_rime_fog
        51 -> R.string.condition_light_drizzle
        53 -> R.string.condition_drizzle
        55 -> R.string.condition_dense_drizzle
        56, 57 -> R.string.condition_freezing_drizzle
        61 -> R.string.condition_light_rain
        63 -> R.string.condition_rain
        65 -> R.string.condition_heavy_rain
        66, 67 -> R.string.condition_freezing_rain
        71 -> R.string.condition_light_snow
        73 -> R.string.condition_snow
        75 -> R.string.condition_heavy_snow
        77 -> R.string.condition_snow_grains
        80 -> R.string.condition_light_showers
        81 -> R.string.condition_showers
        82 -> R.string.condition_heavy_showers
        85 -> R.string.condition_snow_showers
        86 -> R.string.condition_heavy_snow_showers
        95 -> R.string.condition_thunderstorm
        96, 99 -> R.string.condition_thunderstorm_hail
        else -> R.string.condition_unknown
    }
}
