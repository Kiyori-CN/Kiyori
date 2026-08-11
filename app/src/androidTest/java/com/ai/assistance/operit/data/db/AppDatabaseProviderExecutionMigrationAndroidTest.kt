package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseProviderExecutionMigrationAndroidTest {
    private lateinit var openHelper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        openHelper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(null)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL(
                                    """
                                    CREATE TABLE `chats` (
                                        `id` TEXT NOT NULL,
                                        `title` TEXT NOT NULL,
                                        `createdAt` INTEGER NOT NULL,
                                        `updatedAt` INTEGER NOT NULL,
                                        `inputTokens` INTEGER NOT NULL,
                                        `outputTokens` INTEGER NOT NULL,
                                        `currentWindowSize` INTEGER NOT NULL,
                                        `group` TEXT,
                                        `displayOrder` INTEGER NOT NULL,
                                        `workspace` TEXT,
                                        `workspaceEnv` TEXT,
                                        `parentChatId` TEXT,
                                        `characterCardName` TEXT,
                                        `characterGroupId` TEXT,
                                        `locked` INTEGER NOT NULL,
                                        `pinned` INTEGER NOT NULL,
                                        PRIMARY KEY(`id`)
                                    )
                                    """.trimIndent()
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        }
                    )
                    .build()
            )
        database = openHelper.writableDatabase
        database.execSQL("PRAGMA foreign_keys = ON")
    }

    @After
    fun tearDown() {
        openHelper.close()
    }

    @Test
    fun migration20To21_createsExecutionTablesIndicesAndChatCascade() {
        AppDatabase.MIGRATION_20_21.migrate(database)

        val expectedTables =
            setOf(
                "provider_executions",
                "provider_execution_events",
                "message_provider_states",
                "tool_invocation_ledger",
            )
        assertEquals(expectedTables, sqliteObjects("table", expectedTables))

        val expectedIndices =
            setOf(
                "index_provider_executions_provider_remoteResponseId",
                "index_provider_execution_events_remoteResponseId_sequenceNumber",
                "index_message_provider_states_latestExecutionId",
                "index_tool_invocation_ledger_status_updatedAt",
            )
        assertEquals(expectedIndices, sqliteObjects("index", expectedIndices))

        database.execSQL(
            """
            INSERT INTO chats (
                id, title, createdAt, updatedAt, inputTokens, outputTokens,
                currentWindowSize, `group`, displayOrder, workspace, workspaceEnv,
                parentChatId, characterCardName, characterGroupId, locked, pinned
            ) VALUES (
                'chat-1', 'test', 1, 1, 0, 0,
                0, NULL, 0, NULL, NULL,
                NULL, NULL, NULL, 0, 0
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO provider_executions (
                localExecutionId, chatId, messageTimestamp, variantIndex, hopOrdinal,
                provider, modelName, transportKind, requestFingerprint, status,
                remoteResponseId, lastAppliedSequence, terminalEventType, resumeCount,
                lastErrorCode, lastErrorMessage, createdAt, updatedAt, completedAt
            ) VALUES (
                'exec-1', 'chat-1', 100, 0, 0,
                'OPENAI_RESPONSES', 'gpt-5.6-sol', 'RESPONSES', 'fingerprint', 'IN_PROGRESS',
                'resp-1', 1, NULL, 0,
                NULL, NULL, 1, 1, NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO provider_execution_events (
                localExecutionId, remoteResponseId, sequenceNumber, eventType,
                payloadJson, payloadSha256, receivedAt
            ) VALUES (
                'exec-1', 'resp-1', 1, 'response.created',
                '{}', '${"0".repeat(64)}', 1
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO message_provider_states (
                chatId, messageTimestamp, variantIndex, latestExecutionId,
                provider, modelName, remoteResponseId, status, lastAppliedSequence,
                terminalEventType, outputItemsJson, functionCallOutputsJson,
                usageJson, createdAt, updatedAt
            ) VALUES (
                'chat-1', 100, 0, 'exec-1',
                'OPENAI_RESPONSES', 'gpt-5.6-sol', 'resp-1', 'IN_PROGRESS', 1,
                NULL, '[]', '[]',
                NULL, 1, 1
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO tool_invocation_ledger (
                provider, remoteResponseId, callId, localExecutionId, toolName,
                argumentsJson, argumentsSha256, status, resultJson, errorMessage,
                createdAt, updatedAt, startedAt, completedAt
            ) VALUES (
                'OPENAI_RESPONSES', 'resp-1', 'call-1', 'exec-1', 'read_file',
                '{}', '${"1".repeat(64)}', 'PENDING', NULL, NULL,
                1, 1, NULL, NULL
            )
            """.trimIndent()
        )

        database.execSQL("DELETE FROM chats WHERE id = 'chat-1'")

        expectedTables.forEach { table ->
            assertEquals("Expected chat cascade to clear $table", 0, rowCount(table))
        }
    }

    private fun sqliteObjects(
        type: String,
        names: Set<String>,
    ): Set<String> {
        val placeholders = names.joinToString(",") { "?" }
        val args = names.toTypedArray()
        database
            .query(
                "SELECT name FROM sqlite_master WHERE type = ? AND name IN ($placeholders)",
                arrayOf(type, *args),
            )
            .use { cursor ->
                return buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
    }

    private fun rowCount(table: String): Int {
        assertTrue(table.all { character -> character.isLetterOrDigit() || character == '_' })
        database.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }
}
