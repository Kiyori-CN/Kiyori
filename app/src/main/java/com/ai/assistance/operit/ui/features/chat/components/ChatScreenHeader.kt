package com.ai.assistance.operit.ui.features.chat.components

import com.kiyori.design.theme.KIYORI_SECONDARY_BAR_VERTICAL_PADDING_DP
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf

@Composable
fun useFloatingWindowLauncher(
    actualViewModel: ChatViewModel,
    permissionLauncher: ActivityResultLauncher<String>
): () -> Unit {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    return {
        actualViewModel.onFloatingButtonClick(
            FloatingMode.WINDOW,
            permissionLauncher,
            colorScheme,
            typography,
            moveTaskToBackOnReady = true
        )
    }
}

@Composable
fun ChatScreenHeader(
        modifier: Modifier = Modifier,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHeaderTransparent: Boolean,
        chatHeaderHistoryIconColor: Int?,
        chatHeaderPipIconColor: Int?,
        onCharacterSwitcherClick: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    LaunchedEffect(actualViewModel, context) {
        actualViewModel.moveTaskToBackEvents.collect {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCharacterCard by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
            is ActivePrompt.CharacterGroup -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCharacterGroup by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
            is ActivePrompt.CharacterCard -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCardAvatarUri by remember(activeCharacterCard?.id) {
        activeCharacterCard?.id?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeGroupAvatarUri by remember(activeCharacterGroup?.id) {
        activeCharacterGroup?.id?.let { userPreferencesManager.getAiAvatarForCharacterGroupFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeGroupFallbackMemberCardId = remember(activeCharacterGroup?.members) {
        val sortedMembers = activeCharacterGroup?.members?.sortedBy { it.orderIndex }.orEmpty()
        sortedMembers.firstOrNull()?.characterCardId
    }
    val activeGroupFallbackMemberAvatarUri by remember(activeGroupFallbackMemberCardId) {
        activeGroupFallbackMemberCardId?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) }
            ?: flowOf(null)
    }.collectAsState(initial = null)
    val activeCharacterAvatarUri =
        when (activePrompt) {
            is ActivePrompt.CharacterGroup -> activeGroupAvatarUri ?: activeGroupFallbackMemberAvatarUri
            is ActivePrompt.CharacterCard -> activeCardAvatarUri
        }

    val activeStreamingChatIds by actualViewModel.activeStreamingChatIds.collectAsState()
    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
    val currentWindowSize by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val providerUsageAggregate by actualViewModel.providerUsageAggregate.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchWindowFloatingModeAfterMicPermissionGranted(
                    colorScheme = colorScheme,
                    typography = typography,
                    moveTaskToBackOnReady = true
                )
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied))
            }
        }


    val launchFloatingWindow = useFloatingWindowLauncher(actualViewModel, permissionLauncher)

    Row(
            modifier =
                    modifier
                            .fillMaxWidth()
                            .background(
                                    if (chatHeaderTransparent) Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                            // 保留内部按钮的 40dp 触摸目标，只收紧栏本身的上下留白，避免挤占消息区。
                            .padding(horizontal = 16.dp, vertical = KIYORI_SECONDARY_BAR_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChatHeader(
                showChatHistorySelector = showChatHistorySelector,
                onToggleChatHistorySelector = { actualViewModel.toggleChatHistorySelector() },
                modifier = Modifier.weight(1f),
                isFloatingMode = isFloatingMode,
                onLaunchFloatingWindow = launchFloatingWindow,
                historyIconColor = chatHeaderHistoryIconColor,
                pipIconColor = chatHeaderPipIconColor,
                runningTaskCount = activeStreamingChatIds.size,
                activeCharacterName = activeCharacterGroup?.name ?: activeCharacterCard?.name ?: "",
                activeCharacterAvatarUri = activeCharacterAvatarUri,
                onCharacterClick = onCharacterSwitcherClick
        )

        ChatStatisticsButton(
            chatId = currentChatId,
            currentTokens = currentWindowSize,
            maxTokens = maxWindowSizeInK.toLong().coerceAtLeast(0L) * 1024L,
            usage = providerUsageAggregate,
            speedFlow = actualViewModel.generationSpeed,
        )
    }
}
