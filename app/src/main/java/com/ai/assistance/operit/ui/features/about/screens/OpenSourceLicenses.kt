package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.ai.assistance.operit.R

internal const val KIYORI_OPEN_SOURCE_INVENTORY_VERSION = "2026-08-28-r1"

internal data class OpenSourceLibrary(
    val name: String,
    val description: String = "",
    val license: String = "",
    val website: String = "",
    val licenseUrl: String = openSourceLicenseUrl(license),
    val ffmpegKitSourceId: String? = null,
)

private fun openSourceLicenseUrl(license: String): String =
    when {
        license.contains("AGPL-3", ignoreCase = true) ->
            "https://www.gnu.org/licenses/agpl-3.0.html"
        license.contains("LGPL-3", ignoreCase = true) ->
            "https://www.gnu.org/licenses/lgpl-3.0.html"
        license.contains("LGPL-2.1", ignoreCase = true) ->
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"
        license.contains("LGPL-2.0", ignoreCase = true) ->
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html"
        license.contains("GPL-3", ignoreCase = true) ->
            "https://www.gnu.org/licenses/gpl-3.0.html"
        license.contains("GPL-2", ignoreCase = true) ->
            "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
        license.contains("Apache-2.0", ignoreCase = true) ->
            "https://www.apache.org/licenses/LICENSE-2.0"
        license.contains("libpng-2.0", ignoreCase = true) ->
            "https://opensource.org/license/libpng-2-0/"
        license.contains("ISC", ignoreCase = true) ->
            "https://opensource.org/license/isc-license-txt/"
        license.contains("BSD-3", ignoreCase = true) ->
            "https://opensource.org/license/bsd-3-clause/"
        license.contains("BSD-2", ignoreCase = true) ->
            "https://opensource.org/license/bsd-2-clause/"
        license.contains("WTFPL", ignoreCase = true) ->
            "https://www.wtfpl.net/about/"
        license.contains("Zlib", ignoreCase = true) ->
            "https://opensource.org/license/zlib/"
        license.contains("Unlicense", ignoreCase = true) ->
            "https://unlicense.org/"
        license.contains("JSON license", ignoreCase = true) ->
            "https://github.com/stleary/JSON-java/blob/master/LICENSE"
        license.contains("MPL", ignoreCase = true) ->
            "https://www.mozilla.org/en-US/MPL/2.0/"
        license.contains("MIT", ignoreCase = true) ->
            "https://opensource.org/license/mit/"
        else -> error("Unsupported open-source license mapping: $license")
    }

internal fun getOpenSourceLibraries(): List<OpenSourceLibrary> {
    return listOf(
        // UI & Android Framework
        OpenSourceLibrary("android-gif-drawable", "Android GIF 动图解码与显示", "MIT", "https://github.com/koral--/android-gif-drawable"),
        OpenSourceLibrary("Android-Image-Cropper", "Android 图片裁剪组件", "Apache-2.0", "https://github.com/CanHub/Android-Image-Cropper"),
        OpenSourceLibrary("AndroidSVG", "SVG 矢量图解析与渲染", "Apache-2.0", "https://github.com/BigBadaboom/androidsvg"),
        OpenSourceLibrary("AndroidX Compose", "Android 声明式界面工具包", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
        OpenSourceLibrary("AndroidX Core KTX", "Android 核心 API 的 Kotlin 扩展", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("AndroidX AppCompat", "兼容旧版 Android 的基础组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/appcompat"),
        OpenSourceLibrary("AndroidX DataStore", "键值与偏好数据存储组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
        OpenSourceLibrary("AndroidX Lifecycle", "生命周期与协程感知组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
        OpenSourceLibrary("AndroidX Navigation", "Compose 页面导航组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
        OpenSourceLibrary("AndroidX Security Crypto", "Android 加密存储组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/security"),
        OpenSourceLibrary("AndroidX Window", "窗口与折叠屏适配组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/window"),
        OpenSourceLibrary("AndroidX WorkManager", "后台任务调度组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/work"),
        OpenSourceLibrary("AndroidX Glance", "Compose 小组件开发组件", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/glance"),
        OpenSourceLibrary("AndroidX Media3", "媒体播放基础组件", "Apache-2.0", "https://github.com/androidx/media"),
        OpenSourceLibrary("AndroidX WebKit", "Android WebView 能力与兼容性 API", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/webkit"),
        OpenSourceLibrary("Material Components for Android", "Android Material Design 组件", "Apache-2.0", "https://github.com/material-components/material-components-android"),
        OpenSourceLibrary("Kotlin", "Kotlin 编程语言与标准库", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
        OpenSourceLibrary("Android desugar library", "为旧版 Android 提供现代 Java API", "Apache-2.0", "https://github.com/google/desugar_jdk_libs"),
        OpenSourceLibrary("Accompanist", "Jetpack Compose 辅助组件集合", "Apache-2.0", "https://github.com/google/accompanist"),
        OpenSourceLibrary("colorpicker-compose", "Jetpack Compose 颜色选择器", "Apache-2.0", "https://github.com/skydoves/colorpicker-compose"),
        OpenSourceLibrary("Reorderable", "Compose 可拖拽排序列表", "Apache-2.0", "https://github.com/Calvin-LL/Reorderable"),
        OpenSourceLibrary("Swipe", "Compose 滑动揭示操作组件", "Apache-2.0", "https://github.com/saket/swipe"),

        // File & Archive Processing
        OpenSourceLibrary("Apache Commons Compress", "归档文件压缩与解压缩", "Apache-2.0", "https://commons.apache.org/proper/commons-compress/"),
        OpenSourceLibrary("Apache Commons IO", "Java 输入输出工具集", "Apache-2.0", "https://commons.apache.org/proper/commons-io/"),
        OpenSourceLibrary("junrar", "RAR 压缩包解压", "The Unlicense", "https://github.com/junrar/junrar"),
        OpenSourceLibrary("ZIP4J", "Java ZIP 压缩包处理", "Apache-2.0", "https://github.com/srikanth-lingala/zip4j"),

        // Document Processing
        OpenSourceLibrary("Apache PDFBox", "PDF 文档解析与渲染", "Apache-2.0", "https://pdfbox.apache.org/"),
        OpenSourceLibrary("Apache POI", "Excel、Word、PowerPoint 文档处理", "Apache-2.0", "https://poi.apache.org/"),
        OpenSourceLibrary("iText (v5)", "PDF 创建与处理组件；使用 iText 5 时适用 AGPL 或商业许可", "AGPL-3.0（或商业许可）", "https://github.com/itext/itextpdf"),
        OpenSourceLibrary("XMLBeans", "Apache POI 使用的 XML 绑定与 OOXML 类型库", "Apache-2.0", "https://xmlbeans.apache.org/"),
        OpenSourceLibrary("CurvesAPI", "Apache POI 使用的曲线计算库", "Apache-2.0", "https://github.com/virtuald/curvesapi"),

        // APK Tools
        OpenSourceLibrary("apk-parser", "APK 文件解析", "Apache-2.0", "https://github.com/hsiafan/apk-parser"),
        OpenSourceLibrary("apksig", "APK 签名校验工具", "Apache-2.0", "https://developer.android.com/tools/apksigner"),
        OpenSourceLibrary("axml", "Android 二进制 XML 解析", "Apache-2.0", "https://github.com/Sable/axml"),
        OpenSourceLibrary("zipalign-java", "Java 实现的 ZIP 对齐工具", "MIT", "https://github.com/Iyxan23/zipalign-java"),

        // Image Processing
        OpenSourceLibrary("Coil", "Android 图片加载与缓存", "Apache-2.0", "https://coil-kt.github.io/coil/"),
        OpenSourceLibrary("Glide", "Android 图片加载与变换", "BSD, MIT, Apache-2.0", "https://github.com/bumptech/glide"),

        // Multimedia
        OpenSourceLibrary("ExoPlayer", "Android 可扩展媒体播放组件", "Apache-2.0", "https://exoplayer.dev/"),
        OpenSourceLibrary(
            "FFmpeg",
            "播放器与媒体工具使用的音视频编解码库；当前随包构建启用了 GPL 组件",
            "LGPL-2.1-or-later / GPL-2.0-or-later（当前构建按 GPL-3.0-or-later 分发）",
            "https://github.com/FFmpeg/FFmpeg",
            licenseUrl = "https://github.com/FFmpeg/FFmpeg/blob/master/COPYING.GPLv3",
            ffmpegKitSourceId = "ffmpeg",
        ),
        OpenSourceLibrary("FFmpegKit Maintained", "Android FFmpeg 工具包", "LGPL-3.0", "https://github.com/ffmpegkit-maintained/ffmpeg"),
        OpenSourceLibrary("Smart Exception (FFmpegKit)", "FFmpegKit 用于跨语言传递异常和堆栈的运行时组件", "LGPL-3.0", "https://github.com/ffmpegkit-maintained/ffmpeg"),
        OpenSourceLibrary(
            "LLVM libc++",
            "Kiyori 播放器使用的 C++ 运行库",
            "Apache-2.0 WITH LLVM-exception",
            "https://github.com/llvm/llvm-project/tree/main/libcxx",
            licenseUrl = "https://github.com/llvm/llvm-project/blob/main/libcxx/LICENSE.TXT",
        ),
        OpenSourceLibrary("libplacebo", "mpv 播放器使用的视频渲染库", "LGPL-2.1-or-later", "https://github.com/haasn/libplacebo"),
        OpenSourceLibrary("shaderc", "播放器 Vulkan/GLSL 着色器编译工具链", "Apache-2.0", "https://github.com/google/shaderc"),
        OpenSourceLibrary("mpv", "Kiyori 视频播放器使用的媒体内核", "GPL-2.0-or-later", "https://github.com/mpv-player/mpv"),
        OpenSourceLibrary("mpvlibAndroid", "Android JNI 绑定与 libmpv 构建", "GPL-2.0-or-later", "https://github.com/Riteshp2001/mpvlibAndroid"),
        OpenSourceLibrary("Mbed TLS", "播放器 FFmpeg 构建静态链接的 TLS 后端", "Apache-2.0 OR GPL-2.0-or-later", "https://github.com/Mbed-TLS/mbedtls"),
        OpenSourceLibrary("Anime4K", "实时动漫视频超分着色器", "MIT", "https://github.com/bloc97/Anime4K"),

        // FFmpegKit M9 source lock and bundled license closure
        OpenSourceLibrary("cpu-features", "FFmpegKit 与 libvpx 使用的 Android CPU 能力检测库", "Apache-2.0", "https://github.com/arthenica/cpu_features", ffmpegKitSourceId = "cpu-features"),
        OpenSourceLibrary("dav1d", "FFmpegKit 使用的 AV1 视频解码器", "BSD-2-Clause", "https://github.com/arthenica/dav1d", ffmpegKitSourceId = "dav1d"),
        OpenSourceLibrary("Expat", "Fontconfig 与 FFmpegKit 使用的 XML 解析库", "MIT", "https://github.com/arthenica/libexpat", ffmpegKitSourceId = "expat"),
        OpenSourceLibrary(
            "Fontconfig",
            "FFmpegKit 字幕与文字渲染使用的字体发现和配置库",
            "MIT-style / MIT / 公共领域 / Unicode 条款",
            "https://github.com/arthenica/fontconfig",
            licenseUrl = "https://github.com/arthenica/fontconfig/blob/7861a719616b4b132b9cac089c6c64f47832edb1/COPYING",
            ffmpegKitSourceId = "fontconfig",
        ),
        OpenSourceLibrary(
            "FreeType",
            "FFmpegKit 和播放器字幕使用的字体栅格化引擎",
            "FreeType License OR GPL-2.0-or-later",
            "https://github.com/arthenica/freetype2",
            licenseUrl = "https://github.com/arthenica/freetype2/blob/de8b92dd7ec634e9e2b25ef534c54a3537555c11/LICENSE.TXT",
            ffmpegKitSourceId = "freetype",
        ),
        OpenSourceLibrary("FriBidi", "FFmpegKit 字幕使用的双向文字排版库", "LGPL-2.1-or-later", "https://github.com/arthenica/fribidi", ffmpegKitSourceId = "fribidi"),
        OpenSourceLibrary("HarfBuzz", "FFmpegKit 字幕使用的 OpenType 文字塑形引擎", "MIT", "https://github.com/arthenica/harfbuzz", ffmpegKitSourceId = "harfbuzz"),
        OpenSourceLibrary("Kvazaar", "FFmpegKit 使用的 HEVC 视频编码器", "BSD-3-Clause", "https://github.com/arthenica/kvazaar", ffmpegKitSourceId = "kvazaar"),
        OpenSourceLibrary("LAME", "FFmpegKit 使用的 MP3 音频编码器", "LGPL-2.0-or-later", "https://github.com/arthenica/lame", ffmpegKitSourceId = "lame"),
        OpenSourceLibrary("libaom", "FFmpegKit 使用的 AV1 视频编解码库", "BSD-2-Clause", "https://github.com/arthenica/libaom", ffmpegKitSourceId = "libaom"),
        OpenSourceLibrary("libass", "FFmpegKit 与播放器使用的 ASS/SSA 字幕渲染器", "ISC", "https://github.com/arthenica/libass", ffmpegKitSourceId = "libass"),
        OpenSourceLibrary("libiconv", "FFmpegKit 使用的字符编码转换库", "LGPL-2.1-or-later", "https://github.com/arthenica/libiconv", ffmpegKitSourceId = "libiconv"),
        OpenSourceLibrary("libilbc", "FFmpegKit 使用的 iLBC 语音编解码库", "BSD-3-Clause", "https://github.com/arthenica/libilbc", ffmpegKitSourceId = "libilbc"),
        OpenSourceLibrary("libogg", "FFmpegKit 使用的 Ogg 媒体封装库", "BSD-3-Clause", "https://github.com/arthenica/ogg", ffmpegKitSourceId = "libogg"),
        OpenSourceLibrary("libpng", "FFmpegKit 字幕和图像处理使用的 PNG 编解码库", "libpng-2.0", "https://github.com/arthenica/libpng", ffmpegKitSourceId = "libpng"),
        OpenSourceLibrary("libsndfile", "FFmpegKit 使用的音频文件读写库", "LGPL-2.1-or-later", "https://github.com/arthenica/libsndfile", ffmpegKitSourceId = "libsndfile"),
        OpenSourceLibrary("libtheora", "FFmpegKit 使用的 Theora 视频编解码库", "BSD-3-Clause", "https://github.com/arthenica/theora", ffmpegKitSourceId = "libtheora"),
        OpenSourceLibrary("libuuid", "FFmpegKit 使用的 UUID 生成与解析库", "BSD-3-Clause", "https://github.com/arthenica/libuuid", ffmpegKitSourceId = "libuuid"),
        OpenSourceLibrary("libvorbis", "FFmpegKit 使用的 Vorbis 音频编解码库", "BSD-3-Clause", "https://github.com/arthenica/vorbis", ffmpegKitSourceId = "libvorbis"),
        OpenSourceLibrary("libvpx", "FFmpegKit 使用的 VP8/VP9 视频编解码库", "BSD-3-Clause", "https://github.com/arthenica/libvpx", ffmpegKitSourceId = "libvpx"),
        OpenSourceLibrary("libxml2", "FFmpegKit 使用的 XML 解析库", "MIT", "https://github.com/arthenica/libxml2", ffmpegKitSourceId = "libxml2"),
        OpenSourceLibrary("OpenCORE AMR", "FFmpegKit 使用的 AMR-NB 语音编解码库", "Apache-2.0", "https://github.com/arthenica/opencore-amr", ffmpegKitSourceId = "opencore-amr"),
        OpenSourceLibrary("Opus", "FFmpegKit 使用的 Opus 音频编解码库", "BSD-3-Clause", "https://github.com/arthenica/opus", ffmpegKitSourceId = "opus"),
        OpenSourceLibrary("SDL2", "FFmpegKit 命令行工具使用的音视频设备抽象库", "Zlib", "https://github.com/arthenica/SDL", ffmpegKitSourceId = "sdl"),
        OpenSourceLibrary("Shine", "FFmpegKit 使用的定点 MP3 音频编码器", "LGPL-2.0-or-later", "https://github.com/arthenica/shine", ffmpegKitSourceId = "shine"),
        OpenSourceLibrary("Snappy", "FFmpegKit 使用的快速压缩库", "BSD-3-Clause", "https://github.com/arthenica/snappy", ffmpegKitSourceId = "snappy"),
        OpenSourceLibrary("SoX Resampler", "FFmpegKit 使用的高质量音频重采样库", "LGPL-2.1-or-later", "https://github.com/arthenica/soxr", ffmpegKitSourceId = "soxr"),
        OpenSourceLibrary("Speex", "FFmpegKit 使用的语音编解码库", "BSD-3-Clause", "https://github.com/arthenica/speex", ffmpegKitSourceId = "speex"),
        OpenSourceLibrary("TwoLAME", "FFmpegKit 使用的 MPEG Audio Layer II 编码器", "LGPL-2.1-or-later", "https://github.com/arthenica/twolame", ffmpegKitSourceId = "twolame"),
        OpenSourceLibrary("VisualOn AMR-WB", "FFmpegKit 使用的 AMR-WB 音频编码器", "Apache-2.0", "https://github.com/arthenica/vo-amrwbenc", ffmpegKitSourceId = "vo-amrwbenc"),
        OpenSourceLibrary("zimg", "FFmpegKit 使用的图像缩放与色彩空间转换库", "WTFPL-2.0", "https://github.com/arthenica/zimg", ffmpegKitSourceId = "zimg"),

        // Additional mpv native closure components
        OpenSourceLibrary("libunibreak", "播放器字幕换行与 Unicode 断行库", "Zlib", "https://github.com/adah1972/libunibreak"),
        OpenSourceLibrary("Lua", "mpv 脚本与播放器运行时使用的嵌入式脚本语言", "MIT", "https://www.lua.org/"),
        OpenSourceLibrary("MuJS", "mpv 使用的轻量 JavaScript 引擎", "ISC", "https://mujs.com/"),

        // AI & Machine Learning
        OpenSourceLibrary("MNN", "阿里巴巴轻量级深度学习推理引擎", "Apache-2.0", "https://github.com/alibaba/MNN"),
        OpenSourceLibrary("KleidiAI", "MNN ARM CPU 内核优化组件", "Apache-2.0", "https://github.com/ARM-software/kleidiai"),
        OpenSourceLibrary("ONNX Runtime", "跨平台机器学习推理引擎", "MIT", "https://github.com/microsoft/onnxruntime"),

        // NLP & Search
        OpenSourceLibrary("HNSWLib", "近似最近邻向量检索", "Apache-2.0", "https://github.com/jelmerk/hnswlib"),
        OpenSourceLibrary("Jieba-Android", "Android 中文分词", "MIT", "https://github.com/huaban/jieba-analysis"),
        OpenSourceLibrary("ZXing", "二维码与条形码编解码", "Apache-2.0", "https://github.com/zxing/zxing"),

        // Networking
        OpenSourceLibrary("Apache FTPServer", "FTP 服务端组件", "Apache-2.0", "https://mina.apache.org/ftpserver-project/"),
        OpenSourceLibrary("Apache SSHD", "SSH 服务端与客户端组件", "Apache-2.0", "https://mina.apache.org/sshd-project/"),
        OpenSourceLibrary("Apache MINA", "FTPServer 与 SSHD 使用的网络传输基础库", "Apache-2.0", "https://mina.apache.org/"),
        OpenSourceLibrary("JSch", "Java SSH 客户端", "BSD-3-Clause", "https://github.com/mwiede/jsch"),
        OpenSourceLibrary("Jsoup", "Java HTML 解析器", "MIT", "https://jsoup.org/"),
        OpenSourceLibrary("Ktor", "异步网络开发框架", "Apache-2.0", "https://ktor.io/"),
        OpenSourceLibrary("MCP SDK", "Model Context Protocol Kotlin SDK", "MIT", "https://github.com/modelcontextprotocol/kotlin-sdk"),
        OpenSourceLibrary("Mihomo", "传统脚本包和应用代理使用的 v1.19.30 运行时", "GPL-3.0", "https://github.com/MetaCubeX/mihomo"),
        OpenSourceLibrary("NanoHTTPD", "轻量级 HTTP 服务端", "BSD-3-Clause", "https://github.com/NanoHttpd/nanohttpd"),
        OpenSourceLibrary("OkHttp", "HTTP 客户端", "Apache-2.0", "https://square.github.io/okhttp/"),
        OpenSourceLibrary("Retrofit", "类型安全的 HTTP 客户端", "Apache-2.0", "https://square.github.io/retrofit/"),

        // Data & Serialization
        OpenSourceLibrary("Gson", "Google JSON 解析库", "Apache-2.0", "https://github.com/google/gson"),
        OpenSourceLibrary("HJSON", "易读 JSON 格式解析器", "MIT", "https://hjson.github.io/"),
        OpenSourceLibrary("kotlin-uuid", "Kotlin UUID 工具库", "Apache-2.0", "https://github.com/benasher44/uuid"),
        OpenSourceLibrary("kotlinx.serialization", "Kotlin 序列化库", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        OpenSourceLibrary("Moshi", "Kotlin/Java JSON 解析库", "Apache-2.0", "https://github.com/square/moshi"),
        OpenSourceLibrary("SnakeYAML Engine", "Mihomo 订阅清洗使用的安全 YAML 解析器", "Apache-2.0", "https://bitbucket.org/snakeyaml/snakeyaml-engine/"),

        // Database
        OpenSourceLibrary("ObjectBox", "高性能 NoSQL 数据库运行时与代码生成器", "Apache-2.0（运行时）/ AGPL-3.0（生成器）", "https://github.com/objectbox/objectbox-java"),
        OpenSourceLibrary("Room", "Android SQLite ORM 数据库组件", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),

        // LaTeX & Math Rendering
        OpenSourceLibrary("JLatexMath-Android", "Android LaTeX 公式渲染", "GPL-2.0 with Classpath Exception", "https://github.com/noties/jlatexmath-android"),
        OpenSourceLibrary("KaTeX", "LaTeX 到 MathML 转换（用于文本公式导出）", "MIT", "https://github.com/KaTeX/KaTeX"),
        OpenSourceLibrary("RenderX", "LaTeX 公式渲染组件", "MIT", "https://github.com/tech-pw/RenderX"),

        // Security & Crypto
        OpenSourceLibrary("Bouncy Castle", "Java 加密与证书组件", "MIT", "https://www.bouncycastle.org/"),

        // System & Root
        OpenSourceLibrary("libsu", "Android Root 访问组件", "Apache-2.0", "https://github.com/topjohnwu/libsu"),
        OpenSourceLibrary("Shizuku", "让应用安全调用系统 API 的服务组件", "Apache-2.0", "https://github.com/RikkaApps/Shizuku"),
        OpenSourceLibrary("Tasker Plugin Library", "Tasker 插件开发组件", "Apache-2.0", "https://github.com/joaomgcd/TaskerPluginLibrary"),

        // Terminal & Native
        OpenSourceLibrary("DragonBones", "2D 骨骼动画库", "MIT", "https://github.com/DragonBones/DragonBonesCPP"),
        OpenSourceLibrary("Filament", "实时 3D 渲染引擎", "Apache-2.0", "https://github.com/google/filament"),
        OpenSourceLibrary("GLM", "Saba 使用的 C++ 数学库", "MIT", "https://github.com/g-truc/glm"),
        OpenSourceLibrary("llama.cpp", "端侧大语言模型推理运行时", "MIT", "https://github.com/ggml-org/llama.cpp"),
        OpenSourceLibrary("Saba", "MMD 模型解析与渲染组件", "MIT", "https://github.com/benikabocha/saba"),
        OpenSourceLibrary("spdlog", "Saba 使用的 C++ 日志库", "MIT", "https://github.com/gabime/spdlog"),
        OpenSourceLibrary("stb", "Saba 使用的单头文件图像与资源工具集", "MIT / 公共领域", "https://github.com/nothings/stb"),
        OpenSourceLibrary("tinyddsloader", "Saba 使用的 DDS 纹理加载器", "MIT", "https://github.com/benikabocha/tinyddsloader"),
        OpenSourceLibrary("ufbx", "轻量 FBX 文件解析库", "MIT", "https://github.com/ufbx/ufbx"),
        OpenSourceLibrary("QuickJS", "嵌入式 JavaScript 引擎", "MIT", "https://bellard.org/quickjs/"),
        OpenSourceLibrary("Bullet Physics", "物理模拟库（随 MMD 模块使用）", "Zlib", "https://github.com/bulletphysics/bullet3"),
        OpenSourceLibrary("FlatBuffers", "高性能序列化库（随 MNN 使用）", "Apache-2.0", "https://github.com/google/flatbuffers"),
        OpenSourceLibrary("RapidJSON", "高性能 JSON 解析库（随 MNN 使用）", "MIT", "https://github.com/Tencent/rapidjson"),
        OpenSourceLibrary(
            "OpenH264",
            "FFmpeg 播放构建使用的视频编码器",
            "BSD-2-Clause",
            "https://github.com/arthenica/openh264",
            ffmpegKitSourceId = "openh264",
        ),
        OpenSourceLibrary("ncnn", "sherpa-ncnn 使用的端侧神经网络推理库", "BSD-3-Clause", "https://github.com/Tencent/ncnn"),
        OpenSourceLibrary("OpenFST", "sherpa-ncnn 使用的有限状态转换器库", "Apache-2.0", "https://github.com/csukuangfj/openfst"),
        OpenSourceLibrary("Kaldi Native Fbank", "sherpa-ncnn 使用的音频特征提取库", "Apache-2.0", "https://github.com/csukuangfj/kaldi-native-fbank"),
        OpenSourceLibrary("kaldifst", "sherpa-ncnn 使用的 Kaldi FST 封装", "Apache-2.0", "https://github.com/csukuangfj/kaldifst"),
        OpenSourceLibrary("Wasm Micro Runtime", "ToolPkg WebAssembly 沙箱运行时", "Apache-2.0", "https://github.com/bytecodealliance/wasm-micro-runtime"),
        OpenSourceLibrary("zlib", "压缩与解压缩基础库", "Zlib", "https://github.com/madler/zlib"),
        OpenSourceLibrary("KiyoriTerminalCore", "Kiyori 终端子模块及其运行时组件", "LGPL-3.0-or-later", "https://github.com/Kiyori-CN/KiyoriTerminalCore"),
        OpenSourceLibrary("Kiyori", "Kiyori 应用源码与产品层", "GPL-3.0-or-later", "https://github.com/Kiyori-CN/Kiyori"),
        OpenSourceLibrary(
            "GNU config",
            "FFmpegKit 原生构建使用的平台识别脚本",
            "GPL-3.0-or-later（配置脚本例外）",
            "https://github.com/arthenica/gnu-config",
            licenseUrl = "https://git.savannah.gnu.org/cgit/config.git/tree/COPYING",
        ),

        // Kotlin & Logging
        OpenSourceLibrary("java-diff-utils", "Java 文本差异计算", "Apache-2.0", "https://github.com/java-diff-utils/java-diff-utils"),
        OpenSourceLibrary("Kotlin Coroutines", "Kotlin 协程库", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceLibrary("kotlin-logging", "Kotlin 日志门面", "Apache-2.0", "https://github.com/oshai/kotlin-logging"),
        OpenSourceLibrary("Backdrop", "Compose 背景与模糊效果组件", "Apache-2.0", "https://github.com/kyant0/backdrop"),
        OpenSourceLibrary("Liquid", "Android 液态视觉效果组件", "Apache-2.0", "https://github.com/fletchmckee/liquid"),
        OpenSourceLibrary("sherpa-ncnn", "基于 Next-gen Kaldi 的实时语音识别", "Apache-2.0", "https://github.com/k2-fsa/sherpa-ncnn"),
        OpenSourceLibrary("sherpa-mnn", "使用 MNN 后端的语音识别", "Apache-2.0", "https://github.com/k2-fsa/sherpa-mnn"),
        OpenSourceLibrary("SLF4J", "Java 简单日志门面", "MIT", "https://www.slf4j.org/"),
        OpenSourceLibrary("org.json", "Android 与测试使用的 JSON 数据结构实现", "JSON license", "https://github.com/stleary/JSON-java")
    ).sortedBy { it.name }
}

@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val libraries = remember { getOpenSourceLibraries() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.open_source_licenses)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(libraries) { library ->
                    ListItem(
                        headlineContent = { Text(library.name, fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            Column {
                                if (library.description.isNotEmpty()) {
                                    Text(
                                        text = library.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = stringResource(id = R.string.license_format, library.license),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, library.licenseUrl.toUri()).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Policy,
                                        contentDescription = "查看许可证正文",
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, library.website.toUri()).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = stringResource(id = R.string.visit_project),
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(id = R.string.ok))
            }
        }
    )
}
