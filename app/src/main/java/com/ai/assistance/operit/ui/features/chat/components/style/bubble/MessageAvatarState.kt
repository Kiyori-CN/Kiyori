package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * 角色头像查询包含 suspend 数据访问，必须由组合生命周期管理，不能阻塞消息列表的渲染线程。
 * 未绑定独立角色卡头像时继承现有 AI 全局头像，这是当前角色外观合同的一部分。
 */
@Composable
internal fun rememberMessageRoleAvatarUri(
    roleName: String?,
    useGlobalAiAvatarWhenRoleMissing: Boolean,
    characterCardManager: CharacterCardManager,
    preferencesManager: UserPreferencesManager,
): State<String?> =
    produceState<String?>(
        initialValue = null,
        roleName,
        useGlobalAiAvatarWhenRoleMissing,
        characterCardManager,
        preferencesManager,
    ) {
        val normalizedRoleName = roleName?.trim().orEmpty()
        if (normalizedRoleName.isEmpty() && !useGlobalAiAvatarWhenRoleMissing) {
            value = null
            return@produceState
        }

        try {
            val avatarFlow =
                if (normalizedRoleName.isEmpty()) {
                    preferencesManager.customAiAvatarUri
                } else {
                    val characterCard =
                        characterCardManager.findCharacterCardByName(normalizedRoleName)
                    if (characterCard == null) {
                        preferencesManager.customAiAvatarUri
                    } else {
                        preferencesManager.getAiAvatarForCharacterCardFlow(characterCard.id)
                    }
                }
            avatarFlow.collect { avatarUri -> value = avatarUri }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e(
                "MessageAvatarState",
                "Unable to resolve avatar for role=$normalizedRoleName",
                error,
            )
            value = null
        }
    }

