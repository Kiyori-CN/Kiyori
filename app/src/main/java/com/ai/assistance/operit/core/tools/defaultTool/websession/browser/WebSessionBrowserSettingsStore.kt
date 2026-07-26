package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class WebSessionBrowserSettings(
    val homeUrl: String = DEFAULT_BROWSER_HOME_URL,
    val allowWebPageOpenApp: Boolean = true,
    val allowWebPageGeolocation: Boolean = true,
    val floatingSniffPlaybackEnabled: Boolean = true,
)

internal class WebSessionBrowserSettingsStore private constructor(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    private val _state = MutableStateFlow(readSettings())

    val state: StateFlow<WebSessionBrowserSettings> = _state.asStateFlow()
    val current: WebSessionBrowserSettings
        get() = _state.value

    fun setHomeUrl(url: String) {
        require(isSupportedBrowserHomeUrl(url)) { "Unsupported browser home URL: $url" }
        preferences.edit().putString(KEY_HOME_URL, url).apply()
        _state.value = _state.value.copy(homeUrl = url)
    }

    fun setAllowWebPageOpenApp(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ALLOW_WEB_PAGE_OPEN_APP, enabled).apply()
        _state.value = _state.value.copy(allowWebPageOpenApp = enabled)
    }

    fun setAllowWebPageGeolocation(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ALLOW_WEB_PAGE_GEOLOCATION, enabled).apply()
        _state.value = _state.value.copy(allowWebPageGeolocation = enabled)
    }

    fun setFloatingSniffPlaybackEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FLOATING_SNIFF_PLAYBACK, enabled).apply()
        _state.value = _state.value.copy(floatingSniffPlaybackEnabled = enabled)
    }

    private fun readSettings(): WebSessionBrowserSettings =
        WebSessionBrowserSettings(
            homeUrl =
                requireNotNull(preferences.getString(KEY_HOME_URL, DEFAULT_BROWSER_HOME_URL)) {
                    "Browser home URL preference must not be null"
                },
            allowWebPageOpenApp = preferences.getBoolean(KEY_ALLOW_WEB_PAGE_OPEN_APP, true),
            allowWebPageGeolocation =
                preferences.getBoolean(KEY_ALLOW_WEB_PAGE_GEOLOCATION, true),
            floatingSniffPlaybackEnabled =
                preferences.getBoolean(KEY_FLOATING_SNIFF_PLAYBACK, true),
        )

    companion object {
        private const val PREFERENCES_NAME = "web_session_browser_settings"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_ALLOW_WEB_PAGE_OPEN_APP = "allow_web_page_open_app"
        private const val KEY_ALLOW_WEB_PAGE_GEOLOCATION = "allow_web_page_geolocation"
        private const val KEY_FLOATING_SNIFF_PLAYBACK = "floating_sniff_playback"

        @Volatile private var instance: WebSessionBrowserSettingsStore? = null

        fun getInstance(context: Context): WebSessionBrowserSettingsStore =
            instance ?: synchronized(this) {
                instance
                    ?: WebSessionBrowserSettingsStore(context.applicationContext).also { store ->
                        instance = store
                    }
            }
    }
}

internal const val DEFAULT_BROWSER_HOME_URL = "about:blank"

internal fun isSupportedBrowserHomeUrl(url: String): Boolean =
    url.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true) ||
        url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
