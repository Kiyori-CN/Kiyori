package com.ai.assistance.operit.ui.main.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.applyKiyoriNetworkProxy
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class KiyoriWeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val geocoder by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Geocoder(appContext, Locale.getDefault())
    }
    private val client by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .applyKiyoriNetworkProxy(KiyoriNetworkModule.APP_SERVICES)
            .callTimeout(WEATHER_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state =
        MutableStateFlow<KiyoriWeatherState>(
            if (hasLocationPermission()) {
                KiyoriWeatherState.Loading
            } else {
                KiyoriWeatherState.PermissionRequired
            },
        )
    val state: StateFlow<KiyoriWeatherState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var lastSuccessfulRefreshAt = 0L

    fun refresh(force: Boolean = false) {
        if (!hasLocationPermission()) {
            _state.value = KiyoriWeatherState.PermissionRequired
            return
        }
        if (refreshJob?.isActive == true) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (
            !force &&
                _state.value is KiyoriWeatherState.Available &&
                now - lastSuccessfulRefreshAt < REFRESH_INTERVAL_MILLIS
        ) {
            return
        }

        refreshJob =
            scope.launch {
                _state.value = _state.value.beginRefresh()
                if (_state.value !is KiyoriWeatherState.Available) {
                    readSnapshot()?.let { snapshot ->
                        _state.value = snapshot.toAvailableState(isRefreshing = true)
                    }
                }
                try {
                    val coordinates = readCoordinates()
                    val (city, currentWeather) =
                        coroutineScope {
                            val cityDeferred = async { resolveCity(coordinates) }
                            val weatherDeferred = async { requestCurrentWeather(coordinates) }
                            cityDeferred.await() to weatherDeferred.await()
                        }
                    val observedAtEpochMillis = System.currentTimeMillis()
                    val snapshot =
                        KiyoriWeatherSnapshot(
                            city = city,
                            temperatureCelsius = currentWeather.temperatureCelsius,
                            visual = currentWeather.visual,
                            observedAtEpochMillis = observedAtEpochMillis,
                        )
                    lastSuccessfulRefreshAt = SystemClock.elapsedRealtime()
                    _state.value = snapshot.toAvailableState(isRefreshing = false)
                    persistSnapshot(snapshot)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: KiyoriWeatherException) {
                    AppLogger.e(TAG, "Unable to refresh Kiyori Home weather", error)
                    _state.value = _state.value.finishRefreshFailure(error.reason)
                } catch (error: KiyoriWeatherResponseException) {
                    AppLogger.e(TAG, "Invalid Open-Meteo current-weather response", error)
                    _state.value =
                        _state.value.finishRefreshFailure(KiyoriWeatherFailure.INVALID_RESPONSE)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Unexpected Kiyori Home weather failure", error)
                    _state.value =
                        _state.value.finishRefreshFailure(KiyoriWeatherFailure.NETWORK_UNAVAILABLE)
                }
            }
    }

    fun recordPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            refresh(force = true)
        } else {
            _state.value = KiyoriWeatherState.PermissionDenied
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun readCoordinates(): KiyoriWeatherCoordinates {
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            throw KiyoriWeatherException(KiyoriWeatherFailure.LOCATION_DISABLED)
        }
        val nowEpochMillis = System.currentTimeMillis()
        val recentLocation =
            withContext(Dispatchers.IO) {
                val candidates =
                    try {
                        locationManager.getProviders(true).mapNotNull { provider ->
                            locationManager.getLastKnownLocation(provider)?.toCandidate(provider)
                        }
                    } catch (error: SecurityException) {
                        throw KiyoriWeatherException(
                            KiyoriWeatherFailure.LOCATION_UNAVAILABLE,
                            error,
                        )
                    }
                selectRecentKiyoriWeatherLocation(
                    candidates = candidates,
                    nowEpochMillis = nowEpochMillis,
                )
            }
        if (recentLocation != null) {
            return recentLocation.coordinates
        }
        return readCurrentLocation()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun readCurrentLocation(): KiyoriWeatherCoordinates {
        val provider =
            locationManager.getBestProvider(
                android.location.Criteria().apply {
                    accuracy = android.location.Criteria.ACCURACY_COARSE
                },
                true,
            )
                ?: throw KiyoriWeatherException(KiyoriWeatherFailure.LOCATION_UNAVAILABLE)

        val location =
            try {
                withTimeout(CURRENT_LOCATION_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine<Location> { continuation ->
                        val cancellationSignal = CancellationSignal()
                        continuation.invokeOnCancellation { cancellationSignal.cancel() }
                        LocationManagerCompat.getCurrentLocation(
                            locationManager,
                            provider,
                            cancellationSignal,
                            ContextCompat.getMainExecutor(appContext),
                        ) { currentLocation ->
                            if (!continuation.isActive) {
                                return@getCurrentLocation
                            }
                            if (currentLocation == null) {
                                continuation.resumeWithException(
                                    KiyoriWeatherException(
                                        KiyoriWeatherFailure.LOCATION_UNAVAILABLE,
                                    ),
                                )
                            } else {
                                continuation.resumeWith(Result.success(currentLocation))
                            }
                        }
                    }
                }
            } catch (error: TimeoutCancellationException) {
                throw KiyoriWeatherException(
                    KiyoriWeatherFailure.LOCATION_UNAVAILABLE,
                    error,
                )
            }
        val coordinates = location.toCoordinates()
        if (!isValidKiyoriWeatherCoordinates(coordinates)) {
            throw KiyoriWeatherException(KiyoriWeatherFailure.LOCATION_UNAVAILABLE)
        }
        return coordinates
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveCity(coordinates: KiyoriWeatherCoordinates): String =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                throw KiyoriWeatherException(KiyoriWeatherFailure.CITY_UNAVAILABLE)
            }
            val address =
                try {
                    geocoder
                        .getFromLocation(
                            coordinates.latitude,
                            coordinates.longitude,
                            1,
                        )
                        ?.singleOrNull()
                } catch (error: IOException) {
                    throw KiyoriWeatherException(KiyoriWeatherFailure.CITY_UNAVAILABLE, error)
                }
            val city = address?.locality?.trim()
            if (city.isNullOrBlank()) {
                throw KiyoriWeatherException(KiyoriWeatherFailure.CITY_UNAVAILABLE)
            }
            city
        }

    private suspend fun requestCurrentWeather(
        coordinates: KiyoriWeatherCoordinates,
    ): KiyoriCurrentWeather =
        withContext(Dispatchers.IO) {
            val url =
                OPEN_METEO_FORECAST_URL.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("latitude", coordinates.latitude.toString())
                    .addQueryParameter("longitude", coordinates.longitude.toString())
                    .addQueryParameter("current", "temperature_2m,weather_code")
                    .addQueryParameter("timezone", "auto")
                    .build()
            val request = Request.Builder().url(url).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw KiyoriWeatherException(KiyoriWeatherFailure.NETWORK_UNAVAILABLE)
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        throw KiyoriWeatherException(KiyoriWeatherFailure.INVALID_RESPONSE)
                    }
                    parseKiyoriCurrentWeather(body)
                }
            } catch (error: IOException) {
                throw KiyoriWeatherException(KiyoriWeatherFailure.NETWORK_UNAVAILABLE, error)
            }
        }

    private suspend fun readSnapshot(): KiyoriWeatherSnapshot? =
        withContext(Dispatchers.IO) {
            try {
                val json = preferences.getString(SNAPSHOT_KEY, null) ?: return@withContext null
                val snapshot =
                    decodeKiyoriWeatherSnapshot(
                        json = json,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                if (snapshot == null) {
                    AppLogger.w(TAG, "Discarding invalid Kiyori Home weather snapshot")
                    preferences.edit { remove(SNAPSHOT_KEY) }
                }
                snapshot
            } catch (error: CancellationException) {
                throw error
            } catch (error: RuntimeException) {
                AppLogger.e(TAG, "Unable to read Kiyori Home weather snapshot", error)
                null
            }
        }

    private suspend fun persistSnapshot(snapshot: KiyoriWeatherSnapshot) {
        withContext(Dispatchers.IO) {
            try {
                preferences.edit {
                    putString(SNAPSHOT_KEY, encodeKiyoriWeatherSnapshot(snapshot))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: RuntimeException) {
                AppLogger.e(TAG, "Unable to persist Kiyori Home weather snapshot", error)
            }
        }
    }

    private fun Location.toCoordinates(): KiyoriWeatherCoordinates =
        KiyoriWeatherCoordinates(
            latitude = latitude,
            longitude = longitude,
        )

    private fun Location.toCandidate(providerName: String): KiyoriWeatherLocationCandidate =
        KiyoriWeatherLocationCandidate(
            provider = providerName,
            coordinates = toCoordinates(),
            observedAtEpochMillis = time,
        )

    private class KiyoriWeatherException(
        val reason: KiyoriWeatherFailure,
        cause: Throwable? = null,
    ) : Exception(reason.name, cause)

    companion object {
        private const val TAG = "KiyoriHomeWeather"
        private const val OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val PREFERENCES_NAME = "kiyori_home_weather"
        private const val SNAPSHOT_KEY = "latest_success"
        private const val WEATHER_REQUEST_TIMEOUT_SECONDS = 15L
        private const val CURRENT_LOCATION_TIMEOUT_MILLIS = 8_000L
        internal const val REFRESH_INTERVAL_MILLIS = 30L * 60L * 1_000L

        @Volatile private var instance: KiyoriWeatherRepository? = null

        fun getInstance(context: Context): KiyoriWeatherRepository =
            instance ?: synchronized(this) {
                instance
                    ?: KiyoriWeatherRepository(context.applicationContext).also { repository ->
                        instance = repository
                    }
            }
    }
}
