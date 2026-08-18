package com.kiyori.platform.storage

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Kiyori 目录布局的唯一计算 owner。
 *
 * 目录名、大小写和层级都是既有持久数据与外部脚本合同。所有兼容入口只能委派到这里；
 * 如果在 facade 或消费者中重新拼接路径，同一数据可能被写入两个不同目录。
 */
object KiyoriPaths {

    private const val KIYORI_DIR_NAME = "Kiyori"
    private const val DOWNLOADS_COLLECTION_DIR_NAME = "Download"
    private const val PICTURES_COLLECTION_DIR_NAME = "Pictures"
    private const val CLEAN_ON_EXIT_DIR_NAME = "cleanOnExit"
    private const val PLUGINS_DIR_NAME = "plugins"
    private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"
    private const val BRIDGE_DIR_NAME = "bridge"
    private const val EXPORTS_DIR_NAME = "exports"
    private const val EXPORT_BROWSER_DIR_NAME = "browser"
    private const val EXPORT_USERSCRIPTS_DIR_NAME = "userscripts"
    private const val EXPORT_PLAYER_DIR_NAME = "player"
    private const val EXPORT_TOOLBOX_DIR_NAME = "toolbox"
    private const val EXPORT_AI_CONFIG_DIR_NAME = "ai-config"
    private const val EXPORT_CONVERSATION_AUDIT_DIR_NAME = "conversation-audit"
    private const val EXPORT_BACKUPS_DIR_NAME = "backups"
    private const val EXPORT_TOOLPKG_DIR_NAME = "toolpkg"
    private const val TOOLPKG_PUBLIC_DIR_NAME = "toolpkg"
    private const val TOOLPKG_PUBLIC_WORKSPACE_DIR_NAME = "public"
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
    private const val BROWSER_DIR_NAME = "browser"
    private const val BROWSER_DOWNLOADS_DIR_NAME = "downloads"
    private const val BACKUP_DIR_NAME = "backup"
    private const val RAW_SNAPSHOT_DIR_NAME = "raw_snapshot"
    private const val ROOM_DB_DIR_NAME = "room_db"
    private const val CHAT_DIR_NAME = "chat"
    private const val MEMORY_DIR_NAME = "memory"
    private const val MODEL_CONFIG_DIR_NAME = "model_config"
    private const val CHARACTER_CARDS_DIR_NAME = "character_cards"
    private const val PICTURES_MARKDOWN_DIR_NAME = "Markdown"
    private const val PICTURES_SHARED_DIR_NAME = "Shared"
    private const val PICTURES_AI_DIR_NAME = "AI"
    private const val INTERNAL_TOOLPKG_DIR_NAME = "toolpkg"
    private const val INTERNAL_TOOLPKG_VERSION_DIR_NAME = "v1"
    private const val INTERNAL_TOOLPKG_DATA_DIR_NAME = "data"
    private const val INTERNAL_TOOLPKG_GENERATIONS_DIR_NAME = "generations"
    private const val INTERNAL_TOOLPKG_MIGRATION_AUDIT_DIR_NAME = "migration-audit"
    private const val INTERNAL_TOOLPKG_ACTIVE_GENERATION_FILE_NAME = "active-generation.json"
    private const val INTERNAL_TOOLPKG_GENERATION_METADATA_FILE_NAME = "generation.json"
    private const val TOOLPKG_RUNTIME_DIR_NAME = "toolpkg-runtime"
    private const val TOOLPKG_RUNTIME_VERSION_DIR_NAME = "v1"
    private const val TOOLPKG_ARTIFACTS_DIR_NAME = "artifacts"
    private const val TOOLPKG_EXTRACTED_DIR_NAME = "extracted"
    private const val TOOLPKG_ACTIVE_DIR_NAME = "active"
    private const val TOOLPKG_AUDIT_DIR_NAME = "audit"
    private const val TOOLPKG_MARKET_DIR_NAME = "market"
    private const val TOOLPKG_BUILD_DIR_NAME = "toolpkg-build"
    private const val INTERNAL_LOGS_DIR_NAME = "logs"
    private const val INTERNAL_ERROR_DIR_NAME = "errors"
    private const val INTERNAL_BACKUP_STAGING_DIR_NAME = "backup-staging"
    private const val INTERNAL_CONVERSATION_AUDIT_DIR_NAME = "conversation-audit"
    private const val INTERNAL_CONVERSATION_AUDIT_VERSION_DIR_NAME = "v1"
    private const val INTERNAL_CONVERSATION_AUDIT_PAYLOADS_DIR_NAME = "payloads"
    private const val INTERNAL_CONVERSATION_AUDIT_STAGING_DIR_NAME = "staging"

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
        return ensureDir(File(pluginsDir(), pluginConfigDirectoryName(pluginId)))
    }

    internal fun pluginConfigDirectoryName(pluginId: String): String {
        val trimmed = pluginId.trim()
        val safeBaseName =
            trimmed
                .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
                .trim('.', ' ')
                .ifBlank { "plugin" }
        return if (safeBaseName == trimmed) {
            safeBaseName
        } else {
            "$safeBaseName-${Integer.toHexString(trimmed.hashCode())}"
        }
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

    fun conversationAuditExportsDir(): File {
        return ensureDir(File(exportsDir(), EXPORT_CONVERSATION_AUDIT_DIR_NAME))
    }

    fun conversationAuditRootDir(context: Context): File {
        return ensureDir(
            File(
                File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_CONVERSATION_AUDIT_DIR_NAME),
                INTERNAL_CONVERSATION_AUDIT_VERSION_DIR_NAME,
            ),
        )
    }

    fun conversationAuditPayloadsDir(context: Context): File {
        return ensureDir(
            File(
                conversationAuditRootDir(context),
                INTERNAL_CONVERSATION_AUDIT_PAYLOADS_DIR_NAME,
            ),
        )
    }

    fun conversationAuditStagingDir(context: Context): File {
        return ensureDir(
            File(
                conversationAuditRootDir(context),
                INTERNAL_CONVERSATION_AUDIT_STAGING_DIR_NAME,
            ),
        )
    }

    fun exportDir(location: KiyoriPublicLocation): File {
        val projection = publicProjection(location)
        require(projection.collection == KiyoriPublicCollection.DOWNLOADS) {
            "Export location must use the Downloads collection: $location"
        }
        return ensureDir(File(downloadsDir(), projection.relativePath.removePrefix("Download/")))
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

    fun privateWebSessionUserscriptsDir(context: Context): File {
        return ensureDir(File(File(context.filesDir, WEBSESSION_DIR_NAME), USERSCRIPTS_DIR_NAME))
    }

    fun browserDownloadsDir(): File {
        return ensureDir(File(File(kiyoriRootDir(), BROWSER_DIR_NAME), BROWSER_DOWNLOADS_DIR_NAME))
    }

    fun browserApplicationDownloadsDir(context: Context): File {
        val externalDownloads =
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
                "Application download directory is unavailable"
            }
        return ensureDir(File(externalDownloads, BROWSER_DIR_NAME))
    }

    fun publicProjection(location: KiyoriPublicLocation): KiyoriPublicPathProjection {
        return when (location) {
            KiyoriPublicLocation.BROWSER_DOWNLOADS ->
                downloadProjection(BROWSER_DIR_NAME, BROWSER_DOWNLOADS_DIR_NAME)
            KiyoriPublicLocation.EXPORT_BROWSER ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_BROWSER_DIR_NAME)
            KiyoriPublicLocation.EXPORT_USERSCRIPTS ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_USERSCRIPTS_DIR_NAME)
            KiyoriPublicLocation.EXPORT_PLAYER ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_PLAYER_DIR_NAME)
            KiyoriPublicLocation.EXPORT_TOOLBOX ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_TOOLBOX_DIR_NAME)
            KiyoriPublicLocation.EXPORT_AI_CONFIG ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_AI_CONFIG_DIR_NAME)
            KiyoriPublicLocation.EXPORT_BACKUPS ->
                downloadProjection(EXPORTS_DIR_NAME, EXPORT_BACKUPS_DIR_NAME)
            KiyoriPublicLocation.PICTURE_MARKDOWN ->
                pictureProjection(PICTURES_MARKDOWN_DIR_NAME)
            KiyoriPublicLocation.PICTURE_SHARED ->
                pictureProjection(PICTURES_SHARED_DIR_NAME)
            KiyoriPublicLocation.PICTURE_AI ->
                pictureProjection(PICTURES_AI_DIR_NAME)
        }
    }

    fun toolPkgPublicWorkspaceDir(packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(
                File(File(kiyoriRootDir(), TOOLPKG_PUBLIC_DIR_NAME), packageKey),
                TOOLPKG_PUBLIC_WORKSPACE_DIR_NAME,
            ),
        )
    }

    fun toolPkgExportDir(packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(File(exportsDir(), EXPORT_TOOLPKG_DIR_NAME), packageKey),
        )
    }

    fun toolPkgPrivateRootDir(context: Context, packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(
                File(
                    File(context.noBackupFilesDir, KIYORI_DIR_NAME),
                    INTERNAL_TOOLPKG_DIR_NAME,
                ),
                "$INTERNAL_TOOLPKG_VERSION_DIR_NAME${File.separator}$packageKey",
            ),
        )
    }

    fun toolPkgPrivateDataDir(context: Context, packageKey: String): File {
        return ensureDir(File(toolPkgPrivateRootDir(context, packageKey), INTERNAL_TOOLPKG_DATA_DIR_NAME))
    }

    fun toolPkgGenerationsDir(context: Context, packageKey: String): File {
        return ensureDir(File(toolPkgPrivateRootDir(context, packageKey), INTERNAL_TOOLPKG_GENERATIONS_DIR_NAME))
    }

    fun toolPkgGenerationDir(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File {
        requireSafePathSegment(generationId, "generationId")
        return File(toolPkgGenerationsDir(context, packageKey), generationId)
    }

    fun toolPkgGenerationDataDir(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File {
        return File(
            toolPkgGenerationDir(context, packageKey, generationId),
            INTERNAL_TOOLPKG_DATA_DIR_NAME,
        )
    }

    fun toolPkgGenerationMetadataFile(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File {
        return File(
            toolPkgGenerationDir(context, packageKey, generationId),
            INTERNAL_TOOLPKG_GENERATION_METADATA_FILE_NAME,
        )
    }

    fun toolPkgMigrationAuditDir(context: Context, packageKey: String): File {
        return ensureDir(
            File(toolPkgPrivateRootDir(context, packageKey), INTERNAL_TOOLPKG_MIGRATION_AUDIT_DIR_NAME),
        )
    }

    fun toolPkgMigrationAuditFile(
        context: Context,
        packageKey: String,
        generationId: String,
    ): File {
        requireSafePathSegment(generationId, "generationId")
        return File(toolPkgMigrationAuditDir(context, packageKey), "$generationId.json")
    }

    fun toolPkgActiveGenerationFile(context: Context, packageKey: String): File {
        return File(
            toolPkgPrivateRootDir(context, packageKey),
            INTERNAL_TOOLPKG_ACTIVE_GENERATION_FILE_NAME,
        )
    }

    fun toolPkgCacheDir(context: Context, packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(
                File(
                    File(context.cacheDir, KIYORI_DIR_NAME),
                    INTERNAL_TOOLPKG_DIR_NAME,
                ),
                "$INTERNAL_TOOLPKG_VERSION_DIR_NAME${File.separator}$packageKey",
            ),
        )
    }

    fun toolPkgBuildTransactionDir(context: Context, transactionId: String): File {
        requireSafePathSegment(transactionId, "transactionId")
        return ensureDir(
            File(
                File(File(context.cacheDir, KIYORI_DIR_NAME), TOOLPKG_BUILD_DIR_NAME),
                transactionId,
            ),
        )
    }

    fun toolPkgRuntimeRootDir(context: Context): File {
        return ensureDir(
            File(
                File(File(context.filesDir, KIYORI_DIR_NAME), TOOLPKG_RUNTIME_DIR_NAME),
                TOOLPKG_RUNTIME_VERSION_DIR_NAME,
            ),
        )
    }

    fun toolPkgArtifactsDir(context: Context): File {
        return ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_ARTIFACTS_DIR_NAME))
    }

    fun toolPkgExtractedDir(context: Context): File {
        return ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_EXTRACTED_DIR_NAME))
    }

    fun toolPkgActiveDir(context: Context): File {
        return ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_ACTIVE_DIR_NAME))
    }

    fun toolPkgAuditDir(context: Context): File {
        return ensureDir(File(toolPkgRuntimeRootDir(context), TOOLPKG_AUDIT_DIR_NAME))
    }

    fun toolPkgMarketDir(context: Context, packageKey: String): File {
        requirePackageKey(packageKey)
        return ensureDir(
            File(File(toolPkgRuntimeRootDir(context), TOOLPKG_MARKET_DIR_NAME), packageKey),
        )
    }

    fun internalLogsDir(context: Context): File {
        return ensureDir(File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_LOGS_DIR_NAME))
    }

    fun internalErrorsDir(context: Context): File {
        return ensureDir(File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_ERROR_DIR_NAME))
    }

    fun internalBackupStagingDir(context: Context): File {
        return ensureDir(
            File(File(context.filesDir, KIYORI_DIR_NAME), INTERNAL_BACKUP_STAGING_DIR_NAME),
        )
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

    fun browserDownloadsRelativePath(): String {
        return publicProjection(KiyoriPublicLocation.BROWSER_DOWNLOADS).relativePath
    }

    fun backupRootDir(): File {
        return backupRootDir(kiyoriRootDir())
    }

    internal fun backupRootDir(kiyoriRootDir: File): File {
        return ensureDir(File(kiyoriRootDir, BACKUP_DIR_NAME))
    }

    fun rawSnapshotDir(): File {
        return rawSnapshotDir(kiyoriRootDir())
    }

    internal fun rawSnapshotDir(kiyoriRootDir: File): File {
        return ensureDir(File(backupRootDir(kiyoriRootDir), RAW_SNAPSHOT_DIR_NAME))
    }

    fun roomDbDir(): File {
        return roomDbDir(kiyoriRootDir())
    }

    internal fun roomDbDir(kiyoriRootDir: File): File {
        return ensureDir(File(backupRootDir(kiyoriRootDir), ROOM_DB_DIR_NAME))
    }

    fun chatDir(): File {
        return chatDir(kiyoriRootDir())
    }

    internal fun chatDir(kiyoriRootDir: File): File {
        return ensureDir(File(backupRootDir(kiyoriRootDir), CHAT_DIR_NAME))
    }

    fun memoryDir(): File {
        return memoryDir(kiyoriRootDir())
    }

    internal fun memoryDir(kiyoriRootDir: File): File {
        return ensureDir(File(backupRootDir(kiyoriRootDir), MEMORY_DIR_NAME))
    }

    fun modelConfigDir(): File {
        return modelConfigDir(kiyoriRootDir())
    }

    internal fun modelConfigDir(kiyoriRootDir: File): File {
        return ensureDir(File(backupRootDir(kiyoriRootDir), MODEL_CONFIG_DIR_NAME))
    }

    fun characterCardsDir(): File {
        return characterCardsDir(kiyoriRootDir())
    }

    internal fun characterCardsDir(kiyoriRootDir: File): File {
        return ensureDir(File(backupRootDir(kiyoriRootDir), CHARACTER_CARDS_DIR_NAME))
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun downloadProjection(vararg segments: String): KiyoriPublicPathProjection {
        val relativePath =
            listOf(DOWNLOADS_COLLECTION_DIR_NAME, KIYORI_DIR_NAME, *segments)
                .joinToString("/")
        return KiyoriPublicPathProjection(
            collection = KiyoriPublicCollection.DOWNLOADS,
            relativePath = relativePath,
        )
    }

    private fun pictureProjection(vararg segments: String): KiyoriPublicPathProjection {
        val relativePath =
            listOf(PICTURES_COLLECTION_DIR_NAME, KIYORI_DIR_NAME, *segments)
                .joinToString("/")
        return KiyoriPublicPathProjection(
            collection = KiyoriPublicCollection.PICTURES,
            relativePath = relativePath,
        )
    }

    private fun requirePackageKey(packageKey: String) {
        requireSafePathSegment(packageKey, "packageKey")
    }

    private fun requireSafePathSegment(value: String, label: String) {
        require(value.isNotBlank()) { "$label is required" }
        require(value == value.trim()) { "$label must not have surrounding whitespace" }
        require(value != "." && value != "..") { "$label is invalid" }
        require(value.none { it == '/' || it == '\\' || it.code < 32 }) {
            "$label contains an invalid path character"
        }
    }
}

enum class KiyoriPublicCollection {
    DOWNLOADS,
    PICTURES,
}

enum class KiyoriPublicLocation {
    BROWSER_DOWNLOADS,
    EXPORT_BROWSER,
    EXPORT_USERSCRIPTS,
    EXPORT_PLAYER,
    EXPORT_TOOLBOX,
    EXPORT_AI_CONFIG,
    EXPORT_BACKUPS,
    PICTURE_MARKDOWN,
    PICTURE_SHARED,
    PICTURE_AI,
}

data class KiyoriPublicPathProjection(
    val collection: KiyoriPublicCollection,
    val relativePath: String,
)
