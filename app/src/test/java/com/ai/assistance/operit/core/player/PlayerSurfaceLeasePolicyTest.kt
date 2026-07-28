package com.ai.assistance.operit.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfaceLeasePolicyTest {
    @Test
    fun firstSurfaceBecomesActiveOnlyAfterRegistrationAndAttach() {
        val prepared =
            preparePlayerSurfaceLease(
                PlayerSurfaceLeaseState(),
                PlayerSurfaceRole.FLOATING,
            )

        assertEquals(PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE, prepared.phase)
        assertEquals(1L, prepared.pendingTarget?.generation)
        assertTrue(prepared.nativeDetachCompleted)
        assertFalse(prepared.nativeSurfaceAttached)

        val registration =
            registerPlayerSurfaceOwner(prepared, PlayerSurfaceRole.FLOATING, "floating-1")
        val generation = requireNotNull(registration.generation)
        val active =
            activatePendingPlayerSurface(
                registration.state,
                PlayerSurfaceRole.FLOATING,
                "floating-1",
                generation,
            )

        assertEquals(PlayerSurfaceTransferPhase.FLOATING_ACTIVE, active.phase)
        assertEquals(PlayerSurfaceRole.FLOATING, active.currentOwner?.role)
        assertTrue(active.nativeSurfaceAttached)
        assertFalse(active.nativeDetachCompleted)
        assertNull(active.pendingTarget)
    }

    @Test
    fun floatingToFullscreenLaunchesOnlyAfterNativeDetachCompletes() {
        val floating = activeLease(PlayerSurfaceRole.FLOATING, "floating-1")
        val transferring = beginFloatingToFullscreenTransfer(floating)

        assertEquals(
            PlayerSurfaceTransferPhase.FLOATING_TO_FULLSCREEN_WAITING_FLOATING_DESTROY,
            transferring.phase,
        )
        assertNull(requestFullscreenActivityLaunchIfReady(transferring).fullscreenLaunchRequestId)

        val detached = completePlayerSurfaceDetach(transferring)

        assertEquals(PlayerSurfaceTransferPhase.WAITING_FULLSCREEN_SURFACE, detached.phase)
        assertEquals(PlayerSurfaceRole.FULLSCREEN, detached.pendingTarget?.role)
        assertTrue(detached.nativeDetachCompleted)
        assertFalse(detached.nativeSurfaceAttached)
        assertEquals(1L, detached.fullscreenLaunchRequestId)
    }

    @Test
    fun earlyFullscreenSurfaceStaysPendingUntilFloatingOwnerDetaches() {
        val transferring =
            beginFloatingToFullscreenTransfer(
                activeLease(PlayerSurfaceRole.FLOATING, "floating-1"),
            )
        val fullscreenRegistration =
            registerPlayerSurfaceOwner(
                transferring,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-1",
            )
        val fullscreenGeneration = requireNotNull(fullscreenRegistration.generation)

        assertEquals(
            PlayerSurfaceTransferPhase.REPLACING_FULLSCREEN_WAITING_OLD_DESTROY,
            fullscreenRegistration.state.phase,
        )
        assertThrows(IllegalStateException::class.java) {
            activatePendingPlayerSurface(
                fullscreenRegistration.state,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-1",
                fullscreenGeneration,
            )
        }

        val detached = completePlayerSurfaceDetach(fullscreenRegistration.state)
        val fullscreen =
            activatePendingPlayerSurface(
                detached,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-1",
                fullscreenGeneration,
            )

        assertEquals(PlayerSurfaceTransferPhase.FULLSCREEN_ACTIVE, fullscreen.phase)
        assertEquals(fullscreenGeneration, fullscreen.currentOwner?.generation)
        assertTrue(fullscreen.nativeSurfaceAttached)
    }

    @Test
    fun fullscreenToFloatingWaitsForFullscreenDetach() {
        val fullscreen = activeLease(PlayerSurfaceRole.FULLSCREEN, "fullscreen-1")
        val transferring = beginFullscreenToFloatingTransfer(fullscreen)
        val finishRequestId = requireNotNull(transferring.fullscreenFinishRequestId)

        assertEquals(
            PlayerSurfaceTransferPhase.FULLSCREEN_TO_FLOATING_WAITING_FULLSCREEN_DESTROY,
            transferring.phase,
        )
        assertEquals(
            finishRequestId,
            acknowledgeFullscreenFinishRequest(transferring, finishRequestId + 1L)
                .fullscreenFinishRequestId,
        )

        val acknowledged = acknowledgeFullscreenFinishRequest(transferring, finishRequestId)
        val detached = completePlayerSurfaceDetach(acknowledged)
        val registration =
            registerPlayerSurfaceOwner(detached, PlayerSurfaceRole.FLOATING, "floating-2")
        val generation = requireNotNull(registration.generation)
        val floating =
            activatePendingPlayerSurface(
                registration.state,
                PlayerSurfaceRole.FLOATING,
                "floating-2",
                generation,
            )

        assertEquals(PlayerSurfaceTransferPhase.FLOATING_ACTIVE, floating.phase)
        assertEquals(PlayerSurfaceRole.FLOATING, floating.currentOwner?.role)
        assertTrue(floating.nativeSurfaceAttached)
    }

    @Test
    fun staleRoleTokenAndGenerationCannotClaimOrReleaseLease() {
        val floating = activeLease(PlayerSurfaceRole.FLOATING, "floating-1")
        val generation = requireNotNull(floating.currentOwner).generation

        assertFalse(
            isCurrentPlayerSurfaceOwner(
                floating,
                PlayerSurfaceRole.FLOATING,
                "floating-stale",
                generation,
            ),
        )
        assertFalse(
            isCurrentPlayerSurfaceOwner(
                floating,
                PlayerSurfaceRole.FLOATING,
                "floating-1",
                generation + 1L,
            ),
        )

        val waitingFullscreen =
            completePlayerSurfaceDetach(beginFloatingToFullscreenTransfer(floating))
        val staleRegistration =
            registerPlayerSurfaceOwner(
                waitingFullscreen,
                PlayerSurfaceRole.FLOATING,
                "floating-stale",
            )

        assertNull(staleRegistration.generation)
        assertFalse(
            isPendingPlayerSurfaceTarget(
                waitingFullscreen,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-stale",
                requireNotNull(waitingFullscreen.pendingTarget).generation,
            ),
        )
    }

    @Test
    fun configChangeReplacementUsesNewGenerationAfterOldOwnerDetaches() {
        val fullscreen = activeLease(PlayerSurfaceRole.FULLSCREEN, "fullscreen-old")
        val oldGeneration = requireNotNull(fullscreen.currentOwner).generation
        val replacement =
            registerPlayerSurfaceOwner(
                fullscreen,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-new",
            )
        val replacementGeneration = requireNotNull(replacement.generation)

        assertTrue(replacementGeneration > oldGeneration)
        assertEquals(
            PlayerSurfaceTransferPhase.REPLACING_FULLSCREEN_WAITING_OLD_DESTROY,
            replacement.state.phase,
        )

        val detached = completePlayerSurfaceDetach(replacement.state)
        val active =
            activatePendingPlayerSurface(
                detached,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-new",
                replacementGeneration,
            )

        assertEquals(replacementGeneration, active.currentOwner?.generation)
        assertEquals(PlayerSurfaceTransferPhase.FULLSCREEN_ACTIVE, active.phase)
    }

    @Test
    fun ordinarySurfaceRecreationNeverReusesGeneration() {
        val floating = activeLease(PlayerSurfaceRole.FLOATING, "floating-1")
        val firstGeneration = requireNotNull(floating.currentOwner).generation
        val detached = completePlayerSurfaceDetach(floating)
        val registration =
            registerPlayerSurfaceOwner(
                detached,
                PlayerSurfaceRole.FLOATING,
                "floating-1",
            )

        assertTrue(requireNotNull(registration.generation) > firstGeneration)
        assertEquals(
            PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE,
            registration.state.phase,
        )
    }

    @Test
    fun closingRevokesPendingLeaseAndRejectsLateRegistration() {
        val fullscreen = activeLease(PlayerSurfaceRole.FULLSCREEN, "fullscreen-old")
        val replacement =
            registerPlayerSurfaceOwner(
                fullscreen,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-new",
            ).state
        val closing = beginClosingPlayerSurfaceLease(replacement)

        assertEquals(PlayerSurfaceTransferPhase.CLOSING, closing.phase)
        assertNull(closing.pendingTarget)
        assertNull(
            registerPlayerSurfaceOwner(
                closing,
                PlayerSurfaceRole.FULLSCREEN,
                "fullscreen-late",
            ).generation,
        )

        val closed = completeClosingPlayerSurfaceLease(closing)

        assertNull(closed.currentOwner)
        assertFalse(closed.nativeSurfaceAttached)
        assertTrue(closed.nativeDetachCompleted)
    }

    private fun activeLease(
        role: PlayerSurfaceRole,
        ownerToken: String,
    ): PlayerSurfaceLeaseState {
        val prepared = preparePlayerSurfaceLease(PlayerSurfaceLeaseState(), role)
        val registration = registerPlayerSurfaceOwner(prepared, role, ownerToken)
        return activatePendingPlayerSurface(
            registration.state,
            role,
            ownerToken,
            requireNotNull(registration.generation),
        )
    }
}
