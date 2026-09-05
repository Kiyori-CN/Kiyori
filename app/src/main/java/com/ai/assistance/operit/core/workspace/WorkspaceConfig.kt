package com.ai.assistance.operit.core.workspace

import com.ai.assistance.operit.util.AppLogger
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
    private const val TAG = "WorkspaceConfigReader"
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun readConfig(workspacePath: String): WorkspaceConfig {
        val configFile = File(workspacePath, ".operit/config.json")
        if (!configFile.exists()) {
            AppLogger.d(TAG, "Config file not found at ${configFile.absolutePath}, using default")
            return defaultWebConfig()
        }
        return try {
            json.decodeFromString<WorkspaceConfig>(configFile.readText())
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to parse config file: ${error.message}", error)
            defaultWebConfig()
        }
    }

    fun hasConfig(workspacePath: String): Boolean =
        File(workspacePath, ".operit/config.json").exists()

    private fun defaultWebConfig(): WorkspaceConfig =
        WorkspaceConfig(
            server = ServerConfig(enabled = true, port = 8093, autoStart = true),
            preview = PreviewConfig(type = "browser", url = "http://localhost:8093"),
        )
}
