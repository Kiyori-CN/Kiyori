package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

/** 一次替换只解释模板中的占位符，文件原名内的花括号不会被再次当作模板执行。 */
fun fileManagerBatchRenameName(file: FileItem, index: Int, pattern: String): String {
    val dot = file.name.lastIndexOf('.').takeIf { !file.isDirectory && it > 0 } ?: file.name.length
    return Regex("\\{(name|ext|n)\\}").replace(pattern) { match ->
        when (match.groupValues[1]) {
            "name" -> file.name.substring(0, dot)
            "ext" -> file.name.substring(dot)
            else -> (index + 1).toString()
        }
    }
}

fun fileManagerBatchRenameError(files: List<FileItem>, pattern: String): String? {
    val names = files.mapIndexed { index, file -> fileManagerBatchRenameName(file, index, pattern) }
    names.firstNotNullOfOrNull(::fileManagerNameError)?.let { return it }
    if (names.distinct().size != names.size) return "模板产生了重复名称，请加入 {n} 或保留原名"
    if (names.zip(files).all { (name, file) -> name == file.name }) return "模板没有改变任何名称"
    return null
}
