package com.kiyori.integration.operit.onboarding

import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriOnboardingContractTest {
    @Test
    fun `only the welcome title is centered`() {
        assertEquals(TextAlign.Center, resolveKiyoriOnboardingTitleAlignment(eyebrow = null))
        assertEquals(
            TextAlign.Start,
            resolveKiyoriOnboardingTitleAlignment(eyebrow = "浏览与内容"),
        )
    }

    @Test
    fun `onboarding steps form one strict forward and backward sequence`() {
        assertEquals(
            KiyoriOnboardingStep.BROWSER_AND_MEDIA,
            nextKiyoriOnboardingStep(KiyoriOnboardingStep.WELCOME),
        )
        assertEquals(
            KiyoriOnboardingStep.WELCOME,
            previousKiyoriOnboardingStep(KiyoriOnboardingStep.BROWSER_AND_MEDIA),
        )
        assertNull(nextKiyoriOnboardingStep(KiyoriOnboardingStep.PERMISSIONS))
        assertNull(previousKiyoriOnboardingStep(KiyoriOnboardingStep.WELCOME))
    }

    @Test
    fun `persisted agreement step remains until explicit acceptance`() {
        assertEquals(
            KiyoriOnboardingStep.AGREEMENT,
            resolveInitialKiyoriOnboardingStep(
                agreementAccepted = false,
                persistedStep = KiyoriOnboardingStep.AGREEMENT,
            ),
        )
        assertEquals(
            KiyoriOnboardingStep.PERMISSIONS,
            resolveInitialKiyoriOnboardingStep(
                agreementAccepted = true,
                persistedStep = KiyoriOnboardingStep.PERMISSIONS,
            ),
        )
    }

    @Test
    fun `an unaccepted current agreement always returns to the agreement step`() {
        assertEquals(
            KiyoriOnboardingStep.AGREEMENT,
            resolveInitialKiyoriOnboardingStep(
                agreementAccepted = false,
                persistedStep = KiyoriOnboardingStep.PERMISSIONS,
            ),
        )
    }

    @Test
    fun `swipe navigation preserves the explicit agreement gate`() {
        assertEquals(
            KiyoriOnboardingStep.FILES_AND_TOOLS,
            resolveKiyoriOnboardingSwipeTarget(
                step = KiyoriOnboardingStep.AGREEMENT,
                direction = KiyoriOnboardingSwipeDirection.PREVIOUS,
                agreementAccepted = false,
                interactionLocked = false,
            ),
        )
        assertNull(
            resolveKiyoriOnboardingSwipeTarget(
                step = KiyoriOnboardingStep.AGREEMENT,
                direction = KiyoriOnboardingSwipeDirection.NEXT,
                agreementAccepted = false,
                interactionLocked = false,
            ),
        )
        assertEquals(
            KiyoriOnboardingStep.PERMISSIONS,
            resolveKiyoriOnboardingSwipeTarget(
                step = KiyoriOnboardingStep.AGREEMENT,
                direction = KiyoriOnboardingSwipeDirection.NEXT,
                agreementAccepted = true,
                interactionLocked = false,
            ),
        )
    }

    @Test
    fun `permission authorization locks every swipe direction`() {
        KiyoriOnboardingSwipeDirection.entries.forEach { direction ->
            assertNull(
                resolveKiyoriOnboardingSwipeTarget(
                    step = KiyoriOnboardingStep.PERMISSIONS,
                    direction = direction,
                    agreementAccepted = true,
                    interactionLocked = true,
                ),
            )
        }
        assertFalse(
            shouldEnableKiyoriOnboardingPagerInput(
                step = KiyoriOnboardingStep.PERMISSIONS,
                agreementAccepted = true,
                interactionLocked = true,
            ),
        )
    }

    @Test
    fun `native pager input resumes after the agreement is accepted`() {
        assertFalse(
            shouldEnableKiyoriOnboardingPagerInput(
                step = KiyoriOnboardingStep.AGREEMENT,
                agreementAccepted = false,
                interactionLocked = false,
            ),
        )
        assertTrue(
            shouldEnableKiyoriOnboardingPagerInput(
                step = KiyoriOnboardingStep.AGREEMENT,
                agreementAccepted = true,
                interactionLocked = false,
            ),
        )
        assertTrue(
            shouldEnableKiyoriOnboardingPagerInput(
                step = KiyoriOnboardingStep.PERMISSIONS,
                agreementAccepted = true,
                interactionLocked = false,
            ),
        )
    }

    @Test
    fun `permission snapshot counts only actionable incomplete states`() {
        val statuses =
            KiyoriPermissionId.entries.associateWith {
                KiyoriPermissionStatus.GRANTED
            }.toMutableMap()
        statuses[KiyoriPermissionId.LOCATION] = KiyoriPermissionStatus.PARTIAL
        statuses[KiyoriPermissionId.SHIZUKU] = KiyoriPermissionStatus.REQUIRES_SETUP
        statuses[KiyoriPermissionId.SCREEN_CAPTURE] = KiyoriPermissionStatus.ON_DEMAND
        val snapshot = KiyoriPermissionSnapshot(statuses)

        assertFalse(snapshot.isComplete)
        assertEquals(
            listOf(
                KiyoriPermissionId.LOCATION,
                KiyoriPermissionId.SHIZUKU,
            ),
            snapshot.actionableIncomplete,
        )
        assertEquals(
            listOf(
                KiyoriPermissionId.LOCATION,
                KiyoriPermissionId.SHIZUKU,
            ),
            snapshot.selectable,
        )
    }

    @Test
    fun `first-run permission contract excludes unused exact alarm access`() {
        assertFalse(KiyoriPermissionId.entries.any { it.name == "EXACT_ALARM" })
    }

    @Test
    fun `granted not applicable and on demand statuses form a complete snapshot`() {
        val statuses =
            KiyoriPermissionId.entries.associateWith { permissionId ->
                when (permissionId) {
                    KiyoriPermissionId.LEGACY_STORAGE ->
                        KiyoriPermissionStatus.NOT_APPLICABLE

                    KiyoriPermissionId.SCREEN_CAPTURE ->
                        KiyoriPermissionStatus.ON_DEMAND

                    else -> KiyoriPermissionStatus.GRANTED
                }
            }
        val snapshot = KiyoriPermissionSnapshot(statuses)

        assertTrue(snapshot.isComplete)
        assertTrue(snapshot.actionableIncomplete.isEmpty())
        assertEquals(snapshot.totalCount, snapshot.completedCount)
    }

    @Test
    fun `permission selection keeps only currently actionable items`() {
        val statuses =
            KiyoriPermissionId.entries.associateWith {
                KiyoriPermissionStatus.GRANTED
            }.toMutableMap()
        statuses[KiyoriPermissionId.MICROPHONE] =
            KiyoriPermissionStatus.NOT_GRANTED
        statuses[KiyoriPermissionId.SHIZUKU] =
            KiyoriPermissionStatus.REQUIRES_SETUP
        statuses[KiyoriPermissionId.SCREEN_CAPTURE] =
            KiyoriPermissionStatus.ON_DEMAND
        val snapshot = KiyoriPermissionSnapshot(statuses)

        assertEquals(
            linkedSetOf(
                KiyoriPermissionId.MICROPHONE,
                KiyoriPermissionId.SHIZUKU,
            ),
            sanitizeKiyoriPermissionSelection(
                snapshot = snapshot,
                selectedPermissionIds =
                    setOf(
                        KiyoriPermissionId.NOTIFICATIONS,
                        KiyoriPermissionId.MICROPHONE,
                        KiyoriPermissionId.SHIZUKU,
                        KiyoriPermissionId.SCREEN_CAPTURE,
                    ),
            ),
        )
    }

    @Test
    fun `restricted settings is processed before other selected permissions`() {
        assertEquals(
            listOf(
                KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
                KiyoriPermissionId.CAMERA,
                KiyoriPermissionId.SHIZUKU,
            ),
            orderKiyoriPermissionIdsForAuthorization(
                listOf(
                    KiyoriPermissionId.CAMERA,
                    KiyoriPermissionId.SHIZUKU,
                    KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
                ),
            ),
        )
    }

    @Test
    fun `first run and Settings share one complete permission catalog`() {
        val permissionIds =
            kiyoriPermissionGroups.flatMap(KiyoriPermissionGroupSpec::permissionIds)

        assertEquals(
            listOf(
                KiyoriPermissionGroupId.APPLICATION,
                KiyoriPermissionGroupId.SYSTEM_ACCESS,
                KiyoriPermissionGroupId.ADVANCED_CAPABILITIES,
            ),
            kiyoriPermissionGroups.map(KiyoriPermissionGroupSpec::id),
        )
        assertEquals(KiyoriPermissionId.entries, permissionIds)
        assertEquals(23, permissionIds.size)
        assertEquals(KiyoriPermissionId.entries.size, permissionIds.toSet().size)
        assertTrue(
            kiyoriPermissionGroups.all { group ->
                group.title.isNotBlank() &&
                    group.description.isNotBlank() &&
                    group.permissionIds.isNotEmpty()
            },
        )
    }

    @Test
    fun `permission actions keep runtime system and advanced capabilities explicit`() {
        assertEquals(
            KiyoriPermissionActionKind.REQUEST_RUNTIME,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.CAMERA,
                status = KiyoriPermissionStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.NONE,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.READ_INSTALLED_APPS,
                status = KiyoriPermissionStatus.GRANTED,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.OPEN_RESTRICTED_SETTINGS,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.REMOVE_RESTRICTED_SETTINGS,
                status = KiyoriPermissionStatus.REQUIRES_SETUP,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.OPEN_APPLICATION_SETTINGS,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.CAMERA,
                status = KiyoriPermissionStatus.GRANTED,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.OPEN_SYSTEM_SETTINGS,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.OVERLAY,
                status = KiyoriPermissionStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.CONFIGURE_ACCESSIBILITY,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.ACCESSIBILITY,
                status = KiyoriPermissionStatus.REQUIRES_SETUP,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.CONFIGURE_SHIZUKU,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.SHIZUKU,
                status = KiyoriPermissionStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.REQUEST_ROOT,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.ROOT,
                status = KiyoriPermissionStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            KiyoriPermissionActionKind.NONE,
            resolveKiyoriPermissionAction(
                permissionId = KiyoriPermissionId.SCREEN_CAPTURE,
                status = KiyoriPermissionStatus.ON_DEMAND,
            ),
        )
    }

    @Test
    fun `permission summary separates ready pending and on demand states`() {
        val statuses =
            KiyoriPermissionId.entries.associateWith {
                KiyoriPermissionStatus.GRANTED
            }.toMutableMap()
        statuses[KiyoriPermissionId.CAMERA] = KiyoriPermissionStatus.NOT_GRANTED
        statuses[KiyoriPermissionId.LEGACY_STORAGE] =
            KiyoriPermissionStatus.NOT_APPLICABLE
        statuses[KiyoriPermissionId.SCREEN_CAPTURE] =
            KiyoriPermissionStatus.ON_DEMAND

        val summary =
            summarizeKiyoriPermissions(
                KiyoriPermissionSnapshot(statuses),
            )

        assertEquals(21, summary.readyCount)
        assertEquals(1, summary.actionRequiredCount)
        assertEquals(1, summary.onDemandCount)
        assertEquals(KiyoriPermissionId.entries.size, summary.totalCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `permission snapshot rejects a missing status`() {
        val statuses =
            KiyoriPermissionId.entries
                .dropLast(1)
                .associateWith { KiyoriPermissionStatus.GRANTED }
        KiyoriPermissionSnapshot(statuses)
    }
}
