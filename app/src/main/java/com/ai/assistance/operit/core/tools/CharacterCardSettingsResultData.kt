package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable

/** ToolPkg 软件设置 API 返回的角色卡工具访问配置。 */
@Serializable
data class CharacterCardToolAccessConfigResultItem(
    val enabled: Boolean,
    val allowedBuiltinTools: List<String>,
    val allowedPackages: List<String>,
    val allowedSkills: List<String>,
    val allowedMcpServers: List<String>
)

/** ToolPkg 软件设置 API 返回的完整角色卡配置。 */
@Serializable
data class CharacterCardResultItem(
    val id: String,
    val name: String,
    val description: String,
    val characterSetting: String,
    val openingStatement: String,
    val otherContentChat: String,
    val otherContentVoice: String,
    val attachedTagIds: List<String>,
    val advancedCustomPrompt: String,
    val marks: String,
    val chatModelBindingMode: String,
    val chatModelConfigId: String?,
    val chatModelIndex: Int,
    val memoryProfileBindingMode: String,
    val memoryProfileId: String?,
    val toolAccessConfig: CharacterCardToolAccessConfigResultItem,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CharacterCardsResultData(
    val totalCount: Int,
    val activeCharacterCardId: String?,
    val cards: List<CharacterCardResultItem>
) : ToolResultData() {
    override fun toString(): String {
        return "Character cards: $totalCount, active=$activeCharacterCardId"
    }
}

@Serializable
data class CharacterCardResultData(
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Character card: ${card.id} (${card.name})"
    }
}

@Serializable
data class CharacterCardCreateResultData(
    val created: Boolean,
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?,
    val changedFields: List<String>
) : ToolResultData() {
    override fun toString(): String {
        return "Character card created: ${card.id} (${card.name})"
    }
}

@Serializable
data class CharacterCardUpdateResultData(
    val updated: Boolean,
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?,
    val changedFields: List<String>
) : ToolResultData() {
    override fun toString(): String {
        return "Character card updated: ${card.id}, changed=${changedFields.size}"
    }
}

@Serializable
data class CharacterCardDeleteResultData(
    val deleted: Boolean,
    val characterCardId: String,
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Character card deleted: $characterCardId"
    }
}

@Serializable
data class CharacterCardActivationResultData(
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Active character card: $activeCharacterCardId"
    }
}

@Serializable
data class CharacterCardImportResultData(
    val imported: Boolean,
    val card: CharacterCardResultItem,
    val activeCharacterCardId: String?
) : ToolResultData() {
    override fun toString(): String {
        return "Character card imported: ${card.id} (${card.name})"
    }
}

@Serializable
data class CharacterCardExportResultData(
    val characterCardId: String,
    val tavernJson: String
) : ToolResultData() {
    override fun toString(): String {
        return "Character card exported: $characterCardId"
    }
}
