package com.ai.assistance.operit.ui.main.weather

import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal const val KIYORI_WEATHER_SNAPSHOT_SCHEMA_VERSION = 1
internal const val KIYORI_WEATHER_SNAPSHOT_MAX_AGE_MILLIS = 6L * 60L * 60L * 1_000L
internal const val KIYORI_WEATHER_LAST_KNOWN_MAX_AGE_MILLIS = 30L * 60L * 1_000L
private const val KIYORI_WEATHER_MIN_TEMPERATURE_CELSIUS = -100.0
private const val KIYORI_WEATHER_MAX_TEMPERATURE_CELSIUS = 70.0

internal sealed interface KiyoriWeatherState {
    data object PermissionRequired : KiyoriWeatherState

    data object PermissionDenied : KiyoriWeatherState

    data object Loading : KiyoriWeatherState

    data class Available(
        val city: String,
        val temperatureCelsius: Double,
        val visual: KiyoriWeatherVisual,
        val observedAtEpochMillis: Long,
        val isRefreshing: Boolean,
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

internal data class KiyoriWeatherSnapshot(
    val city: String,
    val temperatureCelsius: Double,
    val visual: KiyoriWeatherVisual,
    val observedAtEpochMillis: Long,
)

internal data class KiyoriWeatherCoordinates(
    val latitude: Double,
    val longitude: Double,
)

internal data class KiyoriWeatherLocationCandidate(
    val provider: String,
    val coordinates: KiyoriWeatherCoordinates,
    val observedAtEpochMillis: Long,
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
        val temperatureCelsius = current.get("temperature_2m").asDouble
        if (!isValidKiyoriWeatherTemperature(temperatureCelsius)) {
            throw KiyoriWeatherResponseException(
                "Open-Meteo returned an invalid Celsius temperature",
            )
        }
        KiyoriCurrentWeather(
            temperatureCelsius = temperatureCelsius,
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

internal fun KiyoriWeatherState.beginRefresh(): KiyoriWeatherState =
    when (this) {
        is KiyoriWeatherState.Available -> copy(isRefreshing = true)
        else -> KiyoriWeatherState.Loading
    }

internal fun KiyoriWeatherState.finishRefreshFailure(
    reason: KiyoriWeatherFailure,
): KiyoriWeatherState =
    when (this) {
        is KiyoriWeatherState.Available -> copy(isRefreshing = false)
        else -> KiyoriWeatherState.Unavailable(reason)
    }

internal fun KiyoriWeatherSnapshot.toAvailableState(
    isRefreshing: Boolean,
): KiyoriWeatherState.Available =
    KiyoriWeatherState.Available(
        city = city,
        temperatureCelsius = temperatureCelsius,
        visual = visual,
        observedAtEpochMillis = observedAtEpochMillis,
        isRefreshing = isRefreshing,
    )

internal fun encodeKiyoriWeatherSnapshot(snapshot: KiyoriWeatherSnapshot): String {
    require(snapshot.city.isNotBlank()) { "Weather snapshot city cannot be blank." }
    require(isValidKiyoriWeatherTemperature(snapshot.temperatureCelsius)) {
        "Weather snapshot temperature is invalid."
    }
    require(snapshot.observedAtEpochMillis > 0L) {
        "Weather snapshot observation time must be positive."
    }
    return JsonObject()
        .apply {
            addProperty("schemaVersion", KIYORI_WEATHER_SNAPSHOT_SCHEMA_VERSION)
            addProperty("city", snapshot.city.trim())
            addProperty("temperatureCelsius", snapshot.temperatureCelsius)
            addProperty("visual", snapshot.visual.name)
            addProperty("observedAtEpochMillis", snapshot.observedAtEpochMillis)
        }
        .toString()
}

internal fun decodeKiyoriWeatherSnapshot(
    json: String,
    nowEpochMillis: Long,
    maxAgeMillis: Long = KIYORI_WEATHER_SNAPSHOT_MAX_AGE_MILLIS,
): KiyoriWeatherSnapshot? {
    if (json.isBlank() || nowEpochMillis <= 0L || maxAgeMillis <= 0L) {
        return null
    }
    return try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("schemaVersion")?.asInt != KIYORI_WEATHER_SNAPSHOT_SCHEMA_VERSION) {
            return null
        }
        val city = root.get("city")?.asString?.trim().orEmpty()
        val temperatureCelsius = root.get("temperatureCelsius")?.asDouble ?: return null
        val visualName = root.get("visual")?.asString ?: return null
        val visual =
            try {
                KiyoriWeatherVisual.valueOf(visualName)
            } catch (_: IllegalArgumentException) {
                return null
            }
        val observedAtEpochMillis =
            root.get("observedAtEpochMillis")?.asLong ?: return null
        if (
            city.isBlank() ||
                !isValidKiyoriWeatherTemperature(temperatureCelsius) ||
                observedAtEpochMillis <= 0L ||
                observedAtEpochMillis > nowEpochMillis ||
                nowEpochMillis - observedAtEpochMillis > maxAgeMillis
        ) {
            return null
        }
        KiyoriWeatherSnapshot(
            city = city,
            temperatureCelsius = temperatureCelsius,
            visual = visual,
            observedAtEpochMillis = observedAtEpochMillis,
        )
    } catch (_: RuntimeException) {
        null
    }
}

internal fun selectRecentKiyoriWeatherLocation(
    candidates: List<KiyoriWeatherLocationCandidate>,
    nowEpochMillis: Long,
    maxAgeMillis: Long = KIYORI_WEATHER_LAST_KNOWN_MAX_AGE_MILLIS,
): KiyoriWeatherLocationCandidate? {
    if (nowEpochMillis <= 0L || maxAgeMillis <= 0L) {
        return null
    }
    return candidates
        .asSequence()
        .filter { candidate ->
            candidate.provider.isNotBlank() &&
                isValidKiyoriWeatherCoordinates(candidate.coordinates) &&
                candidate.observedAtEpochMillis > 0L &&
                candidate.observedAtEpochMillis <= nowEpochMillis &&
                nowEpochMillis - candidate.observedAtEpochMillis <= maxAgeMillis
        }
        .maxByOrNull(KiyoriWeatherLocationCandidate::observedAtEpochMillis)
}

internal fun isValidKiyoriWeatherCoordinates(
    coordinates: KiyoriWeatherCoordinates,
): Boolean =
    coordinates.latitude.isFinite() &&
        coordinates.longitude.isFinite() &&
        coordinates.latitude in -90.0..90.0 &&
        coordinates.longitude in -180.0..180.0

private fun isValidKiyoriWeatherTemperature(temperatureCelsius: Double): Boolean =
    temperatureCelsius.isFinite() &&
        temperatureCelsius in
            KIYORI_WEATHER_MIN_TEMPERATURE_CELSIUS..KIYORI_WEATHER_MAX_TEMPERATURE_CELSIUS
