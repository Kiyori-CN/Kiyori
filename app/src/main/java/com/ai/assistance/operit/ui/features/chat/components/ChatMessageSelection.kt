package com.ai.assistance.operit.ui.features.chat.components

/** 列表索引仅是当前显示窗口的投影；分页或删除前面的消息不能改变用户选中的身份。 */
internal fun projectSelectedMessageIndices(
    visibleTimestamps: List<Long>,
    selectedTimestamps: Set<Long>,
): Set<Int> = visibleTimestamps.mapIndexedNotNull { index, timestamp ->
    index.takeIf { timestamp in selectedTimestamps }
}.toSet()
