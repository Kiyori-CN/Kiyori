package com.ai.assistance.operit.ui.main.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun roundTripsValidWeatherSnapshot() {
        val snapshot =
            KiyoriWeatherSnapshot(
                city = "Shanghai",
                temperatureCelsius = 28.5,
                visual = KiyoriWeatherVisual.PARTLY_CLOUDY,
                observedAtEpochMillis = 900_000L,
            )

        val decoded =
            decodeKiyoriWeatherSnapshot(
                json = encodeKiyoriWeatherSnapshot(snapshot),
                nowEpochMillis = 1_000_000L,
            )

        assertEquals(snapshot, decoded)
    }

    @Test
    fun rejectsCorruptFutureExpiredAndInvalidWeatherSnapshots() {
        val now = 10_000_000L
        assertNull(decodeKiyoriWeatherSnapshot("not-json", now))
        assertNull(
            decodeKiyoriWeatherSnapshot(
                """
                {
                  "schemaVersion": 1,
                  "city": "Shanghai",
                  "temperatureCelsius": 25.0,
                  "visual": "CLEAR",
                  "observedAtEpochMillis": ${now + 1}
                }
                """.trimIndent(),
                now,
            ),
        )
        assertNull(
            decodeKiyoriWeatherSnapshot(
                encodeKiyoriWeatherSnapshot(
                    KiyoriWeatherSnapshot(
                        city = "Shanghai",
                        temperatureCelsius = 25.0,
                        visual = KiyoriWeatherVisual.CLEAR,
                        observedAtEpochMillis = now - 1_001L,
                    ),
                ),
                nowEpochMillis = now,
                maxAgeMillis = 1_000L,
            ),
        )
        assertNull(
            decodeKiyoriWeatherSnapshot(
                """
                {
                  "schemaVersion": 1,
                  "city": "",
                  "temperatureCelsius": 25.0,
                  "visual": "UNKNOWN",
                  "observedAtEpochMillis": $now
                }
                """.trimIndent(),
                now,
            ),
        )
        assertNull(
            decodeKiyoriWeatherSnapshot(
                """
                {
                  "schemaVersion": 1,
                  "city": "Shanghai",
                  "temperatureCelsius": 1000.0,
                  "visual": "CLEAR",
                  "observedAtEpochMillis": $now
                }
                """.trimIndent(),
                now,
            ),
        )
    }

    @Test
    fun selectsNewestValidRecentLastKnownLocation() {
        val now = 2_000_000L
        val selected =
            selectRecentKiyoriWeatherLocation(
                candidates =
                    listOf(
                        locationCandidate(
                            provider = "network",
                            latitude = 31.2,
                            longitude = 121.5,
                            observedAtEpochMillis = now - 30_000L,
                        ),
                        locationCandidate(
                            provider = "gps",
                            latitude = 31.21,
                            longitude = 121.51,
                            observedAtEpochMillis = now - 5_000L,
                        ),
                        locationCandidate(
                            provider = "stale",
                            latitude = 31.22,
                            longitude = 121.52,
                            observedAtEpochMillis =
                                now - KIYORI_WEATHER_LAST_KNOWN_MAX_AGE_MILLIS - 1L,
                        ),
                        locationCandidate(
                            provider = "invalid",
                            latitude = 95.0,
                            longitude = 121.5,
                            observedAtEpochMillis = now,
                        ),
                    ),
                nowEpochMillis = now,
            )

        assertEquals("gps", selected?.provider)
    }

    @Test
    fun keepsAvailableWeatherVisibleAcrossRefreshFailure() {
        val available =
            KiyoriWeatherState.Available(
                city = "Shanghai",
                temperatureCelsius = 25.0,
                visual = KiyoriWeatherVisual.CLEAR,
                observedAtEpochMillis = 123L,
                isRefreshing = false,
            )

        val refreshing = available.beginRefresh()
        assertTrue(refreshing is KiyoriWeatherState.Available)
        assertTrue((refreshing as KiyoriWeatherState.Available).isRefreshing)

        val failed = refreshing.finishRefreshFailure(KiyoriWeatherFailure.NETWORK_UNAVAILABLE)
        assertEquals(available, failed)
        assertEquals(
            KiyoriWeatherState.Unavailable(KiyoriWeatherFailure.NETWORK_UNAVAILABLE),
            KiyoriWeatherState.Loading.finishRefreshFailure(
                KiyoriWeatherFailure.NETWORK_UNAVAILABLE,
            ),
        )
    }

    private fun locationCandidate(
        provider: String,
        latitude: Double,
        longitude: Double,
        observedAtEpochMillis: Long,
    ): KiyoriWeatherLocationCandidate =
        KiyoriWeatherLocationCandidate(
            provider = provider,
            coordinates =
                KiyoriWeatherCoordinates(
                    latitude = latitude,
                    longitude = longitude,
                ),
            observedAtEpochMillis = observedAtEpochMillis,
        )
}
