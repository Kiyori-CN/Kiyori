package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.tools.CharacterCardActivationResultData
import com.ai.assistance.operit.core.tools.CharacterCardCreateResultData
import com.ai.assistance.operit.core.tools.CharacterCardDeleteResultData
import com.ai.assistance.operit.core.tools.CharacterCardExportResultData
import com.ai.assistance.operit.core.tools.CharacterCardImportResultData
import com.ai.assistance.operit.core.tools.CharacterCardResultData
import com.ai.assistance.operit.core.tools.CharacterCardResultItem
import com.ai.assistance.operit.core.tools.CharacterCardToolAccessConfigResultItem
import com.ai.assistance.operit.core.tools.CharacterCardUpdateResultData
import com.ai.assistance.operit.core.tools.CharacterCardsResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/**
 * ToolPkg `Tools.SoftwareSettings` 角色卡管理实现。
 *
 * 这里直接操作现有 CharacterCardManager，避免产生第二份角色卡状态或与设置页行为漂移。
 */
class StandardCharacterCardSettingsTools(context: Context) {
    private val characterCardManager = CharacterCardManager.getInstance(context)

    suspend fun listCharacterCards(tool: AITool): ToolResult {
        return try {
            val cards = characterCardManager.getAllCharacterCards()
            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    CharacterCardsResultData(
                        totalCount = cards.size,
                        activeCharacterCardId = activeCharacterCardId(),
                        cards = cards.map(::characterCardToResultItem)
                    )
            )
        } catch (error: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    CharacterCardsResultData(
                        totalCount = 0,
                        activeCharacterCardId = null,
                        cards = emptyList()
                    ),
                error = error.toString()
            )
        }
    }

    suspend fun getCharacterCard(tool: AITool): ToolResult {
        val characterCardId = requiredCharacterCardId(tool) ?: return missingCharacterCardId(tool)
        return runCharacterCardOperation(tool) {
            val card = requireCharacterCard(characterCardId)
            CharacterCardResultData(
                card = characterCardToResultItem(card),
                activeCharacterCardId = activeCharacterCardId()
            )
        }
    }

    suspend fun createCharacterCard(tool: AITool): ToolResult {
        return runCharacterCardOperation(tool) {
            val (draft, changedFields) =
                applyCharacterCardUpdates(
                    tool = tool,
                    current = CharacterCard(id = "", name = ""),
                    requireName = true
                )
            val characterCardId = characterCardManager.createCharacterCard(draft)
            val card = requireCharacterCard(characterCardId)
            CharacterCardCreateResultData(
                created = true,
                card = characterCardToResultItem(card),
                activeCharacterCardId = activeCharacterCardId(),
                changedFields = changedFields
            )
        }
    }

    suspend fun updateCharacterCard(tool: AITool): ToolResult {
        val characterCardId = requiredCharacterCardId(tool) ?: return missingCharacterCardId(tool)
        return runCharacterCardOperation(tool) {
            val current = requireCharacterCard(characterCardId)
            val (updated, changedFields) =
                applyCharacterCardUpdates(
                    tool = tool,
                    current = current,
                    requireName = false
                )
            require(changedFields.isNotEmpty()) {
                "At least one character card update field is required"
            }
            characterCardManager.updateCharacterCard(updated)
            val card = requireCharacterCard(characterCardId)
            CharacterCardUpdateResultData(
                updated = true,
                card = characterCardToResultItem(card),
                activeCharacterCardId = activeCharacterCardId(),
                changedFields = changedFields
            )
        }
    }

    suspend fun deleteCharacterCard(tool: AITool): ToolResult {
        val characterCardId = requiredCharacterCardId(tool) ?: return missingCharacterCardId(tool)
        if (characterCardId == CharacterCardManager.DEFAULT_CHARACTER_CARD_ID) {
            return failure(tool, "The default character card cannot be deleted")
        }
        return runCharacterCardOperation(tool) {
            requireCharacterCard(characterCardId)
            characterCardManager.deleteCharacterCard(characterCardId)
            CharacterCardDeleteResultData(
                deleted = true,
                characterCardId = characterCardId,
                activeCharacterCardId = activeCharacterCardId()
            )
        }
    }

    suspend fun setActiveCharacterCard(tool: AITool): ToolResult {
        val characterCardId = requiredCharacterCardId(tool) ?: return missingCharacterCardId(tool)
        return runCharacterCardOperation(tool) {
            requireCharacterCard(characterCardId)
            characterCardManager.setActiveCharacterCard(characterCardId)
            CharacterCardActivationResultData(activeCharacterCardId = activeCharacterCardId())
        }
    }

    suspend fun clearActiveCharacterCard(tool: AITool): ToolResult {
        return runCharacterCardOperation(tool) {
            characterCardManager.clearActiveCharacterCard()
            CharacterCardActivationResultData(activeCharacterCardId = null)
        }
    }

    suspend fun importCharacterCardFromTavernJson(tool: AITool): ToolResult {
        val tavernJson = parameter(tool, "tavern_json")?.takeIf { it.isNotBlank() }
            ?: return failure(tool, "Missing required parameter: tavern_json")
        return runCharacterCardOperation(tool) {
            val characterCardId =
                characterCardManager.createCharacterCardFromTavernJson(tavernJson).getOrThrow()
            val card = requireCharacterCard(characterCardId)
            CharacterCardImportResultData(
                imported = true,
                card = characterCardToResultItem(card),
                activeCharacterCardId = activeCharacterCardId()
            )
        }
    }

    suspend fun exportCharacterCardToTavernJson(tool: AITool): ToolResult {
        val characterCardId = requiredCharacterCardId(tool) ?: return missingCharacterCardId(tool)
        return runCharacterCardOperation(tool) {
            requireCharacterCard(characterCardId)
            CharacterCardExportResultData(
                characterCardId = characterCardId,
                tavernJson =
                    characterCardManager.exportCharacterCardToTavernJson(characterCardId).getOrThrow()
            )
        }
    }

    private suspend fun runCharacterCardOperation(
        tool: AITool,
        operation: suspend () -> com.ai.assistance.operit.core.tools.ToolResultData
    ): ToolResult {
        return try {
            ToolResult(toolName = tool.name, success = true, result = operation())
        } catch (error: Exception) {
            failure(tool, error.toString())
        }
    }

    private fun failure(tool: AITool, message: String): ToolResult {
        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = message
        )
    }

    private fun missingCharacterCardId(tool: AITool): ToolResult {
        return failure(tool, "Missing required parameter: character_card_id")
    }

    private fun requiredCharacterCardId(tool: AITool): String? {
        return parameter(tool, "character_card_id")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun activeCharacterCardId(): String? {
        return characterCardManager.observeActiveCharacterCardId().first()
    }

    private suspend fun requireCharacterCard(characterCardId: String): CharacterCard {
        return characterCardManager
            .getAllCharacterCards()
            .firstOrNull { card -> card.id == characterCardId }
            ?: throw IllegalArgumentException("Character card not found: $characterCardId")
    }

    private fun applyCharacterCardUpdates(
        tool: AITool,
        current: CharacterCard,
        requireName: Boolean
    ): Pair<CharacterCard, List<String>> {
        var updated = current
        val changedFields = mutableListOf<String>()

        fun applyText(name: String, transform: (CharacterCard, String) -> CharacterCard) {
            val value = parameter(tool, name) ?: return
            updated = transform(updated, value)
            changedFields.add(name)
        }

        parameter(tool, "name")?.let { rawName ->
            val name = rawName.trim()
            require(name.isNotBlank()) { "Character card name cannot be blank" }
            updated = updated.copy(name = name)
            changedFields.add("name")
        }
        if (requireName) {
            require(updated.name.isNotBlank()) { "Missing required parameter: name" }
        }

        applyText("description") { card, value -> card.copy(description = value) }
        applyText("character_setting") { card, value -> card.copy(characterSetting = value) }
        applyText("opening_statement") { card, value -> card.copy(openingStatement = value) }
        applyText("other_content_chat") { card, value -> card.copy(otherContentChat = value) }
        applyText("other_content_voice") { card, value -> card.copy(otherContentVoice = value) }
        applyText("advanced_custom_prompt") { card, value -> card.copy(advancedCustomPrompt = value) }
        applyText("marks") { card, value -> card.copy(marks = value) }

        parameter(tool, "attached_tag_ids")?.let { raw ->
            updated =
                updated.copy(
                    attachedTagIds = parseStringArrayParameter(raw, "attached_tag_ids")
                )
            changedFields.add("attached_tag_ids")
        }
        parameter(tool, "chat_model_binding_mode")?.let { raw ->
            updated =
                updated.copy(
                    chatModelBindingMode =
                        parseEnum(
                            raw,
                            "chat_model_binding_mode",
                            CharacterCardChatModelBindingMode.FOLLOW_GLOBAL,
                            CharacterCardChatModelBindingMode.FIXED_CONFIG
                        )
                )
            changedFields.add("chat_model_binding_mode")
        }
        parameter(tool, "chat_model_config_id")?.let { raw ->
            updated = updated.copy(chatModelConfigId = raw.trim().takeIf { it.isNotEmpty() })
            changedFields.add("chat_model_config_id")
        }
        parameter(tool, "chat_model_index")?.let { raw ->
            val index =
                raw.trim().toIntOrNull()
                    ?: throw IllegalArgumentException(
                        "Invalid integer parameter: chat_model_index"
                    )
            require(index >= 0) { "chat_model_index must be greater than or equal to 0" }
            updated = updated.copy(chatModelIndex = index)
            changedFields.add("chat_model_index")
        }
        parameter(tool, "memory_profile_binding_mode")?.let { raw ->
            updated =
                updated.copy(
                    memoryProfileBindingMode =
                        parseEnum(
                            raw,
                            "memory_profile_binding_mode",
                            CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL,
                            CharacterCardMemoryProfileBindingMode.FIXED_PROFILE
                        )
                )
            changedFields.add("memory_profile_binding_mode")
        }
        parameter(tool, "memory_profile_id")?.let { raw ->
            updated = updated.copy(memoryProfileId = raw.trim().takeIf { it.isNotEmpty() })
            changedFields.add("memory_profile_id")
        }

        var toolAccessConfig = updated.toolAccessConfig
        parameter(tool, "tool_access_enabled")?.let { raw ->
            val enabled =
                parseBooleanParameter(raw)
                    ?: throw IllegalArgumentException(
                        "Invalid boolean parameter: tool_access_enabled"
                    )
            toolAccessConfig = toolAccessConfig.copy(enabled = enabled)
            changedFields.add("tool_access_enabled")
        }
        parameter(tool, "allowed_builtin_tools")?.let { raw ->
            toolAccessConfig =
                toolAccessConfig.copy(
                    allowedBuiltinTools =
                        parseStringArrayParameter(raw, "allowed_builtin_tools")
                )
            changedFields.add("allowed_builtin_tools")
        }
        parameter(tool, "allowed_packages")?.let { raw ->
            toolAccessConfig =
                toolAccessConfig.copy(
                    allowedPackages = parseStringArrayParameter(raw, "allowed_packages")
                )
            changedFields.add("allowed_packages")
        }
        parameter(tool, "allowed_skills")?.let { raw ->
            toolAccessConfig =
                toolAccessConfig.copy(
                    allowedSkills = parseStringArrayParameter(raw, "allowed_skills")
                )
            changedFields.add("allowed_skills")
        }
        parameter(tool, "allowed_mcp_servers")?.let { raw ->
            toolAccessConfig =
                toolAccessConfig.copy(
                    allowedMcpServers = parseStringArrayParameter(raw, "allowed_mcp_servers")
                )
            changedFields.add("allowed_mcp_servers")
        }
        if (toolAccessConfig != updated.toolAccessConfig) {
            updated = updated.copy(toolAccessConfig = toolAccessConfig.normalized())
        }

        return updated to changedFields.distinct()
    }

    private fun parseEnum(
        raw: String,
        parameterName: String,
        first: String,
        second: String
    ): String {
        return when (raw.trim().uppercase()) {
            first -> first
            second -> second
            else -> throw IllegalArgumentException("Invalid $parameterName: $raw")
        }
    }

    private fun parseStringArrayParameter(raw: String, parameterName: String): List<String> {
        val array =
            try {
                JSONArray(raw)
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "Invalid JSON array parameter: $parameterName",
                    error
                )
            }
        return (0 until array.length())
            .map { index ->
                val value = array.get(index)
                require(value is String) {
                    "Invalid JSON array parameter: $parameterName[$index] must be a string"
                }
                value.trim()
            }
            .filter { value -> value.isNotEmpty() }
            .distinct()
    }

    private fun characterCardToResultItem(card: CharacterCard): CharacterCardResultItem {
        val toolAccessConfig = card.toolAccessConfig.normalized()
        return CharacterCardResultItem(
            id = card.id,
            name = card.name,
            description = card.description,
            characterSetting = card.characterSetting,
            openingStatement = card.openingStatement,
            otherContentChat = card.otherContentChat,
            otherContentVoice = card.otherContentVoice,
            attachedTagIds = card.attachedTagIds,
            advancedCustomPrompt = card.advancedCustomPrompt,
            marks = card.marks,
            chatModelBindingMode = card.chatModelBindingMode,
            chatModelConfigId = card.chatModelConfigId,
            chatModelIndex = card.chatModelIndex,
            memoryProfileBindingMode = card.memoryProfileBindingMode,
            memoryProfileId = card.memoryProfileId,
            toolAccessConfig =
                CharacterCardToolAccessConfigResultItem(
                    enabled = toolAccessConfig.enabled,
                    allowedBuiltinTools = toolAccessConfig.allowedBuiltinTools,
                    allowedPackages = toolAccessConfig.allowedPackages,
                    allowedSkills = toolAccessConfig.allowedSkills,
                    allowedMcpServers = toolAccessConfig.allowedMcpServers
                ),
            isDefault = card.isDefault,
            createdAt = card.createdAt,
            updatedAt = card.updatedAt
        )
    }

    private fun parameter(tool: AITool, name: String): String? {
        return tool.parameters.find { it.name == name }?.value
    }

    private fun parseBooleanParameter(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }
}
