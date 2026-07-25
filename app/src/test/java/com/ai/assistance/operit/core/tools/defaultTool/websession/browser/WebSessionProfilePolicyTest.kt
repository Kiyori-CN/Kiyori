package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionProfilePolicyTest {
    @Test
    fun `profile toggle is symmetric`() {
        assertEquals(WebSessionProfile.INCOGNITO, WebSessionProfile.NORMAL.opposite())
        assertEquals(WebSessionProfile.NORMAL, WebSessionProfile.INCOGNITO.opposite())
    }

    @Test
    fun `only normal profile persists browser history`() {
        assertTrue(WebSessionProfile.NORMAL.shouldPersistBrowserHistory)
        assertFalse(WebSessionProfile.INCOGNITO.shouldPersistBrowserHistory)
    }

    @Test
    fun `live incognito windows share a generation and retirement creates a new one`() {
        var generationId = 0
        val generation = WebSessionIncognitoGeneration { "generation-${++generationId}" }

        val firstName = generation.acquireProfileName()
        assertEquals(firstName, generation.acquireProfileName())
        assertEquals(firstName, generation.retireProfile())

        val secondName = generation.acquireProfileName()
        assertNotEquals(firstName, secondName)
        assertTrue(isKiyoriIncognitoProfileName(firstName))
        assertTrue(isKiyoriIncognitoProfileName(secondName))
    }

    @Test
    fun `stale cleanup succeeds when a profile already disappeared`() {
        var deletionAttempts = 0

        val completed =
            deleteStaleKiyoriIncognitoProfiles(
                existingProfileNames =
                    listOf("Default", "${KIYORI_INCOGNITO_PROFILE_PREFIX}retired"),
                deleteProfile = {
                    deletionAttempts += 1
                    false
                },
                remainingProfileNames = { listOf("Default") },
            )

        assertTrue(completed)
        assertEquals(1, deletionAttempts)
    }

    @Test
    fun `browser search reuses only a tab with the requested profile`() {
        assertTrue(
            !shouldCreateSessionForSearch(
                activeProfile = WebSessionProfile.NORMAL,
                requestedProfile = WebSessionProfile.NORMAL,
            ),
        )
        assertTrue(
            shouldCreateSessionForSearch(
                activeProfile = WebSessionProfile.NORMAL,
                requestedProfile = WebSessionProfile.INCOGNITO,
            ),
        )
        assertTrue(
            shouldCreateSessionForSearch(
                activeProfile = null,
                requestedProfile = WebSessionProfile.NORMAL,
            ),
        )
    }

    @Test
    fun `wire profile parsing is explicit and rejects unknown values`() {
        assertEquals(WebSessionProfile.NORMAL, WebSessionProfile.fromWireName("normal"))
        assertEquals(WebSessionProfile.INCOGNITO, WebSessionProfile.fromWireName(" INCOGNITO "))
        assertNull(WebSessionProfile.fromWireName("private"))
    }

    @Test
    fun `closing active tab selects adjacent tab from the same profile first`() {
        val ordered =
            listOf(
                BrowserSessionProfileEntry("normal-left", WebSessionProfile.NORMAL),
                BrowserSessionProfileEntry("private-left", WebSessionProfile.INCOGNITO),
                BrowserSessionProfileEntry("normal-closed", WebSessionProfile.NORMAL),
                BrowserSessionProfileEntry("private-right", WebSessionProfile.INCOGNITO),
                BrowserSessionProfileEntry("normal-right", WebSessionProfile.NORMAL),
            )

        assertEquals(
            "normal-right",
            resolveSessionAfterClose(
                orderedBeforeClose = ordered,
                closedSessionId = "normal-closed",
                remainingSessionIds = ordered.map { it.sessionId }.toSet() - "normal-closed",
                previouslyActiveSessionId = "normal-closed",
                wasActive = true,
            ),
        )
    }

    @Test
    fun `closing the final tab of one profile selects the nearest other profile`() {
        val ordered =
            listOf(
                BrowserSessionProfileEntry("private-left", WebSessionProfile.INCOGNITO),
                BrowserSessionProfileEntry("normal-closed", WebSessionProfile.NORMAL),
                BrowserSessionProfileEntry("private-right", WebSessionProfile.INCOGNITO),
            )

        assertEquals(
            "private-right",
            resolveSessionAfterClose(
                orderedBeforeClose = ordered,
                closedSessionId = "normal-closed",
                remainingSessionIds = setOf("private-left", "private-right"),
                previouslyActiveSessionId = "normal-closed",
                wasActive = true,
            ),
        )
    }

    @Test
    fun `closing a background tab preserves the active session`() {
        val ordered =
            listOf(
                BrowserSessionProfileEntry("active", WebSessionProfile.NORMAL),
                BrowserSessionProfileEntry("closed", WebSessionProfile.INCOGNITO),
            )

        assertEquals(
            "active",
            resolveSessionAfterClose(
                orderedBeforeClose = ordered,
                closedSessionId = "closed",
                remainingSessionIds = setOf("active"),
                previouslyActiveSessionId = "active",
                wasActive = false,
            ),
        )
    }

    @Test
    fun `omitted create profile uses the runtime default`() {
        val resolution =
            resolveSessionProfileRequest(
                requestedWireName = null,
                defaultProfile = WebSessionProfile.INCOGNITO,
                incognitoAvailability = WebSessionIncognitoAvailability.AVAILABLE,
            )

        assertEquals(
            WebSessionProfile.INCOGNITO,
            (resolution as WebSessionProfileResolution.Accepted).profile,
        )
    }

    @Test
    fun `incognito create is rejected when multi profile is unavailable`() {
        val resolution =
            resolveSessionProfileRequest(
                requestedWireName = "incognito",
                defaultProfile = WebSessionProfile.NORMAL,
                incognitoAvailability = WebSessionIncognitoAvailability.UNSUPPORTED,
            )

        assertEquals(
            WebSessionProfileRejection.INCOGNITO_UNAVAILABLE,
            (resolution as WebSessionProfileResolution.Rejected).reason,
        )
    }

    @Test
    fun `unknown create profile is rejected explicitly`() {
        val resolution =
            resolveSessionProfileRequest(
                requestedWireName = "private",
                defaultProfile = WebSessionProfile.NORMAL,
                incognitoAvailability = WebSessionIncognitoAvailability.AVAILABLE,
            )

        assertEquals(
            WebSessionProfileRejection.UNKNOWN_PROFILE,
            (resolution as WebSessionProfileResolution.Rejected).reason,
        )
    }

    @Test
    fun `window selector moves only when its final profile tab is removed`() {
        assertEquals(
            WebSessionProfile.NORMAL,
            resolveSelectedProfileAfterRemoval(
                selectedProfile = WebSessionProfile.NORMAL,
                remainingProfiles = listOf(WebSessionProfile.NORMAL, WebSessionProfile.INCOGNITO),
            ),
        )
        assertEquals(
            WebSessionProfile.INCOGNITO,
            resolveSelectedProfileAfterRemoval(
                selectedProfile = WebSessionProfile.NORMAL,
                remainingProfiles = listOf(WebSessionProfile.INCOGNITO),
            ),
        )
        assertTrue(
            resolveSelectedProfileAfterRemoval(
                selectedProfile = WebSessionProfile.INCOGNITO,
                remainingProfiles = emptyList(),
            ) == WebSessionProfile.INCOGNITO
        )
    }
}
