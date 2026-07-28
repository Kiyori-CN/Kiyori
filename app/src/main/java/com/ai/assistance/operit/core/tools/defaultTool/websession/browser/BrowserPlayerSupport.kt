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
    runOnMainSync {
        val browserSession = getActiveSessionOnMain() ?: return@runOnMainSync false
        val candidate = findMediaCandidate(browserSession, candidateId) ?: return@runOnMainSync false
        require(candidate.directPlaybackReady) {
            "Media candidate does not have direct playback evidence: ${candidate.url}"
        }
        val presentation =
            if (browserSettingsStore.current.floatingSniffPlaybackEnabled) {
                PlayerPresentation.FLOATING_PLAYER
            } else {
                PlayerPresentation.FULLSCREEN_PLAYER
            }
        PlayerSession.getInstance(context).open(
            request =
                createBrowserPlayerMediaRequest(
                    sessionId = browserSession.id,
                    title = browserMediaCandidateTitle(browserSession, candidate),
                    candidate = candidate,
                ),
            presentation = presentation,
        )
        if (presentation == PlayerPresentation.FULLSCREEN_PLAYER) {
            launchFullscreenPlayerActivity()
        }
        true
    }

internal fun StandardBrowserSessionTools.downloadMediaCandidate(candidateId: String): Boolean =
    runOnMainSync {
        val browserSession = getActiveSessionOnMain() ?: return@runOnMainSync false
        val candidate = findMediaCandidate(browserSession, candidateId) ?: return@runOnMainSync false
        startMediaCandidateDownload(browserSession, candidate)
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
        playerSession.enterFullscreen()
        launchFullscreenPlayerActivity()
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

private fun StandardBrowserSessionTools.launchFullscreenPlayerActivity() {
    StandardBrowserSessionTools.browserHost?.prepareForPlayerFullscreen()
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
