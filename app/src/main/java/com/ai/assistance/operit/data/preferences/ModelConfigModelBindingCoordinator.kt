package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.normalizeModelNames
import com.ai.assistance.operit.data.model.remapModelIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ModelBoundCharacterCard(
    val id: String,
    val name: String
)

data class ModelConfigBindingImpact(
    val functionTypes: List<FunctionType> = emptyList(),
    val characterCards: List<ModelBoundCharacterCard> = emptyList()
) {
    val totalCount: Int
        get() = functionTypes.size + characterCards.size

    val hasBindings: Boolean
        get() = totalCount > 0
}

/**
 * 协调模型列表顺序与索引绑定，不持有新的模型配置事实。
 *
 * `ModelConfigManager` 仍是模型列表唯一持久化 owner；本类只在列表发生顺序或删除变化时，
 * 按模型名同步 `FunctionalConfigManager` 和固定角色卡的索引，防止位置变化静默换模。
 */
class ModelConfigModelBindingCoordinator(context: Context) {
    private val modelConfigManager = ModelConfigManager(context)
    private val functionalConfigManager = FunctionalConfigManager(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)

    suspend fun inspectModelBindings(
        configId: String,
        modelName: String
    ): ModelConfigBindingImpact {
        return mutationMutex.withLock {
            val config =
                requireNotNull(modelConfigManager.getModelConfig(configId)) {
                    "Model configuration '$configId' does not exist"
                }
            val models = getModelList(config.modelName)
            if (modelName !in models) {
                return@withLock ModelConfigBindingImpact()
            }

            collectBindingImpact(
                configId = configId,
                serializedModels = config.modelName,
                targetModelNames = setOf(modelName)
            )
        }
    }

    suspend fun inspectModelBindings(
        configId: String,
        modelNames: Collection<String>
    ): ModelConfigBindingImpact {
        val targetModelNames = normalizeModelNames(modelNames).toSet()
        if (targetModelNames.isEmpty()) return ModelConfigBindingImpact()

        return mutationMutex.withLock {
            val config =
                requireNotNull(modelConfigManager.getModelConfig(configId)) {
                    "Model configuration '$configId' does not exist"
                }
            val currentModels = getModelList(config.modelName)
            val presentTargetModelNames = targetModelNames.intersect(currentModels.toSet())
            if (presentTargetModelNames.isEmpty()) {
                return@withLock ModelConfigBindingImpact()
            }

            collectBindingImpact(
                configId = configId,
                serializedModels = config.modelName,
                targetModelNames = presentTargetModelNames
            )
        }
    }

    suspend fun inspectConfigBindings(configId: String): ModelConfigBindingImpact {
        return mutationMutex.withLock {
            val config =
                requireNotNull(modelConfigManager.getModelConfig(configId)) {
                    "Model configuration '$configId' does not exist"
                }
            collectBindingImpact(
                configId = configId,
                serializedModels = config.modelName,
                targetModelNames = null
            )
        }
    }

    /**
     * 在模型配置保存前同步索引绑定。
     *
     * 删除已绑定模型时必须由界面在用户确认后传入 replacementModelName。
     * 该名称必须真实存在于 nextModels；协调器不会选择隐式替代项。
     */
    suspend fun reconcileAndPersist(
        configId: String,
        nextModels: List<String>,
        replacementModelName: String?,
        persistModelConfig: suspend () -> Unit
    ): ModelConfigBindingImpact {
        return mutationMutex.withLock {
            val currentConfig =
                requireNotNull(modelConfigManager.getModelConfig(configId)) {
                    "Model configuration '$configId' does not exist"
                }
            val oldModels = getModelList(currentConfig.modelName)
            val normalizedNextModels = normalizeModelNames(nextModels)

            if (oldModels == normalizedNextModels) {
                persistModelConfig()
                return@withLock ModelConfigBindingImpact()
            }

            val originalFunctionMapping =
                functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val nextFunctionMapping = originalFunctionMapping.toMutableMap()
            val changedFunctionTypes = mutableListOf<FunctionType>()
            val removedFunctionTypes = mutableListOf<FunctionType>()

            originalFunctionMapping.forEach { (functionType, mapping) ->
                if (mapping.configId != configId || oldModels.isEmpty()) {
                    return@forEach
                }
                val selectedModelName = getModelByIndex(currentConfig.modelName, mapping.modelIndex)
                if (selectedModelName.isEmpty()) {
                    return@forEach
                }
                val nextIndex =
                    remapModelIndex(
                        oldModels = oldModels,
                        newModels = normalizedNextModels,
                        requestedIndex = mapping.modelIndex,
                        replacementModelName = replacementModelName
                    )
                if (selectedModelName !in normalizedNextModels) {
                    removedFunctionTypes.add(functionType)
                }
                if (nextIndex != mapping.modelIndex) {
                    nextFunctionMapping[functionType] = mapping.copy(modelIndex = nextIndex)
                    changedFunctionTypes.add(functionType)
                }
            }

            val originalCharacterCards = characterCardManager.getAllCharacterCards()
            val changedCharacterCards = mutableListOf<Pair<CharacterCard, CharacterCard>>()
            val removedCharacterCards = mutableListOf<ModelBoundCharacterCard>()

            originalCharacterCards.forEach { card ->
                if (
                    CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode) !=
                        CharacterCardChatModelBindingMode.FIXED_CONFIG ||
                        card.chatModelConfigId != configId ||
                        oldModels.isEmpty()
                ) {
                    return@forEach
                }
                val selectedModelName =
                    getModelByIndex(currentConfig.modelName, card.chatModelIndex)
                if (selectedModelName.isEmpty()) {
                    return@forEach
                }
                val nextIndex =
                    remapModelIndex(
                        oldModels = oldModels,
                        newModels = normalizedNextModels,
                        requestedIndex = card.chatModelIndex,
                        replacementModelName = replacementModelName
                    )
                if (selectedModelName !in normalizedNextModels) {
                    removedCharacterCards.add(
                        ModelBoundCharacterCard(id = card.id, name = card.name)
                    )
                }
                if (nextIndex != card.chatModelIndex) {
                    changedCharacterCards.add(
                        card to card.copy(chatModelIndex = nextIndex)
                    )
                }
            }

            try {
                if (changedFunctionTypes.isNotEmpty()) {
                    functionalConfigManager.saveFunctionConfigMappingWithIndex(nextFunctionMapping)
                }
                changedCharacterCards.forEach { (_, updatedCard) ->
                    characterCardManager.updateCharacterCard(updatedCard)
                }
                persistModelConfig()
            } catch (error: Exception) {
                val rollbackErrors = mutableListOf<Throwable>()
                if (changedFunctionTypes.isNotEmpty()) {
                    runCatching {
                        functionalConfigManager.saveFunctionConfigMappingWithIndex(
                            originalFunctionMapping
                        )
                    }.exceptionOrNull()?.let(rollbackErrors::add)
                }
                changedCharacterCards.forEach { (originalCard, _) ->
                    runCatching {
                        characterCardManager.updateCharacterCard(originalCard)
                    }.exceptionOrNull()?.let(rollbackErrors::add)
                }
                rollbackErrors.forEach(error::addSuppressed)
                throw error
            }

            ModelConfigBindingImpact(
                functionTypes = removedFunctionTypes,
                characterCards = removedCharacterCards
            )
        }
    }

    /** 撤销删除时，仅恢复删除前确实指向该模型的绑定。 */
    suspend fun restoreBindings(
        configId: String,
        modelName: String,
        impact: ModelConfigBindingImpact
    ) {
        if (!impact.hasBindings) return

        mutationMutex.withLock {
            val config =
                requireNotNull(modelConfigManager.getModelConfig(configId)) {
                    "Model configuration '$configId' does not exist"
                }
            val models = getModelList(config.modelName)
            val restoredIndex = models.indexOf(modelName)
            require(restoredIndex >= 0) {
                "Cannot restore bindings because model '$modelName' is not present"
            }

            val functionMapping =
                functionalConfigManager.functionConfigMappingWithIndexFlow.first().toMutableMap()
            var functionMappingChanged = false
            impact.functionTypes.forEach { functionType ->
                val mapping = functionMapping[functionType]
                if (mapping?.configId == configId && mapping.modelIndex != restoredIndex) {
                    functionMapping[functionType] = mapping.copy(modelIndex = restoredIndex)
                    functionMappingChanged = true
                }
            }
            if (functionMappingChanged) {
                functionalConfigManager.saveFunctionConfigMappingWithIndex(functionMapping)
            }

            val cardIds = impact.characterCards.map { it.id }.toSet()
            characterCardManager.getAllCharacterCards().forEach { card ->
                if (
                    card.id in cardIds &&
                        CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode) ==
                            CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                        card.chatModelConfigId == configId &&
                        card.chatModelIndex != restoredIndex
                ) {
                    characterCardManager.updateCharacterCard(
                        card.copy(chatModelIndex = restoredIndex)
                    )
                }
            }
        }
    }

    private suspend fun collectBindingImpact(
        configId: String,
        serializedModels: String,
        targetModelNames: Set<String>?
    ): ModelConfigBindingImpact {
        val functionTypes =
            functionalConfigManager.functionConfigMappingWithIndexFlow.first()
                .filter { (_, mapping) ->
                    mapping.configId == configId &&
                        (
                            targetModelNames == null ||
                                getModelByIndex(serializedModels, mapping.modelIndex) in
                                    targetModelNames
                        )
                }
                .keys
                .toList()

        val characterCards =
            characterCardManager.getAllCharacterCards()
                .filter { card ->
                    CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode) ==
                        CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                        card.chatModelConfigId == configId &&
                        (
                            targetModelNames == null ||
                                getModelByIndex(serializedModels, card.chatModelIndex) in
                                    targetModelNames
                        )
                }
                .map { card ->
                    ModelBoundCharacterCard(id = card.id, name = card.name)
                }

        return ModelConfigBindingImpact(
            functionTypes = functionTypes,
            characterCards = characterCards
        )
    }

    private companion object {
        val mutationMutex = Mutex()
    }
}
