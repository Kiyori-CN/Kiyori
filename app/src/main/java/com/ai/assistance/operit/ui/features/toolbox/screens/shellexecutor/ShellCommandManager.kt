package com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Shell命令管理器 - 负责命令执行、历史记录管理等
 */
class ShellCommandManager(private val context: Context) {
    
    /**
     * 获取预设命令列表
     */
    fun getPresetCommands(): List<PresetCommand> {
        return listOf(
            PresetCommand(
                name = context.getString(R.string.shell_cmd_test),
                command = context.getString(R.string.shell_cmd_test_command),
                description = context.getString(R.string.shell_cmd_test_desc),
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Check
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_sys_info),
                command = context.getString(R.string.shell_cmd_sys_info_cmd),
                description = context.getString(R.string.shell_cmd_sys_info_desc),
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Info
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_disk),
                command = context.getString(R.string.shell_cmd_disk_cmd),
                description = context.getString(R.string.shell_cmd_disk_desc),
                category = CommandCategory.SYSTEM,
                icon = Icons.Default.Storage
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_memory),
                command = context.getString(R.string.shell_cmd_memory_cmd),
                description = context.getString(R.string.shell_cmd_memory_desc),
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.Memory
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_cpu),
                command = context.getString(R.string.shell_cmd_cpu_cmd),
                description = context.getString(R.string.shell_cmd_cpu_desc),
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.SettingsApplications
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_network),
                command = context.getString(R.string.shell_cmd_network_cmd),
                description = context.getString(R.string.shell_cmd_network_desc),
                category = CommandCategory.NETWORK,
                icon = Icons.Default.Wifi
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_route),
                command = context.getString(R.string.shell_cmd_route_cmd),
                description = context.getString(R.string.shell_cmd_route_desc),
                category = CommandCategory.NETWORK,
                icon = Icons.Default.Router
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_connection),
                command = context.getString(R.string.shell_cmd_connection_cmd),
                description = context.getString(R.string.shell_cmd_connection_desc),
                category = CommandCategory.NETWORK,
                icon = Icons.Default.NetworkCheck
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_installed_apps),
                command = context.getString(R.string.shell_cmd_installed_apps_cmd),
                description = context.getString(R.string.shell_cmd_installed_apps_desc),
                category = CommandCategory.PACKAGE,
                icon = Icons.Default.Apps
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_system_apps),
                command = context.getString(R.string.shell_cmd_system_apps_cmd),
                description = context.getString(R.string.shell_cmd_system_apps_desc),
                category = CommandCategory.PACKAGE,
                icon = Icons.Default.Android
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_3rd_apps),
                command = context.getString(R.string.shell_cmd_3rd_apps_cmd),
                description = context.getString(R.string.shell_cmd_3rd_apps_desc),
                category = CommandCategory.PACKAGE,
                icon = Icons.Default.AppShortcut
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_current_dir),
                command = context.getString(R.string.shell_cmd_current_dir_cmd),
                description = context.getString(R.string.shell_cmd_current_dir_desc),
                category = CommandCategory.FILE,
                icon = Icons.Default.Folder
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_root_dir),
                command = context.getString(R.string.shell_cmd_root_dir_cmd),
                description = context.getString(R.string.shell_cmd_root_dir_desc),
                category = CommandCategory.FILE,
                icon = Icons.Default.FolderOpen
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_storage),
                command = context.getString(R.string.shell_cmd_storage_cmd),
                description = context.getString(R.string.shell_cmd_storage_desc),
                category = CommandCategory.FILE,
                icon = Icons.Default.SdCard
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_process),
                command = context.getString(R.string.shell_cmd_process_cmd),
                description = context.getString(R.string.shell_cmd_process_desc),
                category = CommandCategory.SYSTEM,
                icon = Icons.AutoMirrored.Filled.ViewList
            ),
            PresetCommand(
                name = context.getString(R.string.shell_cmd_properties),
                command = context.getString(R.string.shell_cmd_properties_cmd),
                description = context.getString(R.string.shell_cmd_properties_desc),
                category = CommandCategory.SYSTEM,
                icon = Icons.Outlined.Settings
            )
        )
    }
    
    /**
     * 执行Shell命令
     */
    suspend fun executeCommand(command: String): CommandRecord {
        val result = withContext(Dispatchers.IO) {
            AndroidShellExecutor.executeShellCommand(command)
        }
        
        val record = CommandRecord(
            command = command,
            result = result,
            timestamp = System.currentTimeMillis()
        )
        
        return record
    }
    
}
