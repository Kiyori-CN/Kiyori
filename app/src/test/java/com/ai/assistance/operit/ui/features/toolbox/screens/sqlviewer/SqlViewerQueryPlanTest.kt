package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

import org.junit.Assert.*
import org.junit.Test

class SqlViewerQueryPlanTest {
    @Test fun `schema and explain queries return rows without invalid pagination`() {
        for (sql in listOf("PRAGMA table_info(chats);", "EXPLAIN QUERY PLAN SELECT * FROM chats")) {
            val plan = planSqlViewerQuery(sql, 50, 0, true)
            assertTrue(plan.returnsRows)
            assertFalse(plan.paginated)
            assertFalse(plan.sql.contains("LIMIT"))
        }
    }

    @Test fun `paging preserves existing limit and comments without modifying quoted text`() {
        val sql = "-- heading\nSELECT 'LIMIT 9' FROM chats LIMIT 3 -- tail"
        val plan = planSqlViewerQuery(sql, 2, 2, true)
        assertTrue(plan.paginated)
        assertEquals(sql, plan.baseSql)
        assertTrue(plan.sql.contains("LIMIT 3 -- tail\n) LIMIT 2 OFFSET 2"))
        assertTrue(plan.sql.contains("'LIMIT 9'"))
    }

    @Test fun `invalid page inputs are rejected and write statements are not rewritten`() {
        assertThrows(IllegalArgumentException::class.java) { planSqlViewerQuery("SELECT 1", 0, 0, true) }
        assertThrows(IllegalArgumentException::class.java) { planSqlViewerQuery("SELECT 1", 1001, 0, true) }
        assertThrows(IllegalArgumentException::class.java) { planSqlViewerQuery("SELECT 1", 10, -1, true) }
        val plan = planSqlViewerQuery("/* comment */ UPDATE chats SET title = 'SELECT';", 10, 0, true)
        assertFalse(plan.returnsRows)
        assertFalse(plan.paginated)
        assertEquals("/* comment */ UPDATE chats SET title = 'SELECT'", plan.sql)
    }
}
