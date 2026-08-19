package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal enum class WebSessionSiteFeature(
    val persistedId: String,
) {
    USER_SCRIPTS("user_scripts"),
    RETURN_WITHOUT_RELOAD("return_without_reload"),
    SWIPE_HISTORY_NAVIGATION("swipe_history_navigation"),
    FORCE_PAGE_ZOOM("force_page_zoom"),
    WEB_ELEMENT_LONG_PRESS_MENU("web_element_long_press_menu"),
    WEB_PAGE_OPEN_APP("web_page_open_app"),
    WEB_PAGE_GEOLOCATION("web_page_geolocation"),
    WEBSITE_PASSWORD_SAVING("website_password_saving"),
    MEDIA_CANDIDATE_BADGE("media_candidate_badge"),
    AUTOMATIC_FLOATING_PLAYBACK("automatic_floating_playback"),
    ;

    companion object {
        fun fromPersistedId(id: String): WebSessionSiteFeature =
            entries.singleOrNull { feature -> feature.persistedId == id }
                ?: error("Unsupported browser site feature ID: $id")
    }
}

internal data class WebSessionSiteSettingsRule(
    val domain: String,
    val disabledFeatures: Set<WebSessionSiteFeature>,
)

internal fun normalizeWebSessionSiteSettingsDomain(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return null
    }
    val uriText = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return try {
        val uri = URI(uriText)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return null
        }
        val authority = uri.rawAuthority?.substringAfterLast('@') ?: return null
        if (authority.startsWith('[')) {
            return null
        }
        val portSeparator = authority.lastIndexOf(':')
        val host =
            if (portSeparator >= 0) {
                val portText = authority.substring(portSeparator + 1)
                if (portText.isBlank() || portText.any { character -> !character.isDigit() }) {
                    return null
                }
                authority.substring(0, portSeparator)
            } else {
                authority
            }.trim().trim('.')
        IDN.toASCII(host)
            .lowercase(Locale.ROOT)
            .takeIf { normalized ->
                normalized.isNotBlank() &&
                    normalized.length <= 253 &&
                    normalized
                        .split('.')
                        .all { label ->
                            label.isNotBlank() &&
                                label.length <= 63 &&
                                label.first() != '-' &&
                                label.last() != '-' &&
                                label.all { character ->
                                    character.isLetterOrDigit() || character == '-'
                                }
                        }
            }
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun WebSessionBrowserSettings.siteSettingsRule(
    domainOrUrl: String,
): WebSessionSiteSettingsRule? {
    val domain = normalizeWebSessionSiteSettingsDomain(domainOrUrl) ?: return null
    return siteSettingsRules.singleOrNull { rule -> rule.domain == domain }
}

internal fun WebSessionBrowserSettings.isSiteFeatureDisabled(
    domainOrUrl: String,
    feature: WebSessionSiteFeature,
): Boolean = siteSettingsRule(domainOrUrl)?.disabledFeatures?.contains(feature) == true

internal fun resolveWebSessionSiteFeatureEnabled(
    settings: WebSessionBrowserSettings,
    domainOrUrl: String,
    feature: WebSessionSiteFeature,
    globalEnabled: Boolean,
): Boolean = globalEnabled && !settings.isSiteFeatureDisabled(domainOrUrl, feature)

internal fun updateWebSessionSiteSettingsRules(
    rules: List<WebSessionSiteSettingsRule>,
    domainOrUrl: String,
    feature: WebSessionSiteFeature,
    disabled: Boolean,
): List<WebSessionSiteSettingsRule> {
    val domain =
        requireNotNull(normalizeWebSessionSiteSettingsDomain(domainOrUrl)) {
            "Browser site settings domain is invalid: $domainOrUrl"
        }
    val currentRule = rules.singleOrNull { rule -> rule.domain == domain }
    val updatedFeatures =
        if (disabled) {
            currentRule?.disabledFeatures.orEmpty() + feature
        } else {
            currentRule?.disabledFeatures.orEmpty() - feature
        }
    return rules
        .filterNot { rule -> rule.domain == domain }
        .let { remaining ->
            if (updatedFeatures.isEmpty()) {
                remaining
            } else {
                remaining +
                    WebSessionSiteSettingsRule(
                        domain = domain,
                        disabledFeatures = updatedFeatures,
                    )
            }
        }
        .sortedBy(WebSessionSiteSettingsRule::domain)
}

internal fun encodeWebSessionSiteSettingsRules(
    rules: List<WebSessionSiteSettingsRule>,
): String =
    JSONObject().apply {
        rules
            .sortedBy(WebSessionSiteSettingsRule::domain)
            .forEach { rule ->
                require(rule.disabledFeatures.isNotEmpty()) {
                    "Empty browser site settings rule must not be persisted: ${rule.domain}"
                }
                put(
                    rule.domain,
                    JSONArray().apply {
                        rule.disabledFeatures
                            .sortedBy(WebSessionSiteFeature::persistedId)
                            .forEach { feature -> put(feature.persistedId) }
                    },
                )
            }
    }.toString()

internal fun decodeWebSessionSiteSettingsRules(
    raw: String?,
): List<WebSessionSiteSettingsRule> {
    val encoded = requireNotNull(raw) { "Browser site settings preference must not be null" }
    if (encoded.isBlank()) {
        return emptyList()
    }
    val json = JSONObject(encoded)
    return json.keys()
        .asSequence()
        .map { storedDomain ->
            val domain =
                requireNotNull(normalizeWebSessionSiteSettingsDomain(storedDomain)) {
                    "Stored browser site settings domain is invalid: $storedDomain"
                }
            val featureArray = json.getJSONArray(storedDomain)
            val features =
                buildSet {
                    for (index in 0 until featureArray.length()) {
                        add(
                            WebSessionSiteFeature.fromPersistedId(
                                featureArray.getString(index),
                            ),
                        )
                    }
                }
            require(features.isNotEmpty()) {
                "Stored browser site settings rule must not be empty: $storedDomain"
            }
            WebSessionSiteSettingsRule(
                domain = domain,
                disabledFeatures = features,
            )
        }
        .toList()
        .also { rules ->
            require(rules.map(WebSessionSiteSettingsRule::domain).distinct().size == rules.size) {
                "Stored browser site settings rules contain duplicate normalized domains"
            }
        }
        .sortedBy(WebSessionSiteSettingsRule::domain)
}
