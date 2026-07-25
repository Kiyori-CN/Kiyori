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
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class KiyoriWeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val geocoder = Geocoder(appContext, Locale.getDefault())
    private val client =
        OkHttpClient.Builder()
            .callTimeout(WEATHER_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<KiyoriWeatherState>(KiyoriWeatherState.PermissionRequired)
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
                _state.value = KiyoriWeatherState.Loading
                try {
                    val location = readCurrentLocation()
                    val city = resolveCity(location)
                    val currentWeather = requestCurrentWeather(location)
                    lastSuccessfulRefreshAt = SystemClock.elapsedRealtime()
                    _state.value =
                        KiyoriWeatherState.Available(
                            city = city,
                            temperatureCelsius = currentWeather.temperatureCelsius,
                            visual = currentWeather.visual,
                        )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: KiyoriWeatherException) {
                    AppLogger.e(TAG, "Unable to refresh Kiyori Home weather", error)
                    _state.value = KiyoriWeatherState.Unavailable(error.reason)
                } catch (error: KiyoriWeatherResponseException) {
                    AppLogger.e(TAG, "Invalid Open-Meteo current-weather response", error)
                    _state.value = KiyoriWeatherState.Unavailable(KiyoriWeatherFailure.INVALID_RESPONSE)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Unexpected Kiyori Home weather failure", error)
                    _state.value = KiyoriWeatherState.Unavailable(KiyoriWeatherFailure.NETWORK_UNAVAILABLE)
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
    @Suppress("DEPRECATION")
    private suspend fun readCurrentLocation(): Location =
        suspendCancellableCoroutine { continuation ->
            if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                continuation.resumeWithException(
                    KiyoriWeatherException(KiyoriWeatherFailure.LOCATION_DISABLED),
                )
                return@suspendCancellableCoroutine
            }
            val provider =
                locationManager.getBestProvider(
                    android.location.Criteria().apply {
                        accuracy = android.location.Criteria.ACCURACY_COARSE
                    },
                    true,
                )
            if (provider == null) {
                continuation.resumeWithException(
                    KiyoriWeatherException(KiyoriWeatherFailure.LOCATION_UNAVAILABLE),
                )
                return@suspendCancellableCoroutine
            }

            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                if (!continuation.isActive) {
                    return@getCurrentLocation
                }
                if (location == null) {
                    continuation.resumeWithException(
                        KiyoriWeatherException(KiyoriWeatherFailure.LOCATION_UNAVAILABLE),
                    )
                } else {
                    continuation.resumeWith(Result.success(location))
                }
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveCity(location: Location): String =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) {
                throw KiyoriWeatherException(KiyoriWeatherFailure.CITY_UNAVAILABLE)
            }
            val address =
                try {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.singleOrNull()
                } catch (error: IOException) {
                    throw KiyoriWeatherException(KiyoriWeatherFailure.CITY_UNAVAILABLE, error)
                }
            val city = address?.locality?.trim()
            if (city.isNullOrBlank()) {
                throw KiyoriWeatherException(KiyoriWeatherFailure.CITY_UNAVAILABLE)
            }
            city
        }

    private suspend fun requestCurrentWeather(location: Location): KiyoriCurrentWeather =
        withContext(Dispatchers.IO) {
            val url =
                OPEN_METEO_FORECAST_URL.toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("latitude", location.latitude.toString())
                    .addQueryParameter("longitude", location.longitude.toString())
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

    private class KiyoriWeatherException(
        val reason: KiyoriWeatherFailure,
        cause: Throwable? = null,
    ) : Exception(reason.name, cause)

    companion object {
        private const val TAG = "KiyoriHomeWeather"
        private const val OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val WEATHER_REQUEST_TIMEOUT_SECONDS = 15L
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
