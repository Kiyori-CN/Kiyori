package com.ai.assistance.operit.util

import android.content.Context
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File

/**
 * Operit 源码与生态调用的稳定路径入口。
 *
 * 该对象只保留原 JVM API；目录计算、创建和插件目录净化均由 [KiyoriPaths] 唯一拥有。
 */
object OperitPaths {

    const val SHERPA_NCNN_MODELS_DIR_NAME = KiyoriPaths.SHERPA_NCNN_MODELS_DIR_NAME
    const val VECTOR_INDEX_DIR_NAME = KiyoriPaths.VECTOR_INDEX_DIR_NAME

    const val IMAGE_POOL_DIR_NAME = KiyoriPaths.IMAGE_POOL_DIR_NAME
    const val MEDIA_POOL_DIR_NAME = KiyoriPaths.MEDIA_POOL_DIR_NAME
    const val SKILL_REPO_ZIP_POOL_DIR_NAME = KiyoriPaths.SKILL_REPO_ZIP_POOL_DIR_NAME

    fun downloadsDir(): File = KiyoriPaths.downloadsDir()

    fun kiyoriRootDir(): File = KiyoriPaths.kiyoriRootDir()

    fun cleanOnExitDir(): File = KiyoriPaths.cleanOnExitDir()

    fun pluginsDir(): File = KiyoriPaths.pluginsDir()

    fun pluginConfigDir(pluginId: String): File = KiyoriPaths.pluginConfigDir(pluginId)

    fun cleanOnExitInternalDir(context: Context): File =
        KiyoriPaths.cleanOnExitInternalDir(context)

    fun mcpPluginsDir(): File = KiyoriPaths.mcpPluginsDir()

    fun bridgeDir(): File = KiyoriPaths.bridgeDir()

    fun exportsDir(): File = KiyoriPaths.exportsDir()

    fun workspaceDir(): File = KiyoriPaths.workspaceDir()

    fun workflowDir(): File = KiyoriPaths.workflowDir()

    fun mnnModelsDir(): File = KiyoriPaths.mnnModelsDir()

    fun llamaModelsDir(): File = KiyoriPaths.llamaModelsDir()

    fun outputImagesDir(): File = KiyoriPaths.outputImagesDir()

    fun errorDir(): File = KiyoriPaths.errorDir()

    fun testDir(): File = KiyoriPaths.testDir()

    fun webSessionDir(): File = KiyoriPaths.webSessionDir()

    fun skillsDir(): File = KiyoriPaths.skillsDir()

    fun webSessionUserscriptsDir(): File = KiyoriPaths.webSessionUserscriptsDir()

    fun privateWebSessionUserscriptsDir(context: Context): File =
        KiyoriPaths.privateWebSessionUserscriptsDir(context)

    fun browserDownloadsDir(): File = KiyoriPaths.browserDownloadsDir()

    fun sherpaNcnnModelsDir(context: Context): File =
        KiyoriPaths.sherpaNcnnModelsDir(context)

    fun vectorIndexDir(context: Context): File = KiyoriPaths.vectorIndexDir(context)

    fun imagePoolDir(baseDir: File): File = KiyoriPaths.imagePoolDir(baseDir)

    fun mediaPoolDir(baseDir: File): File = KiyoriPaths.mediaPoolDir(baseDir)

    fun skillRepoZipPoolDir(baseDir: File): File =
        KiyoriPaths.skillRepoZipPoolDir(baseDir)

    fun rawSnapshotExcludedFilesTopLevelDirNames(): Set<String> =
        KiyoriPaths.rawSnapshotExcludedFilesTopLevelDirNames()

    fun kiyoriRootPathSdcard(): String = KiyoriPaths.kiyoriRootPathSdcard()

    fun cleanOnExitPathSdcard(): String = KiyoriPaths.cleanOnExitPathSdcard()

    fun pluginsPathSdcard(): String = KiyoriPaths.pluginsPathSdcard()

    fun bridgePathSdcard(): String = KiyoriPaths.bridgePathSdcard()

    fun exportsPathSdcard(): String = KiyoriPaths.exportsPathSdcard()

    fun workspacePathSdcard(chatId: String): String =
        KiyoriPaths.workspacePathSdcard(chatId)

    fun testPathSdcard(): String = KiyoriPaths.testPathSdcard()

    fun webSessionUserscriptsPathSdcard(): String =
        KiyoriPaths.webSessionUserscriptsPathSdcard()
}
