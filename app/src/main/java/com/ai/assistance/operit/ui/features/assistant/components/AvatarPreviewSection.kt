package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.control.AvatarSettingKeys
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.view.AvatarView
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarRendererFactoryImpl
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import com.kiyori.design.theme.LocalKiyoriSettingsColors

@Composable
fun AvatarPreviewSection(
    modifier: Modifier = Modifier,
    uiState: AssistantConfigViewModel.UiState,
    avatarController: AvatarController?,
    showPreviewContent: Boolean = true,
    onPreviewError: (String) -> Unit,
) {
    val rendererFactory = remember { AvatarRendererFactoryImpl() }
    val colors = LocalKiyoriSettingsColors.current
    val avatarPreviewErrorLogTemplate = stringResource(R.string.avatar_preview_error_log)
    val avatarPreviewFailedMessage = stringResource(R.string.avatar_preview_failed)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.cardBackground,
        border = BorderStroke(width = 0.8.dp, color = colors.divider),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        if (showPreviewContent) {
            Box(modifier = Modifier.fillMaxSize()) {
                val currentModel = uiState.currentAvatarModel
                if (currentModel != null) {
                    if (avatarController != null) {
                        val lastEmotionAnimationMappingState =
                            remember(avatarController) {
                                mutableStateOf<Map<AvatarEmotion, String>>(emptyMap())
                            }
                        val emotionMappingInitializedState =
                            remember(avatarController) {
                                mutableStateOf(false)
                            }

                        LaunchedEffect(avatarController, uiState.emotionAnimationMapping) {
                            val latestMapping = uiState.emotionAnimationMapping
                            val previousMapping = lastEmotionAnimationMappingState.value

                            avatarController.updateEmotionAnimationMapping(latestMapping)

                            if (emotionMappingInitializedState.value) {
                                val changedEmotion =
                                    latestMapping.keys.firstOrNull { emotion ->
                                        previousMapping[emotion] != latestMapping[emotion]
                                    } ?: previousMapping.keys.firstOrNull { emotion ->
                                        !latestMapping.containsKey(emotion)
                                    }

                                changedEmotion?.let { emotion ->
                                    avatarController.setEmotion(emotion)
                                }
                            } else {
                                emotionMappingInitializedState.value = true
                            }

                            lastEmotionAnimationMappingState.value = latestMapping
                        }

                        LaunchedEffect(avatarController, uiState.moodAnimationMapping) {
                            avatarController.updateTriggerAnimationMapping(
                                uiState.moodAnimationMapping
                            )
                        }

                        val runtimeSettings =
                            remember(uiState.config) {
                                uiState.config?.let { settings ->
                                    mutableMapOf<String, Any>(
                                        AvatarSettingKeys.SCALE to settings.scale,
                                        AvatarSettingKeys.TRANSLATE_X to settings.translateX,
                                        AvatarSettingKeys.TRANSLATE_Y to settings.translateY
                                    ).apply {
                                        settings.customSettings.forEach { (key, value) ->
                                            this[key] = value
                                        }
                                    }
                                }
                            }

                        LaunchedEffect(avatarController, runtimeSettings) {
                            runtimeSettings?.let { avatarController.updateSettings(it) }
                        }

                        AvatarView(
                            modifier = Modifier.fillMaxSize(),
                            model = currentModel,
                            controller = avatarController,
                            rendererFactory = rendererFactory,
                            onError = { error ->
                                AppLogger.e(
                                    "AvatarPreviewSection",
                                    avatarPreviewErrorLogTemplate.format(error)
                                )
                                onPreviewError(
                                    avatarPreviewFailedMessage,
                                )
                            }
                        )

                    } else {
                        Text(
                            text =
                                stringResource(
                                    R.string.unsupported_model_type,
                                    currentModel.type.name
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    Text(
                        text =
                            if (uiState.avatarConfigs.isEmpty()) {
                                stringResource(R.string.no_models_available)
                            } else {
                                stringResource(R.string.please_select_model)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

            }
        }
    }
}
