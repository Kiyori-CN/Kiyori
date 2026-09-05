package com.kiyori.integration.operit.onboarding

internal enum class KiyoriOnboardingStep {
    WELCOME,
    BROWSER_AND_MEDIA,
    AI_ASSISTANT,
    FILES_AND_TOOLS,
    AGREEMENT,
    PERMISSIONS,
}

internal fun nextKiyoriOnboardingStep(
    step: KiyoriOnboardingStep,
): KiyoriOnboardingStep? =
    KiyoriOnboardingStep.entries.getOrNull(step.ordinal + 1)

internal fun previousKiyoriOnboardingStep(
    step: KiyoriOnboardingStep,
): KiyoriOnboardingStep? =
    KiyoriOnboardingStep.entries.getOrNull(step.ordinal - 1)

internal enum class KiyoriOnboardingSwipeDirection {
    PREVIOUS,
    NEXT,
}

internal fun resolveKiyoriOnboardingSwipeTarget(
    step: KiyoriOnboardingStep,
    direction: KiyoriOnboardingSwipeDirection,
    agreementAccepted: Boolean,
    interactionLocked: Boolean,
): KiyoriOnboardingStep? {
    if (interactionLocked) {
        return null
    }
    return when (direction) {
        KiyoriOnboardingSwipeDirection.PREVIOUS ->
            previousKiyoriOnboardingStep(step)

        KiyoriOnboardingSwipeDirection.NEXT ->
            if (step == KiyoriOnboardingStep.AGREEMENT && !agreementAccepted) {
                null
            } else {
                nextKiyoriOnboardingStep(step)
            }
    }
}

internal fun shouldEnableKiyoriOnboardingPagerInput(
    step: KiyoriOnboardingStep,
    agreementAccepted: Boolean,
    interactionLocked: Boolean,
): Boolean =
    !interactionLocked &&
        (step != KiyoriOnboardingStep.AGREEMENT || agreementAccepted)

internal fun resolveInitialKiyoriOnboardingStep(
    agreementAccepted: Boolean,
    persistedStep: KiyoriOnboardingStep,
): KiyoriOnboardingStep =
    when {
        !agreementAccepted && persistedStep == KiyoriOnboardingStep.PERMISSIONS ->
            KiyoriOnboardingStep.AGREEMENT
        else -> persistedStep
    }

internal enum class KiyoriPermissionId {
    NOTIFICATIONS,
    MEDIA,
    CAMERA,
    MICROPHONE,
    LOCATION,
    BLUETOOTH,
    PHONE,
    SMS,
    LEGACY_STORAGE,
    READ_INSTALLED_APPS,
    REMOVE_RESTRICTED_SETTINGS,
    ALL_FILES,
    OVERLAY,
    WRITE_SETTINGS,
    USAGE_ACCESS,
    INSTALL_PACKAGES,
    BATTERY_OPTIMIZATION,
    NOTIFICATION_LISTENER,
    DEFAULT_ASSISTANT,
    ACCESSIBILITY,
    SHIZUKU,
    ROOT,
    SCREEN_CAPTURE,
}

internal enum class KiyoriPermissionStatus {
    GRANTED,
    PARTIAL,
    NOT_GRANTED,
    REQUIRES_SETUP,
    NOT_APPLICABLE,
    ON_DEMAND,
}

internal data class KiyoriPermissionSnapshot(
    val statuses: Map<KiyoriPermissionId, KiyoriPermissionStatus>,
) {
    init {
        require(statuses.keys == KiyoriPermissionId.entries.toSet()) {
            "Every Kiyori permission item must have one status"
        }
    }

    fun status(permissionId: KiyoriPermissionId): KiyoriPermissionStatus =
        checkNotNull(statuses[permissionId]) {
            "Missing Kiyori permission status: $permissionId"
        }

    val actionableIncomplete: List<KiyoriPermissionId>
        get() =
            KiyoriPermissionId.entries.filter { permissionId ->
                when (status(permissionId)) {
                    KiyoriPermissionStatus.PARTIAL,
                    KiyoriPermissionStatus.NOT_GRANTED,
                    KiyoriPermissionStatus.REQUIRES_SETUP,
                    -> true

                    KiyoriPermissionStatus.GRANTED,
                    KiyoriPermissionStatus.NOT_APPLICABLE,
                    KiyoriPermissionStatus.ON_DEMAND,
                    -> false
                }
            }

    val selectable: List<KiyoriPermissionId>
        get() =
            KiyoriPermissionId.entries.filter { permissionId ->
                when (status(permissionId)) {
                    KiyoriPermissionStatus.PARTIAL,
                    KiyoriPermissionStatus.NOT_GRANTED,
                    KiyoriPermissionStatus.REQUIRES_SETUP,
                    -> true

                    KiyoriPermissionStatus.GRANTED,
                    KiyoriPermissionStatus.NOT_APPLICABLE,
                    KiyoriPermissionStatus.ON_DEMAND,
                    -> false
                }
            }

    fun canSelect(permissionId: KiyoriPermissionId): Boolean =
        permissionId in selectable

    val completedCount: Int
        get() =
            statuses.values.count { status ->
                status == KiyoriPermissionStatus.GRANTED ||
                    status == KiyoriPermissionStatus.NOT_APPLICABLE ||
                    status == KiyoriPermissionStatus.ON_DEMAND
            }

    val totalCount: Int
        get() = KiyoriPermissionId.entries.size

    val isComplete: Boolean
        get() = actionableIncomplete.isEmpty()
}

internal fun sanitizeKiyoriPermissionSelection(
    snapshot: KiyoriPermissionSnapshot,
    selectedPermissionIds: Set<KiyoriPermissionId>,
): Set<KiyoriPermissionId> =
    KiyoriPermissionId.entries.filterTo(linkedSetOf()) { permissionId ->
        permissionId in selectedPermissionIds && snapshot.canSelect(permissionId)
    }

internal fun orderKiyoriPermissionIdsForAuthorization(
    permissionIds: List<KiyoriPermissionId>,
): List<KiyoriPermissionId> =
    permissionIds.sortedWith(
        compareBy { permissionId ->
            if (permissionId == KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS) 0 else 1
        },
    )
