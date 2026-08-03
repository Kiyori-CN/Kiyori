import com.android.build.api.artifact.SingleArtifact
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.parcelize)
    id("io.objectbox")
}

val playerFfmpegSourceCoordinate =
    "dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7"
val playerFfmpegArm64Sha256 =
    "1a30a94226bf2157927ec6edbb20154f9a1c1c53580f59cf55efe46db87a5ab3"
val playerMpvThinSha256 =
    "fc983b7ed0c8b8be1938283fe94108dfdc593aa31608d55dd1ce119ae201c32c"
val playerMpvFfmpegNamespace =
    linkedMapOf(
        "libavcodec.so" to "libmpcodec.so",
        "libavdevice.so" to "libmpdevice.so",
        "libavfilter.so" to "libmpfilter.so",
        "libavformat.so" to "libmpformat.so",
        "libavutil.so" to "libmputil.so",
        "libswresample.so" to "libmpresample.so",
        "libswscale.so" to "libmpscale.so",
    )
val playerMpvRequiredTlsMarkers =
    listOf(
        "--enable-mbedtls",
        "mbedtls_ssl_handshake",
    )
val playerFfmpegKitLibraryNames =
    setOf(
        "libavcodec.so",
        "libavdevice.so",
        "libavfilter.so",
        "libavformat.so",
        "libavutil.so",
        "libffmpegkit.so",
        "libffmpegkit_abidetect.so",
        "libswresample.so",
        "libswscale.so",
    )
val playerMpvNativeLibraryNames =
    setOf(
        "libc++_shared.so",
        "libmpv.so",
        "libplayer.so",
        *playerMpvFfmpegNamespace.values.toTypedArray(),
    )
val playerRequiredLibcxxSymbols =
    listOf(
        "_ZNSt6__ndk127__from_chars_floating_pointIfEENS_19__from_chars_resultIT_EEPKcS5_NS_12chars_formatE",
        "_ZNSt6__ndk127__from_chars_floating_pointIdEENS_19__from_chars_resultIT_EEPKcS5_NS_12chars_formatE",
    )

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { stream ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun InputStream.containsByteSequence(needle: ByteArray): Boolean {
    require(needle.isNotEmpty()) { "Needle must not be empty" }
    var matched = 0
    while (true) {
        val value = read()
        if (value < 0) return false
        val byte = value.toByte()
        matched =
            when {
                byte == needle[matched] -> matched + 1
                byte == needle[0] -> 1
                else -> 0
            }
        if (matched == needle.size) return true
    }
}

private fun ByteArray.containsByteSequence(needle: ByteArray): Boolean {
    require(needle.isNotEmpty()) { "Needle must not be empty" }
    if (needle.size > size) return false
    for (start in 0..size - needle.size) {
        var matches = true
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return false
}

private fun Project.registerSanitizedDependencyJar(
    taskName: String,
    inputConfiguration: Configuration,
    outputFileName: String,
    excludedPrefixes: Set<String>,
    expectedUnsafeEntries: Set<String>,
): TaskProvider<Zip> =
    inputConfiguration.elements.map { elements -> elements.single().asFile }.let {
        inputJarProvider ->
        tasks.register<Zip>(taskName) {
            group = "build setup"
            description =
                "Removes a closed, unused insecure capability from a fixed upstream dependency JAR."
            inputs.file(inputJarProvider)
            archiveFileName.set(outputFileName)
            destinationDirectory.set(layout.buildDirectory.dir("generated/sanitized-dependencies"))
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true

            doFirst {
                val inputJar = inputJarProvider.get()
                ZipFile(inputJar).use { archive ->
                    val entries =
                        archive.entries().asSequence().filterNot { it.isDirectory }.toList()
                    val entryNames = entries.mapTo(mutableSetOf()) { it.name }
                    val missingUnsafeEntries = expectedUnsafeEntries - entryNames
                    check(missingUnsafeEntries.isEmpty()) {
                        "Sanitizer input changed; expected insecure entries are missing from " +
                            "${inputJar.name}: ${missingUnsafeEntries.sorted()}"
                    }

                    val excludedPrefixBytes =
                        excludedPrefixes.map { prefix -> prefix.toByteArray(Charsets.UTF_8) }
                    val unexpectedReferences = mutableListOf<String>()
                    for (entry in entries) {
                        if (!entry.name.endsWith(".class")) {
                            continue
                        }
                        if (
                            entry.name == "module-info.class" ||
                                entry.name.endsWith("/module-info.class")
                        ) {
                            continue
                        }
                        var excluded = false
                        for (prefix in excludedPrefixes) {
                            if (entry.name.startsWith(prefix)) {
                                excluded = true
                                break
                            }
                        }
                        if (excluded) {
                            continue
                        }
                        val bytecode = archive.getInputStream(entry).use { it.readBytes() }
                        var referencesExcludedCapability = false
                        for (prefixBytes in excludedPrefixBytes) {
                            if (bytecode.containsByteSequence(prefixBytes)) {
                                referencesExcludedCapability = true
                                break
                            }
                        }
                        if (referencesExcludedCapability) {
                            unexpectedReferences += entry.name
                        }
                    }
                    check(unexpectedReferences.isEmpty()) {
                        "Cannot remove insecure dependency capability because retained classes " +
                            "reference it: ${unexpectedReferences.joinToString()}"
                    }
                }
            }

            from(inputJarProvider.map { inputJar -> zipTree(inputJar) }) {
                excludedPrefixes.forEach { prefix -> exclude("$prefix**") }
                exclude(
                    "module-info.class",
                    "META-INF/versions/*/module-info.class",
                    "META-INF/*.SF",
                    "META-INF/*.RSA",
                    "META-INF/*.DSA",
                    "META-INF/*.EC",
                )
            }

            doLast {
                val outputJar = archiveFile.get().asFile
                ZipFile(outputJar).use { archive ->
                    val residualEntries =
                        archive.entries().asSequence()
                            .map { it.name }
                            .filter { name ->
                                excludedPrefixes.any { prefix -> name.startsWith(prefix) }
                            }
                            .toList()
                    check(residualEntries.isEmpty()) {
                        "Sanitized dependency still contains excluded entries: " +
                            residualEntries.joinToString()
                    }
                }
            }
        }
}

@DisableCachingByDefault(
    because = "This task verifies the merged Debug Manifest and has no generated output."
)
abstract class VerifySingleDebugLauncherTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val document =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(mergedManifest.get().asFile)
        val launchableComponents = mutableListOf<String>()
        val allActivityNames = mutableListOf<String>()

        listOf("activity", "activity-alias").forEach { componentTag ->
            val components = document.getElementsByTagName(componentTag)
            for (componentIndex in 0 until components.length) {
                val component = components.item(componentIndex) as Element
                val componentName = component.getAttributeNS(androidNamespace, "name")
                allActivityNames += componentName
                val children = component.childNodes
                for (childIndex in 0 until children.length) {
                    val intentFilter = children.item(childIndex) as? Element ?: continue
                    if (intentFilter.tagName != "intent-filter") continue
                    val actions =
                        intentFilter.getElementsByTagName("action").let { nodes ->
                            buildSet {
                                for (index in 0 until nodes.length) {
                                    add(
                                        (nodes.item(index) as Element)
                                            .getAttributeNS(androidNamespace, "name")
                                    )
                                }
                            }
                        }
                    val categories =
                        intentFilter.getElementsByTagName("category").let { nodes ->
                            buildSet {
                                for (index in 0 until nodes.length) {
                                    add(
                                        (nodes.item(index) as Element)
                                            .getAttributeNS(androidNamespace, "name")
                                    )
                                }
                            }
                        }
                    if (
                        "android.intent.action.MAIN" in actions &&
                            "android.intent.category.LAUNCHER" in categories
                    ) {
                        launchableComponents += componentName
                    }
                }
            }
        }

        check("live.pw.renderX.LatexView" !in allActivityNames) {
            "RenderX sample LatexView leaked into the merged Debug Manifest"
        }
        check(
            launchableComponents ==
                listOf("com.ai.assistance.operit.ui.main.MainActivity")
        ) {
            "Debug APK must expose exactly one launcher MainActivity, found $launchableComponents"
        }
        logger.lifecycle(
            "Verified one Debug launcher: ${launchableComponents.single()}"
        )
    }
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

@DisableCachingByDefault(
    because = "The output is built by the machine-local Rust and Android NDK toolchains."
)
abstract class BuildNativeRipgrepTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cargoManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cargoLockFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSourceDirectory: DirectoryProperty

    @get:Input
    abstract val rustTarget: Property<String>

    @get:Input
    abstract val rustToolchain: Property<String>

    @get:Input
    abstract val androidApiLevel: Property<Int>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:LocalState
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val target = rustTarget.get()
        val apiLevel = androidApiLevel.get()
        val osName = System.getProperty("os.name").lowercase()
        val hostTag =
            when {
                osName.contains("windows") -> "windows-x86_64"
                osName.contains("linux") -> "linux-x86_64"
                osName.contains("mac") || osName.contains("darwin") -> "darwin-x86_64"
                else -> error("Unsupported native ripgrep build host: $osName")
            }
        val linkerName =
            "aarch64-linux-android${apiLevel}-clang" +
                if (osName.contains("windows")) ".cmd" else ""
        val linker =
            ndkDirectory.get().asFile
                .resolve("toolchains/llvm/prebuilt/$hostTag/bin/$linkerName")
                .canonicalFile
        check(linker.isFile) {
            "Android linker for native ripgrep was not found: $linker"
        }

        val cargoTargetRoot = cargoTargetDirectory.get().asFile
        val cargoExecutable = if (osName.contains("windows")) "cargo.exe" else "cargo"
        val linkerEnvironmentName =
            "CARGO_TARGET_${target.uppercase().replace('-', '_')}_LINKER"

        execOperations.exec {
            workingDir(cargoManifestFile.get().asFile.parentFile)
            executable(cargoExecutable)
            args(
                "+${rustToolchain.get()}",
                "build",
                "--manifest-path",
                cargoManifestFile.get().asFile.absolutePath,
                "--release",
                "--target",
                target,
                "--locked",
            )
            environment("CARGO_TARGET_DIR", cargoTargetRoot.absolutePath)
            environment(linkerEnvironmentName, linker.absolutePath)
        }.assertNormalExitValue()

        val builtLibrary =
            cargoTargetRoot.resolve("$target/release/liboperit_ripgrep.so")
        check(builtLibrary.isFile && builtLibrary.length() > 0L) {
            "Cargo did not produce native ripgrep: $builtLibrary"
        }

        val generatedRoot = outputDirectory.get().asFile
        check(!generatedRoot.exists() || generatedRoot.deleteRecursively()) {
            "Unable to clear generated native ripgrep directory: $generatedRoot"
        }
        val abiDirectory = generatedRoot.resolve("arm64-v8a")
        check(abiDirectory.mkdirs() || abiDirectory.isDirectory) {
            "Unable to create generated native ripgrep ABI directory: $abiDirectory"
        }
        builtLibrary.copyTo(abiDirectory.resolve("liboperit_ripgrep.so"), overwrite = true)
    }
}

@DisableCachingByDefault(
    because = "The executable is built by the machine-local Android NDK toolchain."
)
abstract class BuildShellIdentityLauncherTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val androidApiLevel: Property<Int>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private fun File.calculateSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        require(needle.isNotEmpty()) { "Needle must not be empty" }
        if (needle.size > size) return false
        for (start in 0..size - needle.size) {
            var matches = true
            for (index in needle.indices) {
                if (this[start + index] != needle[index]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    @TaskAction
    fun build() {
        val osName = System.getProperty("os.name").lowercase()
        val hostTag =
            when {
                osName.contains("windows") -> "windows-x86_64"
                osName.contains("linux") -> "linux-x86_64"
                osName.contains("mac") || osName.contains("darwin") -> "darwin-x86_64"
                else -> error("Unsupported shell identity launcher build host: $osName")
            }
        val compilerName =
            "aarch64-linux-android${androidApiLevel.get()}-clang++" +
                if (osName.contains("windows")) ".cmd" else ""
        val compiler =
            ndkDirectory.get().asFile
                .resolve("toolchains/llvm/prebuilt/$hostTag/bin/$compilerName")
                .canonicalFile
        check(compiler.isFile) {
            "Android compiler for shell identity launcher was not found: $compiler"
        }

        val generatedRoot = outputDirectory.get().asFile
        check(!generatedRoot.exists() || generatedRoot.deleteRecursively()) {
            "Unable to clear generated shell identity launcher directory: $generatedRoot"
        }
        check(generatedRoot.mkdirs() || generatedRoot.isDirectory) {
            "Unable to create generated shell identity launcher directory: $generatedRoot"
        }
        val launcher = generatedRoot.resolve("operit_shell_exec")
        execOperations.exec {
            executable(compiler)
            args(
                sourceFile.get().asFile.absolutePath,
                "-std=c++17",
                "-O2",
                "-DNDEBUG",
                "-fno-exceptions",
                "-fno-rtti",
                "-nostdlib++",
                "-fPIE",
                "-pie",
                "-Wl,-z,max-page-size=16384",
                "-Wl,--strip-all",
                "-landroid",
                "-llog",
                "-o",
                launcher.absolutePath,
            )
        }.assertNormalExitValue()

        val bytes = launcher.readBytes()
        check(bytes.size >= 64) { "Shell identity launcher is not a complete ELF file" }
        check(
            bytes[0] == 0x7f.toByte() &&
                bytes[1] == 'E'.code.toByte() &&
                bytes[2] == 'L'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
        ) {
            "Shell identity launcher has no ELF magic"
        }
        check(bytes[4] == 2.toByte() && bytes[5] == 1.toByte()) {
            "Shell identity launcher must be little-endian ELF64"
        }

        fun readUnsignedShort(offset: Int): Int =
            (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8)

        fun readUnsignedInt(offset: Int): Long {
            var value = 0L
            for (index in 0 until 4) {
                value = value or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8))
            }
            return value
        }

        fun readLong(offset: Int): Long {
            var value = 0L
            for (index in 0 until 8) {
                value = value or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8))
            }
            return value
        }

        check(readUnsignedShort(18) == 183) {
            "Shell identity launcher must target AArch64"
        }
        val programHeaderOffset = readLong(32)
        val programHeaderEntrySize = readUnsignedShort(54)
        val programHeaderCount = readUnsignedShort(56)
        check(programHeaderOffset in 0..Int.MAX_VALUE.toLong()) {
            "Shell identity launcher has an invalid program-header offset"
        }
        val loadAlignments =
            buildList {
                repeat(programHeaderCount) { index ->
                    val offset = programHeaderOffset.toInt() + index * programHeaderEntrySize
                    check(offset >= 0 && offset + programHeaderEntrySize <= bytes.size) {
                        "Shell identity launcher program header exceeds the file"
                    }
                    if (readUnsignedInt(offset) == 1L) {
                        add(readLong(offset + 48))
                    }
                }
            }
        check(loadAlignments.isNotEmpty() && loadAlignments.all { alignment -> alignment >= 0x4000L }) {
            "Shell identity launcher PT_LOAD alignment must be at least 0x4000: $loadAlignments"
        }
        check(bytes.containsSequence("/system/bin/linker64".toByteArray(Charsets.US_ASCII))) {
            "Shell identity launcher does not use the Android arm64 linker"
        }
        check(!bytes.containsSequence("native-lib.cpp".toByteArray(Charsets.US_ASCII))) {
            "Shell identity launcher still contains source-level debug paths"
        }
        check(!bytes.containsSequence("libc++_shared.so".toByteArray(Charsets.US_ASCII))) {
            "Shell identity launcher must not depend on the APK C++ shared runtime"
        }
        logger.lifecycle(
            "Verified shell identity launcher: SHA-256=${launcher.calculateSha256()}, " +
                "PT_LOAD=${loadAlignments.joinToString { alignment -> "0x${alignment.toString(16)}" }}"
        )
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
    compileSdk = 37
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
    bundle {
        language {
            // Kiyori owns its in-app locale switcher, so every installed APK must carry every
            // declared locale instead of relying on Play-delivered language splits.
            enableSplit = false
        }
    }
    lint {
        val configuredBaseline = providers.gradleProperty("kiyori.lintBaseline").orNull
        baseline = file(configuredBaseline ?: "lint-baseline.xml")
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
            // BouncyCastle 1.85 的 bcprov/bcutil/bcpkix 携带逐字节相同的许可证；
            // 保留一份精确副本，否则 Java resource merge 会因重复路径失败。
            pickFirsts += "/META-INF/LICENSE.md"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "META-INF/versions/9/module-info.class"
            
            // Fix for duplicate Netty files
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/INDEX.LIST"
            
        }
    }
val verifyPlayerNativeInputs =
    tasks.register("verifyPlayerNativeInputs") {
        description = "Verifies the fixed FFmpegKit and namespaced HTTPS-capable libmpv inputs."
        val mpvThinAar = layout.projectDirectory.file("libs/mpv-player-arm64.aar")
        val ffmpegArm64Aar = layout.projectDirectory.file("libs/ffmpeg-kit-player-arm64.aar")
        inputs.file(mpvThinAar)
        inputs.file(ffmpegArm64Aar)
        doLast {
            val mpvAar = mpvThinAar.asFile
            check(mpvAar.isFile) {
                "Missing ${mpvAar.path}; run ci/script/prepare_mpv_player_dependency.py"
            }
            val mpvSha256 = mpvAar.sha256Hex()
            check(mpvSha256 == playerMpvThinSha256) {
                "Unexpected mpv thin AAR SHA-256: $mpvSha256"
            }
            val expectedMpvMembers =
                listOf(
                    "R.txt",
                    "AndroidManifest.xml",
                    "classes.jar",
                    "assets/cacert.pem",
                    "assets/subfont.ttf",
                    "META-INF/com/android/build/gradle/aar-metadata.properties",
                    "jni/arm64-v8a/libc++_shared.so",
                    *playerMpvFfmpegNamespace.values
                        .map { library -> "jni/arm64-v8a/$library" }
                        .toTypedArray(),
                    "jni/arm64-v8a/libmpv.so",
                    "jni/arm64-v8a/libplayer.so",
                )
            ZipFile(mpvAar).use { archive ->
                val members = archive.entries().asSequence().map { it.name }.toList()
                check(members == expectedMpvMembers) {
                    "Unexpected mpv thin AAR member list: $members"
                }
                val classesEntry = requireNotNull(archive.getEntry("classes.jar")) {
                    "mpv thin AAR has no classes.jar"
                }
                val requiredClasses =
                    setOf(
                        "is/xyz/mpv/MPVLib.class",
                        "is/xyz/mpv/MPVLib\$EventObserver.class",
                        "is/xyz/mpv/MPVLib\$LogObserver.class",
                        "is/xyz/mpv/MPVNode.class",
                        "is/xyz/mpv/Utils.class",
                    )
                val packagedClasses = mutableSetOf<String>()
                ZipInputStream(ByteArrayInputStream(archive.getInputStream(classesEntry).readBytes())).use { classes ->
                    while (true) {
                        val entry = classes.nextEntry ?: break
                        if (!entry.isDirectory && entry.name in requiredClasses) {
                            packagedClasses += entry.name
                        }
                    }
                }
                check(packagedClasses == requiredClasses) {
                    "mpv thin AAR is missing runtime classes: ${requiredClasses - packagedClasses}"
                }
                val mpvNativePayloads =
                    expectedMpvMembers
                        .filter { member -> member.startsWith("jni/") }
                        .associateWith { member ->
                            archive.getInputStream(requireNotNull(archive.getEntry(member))).use { stream ->
                                stream.readBytes()
                            }
                        }
                playerMpvFfmpegNamespace.keys.forEach { sourceName ->
                    val sourceNeedle = sourceName.toByteArray(Charsets.US_ASCII)
                    val owners =
                        mpvNativePayloads
                            .filterValues { payload -> payload.containsByteSequence(sourceNeedle) }
                            .keys
                    check(owners.isEmpty()) {
                        "mpv native namespace still references $sourceName in $owners"
                    }
                }
                playerMpvFfmpegNamespace.values.forEach { namespacedName ->
                    val member = "jni/arm64-v8a/$namespacedName"
                    val payload = requireNotNull(mpvNativePayloads[member])
                    check(payload.containsByteSequence(namespacedName.toByteArray(Charsets.US_ASCII))) {
                        "$member has no matching namespaced SONAME"
                    }
                }
                val libmpvPayload =
                    requireNotNull(mpvNativePayloads["jni/arm64-v8a/libmpv.so"])
                playerMpvFfmpegNamespace.values.forEach { namespacedName ->
                    check(libmpvPayload.containsByteSequence(namespacedName.toByteArray(Charsets.US_ASCII))) {
                        "libmpv.so does not reference namespaced dependency $namespacedName"
                    }
                }
                val libplayerPayload =
                    requireNotNull(mpvNativePayloads["jni/arm64-v8a/libplayer.so"])
                setOf(
                    "libmpcodec.so",
                    "libmpformat.so",
                    "libmputil.so",
                    "libmpscale.so",
                ).forEach { namespacedName ->
                    check(libplayerPayload.containsByteSequence(namespacedName.toByteArray(Charsets.US_ASCII))) {
                        "libplayer.so does not reference namespaced dependency $namespacedName"
                    }
                }
                val mpvAvformatPayload =
                    requireNotNull(mpvNativePayloads["jni/arm64-v8a/libmpformat.so"])
                playerMpvRequiredTlsMarkers.forEach { marker ->
                    check(mpvAvformatPayload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "namespaced mpv libavformat lacks TLS marker $marker"
                    }
                }
                playerRequiredLibcxxSymbols.forEach { symbol ->
                    val needle = symbol.toByteArray(Charsets.US_ASCII)
                    val mpvNeedsSymbol = libmpvPayload.containsByteSequence(needle)
                    check(mpvNeedsSymbol) { "libmpv.so does not reference required C++ symbol $symbol" }
                    val runtimeExportsSymbol =
                        requireNotNull(mpvNativePayloads["jni/arm64-v8a/libc++_shared.so"])
                            .containsByteSequence(needle)
                    check(runtimeExportsSymbol) {
                        "mpv libc++_shared.so does not provide required C++ symbol $symbol"
                    }
                }
            }

            val ffmpegAar = ffmpegArm64Aar.asFile
            check(ffmpegAar.isFile) {
                "Missing ${ffmpegAar.path}, derived from $playerFfmpegSourceCoordinate; " +
                    "run ci/script/prepare_mpv_player_dependency.py"
            }
            val ffmpegSha256 = ffmpegAar.sha256Hex()
            check(ffmpegSha256 == playerFfmpegArm64Sha256) {
                "Unexpected player FFmpegKit AAR SHA-256: $ffmpegSha256"
            }
            val expectedFfmpegMembers =
                playerFfmpegKitLibraryNames.mapTo(mutableSetOf()) { library ->
                    "jni/arm64-v8a/$library"
                }
            ZipFile(ffmpegAar).use { archive ->
                val nativeMembers =
                    archive.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("jni/") && it.endsWith(".so") }
                        .toSet()
                check(nativeMembers == expectedFfmpegMembers) {
                    "Unexpected player FFmpegKit native member list: $nativeMembers"
                }
            }
        }
    }

tasks.named("preBuild").configure {
    dependsOn(verifyPlayerNativeInputs)
}

val verifyDebugPlayerRuntimePackaging =
    tasks.register("verifyDebugPlayerRuntimePackaging") {
        description = "Verifies the Debug APK player classes and isolated HTTPS-capable native closure."
        val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
        inputs.file(debugApk)
        doLast {
            val apk = debugApk.get().asFile
            check(apk.isFile) { "Debug APK is missing: $apk" }
            val requiredDescriptors =
                listOf(
                    "Lis/xyz/mpv/MPVLib;",
                    "Lis/xyz/mpv/MPVNode;",
                    "Lcom/ai/assistance/operit/core/player/runtime/MpvPlayerEngine;",
                    "Lcom/ai/assistance/operit/core/player/runtime/PlayerRuntimeService;",
                )
            ZipFile(apk).use { archive ->
                val dexEntries =
                    archive.entries().asSequence()
                        .filter { entry ->
                            !entry.isDirectory && Regex("classes(\\d+)?\\.dex").matches(entry.name)
                        }
                        .toList()
                check(dexEntries.isNotEmpty()) { "Debug APK contains no classes*.dex entries" }
                requiredDescriptors.forEach { descriptor ->
                    val needle = descriptor.toByteArray(Charsets.US_ASCII)
                    val found =
                        dexEntries.any { entry ->
                            archive.getInputStream(entry).buffered().use { stream ->
                                stream.containsByteSequence(needle)
                            }
                        }
                    check(found) { "Debug APK is missing runtime descriptor $descriptor" }
                }
                val requiredPlayerNativeNames =
                    playerFfmpegKitLibraryNames + playerMpvNativeLibraryNames
                requiredPlayerNativeNames.forEach { libraryName ->
                    val expectedPath = "lib/arm64-v8a/$libraryName"
                    val matches =
                        archive.entries().asSequence()
                            .filter { entry -> !entry.isDirectory && entry.name == expectedPath }
                            .toList()
                    check(matches.size == 1) {
                        "Debug APK must contain exactly one $expectedPath, found ${matches.size}"
                    }
                }
                val apkLibmpv =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libmpv.so")),
                    ).use { stream -> stream.readBytes() }
                playerMpvFfmpegNamespace.forEach { (sourceName, namespacedName) ->
                    check(!apkLibmpv.containsByteSequence(sourceName.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK libmpv.so still references non-namespaced $sourceName"
                    }
                    check(apkLibmpv.containsByteSequence(namespacedName.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK libmpv.so is missing namespaced $namespacedName"
                    }
                }
                val apkMpvAvformat =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libmpformat.so")),
                    ).use { stream -> stream.readBytes() }
                playerMpvRequiredTlsMarkers.forEach { marker ->
                    check(apkMpvAvformat.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK namespaced mpv libavformat lacks TLS marker $marker"
                    }
                }
                val libcxxEntries =
                    archive.entries().asSequence()
                        .filter { entry -> !entry.isDirectory && entry.name.endsWith("/libc++_shared.so") }
                        .toList()
                check(libcxxEntries.map { entry -> entry.name } == listOf("lib/arm64-v8a/libc++_shared.so")) {
                    "Debug APK must contain one arm64 C++ runtime: ${libcxxEntries.map { it.name }}"
                }
                playerRequiredLibcxxSymbols.forEach { symbol ->
                    val needle = symbol.toByteArray(Charsets.US_ASCII)
                    val found =
                        archive.getInputStream(libcxxEntries.single()).buffered().use { stream ->
                            stream.containsByteSequence(needle)
                        }
                    check(found) { "Debug APK C++ runtime is missing required symbol $symbol" }
                }
            }
        }
    }

val verifySingleDebugLauncher =
    tasks.register<VerifySingleDebugLauncherTask>("verifySingleDebugLauncher") {
        description =
            "Verifies that dependency manifests cannot add a second Debug launcher icon."
    }

tasks.matching { task -> task.name == "assembleDebug" }.configureEach {
    finalizedBy(verifyDebugPlayerRuntimePackaging, verifySingleDebugLauncher)
}

//    aaptOptions {
//        noCompress += "tflite"
//    }
}

val buildNativeRipgrep =
    tasks.register<BuildNativeRipgrepTask>("buildNativeRipgrep") {
        description = "Builds the arm64 native ripgrep library packaged by every Android variant."
        cargoManifestFile.set(rootProject.layout.projectDirectory.file("tools/native_ripgrep/Cargo.toml"))
        cargoLockFile.set(rootProject.layout.projectDirectory.file("tools/native_ripgrep/Cargo.lock"))
        rustSourceDirectory.set(rootProject.layout.projectDirectory.dir("tools/native_ripgrep/src"))
        rustTarget.set("aarch64-linux-android")
        rustToolchain.set("1.88.0")
        androidApiLevel.set(26)
        ndkVersion.set(providers.gradleProperty("kiyori.android.ndkVersion"))
        ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
        cargoTargetDirectory.set(layout.buildDirectory.dir("native-ripgrep/cargo-target"))
        outputDirectory.set(layout.buildDirectory.dir("generated/nativeRipgrep/jniLibs"))
    }

val buildShellIdentityLauncher =
    tasks.register<BuildShellIdentityLauncherTask>("buildShellIdentityLauncher") {
        description =
            "Builds and verifies the 16 KB-compatible arm64 shell identity launcher asset."
        sourceFile.set(
            rootProject.layout.projectDirectory.file(
                "tools/shell_identity_launcher/native-lib.cpp"
            )
        )
        androidApiLevel.set(26)
        ndkVersion.set(providers.gradleProperty("kiyori.android.ndkVersion"))
        ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
        outputDirectory.set(layout.buildDirectory.dir("generated/shellIdentityLauncherAssets"))
    }

androidComponents {
    // AGP 9 removes applicationVariants and its internal output types. Keeping the
    // filenames on the public Variant API prevents the nightly/clone artifact contract
    // from depending on an implementation class that no longer exists.
    onVariants { variant ->
        if (variant.name == "debug") {
            tasks.named<VerifySingleDebugLauncherTask>(
                "verifySingleDebugLauncher"
            ).configure {
                mergedManifest.set(
                    variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
                )
            }
        }
        val assets =
            requireNotNull(variant.sources.assets) {
                "Android variant ${variant.name} does not expose an assets source directory."
            }
        assets.addGeneratedSourceDirectory(generateBundledToolPkgAssets) {
            it.outputDirectory
        }
        assets.addGeneratedSourceDirectory(buildShellIdentityLauncher) {
            it.outputDirectory
        }
        val jniLibs =
            requireNotNull(variant.sources.jniLibs) {
                "Android variant ${variant.name} does not expose a JNI libs source directory."
            }
        jniLibs.addGeneratedSourceDirectory(buildNativeRipgrep) {
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

val poiOoxmlSanitizerInput =
    configurations.create("poiOoxmlSanitizerInput") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
val bcpkixSanitizerInput =
    configurations.create("bcpkixSanitizerInput") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }
val sanitizePoiOoxml =
    project.registerSanitizedDependencyJar(
        taskName = "sanitizePoiOoxml",
        inputConfiguration = poiOoxmlSanitizerInput,
        outputFileName = "poi-ooxml-safe-${libs.versions.poi.get()}.jar",
        excludedPrefixes =
            setOf(
                "org/apache/poi/poifs/crypt/dsig/",
                "org/apache/poi/xssf/usermodel/XSSFSignatureLine",
                "org/apache/poi/xwpf/usermodel/XWPFSignatureLine",
            ),
        expectedUnsafeEntries =
            setOf(
                "org/apache/poi/poifs/crypt/dsig/services/" +
                    "TimeStampSimpleHttpClient\$UnsafeTrustManager.class",
            ),
    )
val sanitizeBcpkix =
    project.registerSanitizedDependencyJar(
        taskName = "sanitizeBcpkix",
        inputConfiguration = bcpkixSanitizerInput,
        outputFileName = "bcpkix-safe-${libs.versions.bouncycastle.get()}.jar",
        excludedPrefixes = setOf("org/bouncycastle/est/"),
        expectedUnsafeEntries =
            setOf(
                "org/bouncycastle/est/jcajce/JcaJceUtils\$1.class",
            ),
    )
dependencies {
    add(poiOoxmlSanitizerInput.name, libs.poi.ooxml)
    add(bcpkixSanitizerInput.name, libs.bouncycastle.bcpkix)

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
    implementation("com.google.android.filament:filament-android:1.74.0")
    implementation("com.google.android.filament:gltfio-android:1.74.0")
    implementation("com.google.android.filament:filament-utils-android:1.74.0")
    implementation(libs.androidx.ui.graphics.android)
    // Fixed vendored JARs remain globbed. The two generated player AARs are explicit and own
    // disjoint native names, including one C++ runtime built with the same toolchain as libmpv.
    implementation(files("libs/smart-exception-common-0.2.1.jar"))
    implementation(files("libs/smart-exception-java-0.2.1.jar"))
    implementation(files("libs/mpv-player-arm64.aar"))
    implementation(files("libs/ffmpeg-kit-player-arm64.aar"))
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
    
    // AndroidX Media3 for audio/video playback and background media.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    
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
    
    // ONNX Runtime for Android - 支持更强大的多语言Embedding模型
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx) // Kotlin扩展和协程支持
    // Room 2.8.4 的处理器默认解析到 kotlin-metadata-jvm 2.2.0，无法读取 Kotlin 2.4 metadata。
    // 显式对齐编译器 metadata 库；否则 KAPT 在生成 Room 实现前会因 metadata 版本上限失败。
    kapt(libs.kotlin.metadata.jvm)
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
    implementation(files(sanitizePoiOoxml.flatMap { it.archiveFile }).builtBy(sanitizePoiOoxml))
    implementation(libs.poi.ooxml.lite)
    implementation(libs.poi.scratchpad)
    implementation(libs.xmlbeans)
    implementation(libs.curvesapi)

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
    
    // PDFBox Android still declares the older jdk15to18 line. Keep one current
    // BouncyCastle family so PKIX, utility, and provider classes cannot diverge.
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
    }

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // BouncyCastle - explicitly include jdk18on version to avoid conflicts
    implementation(libs.bouncycastle.bcprov)
    implementation(files(sanitizeBcpkix.flatMap { it.archiveFile }).builtBy(sanitizeBcpkix))
    implementation(libs.bouncycastle.bcutil)

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
