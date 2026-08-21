package com.ai.assistance.operit.ui.features.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelNameTagEditorContractTest {
    @Test
    fun `model tag editor exposes the agreed interaction contract`() {
        val editor =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/ModelNameTagEditor.kt"
            ).readText()
        val tokens =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/ModelSettingsUiTokens.kt"
            ).readText()

        assertTrue(editor.contains("CompleteModelTagFlow("))
        assertTrue(editor.contains("planModelTagFlow("))
        assertTrue(editor.contains("COLLAPSED_MODEL_TAG_ROW_COUNT = 3"))
        assertTrue(editor.contains("includesExpandIndicator"))
        assertTrue(editor.contains("hiddenModelCount"))
        assertTrue(editor.contains("SubcomposeLayout("))
        assertTrue(editor.contains("R.string.model_expand_remaining"))
        assertFalse(editor.contains("R.string.model_expand_all"))
        assertFalse(editor.contains("shownItemCount"))
        assertFalse(editor.contains("totalItemCount"))
        assertTrue(editor.contains(".combinedClickable("))
        assertTrue(editor.contains("onClick = onCopy"))
        assertTrue(editor.contains("onLongClick = onLongPress"))
        assertTrue(editor.contains("onDelete"))
        assertTrue(editor.contains("ModalBottomSheet("))
        assertTrue(editor.contains("rememberReorderableLazyListState"))
        assertTrue(editor.contains("parseModelNameInput"))
        assertTrue(editor.contains("copyPlainTextToClipboard"))
        assertTrue(editor.contains("model_delete_accessibility"))
        assertTrue(editor.contains("model_drag_accessibility"))
        assertTrue(editor.contains("canFetchFromUpstream"))
        assertTrue(editor.contains("onFetchFromUpstream"))
        assertTrue(editor.contains("ModelSettingsActionHeight"))
        assertTrue(editor.contains("ModelSettingsActionShape"))
        assertFalse(editor.contains("widthIn(max = 292.dp)"))
        assertFalse(editor.contains("COLLAPSED_MODEL_TAGS_HEIGHT"))
        assertFalse(editor.contains("collapsedModelTagsHeight"))
        assertFalse(editor.contains("ModelTagLayout"))
        assertFalse(editor.contains(".clipToBounds()"))
        assertFalse(editor.contains("positionInParent()"))
        assertFalse(editor.contains("ContextualFlowRow"))
        assertFalse(editor.contains("ContextualFlowRowOverflow"))
        assertFalse(editor.contains("FlowRowOverflow"))

        assertTrue(tokens.contains("ModelSettingsActionHeight = 40.dp"))
        assertTrue(tokens.contains("ModelSettingsActionShape = RoundedCornerShape(12.dp)"))
        assertTrue(tokens.contains("ModelSettingsActionIconSize = 17.dp"))

        val modelTagBlock =
            editor
                .substringAfter("private fun ModelNameTag(")
                .substringBefore("private fun ModelOverflowChip(")
        assertTrue(modelTagBlock.contains("softWrap = true"))
        assertFalse(modelTagBlock.contains("maxLines = 1"))
        assertFalse(modelTagBlock.contains("TextOverflow.Ellipsis"))

        val overflowChipBlock =
            editor
                .substringAfter("private fun ModelOverflowChip(")
                .substringBefore("private fun ModelManualAddDialog(")
        assertTrue(overflowChipBlock.contains("Text("))
        assertFalse(overflowChipBlock.contains("IconButton("))
        assertFalse(overflowChipBlock.contains("Icons.Default.Clear"))
        assertFalse(overflowChipBlock.contains("onDelete"))
    }

    @Test
    fun `upstream picker uses the modern sheet and preserves the single owner`() {
        val section =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ModelApiSettingsSection.kt"
            ).readText()
        val picker =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/components/UpstreamModelPickerSheet.kt"
            ).readText()

        assertTrue(section.contains("ModelNameTagEditor("))
        assertTrue(section.contains("UpstreamModelPickerSheet("))
        assertTrue(section.contains("mergeModelNames"))
        assertTrue(section.contains("fetchAvailableModels"))
        assertTrue(section.contains("ModelConfigModelBindingCoordinator"))
        assertTrue(section.contains("reconcileUpstreamModelSelection"))
        assertTrue(section.contains("PendingUpstreamModelSelection"))
        assertTrue(section.contains("modelNames = change.removedModels"))
        assertFalse(section.contains("modelNameInput"))

        val upstreamHostBlock =
            section
                .substringAfter("if (showModelsDialog) {")
                .substringBefore("pendingUpstreamModelSelection?.let")
        assertTrue(upstreamHostBlock.contains("UpstreamModelPickerSheet("))
        assertFalse(upstreamHostBlock.contains("Dialog("))
        assertFalse(upstreamHostBlock.contains(".heightIn(max = 520.dp)"))

        assertTrue(picker.contains("ModalBottomSheet("))
        assertTrue(picker.contains("model_upstream_select_filtered"))
        assertTrue(picker.contains("model_upstream_apply_changes"))
        assertTrue(picker.contains("existingUpstreamModelIds"))
        assertTrue(picker.contains("onApplySelection(selectedModels)"))
        assertTrue(picker.contains("checked = isSelected"))
        assertFalse(picker.contains("enabled = !isExisting"))
        assertTrue(picker.contains(".heightIn(min = 56.dp)"))
        assertTrue(picker.contains(".imePadding()"))
        assertTrue(picker.contains(".heightIn(min = 360.dp, max = 720.dp)"))
        assertFalse(picker.contains(".fillMaxHeight("))
        assertFalse(picker.contains(".height(48.dp)"))
    }

    @Test
    fun `connection testing keeps the first model and all enabled capabilities`() {
        val screen =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/ModelConfigScreen.kt"
            ).readText()
        val tester =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/ModelConfigConnectionTester.kt"
            ).readText()

        assertTrue(screen.contains("test_model"))
        assertTrue(screen.contains("tested_model_name"))
        assertTrue(screen.contains("getModelList(selected.modelName)"))
        assertTrue(screen.contains("FilledTonalButton("))
        assertTrue(screen.contains("ModelSettingsActionHeight"))
        assertTrue(screen.contains("ModelSettingsActionShape"))
        assertFalse(screen.contains("test_first_model"))

        assertTrue(tester.contains("requestedModelIndex: Int = 0"))
        assertTrue(tester.contains("getValidModelIndex(config.modelName, requestedModelIndex)"))
        assertTrue(tester.contains("config.copy(modelName = testedModelName)"))
        assertTrue(tester.contains("runCase(ModelConnectionTestType.CHAT)"))
        assertTrue(tester.contains("configForTest.enableToolCall"))
        assertTrue(tester.contains("runCase(ModelConnectionTestType.TOOL_CALL)"))
        assertTrue(tester.contains("configForTest.enableDirectImageProcessing"))
        assertTrue(tester.contains("runCase(ModelConnectionTestType.IMAGE)"))
        assertTrue(tester.contains("configForTest.enableDirectAudioProcessing"))
        assertTrue(tester.contains("runCase(ModelConnectionTestType.AUDIO)"))
        assertTrue(tester.contains("configForTest.enableDirectVideoProcessing"))
        assertTrue(tester.contains("runCase(ModelConnectionTestType.VIDEO)"))
    }

    @Test
    fun `clear all immediately exposes checking confirmation or blocked dialogs`() {
        val section =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/ui/features/settings/sections/ModelApiSettingsSection.kt"
            ).readText()
        val requestClearBlock =
            section
                .substringAfter("fun requestClearModels()")
                .substringBefore("suspend fun clearModelsWithUndo()")

        assertTrue(section.contains("private sealed interface ModelClearDialogState"))
        assertTrue(section.contains("ModelClearDialogState.Checking"))
        assertTrue(section.contains("ModelClearDialogState.Confirm"))
        assertTrue(section.contains("ModelClearDialogState.Blocked"))
        assertTrue(section.contains("R.string.model_clear_checking"))
        assertTrue(section.contains("R.string.model_clear_bound_title"))
        assertFalse(section.contains("showClearModelsConfirmation"))
        assertTrue(
            requestClearBlock.indexOf("modelClearDialogState = ModelClearDialogState.Checking") <
                requestClearBlock.indexOf("scope.launch")
        )
        assertTrue(requestClearBlock.contains("ModelClearDialogState.Blocked(impact.totalCount)"))
        assertTrue(requestClearBlock.contains("ModelClearDialogState.Confirm(modelNamesInput.size)"))
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
