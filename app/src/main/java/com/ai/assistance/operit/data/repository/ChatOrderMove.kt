package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatHistory

/** 只比较位置与绑定；并发标题、用量及消息更新不应阻止排序或被旧UI快照覆盖。 */
data class ChatPositionSnapshot(
    val id: String,
    val order: Long,
    val group: String?,
    val cardName: String?,
    val characterGroupId: String?,
    val pinned: Boolean,
) {
    companion object {
        fun from(chat: ChatHistory) = ChatPositionSnapshot(
            chat.id, chat.displayOrder, chat.group, chat.characterCardName, chat.characterGroupId, chat.pinned,
        )
    }
}

data class ChatOrderMove(
    val original: ChatPositionSnapshot,
    val anchor: ChatPositionSnapshot?,
    val beforeAnchor: Boolean,
    val targetGroup: String?,
    val targetCardName: String?,
    val targetCharacterGroupId: String?,
)

class ChatOrderChangedException : IllegalStateException("Chat position changed")

/** 在原Room事务的完整当前列表上执行单次移动，不能把筛选结果当作完整列表。 */
internal fun applyChatOrderMove(current: List<ChatHistory>, move: ChatOrderMove): List<ChatHistory> {
    require(current.map { it.id }.distinct().size == current.size)
    val moved = current.firstOrNull { it.id == move.original.id } ?: throw ChatOrderChangedException()
    if (ChatPositionSnapshot.from(moved) != move.original) throw ChatOrderChangedException()
    if (move.targetCardName != moved.characterCardName || move.targetCharacterGroupId != moved.characterGroupId) {
        require(move.targetCardName == null || move.targetCharacterGroupId == null)
    }
    val ordered = current.toMutableList()
    move.anchor?.let { expected ->
        require(expected.id != moved.id)
        val anchor = current.firstOrNull { it.id == expected.id } ?: throw ChatOrderChangedException()
        if (ChatPositionSnapshot.from(anchor) != expected || anchor.pinned != moved.pinned) {
            throw ChatOrderChangedException()
        }
        ordered.removeAll { it.id == moved.id }
        val anchorIndex = ordered.indexOfFirst { it.id == anchor.id }
        ordered.add(anchorIndex + if (move.beforeAnchor) 0 else 1, moved)
    }
    return ordered.mapIndexed { index, chat ->
        if (chat.id == moved.id) chat.copy(
            displayOrder = index.toLong(),
            group = move.targetGroup,
            characterCardName = move.targetCardName,
            characterGroupId = move.targetCharacterGroupId,
        ) else chat.copy(displayOrder = index.toLong())
    }
}
