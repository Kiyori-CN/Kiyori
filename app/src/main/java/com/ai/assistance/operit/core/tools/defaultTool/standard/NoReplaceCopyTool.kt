package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Path

/** 显式严格模式共用入口，所有后端先识别参数，不能悄悄进入各自的覆盖实现。 */
internal suspend fun executeNoReplaceCopyTool(
    tool: AITool,
    backendEnvironment: String = "android",
    commit: (Path, Path) -> Unit = { source, destination -> NativeNoReplaceCommit.commit(source, destination) },
): ToolResult {
    fun parameter(name: String) = tool.parameters.find { it.name == name }?.value
    val source = parameter("source").orEmpty()
    val destination = parameter("destination").orEmpty()
    val environment = parameter("environment") ?: backendEnvironment
    val sourceEnvironment = parameter("source_environment") ?: environment
    val destinationEnvironment = parameter("dest_environment") ?: environment
    fun result(success: Boolean, message: String, code: String? = null, staging: String? = null) = ToolResult(
        toolName = tool.name,
        success = success,
        result = FileOperationData("copy", environment, source, success, message,
            errorCode = code, destination = destination, stagingPath = staging),
        error = message.takeUnless { success },
    )
    if (parameter("copy_mode") != "no_replace") {
        return result(false, "不支持的复制模式", FileCopyErrorCode.UNSUPPORTED)
    }
    if (backendEnvironment != "android" || !sourceEnvironment.equals("android", true) ||
        !destinationEnvironment.equals("android", true) ||
        source.startsWith("content:", true) || destination.startsWith("content:", true)) {
        return result(false, "此位置尚不支持不覆盖复制，请使用手机中应用可访问的目录", FileCopyErrorCode.UNSUPPORTED)
    }
    if (source.isBlank() || destination.isBlank() || !File(source).isAbsolute || !File(destination).isAbsolute) {
        return result(false, "复制需要有效的源和目标绝对路径", FileCopyErrorCode.FAILED)
    }
    PathValidator.validateAndroidPath(source, tool.name, "source")?.let { return it }
    PathValidator.validateAndroidPath(destination, tool.name, "destination")?.let { return it }
    return try {
        val coroutineContext = currentCoroutineContext()
        val receipt = copyLocalNoReplace(File(source).toPath(), File(destination).toPath(),
            parameter("recursive")?.toBoolean() ?: true, commit,
            checkActive = { coroutineContext.ensureActive() })
        result(true, receipt.cleanupWarning ?: "复制完成", staging = receipt.stagingPath)
    } catch (e: CancellationException) {
        throw e
    } catch (e: LocalCopyException) {
        result(false, e.message ?: "复制失败", e.code, e.stagingPath)
    } catch (e: Exception) {
        // 原始异常可能含私人路径；位置已在结构化结果中，通用错误只展示类型。
        result(false, "复制失败（${e.javaClass.simpleName}），源项目保留", FileCopyErrorCode.FAILED)
    }
}
