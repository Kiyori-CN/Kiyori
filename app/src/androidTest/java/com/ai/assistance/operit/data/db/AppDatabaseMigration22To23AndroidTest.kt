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
class AppDatabaseMigration22To23AndroidTest {
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
                                        `id` TEXT NOT NULL PRIMARY KEY,
                                        `title` TEXT NOT NULL,
                                        `inputTokens` INTEGER NOT NULL DEFAULT 0
                                    )
                                    """.trimIndent()
                                )
                                db.execSQL(
                                    """
                                    CREATE TABLE `messages` (
                                        `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `chatId` TEXT NOT NULL,
                                        `content` TEXT NOT NULL
                                    )
                                    """.trimIndent()
                                )
                                db.execSQL(
                                    """
                                    CREATE TABLE `message_variants` (
                                        `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `chatId` TEXT NOT NULL,
                                        `content` TEXT NOT NULL
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
        database.execSQL("INSERT INTO chats(id, title, inputTokens) VALUES ('chat-1', 'test', 7)")
        database.execSQL(
            "INSERT INTO messages(chatId, content) VALUES ('chat-1', 'message')"
        )
        database.execSQL(
            "INSERT INTO message_variants(chatId, content) VALUES ('chat-1', 'variant')"
        )
    }

    @After
    fun tearDown() {
        openHelper.close()
    }

    @Test
    fun migration22To23AddsUsageColumnsWithZeroDefaultsAndPreservesRows() {
        AppDatabase.MIGRATION_22_23.migrate(database)

        val expectedColumns =
            setOf(
                "providerRequestCount",
                "providerUsageRequestCount",
                "providerCacheMetricRequestCount",
                "providerTotalInputTokens",
                "providerUncachedInputTokens",
                "providerCacheReadTokens",
                "providerCacheWriteTokens",
                "providerOutputTokens",
                "providerReasoningTokens",
            )
        listOf("chats", "messages", "message_variants").forEach { table ->
            assertEquals(expectedColumns, tableColumns(table, expectedColumns))
        }

        assertEquals(7, scalarInt("SELECT inputTokens FROM chats WHERE id = 'chat-1'"))
        assertEquals(0, scalarInt("SELECT providerRequestCount FROM chats WHERE id = 'chat-1'"))
        assertEquals(
            0L,
            scalarLong("SELECT providerTotalInputTokens FROM chats WHERE id = 'chat-1'")
        )
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM messages"))
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM message_variants"))
    }

    private fun tableColumns(table: String, expected: Set<String>): Set<String> {
        assertTrue(table.all { it.isLetterOrDigit() || it == '_' })
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            return buildSet {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (name in expected) {
                        add(name)
                    }
                }
            }
        }
    }

    private fun scalarInt(sql: String): Int =
        database.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun scalarLong(sql: String): Long =
        database.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
