package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import java.util.Locale
import kotlin.math.roundToInt

internal data class BrowserMediaCandidateRanking(
    val score: Int,
    val isRecommended: Boolean,
    val automaticFloatingEligible: Boolean,
    val summary: String,
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
                .thenByDescending { it.second.score }
                .thenByDescending { it.first.durationMillis != null || it.first.isLive }
                .thenByDescending { it.first.lastDiscoveredAt }
                .thenBy { it.first.id },
        )
        .map(Pair<BrowserMediaCandidate, BrowserMediaCandidateRanking>::first)

internal fun rankBrowserMediaCandidate(
    candidate: BrowserMediaCandidate,
): BrowserMediaCandidateRanking {
    if (!candidate.isActionableVideo) {
        return BrowserMediaCandidateRanking(
            score = Int.MIN_VALUE,
            isRecommended = false,
            automaticFloatingEligible = false,
            summary = "不可执行的视频线索",
        )
    }

    val videoFormat =
        requireNotNull(candidate.videoFormat) {
            "An actionable browser media candidate must have an exact video format"
        }
    val reasons = mutableListOf<String>()
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
        browserMediaResolutionScore(candidate.videoWidth, candidate.videoHeight)
    score += resolutionScore
    if (resolutionScore >= 35) {
        reasons += "${candidate.videoWidth}×${candidate.videoHeight}"
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
                    (candidate.durationMillis ?: 0L) >= 60_000L
            )

    return BrowserMediaCandidateRanking(
        score = score,
        isRecommended = isRecommended,
        automaticFloatingEligible = automaticFloatingEligible,
        summary = reasons.distinct().take(3).joinToString(" · "),
    )
}

internal fun selectAutomaticFloatingMediaCandidate(
    candidates: List<WebSessionBrowserMediaCandidate>,
): WebSessionBrowserMediaCandidate? =
    candidates
        .asSequence()
        .filter(WebSessionBrowserMediaCandidate::automaticFloatingEligible)
        .sortedWith(
            compareByDescending<WebSessionBrowserMediaCandidate>(
                WebSessionBrowserMediaCandidate::rankingScore,
            )
                .thenByDescending(WebSessionBrowserMediaCandidate::lastDiscoveredAt)
                .thenBy(WebSessionBrowserMediaCandidate::id),
        )
        .firstOrNull()

private fun browserMediaResolutionScore(
    width: Int?,
    height: Int?,
): Int {
    val pixels = (width ?: 0).toLong() * (height ?: 0).toLong()
    return when {
        pixels >= 3_840L * 2_160L -> 80
        pixels >= 1_920L * 1_080L -> 65
        pixels >= 1_280L * 720L -> 50
        pixels >= 854L * 480L -> 35
        pixels > 0L -> 15
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
