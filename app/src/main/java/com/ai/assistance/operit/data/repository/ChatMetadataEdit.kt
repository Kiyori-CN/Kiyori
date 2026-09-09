package com.ai.assistance.operit.data.repository

/** 一次历史编辑的值快照；不持有聊天或偏好的第二份状态。 */
data class ChatMetadataSnapshot(
    val title: String,
    val characterCardName: String?,
    val characterGroupId: String?,
)

class ChatMetadataConflictException : IllegalStateException("Chat metadata changed while editing")

/** 只合并用户实际修改的字段，角色与群组作为一个互斥绑定处理，避免拼出混合目标。 */
fun mergeChatMetadataEdit(
    current: ChatMetadataSnapshot,
    original: ChatMetadataSnapshot,
    edited: ChatMetadataSnapshot,
): ChatMetadataSnapshot {
    require(edited.title.isNotBlank()) { "Chat title must not be blank" }
    require(edited.characterCardName == null || edited.characterGroupId == null) {
        "Chat can bind either a character or a group"
    }
    val titleChanged = edited.title != original.title
    val originalBinding = original.characterCardName to original.characterGroupId
    val editedBinding = edited.characterCardName to edited.characterGroupId
    val currentBinding = current.characterCardName to current.characterGroupId
    val bindingChanged = editedBinding != originalBinding
    if ((titleChanged && current.title != original.title && current.title != edited.title) ||
        (bindingChanged && currentBinding != originalBinding && currentBinding != editedBinding)
    ) {
        throw ChatMetadataConflictException()
    }
    return current.copy(
        title = if (titleChanged) edited.title else current.title,
        characterCardName = if (bindingChanged) edited.characterCardName else current.characterCardName,
        characterGroupId = if (bindingChanged) edited.characterGroupId else current.characterGroupId,
    )
}
