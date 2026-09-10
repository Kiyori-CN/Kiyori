package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/** 原子创建一个空项目；不预检查 exists，不创建父目录，不修改已存在的文件或链接。 */
internal fun createLocalNoReplace(path: Path, directory: Boolean) {
    require(path.isAbsolute) { "新建位置必须为绝对路径" }
    if (directory) Files.createDirectory(path) else Files.createFile(path)
}

internal fun executeNoReplaceCreateTool(tool: AITool, directory: Boolean, backendEnvironment: String = "android"): ToolResult {
    fun parameter(name: String) = tool.parameters.find { it.name == name }?.value
    val path = parameter("path").orEmpty()
    val environment = parameter("environment") ?: backendEnvironment
    fun result(success: Boolean, message: String, code: String? = null) = ToolResult(
        tool.name, success,
        FileOperationData(if (directory) "mkdir" else "create", environment, path, success, message, errorCode = code),
        message.takeUnless { success },
    )
    val textCopy = parameter("create_mode") == "no_replace_text" && !directory
    if ((parameter("create_mode") != "no_replace" && !textCopy) || backendEnvironment != "android" ||
        !environment.equals("android", true) || path.startsWith("content:", true)) {
        return result(false, "此位置尚不支持不覆盖新建", FileCopyErrorCode.UNSUPPORTED)
    }
    if ((!textCopy && !parameter("new").isNullOrEmpty()) || parameter("create_parents") == "true") {
        return result(false, "此模式仅创建当前目录中的空白文件或文件夹", FileCopyErrorCode.UNSUPPORTED)
    }
    PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
    return try {
        if (textCopy) saveLocalTextCopy(java.io.File(path).toPath(), parameter("new").orEmpty(), NativeNoReplaceCommit::commit)
        else createLocalNoReplace(java.io.File(path).toPath(), directory)
        result(true, "创建完成")
    } catch (e: LocalCopyException) {
        ToolResult(tool.name, false, FileOperationData("create", environment, path, false,
            e.message.orEmpty(), errorCode = e.code, stagingPath = e.stagingPath), e.message)
    } catch (e: FileAlreadyExistsException) {
        result(false, "此名称已存在，请使用其他名称", FileCopyErrorCode.CONFLICT)
    } catch (e: Exception) {
        result(false, "创建失败（${e.javaClass.simpleName}）", FileCopyErrorCode.FAILED)
    }
}
