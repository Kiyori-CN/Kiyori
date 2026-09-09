package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.AIToolHook
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import com.ai.assistance.operit.core.workspace.GitIgnoreFilter
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.github.difflib.DiffUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.ai.assistance.operit.core.workspace.WorkspaceChangeTracker

@Serializable
data class FileStat(
    val size: Long,
    val lastModified: Long
)

@Serializable
data class BackupManifest(
    val timestamp: Long,
    val files: Map<String, String>, // relativePath -> hash
    val fileStats: Map<String, FileStat> = emptyMap() // relativePath -> (size, lastModified)
)

class WorkspaceBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "WorkspaceBackupManager"
        private const val BACKUP_DIR_NAME = ".backup"
        private const val OBJECTS_DIR_NAME = "objects"
        private const val CHAT_BACKUPS_DIR_NAME = "chats"
        private const val CURRENT_STATE_FILE_NAME = "current_state.json"

        private val WORKSPACE_MUTATING_TOOLS =
            setOf(
                "apply_file",
                "create_file",
                "edit_file",
                "write_file",
                "write_file_binary",
                "move_file",
                "delete_file",
                "copy_file",
                "make_directory"
            )

        @Volatile
        private var INSTANCE: WorkspaceBackupManager? = null

        fun getInstance(context: Context): WorkspaceBackupManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkspaceBackupManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val toolHandler by lazy { AIToolHandler.getInstance(context) }

    data class WorkspaceFileChange(
        val path: String,
        val changeType: ChangeType,
        val changedLines: Int
    )

    enum class ChangeType {
        ADDED,
        DELETED,
        MODIFIED
    }

    /**
     * Synchronizes the workspace state based on the message timestamp.
     * It either creates a new backup or restores to a previous state.
     */
    suspend fun prepareRewind(
        workspacePath: String,
        messageTimestamp: Long,
        workspaceEnv: String? = null,
        chatId: String? = null
    ): suspend () -> Unit {
        val obsoleteManifests = syncStateProvider(workspacePath, workspaceEnv, messageTimestamp, chatId)
        // 文件系统与聊天数据库没有共同事务。只有历史提交成功后才能销毁重试所需的清单。
        return {
            withContext(Dispatchers.IO) {
                obsoleteManifests.forEach { deleteFileProvider(it, workspaceEnv) }
            }
        }
    }

    private data class HookSessionInit(
        val backupDir: String,
        val objectsDir: String,
        val currentState: BackupManifest,
        val gitignoreRules: List<String>
    )

    fun createWorkspaceToolHookSession(
        workspacePath: String,
        workspaceEnv: String?,
        messageTimestamp: Long,
        chatId: String?
    ): WorkspaceToolHookSession {
        return WorkspaceToolHookSession(workspacePath, workspaceEnv, messageTimestamp, chatId)
    }

    inner class WorkspaceToolHookSession(
        private val workspacePath: String,
        private val workspaceEnv: String?,
        private val messageTimestamp: Long,
        private val chatScopeId: String?
    ) : AIToolHook {
        private val closed = AtomicBoolean(false)
        private var initialized = false
        private var backupDir: String? = null
        private var objectsDir: String? = null
        private var currentState: BackupManifest? = null
        private var gitignoreRules: List<String> = emptyList()

        override fun onToolExecutionStarted(tool: AITool) {
            if (closed.get()) return
            if (!isWorkspaceMutatingTool(tool.name)) return

            val affectedPaths = extractWorkspaceAffectedPaths(tool, workspacePath, workspaceEnv)
            if (affectedPaths.isEmpty()) return
            WorkspaceChangeTracker.getInstance(context)
                .ignoreAiChanges(workspacePath, workspaceEnv, extractWorkspaceMutatedPaths(tool, workspacePath, workspaceEnv))

            runBlocking(Dispatchers.IO) {
                if (initialized) return@runBlocking

                val init = initializeHookSessionProvider(workspacePath, workspaceEnv, messageTimestamp, chatScopeId)
                if (init != null) {
                    backupDir = init.backupDir
                    objectsDir = init.objectsDir
                    currentState = init.currentState
                    gitignoreRules = init.gitignoreRules
                    initialized = true
                    AppLogger.d(
                        TAG,
                        "Workspace tool hook session initialized at timestamp=$messageTimestamp"
                    )
                }
            }
        }

        override fun onToolExecutionResult(tool: AITool, result: ToolResult) {
            if (closed.get() || !initialized || !result.success) return
            if (!isWorkspaceMutatingTool(tool.name)) return

            val affectedPaths =
                extractWorkspaceAffectedPaths(tool, workspacePath, workspaceEnv).distinct()
            if (affectedPaths.isEmpty()) return
            WorkspaceChangeTracker.getInstance(context)
                .ignoreAiChanges(workspacePath, workspaceEnv, extractWorkspaceMutatedPaths(tool, workspacePath, workspaceEnv))

            runBlocking(Dispatchers.IO) {
                val state = currentState ?: return@runBlocking
                val sessionObjectsDir = objectsDir ?: return@runBlocking

                val updatedFiles = state.files.toMutableMap()
                val updatedStats = state.fileStats.toMutableMap()

                for (affectedPath in affectedPaths) {
                    refreshPathInStateProvider(
                        workspacePath = workspacePath,
                        workspaceEnv = workspaceEnv,
                        targetPath = affectedPath,
                        objectsDir = sessionObjectsDir,
                        gitignoreRules = gitignoreRules,
                        files = updatedFiles,
                        stats = updatedStats
                    )
                }

                currentState =
                    BackupManifest(
                        timestamp = System.currentTimeMillis(),
                        files = updatedFiles,
                        fileStats = updatedStats
                    )
            }

            WorkspacePreviewRefreshBus.tryEmit(
                WorkspacePreviewRefreshEvent(
                    workspacePath = workspacePath,
                    workspaceEnv = workspaceEnv,
                    affectedPaths = affectedPaths,
                    source = tool.name
                )
            )
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            if (!initialized) return

            runBlocking(Dispatchers.IO) {
                val state = currentState ?: return@runBlocking
                val sessionBackupDir = backupDir ?: return@runBlocking
                saveCurrentStateManifestProvider(sessionBackupDir, workspaceEnv, state)
            }
        }
    }

    private fun isWorkspaceMutatingTool(toolName: String): Boolean {
        return WORKSPACE_MUTATING_TOOLS.contains(toolName)
    }

    private fun isEnvironmentMatchForWorkspace(toolEnv: String?, workspaceEnv: String?): Boolean {
        val normalizedToolEnv = toolEnv?.trim().orEmpty()
        val normalizedWorkspaceEnv = workspaceEnv?.trim().orEmpty()

        if (normalizedWorkspaceEnv.isBlank()) {
            return normalizedToolEnv.isBlank() || normalizedToolEnv.equals("android", ignoreCase = true)
        }

        return normalizedToolEnv.equals(normalizedWorkspaceEnv, ignoreCase = true)
    }

    private fun extractWorkspaceAffectedPaths(
        tool: AITool,
        workspacePath: String,
        workspaceEnv: String?
    ): List<String> {
        val result = mutableListOf<String>()

        fun collectPath(path: String?, toolEnv: String?) {
            var normalizedPath = path?.trim()?.trimEnd('/').orEmpty()
            if (normalizedPath.isBlank()) return
            if (!isEnvironmentMatchForWorkspace(toolEnv, workspaceEnv)) return

            if (makeRelativePath(workspacePath, normalizedPath) == null && !normalizedPath.startsWith("/")) {
                normalizedPath = joinPath(workspacePath, normalizedPath)
            }

            val relativePath = makeRelativePath(workspacePath, normalizedPath) ?: return
            if (
                relativePath == BACKUP_DIR_NAME ||
                    relativePath.startsWith("$BACKUP_DIR_NAME/")
            ) {
                return
            }
            result.add(normalizedPath)
        }

        val defaultEnvironment = tool.parameters.find { it.name == "environment" }?.value

        when (tool.name) {
            "apply_file", "create_file", "edit_file", "delete_file", "write_file", "write_file_binary" -> {
                collectPath(
                    tool.parameters.find { it.name == "path" }?.value,
                    defaultEnvironment
                )
            }

            "make_directory" -> {
                collectPath(
                    tool.parameters.find { it.name == "path" }?.value,
                    defaultEnvironment
                )
            }

            "move_file" -> {
                collectPath(
                    tool.parameters.find { it.name == "source" }?.value,
                    defaultEnvironment
                )
                collectPath(
                    tool.parameters.find { it.name == "destination" }?.value,
                    defaultEnvironment
                )
            }

            "copy_file" -> {
                val sourceEnvironment =
                    tool.parameters.find { it.name == "source_environment" }?.value
                        ?: defaultEnvironment
                val destinationEnvironment =
                    tool.parameters.find { it.name == "dest_environment" }?.value
                        ?: defaultEnvironment
                collectPath(
                    tool.parameters.find { it.name == "source" }?.value,
                    sourceEnvironment
                )
                collectPath(
                    tool.parameters.find { it.name == "destination" }?.value,
                    destinationEnvironment
                )
            }
        }

        return result
    }

    private fun extractWorkspaceMutatedPaths(
        tool: AITool,
        workspacePath: String,
        workspaceEnv: String?
    ): List<String> {
        val result = mutableListOf<String>()

        fun collectPath(path: String?, toolEnv: String?) {
            var normalizedPath = path?.trim()?.trimEnd('/').orEmpty()
            if (normalizedPath.isBlank()) return
            if (!isEnvironmentMatchForWorkspace(toolEnv, workspaceEnv)) return

            if (makeRelativePath(workspacePath, normalizedPath) == null && !normalizedPath.startsWith("/")) {
                normalizedPath = joinPath(workspacePath, normalizedPath)
            }

            val relativePath = makeRelativePath(workspacePath, normalizedPath) ?: return
            if (
                relativePath == BACKUP_DIR_NAME ||
                    relativePath.startsWith("$BACKUP_DIR_NAME/")
            ) {
                return
            }
            result.add(normalizedPath)
        }

        val defaultEnvironment = tool.parameters.find { it.name == "environment" }?.value

        when (tool.name) {
            "apply_file",
            "create_file",
            "edit_file",
            "delete_file",
            "write_file",
            "write_file_binary",
            "make_directory" -> {
                collectPath(
                    tool.parameters.find { it.name == "path" }?.value,
                    defaultEnvironment
                )
            }

            "move_file" -> {
                collectPath(
                    tool.parameters.find { it.name == "source" }?.value,
                    defaultEnvironment
                )
                collectPath(
                    tool.parameters.find { it.name == "destination" }?.value,
                    defaultEnvironment
                )
            }

            "copy_file" -> {
                val destinationEnvironment =
                    tool.parameters.find { it.name == "dest_environment" }?.value
                        ?: defaultEnvironment
                collectPath(
                    tool.parameters.find { it.name == "destination" }?.value,
                    destinationEnvironment
                )
            }
        }

        return result.distinct()
    }

    private suspend fun initializeHookSessionProvider(
        workspacePath: String,
        workspaceEnv: String?,
        messageTimestamp: Long,
        chatId: String?
    ): HookSessionInit? {
        val workspaceInfo = fileExistsProvider(workspacePath, workspaceEnv)
        if (workspaceInfo == null || !workspaceInfo.exists || !workspaceInfo.isDirectory) {
            AppLogger.w(TAG, "Workspace hook ignored: invalid workspace path $workspacePath")
            return null
        }

        val backupRootDir = joinPath(workspacePath, BACKUP_DIR_NAME)
        ensureDirectory(backupRootDir, workspaceEnv)
        val backupDir = resolveChatBackupDir(backupRootDir, chatId)
        ensureDirectory(backupDir, workspaceEnv)

        val objectsDir = joinPath(backupRootDir, OBJECTS_DIR_NAME)
        ensureDirectory(objectsDir, workspaceEnv)

        val existingBackups = listBackupsInBackupDir(backupDir, workspaceEnv)
        val targetManifestPath = joinPath(backupDir, "$messageTimestamp.json")
        val hasTargetManifest =
            fileExistsProvider(targetManifestPath, workspaceEnv)?.exists == true
        val gitignoreRules = loadGitignoreRulesProvider(workspacePath, workspaceEnv)

        var currentState = loadCurrentStateManifestProvider(backupDir, workspaceEnv)
        if (currentState == null) {
            if (hasTargetManifest) {
                currentState = loadBackupManifestProvider(backupDir, messageTimestamp, workspaceEnv)
            }
            if (currentState == null) {
                val latestTimestamp = existingBackups.lastOrNull()
                if (latestTimestamp != null) {
                    currentState = loadBackupManifestProvider(backupDir, latestTimestamp, workspaceEnv)
                }
            }
            if (currentState == null) {
                currentState = BackupManifest(timestamp = messageTimestamp, files = emptyMap(), fileStats = emptyMap())
            }
        }

        if (!hasTargetManifest) {
            writeBackupManifestProvider(
                backupDir = backupDir,
                timestamp = messageTimestamp,
                workspaceEnv = workspaceEnv,
                manifest = currentState.copy(timestamp = messageTimestamp)
            )
        }

        saveCurrentStateManifestProvider(backupDir, workspaceEnv, currentState)
        return HookSessionInit(
            backupDir = backupDir,
            objectsDir = objectsDir,
            currentState = currentState,
            gitignoreRules = gitignoreRules
        )
    }

    private suspend fun loadCurrentStateManifestProvider(
        backupDir: String,
        workspaceEnv: String?
    ): BackupManifest? {
        val statePath = joinPath(backupDir, CURRENT_STATE_FILE_NAME)
        if (fileExistsProvider(statePath, workspaceEnv)?.exists != true) return null
        val readRes =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_full",
                    parameters =
                        withWorkspaceEnvParams(
                            listOf(
                                ToolParameter("path", statePath),
                                ToolParameter("text_only", "true")
                            ),
                            workspaceEnv
                        )
                )
            )
        val content = (readRes.result as? FileContentData)?.content
        check(readRes.success && !content.isNullOrBlank()) { "Cannot read workspace current-state manifest" }
        return json.decodeFromString<BackupManifest>(content)
    }

    private suspend fun saveCurrentStateManifestProvider(
        backupDir: String,
        workspaceEnv: String?,
        manifest: BackupManifest
    ) {
        val statePath = joinPath(backupDir, CURRENT_STATE_FILE_NAME)
        val result = toolHandler.executeTool(
            AITool(
                name = "write_file",
                parameters =
                    withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", statePath),
                            ToolParameter("content", json.encodeToString(manifest)),
                            ToolParameter("append", "false")
                        ),
                        workspaceEnv
                    )
            )
        )
        check(result.success) { result.error ?: "Workspace backup write failed" }
    }

    private suspend fun writeBackupManifestProvider(
        backupDir: String,
        timestamp: Long,
        workspaceEnv: String?,
        manifest: BackupManifest
    ) {
        val manifestPath = joinPath(backupDir, "$timestamp.json")
        val result = toolHandler.executeTool(
            AITool(
                name = "write_file",
                parameters =
                    withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", manifestPath),
                            ToolParameter("content", json.encodeToString(manifest)),
                            ToolParameter("append", "false")
                        ),
                        workspaceEnv
                    )
            )
        )
        check(result.success) { result.error ?: "Workspace backup write failed" }
    }

    private suspend fun refreshPathInStateProvider(
        workspacePath: String,
        workspaceEnv: String?,
        targetPath: String,
        objectsDir: String,
        gitignoreRules: List<String>,
        files: MutableMap<String, String>,
        stats: MutableMap<String, FileStat>
    ) {
        val normalizedTargetPath = targetPath.trim().trimEnd('/').ifBlank { targetPath }
        val relativeTarget = makeRelativePath(workspacePath, normalizedTargetPath) ?: return

        removePathFromState(relativeTarget, files, stats)

        val existsData = fileExistsProvider(normalizedTargetPath, workspaceEnv)
        if (existsData == null || !existsData.exists) {
            return
        }

        if (existsData.isDirectory) {
            val childFiles =
                listWorkspaceTextFilesUnderPathProvider(
                    workspacePath = workspacePath,
                    startPath = normalizedTargetPath,
                    workspaceEnv = workspaceEnv,
                    gitignoreRules = gitignoreRules
                )
            for (childPath in childFiles) {
                val relativeChildPath = makeRelativePath(workspacePath, childPath) ?: continue
                val snapshot =
                    snapshotFileForStateProvider(childPath, workspaceEnv, objectsDir)
                        ?: continue
                files[relativeChildPath] = snapshot.first
                stats[relativeChildPath] = snapshot.second
            }
            return
        }

        val fileName = relativeTarget.substringAfterLast('/')
        if (!FileUtils.isTextBasedFileName(fileName)) {
            return
        }
        if (GitIgnoreFilter.shouldIgnore(relativeTarget, fileName, isDirectory = false, rules = gitignoreRules)) {
            return
        }

        val snapshot = snapshotFileForStateProvider(normalizedTargetPath, workspaceEnv, objectsDir)
            ?: return
        files[relativeTarget] = snapshot.first
        stats[relativeTarget] = snapshot.second
    }

    private suspend fun listWorkspaceTextFilesUnderPathProvider(
        workspacePath: String,
        startPath: String,
        workspaceEnv: String?,
        gitignoreRules: List<String>
    ): List<String> {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "find_files",
                    parameters =
                        withWorkspaceEnvParams(
                            listOf(
                                ToolParameter("path", startPath),
                                ToolParameter("pattern", "*"),
                                ToolParameter("use_path_pattern", "false"),
                                ToolParameter("case_insensitive", "false")
                            ),
                            workspaceEnv
                        )
                )
            )

        val allFiles = (res.result as? FindFilesResultData)?.files.orEmpty()
        val normalizedWorkspaceRoot = workspacePath.trimEnd('/')

        return allFiles
            .asSequence()
            .mapNotNull { fullPath ->
                val relative = makeRelativePath(normalizedWorkspaceRoot, fullPath) ?: return@mapNotNull null
                if (relative.isBlank()) return@mapNotNull null

                val fileName = relative.substringAfterLast('/')
                if (!FileUtils.isTextBasedFileName(fileName)) return@mapNotNull null
                if (GitIgnoreFilter.shouldIgnore(relative, fileName, isDirectory = false, rules = gitignoreRules)) {
                    return@mapNotNull null
                }

                fullPath
            }
            .toList()
    }

    private fun removePathFromState(
        relativePath: String,
        files: MutableMap<String, String>,
        stats: MutableMap<String, FileStat>
    ) {
        if (relativePath.isBlank()) {
            files.clear()
            stats.clear()
            return
        }

        val prefix = "$relativePath/"
        files.keys
            .filter { it == relativePath || it.startsWith(prefix) }
            .forEach { files.remove(it) }
        stats.keys
            .filter { it == relativePath || it.startsWith(prefix) }
            .forEach { stats.remove(it) }
    }

    private suspend fun snapshotFileForStateProvider(
        filePath: String,
        workspaceEnv: String?,
        objectsDir: String
    ): Pair<String, FileStat>? {
        val base64 = readBinaryBase64(filePath, workspaceEnv) ?: return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }

        val info = fileInfoProvider(filePath, workspaceEnv)
        val infoSize = info?.size
        val infoLastModifiedMs = info?.lastModified?.let { parseLastModifiedToMillis(it) }
        val stat =
            if (infoSize != null && infoLastModifiedMs != null) {
                FileStat(size = infoSize, lastModified = infoLastModifiedMs)
            } else {
                FileStat(size = bytes.size.toLong(), lastModified = 0L)
            }

        val objectPath = buildShardedObjectPath(objectsDir, hash)
        val objectExists = fileExistsProvider(objectPath, workspaceEnv)?.exists == true
        if (!objectExists) {
            val bucketDir = joinPath(objectsDir, objectBucketPrefix(hash))
            ensureDirectory(bucketDir, workspaceEnv)
            writeBinaryBase64(objectPath, base64, workspaceEnv)
        }

        return hash to stat
    }

    private fun withWorkspaceEnvParams(base: List<ToolParameter>, workspaceEnv: String?): List<ToolParameter> {
        if (workspaceEnv.isNullOrBlank()) return base
        return base + ToolParameter("environment", workspaceEnv)
    }

    private fun normalizeChatScope(chatId: String?): String {
        val raw = chatId?.trim().orEmpty()
        if (raw.isBlank()) return "__default__"
        return raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun resolveChatBackupDir(backupRootDir: String, chatId: String?): String {
        val chatsDir = joinPath(backupRootDir, CHAT_BACKUPS_DIR_NAME)
        return joinPath(chatsDir, normalizeChatScope(chatId))
    }

    private fun joinPath(parent: String, child: String): String {
        val p = parent.trimEnd('/')
        val c = child.trimStart('/')
        return if (p.isEmpty()) "/$c" else "$p/$c"
    }

    private fun makeRelativePath(root: String, fullPath: String): String? {
        val normalizedRoot = root.trimEnd('/')
        if (normalizedRoot.isBlank()) return null
        if (fullPath != normalizedRoot && !fullPath.startsWith("$normalizedRoot/")) return null
        return fullPath.removePrefix(normalizedRoot).trimStart('/').ifBlank { "" }
    }

    private fun objectBucketPrefix(hash: String): String {
        if (hash.length < 2) return "__"
        return hash.substring(0, 2)
    }

    private fun buildShardedObjectPath(objectsDir: String, hash: String): String {
        return joinPath(joinPath(objectsDir, objectBucketPrefix(hash)), hash)
    }

    private fun buildLegacyObjectPath(objectsDir: String, hash: String): String {
        return joinPath(objectsDir, hash)
    }

    private suspend fun resolveObjectPathForRead(objectsDir: String, hash: String, workspaceEnv: String?): String? {
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid workspace backup hash" }
        val sharded = buildShardedObjectPath(objectsDir, hash)
        if (fileExistsProvider(sharded, workspaceEnv)?.exists == true) return sharded

        val legacy = buildLegacyObjectPath(objectsDir, hash)
        if (fileExistsProvider(legacy, workspaceEnv)?.exists == true) return legacy

        return null
    }

    private suspend fun ensureDirectory(path: String, workspaceEnv: String?) {
        val result = toolHandler.executeTool(
            AITool(
                name = "make_directory",
                parameters = withWorkspaceEnvParams(
                    listOf(
                        ToolParameter("path", path),
                        ToolParameter("create_parents", "true")
                    ),
                    workspaceEnv
                )
            )
        )
        check(result.success) { result.error ?: "Workspace backup write failed" }
    }

    private suspend fun listBackupsInBackupDir(backupDir: String, workspaceEnv: String?): List<Long> {
        if (fileExistsProvider(backupDir, workspaceEnv)?.exists != true) return emptyList()
        val listRes =
            toolHandler.executeTool(
                AITool(
                    name = "list_files",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", backupDir)), workspaceEnv)
                )
            )

        check(listRes.success && listRes.result is DirectoryListingData) { "Cannot list workspace backups" }
        val entries = listRes.result.entries
        return entries
            .asSequence()
            .filter { !it.isDirectory }
            .map { it.name }
            .filter { it.endsWith(".json") }
            .mapNotNull { it.removeSuffix(".json").toLongOrNull() }
            .sorted()
            .toList()
    }

    private suspend fun loadBackupManifestProvider(backupDir: String, targetTimestamp: Long, workspaceEnv: String?): BackupManifest? {
        val manifestPath = joinPath(backupDir, "$targetTimestamp.json")
        val readRes =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_full",
                    parameters = withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", manifestPath),
                            ToolParameter("text_only", "true")
                        ),
                        workspaceEnv
                    )
                )
            )
        val content = (readRes.result as? FileContentData)?.content
        check(readRes.success && !content.isNullOrBlank()) { "Cannot read workspace backup manifest" }
        return json.decodeFromString<BackupManifest>(content)
    }

    private suspend fun readBinaryBase64(path: String, workspaceEnv: String?): String? {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_binary",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
                )
            )
        check(res.success && res.result is BinaryFileContentData) { "Cannot read workspace backup object" }
        return res.result.contentBase64
    }

    private suspend fun writeBinaryBase64(path: String, contentBase64: String, workspaceEnv: String?) {
        val result = toolHandler.executeTool(
            AITool(
                name = "write_file_binary",
                parameters = withWorkspaceEnvParams(
                    listOf(
                        ToolParameter("path", path),
                        ToolParameter("base64Content", contentBase64)
                    ),
                    workspaceEnv
                )
            )
        )
        check(result.success) { result.error ?: "Workspace backup write failed" }
    }

    private suspend fun deleteFileProvider(path: String, workspaceEnv: String?) {
        if (fileExistsProvider(path, workspaceEnv)?.exists != true) return
        val result = toolHandler.executeTool(
            AITool(
                name = "delete_file",
                parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
            )
        )
        check(result.success) { result.error ?: "Workspace backup write failed" }
    }

    private suspend fun fileExistsProvider(path: String, workspaceEnv: String?): FileExistsData? {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "file_exists",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
                )
            )
        check(res.success && res.result is FileExistsData) { "Cannot inspect workspace backup path" }
        return res.result
    }

    private suspend fun fileInfoProvider(path: String, workspaceEnv: String?): FileInfoData? {
        val res =
            toolHandler.executeTool(
                AITool(
                    name = "file_info",
                    parameters = withWorkspaceEnvParams(listOf(ToolParameter("path", path)), workspaceEnv)
                )
            )
        return res.result as? FileInfoData
    }

    private fun parseLastModifiedToMillis(lastModified: String): Long? {
        val raw = lastModified.trim()
        if (raw.isBlank()) return null

        val patterns = listOf("yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss")
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = true
                val date = sdf.parse(raw) ?: continue
                return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private suspend fun loadGitignoreRulesProvider(workspacePath: String, workspaceEnv: String?): List<String> {
        val rules = mutableListOf<String>()
        rules.addAll(listOf(".backup", ".operit"))

        val gitignorePath = joinPath(workspacePath, ".gitignore")
        val readRes =
            toolHandler.executeTool(
                AITool(
                    name = "read_file_full",
                    parameters = withWorkspaceEnvParams(
                        listOf(
                            ToolParameter("path", gitignorePath),
                            ToolParameter("text_only", "true")
                        ),
                        workspaceEnv
                    )
                )
            )
        val content = (readRes.result as? FileContentData)?.content
        if (readRes.success && !content.isNullOrBlank()) {
            content
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { rules.add(it) }
        }

        return rules
    }

    private suspend fun syncStateProvider(
        workspacePath: String,
        workspaceEnv: String?,
        messageTimestamp: Long,
        chatId: String?
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val exists = fileExistsProvider(workspacePath, workspaceEnv)
            if (exists == null || !exists.exists || !exists.isDirectory) {
                error("Workspace path does not exist or is not a directory")
            }

            val backupRootDir = joinPath(workspacePath, BACKUP_DIR_NAME)
            ensureDirectory(backupRootDir, workspaceEnv)
            val backupDir = resolveChatBackupDir(backupRootDir, chatId)
            ensureDirectory(backupDir, workspaceEnv)
            val objectsDir = joinPath(backupRootDir, OBJECTS_DIR_NAME)
            ensureDirectory(objectsDir, workspaceEnv)

            val existingBackups = listBackupsInBackupDir(backupDir, workspaceEnv)
            AppLogger.d(TAG, "syncState called for timestamp: $messageTimestamp. Existing backups: $existingBackups")

            var currentState = loadCurrentStateManifestProvider(backupDir, workspaceEnv)
            if (currentState == null) {
                val latestTimestamp = existingBackups.lastOrNull()
                if (latestTimestamp != null) {
                    currentState = loadBackupManifestProvider(backupDir, latestTimestamp, workspaceEnv)
                }
                if (currentState == null) {
                    currentState = BackupManifest(timestamp = messageTimestamp, files = emptyMap(), fileStats = emptyMap())
                }
                saveCurrentStateManifestProvider(backupDir, workspaceEnv, currentState)
            }

            val newerBackups = existingBackups.filter { it > messageTimestamp }
            if (newerBackups.isNotEmpty()) {
                val restoreTimestamp = newerBackups.first()
                AppLogger.i(TAG, "Newer backups found. Rewinding workspace to state at $restoreTimestamp")
                AppLogger.d(TAG, "[Rewind] Calculated restoreTimestamp: $restoreTimestamp")

                val targetManifest = checkNotNull(loadBackupManifestProvider(backupDir, restoreTimestamp, workspaceEnv)) {
                    "Workspace restore manifest is missing"
                }
                restoreFromManifestsProvider(
                    workspacePath = workspacePath,
                    workspaceEnv = workspaceEnv,
                    objectsDir = objectsDir,
                    currentState = currentState,
                    targetManifest = targetManifest
                )

                saveCurrentStateManifestProvider(backupDir, workspaceEnv, targetManifest)

                val backupsToDelete = newerBackups.filter { it >= restoreTimestamp }
                AppLogger.d(TAG, "[Rewind] Backups to be deleted: $backupsToDelete")
                AppLogger.d(TAG, "Deleting backups from $restoreTimestamp onwards: $backupsToDelete")
                return@withContext backupsToDelete.map { ts -> joinPath(backupDir, "$ts.json") }
            }

            val existingManifest =
                if (existingBackups.contains(messageTimestamp)) {
                    loadBackupManifestProvider(backupDir, messageTimestamp, workspaceEnv)
                } else {
                    null
                }

            if (existingManifest != null) {
                saveCurrentStateManifestProvider(backupDir, workspaceEnv, existingManifest)
                AppLogger.d(TAG, "Backup for timestamp $messageTimestamp already exists. Synced current state.")
                return@withContext emptyList()
            }

            AppLogger.i(TAG, "No newer backups found for timestamp $messageTimestamp. Recording current state snapshot.")
            writeBackupManifestProvider(
                backupDir = backupDir,
                timestamp = messageTimestamp,
                workspaceEnv = workspaceEnv,
                manifest = currentState.copy(timestamp = messageTimestamp)
            )
            emptyList()
        }
    }

    private suspend fun restoreFromManifestsProvider(
        workspacePath: String,
        workspaceEnv: String?,
        objectsDir: String,
        currentState: BackupManifest,
        targetManifest: BackupManifest,
    ) {
        restoreWorkspaceFiles(
            currentFiles = currentState.files,
            targetFiles = targetManifest.files,
            resolveObject = { hash -> resolveObjectPathForRead(objectsDir, hash, workspaceEnv) },
            writeFile = { relativePath, objectPath, hash ->
                val content = checkNotNull(readBinaryBase64(objectPath, workspaceEnv)) { "Workspace backup object is unreadable" }
                val bytes = Base64.decode(content, Base64.DEFAULT)
                val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                check(actualHash.equals(hash, ignoreCase = true)) { "Workspace backup object checksum mismatch" }
                val targetPath = joinPath(workspacePath, relativePath)
                val parent = targetPath.substringBeforeLast('/', "")
                if (parent.isNotBlank()) ensureDirectory(parent, workspaceEnv)
                writeBinaryBase64(targetPath, content, workspaceEnv)
            },
            deleteFile = { relativePath -> deleteFileProvider(joinPath(workspacePath, relativePath), workspaceEnv) },
        )
    }

    private fun normalizeTextLinesForDiff(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        return normalized.split('\n')
    }

    private fun estimateChangedLines(beforeText: String, afterText: String): Int {
        if (beforeText == afterText) return 0
        return try {
            val beforeLines = normalizeTextLinesForDiff(beforeText)
            val afterLines = normalizeTextLinesForDiff(afterText)
            val patch = DiffUtils.diff(beforeLines, afterLines)
            var changed = 0
            for (delta in patch.deltas) {
                when (delta.type) {
                    com.github.difflib.patch.DeltaType.INSERT -> changed += delta.target.lines.size
                    com.github.difflib.patch.DeltaType.DELETE -> changed += delta.source.lines.size
                    com.github.difflib.patch.DeltaType.CHANGE -> {
                        val a = delta.source.lines.size
                        val b = delta.target.lines.size
                        changed += if (a > b) a else b
                    }
                    else -> Unit
                }
            }
            changed
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to estimate changed lines", e)
            0
        }
    }

    suspend fun previewChanges(
        workspacePath: String,
        targetTimestamp: Long,
        workspaceEnv: String? = null,
        chatId: String? = null
    ): List<WorkspaceFileChange> {
        return previewChangesProvider(workspacePath, workspaceEnv, targetTimestamp, chatId)
    }

    suspend fun previewChangesForRewind(
        workspacePath: String,
        workspaceEnv: String?,
        rewindTimestamp: Long,
        chatId: String?
    ): List<WorkspaceFileChange> {
        val workspaceInfo = fileExistsProvider(workspacePath, workspaceEnv)
        check(workspaceInfo?.exists == true && workspaceInfo.isDirectory) { "Workspace is unavailable" }
        val backupRootDir = joinPath(workspacePath, BACKUP_DIR_NAME)
        val backupDir = resolveChatBackupDir(backupRootDir, chatId)
        val existingBackups = listBackupsInBackupDir(backupDir, workspaceEnv)
        val newerBackups = existingBackups.filter { it > rewindTimestamp }
        if (newerBackups.isEmpty()) return emptyList()
        val restoreTimestamp = newerBackups.first()
        return previewChangesProvider(workspacePath, workspaceEnv, restoreTimestamp, chatId)
    }

    private suspend fun loadCurrentStateForDiffProvider(
        backupDir: String,
        workspaceEnv: String?
    ): BackupManifest {
        val currentState = loadCurrentStateManifestProvider(backupDir, workspaceEnv)
        if (currentState != null) {
            return currentState
        }

        val latestTimestamp = listBackupsInBackupDir(backupDir, workspaceEnv).lastOrNull()
        if (latestTimestamp != null) {
            val latestManifest = loadBackupManifestProvider(backupDir, latestTimestamp, workspaceEnv)
            if (latestManifest != null) {
                return latestManifest
            }
        }

        return BackupManifest(
            timestamp = System.currentTimeMillis(),
            files = emptyMap(),
            fileStats = emptyMap()
        )
    }

    private suspend fun readTextFromObjectHashProvider(
        objectsDir: String,
        hash: String,
        workspaceEnv: String?
    ): String? {
        val objectPath = checkNotNull(resolveObjectPathForRead(objectsDir, hash, workspaceEnv)) { "Workspace backup object is missing" }
        val objectBase64 = checkNotNull(readBinaryBase64(objectPath, workspaceEnv)) { "Workspace backup object is unreadable" }
        return String(Base64.decode(objectBase64, Base64.DEFAULT), Charsets.UTF_8)
    }

    private suspend fun estimateLineCountFromHashProvider(
        objectsDir: String,
        hash: String,
        workspaceEnv: String?
    ): Int {
        val text = readTextFromObjectHashProvider(objectsDir, hash, workspaceEnv) ?: return 0
        return normalizeTextLinesForDiff(text).size
    }

    private suspend fun estimateChangedLinesBetweenHashesProvider(
        objectsDir: String,
        currentHash: String,
        targetHash: String,
        workspaceEnv: String?
    ): Int {
        if (currentHash == targetHash) return 0
        val currentText = readTextFromObjectHashProvider(objectsDir, currentHash, workspaceEnv) ?: return 0
        val targetText = readTextFromObjectHashProvider(objectsDir, targetHash, workspaceEnv) ?: return 0
        return estimateChangedLines(currentText, targetText)
    }

    private suspend fun previewChangesProvider(
        workspacePath: String,
        workspaceEnv: String?,
        targetTimestamp: Long,
        chatId: String?
    ): List<WorkspaceFileChange> {
        return withContext(Dispatchers.IO) {
            val exists = fileExistsProvider(workspacePath, workspaceEnv)
            if (exists == null || !exists.exists || !exists.isDirectory) {
                error("Workspace path does not exist or is not a directory")
            }

            val backupRootDir = joinPath(workspacePath, BACKUP_DIR_NAME)
            val backupDir = resolveChatBackupDir(backupRootDir, chatId)
            val objectsDir = joinPath(backupRootDir, OBJECTS_DIR_NAME)

            val currentState = loadCurrentStateForDiffProvider(backupDir, workspaceEnv)
            val targetManifest = checkNotNull(loadBackupManifestProvider(backupDir, targetTimestamp, workspaceEnv)) {
                "Workspace preview manifest is missing"
            }

            val currentFiles = currentState.files
            val targetFiles = targetManifest.files
            val changes = mutableListOf<WorkspaceFileChange>()

            for ((relativePath, currentHash) in currentFiles) {
                val targetHash = targetFiles[relativePath]
                if (targetHash == null) {
                    val deletedLines = estimateLineCountFromHashProvider(objectsDir, currentHash, workspaceEnv)
                    changes.add(WorkspaceFileChange(relativePath, ChangeType.DELETED, deletedLines))
                    continue
                }

                if (targetHash != currentHash) {
                    val changedLines =
                        estimateChangedLinesBetweenHashesProvider(
                            objectsDir = objectsDir,
                            currentHash = currentHash,
                            targetHash = targetHash,
                            workspaceEnv = workspaceEnv
                        )
                    if (changedLines > 0) {
                        changes.add(WorkspaceFileChange(relativePath, ChangeType.MODIFIED, changedLines))
                    }
                }
            }

            for ((relativePath, targetHash) in targetFiles) {
                if (currentFiles.containsKey(relativePath)) continue
                val addedLines = estimateLineCountFromHashProvider(objectsDir, targetHash, workspaceEnv)
                changes.add(WorkspaceFileChange(relativePath, ChangeType.ADDED, addedLines))
            }

            changes.sortedBy { it.path }
        }
    }
}
