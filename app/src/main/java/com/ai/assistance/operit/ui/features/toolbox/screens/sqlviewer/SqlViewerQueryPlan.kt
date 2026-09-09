package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

internal data class SqlViewerQueryPlan(
    val baseSql: String,
    val sql: String,
    val returnsRows: Boolean,
    val paginated: Boolean
)

/** 只跳过开头的SQL注释；不改写字符串或查询正文，语法验证仍由SQLite负责。 */
private fun firstSqlKeyword(sql: String): String {
    var remaining = sql.trimStart()
    while (true) {
        remaining = when {
            remaining.startsWith("--") -> remaining.substringAfter('\n', "").trimStart()
            remaining.startsWith("/*") -> {
                val end = remaining.indexOf("*/", 2)
                if (end < 0) return ""
                remaining.substring(end + 2).trimStart()
            }
            else -> return remaining.takeWhile { it.isLetter() }.uppercase()
        }
    }
}

internal fun planSqlViewerQuery(rawSql: String, pageSize: Int, offset: Int, paginate: Boolean): SqlViewerQueryPlan {
    val base = rawSql.trim().removeSuffix(";")
    require(base.isNotBlank()) { "SQL is empty" }
    require(pageSize in 1..1000 && offset >= 0) { "Page size must be 1–1000 and offset must not be negative" }
    val keyword = firstSqlKeyword(base)
    val paginated = paginate && keyword in setOf("SELECT", "WITH")
    return SqlViewerQueryPlan(
        baseSql = base,
        // 外层分页保留原有LIMIT/ORDER；换行防止尾部单行注释吃掉右括号。
        sql = if (paginated) "SELECT * FROM (\n$base\n) LIMIT $pageSize OFFSET $offset" else base,
        returnsRows = keyword in setOf("SELECT", "WITH", "PRAGMA", "EXPLAIN"),
        paginated = paginated
    )
}
