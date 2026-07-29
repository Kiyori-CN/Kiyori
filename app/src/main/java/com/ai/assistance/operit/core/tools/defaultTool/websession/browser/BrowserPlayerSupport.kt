package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.Intent
import android.net.Uri
import com.ai.assistance.operit.core.player.PlayerMediaRequest
import com.ai.assistance.operit.core.player.PlayerMediaSource
import com.ai.assistance.operit.core.player.PlayerPresentation
import com.ai.assistance.operit.core.player.PlayerSession
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.ui.features.player.PlayerActivity

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

private fun browserMediaCandidateTitle(
    session: BrowserToolSession,
    candidate: BrowserMediaCandidate,
): String =
    candidate.pageTitle.takeIf(String::isNotBlank)
        ?: session.pageTitle.takeIf(String::isNotBlank)
        ?: Uri.parse(candidate.url).lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
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
    )
