package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlin.properties.ReadOnlyProperty

private val PREFERENCES_SCHEMA_VERSION_KEY = stringPreferencesKey("__kiyori_preferences_schema_version")

internal interface PreferencesSchemaMigration {
    suspend fun migrate(version: Int, preferences: MutablePreferences)
    suspend fun cleanUp() = Unit
}

internal fun preferenceSchemaMigration(
    migrate: suspend (version: Int, preferences: MutablePreferences) -> Unit,
): PreferencesSchemaMigration = object : PreferencesSchemaMigration {
    override suspend fun migrate(version: Int, preferences: MutablePreferences) {
        migrate(version, preferences)
    }
}

internal fun versionedPreferencesDataStore(
    name: String,
    currentVersion: Int,
    createMigration: (Context) -> PreferencesSchemaMigration,
): ReadOnlyProperty<Context, DataStore<Preferences>> {
    require(currentVersion >= 0) { "Preference schema version must be non-negative: $currentVersion" }
    return preferencesDataStore(
        name = name,
        produceMigrations = { context ->
            listOf(
                PreferencesSchemaDataMigration(
                    currentVersion = currentVersion,
                    migration = createMigration(context.applicationContext),
                )
            )
        },
    )
}

private class PreferencesSchemaDataMigration(
    private val currentVersion: Int,
    private val migration: PreferencesSchemaMigration,
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = readSchemaVersion(currentData)
        if (version > currentVersion) {
            throw PreferencesSchemaVersionTooNewException(version, currentVersion)
        }
        return version < currentVersion
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val preferences = currentData.toMutablePreferences()
        var version = readSchemaVersion(preferences)
        while (version < currentVersion) {
            migration.migrate(version, preferences)
            version++
        }
        preferences[PREFERENCES_SCHEMA_VERSION_KEY] = currentVersion.toString()
        return preferences
    }

    override suspend fun cleanUp() = migration.cleanUp()

    private fun readSchemaVersion(preferences: Preferences): Int {
        val raw = preferences[PREFERENCES_SCHEMA_VERSION_KEY] ?: return 0
        return raw.toIntOrNull() ?: throw InvalidPreferencesSchemaVersionException(raw)
    }
}

internal class InvalidPreferencesSchemaVersionException(value: String) :
    IllegalStateException("Invalid preferences schema version: $value")

internal class PreferencesSchemaVersionTooNewException(actual: Int, expected: Int) :
    IllegalStateException("Preferences schema version $actual is newer than runtime version $expected")
