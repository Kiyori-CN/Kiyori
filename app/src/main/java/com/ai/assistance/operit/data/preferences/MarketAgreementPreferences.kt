package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Stores the locally bundled plugin market agreement version accepted by the user. */
class MarketAgreementPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAgreementAccepted(): Boolean {
        return prefs.getString(ACCEPTED_VERSION_KEY, null) == CURRENT_MARKET_AGREEMENT_VERSION
    }

    fun acceptCurrentAgreement() {
        prefs.edit {
            putString(ACCEPTED_VERSION_KEY, CURRENT_MARKET_AGREEMENT_VERSION)
        }
    }

    companion object {
        private const val PREFS_NAME = "market_agreement_preferences"
        private const val ACCEPTED_VERSION_KEY = "accepted_market_agreement_version"

        /** Bump this value whenever the bundled market agreement changes substantively. */
        const val CURRENT_MARKET_AGREEMENT_VERSION = "2026-08-08.1"
    }
}
