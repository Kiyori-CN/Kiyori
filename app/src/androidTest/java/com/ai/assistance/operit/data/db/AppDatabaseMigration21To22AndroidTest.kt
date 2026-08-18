package com.ai.assistance.operit.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
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
class AppDatabaseMigration21To22AndroidTest {
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
                                db.execSQL(
                                    """
                                    CREATE TABLE `provider_executions` (
                                        `localExecutionId` TEXT NOT NULL,
                                        `chatId` TEXT NOT NULL,
                                        PRIMARY KEY(`localExecutionId`)
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
    fun migration21To22_createsAuditSchemaReconstructsStatusAndPreservesPayloadRestrict() {
        insertChat("chat-partial")
        insertChat("chat-basic")
        database.execSQL(
            "INSERT INTO provider_executions (localExecutionId, chatId) " +
                "VALUES ('exec-1', 'chat-partial')"
        )

        AppDatabase.MIGRATION_21_22.migrate(database)

        val expectedTables =
            setOf(
                "conversation_audits",
                "conversation_audit_events",
                "conversation_audit_payloads",
                "conversation_audit_event_payloads",
                "conversation_message_revisions",
                "conversation_message_projections",
                "conversation_audit_seals",
            )
        assertEquals(expectedTables, sqliteObjects("table", expectedTables))

        val expectedIndices =
            setOf(
                "index_conversation_audit_events_chatId_sequenceNumber",
                "index_conversation_audit_event_payloads_payloadSha256",
                "index_conversation_message_revisions_chatId_messageTimestamp_variantIndex_revisionNumber",
                "index_conversation_message_projections_currentRevisionId",
                "index_conversation_audit_seals_chatId_sequenceNumber",
            )
        assertEquals(expectedIndices, sqliteObjects("index", expectedIndices))
        assertEquals("PARTIAL", scalarText("chat-partial", "completenessStatus"))
        assertEquals("PARTIAL", scalarText("chat-partial", "legacyReconstructionLevel"))
        assertEquals("BASIC", scalarText("chat-basic", "completenessStatus"))
        assertEquals("BASIC", scalarText("chat-basic", "legacyReconstructionLevel"))

        val sha = "1".repeat(64)
        database.execSQL(
            """
            INSERT INTO conversation_audit_payloads (
                payloadSha256, relativePath, plainByteCount, storedByteCount,
                mediaType, encoding, compression, encryptionAlgorithm,
                keyAlias, nonceBase64, createdAt
            ) VALUES (
                '$sha', 'payloads/1.audit', 4, 16,
                'text/plain', 'utf-8', 'gzip', 'AES/GCM/NoPadding',
                'key', 'nonce', 1
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO conversation_audit_events (
                eventId, chatId, sequenceNumber, occurredAt, recordedAt,
                category, eventType, actor, summary, messageTimestamp, variantIndex,
                localExecutionId, providerCallId, parentEventId, sourceChatId, sourceEventId,
                previousEventSha256, eventSha256, visibility, terminalState
            ) VALUES (
                'event-1', 'chat-partial', 1, 1, 1,
                'SESSION', 'CHAT_CREATED', 'KIYORI', 'created', NULL, NULL,
                NULL, NULL, NULL, NULL, NULL,
                '${"0".repeat(64)}', '${"2".repeat(64)}', 'TIMELINE', NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO conversation_audit_event_payloads (
                eventId, payloadSha256, label, ordinal, role
            ) VALUES ('event-1', '$sha', 'content', 0, 'system')
            """.trimIndent()
        )

        val restrictFailure =
            runCatching {
                database.execSQL(
                    "DELETE FROM conversation_audit_payloads WHERE payloadSha256 = '$sha'"
                )
            }.exceptionOrNull()
        assertTrue(restrictFailure is SQLiteConstraintException)

        database.execSQL("DELETE FROM chats WHERE id = 'chat-partial'")
        assertEquals(0, rowCount("conversation_audits", "chatId", "chat-partial"))
        assertEquals(0, rowCount("conversation_audit_events", "chatId", "chat-partial"))
        assertEquals(0, rowCount("conversation_audit_event_payloads"))
        assertEquals(1, rowCount("conversation_audit_payloads"))
        database.execSQL(
            "DELETE FROM conversation_audit_payloads WHERE payloadSha256 = '$sha'"
        )
        assertEquals(0, rowCount("conversation_audit_payloads"))
    }

    private fun insertChat(id: String) {
        database.execSQL(
            """
            INSERT INTO chats (
                id, title, createdAt, updatedAt, inputTokens, outputTokens,
                currentWindowSize, `group`, displayOrder, workspace, workspaceEnv,
                parentChatId, characterCardName, characterGroupId, locked, pinned
            ) VALUES (
                '$id', 'test', 1, 1, 0, 0,
                0, NULL, 0, NULL, NULL,
                NULL, NULL, NULL, 0, 0
            )
            """.trimIndent()
        )
    }

    private fun scalarText(
        chatId: String,
        column: String,
    ): String {
        assertTrue(column.all { character -> character.isLetterOrDigit() || character == '_' })
        database
            .query(
                "SELECT $column FROM conversation_audits WHERE chatId = ?",
                arrayOf(chatId),
            )
            .use { cursor ->
                cursor.moveToFirst()
                return cursor.getString(0)
            }
    }

    private fun sqliteObjects(
        type: String,
        names: Set<String>,
    ): Set<String> {
        val placeholders = names.joinToString(",") { "?" }
        database
            .query(
                "SELECT name FROM sqlite_master WHERE type = ? AND name IN ($placeholders)",
                arrayOf(type, *names.toTypedArray()),
            )
            .use { cursor ->
                return buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
    }

    private fun rowCount(
        table: String,
        column: String? = null,
        value: String? = null,
    ): Int {
        assertTrue(table.all { character -> character.isLetterOrDigit() || character == '_' })
        if (column != null) {
            assertTrue(column.all { character -> character.isLetterOrDigit() || character == '_' })
        }
        val sql =
            if (column == null) {
                "SELECT COUNT(*) FROM $table"
            } else {
                "SELECT COUNT(*) FROM $table WHERE $column = ?"
            }
        val arguments = if (column == null) emptyArray() else arrayOf(requireNotNull(value))
        database.query(sql, arguments).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }
}
