package com.ai.assistance.operit.data.repository

/** 原生历史分组的操作范围；null绑定不能隐式扩大为所有角色。仅为请求值对象。 */
sealed interface ChatGroupScope {
    data object All : ChatGroupScope
    data object Unbound : ChatGroupScope
    data class Card(val name: String, val includeUnbound: Boolean = false) : ChatGroupScope
    data class Group(val id: String) : ChatGroupScope
}

data class ChatGroupTarget(val groupName: String, val scope: ChatGroupScope) {
    init {
        // 旧导入/兼容接口可以留下空白分组名，仍须允许精确定位并重命名修复。
        // 未分组由null表示，String目标不会扩大到它；新建名称校验在写入入口执行。
        when (scope) {
            is ChatGroupScope.Card -> require(scope.name.isNotBlank())
            is ChatGroupScope.Group -> require(scope.id.isNotBlank())
            else -> Unit
        }
    }

    fun matches(group: String?, cardName: String?, characterGroupId: String?): Boolean {
        if (group != groupName) return false
        return scope.matchesBinding(cardName, characterGroupId)
    }
}

fun ChatGroupScope.matchesBinding(cardName: String?, characterGroupId: String?): Boolean {
    val groupId = characterGroupId?.trim()?.takeIf { it.isNotEmpty() }
    val card = cardName?.takeIf { it.isNotBlank() }
    // 群组绑定优先于遗留角色名；默认角色可以包含未绑定，但不能包含群组对话。
    return when (this) {
        ChatGroupScope.All -> true
        ChatGroupScope.Unbound -> groupId == null && card == null
        is ChatGroupScope.Card -> groupId == null && (card == name || (includeUnbound && card == null))
        is ChatGroupScope.Group -> groupId == id
    }
}

data class ChatGroupMember(val id: String, val locked: Boolean)

class ChatGroupChangedException : IllegalStateException("Chat group membership changed")

class ChatGroupCleanupException(val deletedChatIds: Set<String>, val failureType: String) :
    IllegalStateException("Chat group changed, but post-deletion cleanup did not complete")

internal fun requireUnchangedChatGroup(expected: List<ChatGroupMember>, actual: List<ChatGroupMember>) {
    if (expected.isEmpty() || expected.toSet() != actual.toSet()) throw ChatGroupChangedException()
}
