package com.ai.assistance.operit.util

import android.content.Context
import android.os.Environment
import java.io.File

object OperitPaths {

    private const val KIYORI_DIR_NAME = "Kiyori"
    private const val CLEAN_ON_EXIT_DIR_NAME = "cleanOnExit"
    private const val PLUGINS_DIR_NAME = "plugins"
    private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"
    private const val BRIDGE_DIR_NAME = "bridge"
    private const val EXPORTS_DIR_NAME = "exports"
    private const val WORKSPACE_DIR_NAME = "workspace"
    private const val WORKFLOW_DIR_NAME = "workflow"
    private const val MODELS_DIR_NAME = "models"
    private const val MNN_MODELS_DIR_NAME = "mnn"
    private const val LLAMA_MODELS_DIR_NAME = "llama"
    private const val OUTPUT_IMAGES_DIR_NAME = "output images"
    private const val ERROR_DIR_NAME = "error"
    private const val TEST_DIR_NAME = "test"
    private const val WEBSESSION_DIR_NAME = "websession"
    private const val USERSCRIPTS_DIR_NAME = "userscripts"
    private const val SKILLS_DIR_NAME = "skills"

    const val SHERPA_NCNN_MODELS_DIR_NAME = ".sherpa_ncnn_models"
    const val VECTOR_INDEX_DIR_NAME = ".vector_index"

    const val IMAGE_POOL_DIR_NAME = "image_pool"
    const val MEDIA_POOL_DIR_NAME = "media_pool"
    const val SKILL_REPO_ZIP_POOL_DIR_NAME = "skill_repo_zip_pool"

    fun downloadsDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun kiyoriRootDir(): File {
        return ensureDir(File(downloadsDir(), KIYORI_DIR_NAME))
    }

    fun cleanOnExitDir(): File {
        return ensureDir(File(kiyoriRootDir(), CLEAN_ON_EXIT_DIR_NAME))
    }

    fun pluginsDir(): File {
        return ensureDir(File(kiyoriRootDir(), PLUGINS_DIR_NAME))
    }

    fun pluginConfigDir(pluginId: String): File {
        val trimmed = pluginId.trim()
        val safeBaseName =
            trimmed
                .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
                .trim('.', ' ')
                .ifBlank { "plugin" }
        val safeName =
            if (safeBaseName == trimmed) {
                safeBaseName
            } else {
                "$safeBaseName-${Integer.toHexString(trimmed.hashCode())}"
            }
        return ensureDir(File(pluginsDir(), safeName))
    }

    fun cleanOnExitInternalDir(context: Context): File {
        return ensureDir(File(ensureDir(File(context.cacheDir, KIYORI_DIR_NAME)), CLEAN_ON_EXIT_DIR_NAME))
    }

    fun mcpPluginsDir(): File {
        return ensureDir(File(kiyoriRootDir(), MCP_PLUGINS_DIR_NAME))
    }

    fun bridgeDir(): File {
        return ensureDir(File(kiyoriRootDir(), BRIDGE_DIR_NAME))
    }

    fun exportsDir(): File {
        return ensureDir(File(kiyoriRootDir(), EXPORTS_DIR_NAME))
    }

    fun workspaceDir(): File {
        return ensureDir(File(kiyoriRootDir(), WORKSPACE_DIR_NAME))
    }

    fun workflowDir(): File {
        return ensureDir(File(kiyoriRootDir(), WORKFLOW_DIR_NAME))
    }

    fun mnnModelsDir(): File {
        return ensureDir(File(File(kiyoriRootDir(), MODELS_DIR_NAME), MNN_MODELS_DIR_NAME))
    }

    fun llamaModelsDir(): File {
        return ensureDir(File(File(kiyoriRootDir(), MODELS_DIR_NAME), LLAMA_MODELS_DIR_NAME))
    }

    fun outputImagesDir(): File {
        return ensureDir(File(kiyoriRootDir(), OUTPUT_IMAGES_DIR_NAME))
    }

    fun errorDir(): File {
        return ensureDir(File(kiyoriRootDir(), ERROR_DIR_NAME))
    }

    fun testDir(): File {
        return ensureDir(File(kiyoriRootDir(), TEST_DIR_NAME))
    }

    fun webSessionDir(): File {
        return ensureDir(File(kiyoriRootDir(), WEBSESSION_DIR_NAME))
    }

    fun skillsDir(): File {
        return ensureDir(File(kiyoriRootDir(), SKILLS_DIR_NAME))
    }

    fun webSessionUserscriptsDir(): File {
        return ensureDir(File(webSessionDir(), USERSCRIPTS_DIR_NAME))
    }

    fun sherpaNcnnModelsDir(context: Context): File {
        return ensureDir(File(context.filesDir, SHERPA_NCNN_MODELS_DIR_NAME))
    }

    fun vectorIndexDir(context: Context): File {
        return ensureDir(File(context.filesDir, VECTOR_INDEX_DIR_NAME))
    }

    fun imagePoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, IMAGE_POOL_DIR_NAME))
    }

    fun mediaPoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, MEDIA_POOL_DIR_NAME))
    }

    fun skillRepoZipPoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, SKILL_REPO_ZIP_POOL_DIR_NAME))
    }

    fun rawSnapshotExcludedFilesTopLevelDirNames(): Set<String> {
        return setOf(
            SHERPA_NCNN_MODELS_DIR_NAME,
            VECTOR_INDEX_DIR_NAME,
            IMAGE_POOL_DIR_NAME,
            MEDIA_POOL_DIR_NAME,
            SKILL_REPO_ZIP_POOL_DIR_NAME
        )
    }

    fun kiyoriRootPathSdcard(): String {
        return "/sdcard/Download/$KIYORI_DIR_NAME"
    }

    fun cleanOnExitPathSdcard(): String {
        return "${kiyoriRootPathSdcard()}/$CLEAN_ON_EXIT_DIR_NAME"
    }

    fun pluginsPathSdcard(): String {
        return "${kiyoriRootPathSdcard()}/$PLUGINS_DIR_NAME"
    }

    fun bridgePathSdcard(): String {
        return "${kiyoriRootPathSdcard()}/$BRIDGE_DIR_NAME"
    }

    fun exportsPathSdcard(): String {
        return "${kiyoriRootPathSdcard()}/$EXPORTS_DIR_NAME"
    }

    fun workspacePathSdcard(chatId: String): String {
        return "${kiyoriRootPathSdcard()}/$WORKSPACE_DIR_NAME/$chatId"
    }

    fun testPathSdcard(): String {
        return "${kiyoriRootPathSdcard()}/$TEST_DIR_NAME"
    }

    fun webSessionUserscriptsPathSdcard(): String {
        return "${kiyoriRootPathSdcard()}/$WEBSESSION_DIR_NAME/$USERSCRIPTS_DIR_NAME"
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
