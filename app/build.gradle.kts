import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.parcelize)
    id("io.objectbox")
}

@CacheableTask
abstract class GenerateBundledToolPkgAssetsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val whitelistFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val examplesDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val examplesRoot = examplesDirectory.get().asFile.canonicalFile
        val outputRoot = outputDirectory.get().asFile
        val outputPackages = outputRoot.resolve("packages")
        check(!outputRoot.exists() || outputRoot.deleteRecursively()) {
            "Unable to clear generated ToolPkg assets directory: $outputRoot"
        }
        check(outputPackages.mkdirs() || outputPackages.isDirectory) {
            "Unable to create generated ToolPkg assets directory: $outputPackages"
        }

        val items =
            whitelistFile.get().asFile.readLines(Charsets.UTF_8)
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }

        val outputNames = mutableSetOf<String>()
        items.forEach { item ->
            val normalized = item.replace('\\', '/').trim('/')
            check(normalized.isNotEmpty() && !normalized.startsWith("/") && ".." !in normalized.split('/')) {
                "Bundled package path escapes examples/: $item"
            }
            val stem =
                when {
                    normalized.endsWith(".toolpkg", ignoreCase = true) -> normalized.dropLast(8)
                    normalized.endsWith(".js", ignoreCase = true) -> normalized.dropLast(3)
                    else -> normalized
                }.trimEnd('/')
            var unresolvedPackageRoot = examplesRoot
            stem.split('/').forEach { segment ->
                unresolvedPackageRoot = unresolvedPackageRoot.resolve(segment)
                check(!Files.isSymbolicLink(unresolvedPackageRoot.toPath())) {
                    "Bundled ToolPkg source path contains a symbolic link: $unresolvedPackageRoot"
                }
            }
            val packageRoot = unresolvedPackageRoot.canonicalFile
            check(packageRoot.toPath().startsWith(examplesRoot.toPath())) {
                "Bundled ToolPkg path escapes examples/: $item"
            }

            val manifest =
                listOf(packageRoot.resolve("manifest.hjson"), packageRoot.resolve("manifest.json"))
                    .firstOrNull(File::isFile)
            if (manifest == null) {
                check(normalized.endsWith(".js", ignoreCase = true)) {
                    "Bundled ToolPkg has no manifest: $item"
                }
                return@forEach
            }

            check(packageRoot.isDirectory) {
                "Bundled ToolPkg source is not a regular directory: $packageRoot"
            }
            check(
                packageRoot.resolve("dist/main.js").isFile ||
                    packageRoot.resolve("main.js").isFile
            ) {
                "Bundled ToolPkg has no built main runtime: $packageRoot"
            }

            val files = mutableListOf<File>()
            fun collect(path: File) {
                check(!Files.isSymbolicLink(path.toPath())) {
                    "Bundled ToolPkg cannot contain symbolic links: $path"
                }
                if (path.isDirectory) {
                    path.listFiles().orEmpty().sortedBy(File::getName).forEach(::collect)
                } else if (path.isFile) {
                    files += path
                }
            }

            collect(manifest)
            listOf("dist", "resources", "modules", "assets")
                .map(packageRoot::resolve)
                .filter(File::exists)
                .forEach(::collect)
            packageRoot.resolve("main.js").takeIf(File::isFile)?.let(::collect)

            val outputFile = outputPackages.resolve("${packageRoot.name}.toolpkg")
            check(outputNames.add(outputFile.name)) {
                "Bundled ToolPkg output name is duplicated: ${outputFile.name}"
            }
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { archive ->
                files
                    .distinctBy { it.canonicalPath }
                    .sortedBy { it.relativeTo(packageRoot).invariantSeparatorsPath }
                    .forEach { source ->
                        val entry = ZipEntry(source.relativeTo(packageRoot).invariantSeparatorsPath)
                        entry.time = 0L
                        archive.putNextEntry(entry)
                        source.inputStream().use { input -> input.copyTo(archive) }
                        archive.closeEntry()
                    }
            }
        }
    }
}

val generateBundledToolPkgAssets =
    tasks.register<GenerateBundledToolPkgAssetsTask>("generateBundledToolPkgAssets") {
        description = "Generates the production ToolPkg assets bundled with every Android variant."
        whitelistFile.set(rootProject.layout.projectDirectory.file("tools/example_packages/packages_whitelist.txt"))
        examplesDirectory.set(rootProject.layout.projectDirectory.dir("examples"))
        outputDirectory.set(layout.buildDirectory.dir("generated/bundledToolPkgAssets"))
    }

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.ai.assistance.operit"
    compileSdk = 36
    ndkVersion = providers.gradleProperty("kiyori.android.ndkVersion").get()

    signingConfigs {
        val releaseKeystorePath = localProperties.getProperty("RELEASE_STORE_FILE")
        val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
        val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")

        if (releaseKeystorePath != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null &&
            File(releaseKeystorePath).exists()
        ) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.kiyori"
        minSdk = 26
        targetSdk = 34
        versionCode = 45
        versionName = "0.1.0"

        // Marketplace ranges describe the inherited Operit runtime contract, not Kiyori's product version.
        buildConfigField("String", "OPERIT_MARKET_COMPAT_VERSION", "\"1.12.0+4\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            // Explicitly specify the ABIs we package for the app process.
            // terminal now also ships x86_64 runtime binaries for the Android Studio emulator,
            // while the rest of the app remains primarily ARM-focused.
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
            }
        }

        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${localProperties.getProperty("GITHUB_CLIENT_ID")}\"")
        buildConfigField("String", "GITHUB_CLIENT_SECRET", "\"${localProperties.getProperty("GITHUB_CLIENT_SECRET")}\"")
    }

    buildTypes {
        val releaseSigningConfig = signingConfigs.findByName("release")

        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
        }
        debug {
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
        }
        create("clone") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".clone"
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Kiyori")
        }
        create("nightly") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
        // The clone build type defines app_name through resValue; AGP 9 requires
        // this generated-resource feature to be declared explicitly.
        resValues = true
    }
    lint {
        baseline = file("lint-baseline.xml")
        checkDependencies = true
    }

    packaging {
        
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE-EPL-1.0.txt"
            excludes += "LICENSE-EPL-1.0.txt"
            excludes += "/META-INF/LICENSE-EDL-1.0.txt"
            excludes += "LICENSE-EDL-1.0.txt"
            
            // Resolve merge conflicts for document libraries
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/module-info.class"
            
            // Fix for duplicate Netty files
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            
            // Fix for any other potential duplicate files
            pickFirsts += "**/*.so"
        }
    }
//    aaptOptions {
//        noCompress += "tflite"
//    }
}

androidComponents {
    // AGP 9 removes applicationVariants and its internal output types. Keeping the
    // filenames on the public Variant API prevents the nightly/clone artifact contract
    // from depending on an implementation class that no longer exists.
    onVariants { variant ->
        val assets =
            requireNotNull(variant.sources.assets) {
                "Android variant ${variant.name} does not expose an assets source directory."
            }
        assets.addGeneratedSourceDirectory(generateBundledToolPkgAssets) {
            it.outputDirectory
        }
        val outputFileName =
            when (variant.buildType) {
                "nightly" -> "app-nightly.apk"
                "clone" -> "app-clone.apk"
                else -> null
            }
        if (outputFileName != null) {
            variant.outputs.forEach { output ->
                output.outputFileName.set(outputFileName)
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.github.jelmerk:hnswlib-core:1.2.1")
    implementation(project(":dragonbones"))
    implementation(project(":terminal"))
    implementation(project(":mnn"))
    implementation(project(":llama"))
    implementation(project(":mmd"))
    implementation(project(":fbx"))
    implementation(project(":showerclient"))
    implementation(project(":quickjs"))

    // glTF runtime rendering (Filament)
    implementation("com.google.android.filament:filament-android:1.69.2")
    implementation("com.google.android.filament:gltfio-android:1.69.2")
    implementation("com.google.android.filament:filament-utils-android:1.69.2")
    implementation(libs.androidx.ui.graphics.android)
    // Vendored binary dependencies live in app/libs, including ffmpeg-kit and its Java-side deps.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.androidx.animation.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.activity.ktx)

    // Desugaring support for modern Java APIs on older Android
    coreLibraryDesugaring(libs.desugar.jdk)

    // ML Kit - 文本识别
    implementation(libs.mlkit.text.recognition)
    // ML Kit - 多语言识别支持
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.korean)
    implementation(libs.mlkit.text.devanagari)
    
    implementation(libs.zxing.core)
    
    // diff
    implementation(libs.java.diff.utils)
    
    // APK解析和修改库
    implementation(libs.android.apksig) // APK签名工具
    implementation(libs.apk.parser) // 用于解析和处理AndroidManifest.xml
    implementation(libs.sable.axml) // 用于Android二进制XML的读写
    implementation(libs.zipalign.java) // 用于处理ZIP文件对齐
    
    // ZIP处理库 - 用于APK解压和重打包
    implementation(libs.commons.compress)
    implementation(libs.commons.io) // 添加Apache Commons IO
    
    // 图片处理库
    implementation(libs.glide) // 用于处理图像
    
    // XML处理
    implementation(libs.androidx.core.ktx)
    
    // libsu - root access library
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.github.topjohnwu.libsu:nio:6.0.0")
    
    // Add missing SVG support
    implementation(libs.androidsvg)
    
    // Add missing GIF support for Markwon
    implementation(libs.android.gif)
    
    // Image Cropper for background image cropping
    implementation(libs.image.cropper)
    
    // ExoPlayer for video background
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    
    // Material 3 Window Size Class
    implementation(libs.material3.window)
    
    // Window metrics library for foldables and adaptive layouts
    implementation(libs.window)
    implementation(libs.androidx.webkit)

    // Document conversion libraries
    implementation(libs.itextg)
    implementation(libs.pdfbox)
    implementation(libs.zip4j)
    
    // 图片加载库
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    
    // LaTeX rendering libraries
    implementation(libs.jlatexmath)
    implementation(libs.renderx) // RenderX library for LaTeX rendering
    
    // Base Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.parcelize.runtime)
    
    // UUID dependencies
    implementation(libs.uuid)
    
    // Gson for JSON parsing
    implementation(libs.gson)

    // HJSON dependency for human-friendly JSON parsing
    implementation(libs.hjson)

    // 中文分词库 - Jieba Android
    implementation(libs.jieba)

    // 向量搜索库 - 轻量级实现，适合Android
    implementation(libs.hnswlib.core)
    implementation(libs.hnswlib.utils)
    
    implementation(libs.mediapipe.tasks.text)
    
    // ONNX Runtime for Android - 支持更强大的多语言Embedding模型
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx) // Kotlin扩展和协程支持
    kapt(libs.room.compiler) // 使用kapt代替ksp

    // ObjectBox
    implementation(libs.objectbox.kotlin)
    kapt(libs.objectbox.processor)
    implementation(libs.commons.compress.v2)
    implementation(libs.junrar)

    // Compose dependencies - use BOM for version consistency
    implementation(enforcedPlatform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    // Use BOM version for all Compose dependencies
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)

    // Navigation Compose
    implementation(libs.navigation.compose)

    // Shizuku dependencies
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Tasker Plugin Library
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")
    
    // WorkManager for scheduled workflows
    implementation(libs.work.runtime.ktx)

    // Network dependencies
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.jsoup)

    // DataStore dependencies
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.core)

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(enforcedPlatform(libs.compose.bom))

    // Apache POI - for Document processing (DOC, DOCX, etc.)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.scratchpad)

    // Kotlin logging
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // Color picker for theme customization
    implementation(libs.colorpicker)
    implementation(libs.backdrop) {
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.androidx.lifecycle")
        exclude(group = "org.jetbrains.androidx.savedstate")
    }
    implementation(libs.liquid) {
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.androidx.lifecycle")
        exclude(group = "org.jetbrains.androidx.savedstate")
    }
    
    // NanoHTTPD for local web server
    implementation(libs.nanohttpd)

    // 添加测试依赖
    testImplementation(libs.junit)
    
    // Android测试依赖
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(enforcedPlatform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    
    // 协程测试依赖
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.coroutines.test)
    
    // 模拟测试框架 - 保留现有的 mockito 并新增 mockk
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.mockito.android)
    
    // // 新增的测试依赖 - mockk 和 kotlin-test
    // testImplementation(libs.mockk)
    // testImplementation(libs.ktor.server.test.host)
    // testImplementation(libs.kotlinx.coroutines.debug)
    // androidTestImplementation(libs.mockk)
    
    implementation(libs.reorderable) {
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.androidx.lifecycle")
        exclude(group = "org.jetbrains.androidx.savedstate")
    }

    // Swipe to reveal actions
    implementation(libs.swipe)

    // Coroutine
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation("io.modelcontextprotocol.sdk:mcp:1.1.0")
    
    // Exclude bcprov-jdk15to18 from all configurations to avoid duplicate classes
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // BouncyCastle - explicitly include jdk18on version to avoid conflicts
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")


    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // Glance for Widgets (Compose for Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
