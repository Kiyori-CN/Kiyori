package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactMarketCompatibilityTest {
    @Test
    fun operitMarketCompatibility_matchesAdoptedPlus9RuntimeContract() {
        assertEquals("1.12.0+9", BuildConfig.OPERIT_MARKET_COMPAT_VERSION)
        assertTrue(
            isAppVersionSupported(
                appVersion = BuildConfig.OPERIT_MARKET_COMPAT_VERSION,
                minSupportedAppVersion = "1.12.0+9",
                maxSupportedAppVersion = null
            )
        )
        assertFalse(
            isAppVersionSupported(
                appVersion = BuildConfig.OPERIT_MARKET_COMPAT_VERSION,
                minSupportedAppVersion = "1.12.0+10",
                maxSupportedAppVersion = null
            )
        )
    }

    @Test
    fun kiyoriProductVersion_isNotUsedAsOperitMarketCompatibility() {
        assertFalse(
            isAppVersionSupported(
                appVersion = BuildConfig.VERSION_NAME,
                minSupportedAppVersion = "1.10.0",
                maxSupportedAppVersion = "1.99.99"
            )
        )
        assertTrue(
            isAppVersionSupported(
                appVersion = BuildConfig.OPERIT_MARKET_COMPAT_VERSION,
                minSupportedAppVersion = "1.10.0",
                maxSupportedAppVersion = "1.99.99"
            )
        )
    }

    @Test
    fun operitMarketCompatibility_stillRejectsUnsupportedRanges() {
        assertFalse(
            isAppVersionSupported(
                appVersion = BuildConfig.OPERIT_MARKET_COMPAT_VERSION,
                minSupportedAppVersion = "2.0.0",
                maxSupportedAppVersion = "2.99.99"
            )
        )
    }
}
