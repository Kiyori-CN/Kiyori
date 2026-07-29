package com.ai.assistance.operit.core.player

internal enum class PlayerSurfaceRole {
    FLOATING,
    FULLSCREEN,
}

internal enum class PlayerSurfaceTransferPhase {
    NONE,
    WAITING_FLOATING_SURFACE,
    FLOATING_ACTIVE,
    FLOATING_TO_FULLSCREEN_WAITING_FLOATING_DESTROY,
    WAITING_FULLSCREEN_SURFACE,
    FULLSCREEN_ACTIVE,
    FULLSCREEN_TO_FLOATING_WAITING_FULLSCREEN_DESTROY,
    REPLACING_FLOATING_WAITING_OLD_DESTROY,
    REPLACING_FULLSCREEN_WAITING_OLD_DESTROY,
    CLOSING,
}

internal enum class PlayerNativeSurfaceState {
    DETACHED,
    ATTACHING,
    ATTACHED,
    DETACHING,
}

internal data class PlayerSurfaceLeaseOwner(
    val role: PlayerSurfaceRole,
    val ownerToken: String,
    val generation: Long,
)

internal data class PlayerSurfaceLeaseTarget(
    val role: PlayerSurfaceRole,
    val ownerToken: String?,
    val generation: Long,
)

internal data class PlayerSurfaceLeaseState(
    val phase: PlayerSurfaceTransferPhase = PlayerSurfaceTransferPhase.NONE,
    val currentOwner: PlayerSurfaceLeaseOwner? = null,
    val pendingTarget: PlayerSurfaceLeaseTarget? = null,
    val generation: Long = 0L,
    val nativeState: PlayerNativeSurfaceState = PlayerNativeSurfaceState.DETACHED,
    val transferTarget: PlayerSurfaceRole? = null,
    val activityRequestGeneration: Long = 0L,
    val fullscreenLaunchRequestId: Long? = null,
    val fullscreenFinishRequestId: Long? = null,
) {
    val nativeSurfaceAttached: Boolean
        get() =
            nativeState == PlayerNativeSurfaceState.ATTACHED ||
                nativeState == PlayerNativeSurfaceState.DETACHING

    val nativeDetachCompleted: Boolean
        get() = nativeState == PlayerNativeSurfaceState.DETACHED
}

internal data class PlayerSurfaceRegistration(
    val state: PlayerSurfaceLeaseState,
    val generation: Long?,
)

internal fun preparePlayerSurfaceLease(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
): PlayerSurfaceLeaseState {
    if (state.currentOwner != null || state.pendingTarget?.role == role) return state
    val generation = state.generation + 1L
    return state.copy(
        phase = waitingPhase(role),
        pendingTarget = PlayerSurfaceLeaseTarget(role, null, generation),
        generation = generation,
        transferTarget = role,
        fullscreenLaunchRequestId = null,
        fullscreenFinishRequestId = null,
    )
}

internal fun registerPlayerSurfaceOwner(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
): PlayerSurfaceRegistration {
    require(ownerToken.isNotBlank()) { "Player surface owner token is blank" }
    if (state.phase == PlayerSurfaceTransferPhase.CLOSING) {
        return PlayerSurfaceRegistration(state, null)
    }
    state.currentOwner
        ?.takeIf { it.role == role && it.ownerToken == ownerToken }
        ?.let { return PlayerSurfaceRegistration(state, it.generation) }
    state.pendingTarget
        ?.takeIf { it.role == role && (it.ownerToken == null || it.ownerToken == ownerToken) }
        ?.let { target ->
            return PlayerSurfaceRegistration(
                state.copy(pendingTarget = target.copy(ownerToken = ownerToken)),
                target.generation,
            )
        }

    val current = state.currentOwner
    val expectedRole = state.pendingTarget?.role ?: state.transferTarget
    if (
        current == null &&
            expectedRole != role
    ) {
        return PlayerSurfaceRegistration(state, null)
    }
    if (
        current != null &&
            current.role != role &&
            state.transferTarget != role
    ) {
        return PlayerSurfaceRegistration(state, null)
    }
    if (
        current != null &&
            current.role == role &&
            state.transferTarget != null &&
            state.transferTarget != role
    ) {
        return PlayerSurfaceRegistration(state, null)
    }
    val generation = state.generation + 1L
    val nextPhase =
        if (current == null) {
            waitingPhase(role)
        } else {
            replacingPhase(role)
        }
    return PlayerSurfaceRegistration(
        state.copy(
            phase = nextPhase,
            pendingTarget = PlayerSurfaceLeaseTarget(role, ownerToken, generation),
            generation = generation,
            transferTarget = role,
        ),
        generation,
    )
}

internal fun beginPendingPlayerSurfaceAttach(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
    generation: Long,
): PlayerSurfaceLeaseState {
    check(state.currentOwner == null) { "A native Surface is already active" }
    check(state.nativeState == PlayerNativeSurfaceState.DETACHED) {
        "Native Surface is not ready for attach"
    }
    val pending = requireNotNull(state.pendingTarget) { "No pending Surface lease" }
    check(
        pending.role == role &&
            pending.ownerToken == ownerToken &&
            pending.generation == generation,
    ) {
        "Pending Surface lease identity does not match"
    }
    return state.copy(nativeState = PlayerNativeSurfaceState.ATTACHING)
}

internal fun activatePendingPlayerSurface(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
    generation: Long,
): PlayerSurfaceLeaseState {
    check(state.currentOwner == null) { "A native Surface is already active" }
    check(state.nativeState == PlayerNativeSurfaceState.ATTACHING) {
        "Native Surface attach has not been sent"
    }
    val pending = requireNotNull(state.pendingTarget) { "No pending Surface lease" }
    check(
        pending.role == role &&
            pending.ownerToken == ownerToken &&
            pending.generation == generation,
    ) {
        "Pending Surface lease identity does not match"
    }
    return state.copy(
        phase = activePhase(role),
        currentOwner = PlayerSurfaceLeaseOwner(role, ownerToken, generation),
        pendingTarget = null,
        nativeState = PlayerNativeSurfaceState.ATTACHED,
        transferTarget = null,
    )
}

internal fun rejectPendingPlayerSurfaceAttach(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
    generation: Long,
): PlayerSurfaceLeaseState {
    check(state.currentOwner == null) { "A native Surface is already active" }
    check(state.nativeState == PlayerNativeSurfaceState.ATTACHING) {
        "Native Surface attach is not pending"
    }
    val pending = requireNotNull(state.pendingTarget) { "No pending Surface lease" }
    check(
        pending.role == role &&
            pending.ownerToken == ownerToken &&
            pending.generation == generation,
    ) {
        "Pending Surface lease identity does not match"
    }
    return state.copy(nativeState = PlayerNativeSurfaceState.DETACHED)
}

internal fun rejectPlayerSurfaceDetach(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    check(state.nativeState == PlayerNativeSurfaceState.DETACHING) {
        "Native Surface detach is not pending"
    }
    return state.copy(nativeState = PlayerNativeSurfaceState.ATTACHED)
}

internal fun requestFullscreenActivityLaunchIfReady(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    if (state.fullscreenLaunchRequestId != null || state.currentOwner != null) return state
    val targetRole = state.pendingTarget?.role ?: state.transferTarget
    check(targetRole == PlayerSurfaceRole.FULLSCREEN) {
        "Fullscreen Activity cannot launch without a pending fullscreen Surface"
    }
    check(state.nativeDetachCompleted) {
        "Fullscreen Activity cannot launch before native Surface detach completes"
    }
    val requestId = state.activityRequestGeneration + 1L
    return state.copy(
        activityRequestGeneration = requestId,
        fullscreenLaunchRequestId = requestId,
    )
}

internal fun beginFloatingToFullscreenTransfer(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    check(state.currentOwner?.role == PlayerSurfaceRole.FLOATING) {
        "Floating Surface must be active before fullscreen transfer"
    }
    check(state.phase == PlayerSurfaceTransferPhase.FLOATING_ACTIVE) {
        "Floating Surface lease is not active"
    }
    return state.copy(
        phase = PlayerSurfaceTransferPhase.FLOATING_TO_FULLSCREEN_WAITING_FLOATING_DESTROY,
        pendingTarget = null,
        transferTarget = PlayerSurfaceRole.FULLSCREEN,
    )
}

internal fun beginFullscreenToFloatingTransfer(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    check(state.currentOwner?.role == PlayerSurfaceRole.FULLSCREEN) {
        "Fullscreen Surface must be active before floating transfer"
    }
    check(state.phase == PlayerSurfaceTransferPhase.FULLSCREEN_ACTIVE) {
        "Fullscreen Surface lease is not active"
    }
    return requestFullscreenActivityFinish(
        state.copy(
            phase = PlayerSurfaceTransferPhase.FULLSCREEN_TO_FLOATING_WAITING_FULLSCREEN_DESTROY,
            pendingTarget = null,
            transferTarget = PlayerSurfaceRole.FLOATING,
        ),
    )
}

internal fun requestFullscreenActivityFinish(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    if (state.fullscreenFinishRequestId != null) return state
    val requestId = state.activityRequestGeneration + 1L
    return state.copy(
        activityRequestGeneration = requestId,
        fullscreenFinishRequestId = requestId,
    )
}

internal fun beginPlayerSurfaceDetach(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
    generation: Long,
): PlayerSurfaceLeaseState {
    check(
        isCurrentPlayerSurfaceOwner(
            state = state,
            role = role,
            ownerToken = ownerToken,
            generation = generation,
        ),
    ) {
        "Current Surface lease identity does not match"
    }
    check(state.nativeState == PlayerNativeSurfaceState.ATTACHED) {
        "Native Surface is not attached"
    }
    return state.copy(nativeState = PlayerNativeSurfaceState.DETACHING)
}

internal fun completePlayerSurfaceDetach(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    check(state.nativeState == PlayerNativeSurfaceState.DETACHING) {
        "Native Surface detach has not been sent"
    }
    val previousRole = requireNotNull(state.currentOwner).role
    val detached =
        state.copy(
            currentOwner = null,
            nativeState = PlayerNativeSurfaceState.DETACHED,
        )
    return when (state.phase) {
        PlayerSurfaceTransferPhase.FLOATING_TO_FULLSCREEN_WAITING_FLOATING_DESTROY -> {
            requestFullscreenActivityLaunchIfReady(
                detached.waitForNextSurface(PlayerSurfaceRole.FULLSCREEN),
            )
        }
        PlayerSurfaceTransferPhase.FULLSCREEN_TO_FLOATING_WAITING_FULLSCREEN_DESTROY ->
            detached.waitForNextSurface(PlayerSurfaceRole.FLOATING)
        PlayerSurfaceTransferPhase.REPLACING_FLOATING_WAITING_OLD_DESTROY ->
            detached.copy(phase = PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE)
        PlayerSurfaceTransferPhase.REPLACING_FULLSCREEN_WAITING_OLD_DESTROY ->
            detached.copy(phase = PlayerSurfaceTransferPhase.WAITING_FULLSCREEN_SURFACE)
        PlayerSurfaceTransferPhase.CLOSING ->
            detached.copy(phase = PlayerSurfaceTransferPhase.CLOSING, pendingTarget = null)
        else ->
            detached.waitForNextSurface(previousRole)
    }
}

internal fun beginClosingPlayerSurfaceLease(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState =
    state.copy(
        phase = PlayerSurfaceTransferPhase.CLOSING,
        pendingTarget = null,
        transferTarget = null,
        fullscreenLaunchRequestId = null,
        fullscreenFinishRequestId = null,
    )

internal fun completeClosingPlayerSurfaceLease(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState {
    check(state.phase == PlayerSurfaceTransferPhase.CLOSING) {
        "Player Surface lease is not closing"
    }
    return state.copy(
        currentOwner = null,
        pendingTarget = null,
        nativeState = PlayerNativeSurfaceState.DETACHED,
        transferTarget = null,
        fullscreenLaunchRequestId = null,
        fullscreenFinishRequestId = null,
    )
}

internal fun resetPlayerSurfaceLeaseAfterRuntimeStop(
    state: PlayerSurfaceLeaseState,
): PlayerSurfaceLeaseState =
    state.copy(
        phase =
            if (state.phase == PlayerSurfaceTransferPhase.CLOSING) {
                PlayerSurfaceTransferPhase.CLOSING
            } else {
                PlayerSurfaceTransferPhase.NONE
            },
        currentOwner = null,
        pendingTarget = null,
        nativeState = PlayerNativeSurfaceState.DETACHED,
        transferTarget = null,
        fullscreenLaunchRequestId = null,
        fullscreenFinishRequestId = null,
    )

internal fun isCurrentPlayerSurfaceOwner(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
    generation: Long,
): Boolean =
    state.currentOwner?.let { owner ->
        owner.role == role &&
            owner.ownerToken == ownerToken &&
            owner.generation == generation
    } == true

internal fun isPendingPlayerSurfaceTarget(
    state: PlayerSurfaceLeaseState,
    role: PlayerSurfaceRole,
    ownerToken: String,
    generation: Long,
): Boolean =
    state.pendingTarget?.let { target ->
        target.role == role &&
            target.ownerToken == ownerToken &&
            target.generation == generation
    } == true

internal fun acknowledgeFullscreenLaunchRequest(
    state: PlayerSurfaceLeaseState,
    requestId: Long,
): PlayerSurfaceLeaseState =
    if (state.fullscreenLaunchRequestId == requestId) {
        state.copy(fullscreenLaunchRequestId = null)
    } else {
        state
    }

internal fun acknowledgeFullscreenFinishRequest(
    state: PlayerSurfaceLeaseState,
    requestId: Long,
): PlayerSurfaceLeaseState =
    if (state.fullscreenFinishRequestId == requestId) {
        state.copy(fullscreenFinishRequestId = null)
    } else {
        state
    }

private fun PlayerSurfaceLeaseState.waitForNextSurface(
    role: PlayerSurfaceRole,
): PlayerSurfaceLeaseState {
    if (pendingTarget?.role == role) {
        return copy(
            phase = waitingPhase(role),
            transferTarget = role,
        )
    }
    val nextGeneration = generation + 1L
    return copy(
        phase = waitingPhase(role),
        pendingTarget = PlayerSurfaceLeaseTarget(role, null, nextGeneration),
        generation = nextGeneration,
        transferTarget = role,
    )
}

private fun waitingPhase(role: PlayerSurfaceRole): PlayerSurfaceTransferPhase =
    when (role) {
        PlayerSurfaceRole.FLOATING -> PlayerSurfaceTransferPhase.WAITING_FLOATING_SURFACE
        PlayerSurfaceRole.FULLSCREEN -> PlayerSurfaceTransferPhase.WAITING_FULLSCREEN_SURFACE
    }

private fun activePhase(role: PlayerSurfaceRole): PlayerSurfaceTransferPhase =
    when (role) {
        PlayerSurfaceRole.FLOATING -> PlayerSurfaceTransferPhase.FLOATING_ACTIVE
        PlayerSurfaceRole.FULLSCREEN -> PlayerSurfaceTransferPhase.FULLSCREEN_ACTIVE
    }

private fun replacingPhase(role: PlayerSurfaceRole): PlayerSurfaceTransferPhase =
    when (role) {
        PlayerSurfaceRole.FLOATING ->
            PlayerSurfaceTransferPhase.REPLACING_FLOATING_WAITING_OLD_DESTROY
        PlayerSurfaceRole.FULLSCREEN ->
            PlayerSurfaceTransferPhase.REPLACING_FULLSCREEN_WAITING_OLD_DESTROY
    }
