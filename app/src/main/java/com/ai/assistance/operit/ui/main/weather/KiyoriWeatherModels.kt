package com.ai.assistance.operit.ui.main.weather

import com.google.gson.JsonParser

internal sealed interface KiyoriWeatherState {
    data object PermissionRequired : KiyoriWeatherState

    data object PermissionDenied : KiyoriWeatherState

    data object Loading : KiyoriWeatherState

    data class Available(
        val city: String,
        val temperatureCelsius: Double,
        val visual: KiyoriWeatherVisual,
    ) : KiyoriWeatherState

    data class Unavailable(val reason: KiyoriWeatherFailure) : KiyoriWeatherState
}

internal enum class KiyoriWeatherFailure {
    LOCATION_DISABLED,
    LOCATION_UNAVAILABLE,
    CITY_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    INVALID_RESPONSE,
}

internal enum class KiyoriWeatherVisual {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
}

internal data class KiyoriCurrentWeather(
    val temperatureCelsius: Double,
    val visual: KiyoriWeatherVisual,
)

internal class KiyoriWeatherResponseException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun parseKiyoriCurrentWeather(json: String): KiyoriCurrentWeather =
    try {
        val root = JsonParser.parseString(json).asJsonObject
        val currentUnits = root.getAsJsonObject("current_units")
        if (currentUnits.get("temperature_2m").asString != "\u00B0C") {
            throw KiyoriWeatherResponseException(
                "Open-Meteo returned a non-Celsius temperature unit",
            )
        }
        val current = root.getAsJsonObject("current")
        KiyoriCurrentWeather(
            temperatureCelsius = current.get("temperature_2m").asDouble,
            visual = mapKiyoriWeatherCode(current.get("weather_code").asInt),
        )
    } catch (error: KiyoriWeatherResponseException) {
        throw error
    } catch (error: RuntimeException) {
        throw KiyoriWeatherResponseException("Invalid Open-Meteo current-weather response", error)
    }

internal fun mapKiyoriWeatherCode(weatherCode: Int): KiyoriWeatherVisual =
    when (weatherCode) {
        0 -> KiyoriWeatherVisual.CLEAR
        1, 2 -> KiyoriWeatherVisual.PARTLY_CLOUDY
        3 -> KiyoriWeatherVisual.OVERCAST
        45, 48 -> KiyoriWeatherVisual.FOG
        51, 53, 55, 56, 57 -> KiyoriWeatherVisual.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> KiyoriWeatherVisual.RAIN
        71, 73, 75, 77, 85, 86 -> KiyoriWeatherVisual.SNOW
        95, 96, 99 -> KiyoriWeatherVisual.THUNDERSTORM
        else -> throw KiyoriWeatherResponseException(
            "Unsupported Open-Meteo weather code: $weatherCode",
        )
    }
