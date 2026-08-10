package com.kiyori.app.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriMainStartupGateCoordinatorTest {
    @Test
    fun `incomplete onboarding has precedence over agreement state`() {
        assertEquals(
            KiyoriMainStartupDestination.ONBOARDING,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = false,
                onboardingCompleted = false,
            ),
        )
        assertEquals(
            KiyoriMainStartupDestination.ONBOARDING,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = true,
                onboardingCompleted = false,
            ),
        )
    }

    @Test
    fun `completed onboarding with outdated agreement requires confirmation`() {
        assertEquals(
            KiyoriMainStartupDestination.AGREEMENT,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = false,
                onboardingCompleted = true,
            ),
        )
    }

    @Test
    fun `completed onboarding and current agreement show content`() {
        assertEquals(
            KiyoriMainStartupDestination.CONTENT,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = true,
                onboardingCompleted = true,
            ),
        )
    }

    @Test
    fun `accepting agreement updates persistent owner and memory projection`() {
        var agreementAccepted = false
        val coordinator =
            coordinator(
                isAgreementAccepted = { agreementAccepted },
                acceptCurrentAgreement = { agreementAccepted = true },
                isOnboardingCompleted = { false },
            )

        assertFalse(coordinator.isAgreementAccepted)
        coordinator.acceptCurrentAgreement()

        assertTrue(agreementAccepted)
        assertTrue(coordinator.isAgreementAccepted)
        assertEquals(KiyoriMainStartupDestination.ONBOARDING, coordinator.destination)
    }

    @Test
    fun `completing onboarding exposes content only after agreement acceptance`() {
        var onboardingCompleted = false
        val coordinator =
            coordinator(
                isAgreementAccepted = { true },
                isOnboardingCompleted = { onboardingCompleted },
                completeOnboarding = { onboardingCompleted = true },
            )

        assertFalse(coordinator.isReadyForContent)
        coordinator.completeOnboarding()

        assertTrue(onboardingCompleted)
        assertTrue(coordinator.isReadyForContent)
        assertEquals(KiyoriMainStartupDestination.CONTENT, coordinator.destination)
    }

    private fun coordinator(
        isAgreementAccepted: () -> Boolean,
        acceptCurrentAgreement: () -> Unit = {},
        isOnboardingCompleted: () -> Boolean,
        completeOnboarding: () -> Unit = {},
    ): KiyoriMainStartupGateCoordinator =
        KiyoriMainStartupGateCoordinator(
            isAgreementAccepted = isAgreementAccepted,
            acceptCurrentAgreement = acceptCurrentAgreement,
            isOnboardingCompleted = isOnboardingCompleted,
            completeOnboarding = completeOnboarding,
        )
}
