package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal data class WebSessionBrowserSettings(
    val homeUrl: String = DEFAULT_BROWSER_HOME_URL,
    val allowWebPageOpenApp: Boolean = true,
    val allowWebPageGeolocation: Boolean = true,
    val showMediaCandidateBadge: Boolean = true,
    val automaticFloatingPlaybackEnabled: Boolean = true,
    val userAgentMode: WebSessionUserAgentMode = WebSessionUserAgentMode.ANDROID,
    val customGlobalUserAgent: String = "",
    val siteUserAgentRules: List<WebSessionSiteUserAgentRule> = emptyList(),
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

    fun setShowMediaCandidateBadge(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_MEDIA_CANDIDATE_BADGE, enabled).apply()
        _state.value = _state.value.copy(showMediaCandidateBadge = enabled)
    }

    fun setAutomaticFloatingPlaybackEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOMATIC_FLOATING_PLAYBACK, enabled).apply()
        _state.value = _state.value.copy(automaticFloatingPlaybackEnabled = enabled)
    }

    fun setUserAgentMode(mode: WebSessionUserAgentMode) {
        if (mode == WebSessionUserAgentMode.CUSTOM_GLOBAL) {
            require(_state.value.customGlobalUserAgent.isNotBlank()) {
                "Custom global user-agent must be saved before selecting its mode"
            }
        }
        preferences.edit().putString(KEY_USER_AGENT_MODE, mode.persistedId).apply()
        _state.value = _state.value.copy(userAgentMode = mode)
    }

    fun setCustomGlobalUserAgentAndSelect(userAgent: String) {
        val normalized = userAgent.trim()
        require(normalized.isNotEmpty()) { "Custom global user-agent must not be blank" }
        preferences.edit()
            .putString(KEY_CUSTOM_GLOBAL_USER_AGENT, normalized)
            .putString(KEY_USER_AGENT_MODE, WebSessionUserAgentMode.CUSTOM_GLOBAL.persistedId)
            .apply()
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
                requireNotNull(preferences.getString(KEY_HOME_URL, DEFAULT_BROWSER_HOME_URL)) {
                    "Browser home URL preference must not be null"
                },
            allowWebPageOpenApp = preferences.getBoolean(KEY_ALLOW_WEB_PAGE_OPEN_APP, true),
            allowWebPageGeolocation =
                preferences.getBoolean(KEY_ALLOW_WEB_PAGE_GEOLOCATION, true),
            showMediaCandidateBadge =
                preferences.getBoolean(KEY_SHOW_MEDIA_CANDIDATE_BADGE, true),
            automaticFloatingPlaybackEnabled =
                preferences.getBoolean(KEY_AUTOMATIC_FLOATING_PLAYBACK, true),
            userAgentMode = userAgentMode,
            customGlobalUserAgent = customGlobalUserAgent,
            siteUserAgentRules = decodeSiteUserAgentRules(preferences.getString(KEY_SITE_USER_AGENTS, "")),
        )
    }

    private fun writeSiteUserAgentRules(rules: List<WebSessionSiteUserAgentRule>) {
        val encoded = JSONObject()
        rules.forEach { rule -> encoded.put(rule.domain, rule.userAgent) }
        preferences.edit().putString(KEY_SITE_USER_AGENTS, encoded.toString()).apply()
        _state.value = _state.value.copy(siteUserAgentRules = rules)
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
        private const val KEY_ALLOW_WEB_PAGE_OPEN_APP = "allow_web_page_open_app"
        private const val KEY_ALLOW_WEB_PAGE_GEOLOCATION = "allow_web_page_geolocation"
        private const val KEY_SHOW_MEDIA_CANDIDATE_BADGE = "show_media_candidate_badge"
        private const val KEY_AUTOMATIC_FLOATING_PLAYBACK = "automatic_floating_playback"
        private const val KEY_USER_AGENT_MODE = "user_agent_mode"
        private const val KEY_CUSTOM_GLOBAL_USER_AGENT = "custom_global_user_agent"
        private const val KEY_SITE_USER_AGENTS = "site_user_agents"

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
