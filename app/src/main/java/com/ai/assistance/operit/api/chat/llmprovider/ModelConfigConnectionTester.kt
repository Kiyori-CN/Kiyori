package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.toPromptTurns
import com.ai.assistance.operit.util.AssetCopyUtils
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import kotlinx.coroutines.CancellationException

enum class ModelConnectionTestType {
    CHAT,
    TOOL_CALL,
    IMAGE,
    AUDIO,
    VIDEO
}

enum class ModelConnectionTestOutcome {
    PASSED,
    UNVERIFIED,
    FAILED
}

data class ModelConnectionTestItem(
    val type: ModelConnectionTestType,
    val outcome: ModelConnectionTestOutcome,
    val error: String? = null
) {
    val success: Boolean
        get() = outcome == ModelConnectionTestOutcome.PASSED
}

data class ModelConnectionTestReport(
    val configId: String,
    val configName: String,
    val providerType: String,
    val requestedModelIndex: Int,
    val actualModelIndex: Int,
    val testedModelName: String,
    val items: List<ModelConnectionTestItem>
) {
    val success: Boolean
        get() = items.none { it.outcome == ModelConnectionTestOutcome.FAILED }

    val verified: Boolean
        get() = items.isNotEmpty() && items.all { it.outcome == ModelConnectionTestOutcome.PASSED }
}

object ModelConfigConnectionTester {
    suspend fun run(
        context: Context,
        modelConfigManager: ModelConfigManager,
        config: ModelConfigData,
        requestedModelIndex: Int = 0,
        onActiveServiceChanged: (AIService?) -> Unit = {}
    ): ModelConnectionTestReport {
        val actualModelIndex = getValidModelIndex(config.modelName, requestedModelIndex)
        val testedModelName = getModelByIndex(config.modelName, actualModelIndex)
        val configForTest = config.copy(modelName = testedModelName)
        val items = mutableListOf<ModelConnectionTestItem>()

        val service =
            AIServiceFactory.createService(
                config = configForTest,
                modelConfigManager = modelConfigManager,
                context = context
            )
        onActiveServiceChanged(service)

        try {
            val parameters = modelConfigManager.getModelParametersForSnapshot(configForTest)

            suspend fun collectResponse(history: List<PromptTurn>): String {
                val buffer = StringBuilder()
                service.sendMessage(
                    context,
                    history,
                    parameters,
                    stream = false,
                    enableRetry = false,
                ).collect { chunk -> buffer.append(chunk) }
                return buffer.toString()
            }

            suspend fun runCase(
                type: ModelConnectionTestType,
                block: suspend () -> ModelConnectionTestOutcome
            ) {
                val item =
                    try {
                        ModelConnectionTestItem(type = type, outcome = block())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ModelConnectionTestItem(
                            type = type,
                            outcome = ModelConnectionTestOutcome.FAILED,
                            error = e.message
                        )
                    }
                items.add(item)
            }

            runCase(ModelConnectionTestType.CHAT) {
                collectResponse(listOf(PromptTurn(kind = PromptTurnKind.USER, content = "Hi")))
                ModelConnectionTestOutcome.PASSED
            }

            if (configForTest.enableToolCall) {
                runCase(ModelConnectionTestType.TOOL_CALL) {
                    val availableTools =
                        listOf(
                            ToolPrompt(
                                name = "echo",
                                description = "Echoes the provided text.",
                                parametersStructured =
                                    listOf(
                                        ToolParameterSchema(
                                            name = "text",
                                            type = "string",
                                            description = "Text to echo.",
                                            required = true
                                        )
                                    )
                            )
                        )

                    suspend fun runToolCallTest(toolName: String) {
                        val toolTagName = ChatMarkupRegex.generateRandomToolTagName()
                        val toolResultTagName = ChatMarkupRegex.generateRandomToolResultTagName()
                        val testHistory = mutableListOf("system" to "You are a helpful assistant.")
                        testHistory.add(
                            "assistant" to
                                "<$toolTagName name=\"$toolName\"><param name=\"text\">ping</param></$toolTagName>"
                        )
                        testHistory.add(
                            "user" to
                                "<$toolResultTagName name=\"$toolName\" status=\"success\"><content>pong</content></$toolResultTagName>"
                        )
                        service.sendMessage(
                            context,
                            testHistory.toPromptTurns(),
                            parameters,
                            stream = false,
                            availableTools = availableTools,
                            enableRetry = false
                        ).collect { }
                    }
                    runToolCallTest("echo")
                    ModelConnectionTestOutcome.PASSED
                }
            }

            if (configForTest.enableDirectImageProcessing) {
                runCase(ModelConnectionTestType.IMAGE) {
                    val imageFile =
                        AssetCopyUtils.copyAssetToCache(context, MediaCapabilityProbe.IMAGE_ASSET_PATH)
                    val imageId = ImagePoolManager.addImage(imageFile.absolutePath)
                    if (imageId == "error") {
                        throw IllegalStateException("Failed to create test image")
                    }
                    try {
                        val prompt =
                            buildString {
                                append(MediaLinkBuilder.image(context, imageId))
                                append("\n")
                                append(MediaCapabilityProbe.IMAGE_PROMPT)
                            }
                        val response =
                            collectResponse(
                                listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt))
                            )
                        if (MediaCapabilityProbe.matchesImage(response)) {
                            ModelConnectionTestOutcome.PASSED
                        } else {
                            ModelConnectionTestOutcome.UNVERIFIED
                        }
                    } finally {
                        ImagePoolManager.removeImage(imageId)
                        runCatching { imageFile.delete() }
                    }
                }
            }

            if (configForTest.enableDirectAudioProcessing) {
                runCase(ModelConnectionTestType.AUDIO) {
                    val audioFile =
                        AssetCopyUtils.copyAssetToCache(context, MediaCapabilityProbe.AUDIO_ASSET_PATH)
                    val audioId = MediaPoolManager.addMedia(audioFile.absolutePath, "audio/mpeg")
                    if (audioId == "error") {
                        throw IllegalStateException("Failed to create test audio")
                    }
                    try {
                        val prompt =
                            buildString {
                                append(MediaLinkBuilder.audio(context, audioId))
                                append("\n")
                                append(MediaCapabilityProbe.AUDIO_PROMPT)
                            }
                        val response =
                            collectResponse(
                                listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt))
                            )
                        if (MediaCapabilityProbe.matchesAudio(response)) {
                            ModelConnectionTestOutcome.PASSED
                        } else {
                            ModelConnectionTestOutcome.UNVERIFIED
                        }
                    } finally {
                        MediaPoolManager.removeMedia(audioId)
                        runCatching { audioFile.delete() }
                    }
                }
            }

            if (configForTest.enableDirectVideoProcessing) {
                runCase(ModelConnectionTestType.VIDEO) {
                    val videoFile =
                        AssetCopyUtils.copyAssetToCache(context, MediaCapabilityProbe.VIDEO_ASSET_PATH)
                    val videoId = MediaPoolManager.addMedia(videoFile.absolutePath, "video/mp4")
                    if (videoId == "error") {
                        throw IllegalStateException("Failed to create test video")
                    }
                    try {
                        val prompt =
                            buildString {
                                append(MediaLinkBuilder.video(context, videoId))
                                append("\n")
                                append(MediaCapabilityProbe.VIDEO_PROMPT)
                            }
                        val response =
                            collectResponse(
                                listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt))
                            )
                        if (MediaCapabilityProbe.matchesVideo(response)) {
                            ModelConnectionTestOutcome.PASSED
                        } else {
                            ModelConnectionTestOutcome.UNVERIFIED
                        }
                    } finally {
                        MediaPoolManager.removeMedia(videoId)
                        runCatching { videoFile.delete() }
                    }
                }
            }
        } catch (e: CancellationException) {
            runCatching { service.cancelStreaming() }
            throw e
        } catch (e: Exception) {
            if (items.none { it.type == ModelConnectionTestType.CHAT }) {
                items.add(
                    ModelConnectionTestItem(
                        type = ModelConnectionTestType.CHAT,
                        outcome = ModelConnectionTestOutcome.FAILED,
                        error = e.message ?: "Unknown error"
                    )
                )
            }
        } finally {
            onActiveServiceChanged(null)
            service.release()
        }

        return ModelConnectionTestReport(
            configId = configForTest.id,
            configName = configForTest.name,
            providerType = configForTest.apiProviderTypeId,
            requestedModelIndex = requestedModelIndex,
            actualModelIndex = actualModelIndex,
            testedModelName = testedModelName,
            items = items
        )
    }
}
