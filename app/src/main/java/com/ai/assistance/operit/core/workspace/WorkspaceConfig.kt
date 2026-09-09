package com.ai.assistance.operit.core.workspace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Stable .operit/config.json contract shared by workspace UI and ToolPkg services. */
@Serializable
data class WorkspaceConfig(
    val projectType: String = "web",
    val title: String? = null,
    val description: String? = null,
    val server: ServerConfig = ServerConfig(),
    val preview: PreviewConfig = PreviewConfig(),
    val commands: List<CommandConfig> = emptyList(),
    val export: ExportConfig = ExportConfig(),
    val watch: WatchConfig = WatchConfig(),
)

@Serializable
data class ServerConfig(
    val enabled: Boolean = false,
    val port: Int = 8093,
    val autoStart: Boolean = false,
)

@Serializable
data class PreviewConfig(
    val type: String = "browser",
    val url: String = "",
    val showPreviewButton: Boolean = false,
    val previewButtonLabel: String = "",
)

@Serializable
data class CommandConfig(
    val id: String,
    val label: String,
    val command: String? = null,
    val tool: String? = null,
    val toolParameters: Map<String, String> = emptyMap(),
    val workingDir: String = ".",
    val shell: Boolean = true,
    val usesDedicatedSession: Boolean = false,
    val sessionTitle: String? = null,
)

@Serializable
data class ExportConfig(val enabled: Boolean = true)

@Serializable
data class WatchConfig(
    val enabled: Boolean = true,
    val maxDepth: Int = 3,
    val maxChangedFiles: Int = 80,
    val exclude: List<String> = listOf(".git", ".operit", ".backup", "backup"),
)

object WorkspaceConfigReader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun readConfig(workspacePath: String): WorkspaceConfig {
        val root = File(workspacePath)
        require(root.isDirectory) { "Workspace directory is unavailable" }
        val configFile = File(root, ".operit/config.json")
        val content = try {
            java.nio.file.Files.newBufferedReader(configFile.toPath(), Charsets.UTF_8).use { it.readText() }
        } catch (missing: java.nio.file.NoSuchFileException) {
            val parent = requireNotNull(configFile.parentFile).toPath()
            if (java.nio.file.Files.exists(configFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
                (java.nio.file.Files.exists(parent, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !java.nio.file.Files.isDirectory(parent))) throw missing
            return defaultWebConfig()
        }
        return parseConfig(content)
    }

    fun parseConfig(content: String): WorkspaceConfig = try {
        json.decodeFromString<WorkspaceConfig>(content)
    } catch (error: kotlinx.serialization.SerializationException) {
        // JSON 解析异常可能夹带命令/环境变量正文；错误可见，但不能带入持久日志。
        throw java.io.IOException("Invalid workspace configuration (${error.javaClass.simpleName})")
    }

    fun hasConfig(workspacePath: String): Boolean =
        File(workspacePath, ".operit/config.json").exists()

    fun defaultWebConfig(): WorkspaceConfig =
        WorkspaceConfig(
            server = ServerConfig(enabled = true, port = 8093, autoStart = true),
            preview = PreviewConfig(type = "browser", url = "http://localhost:8093"),
        )
}
