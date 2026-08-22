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
class AppDatabaseMigration23To24AndroidTest {
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
                                listOf(
                                    "chats" to """
                                        `id` TEXT NOT NULL PRIMARY KEY,
                                        `title` TEXT NOT NULL,
                                        `providerCacheMetricRequestCount` INTEGER NOT NULL DEFAULT 0,
                                        `providerTotalInputTokens` INTEGER NOT NULL DEFAULT 0
                                    """.trimIndent(),
                                    "messages" to """
                                        `messageId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `chatId` TEXT NOT NULL,
                                        `content` TEXT NOT NULL,
                                        `providerCacheMetricRequestCount` INTEGER NOT NULL DEFAULT 0,
                                        `providerTotalInputTokens` INTEGER NOT NULL DEFAULT 0
                                    """.trimIndent(),
                                    "message_variants" to """
                                        `variantId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `chatId` TEXT NOT NULL,
                                        `content` TEXT NOT NULL,
                                        `providerCacheMetricRequestCount` INTEGER NOT NULL DEFAULT 0,
                                        `providerTotalInputTokens` INTEGER NOT NULL DEFAULT 0
                                    """.trimIndent(),
                                ).forEach { (table, columns) ->
                                    db.execSQL("CREATE TABLE `$table` ($columns)")
                                }
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
        database.execSQL(
            """
            INSERT INTO chats(
                id, title, providerCacheMetricRequestCount, providerTotalInputTokens
            ) VALUES ('chat-1', 'test', 2, 100)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO messages(
                chatId, content, providerCacheMetricRequestCount, providerTotalInputTokens
            ) VALUES ('chat-1', 'message', 1, 80)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO message_variants(
                chatId, content, providerCacheMetricRequestCount, providerTotalInputTokens
            ) VALUES ('chat-1', 'variant', 1, 60)
            """.trimIndent()
        )
    }

    @After
    fun tearDown() {
        openHelper.close()
    }

    @Test
    fun migration23To24AddsMetricPromptColumnWithUnknownDefaultAndPreservesRows() {
        AppDatabase.MIGRATION_23_24.migrate(database)

        val expectedColumns = setOf("providerCacheMetricPromptTokens")
        listOf("chats", "messages", "message_variants").forEach { table ->
            assertEquals(expectedColumns, tableColumns(table, expectedColumns))
        }

        assertEquals(
            0,
            scalarInt("SELECT providerCacheMetricRequestCount FROM chats WHERE id = 'chat-1'"),
        )
        assertEquals(100L, scalarLong("SELECT providerTotalInputTokens FROM chats WHERE id = 'chat-1'"))
        assertEquals(
            0L,
            scalarLong("SELECT providerCacheMetricPromptTokens FROM chats WHERE id = 'chat-1'"),
        )
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM messages"))
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM message_variants"))
        assertEquals(
            0L,
            scalarLong("SELECT providerCacheMetricPromptTokens FROM messages"),
        )
        assertEquals(
            0L,
            scalarLong("SELECT providerCacheMetricPromptTokens FROM message_variants"),
        )
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
