package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Intent
import androidx.core.net.toUri
import com.ai.assistance.operit.core.player.PlayerMediaRequest
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerPresentation
import com.ai.assistance.operit.core.player.PlayerQueueResolver
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.ui.features.player.PlayerActivity
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import kotlinx.coroutines.launch

internal fun StandardBrowserSessionTools.playMediaCandidate(candidateId: String): Boolean =
    openMediaCandidate(candidateId, PlayerPresentation.FULLSCREEN_PLAYER)

internal fun StandardBrowserSessionTools.playMediaCandidateFloating(candidateId: String): Boolean =
    openMediaCandidate(candidateId, PlayerPresentation.FLOATING_PLAYER)

private fun StandardBrowserSessionTools.openMediaCandidate(
    candidateId: String,
    presentation: PlayerPresentation,
): Boolean =
    runOnMainSync {
        val browserSession = getActiveSessionOnMain() ?: return@runOnMainSync false
        val candidate = findMediaCandidate(browserSession, candidateId) ?: return@runOnMainSync false
        require(candidate.isActionableVideo) {
            "Media candidate is not an actionable video: ${candidate.url}"
        }
        val playerSession = PlayerSession.getInstance(context)
        playerSession.open(
            request =
                createBrowserPlayerMediaRequest(
                    sessionId = browserSession.id,
                    title = browserMediaCandidateTitle(browserSession, candidate),
                    candidate = candidate,
                ),
            presentation = presentation,
        )
        if (presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            playerSession.requestFullscreenActivityLaunchWhenReady()
        }
        true
    }

internal fun StandardBrowserSessionTools.downloadMediaCandidate(
    candidateId: String,
    sourceSessionId: String? = null,
    destination: BrowserDownloadDestination = BrowserDownloadDestination.FollowSettings,
): Boolean =
    runOnMainSync {
        val browserSession =
            if (sourceSessionId == null) {
                getActiveSessionOnMain()
            } else {
                sessionById(sourceSessionId)
            } ?: return@runOnMainSync false
        val candidate = findMediaCandidate(browserSession, candidateId) ?: return@runOnMainSync false
        startMediaCandidateDownload(browserSession, candidate, destination)
    }

internal fun StandardBrowserSessionTools.toggleBrowserPlayerPause() {
    runOnMainSync<Unit> {
        PlayerSession.getInstance(context).togglePause()
    }
}

internal fun StandardBrowserSessionTools.openBrowserPlayerFullscreen() {
    runOnMainSync<Unit> {
        val playerSession = PlayerSession.getInstance(context)
        check(playerSession.state.value.presentation == PlayerPresentation.FLOATING_PLAYER) {
            "Only a floating player can enter browser fullscreen presentation"
        }
        playerSession.requestFullscreenFromFloating()
    }
}

internal fun StandardBrowserSessionTools.closeBrowserPlayer() {
    runOnMainSync<Unit> {
        PlayerSession.getInstance(context).close()
    }
}

internal fun StandardBrowserSessionTools.closePlayerOwnedByBrowserSession(sessionId: String) {
    val playerSession = PlayerSession.getInstance(context)
    if (playerSession.state.value.request?.sourceSessionId == sessionId) {
        playerSession.close()
    }
}

internal fun StandardBrowserSessionTools.launchBrowserPlayerFullscreenActivity() {
    context.startActivity(
        PlayerActivity.createReuseSessionIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

internal fun StandardBrowserSessionTools.playHistoryMedia(
    entry: WebSessionHistoryEntry,
): Boolean =
    runOnMainSync {
        require(
            entry.category == WebSessionHistoryCategory.VIDEO ||
                entry.category == WebSessionHistoryCategory.MUSIC
        ) {
            "Only media history entries can be sent to PlayerSession"
        }
        val origin =
            entry.mediaOrigin ?: resolveWebSessionHistoryMediaOrigin(entry.url)
        val activeSession =
            if (origin == WebSessionHistoryMediaOrigin.ONLINE) {
                getActiveSessionOnMain() ?: return@runOnMainSync false
            } else {
                null
            }
        val headers = linkedMapOf<String, String>()
        activeSession?.let { session ->
            session.webView.settings.userAgentString
                ?.takeIf(String::isNotBlank)
                ?.let { value -> headers["User-Agent"] = value }
            profileManager.cookieManagerFor(session.webView, session.profile)
                .getCookie(entry.url)
                ?.takeIf(String::isNotBlank)
                ?.let { value -> headers["Cookie"] = value }
            entry.sourcePageUrl
                .takeIf(String::isNotBlank)
                ?.let { value -> headers["Referer"] = value }
        }
        val playerSession = PlayerSession.getInstance(context)
        val request =
            PlayerMediaRequest(
                requestId = UUID.randomUUID().toString(),
                uri = entry.url,
                title = entry.title,
                headers = headers,
                source =
                    if (origin == WebSessionHistoryMediaOrigin.ONLINE) {
                        PlayerMediaSource.BROWSER_CANDIDATE
                    } else {
                        PlayerMediaSource.EXTERNAL_INTENT
                    },
                sourceSessionId = activeSession?.id,
                cookieScopeUrl = entry.sourcePageUrl.takeIf(String::isNotBlank),
                sourcePageUrl = entry.sourcePageUrl.takeIf(String::isNotBlank),
            )
        playerSession.open(
            request = request,
            presentation = PlayerPresentation.FULLSCREEN_PLAYER,
        )
        if (origin == WebSessionHistoryMediaOrigin.LOCAL) {
            ioScope.launch {
                try {
                    val queue = PlayerQueueResolver.resolve(context.applicationContext, request)
                    StandardBrowserSessionTools.mainHandler.post {
                        playerSession.replaceQueueForCurrent(request.requestId, queue)
                    }
                } catch (error: Exception) {
                    AppLogger.e(
                        "BrowserPlayerSupport",
                        "Failed to restore the local history playback queue",
                        error,
                    )
                }
            }
        }
        playerSession.requestFullscreenActivityLaunchWhenReady()
        true
    }

private fun browserMediaCandidateTitle(
    session: BrowserToolSession,
    candidate: BrowserMediaCandidate,
): String =
    candidate.pageTitle.takeIf(String::isNotBlank)
        ?: session.pageTitle.takeIf(String::isNotBlank)
        ?: candidate.url.toUri().lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: "网页视频"

internal fun createBrowserPlayerMediaRequest(
    sessionId: String,
    title: String,
    candidate: BrowserMediaCandidate,
): PlayerMediaRequest =
    PlayerMediaRequest(
        requestId = candidate.id,
        uri = candidate.url,
        title = title,
        headers = candidate.requestHeaders,
        source = PlayerMediaSource.BROWSER_CANDIDATE,
        sourceSessionId = sessionId,
        cookieScopeUrl = candidate.cookieScopeUrl,
        sourcePageUrl = candidate.pageUrl,
    )
