package com.kiyori.platform.storage

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * AI 产生的可交付文件的唯一策略 owner。
 *
 * 工具描述和角色卡只能引用这里的投影，不能各自拼接路径。Android 与 Ubuntu
 * 使用不同的绝对路径，但共享同一个相对布局，便于跨环境复制和审计。
 */
object KiyoriArtifactStoragePolicy {
    private const val PREFS = "kiyori_artifact_storage"
    private const val KEY_ANDROID_ROOT = "android_root"
    private const val KEY_LINUX_ROOT = "linux_root"
    private const val DEFAULT_ANDROID_ROOT = "Download/Kiyori/workspace"
    private const val DEFAULT_LINUX_ROOT = "/workspace"

    data class Roots(val android: String, val linux: String)

    fun roots(context: Context): Roots {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val android = normalizeAndroidRoot(prefs.getString(KEY_ANDROID_ROOT, null))
        val linux = normalizeLinuxRoot(prefs.getString(KEY_LINUX_ROOT, null))
        return Roots(android = android, linux = linux)
    }

    fun setRoots(context: Context, androidRoot: String, linuxRoot: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ANDROID_ROOT, normalizeAndroidRoot(androidRoot))
            .putString(KEY_LINUX_ROOT, normalizeLinuxRoot(linuxRoot))
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ANDROID_ROOT).remove(KEY_LINUX_ROOT).apply()
    }

    fun androidAbsoluteRoot(context: Context): File {
        val relative = roots(context).android.removePrefix("Download/")
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relative)
    }

    fun prompt(context: Context, english: Boolean): String {
        val roots = roots(context)
        return if (english) {
            """ARTIFACT STORAGE POLICY (ENFORCED)
- Unless the user explicitly gives another destination, every generated or exported artifact (documents, scripts, reports, archives, images, videos, downloads and scaffolding) MUST be written below Android `${roots.android}` or Ubuntu `${roots.linux}`.
- Never create project files in `/root`, `/`, the app private files directory, the terminal home directory, or an unspecified current directory. Do not use relative paths for writes.
- Create a task-specific subdirectory using a short kebab-case name, then use descriptive lowercase names with an extension; avoid `test`, `new`, `output`, timestamps alone, or overwriting an existing file.
- Before writing, state the resolved absolute destination and use the matching environment (`android` or `linux`). After writing, report the final absolute path and verify the file exists.
- A user-provided path or an explicitly selected workspace takes precedence; do not silently relocate it.
""".trimIndent()
        } else {
            """产物保存策略（强制执行）
- 除非用户明确指定其他位置，所有生成或导出的产物（文档、脚本、报告、压缩包、图片、视频、下载内容和脚手架）必须写入 Android `${roots.android}` 或 Ubuntu `${roots.linux}` 下。
- 禁止把项目文件写入 `/root`、`/`、应用私有 files 目录、终端 home 或未指定的当前目录；写入时不得使用相对路径。
- 先创建任务专用的短横线命名子目录，再使用有描述性的英文小写文件名和正确扩展名；不要使用 `test`、`new`、`output`、仅时间戳，也不要覆盖已有文件。
- 写入前说明解析后的绝对目标路径，并使用匹配的 `environment`（`android` 或 `linux`）；写入后返回最终绝对路径并验证文件存在。
- 用户明确给出的路径或已选择的工作区优先，不得静默改写。
""".trimIndent()
        }
    }

    private fun normalizeAndroidRoot(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_ANDROID_ROOT }
        require(!raw.startsWith("/") && !raw.contains("..")) { "Android artifact root must be relative to Download" }
        return raw.trim('/').replace('\\', '/')
    }

    private fun normalizeLinuxRoot(value: String?): String {
        val raw = value?.trim().orEmpty().ifBlank { DEFAULT_LINUX_ROOT }
        require(raw.startsWith("/") && !raw.split('/').contains("..")) { "Linux artifact root must be an absolute path without .." }
        return "/" + raw.trim('/').replace('\\', '/')
    }
}
