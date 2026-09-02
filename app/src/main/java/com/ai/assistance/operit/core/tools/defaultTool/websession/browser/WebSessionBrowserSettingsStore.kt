package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal data class WebSessionBrowserSettings(
    val homeUrl: String = DEFAULT_BROWSER_HOME_URL,
    val returnWithoutReloadEnabled: Boolean = false,
    val forcePageZoomEnabled: Boolean = false,
    val webTextZoomPercent: Int = DEFAULT_WEB_TEXT_ZOOM_PERCENT,
    val allowWebPageOpenApp: Boolean = true,
    val allowWebPageGeolocation: Boolean = true,
    val websitePasswordSavingEnabled: Boolean = false,
    val cookieReaderEnabled: Boolean = true,
    val showMediaCandidateBadge: Boolean = true,
    val automaticFloatingPlaybackEnabled: Boolean = true,
    val automaticFloatingMinimumDurationMillis: Long =
        DEFAULT_AUTOMATIC_FLOATING_MINIMUM_DURATION_MILLIS,
    val webElementLongPressMenuEnabled: Boolean = true,
    val swipeHistoryNavigationEnabled: Boolean = false,
    val searchEngineQuickSwitchBarEnabled: Boolean = true,
    val restoreLastSearchResultEnabled: Boolean = false,
    val askBeforeRestoringPagesEnabled: Boolean = false,
    val retainMultipleWindowsEnabled: Boolean = false,
    val userAgentMode: WebSessionUserAgentMode = WebSessionUserAgentMode.ANDROID,
    val customGlobalUserAgent: String = "",
    val siteUserAgentRules: List<WebSessionSiteUserAgentRule> = emptyList(),
    val siteSettingsRules: List<WebSessionSiteSettingsRule> = emptyList(),
)

internal val FRESH_INSTALL_BROWSER_SETTINGS =
    WebSessionBrowserSettings(
        homeUrl = INITIAL_BROWSER_HOME_URL,
        returnWithoutReloadEnabled = true,
        forcePageZoomEnabled = true,
        websitePasswordSavingEnabled = true,
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
        preferences.edit { putString(KEY_HOME_URL, url) }
        _state.value = _state.value.copy(homeUrl = url)
    }

    fun setReturnWithoutReloadEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_RETURN_WITHOUT_RELOAD, enabled) }
        _state.value = _state.value.copy(returnWithoutReloadEnabled = enabled)
    }

    fun setForcePageZoomEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_FORCE_PAGE_ZOOM, enabled) }
        _state.value = _state.value.copy(forcePageZoomEnabled = enabled)
    }

    fun setWebTextZoomPercent(percent: Int) {
        require(isSupportedWebTextZoomPercent(percent)) {
            "Unsupported web text zoom percent: $percent"
        }
        preferences.edit { putInt(KEY_WEB_TEXT_ZOOM_PERCENT, percent) }
        _state.value = _state.value.copy(webTextZoomPercent = percent)
    }

    fun setAllowWebPageOpenApp(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ALLOW_WEB_PAGE_OPEN_APP, enabled) }
        _state.value = _state.value.copy(allowWebPageOpenApp = enabled)
    }

    fun setAllowWebPageGeolocation(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ALLOW_WEB_PAGE_GEOLOCATION, enabled) }
        _state.value = _state.value.copy(allowWebPageGeolocation = enabled)
    }

    fun setWebsitePasswordSavingEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_WEBSITE_PASSWORD_SAVING, enabled) }
        _state.value = _state.value.copy(websitePasswordSavingEnabled = enabled)
    }

    fun setCookieReaderEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_COOKIE_READER_ENABLED, enabled) }
        _state.value = _state.value.copy(cookieReaderEnabled = enabled)
    }

    fun setShowMediaCandidateBadge(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_MEDIA_CANDIDATE_BADGE, enabled) }
        _state.value = _state.value.copy(showMediaCandidateBadge = enabled)
    }

    fun setAutomaticFloatingPlaybackEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTOMATIC_FLOATING_PLAYBACK, enabled) }
        _state.value = _state.value.copy(automaticFloatingPlaybackEnabled = enabled)
    }

    fun setAutomaticFloatingMinimumDurationMillis(durationMillis: Long) {
        require(isSupportedAutomaticFloatingMinimumDuration(durationMillis)) {
            "Unsupported automatic floating minimum duration: $durationMillis"
        }
        preferences.edit {
            putLong(KEY_AUTOMATIC_FLOATING_MINIMUM_DURATION, durationMillis)
        }
        _state.value =
            _state.value.copy(automaticFloatingMinimumDurationMillis = durationMillis)
    }

    fun setWebElementLongPressMenuEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_WEB_ELEMENT_LONG_PRESS_MENU, enabled) }
        _state.value = _state.value.copy(webElementLongPressMenuEnabled = enabled)
    }

    fun setSwipeHistoryNavigationEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SWIPE_HISTORY_NAVIGATION, enabled) }
        _state.value = _state.value.copy(swipeHistoryNavigationEnabled = enabled)
    }

    fun setSearchEngineQuickSwitchBarEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SEARCH_ENGINE_QUICK_SWITCH_BAR, enabled) }
        _state.value = _state.value.copy(searchEngineQuickSwitchBarEnabled = enabled)
    }

    fun setRestoreLastSearchResultEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_RESTORE_LAST_SEARCH_RESULT, enabled) }
        _state.value = _state.value.copy(restoreLastSearchResultEnabled = enabled)
    }

    fun setAskBeforeRestoringPagesEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ASK_BEFORE_RESTORING_PAGES, enabled) }
        _state.value = _state.value.copy(askBeforeRestoringPagesEnabled = enabled)
    }

    fun setRetainMultipleWindowsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_RETAIN_MULTIPLE_WINDOWS, enabled) }
        _state.value = _state.value.copy(retainMultipleWindowsEnabled = enabled)
    }

    fun setUserAgentMode(mode: WebSessionUserAgentMode) {
        if (mode == WebSessionUserAgentMode.CUSTOM_GLOBAL) {
            require(_state.value.customGlobalUserAgent.isNotBlank()) {
                "Custom global user-agent must be saved before selecting its mode"
            }
        }
        preferences.edit { putString(KEY_USER_AGENT_MODE, mode.persistedId) }
        _state.value = _state.value.copy(userAgentMode = mode)
    }

    fun setCustomGlobalUserAgentAndSelect(userAgent: String) {
        val normalized = userAgent.trim()
        require(normalized.isNotEmpty()) { "Custom global user-agent must not be blank" }
        preferences.edit {
            putString(KEY_CUSTOM_GLOBAL_USER_AGENT, normalized)
            putString(KEY_USER_AGENT_MODE, WebSessionUserAgentMode.CUSTOM_GLOBAL.persistedId)
        }
        _state.value =
            _state.value.copy(
                userAgentMode = WebSessionUserAgentMode.CUSTOM_GLOBAL,
                customGlobalUserAgent = normalized,
            )
    }

    fun setSiteUserAgentRule(domain: String, userAgent: String) {
        val normalizedDomain = requireNotNull(normalizeWebSessionUserAgentDomain(domain)) {
            "Browser user-agent rule domain is invalid: $domain"
        }
        val normalizedUserAgent = userAgent.trim()
        require(normalizedUserAgent.isNotEmpty()) { "Site user-agent must not be blank" }
        val updated =
            _state.value.siteUserAgentRules
                .filterNot { it.domain == normalizedDomain }
                .plus(WebSessionSiteUserAgentRule(normalizedDomain, normalizedUserAgent))
                .sortedBy { it.domain }
        writeSiteUserAgentRules(updated)
    }

    fun removeSiteUserAgentRule(domain: String) {
        val normalizedDomain = requireNotNull(normalizeWebSessionUserAgentDomain(domain)) {
            "Browser user-agent rule domain is invalid: $domain"
        }
        val updated = _state.value.siteUserAgentRules.filterNot { it.domain == normalizedDomain }
        writeSiteUserAgentRules(updated)
    }

    fun setSiteFeatureDisabled(
        domain: String,
        feature: WebSessionSiteFeature,
        disabled: Boolean,
    ) {
        writeSiteSettingsRules(
            updateWebSessionSiteSettingsRules(
                rules = _state.value.siteSettingsRules,
                domainOrUrl = domain,
                feature = feature,
                disabled = disabled,
            ),
        )
    }

    fun clearSiteSettings(domain: String) {
        val normalizedDomain =
            requireNotNull(normalizeWebSessionSiteSettingsDomain(domain)) {
                "Browser site settings domain is invalid: $domain"
            }
        writeSiteSettingsRules(
            _state.value.siteSettingsRules.filterNot { rule ->
                rule.domain == normalizedDomain
            },
        )
    }

    private fun readSettings(): WebSessionBrowserSettings {
        val userAgentMode =
            WebSessionUserAgentMode.fromPersistedId(
                requireNotNull(
                    preferences.getString(
                        KEY_USER_AGENT_MODE,
                        WebSessionUserAgentMode.ANDROID.persistedId,
                    ),
                ) { "Browser user-agent mode preference must not be null" },
            )
        val customGlobalUserAgent =
            requireNotNull(preferences.getString(KEY_CUSTOM_GLOBAL_USER_AGENT, "")) {
                "Custom global user-agent preference must not be null"
            }
        if (userAgentMode == WebSessionUserAgentMode.CUSTOM_GLOBAL) {
            require(customGlobalUserAgent.isNotBlank()) {
                "Selected custom global user-agent must not be blank"
            }
        }
        return WebSessionBrowserSettings(
            homeUrl =
                requireNotNull(
                    preferences.getString(KEY_HOME_URL, FRESH_INSTALL_BROWSER_SETTINGS.homeUrl),
                ) {
                    "Browser home URL preference must not be null"
                },
            returnWithoutReloadEnabled =
                preferences.getBoolean(
                    KEY_RETURN_WITHOUT_RELOAD,
                    FRESH_INSTALL_BROWSER_SETTINGS.returnWithoutReloadEnabled,
                ),
            forcePageZoomEnabled =
                preferences.getBoolean(
                    KEY_FORCE_PAGE_ZOOM,
                    FRESH_INSTALL_BROWSER_SETTINGS.forcePageZoomEnabled,
                ),
            webTextZoomPercent =
                preferences
                    .getInt(KEY_WEB_TEXT_ZOOM_PERCENT, DEFAULT_WEB_TEXT_ZOOM_PERCENT)
                    .also { percent ->
                        require(isSupportedWebTextZoomPercent(percent)) {
                            "Invalid web text zoom percent: $percent"
                        }
                    },
            allowWebPageOpenApp = preferences.getBoolean(KEY_ALLOW_WEB_PAGE_OPEN_APP, true),
            allowWebPageGeolocation =
                preferences.getBoolean(KEY_ALLOW_WEB_PAGE_GEOLOCATION, true),
            websitePasswordSavingEnabled =
                preferences.getBoolean(
                    KEY_WEBSITE_PASSWORD_SAVING,
                    FRESH_INSTALL_BROWSER_SETTINGS.websitePasswordSavingEnabled,
                ),
            cookieReaderEnabled =
                preferences.getBoolean(
                    KEY_COOKIE_READER_ENABLED,
                    FRESH_INSTALL_BROWSER_SETTINGS.cookieReaderEnabled,
                ),
            showMediaCandidateBadge =
                preferences.getBoolean(KEY_SHOW_MEDIA_CANDIDATE_BADGE, true),
            automaticFloatingPlaybackEnabled =
                preferences.getBoolean(KEY_AUTOMATIC_FLOATING_PLAYBACK, true),
            automaticFloatingMinimumDurationMillis =
                preferences
                    .getLong(
                        KEY_AUTOMATIC_FLOATING_MINIMUM_DURATION,
                        DEFAULT_AUTOMATIC_FLOATING_MINIMUM_DURATION_MILLIS,
                    )
                    .also { durationMillis ->
                        require(isSupportedAutomaticFloatingMinimumDuration(durationMillis)) {
                            "Invalid automatic floating minimum duration: $durationMillis"
                        }
                    },
            webElementLongPressMenuEnabled =
                preferences.getBoolean(KEY_WEB_ELEMENT_LONG_PRESS_MENU, true),
            swipeHistoryNavigationEnabled =
                preferences.getBoolean(KEY_SWIPE_HISTORY_NAVIGATION, false),
            searchEngineQuickSwitchBarEnabled =
                preferences.getBoolean(KEY_SEARCH_ENGINE_QUICK_SWITCH_BAR, true),
            restoreLastSearchResultEnabled =
                preferences.getBoolean(KEY_RESTORE_LAST_SEARCH_RESULT, false),
            askBeforeRestoringPagesEnabled =
                preferences.getBoolean(KEY_ASK_BEFORE_RESTORING_PAGES, false),
            retainMultipleWindowsEnabled =
                preferences.getBoolean(KEY_RETAIN_MULTIPLE_WINDOWS, false),
            userAgentMode = userAgentMode,
            customGlobalUserAgent = customGlobalUserAgent,
            siteUserAgentRules = decodeSiteUserAgentRules(preferences.getString(KEY_SITE_USER_AGENTS, "")),
            siteSettingsRules =
                decodeWebSessionSiteSettingsRules(
                    preferences.getString(KEY_SITE_SETTINGS, ""),
                ),
        )
    }

    private fun writeSiteUserAgentRules(rules: List<WebSessionSiteUserAgentRule>) {
        val encoded = JSONObject()
        rules.forEach { rule -> encoded.put(rule.domain, rule.userAgent) }
        preferences.edit { putString(KEY_SITE_USER_AGENTS, encoded.toString()) }
        _state.value = _state.value.copy(siteUserAgentRules = rules)
    }

    private fun writeSiteSettingsRules(rules: List<WebSessionSiteSettingsRule>) {
        preferences.edit {
            putString(KEY_SITE_SETTINGS, encodeWebSessionSiteSettingsRules(rules))
        }
        _state.value = _state.value.copy(siteSettingsRules = rules)
    }

    private fun decodeSiteUserAgentRules(raw: String?): List<WebSessionSiteUserAgentRule> {
        val encoded = requireNotNull(raw) { "Site user-agent rules preference must not be null" }
        if (encoded.isBlank()) {
            return emptyList()
        }
        val json = JSONObject(encoded)
        val rules = json.keys().asSequence()
            .map { domain ->
                WebSessionSiteUserAgentRule(
                    domain =
                        requireNotNull(normalizeWebSessionUserAgentDomain(domain)) {
                            "Stored browser user-agent rule domain is invalid: $domain"
                        },
                    userAgent = json.getString(domain).trim().also {
                        require(it.isNotEmpty()) {
                            "Stored site user-agent must not be blank: $domain"
                        }
                    },
                )
            }
            .toList()
        require(rules.map { it.domain }.distinct().size == rules.size) {
            "Stored browser user-agent rules contain duplicate normalized domains"
        }
        return rules.sortedBy { it.domain }
    }

    companion object {
        private const val PREFERENCES_NAME = "web_session_browser_settings"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_RETURN_WITHOUT_RELOAD = "return_without_reload"
        private const val KEY_FORCE_PAGE_ZOOM = "force_page_zoom"
        private const val KEY_WEB_TEXT_ZOOM_PERCENT = "web_text_zoom_percent"
        private const val KEY_ALLOW_WEB_PAGE_OPEN_APP = "allow_web_page_open_app"
        private const val KEY_ALLOW_WEB_PAGE_GEOLOCATION = "allow_web_page_geolocation"
        private const val KEY_WEBSITE_PASSWORD_SAVING = "website_password_saving"
        private const val KEY_COOKIE_READER_ENABLED = "cookie_reader_enabled"
        private const val KEY_SHOW_MEDIA_CANDIDATE_BADGE = "show_media_candidate_badge"
        private const val KEY_AUTOMATIC_FLOATING_PLAYBACK = "automatic_floating_playback"
        private const val KEY_AUTOMATIC_FLOATING_MINIMUM_DURATION =
            "automatic_floating_minimum_duration"
        private const val KEY_WEB_ELEMENT_LONG_PRESS_MENU =
            "web_element_long_press_menu"
        private const val KEY_SWIPE_HISTORY_NAVIGATION = "swipe_history_navigation"
        private const val KEY_SEARCH_ENGINE_QUICK_SWITCH_BAR = "search_engine_quick_switch_bar"
        private const val KEY_RESTORE_LAST_SEARCH_RESULT = "restore_last_search_result"
        private const val KEY_ASK_BEFORE_RESTORING_PAGES = "ask_before_restoring_pages"
        private const val KEY_RETAIN_MULTIPLE_WINDOWS = "retain_multiple_windows"
        private const val KEY_USER_AGENT_MODE = "user_agent_mode"
        private const val KEY_CUSTOM_GLOBAL_USER_AGENT = "custom_global_user_agent"
        private const val KEY_SITE_USER_AGENTS = "site_user_agents"
        private const val KEY_SITE_SETTINGS = "site_settings"

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

// Keep the blank-page sentinel separate so "恢复为空白页" remains an explicit user action.
internal const val DEFAULT_BROWSER_HOME_URL = "about:blank"
internal const val INITIAL_BROWSER_HOME_URL = "https://go.itab.link"
internal const val DEFAULT_WEB_TEXT_ZOOM_PERCENT = 100
internal const val MIN_WEB_TEXT_ZOOM_PERCENT = 50
internal const val MAX_WEB_TEXT_ZOOM_PERCENT = 200
internal const val WEB_TEXT_ZOOM_STEP_PERCENT = 5
internal const val DEFAULT_AUTOMATIC_FLOATING_MINIMUM_DURATION_MILLIS = 60_000L
internal val AUTOMATIC_FLOATING_MINIMUM_DURATION_OPTIONS_MILLIS =
    listOf(
        30_000L,
        60_000L,
        3L * 60_000L,
        5L * 60_000L,
        10L * 60_000L,
        30L * 60_000L,
        60L * 60_000L,
    )

private const val MIN_AUTOMATIC_FLOATING_DURATION_MILLIS = 1_000L
private const val MAX_AUTOMATIC_FLOATING_DURATION_MILLIS = 86_400_000L

internal fun isSupportedBrowserHomeUrl(url: String): Boolean =
    url.equals(DEFAULT_BROWSER_HOME_URL, ignoreCase = true) ||
        url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)

internal fun isSupportedWebTextZoomPercent(percent: Int): Boolean =
    percent in MIN_WEB_TEXT_ZOOM_PERCENT..MAX_WEB_TEXT_ZOOM_PERCENT &&
        percent % WEB_TEXT_ZOOM_STEP_PERCENT == 0

internal fun formatWebTextZoomPercent(percent: Int): String {
    require(isSupportedWebTextZoomPercent(percent)) {
        "Unsupported web text zoom percent label: $percent"
    }
    return if (percent == DEFAULT_WEB_TEXT_ZOOM_PERCENT) {
        "默认 · $percent%"
    } else {
        "$percent%"
    }
}

internal fun isSupportedAutomaticFloatingMinimumDuration(durationMillis: Long): Boolean =
    durationMillis in
        MIN_AUTOMATIC_FLOATING_DURATION_MILLIS..MAX_AUTOMATIC_FLOATING_DURATION_MILLIS &&
        durationMillis % 1_000L == 0L

internal fun parseAutomaticFloatingDurationSeconds(input: String): Long? {
    val seconds = input.trim().toLongOrNull() ?: return null
    if (seconds !in 1L..86_400L) return null
    return seconds * 1_000L
}

internal fun formatAutomaticFloatingMinimumDuration(durationMillis: Long): String {
    require(isSupportedAutomaticFloatingMinimumDuration(durationMillis)) {
        "Unsupported automatic floating minimum duration label: $durationMillis"
    }
    val totalSeconds = durationMillis / 1_000L
    return when {
        totalSeconds < 60L -> "$totalSeconds 秒"
        totalSeconds % 60L == 0L -> "${totalSeconds / 60L} 分钟"
        else -> "${totalSeconds / 60L} 分 ${totalSeconds % 60L} 秒"
    }
}
