package com.kiyori.integration.operit.onboarding

import android.content.Context
import androidx.core.content.edit

internal class KiyoriOnboardingPreferences(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean =
        preferences.getString(KEY_COMPLETED_VERSION, null) == CURRENT_ONBOARDING_VERSION

    fun readCurrentStep(): KiyoriOnboardingStep =
        preferences.getString(KEY_CURRENT_STEP, null)?.let(KiyoriOnboardingStep::valueOf)
            ?: KiyoriOnboardingStep.WELCOME

    fun saveCurrentStep(step: KiyoriOnboardingStep) {
        preferences.edit {
            putString(KEY_CURRENT_STEP, step.name)
        }
    }

    fun complete() {
        preferences.edit {
            putString(KEY_COMPLETED_VERSION, CURRENT_ONBOARDING_VERSION)
            putString(KEY_CURRENT_STEP, KiyoriOnboardingStep.PERMISSIONS.name)
            remove(KEY_SELECTED_PERMISSIONS)
        }
    }

    fun readSelectedPermissions(): Set<KiyoriPermissionId> =
        preferences.getString(KEY_SELECTED_PERMISSIONS, null)
            ?.split(',')
            ?.filter(String::isNotEmpty)
            ?.map(KiyoriPermissionId::valueOf)
            ?.toSet()
            ?: emptySet()

    fun saveSelectedPermissions(permissionIds: Set<KiyoriPermissionId>) {
        preferences.edit {
            putString(
                KEY_SELECTED_PERMISSIONS,
                permissionIds.joinToString(",") { it.name },
            )
        }
    }

    fun clearSelectedPermissions() {
        preferences.edit {
            remove(KEY_SELECTED_PERMISSIONS)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "kiyori_onboarding_preferences_r9"
        private const val KEY_CURRENT_STEP = "current_step_r9"
        private const val KEY_COMPLETED_VERSION = "completed_version"
        private const val KEY_SELECTED_PERMISSIONS = "selected_permissions_r2"

        const val CURRENT_ONBOARDING_VERSION = "2026-08-11-r9"
    }
}
