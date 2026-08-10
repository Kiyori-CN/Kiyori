package com.ai.assistance.operit.ui.features.agreement.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriAgreementReadinessTest {
    @Test
    fun `acceptance requires only explicit consent`() {
        assertFalse(
            canAcceptKiyoriAgreement(checked = false),
        )
        assertTrue(
            canAcceptKiyoriAgreement(checked = true),
        )
    }
}
