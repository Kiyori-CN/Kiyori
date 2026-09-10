package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** 仅同一目录重命名；没有 rename 失败后复制/删源的分支。 */
internal fun renameLocalNoReplace(source: Path, destination: Path, commit: (Path, Path) -> Unit) =
    moveLocalNoReplace(source, destination, false, commit)

internal fun moveLocalNoReplace(source: Path, destination: Path, allowDifferentParent: Boolean = true, commit: (Path, Path) -> Unit) {
    val from = source.toAbsolutePath().normalize()
    val to = destination.toAbsolutePath().normalize()
    val parent = from.parent?.toRealPath() ?: throw IOException("源目录不可用")
    val targetParent = to.parent?.toRealPath() ?: throw IOException("目标目录不可用")
    require(allowDifferentParent || targetParent == parent) { "此模式只支持同一目录重命名" }
    val sourcePath = parent.resolve(from.fileName)
    val destinationPath = targetParent.resolve(to.fileName)
    require(!destinationPath.startsWith(sourcePath)) { "目标不能是源项目或其子目录" }
    require(sourcePath != destinationPath) { "请输入不同的名称" }
    val attributes = Files.readAttributes(sourcePath, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    if (!attributes.isRegularFile && !attributes.isDirectory) throw IOException("不支持重命名链接或特殊文件")
    if (Files.exists(destinationPath, NOFOLLOW_LINKS)) throw LocalCopyException(FileCopyErrorCode.CONFLICT, "目标名称已存在")
    commit(sourcePath, destinationPath)
}

internal fun executeNoReplaceRenameTool(tool: AITool, backendEnvironment: String = "android"): ToolResult {
    fun parameter(name: String) = tool.parameters.find { it.name == name }?.value
    val source = parameter("source").orEmpty()
    val destination = parameter("destination").orEmpty()
    val environment = parameter("environment") ?: backendEnvironment
    fun result(success: Boolean, message: String, code: String? = null) = ToolResult(
        tool.name, success,
        FileOperationData("rename", environment, source, success, message,
            errorCode = code, destination = destination),
        message.takeUnless { success },
    )
    val move = parameter("move_mode") == "move_no_replace"
    if ((parameter("move_mode") != "rename_no_replace" && !move) || backendEnvironment != "android" ||
        !environment.equals("android", true) ||
        parameter("source_environment")?.equals("android", true) == false ||
        parameter("dest_environment")?.equals("android", true) == false ||
        source.startsWith("content:", true) || destination.startsWith("content:", true)) {
        return result(false, "此位置尚不支持不覆盖重命名", FileCopyErrorCode.UNSUPPORTED)
    }
    PathValidator.validateAndroidPath(source, tool.name, "source")?.let { return it }
    PathValidator.validateAndroidPath(destination, tool.name, "destination")?.let { return it }
    return try {
        moveLocalNoReplace(java.io.File(source).toPath(), java.io.File(destination).toPath(), move, NativeNoReplaceCommit::commit)
        result(true, if (move) "移动完成" else "重命名完成")
    } catch (e: LinkageError) {
        result(false, "原子文件提交组件不可用", FileCopyErrorCode.UNSUPPORTED)
    } catch (e: LocalCopyException) {
        result(false, e.message ?: "重命名失败", e.code)
    } catch (e: Exception) {
        result(false, "重命名失败（${e.javaClass.simpleName}）", FileCopyErrorCode.FAILED)
    }
}
