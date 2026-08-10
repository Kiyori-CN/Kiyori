package com.ai.assistance.operit.ui.main

/**
 * 首次进入 AI 首页必须存在可打开的真实会话；已有有效会话时只尊重用户的每次启动新建偏好。
 *
 * 模型或 API 配置只影响消息发送能力，不能阻止空白会话在产品首页中建立。
 */
internal fun shouldCreateKiyoriStartupChat(
    currentChatId: String?,
    currentChatExists: Boolean,
    startWithNewChat: Boolean,
): Boolean =
    currentChatId == null ||
        !currentChatExists ||
        startWithNewChat
