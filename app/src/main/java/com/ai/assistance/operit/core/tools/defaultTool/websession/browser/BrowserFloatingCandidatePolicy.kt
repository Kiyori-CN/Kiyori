package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

internal data class BrowserMediaCandidateRanking(
    val score: Int,
    val isRecommended: Boolean,
    val automaticFloatingEligible: Boolean,
    val qualityHeight: Int?,
    val qualityLabel: String?,
    val summary: String,
)

internal data class BrowserMediaCandidateQuality(
    val width: Int?,
    val height: Int,
    val label: String,
)

internal fun sortBrowserMediaCandidates(
    candidates: List<BrowserMediaCandidate>,
): List<BrowserMediaCandidate> =
    candidates
        .map { candidate -> candidate to rankBrowserMediaCandidate(candidate) }
        .sortedWith(
            compareByDescending<Pair<BrowserMediaCandidate, BrowserMediaCandidateRanking>> {
                it.second.isRecommended
            }
                .thenByDescending { it.second.qualityHeight ?: 0 }
                .thenByDescending { it.second.score }
                .thenByDescending { it.first.durationMillis != null || it.first.isLive }
                .thenByDescending { it.first.lastDiscoveredAt }
                .thenBy { it.first.id },
        )
        .map(Pair<BrowserMediaCandidate, BrowserMediaCandidateRanking>::first)

internal fun rankBrowserMediaCandidate(
    candidate: BrowserMediaCandidate,
): BrowserMediaCandidateRanking {
    if (candidate.isActionableAudio) {
        val reasons = mutableListOf("网页音频资源")
        var score = 90
        if (BrowserMediaCandidateDiscoverySource.DOM_AUDIO_CURRENT_SRC in candidate.discoverySources) {
            score += 260
            reasons.add(0, "网页当前音频")
        }
        if (BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE in candidate.discoverySources) {
            score += 60
            reasons += "响应类型已确认"
        }
        return BrowserMediaCandidateRanking(
            score = score,
            isRecommended = false,
            automaticFloatingEligible = false,
            qualityHeight = null,
            qualityLabel = null,
            summary = reasons.joinToString(" · "),
        )
    }
    if (!candidate.isActionableVideo) {
        return BrowserMediaCandidateRanking(
            score = Int.MIN_VALUE,
            isRecommended = false,
            automaticFloatingEligible = false,
            qualityHeight = null,
            qualityLabel = null,
            summary = "不可执行的媒体线索",
        )
    }

    val videoFormat =
        requireNotNull(candidate.videoFormat) {
            "An actionable browser media candidate must have an exact video format"
        }
    val reasons = mutableListOf<String>()
    val quality =
        resolveBrowserMediaCandidateQuality(
            width = candidate.videoWidth,
            height = candidate.videoHeight,
            url = candidate.url,
        )
    var score =
        when (videoFormat) {
            BrowserMediaCandidateVideoFormat.M3U8 -> 180.also { reasons += "HLS 清单" }
            BrowserMediaCandidateVideoFormat.MPD -> 175.also { reasons += "DASH 清单" }
            BrowserMediaCandidateVideoFormat.OTHER_VIDEO -> 80.also { reasons += "网页视频直链" }
            else -> 150.also { reasons += "${videoFormat.displayName} 直链" }
        }

    if (BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT in candidate.discoverySources) {
        score += 600
        reasons.add(0, "网页正在播放")
    }
    if (BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC in candidate.discoverySources) {
        score += 450
        reasons += "网页当前视频"
    }
    if (BrowserMediaCandidateDiscoverySource.DOM_SRC in candidate.discoverySources) {
        score += 280
        reasons += "视频元素地址"
    }
    if (BrowserMediaCandidateDiscoverySource.DOM_SOURCE in candidate.discoverySources) {
        score += 260
        reasons += "视频源地址"
    }
    if (BrowserMediaCandidateDiscoverySource.INTERCEPTED_RESPONSE in candidate.discoverySources) {
        score += 80
        reasons += "响应类型已确认"
    }
    if (BrowserMediaCandidateDiscoverySource.NETWORK_REQUEST in candidate.discoverySources) {
        score += 20
    }

    val viewportScore =
        ((candidate.viewportAreaRatio ?: 0.0).coerceIn(0.0, 1.0) * 180.0)
            .roundToInt()
    score += viewportScore
    if (viewportScore >= 45) reasons += "页面主画面"

    val resolutionScore =
        browserMediaResolutionScore(quality?.height)
    score += resolutionScore
    if (quality != null) {
        reasons += "${quality.label} 画质"
    }

    when {
        candidate.isLive -> {
            score += 70
            reasons += "直播"
        }
        candidate.durationMillis != null -> {
            score +=
                when {
                    candidate.durationMillis >= 30L * 60L * 1_000L -> 100
                    candidate.durationMillis >= 10L * 60L * 1_000L -> 85
                    candidate.durationMillis >= 3L * 60L * 1_000L -> 70
                    candidate.durationMillis >= 60L * 1_000L -> 55
                    else -> 25
                }
            reasons += "时长 ${formatBrowserCandidateDuration(candidate.durationMillis)}"
        }
    }

    score += ((candidate.discoverySources.size - 1).coerceAtLeast(0) * 15).coerceAtMost(60)

    val normalizedUrl = candidate.url.lowercase(Locale.ROOT)
    val noisy = BROWSER_MEDIA_NOISE_KEYWORDS.any(normalizedUrl::contains)
    if (noisy) {
        score -= 220
        reasons += "疑似预览或广告"
    }

    val backgroundLoop =
        candidate.muted &&
            candidate.looping &&
            candidate.autoplay &&
            (candidate.viewportAreaRatio ?: 0.0) < 0.12
    if (backgroundLoop) {
        score -= 250
        reasons += "小型静音循环"
    }

    val isRecommended = score >= 200 && !noisy && !backgroundLoop
    val hasDomEvidence =
        candidate.discoverySources.any { source ->
            source == BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT ||
                source == BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC ||
                source == BrowserMediaCandidateDiscoverySource.DOM_SRC ||
                source == BrowserMediaCandidateDiscoverySource.DOM_SOURCE
        }
    val manifest =
        videoFormat == BrowserMediaCandidateVideoFormat.M3U8 ||
            videoFormat == BrowserMediaCandidateVideoFormat.MPD
    val automaticFloatingEligible =
        isRecommended &&
            (
                hasDomEvidence ||
                    manifest ||
                    candidate.isLive ||
                    candidate.durationMillis != null
            )

    return BrowserMediaCandidateRanking(
        score = score,
        isRecommended = isRecommended,
        automaticFloatingEligible = automaticFloatingEligible,
        qualityHeight = quality?.height,
        qualityLabel = quality?.label,
        summary = reasons.distinct().take(3).joinToString(" · "),
    )
}

internal fun selectAutomaticFloatingMediaCandidate(
    candidates: List<WebSessionBrowserMediaCandidate>,
    minimumDurationMillis: Long,
): WebSessionBrowserMediaCandidate? =
    candidates
        .asSequence()
        .filter(WebSessionBrowserMediaCandidate::automaticFloatingEligible)
        .filter { candidate ->
            browserMediaCandidateMeetsAutomaticFloatingDuration(
                candidate = candidate,
                minimumDurationMillis = minimumDurationMillis,
            )
        }
        .sortedWith(
            compareByDescending<WebSessionBrowserMediaCandidate> { it.qualityHeight ?: 0 }
                .thenByDescending(WebSessionBrowserMediaCandidate::rankingScore)
                .thenByDescending(WebSessionBrowserMediaCandidate::lastDiscoveredAt)
                .thenBy(WebSessionBrowserMediaCandidate::id),
        )
        .firstOrNull()

internal fun browserMediaCandidateMeetsAutomaticFloatingDuration(
    candidate: WebSessionBrowserMediaCandidate,
    minimumDurationMillis: Long,
): Boolean {
    require(isSupportedAutomaticFloatingMinimumDuration(minimumDurationMillis)) {
        "Unsupported automatic floating minimum duration: $minimumDurationMillis"
    }
    return candidate.isLive ||
        candidate.durationMillis?.let { duration -> duration >= minimumDurationMillis } == true
}

internal fun automaticFloatingCandidateStabilityDelayMillis(
    candidate: WebSessionBrowserMediaCandidate,
): Long =
    when {
        BrowserMediaCandidateDiscoverySource.DOM_PLAY_EVENT in candidate.discoverySources -> 100L
        BrowserMediaCandidateDiscoverySource.DOM_CURRENT_SRC in candidate.discoverySources -> 180L
        else -> 300L
    }

internal fun resolveBrowserMediaCandidateQuality(
    width: Int?,
    height: Int?,
    url: String,
): BrowserMediaCandidateQuality? {
    if (width != null && width > 0 && height != null && height > 0) {
        val qualityHeight = min(width, height)
        return BrowserMediaCandidateQuality(
            width = width,
            height = qualityHeight,
            label = "${qualityHeight}P",
        )
    }
    if (height != null && height > 0) {
        return BrowserMediaCandidateQuality(
            width = width,
            height = height,
            label = "${height}P",
        )
    }

    val normalizedUrl = url.lowercase(Locale.ROOT)
    BROWSER_MEDIA_DIMENSION_PATTERN.find(normalizedUrl)?.let { match ->
        val parsedWidth = match.groupValues[1].toInt()
        val parsedHeight = match.groupValues[2].toInt()
        val qualityHeight = min(parsedWidth, parsedHeight)
        return BrowserMediaCandidateQuality(
            width = parsedWidth,
            height = qualityHeight,
            label = "${qualityHeight}P",
        )
    }
    val parsedHeight =
        BROWSER_MEDIA_QUALITY_PARAMETER_PATTERN.find(normalizedUrl)?.groupValues?.get(1)?.toInt()
            ?: BROWSER_MEDIA_HEIGHT_PATTERN.find(normalizedUrl)?.groupValues?.get(1)?.toInt()
            ?: return null
    return BrowserMediaCandidateQuality(
        width = null,
        height = parsedHeight,
        label = "${parsedHeight}P",
    )
}

private fun browserMediaResolutionScore(
    qualityHeight: Int?,
): Int {
    return when {
        qualityHeight == null -> 0
        qualityHeight >= 2_160 -> 80
        qualityHeight >= 1_440 -> 72
        qualityHeight >= 1_080 -> 65
        qualityHeight >= 720 -> 50
        qualityHeight >= 480 -> 35
        qualityHeight > 0 -> 15
        else -> 0
    }
}

private fun formatBrowserCandidateDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(1L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

private val BROWSER_MEDIA_NOISE_KEYWORDS =
    listOf(
        "advert",
        "/ads/",
        "doubleclick",
        "tracker",
        "analytics",
        "beacon",
        "poster",
        "thumbnail",
        "sprite",
        "preview",
        "sample",
        "subtitle",
        ".vtt",
        ".srt",
        ".ass",
    )

private val BROWSER_MEDIA_DIMENSION_PATTERN =
    Regex("""(?<!\d)(\d{3,5})[x×](\d{3,5})(?!\d)""", RegexOption.IGNORE_CASE)
private val BROWSER_MEDIA_QUALITY_PARAMETER_PATTERN =
    Regex(
        """(?:quality|resolution|res|height|video[_-]?height)[=:/_-]+(2160|1440|1080|720|576|540|480|360|240|144)""",
        RegexOption.IGNORE_CASE,
    )
private val BROWSER_MEDIA_HEIGHT_PATTERN =
    Regex("""(?<!\d)(2160|1440|1080|720|576|540|480|360|240|144)p(?!\d)""")
