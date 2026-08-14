package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets

/**
 * App-private storage for ToolPkg container configuration consumed by native host services.
 *
 * The repository deliberately requires both the container identity and variable name. It has no
 * JavaScript bridge and never consults process environment variables, so a ToolPkg script cannot
 * read another container's values by guessing an environment variable name.
 */
class ToolPkgHostEnvironmentRepository private constructor(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getValue(containerPackageName: String, variableName: String): String? {
        val storageKey = storageKey(containerPackageName, variableName)
        return preferences.getString(storageKey, null)?.takeIf(String::isNotBlank)
    }

    fun setValue(containerPackageName: String, variableName: String, value: String) {
        val storageKey = storageKey(containerPackageName, variableName)
        if (value.isBlank()) {
            preferences.edit { remove(storageKey) }
        } else {
            preferences.edit { putString(storageKey, value) }
        }
    }

    fun removeValue(containerPackageName: String, variableName: String) {
        preferences.edit { remove(storageKey(containerPackageName, variableName)) }
    }

    private fun storageKey(containerPackageName: String, variableName: String): String {
        val normalizedContainer = containerPackageName.trim()
        val normalizedVariable = variableName.trim()
        require(normalizedContainer.isNotEmpty()) {
            "ToolPkg container package name must not be blank"
        }
        require(normalizedVariable.isNotEmpty()) {
            "ToolPkg host environment variable name must not be blank"
        }
        return listOf(normalizedContainer, normalizedVariable).joinToString(KEY_SEPARATOR) { value ->
            Base64.encodeToString(
                value.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
            )
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "toolpkg_host_environment"
        private const val KEY_SEPARATOR = "."

        @Volatile
        private var instance: ToolPkgHostEnvironmentRepository? = null

        fun getInstance(context: Context): ToolPkgHostEnvironmentRepository {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: ToolPkgHostEnvironmentRepository(context.applicationContext).also {
                            instance = it
                        }
                }
        }
    }
}
