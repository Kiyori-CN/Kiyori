package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

internal object NetworkFileSystemTools {
    fun hasNetworkEnvironment(tool: AITool): Boolean = tool.parameters.any {
        it.name in setOf("environment", "source_environment", "destination_environment", "dest_environment", "target_environment") && it.value.startsWith("network:", ignoreCase = true)
    }

    /** 在标准文件工具入口分流全部网络参数；未支持的操作必须失败，绝不访问同名 Android 路径。 */
    suspend fun executeIfNetwork(context: Context, tool: AITool): ToolResult? {
        if (!hasNetworkEnvironment(tool)) return null
        val environment = tool.parameters.firstOrNull { it.name == "environment" }?.value.orEmpty()
        val path = tool.parameters.firstOrNull { it.name == "path" }?.value ?: "/"
        fun failure(message: String) = ToolResult(tool.name, false,
            FileOperationData(tool.name, environment, path, false, message, FileCopyErrorCode.UNSUPPORTED), message)
        if (tool.name != "list_files") return failure("网络存储当前支持目录浏览；此操作尚未接入，不会修改远程或本地文件。")
        return try {
            val profile = ApiPreferences.getInstance(context).fileNetworksFlow.first().firstOrNull { it.id == environment.substringAfter(':') }
                ?: return failure("网络存储配置已移除，请重新选择位置")
            val coroutine = currentCoroutineContext()
            val deadline = System.nanoTime() + 30_000_000_000L
            val entries = NetworkDirectoryClient.list(profile, path, NetworkStorageSecret.decrypt(profile.encryptedSecret)) {
                coroutine.ensureActive()
                check(System.nanoTime() < deadline) { "网络目录读取超时" }
            }
            ToolResult(tool.name, true, DirectoryListingData(path, entries, environment))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: Exception) {
            // 服务器异常文字可能携带地址或账户；保留类型，避免输出原始请求和凭据。
            failure("连接或目录读取失败（${failure.javaClass.simpleName}）。请检查地址、凭据、证书或主机指纹。")
        }
    }
}
