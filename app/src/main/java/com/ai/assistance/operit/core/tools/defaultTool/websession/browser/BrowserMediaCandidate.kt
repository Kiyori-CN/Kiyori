package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.util.AppLogger
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong
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
    DOM_AUDIO_CURRENT_SRC("dom_audio_current_src"),
    DOM_AUDIO_SRC("dom_audio_src"),
    DOM_AUDIO_SOURCE("dom_audio_source"),
    ;

    companion object {
        fun fromWireName(value: String): BrowserMediaCandidateDiscoverySource? =
            entries.singleOrNull { it.wireName == value }
    }
}

internal enum class BrowserMediaKind {
    VIDEO,
    AUDIO,
    UNKNOWN_MEDIA,
}

internal enum class BrowserMediaCandidateUrlEvidence {
    VIDEO_FILE,
    HLS_MANIFEST,
    DASH_MANIFEST,
    NONE,
}

internal enum class BrowserMediaCandidateVideoFormat(
    val displayName: String,
) {
    M3U8("M3U8"),
    MPD("MPD"),
    MP4("MP4"),
    M4V("M4V"),
    MOV("MOV"),
    WEBM("WEBM"),
    MKV("MKV"),
    AVI("AVI"),
    FLV("FLV"),
    WMV("WMV"),
    THREE_GP("3GP"),
    OGV("OGV"),
    MPEG("MPEG"),
    MPG("MPG"),
    MPE("MPE"),
    ASF("ASF"),
    RMVB("RMVB"),
    RM("RM"),
    QT("QT"),
    OTHER_VIDEO("其他视频"),
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
    val documentToken: String = "",
    val responseMimeType: String? = null,
    val declaredMimeType: String? = null,
    val contentDisposition: String? = null,
    val durationMillis: Long? = null,
    val isLive: Boolean = false,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val viewportAreaRatio: Double? = null,
    val muted: Boolean = false,
    val looping: Boolean = false,
    val autoplay: Boolean = false,
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
    val documentToken: String = "",
    val firstDiscoveredAt: Long,
    val lastDiscoveredAt: Long,
    val discoverySources: Set<BrowserMediaCandidateDiscoverySource>,
    val responseMimeType: String?,
    val declaredMimeType: String?,
    val contentDisposition: String?,
    val durationMillis: Long?,
    val isLive: Boolean,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val viewportAreaRatio: Double?,
    val muted: Boolean,
    val looping: Boolean,
    val autoplay: Boolean,
) {
    val urlEvidence: BrowserMediaCandidateUrlEvidence
        get() = classifyBrowserMediaCandidateUrl(url)

    val contentDispositionEvidence: BrowserMediaCandidateUrlEvidence
        get() = classifyBrowserMediaContentDisposition(contentDisposition)

    val hasVideoDomEvidence: Boolean
        get() = hasBrowserVideoDomEvidence(discoverySources)

    val hasAudioDomEvidence: Boolean
        get() = hasBrowserAudioDomEvidence(discoverySources)

    /**
     * DOM element identity outranks a misleading URL suffix or response MIME. This is the
     * important distinction for a video transported by a path ending in ".mp3".
     */
    val mediaKind: BrowserMediaKind
        get() =
            when {
                hasVideoDomEvidence -> BrowserMediaKind.VIDEO
                hasAudioDomEvidence -> BrowserMediaKind.AUDIO
                isVideoOrManifestMime(responseMimeType) -> BrowserMediaKind.VIDEO
                isAudioMime(responseMimeType) -> BrowserMediaKind.AUDIO
                contentDispositionEvidence != BrowserMediaCandidateUrlEvidence.NONE ->
                    BrowserMediaKind.VIDEO
                browserMediaContentDispositionIsAudio(contentDisposition) ->
                    BrowserMediaKind.AUDIO
                isVideoOrManifestMime(declaredMimeType) ||
                    isVideoOrManifestMime(accept) -> BrowserMediaKind.VIDEO
                isAudioMime(declaredMimeType) || isAudioMime(accept) -> BrowserMediaKind.AUDIO
                urlEvidence != BrowserMediaCandidateUrlEvidence.NONE -> BrowserMediaKind.VIDEO
                isExplicitBrowserAudioResource(url) -> BrowserMediaKind.AUDIO
                else -> BrowserMediaKind.UNKNOWN_MEDIA
            }

    val isBlob: Boolean
        get() = url.startsWith("blob:", ignoreCase = true)

    val videoFormat: BrowserMediaCandidateVideoFormat?
        get() =
            resolveBrowserMediaCandidateVideoFormat(
                url = url,
                responseMimeType = responseMimeType,
                declaredMimeType = declaredMimeType,
                accept = accept,
                hasDomEvidence = hasVideoDomEvidence,
                contentDisposition = contentDisposition,
            )

    val isLikelyMediaFragment: Boolean
        get() = isLikelyBrowserMediaFragmentUrl(url)

    val directPlaybackReady: Boolean
        get() {
            if (!isHttpMediaCandidateUrl(url)) return false
            return mediaKind != BrowserMediaKind.UNKNOWN_MEDIA
        }

    val isActionableVideo: Boolean
        get() =
            directPlaybackReady &&
                !isBlob &&
                mediaKind == BrowserMediaKind.VIDEO &&
                videoFormat != null &&
                !isLikelyMediaFragment

    val isActionableAudio: Boolean
        get() =
            directPlaybackReady &&
                !isBlob &&
                mediaKind == BrowserMediaKind.AUDIO &&
                !isLikelyMediaFragment

    val isActionableMedia: Boolean
        get() = isActionableVideo || isActionableAudio

    val downloadReady: Boolean
        get() = isActionableMedia

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
    require(
        current == null ||
            normalizeBrowserResourceIdentityUrl(current.url) ==
                normalizeBrowserResourceIdentityUrl(observation.url),
    ) {
        "A media candidate can only merge observations for the same resource identity"
    }
    val mergedHeaders = mergeCaseInsensitiveHeaders(current?.requestHeaders.orEmpty(), observation.requestHeaders)
    val firstDiscoveredAt = current?.firstDiscoveredAt?.coerceAtMost(observation.discoveredAt) ?: observation.discoveredAt
    val lastDiscoveredAt = current?.lastDiscoveredAt?.coerceAtLeast(observation.discoveredAt) ?: observation.discoveredAt
    val observedDurationMillis =
        observation.durationMillis
            ?.takeIf(::isReasonableBrowserMediaDurationMillis)
            ?: extractBrowserMediaCandidateDurationMillis(observation.url, observation.requestHeaders)
    val durationMillis =
        listOfNotNull(current?.durationMillis, observedDurationMillis)
            .maxOrNull()
    val isLive =
        durationMillis == null &&
            (current?.isLive == true || observation.isLive)
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
        documentToken = observation.documentToken,
        firstDiscoveredAt = firstDiscoveredAt,
        lastDiscoveredAt = lastDiscoveredAt,
        discoverySources = current?.discoverySources.orEmpty() + observation.source,
        responseMimeType = observation.responseMimeType.nonBlankOr(current?.responseMimeType),
        declaredMimeType = observation.declaredMimeType.nonBlankOr(current?.declaredMimeType),
        contentDisposition = observation.contentDisposition.nonBlankOr(current?.contentDisposition),
        durationMillis = durationMillis,
        isLive = isLive,
        videoWidth = maxPositive(current?.videoWidth, observation.videoWidth),
        videoHeight = maxPositive(current?.videoHeight, observation.videoHeight),
        viewportAreaRatio =
            maxPositive(current?.viewportAreaRatio, observation.viewportAreaRatio)
                ?.coerceIn(0.0, 1.0),
        muted = current?.muted == true || observation.muted,
        looping = current?.looping == true || observation.looping,
        autoplay = current?.autoplay == true || observation.autoplay,
    )
}

internal fun isBrowserMediaCandidateObservation(observation: BrowserMediaCandidateObservation): Boolean {
    if (observation.url.isBlank()) return false
    val hasVideoDomEvidence = hasBrowserVideoDomEvidence(setOf(observation.source))
    val hasAudioDomEvidence = hasBrowserAudioDomEvidence(setOf(observation.source))
    if (hasVideoDomEvidence || hasAudioDomEvidence) return true
    if (classifyBrowserMediaCandidateUrl(observation.url) != BrowserMediaCandidateUrlEvidence.NONE) return true
    if (
        classifyBrowserMediaContentDisposition(observation.contentDisposition) !=
            BrowserMediaCandidateUrlEvidence.NONE
    ) {
        return true
    }
    return isVideoOrManifestMime(observation.responseMimeType) ||
        isVideoOrManifestMime(observation.declaredMimeType) ||
        isVideoOrManifestMime(observation.requestHeaders.headerValue("Accept")) ||
        isAudioMime(observation.responseMimeType) ||
        isAudioMime(observation.declaredMimeType) ||
        isAudioMime(observation.requestHeaders.headerValue("Accept")) ||
        browserMediaContentDispositionIsAudio(observation.contentDisposition) ||
        isExplicitBrowserAudioResource(observation.url)
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
        sortBrowserMediaCandidates(session.mediaCandidates)
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
        normalizeBrowserResourceIdentityUrl(candidate.url) ==
            normalizeBrowserResourceIdentityUrl(networkUrl) &&
            candidate.isActionableMedia
    }?.id

internal fun StandardBrowserSessionTools.recordRequestMediaCandidate(
    session: BrowserToolSession,
    request: WebResourceRequest,
    documentToken: String = session.credentialDocumentToken,
) {
    recordMediaCandidate(
        session = session,
        observation = buildRequestMediaObservation(session, request, documentToken),
    )
}

internal fun StandardBrowserSessionTools.recordInterceptedResponseMediaCandidate(
    session: BrowserToolSession,
    request: WebResourceRequest,
    response: WebResourceResponse,
    documentToken: String = session.credentialDocumentToken,
) {
    recordMediaCandidate(
        session = session,
        observation =
            buildRequestMediaObservation(session, request, documentToken).copy(
                source = BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE,
                responseMimeType = response.mimeType,
                contentDisposition =
                    response.responseHeaders.orEmpty().headerValue("Content-Disposition"),
                durationMillis =
                    extractBrowserMediaCandidateDurationMillis(
                        request.url?.toString().orEmpty(),
                        response.responseHeaders.orEmpty(),
                    ),
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
                documentToken = session.credentialDocumentToken,
                declaredMimeType = json.optString("mimeType").takeIf(String::isNotBlank),
                durationMillis =
                    json.optLong("durationMillis")
                        .takeIf { json.has("durationMillis") && it > 0L },
                isLive = json.optBoolean("isLive", false),
                videoWidth = json.optInt("videoWidth").takeIf { it > 0 },
                videoHeight = json.optInt("videoHeight").takeIf { it > 0 },
                viewportAreaRatio =
                    json.optDouble("viewportAreaRatio")
                        .takeIf { it.isFinite() && it > 0.0 },
                muted = json.optBoolean("muted", false),
                looping = json.optBoolean("looping", false),
                autoplay = json.optBoolean("autoplay", false),
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
    documentToken: String = session.credentialDocumentToken,
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
        documentToken = documentToken,
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
    var diagnosticCandidate: BrowserMediaCandidate? = null
    var diagnosticEvent: String? = null
    if (session.credentialDocumentToken != observation.documentToken) {
        return
    }
    synchronized(session.mediaCandidates) {
        val resourceIdentity = normalizeBrowserResourceIdentityUrl(observation.url)
        val index =
            session.mediaCandidates.indexOfFirst {
                it.documentToken == observation.documentToken &&
                    normalizeBrowserResourceIdentityUrl(it.url) == resourceIdentity
            }
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
            if (current == null || merged.isActionableMedia != current.isActionableMedia) {
                diagnosticCandidate = merged
                diagnosticEvent =
                    if (current == null) {
                        "MEDIA_CANDIDATE_DISCOVERED"
                    } else {
                        "MEDIA_CANDIDATE_CLASSIFIED"
                    }
            }
        }
    }
    diagnosticCandidate?.let { candidate ->
        recordBrowserDiagnostic(
            level = BrowserDiagnosticLevel.INFO,
            category = BrowserDiagnosticCategory.MEDIA,
            event = requireNotNull(diagnosticEvent),
            session = session,
            documentToken = candidate.documentToken,
            details =
                mapOf(
                    "url" to candidate.url,
                    "mediaKind" to candidate.mediaKind.name,
                    "actionable" to candidate.isActionableMedia.toString(),
                    "sourceCount" to candidate.discoverySources.size.toString(),
                ),
        )
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

private fun hasBrowserVideoDomEvidence(
    sources: Set<BrowserMediaCandidateDiscoverySource>,
): Boolean =
    sources.any { source ->
        source == BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC ||
            source == BrowserMediaCandidateDiscoverySource.DOM_SRC ||
            source == BrowserMediaCandidateDiscoverySource.DOM_SOURCE ||
            source == BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT
    }

private fun hasBrowserAudioDomEvidence(
    sources: Set<BrowserMediaCandidateDiscoverySource>,
): Boolean =
    sources.any { source ->
        source == BrowserMediaCandidateDiscoverySource.DOM_AUDIO_CURRENT_SRC ||
            source == BrowserMediaCandidateDiscoverySource.DOM_AUDIO_SRC ||
            source == BrowserMediaCandidateDiscoverySource.DOM_AUDIO_SOURCE
    }

internal fun resolveBrowserMediaCandidateVideoFormat(
    url: String,
    responseMimeType: String? = null,
    declaredMimeType: String? = null,
    accept: String? = null,
    hasDomEvidence: Boolean = false,
    contentDisposition: String? = null,
): BrowserMediaCandidateVideoFormat? {
    val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
    VIDEO_FORMAT_EXTENSIONS.entries.firstOrNull { (_, extensions) ->
        extensions.any(path::endsWith)
    }?.key?.let { return it }
    val contentDispositionFileName =
        browserMediaContentDispositionFileName(contentDisposition)?.lowercase(Locale.ROOT)
    if (contentDispositionFileName != null) {
        VIDEO_FORMAT_EXTENSIONS.entries.firstOrNull { (_, extensions) ->
            extensions.any(contentDispositionFileName::endsWith)
        }?.key?.let { return it }
    }

    sequenceOf(responseMimeType, declaredMimeType, accept)
        .mapNotNull { value -> value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) }
        .forEach { mimeType ->
            VIDEO_FORMAT_MIME_TYPES.entries.firstOrNull { (_, mimeTypes) ->
                mimeTypes.any { knownMime -> mimeType == knownMime || mimeType.contains(knownMime) }
            }?.key?.let { return it }
        }

    return BrowserMediaCandidateVideoFormat.OTHER_VIDEO.takeIf {
        hasDomEvidence && isHttpMediaCandidateUrl(url)
    }
}

internal fun isLikelyBrowserMediaFragmentUrl(url: String): Boolean {
    val normalized = url.substringBefore('#').lowercase(Locale.ROOT)
    val path = normalized.substringBefore('?')
    val fileName = path.substringAfterLast('/')
    if (fileName == "init.mp4" || fileName == "init.m4s") return true
    if (path.endsWith(".m4s") || path.endsWith(".ts")) return true
    return MEDIA_FRAGMENT_KEYWORDS.any(normalized::contains)
}

internal fun extractBrowserMediaCandidateDurationMillis(
    url: String,
    headers: Map<String, String> = emptyMap(),
): Long? {
    DURATION_HEADER_NAMES.forEach { headerName ->
        val value = headers.headerValue(headerName) ?: return@forEach
        parseBrowserMediaDurationMillis(
            rawValue = value,
            valueIsMillis = headerName.endsWith("-Ms", ignoreCase = true),
        )?.let { return it }
    }
    val query = url.substringBefore('#').substringAfter('?', "")
    if (query.isBlank()) return null
    return query.split('&').firstNotNullOfOrNull { item ->
        val separatorIndex = item.indexOf('=')
        if (separatorIndex <= 0) return@firstNotNullOfOrNull null
        val name = item.substring(0, separatorIndex)
        if (DURATION_QUERY_NAMES.none { it.equals(name, ignoreCase = true) }) {
            return@firstNotNullOfOrNull null
        }
        parseBrowserMediaDurationMillis(
            rawValue = item.substring(separatorIndex + 1),
            valueIsMillis =
                name.contains("ms", ignoreCase = true) ||
                    name.contains("millis", ignoreCase = true),
        )
    }
}

private fun parseBrowserMediaDurationMillis(
    rawValue: String,
    valueIsMillis: Boolean,
): Long? {
    val value = rawValue.trim().trim('"', '\'')
    if (value.isBlank()) return null
    val colonParts = value.split(':')
    if (colonParts.size in 2..3) {
        val seconds = colonParts.last().toDoubleOrNull() ?: return null
        val minutes = colonParts[colonParts.lastIndex - 1].toLongOrNull() ?: return null
        val hours = if (colonParts.size == 3) colonParts.first().toLongOrNull() ?: return null else 0L
        val millis =
            hours * 3_600_000L +
                minutes * 60_000L +
                (seconds * 1_000.0).roundToLong()
        return millis.takeIf(::isReasonableBrowserMediaDurationMillis)
    }

    val numericMatch = Regex("""^-?\d+(?:\.\d+)?""").find(value) ?: return null
    val numericValue = numericMatch.value.toDoubleOrNull() ?: return null
    if (!numericValue.isFinite() || numericValue <= 0.0) return null
    val suffix = value.substring(numericMatch.range.last + 1).trim().lowercase(Locale.ROOT)
    val millis =
        when {
            valueIsMillis || suffix.startsWith("ms") -> numericValue
            suffix.startsWith("m") -> numericValue * 60_000.0
            else -> numericValue * 1_000.0
        }.roundToLong()
    return millis.takeIf(::isReasonableBrowserMediaDurationMillis)
}

private fun isReasonableBrowserMediaDurationMillis(value: Long): Boolean =
    value in 1L..MAX_REASONABLE_MEDIA_DURATION_MS

private fun isExplicitBrowserAudioResource(url: String): Boolean {
    val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
    return AUDIO_FILE_EXTENSIONS.any(path::endsWith)
}

private fun isAudioMime(value: String?): Boolean {
    val normalized = value?.lowercase(Locale.ROOT).orEmpty()
    return normalized.contains("audio/") && !normalized.contains("video/")
}

private fun browserMediaContentDispositionIsAudio(contentDisposition: String?): Boolean {
    val contentDispositionFileName =
        browserMediaContentDispositionFileName(contentDisposition)?.lowercase(Locale.ROOT)
            ?: return false
    return AUDIO_FILE_EXTENSIONS.any(contentDispositionFileName::endsWith)
}

private fun classifyBrowserMediaContentDisposition(
    contentDisposition: String?,
): BrowserMediaCandidateUrlEvidence {
    val fileName =
        browserMediaContentDispositionFileName(contentDisposition)?.lowercase(Locale.ROOT)
            ?: return BrowserMediaCandidateUrlEvidence.NONE
    return when {
        VIDEO_FILE_EXTENSIONS.any(fileName::endsWith) ->
            BrowserMediaCandidateUrlEvidence.VIDEO_FILE
        HLS_MANIFEST_EXTENSIONS.any(fileName::endsWith) ->
            BrowserMediaCandidateUrlEvidence.HLS_MANIFEST
        DASH_MANIFEST_EXTENSIONS.any(fileName::endsWith) ->
            BrowserMediaCandidateUrlEvidence.DASH_MANIFEST
        else -> BrowserMediaCandidateUrlEvidence.NONE
    }
}

private fun browserMediaContentDispositionFileName(contentDisposition: String?): String? {
    val value = contentDisposition?.takeIf(String::isNotBlank) ?: return null
    val parameters =
        value
            .split(';')
            .asSequence()
            .map(String::trim)
            .toList()
    val parameter =
        parameters.firstOrNull { part -> part.startsWith("filename*=", ignoreCase = true) }
            ?: parameters.firstOrNull { part -> part.startsWith("filename=", ignoreCase = true) }
            ?: return null
    val rawValue = parameter.substringAfter('=').trim().trim('"', '\'')
    val encodedFileName =
        if (parameter.startsWith("filename*=", ignoreCase = true)) {
            val charsetEnd = rawValue.indexOf('\'')
            val languageEnd =
                rawValue.indexOf('\'', startIndex = charsetEnd + 1)
            if (charsetEnd >= 0 && languageEnd > charsetEnd) {
                rawValue.substring(languageEnd + 1)
            } else {
                rawValue
            }
        } else {
            rawValue
        }
    return runCatching { URLDecoder.decode(encodedFileName, Charsets.UTF_8.name()) }
        .getOrDefault(encodedFileName)
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .takeIf(String::isNotBlank)
}

private fun maxPositive(first: Int?, second: Int?): Int? =
    listOfNotNull(first?.takeIf { it > 0 }, second?.takeIf { it > 0 }).maxOrNull()

private fun maxPositive(first: Double?, second: Double?): Double? =
    listOfNotNull(
        first?.takeIf { it.isFinite() && it > 0.0 },
        second?.takeIf { it.isFinite() && it > 0.0 },
    ).maxOrNull()

private const val MAX_REASONABLE_MEDIA_DURATION_MS = 7L * 24L * 60L * 60L * 1_000L

private val VIDEO_FORMAT_EXTENSIONS =
    linkedMapOf(
        BrowserMediaCandidateVideoFormat.M3U8 to listOf(".m3u8"),
        BrowserMediaCandidateVideoFormat.MPD to listOf(".mpd"),
        BrowserMediaCandidateVideoFormat.MP4 to listOf(".mp4"),
        BrowserMediaCandidateVideoFormat.M4V to listOf(".m4v"),
        BrowserMediaCandidateVideoFormat.MOV to listOf(".mov"),
        BrowserMediaCandidateVideoFormat.WEBM to listOf(".webm"),
        BrowserMediaCandidateVideoFormat.MKV to listOf(".mkv"),
        BrowserMediaCandidateVideoFormat.AVI to listOf(".avi"),
        BrowserMediaCandidateVideoFormat.FLV to listOf(".flv"),
        BrowserMediaCandidateVideoFormat.WMV to listOf(".wmv"),
        BrowserMediaCandidateVideoFormat.THREE_GP to listOf(".3gp"),
        BrowserMediaCandidateVideoFormat.OGV to listOf(".ogv"),
        BrowserMediaCandidateVideoFormat.MPEG to listOf(".mpeg"),
        BrowserMediaCandidateVideoFormat.MPG to listOf(".mpg"),
        BrowserMediaCandidateVideoFormat.MPE to listOf(".mpe"),
        BrowserMediaCandidateVideoFormat.ASF to listOf(".asf"),
        BrowserMediaCandidateVideoFormat.RMVB to listOf(".rmvb"),
        BrowserMediaCandidateVideoFormat.RM to listOf(".rm"),
        BrowserMediaCandidateVideoFormat.QT to listOf(".qt"),
    )
private val VIDEO_FORMAT_MIME_TYPES =
    linkedMapOf(
        BrowserMediaCandidateVideoFormat.M3U8 to
            listOf("application/vnd.apple.mpegurl", "application/x-mpegurl"),
        BrowserMediaCandidateVideoFormat.MPD to listOf("application/dash+xml"),
        BrowserMediaCandidateVideoFormat.MP4 to listOf("video/mp4"),
        BrowserMediaCandidateVideoFormat.M4V to listOf("video/x-m4v"),
        BrowserMediaCandidateVideoFormat.MOV to listOf("video/quicktime"),
        BrowserMediaCandidateVideoFormat.WEBM to listOf("video/webm"),
        BrowserMediaCandidateVideoFormat.MKV to listOf("video/x-matroska", "video/mkv"),
        BrowserMediaCandidateVideoFormat.AVI to listOf("video/x-msvideo"),
        BrowserMediaCandidateVideoFormat.FLV to listOf("video/x-flv"),
        BrowserMediaCandidateVideoFormat.WMV to listOf("video/x-ms-wmv"),
        BrowserMediaCandidateVideoFormat.THREE_GP to listOf("video/3gpp"),
        BrowserMediaCandidateVideoFormat.OGV to listOf("video/ogg"),
        BrowserMediaCandidateVideoFormat.MPEG to listOf("video/mpeg"),
        BrowserMediaCandidateVideoFormat.ASF to listOf("video/x-ms-asf"),
        BrowserMediaCandidateVideoFormat.RMVB to listOf("application/vnd.rn-realmedia-vbr"),
        BrowserMediaCandidateVideoFormat.RM to listOf("application/vnd.rn-realmedia"),
    )
private val VIDEO_FILE_EXTENSIONS =
    VIDEO_FORMAT_EXTENSIONS
        .filterKeys { format ->
            format != BrowserMediaCandidateVideoFormat.M3U8 &&
                format != BrowserMediaCandidateVideoFormat.MPD
        }
        .values
        .flatten()
private val HLS_MANIFEST_EXTENSIONS = listOf(".m3u8")
private val DASH_MANIFEST_EXTENSIONS = listOf(".mpd")
private val AUDIO_FILE_EXTENSIONS =
    listOf(
        ".aac",
        ".ac3",
        ".aif",
        ".aiff",
        ".amr",
        ".ape",
        ".dts",
        ".flac",
        ".m4a",
        ".mka",
        ".mp3",
        ".mpa",
        ".ogg",
        ".opus",
        ".ra",
        ".wav",
        ".wma",
    )
private val MEDIA_FRAGMENT_KEYWORDS =
    listOf("/segment/", "/segments/", "/chunk/", "/chunks/", "/fragment/", "/fragments/")
private val DURATION_HEADER_NAMES =
    listOf(
        "X-Content-Duration",
        "Content-Duration",
        "Duration",
        "X-Duration",
        "X-Video-Duration",
        "X-Playback-Duration",
        "X-Amz-Meta-Duration",
        "X-Amz-Meta-Duration-Ms",
        "X-Kiyori-Duration",
        "X-Kiyori-Duration-Ms",
    )
private val DURATION_QUERY_NAMES =
    setOf(
        "duration",
        "dur",
        "duration_ms",
        "durationMillis",
        "duration_millis",
        "video_duration",
        "videoDuration",
        "media_duration",
        "content_duration",
    )

internal val BROWSER_MEDIA_CANDIDATE_OBSERVER_SCRIPT =
    """
    (function() {
      const bridge = window.OperitMediaCandidateBridge;
      if (!bridge || typeof bridge.observe !== 'function') return;
      const stateKey = '__operitMediaCandidateObserverV2';
      const report = function(media, url, source, mimeType, mediaKind) {
        if (!url) return;
        const rect = media.getBoundingClientRect();
        const viewportWidth = Math.max(window.innerWidth || 0, 1);
        const viewportHeight = Math.max(window.innerHeight || 0, 1);
        const visibleWidth = Math.max(0, Math.min(rect.right, viewportWidth) - Math.max(rect.left, 0));
        const visibleHeight = Math.max(0, Math.min(rect.bottom, viewportHeight) - Math.max(rect.top, 0));
        const durationMillis =
          Number.isFinite(media.duration) && media.duration > 0
            ? Math.round(media.duration * 1000)
            : null;
        bridge.observe(JSON.stringify({
          url: String(url),
          source: source,
          mimeType: mimeType ? String(mimeType) : '',
          mediaKind: mediaKind,
          durationMillis: durationMillis,
          isLive: media.duration === Infinity,
          videoWidth: mediaKind === 'video' ? (Number(media.videoWidth) || 0) : 0,
          videoHeight: mediaKind === 'video' ? (Number(media.videoHeight) || 0) : 0,
          viewportAreaRatio: Math.min(1, (visibleWidth * visibleHeight) / (viewportWidth * viewportHeight)),
          muted: Boolean(media.muted),
          looping: Boolean(media.loop),
          autoplay: Boolean(media.autoplay)
        }));
      };
      const scanMedia = function(media, playEvent) {
        const isVideo = media instanceof HTMLVideoElement;
        const isAudio = media instanceof HTMLAudioElement;
        if (!isVideo && !isAudio) return;
        const mediaKind = isVideo ? 'video' : 'audio';
        const currentSource = media.currentSrc || '';
        if (currentSource) {
          report(
            media,
            currentSource,
            isVideo
              ? (playEvent ? 'dom_play_event' : 'dom_current_src')
              : 'dom_audio_current_src',
            media.type || '',
            mediaKind
          );
        }
        const attributeSource = media.getAttribute('src') || '';
        if (attributeSource) {
          report(
            media,
            media.src || attributeSource,
            isVideo ? 'dom_src' : 'dom_audio_src',
            media.type || '',
            mediaKind
          );
        }
        media.querySelectorAll('source').forEach(function(source) {
          const sourceUrl = source.src || source.getAttribute('src') || '';
          if (sourceUrl) {
            report(
              media,
              sourceUrl,
              isVideo ? 'dom_source' : 'dom_audio_source',
              source.type || '',
              mediaKind
            );
          }
        });
      };
      const scan = function(root) {
        if (root instanceof HTMLVideoElement || root instanceof HTMLAudioElement) {
          scanMedia(root, false);
        }
        if (root && root.querySelectorAll) {
          root.querySelectorAll('video, audio').forEach(function(media) {
            scanMedia(media, false);
          });
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
        scanMedia(event.target, true);
      }, true);
      document.addEventListener('loadedmetadata', function(event) {
        scanMedia(event.target, false);
      }, true);
      document.addEventListener('durationchange', function(event) {
        scanMedia(event.target, false);
      }, true);
      document.addEventListener('resize', function(event) {
        scanMedia(event.target, false);
      }, true);
      window[stateKey] = { observer: observer, scan: scan };
      scan(document);
    })();
    """.trimIndent()
