package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

private const val MEDIA_CANDIDATE_TAG = "BrowserMediaCandidate"
private const val MAX_MEDIA_CANDIDATES = 80

internal enum class BrowserMediaCandidateDiscoverySource(val wireName: String) {
    NETWORK_REQUEST("network_request"),
    INTERCEPTED_RESPONSE("intercepted_response"),
    DOM_CURRENT_SRC("dom_current_src"),
    DOM_SRC("dom_src"),
    DOM_SOURCE("dom_source"),
    DOM_PLAY_EVENT("dom_play_event"),
    ;

    companion object {
        fun fromWireName(value: String): BrowserMediaCandidateDiscoverySource? =
            entries.singleOrNull { it.wireName == value }
    }
}

internal enum class BrowserMediaCandidateUrlEvidence {
    VIDEO_FILE,
    HLS_MANIFEST,
    DASH_MANIFEST,
    NONE,
}

internal data class BrowserMediaCandidateObservation(
    val url: String,
    val requestHeaders: Map<String, String>,
    val pageUrl: String,
    val pageTitle: String,
    val sourceSessionId: String,
    val sourceProfile: String,
    val cookieScope: String,
    val cookieScopeUrl: String,
    val source: BrowserMediaCandidateDiscoverySource,
    val responseMimeType: String? = null,
    val declaredMimeType: String? = null,
    val discoveredAt: Long = System.currentTimeMillis(),
)

internal data class BrowserMediaCandidate(
    val id: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val origin: String?,
    val userAgent: String?,
    val referer: String?,
    val cookie: String?,
    val cookieScope: String,
    val cookieScopeUrl: String,
    val range: String?,
    val accept: String?,
    val pageUrl: String,
    val pageTitle: String,
    val sourceSessionId: String,
    val sourceProfile: String,
    val firstDiscoveredAt: Long,
    val lastDiscoveredAt: Long,
    val discoverySources: Set<BrowserMediaCandidateDiscoverySource>,
    val responseMimeType: String?,
    val declaredMimeType: String?,
) {
    val urlEvidence: BrowserMediaCandidateUrlEvidence
        get() = classifyBrowserMediaCandidateUrl(url)

    val isBlob: Boolean
        get() = url.startsWith("blob:", ignoreCase = true)

    val directPlaybackReady: Boolean
        get() {
            if (!isHttpMediaCandidateUrl(url)) return false
            if (urlEvidence != BrowserMediaCandidateUrlEvidence.NONE) return true
            return discoverySources.any { source ->
                source == BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC ||
                    source == BrowserMediaCandidateDiscoverySource.DOM_SRC ||
                    source == BrowserMediaCandidateDiscoverySource.DOM_SOURCE ||
                    source == BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT
            }
        }

    val downloadReady: Boolean
        get() = directPlaybackReady

    val displayMimeType: String?
        get() = responseMimeType ?: declaredMimeType
}

internal fun classifyBrowserMediaCandidateUrl(url: String): BrowserMediaCandidateUrlEvidence {
    val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
    return when {
        VIDEO_FILE_EXTENSIONS.any(path::endsWith) -> BrowserMediaCandidateUrlEvidence.VIDEO_FILE
        HLS_MANIFEST_EXTENSIONS.any(path::endsWith) -> BrowserMediaCandidateUrlEvidence.HLS_MANIFEST
        DASH_MANIFEST_EXTENSIONS.any(path::endsWith) -> BrowserMediaCandidateUrlEvidence.DASH_MANIFEST
        else -> BrowserMediaCandidateUrlEvidence.NONE
    }
}

internal fun mergeBrowserMediaCandidate(
    current: BrowserMediaCandidate?,
    observation: BrowserMediaCandidateObservation,
    newCandidateId: String,
): BrowserMediaCandidate? {
    if (!isBrowserMediaCandidateObservation(observation)) return current
    require(current == null || current.url == observation.url) {
        "A media candidate can only merge observations for the exact original URL"
    }
    val mergedHeaders = mergeCaseInsensitiveHeaders(current?.requestHeaders.orEmpty(), observation.requestHeaders)
    val firstDiscoveredAt = current?.firstDiscoveredAt?.coerceAtMost(observation.discoveredAt) ?: observation.discoveredAt
    val lastDiscoveredAt = current?.lastDiscoveredAt?.coerceAtLeast(observation.discoveredAt) ?: observation.discoveredAt
    return BrowserMediaCandidate(
        id = current?.id ?: newCandidateId,
        url = observation.url,
        requestHeaders = mergedHeaders,
        origin = mergedHeaders.headerValue("Origin"),
        userAgent = mergedHeaders.headerValue("User-Agent"),
        referer = mergedHeaders.headerValue("Referer"),
        cookie = mergedHeaders.headerValue("Cookie"),
        cookieScope = observation.cookieScope.ifBlank { current?.cookieScope.orEmpty() },
        cookieScopeUrl = observation.cookieScopeUrl.ifBlank { current?.cookieScopeUrl.orEmpty() },
        range = mergedHeaders.headerValue("Range"),
        accept = mergedHeaders.headerValue("Accept"),
        pageUrl = observation.pageUrl.ifBlank { current?.pageUrl.orEmpty() },
        pageTitle = observation.pageTitle.ifBlank { current?.pageTitle.orEmpty() },
        sourceSessionId = observation.sourceSessionId.ifBlank { current?.sourceSessionId.orEmpty() },
        sourceProfile = observation.sourceProfile.ifBlank { current?.sourceProfile.orEmpty() },
        firstDiscoveredAt = firstDiscoveredAt,
        lastDiscoveredAt = lastDiscoveredAt,
        discoverySources = current?.discoverySources.orEmpty() + observation.source,
        responseMimeType = observation.responseMimeType.nonBlankOr(current?.responseMimeType),
        declaredMimeType = observation.declaredMimeType.nonBlankOr(current?.declaredMimeType),
    )
}

internal fun isBrowserMediaCandidateObservation(observation: BrowserMediaCandidateObservation): Boolean {
    if (observation.url.isBlank()) return false
    val domEvidence =
        observation.source == BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC ||
            observation.source == BrowserMediaCandidateDiscoverySource.DOM_SRC ||
            observation.source == BrowserMediaCandidateDiscoverySource.DOM_SOURCE ||
            observation.source == BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT
    if (domEvidence) return true
    if (classifyBrowserMediaCandidateUrl(observation.url) != BrowserMediaCandidateUrlEvidence.NONE) return true
    return isVideoOrManifestMime(observation.responseMimeType) ||
        isVideoOrManifestMime(observation.declaredMimeType) ||
        isVideoOrManifestMime(observation.requestHeaders.headerValue("Accept"))
}

internal fun StandardBrowserSessionTools.clearMediaCandidates(session: BrowserToolSession) {
    synchronized(session.mediaCandidates) {
        session.mediaCandidates.clear()
    }
    notifySessionStateChanged(session)
}

internal fun StandardBrowserSessionTools.snapshotMediaCandidates(
    session: BrowserToolSession,
): List<BrowserMediaCandidate> =
    synchronized(session.mediaCandidates) {
        session.mediaCandidates.sortedByDescending(BrowserMediaCandidate::lastDiscoveredAt)
    }

internal fun StandardBrowserSessionTools.findMediaCandidate(
    session: BrowserToolSession,
    candidateId: String,
): BrowserMediaCandidate? =
    synchronized(session.mediaCandidates) {
        session.mediaCandidates.singleOrNull { it.id == candidateId }
    }

internal fun findDirectMediaCandidateIdForNetworkEntry(
    candidates: List<BrowserMediaCandidate>,
    networkUrl: String,
): String? =
    candidates.singleOrNull { candidate ->
        candidate.url == networkUrl && candidate.directPlaybackReady
    }?.id

internal fun StandardBrowserSessionTools.recordRequestMediaCandidate(
    session: BrowserToolSession,
    request: WebResourceRequest,
) {
    recordMediaCandidate(
        session = session,
        observation = buildRequestMediaObservation(session, request),
    )
}

internal fun StandardBrowserSessionTools.recordInterceptedResponseMediaCandidate(
    session: BrowserToolSession,
    request: WebResourceRequest,
    response: WebResourceResponse,
) {
    recordMediaCandidate(
        session = session,
        observation =
            buildRequestMediaObservation(session, request).copy(
                source = BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE,
                responseMimeType = response.mimeType,
            ),
    )
}

internal fun StandardBrowserSessionTools.recordDomMediaCandidate(
    session: BrowserToolSession,
    payload: String?,
) {
    val json = payload?.takeIf(String::isNotBlank)?.let(::JSONObject) ?: return
    val source =
        BrowserMediaCandidateDiscoverySource.fromWireName(json.optString("source")) ?: return
    val url = json.optString("url")
    if (url.isBlank()) return
    val headers = captureSessionMediaHeaders(session, url, emptyMap())
    recordMediaCandidate(
        session = session,
        observation =
            BrowserMediaCandidateObservation(
                url = url,
                requestHeaders = headers,
                pageUrl = session.currentUrl,
                pageTitle = session.pageTitle,
                sourceSessionId = session.id,
                sourceProfile = session.profile.wireName,
                cookieScope = session.profile.wireName,
                cookieScopeUrl = url,
                source = source,
                declaredMimeType = json.optString("mimeType").takeIf(String::isNotBlank),
            ),
    )
}

internal fun StandardBrowserSessionTools.injectMediaCandidateObserver(webView: WebView) {
    webView.evaluateJavascript(BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT, null)
}

internal class BrowserMediaCandidateBridge(
    private val tools: StandardBrowserSessionTools,
    private val session: BrowserToolSession,
) {
    @JavascriptInterface
    fun observe(payload: String?) {
        runCatching { tools.recordDomMediaCandidate(session, payload) }
            .onFailure { error ->
                AppLogger.w(MEDIA_CANDIDATE_TAG, "Unable to record DOM media candidate", error)
            }
    }
}

private fun StandardBrowserSessionTools.buildRequestMediaObservation(
    session: BrowserToolSession,
    request: WebResourceRequest,
): BrowserMediaCandidateObservation {
    val url = request.url?.toString().orEmpty()
    val observedHeaders = request.requestHeaders?.filterKeys(String::isNotBlank).orEmpty()
    return BrowserMediaCandidateObservation(
        url = url,
        requestHeaders = captureSessionMediaHeaders(session, url, observedHeaders),
        pageUrl = session.currentUrl,
        pageTitle = session.pageTitle,
        sourceSessionId = session.id,
        sourceProfile = session.profile.wireName,
        cookieScope = session.profile.wireName,
        cookieScopeUrl = url,
        source = BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST,
    )
}

private fun StandardBrowserSessionTools.captureSessionMediaHeaders(
    session: BrowserToolSession,
    url: String,
    observedHeaders: Map<String, String>,
): Map<String, String> {
    val cookie =
        url.takeIf(String::isNotBlank)?.let { candidateUrl ->
            runCatching { session.cookieManager.getCookie(candidateUrl) }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
        }
    return buildBrowserMediaCandidateHeaders(
        observedHeaders = observedHeaders,
        appliedUserAgent = session.appliedUserAgent,
        cookie = cookie,
    )
}

internal fun buildBrowserMediaCandidateHeaders(
    observedHeaders: Map<String, String>,
    appliedUserAgent: String,
    cookie: String?,
): Map<String, String> {
    var headers = observedHeaders
    if (headers.headerValue("User-Agent") == null && appliedUserAgent.isNotBlank()) {
        headers = mergeCaseInsensitiveHeaders(headers, mapOf("User-Agent" to appliedUserAgent))
    }
    if (headers.headerValue("Cookie") == null && !cookie.isNullOrBlank()) {
        headers = mergeCaseInsensitiveHeaders(headers, mapOf("Cookie" to cookie))
    }
    return headers
}

private fun StandardBrowserSessionTools.recordMediaCandidate(
    session: BrowserToolSession,
    observation: BrowserMediaCandidateObservation,
) {
    var changed = false
    synchronized(session.mediaCandidates) {
        val index = session.mediaCandidates.indexOfFirst { it.url == observation.url }
        val current = index.takeIf { it >= 0 }?.let(session.mediaCandidates::get)
        val merged = mergeBrowserMediaCandidate(current, observation, UUID.randomUUID().toString())
            ?: return@synchronized
        if (merged != current) {
            if (index >= 0) {
                session.mediaCandidates[index] = merged
            } else {
                session.mediaCandidates += merged
            }
            while (session.mediaCandidates.size > MAX_MEDIA_CANDIDATES) {
                val oldestIndex =
                    session.mediaCandidates.indices.minByOrNull { candidateIndex ->
                        session.mediaCandidates[candidateIndex].lastDiscoveredAt
                    } ?: 0
                session.mediaCandidates.removeAt(oldestIndex)
            }
            changed = true
        }
    }
    if (changed) {
        notifySessionStateChanged(session)
        StandardBrowserSessionTools.mainHandler.post {
            refreshSessionUiOnMain(session.id)
        }
    }
}

private fun mergeCaseInsensitiveHeaders(
    current: Map<String, String>,
    observed: Map<String, String>,
): Map<String, String> {
    val merged = LinkedHashMap(current)
    observed.forEach { (name, value) ->
        if (name.isBlank()) return@forEach
        merged.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(merged::remove)
        merged[name] = value
    }
    return merged.toMap()
}

private fun Map<String, String>.headerValue(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.takeIf(String::isNotBlank)

private fun String?.nonBlankOr(previous: String?): String? =
    this?.takeIf(String::isNotBlank) ?: previous

private fun isHttpMediaCandidateUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

private fun isVideoOrManifestMime(value: String?): Boolean {
    val normalized = value?.lowercase(Locale.ROOT).orEmpty()
    return normalized.contains("video/") ||
        normalized.contains("application/vnd.apple.mpegurl") ||
        normalized.contains("application/x-mpegurl") ||
        normalized.contains("application/dash+xml")
}

private val VIDEO_FILE_EXTENSIONS =
    listOf(".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi", ".flv", ".wmv", ".3gp", ".ogv")
private val HLS_MANIFEST_EXTENSIONS = listOf(".m3u8")
private val DASH_MANIFEST_EXTENSIONS = listOf(".mpd")

internal val BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT =
    """
    (function() {
      const bridge = window.OperitMediaCandidateBridge;
      if (!bridge || typeof bridge.observe !== 'function') return;
      const stateKey = '__operitMediaCandidateObserverV1';
      const report = function(url, source, mimeType) {
        if (!url) return;
        bridge.observe(JSON.stringify({
          url: String(url),
          source: source,
          mimeType: mimeType ? String(mimeType) : ''
        }));
      };
      const scanVideo = function(video, playEvent) {
        if (!(video instanceof HTMLVideoElement)) return;
        const currentSource = video.currentSrc || '';
        if (currentSource) {
          report(currentSource, playEvent ? 'dom_play_event' : 'dom_current_src', video.type || '');
        }
        const attributeSource = video.getAttribute('src') || '';
        if (attributeSource) report(video.src || attributeSource, 'dom_src', video.type || '');
        video.querySelectorAll('source').forEach(function(source) {
          const sourceUrl = source.src || source.getAttribute('src') || '';
          if (sourceUrl) report(sourceUrl, 'dom_source', source.type || '');
        });
      };
      const scan = function(root) {
        if (root instanceof HTMLVideoElement) scanVideo(root, false);
        if (root && root.querySelectorAll) {
          root.querySelectorAll('video').forEach(function(video) { scanVideo(video, false); });
        }
      };
      if (window[stateKey]) {
        window[stateKey].scan(document);
        return;
      }
      const observer = new MutationObserver(function(records) {
        records.forEach(function(record) {
          if (record.type === 'attributes') scan(record.target);
          record.addedNodes.forEach(function(node) {
            if (node && node.nodeType === Node.ELEMENT_NODE) scan(node);
          });
        });
      });
      observer.observe(document.documentElement || document, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['src', 'type']
      });
      document.addEventListener('play', function(event) {
        scanVideo(event.target, true);
      }, true);
      window[stateKey] = { observer: observer, scan: scan };
      scan(document);
    })();
    """.trimIndent()
