package com.ai.assistance.operit.ui.features.chat.webview.workspace

/** 目录是数据，命令是显式配置的 Shell 代码；进入目录失败必须阻止整段代码执行。 */
internal fun workspaceShellCommand(workspacePath: String, command: String, workingDir: String = ".", shell: Boolean = true): String {
    require(workspacePath.startsWith('/')) { "Workspace directory must be an absolute terminal path" }
    require('\u0000' !in workspacePath && '\u0000' !in command && '\u0000' !in workingDir) { "Workspace commands cannot contain NUL" }
    require(command.isNotBlank()) { "Workspace command is empty" }
    // 当前协议只有命令字符串，没有 argv；不能把 shell=false 静默当作 Shell 代码执行。
    require(shell) { "Workspace terminal commands require shell=true; direct argv execution is not configured" }
    require(workingDir.isNotBlank()) { "Workspace command workingDir is empty" }
    val target = when {
        workingDir == "." -> workspacePath
        workingDir.startsWith('/') -> workingDir
        else -> "${workspacePath.trimEnd('/')}/$workingDir"
    }
    val directory = "'${target.replace("'", "'\"'\"'")}'"
    // 换行避免末尾注释吞掉右括号；整个命令保留同一会话的 export/cd 等 Shell 状态。
    return "cd -- $directory && {\n$command\n}"
}
