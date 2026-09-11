package com.kiyori.platform.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import java.io.IOException

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

    data class Roots(val android: String, val linux: String)

    fun normalizeRoots(androidRoot: String?, linuxRoot: String?): Roots = Roots(
        ArtifactPathRules.androidRoot(androidRoot, Environment.getExternalStorageDirectory().absolutePath),
        ArtifactPathRules.linuxRoot(linuxRoot),
    )

    fun roots(context: Context): Roots {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = prefs.all
        return normalizeRoots(values[KEY_ANDROID_ROOT] as String?, values[KEY_LINUX_ROOT] as String?)
    }

    /** 消费端只解析自己使用的环境，另一端损坏的旧偏好不能阻断本端操作。 */
    fun root(context: Context, environment: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (environment) {
            "android" -> ArtifactPathRules.androidRoot(prefs.getString(KEY_ANDROID_ROOT, null), Environment.getExternalStorageDirectory().absolutePath)
            "linux" -> ArtifactPathRules.linuxRoot(prefs.getString(KEY_LINUX_ROOT, null))
            else -> throw IllegalArgumentException("Expected android or linux environment")
        }
    }

    @Synchronized
    fun setRoots(context: Context, androidRoot: String, linuxRoot: String) {
        val normalized = normalizeRoots(androidRoot, linuxRoot)
        persistRoots(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), normalized)
    }

    internal fun persistRoots(prefs: SharedPreferences, normalized: Roots) {
        val previous = prefs.all
        val saved = prefs.edit()
            .putString(KEY_ANDROID_ROOT, normalized.android)
            .putString(KEY_LINUX_ROOT, normalized.linux).commit()
        if (!saved) {
            // Android commit() 失败仍可能更新内存；必须恢复，否则工具会使用 UI 报告未保存的新目录。
            val rollback = prefs.edit()
            for (key in listOf(KEY_ANDROID_ROOT, KEY_LINUX_ROOT)) {
                if (previous.containsKey(key)) rollback.putString(key, previous[key] as String?) else rollback.remove(key)
            }
            val restored = rollback.commit()
            throw IOException(if (restored) "Unable to persist artifact storage settings; previous values restored" else "Unable to persist artifact storage settings; previous values restored in memory but disk persistence failed")
        }
    }

    fun reset(context: Context) {
        val defaults = normalizeRoots(null, null)
        setRoots(context, defaults.android, defaults.linux)
    }

    /** 仅由用户点击写入检查触发；保留新建目录，临时探测文件必须清理。 */
    fun checkAndroidWrite(path: String) {
        val root = File(ArtifactPathRules.androidRoot(path, Environment.getExternalStorageDirectory().absolutePath))
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) throw IOException("Cannot create artifact directory")
        val probe = File.createTempFile(".kiyori-write-check-", ".tmp", root)
        try {
            probe.writeText("Kiyori storage check", Charsets.UTF_8)
            check(probe.readText(Charsets.UTF_8) == "Kiyori storage check") { "Artifact write verification failed" }
        } finally {
            if (!probe.delete()) throw IOException("Cannot remove storage check file: $probe")
        }
    }

    fun androidAbsoluteRoot(context: Context): File {
        return File(root(context, "android"))
    }

    /** 设置页必须能够打开并修复旧版本存入的非法值，不能静默切换输出目标。 */
    fun savedInputs(context: Context): Roots {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Roots(prefs.getString(KEY_ANDROID_ROOT, null).orEmpty(), prefs.getString(KEY_LINUX_ROOT, null).orEmpty())
    }

    fun reserveAndroidOutput(context: Context, category: String, relativeName: String): File =
        ArtifactPathRules.reserveCategorizedFile(androidAbsoluteRoot(context), category, relativeName)

    fun prompt(context: Context, english: Boolean): String {
        val roots = try {
            roots(context)
        } catch (error: IllegalArgumentException) {
            return if (english) {
                "Artifact storage settings are invalid. Ask the user to repair AI artifact storage settings or specify an absolute destination; do not invent a default directory."
            } else {
                "产物保存设置无效。请用户在 AI 产物保存位置设置中修正，或明确指定绝对目标路径；不要自行选择默认目录。"
            }
        }
        return if (english) {
            """ARTIFACT STORAGE POLICY
- Unless the user explicitly gives another destination, every generated or exported artifact (documents, scripts, reports, archives, images, videos, downloads and scaffolding) MUST be written below Android `${roots.android}` or Ubuntu `${roots.linux}`.
- Never create project files in `/root`, `/`, the app private files directory, the terminal home directory, or an unspecified current directory. Do not use relative paths for writes.
- Create a descriptive task subdirectory, separate deliverables from temporary work, use descriptive filenames (Unicode supported) with correct extensions, and do not overwrite existing deliverables without permission.
- Before writing, state the resolved absolute destination and use the matching environment (`android` or `linux`). After writing, report the final absolute path and verify the file exists.
- A user-provided path or an explicitly selected workspace takes precedence; do not silently relocate it.
- Installed runtimes, virtual environments, package caches and ToolPkg private data retain their managed locations. Do not move dependencies into delivery directories.
- Bundled scripts read live defaults through getArtifactPath(environment). Android and Ubuntu are separate filesystems. Arbitrary shell/third-party/MCP code is not sandboxed by this policy: pass explicit output arguments. Remote SSH/MCP does not share local directories.
""".trimIndent()
        } else {
            """产物保存策略
- 除非用户明确指定其他位置，所有生成或导出的产物（文档、脚本、报告、压缩包、图片、视频、下载内容和脚手架）必须写入 Android `${roots.android}` 或 Ubuntu `${roots.linux}` 下。
- 禁止把项目文件写入 `/root`、`/`、应用私有 files 目录、终端 home 或未指定的当前目录；写入时不得使用相对路径。
- 创建有意义的任务子目录，分开交付物与过程文件；使用描述性文件名（支持中文等 Unicode）和正确扩展名，未经允许不覆盖已有交付物。
- 写入前说明解析后的绝对目标路径，并使用匹配的 `environment`（`android` 或 `linux`）；写入后返回最终绝对路径并验证文件存在。
- 用户明确给出的路径或已选择的工作区优先，不得静默改写。
- 已安装运行时、虚拟环境、依赖缓存和 ToolPkg 私有数据保持各自管理目录，不把环境依赖搬进交付目录。
- 内置脚本通过 getArtifactPath(environment) 读取当前默认值。Android 与 Ubuntu 是独立文件系统。该策略不是任意 Shell、第三方或 MCP 代码的沙箱，调用时必须传递输出参数；远端 SSH/MCP 不共享本地目录。
""".trimIndent()
        }
    }

}
