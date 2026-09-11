package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Policy

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.main.shell.KiyoriCollapsingSettingsPage
import com.ai.assistance.operit.ui.main.shell.KiyoriSettingsGroupSection

internal const val KIYORI_OPEN_SOURCE_LICENSES_PAGE_TITLE = "开源协议"

internal val openSourceCategoryOrder =
    listOf("界面与 Android", "媒体与图形", "AI 与本地推理", "网络与数据", "文档与文件", "开发工具")

@Composable
internal fun KiyoriOpenSourceLicensesPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val libraries = remember { getOpenSourceLibraries() }
    var query by remember { mutableStateOf("") }
    val filteredLibraries =
        remember(libraries, query) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) {
                libraries
            } else {
                libraries.filter { library ->
                    library.name.contains(normalizedQuery, ignoreCase = true) ||
                        library.description.contains(normalizedQuery, ignoreCase = true) ||
                        library.license.contains(normalizedQuery, ignoreCase = true) ||
                        library.website.contains(normalizedQuery, ignoreCase = true) ||
                        library.licenseUrl.contains(normalizedQuery, ignoreCase = true)
                }
            }
        }

    fun openProject(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun openLicense(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    KiyoriCollapsingSettingsPage(
        title = KIYORI_OPEN_SOURCE_LICENSES_PAGE_TITLE,
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Kiyori 开源组成",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text =
                        "本清单覆盖当前可由 Gradle、源码模块、native source lock、随包许可证或运行时装载证据确认的开源组件。" +
                            "具体权利义务以组件随附的许可证正文为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                Text(
                    text =
                        "当前清单 ${filteredLibraries.size} / ${libraries.size} 项 · " +
                            "清单基线 $KIYORI_OPEN_SOURCE_INVENTORY_VERSION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    placeholder = { Text("搜索组件、许可证或项目地址") },
                )
            }
        }
        openSourceCategoryOrder.forEach { category ->
            val categoryLibraries = filteredLibraries.filter { library ->
                openSourceLibraryCategory(library) == category
            }
            if (categoryLibraries.isNotEmpty()) {
                item {
                    KiyoriSettingsGroupSection(
                        title = category,
                        description = openSourceCategoryDescription(category),
                    ) {
                        categoryLibraries.forEachIndexed { index, library ->
                                OpenSourceLicenseRow(
                                    library = library,
                                    onOpenProject = { openProject(library.website) },
                                    onOpenLicense = { openLicense(library.licenseUrl) },
                                )
                            if (index != categoryLibraries.lastIndex) {
                                androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
        if (filteredLibraries.isEmpty()) {
            item {
                Text(
                    text = "没有匹配的开源组件",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OpenSourceLicenseRow(
    library: OpenSourceLibrary,
    onOpenProject: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = openSourceLibrarySummary(library),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "许可证：${library.license}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "项目地址：${library.website}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenProject),
                )
            }
            Row {
                IconButton(onClick = onOpenLicense) {
                    Icon(
                        imageVector = Icons.Default.Policy,
                        contentDescription = "查看许可证正文",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onOpenProject) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "打开项目主页",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun openSourceCategoryDescription(category: String): String =
    when (category) {
        "界面与 Android" -> "Compose、AndroidX 与应用界面基础组件"
        "媒体与图形" -> "视频播放、图像、3D 与实时渲染组件"
        "AI 与本地推理" -> "文字识别、语音识别和端侧模型运行时"
        "网络与数据" -> "网络连接、代理、序列化、数据库和安全组件"
        "文档与文件" -> "文档、压缩包、PDF 与 APK 处理组件"
        "开发工具" -> "脚本引擎、终端、日志与开发辅助组件"
        else -> "随 Kiyori 使用的开源组件"
    }

internal fun openSourceLibraryCategory(library: OpenSourceLibrary): String =
    when {
        library.name in setOf(
            "AndroidX Compose", "AndroidX Core KTX", "AndroidX AppCompat", "AndroidX DataStore", "AndroidX Security Crypto",
            "AndroidX Window", "AndroidX WorkManager", "AndroidX Glance", "AndroidX Media3", "AndroidX WebKit",
            "AndroidX Lifecycle", "AndroidX Navigation", "Material Components for Android", "Accompanist", "colorpicker-compose", "Reorderable", "Swipe", "Backdrop", "Liquid",
            "Kotlin", "Coil", "Glide", "AndroidSVG", "android-gif-drawable", "Android-Image-Cropper",
        ) -> "界面与 Android"
        library.name in setOf(
            "ExoPlayer", "FFmpeg", "FFmpegKit Maintained", "Smart Exception (FFmpegKit)", "LLVM libc++", "libplacebo", "shaderc",
            "mpv", "mpvlibAndroid", "mpv-android-anime4k", "Mbed TLS", "Anime4K", "Filament", "Saba", "GLM", "spdlog", "stb", "tinyddsloader", "ufbx", "DragonBones", "Bullet Physics",
            "cpu-features", "dav1d", "Expat", "Fontconfig", "FreeType", "FriBidi", "HarfBuzz", "Kvazaar", "LAME", "libaom", "libass", "libiconv",
            "libilbc", "libogg", "libpng", "libsndfile", "libtheora", "libuuid", "libvorbis", "libvpx", "libxml2", "OpenCORE AMR", "OpenH264", "Opus",
            "SDL2", "Shine", "Snappy", "SoX Resampler", "Speex", "TwoLAME", "VisualOn AMR-WB", "zimg", "libunibreak", "Lua", "MuJS",
        ) -> "媒体与图形"
        library.name in setOf(
            "ML Kit", "MNN", "KleidiAI", "ONNX Runtime", "llama.cpp", "sherpa-ncnn", "sherpa-mnn",
        "ncnn", "OpenFST", "Kaldi Native Fbank", "kaldifst",
        ) -> "AI 与本地推理"
        library.name in setOf(
            "Apache FTPServer", "Apache SSHD", "Apache MINA", "JSch", "Jsoup", "Ktor", "MCP SDK", "Mihomo", "NanoHTTPD",
            "OkHttp", "Retrofit", "Gson", "HJSON", "kotlin-uuid", "kotlinx.serialization", "Moshi",
            "SnakeYAML Engine", "ObjectBox", "Room", "Bouncy Castle", "Kotlin Coroutines", "SLF4J", "kotlin-logging", "ZXing",
        ) -> "网络与数据"
        library.name in setOf(
            "Apache Commons Compress", "Apache Commons IO", "junrar", "ZIP4J", "Apache PDFBox", "Apache POI",
            "iText (v5)", "XMLBeans", "CurvesAPI", "apk-parser", "apksig", "axml", "zipalign-java", "FlatBuffers", "RapidJSON", "java-diff-utils", "Android desugar library",
        ) -> "文档与文件"
        else -> "开发工具"
    }

private fun openSourceLibrarySummary(library: OpenSourceLibrary): String =
    when (library.name) {
        "mpv", "mpvlibAndroid" -> "Kiyori 视频播放器使用的媒体内核与 Android JNI 构建"
        "mpv-android-anime4k" -> "Android mpv 播放器的 Anime4K 集成"
        "Operit" -> "Kiyori 继续开发所基于的上游应用源码"
        "hikerView" -> "浏览器规则解析、插件与播放器参考项目"
        "OperitTerminal" -> "Android Ubuntu 终端与 Operit 的集成项目"
        "FFmpegKit Maintained" -> "播放器与媒体工具使用的 FFmpegKit Android 构建"
        "FFmpeg" -> "播放器与媒体工具使用的音视频编解码库"
        "Smart Exception (FFmpegKit)" -> "FFmpegKit 的异常与堆栈跨语言传递组件"
        "Mihomo" -> "网络代理运行时，用于订阅解析和模块路由"
        "llama.cpp", "MNN", "ONNX Runtime" -> "端侧模型推理与本地 AI 能力"
        "AndroidX Compose", "AndroidX Media3", "AndroidX WebKit" -> "Android 原生界面、媒体与 WebView 基础设施"
        "Saba", "ufbx", "Filament", "GLM", "spdlog", "stb", "tinyddsloader" -> "模型解析、资源加载与 3D 渲染能力"
        "ncnn", "OpenFST", "Kaldi Native Fbank", "kaldifst", "sherpa-ncnn", "sherpa-mnn" -> "本地语音识别与音频特征处理"
        "Wasm Micro Runtime" -> "ToolPkg WebAssembly 沙箱运行时"
        else -> library.description
    }
