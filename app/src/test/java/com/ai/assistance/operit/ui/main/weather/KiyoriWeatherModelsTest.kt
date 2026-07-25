package com.ai.assistance.operit.ui.main.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KiyoriWeatherModelsTest {
    @Test
    fun parsesCelsiusCurrentWeather() {
        val weather =
            parseKiyoriCurrentWeather(
                """
                {
                  "current_units": {"temperature_2m": "\u00b0C"},
                  "current": {"temperature_2m": 18.6, "weather_code": 63}
                }
                """.trimIndent(),
            )

        assertEquals(18.6, weather.temperatureCelsius, 0.0)
        assertEquals(KiyoriWeatherVisual.RAIN, weather.visual)
    }

    @Test
    fun mapsEveryOpenMeteoWmoGroup() {
        assertEquals(KiyoriWeatherVisual.CLEAR, mapKiyoriWeatherCode(0))
        assertEquals(KiyoriWeatherVisual.PARTLY_CLOUDY, mapKiyoriWeatherCode(2))
        assertEquals(KiyoriWeatherVisual.OVERCAST, mapKiyoriWeatherCode(3))
        assertEquals(KiyoriWeatherVisual.FOG, mapKiyoriWeatherCode(48))
        assertEquals(KiyoriWeatherVisual.DRIZZLE, mapKiyoriWeatherCode(57))
        assertEquals(KiyoriWeatherVisual.RAIN, mapKiyoriWeatherCode(82))
        assertEquals(KiyoriWeatherVisual.SNOW, mapKiyoriWeatherCode(86))
        assertEquals(KiyoriWeatherVisual.THUNDERSTORM, mapKiyoriWeatherCode(99))
    }

    @Test
    fun rejectsUnsupportedWeatherCode() {
        assertThrows(KiyoriWeatherResponseException::class.java) {
            mapKiyoriWeatherCode(100)
        }
    }

    @Test
    fun rejectsNonCelsiusResponse() {
        assertThrows(KiyoriWeatherResponseException::class.java) {
            parseKiyoriCurrentWeather(
                """
                {
                  "current_units": {"temperature_2m": "\u00b0F"},
                  "current": {"temperature_2m": 70.0, "weather_code": 0}
                }
                """.trimIndent(),
            )
        }
    }
}
