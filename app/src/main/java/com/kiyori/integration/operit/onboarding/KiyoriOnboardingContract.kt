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

internal fun shouldEnableKiyoriOnboardingPagerInput(
    interactionLocked: Boolean,
): Boolean = !interactionLocked

// 未同意时协议就是 Pager 的最后一页。保留原生反向拖动，不在协议页切换手势所有者。
internal fun kiyoriOnboardingPageCount(agreementAccepted: Boolean): Int =
    if (agreementAccepted) KiyoriOnboardingStep.entries.size
    else KiyoriOnboardingStep.AGREEMENT.ordinal + 1

internal fun resolveInitialKiyoriOnboardingStep(
    agreementAccepted: Boolean,
    persistedStep: KiyoriOnboardingStep,
    startFromBeginning: Boolean = false,
): KiyoriOnboardingStep =
    when {
        startFromBeginning -> KiyoriOnboardingStep.WELCOME
        !agreementAccepted && persistedStep == KiyoriOnboardingStep.PERMISSIONS ->
            KiyoriOnboardingStep.AGREEMENT
        else -> persistedStep
    }

internal fun canNavigateKiyoriOnboarding(
    currentStep: KiyoriOnboardingStep,
    targetStep: KiyoriOnboardingStep,
    agreementAccepted: Boolean,
    interactionLocked: Boolean,
): Boolean =
    !interactionLocked && currentStep != targetStep &&
        targetStep.ordinal < kiyoriOnboardingPageCount(agreementAccepted)

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
