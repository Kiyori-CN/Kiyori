package com.ai.assistance.operit.ui.features.chat.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTemplateSharedAssetsTest {
    @Test
    fun androidWorkspaceMaterializesSharedAapt2AtTemplateContractPath() {
        assertEquals(
            listOf(
                WorkspaceTemplateSharedAsset(
                    sourceAssetPath = "templates/shared/android-tools/aapt2-arm64-v8a",
                    destinationRelativePath = "tools/aapt2/aapt2-arm64-v8a",
                )
            ),
            workspaceTemplateSharedAssets("android"),
        )
    }

    @Test
    fun flutterWorkspaceMaterializesSharedAapt2UnderAndroidSubproject() {
        assertEquals(
            listOf(
                WorkspaceTemplateSharedAsset(
                    sourceAssetPath = "templates/shared/android-tools/aapt2-arm64-v8a",
                    destinationRelativePath = "android/tools/aapt2/aapt2-arm64-v8a",
                )
            ),
            workspaceTemplateSharedAssets("flutter"),
        )
    }

    @Test
    fun nonAndroidTemplatesDoNotMaterializeAndroidToolchainAssets() {
        listOf("web", "node", "typescript", "python", "java", "go", "office", "blank")
            .forEach { templateName ->
                assertTrue(
                    "Unexpected shared assets for $templateName",
                    workspaceTemplateSharedAssets(templateName).isEmpty(),
                )
            }
    }
}
