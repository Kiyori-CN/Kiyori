package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.model.ActivePrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class ActivePromptManager private constructor(context: Context) {

    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)

    val activePromptFlow: Flow<ActivePrompt> =
        combine(
            characterGroupCardManager.observeActiveCharacterGroupId(),
            characterCardManager.observeActiveCharacterCardId()
        ) { groupId, cardId ->
            when {
                !groupId.isNullOrBlank() -> ActivePrompt.CharacterGroup(groupId)
                !cardId.isNullOrBlank() -> ActivePrompt.CharacterCard(cardId)
                else -> ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
            }
        }.distinctUntilChanged()

    suspend fun getActivePrompt(): ActivePrompt = activePromptFlow.first()

    private val activation = PromptActivationCoordinator()

    suspend fun setActivePrompt(prompt: ActivePrompt, stillValid: () -> Boolean = { true }): Boolean =
        activation.activate(stillValid) { checkCurrent -> applyPrompt(prompt, checkCurrent) }

    private suspend fun applyPrompt(prompt: ActivePrompt, checkCurrent: () -> Unit) {
        when (prompt) {
            is ActivePrompt.CharacterGroup -> {
                characterGroupCardManager.writeActiveCharacterGroupCard(prompt.id, checkCurrent)
                characterCardManager.writeActiveCharacterCard(null, checkCurrent)
            }
            is ActivePrompt.CharacterCard -> {
                characterCardManager.writeActiveCharacterCard(prompt.id, checkCurrent)
                characterGroupCardManager.writeActiveCharacterGroupCard(null, checkCurrent)
            }
        }
        checkCurrent()
    }

    // 兼容工具的“只清空角色卡”语义，同时共享所有激活请求的串行边界。
    suspend fun clearActiveCharacterCard(): Boolean = activation.activate({ true }) { checkCurrent ->
        characterCardManager.writeActiveCharacterCard(null, checkCurrent)
    }

    suspend fun activateForChatBinding(
        characterCardName: String?,
        characterGroupId: String?,
        stillValid: () -> Boolean = { true },
    ): Boolean = activation.activate(stillValid) { checkCurrent ->
        val groupId = characterGroupId?.trim()?.takeIf { it.isNotBlank() }
        val prompt = if (groupId != null) {
            ActivePrompt.CharacterGroup(groupId)
        } else {
            val cardName = characterCardName?.trim()?.takeIf { it.isNotBlank() }
            val card = cardName?.let { characterCardManager.findCharacterCardByName(it) }
            ActivePrompt.CharacterCard(card?.id ?: CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        }
        checkCurrent()
        applyPrompt(prompt, checkCurrent)
    }

    suspend fun resolveActiveCardIdForSend(): String {
        return when (val prompt = getActivePrompt()) {
            is ActivePrompt.CharacterCard -> prompt.id
            is ActivePrompt.CharacterGroup -> CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ActivePromptManager? = null

        fun getInstance(context: Context): ActivePromptManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivePromptManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
