import com.android.build.api.artifact.SingleArtifact
import com.kiyori.buildlogic.tasks.BuildNativeRipgrepTask
import com.kiyori.buildlogic.tasks.BuildShellIdentityLauncherTask
import com.kiyori.buildlogic.tasks.GenerateBundledToolPkgAssetsTask
import com.kiyori.buildlogic.tasks.PrepareMihomoRuntimeTask
import com.kiyori.buildlogic.tasks.VerifySingleDebugLauncherTask
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.parcelize)
    id("io.objectbox")
}

val playerFfmpegSourceIdentity =
    "ffmpegkit-maintained/ffmpeg@62b07bf097baf26b416c815aea514e05c9ad6d63 + " +
        "FFmpeg@n9.0.1/bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa + " +
        "OpenH264@v2.6.0/652bdb7719f30b52b08e506645a7322ff1b2cc6f"
val playerFfmpegArm64Sha256 =
    "7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394"
val playerMpvThinSha256 =
    "f52aca6f35c651be7aab55f2efe6b5f40180d1ebaeb1404cc446470bf8deb6a4"
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
val playerFfmpegVersionNamespaces =
    linkedMapOf(
        "libavcodec.so" to "LIBAVCODEC_63",
        "libavdevice.so" to "LIBAVDEVICE_63",
        "libavfilter.so" to "LIBAVFILTER_12",
        "libavformat.so" to "LIBAVFORMAT_63",
        "libavutil.so" to "LIBAVUTIL_61",
        "libswresample.so" to "LIBSWRESAMPLE_7",
        "libswscale.so" to "LIBSWSCALE_10",
    )
val playerMpvFfmpegVersionNamespaces =
    playerFfmpegVersionNamespaces.mapKeys { (normalName, _) ->
        requireNotNull(playerMpvFfmpegNamespace[normalName])
    }
val playerFfmpegRequiredBuildMarkers =
    listOf(
        "n9.0.1",
        "--enable-gpl",
        "--enable-libfontconfig",
        "--enable-libfreetype",
        "--enable-libharfbuzz",
        "--enable-libfribidi",
        "--enable-libmp3lame",
        "--enable-libass",
        "--enable-libdav1d",
        "--enable-libaom",
        "--enable-libopenh264",
        "--disable-openssl",
        "--enable-zlib",
        "--enable-mediacodec",
    )
val playerFfmpegQualifiedCodecMarkers =
    listOf(
        "libopenh264enc",
        "AAC encoder",
        "libavcodec/aacenc.c",
    )
val playerFfmpegQualifiedMp4Markers =
    listOf(
        "libavformat/movenc.c",
        "mov/mp4/tgp/psp/tg2/ipod/ismv/f4v muxer",
    )
val playerFfmpegRequiredFilterMarkers = listOf("drawtext", "eq", "boxblur")
val playerFfmpegKitWrapperMarker = "8.1.7-kiyori-n9.0.1-r6"
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
val playerNativeZipAlignment = 16 * 1024L
val scriptProxyMihomoSha256 =
    "94344144936968f25e7089bbeac2d87f3caf67574ba433511424724ad7435dad"
val scriptProxyLauncherSha256 =
    "93399232fa7a6b786a142e45dd1658ea0690aeb9ac9ebaa4a656a77d8c1e2c67"

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

private fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

private fun ByteArray.requireAndroidArm64Elf(label: String) {
    check(size >= 64 && copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
        "$label has no ELF magic"
    }
    check(this[4] == 2.toByte() && this[5] == 1.toByte()) {
        "$label must be little-endian ELF64"
    }
    fun ushort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
    fun uint(offset: Int): Long =
        (0 until 4).fold(0L) { value, index ->
            value or ((this[offset + index].toLong() and 0xffL) shl (index * 8))
        }
    fun long(offset: Int): Long =
        (0 until 8).fold(0L) { value, index ->
            value or ((this[offset + index].toLong() and 0xffL) shl (index * 8))
        }
    check(ushort(16) == 3) { "$label must be ET_DYN/PIE" }
    check(ushort(18) == 183) { "$label must target AArch64" }
    val programHeaderOffset = long(32)
    val entrySize = ushort(54)
    val entryCount = ushort(56)
    val elfSize = size
    check(programHeaderOffset in 0..Int.MAX_VALUE.toLong()) {
        "$label has an invalid program-header offset"
    }
    val loadAlignments = buildList {
        repeat(entryCount) { index ->
            val offset = programHeaderOffset.toInt() + index * entrySize
            check(offset >= 0 && offset + entrySize <= elfSize) {
                "$label program header exceeds the file"
            }
            if (uint(offset) == 1L) add(long(offset + 48))
        }
    }
    check(loadAlignments.isNotEmpty() && loadAlignments.all { alignment -> alignment >= 0x4000L }) {
        "$label PT_LOAD alignment must be at least 0x4000: $loadAlignments"
    }
    check(containsByteSequence("/system/bin/linker64".toByteArray(Charsets.US_ASCII))) {
        "$label does not use the Android arm64 linker"
    }
}

private fun RandomAccessFile.readUnsignedShortLittleEndian(): Int {
    val first = read()
    val second = read()
    check(first >= 0 && second >= 0) { "Truncated ZIP header in $this" }
    return first or (second shl 8)
}

private fun RandomAccessFile.readUnsignedIntLittleEndian(): Long {
    val first = read()
    val second = read()
    val third = read()
    val fourth = read()
    check(first >= 0 && second >= 0 && third >= 0 && fourth >= 0) {
        "Truncated ZIP header in $this"
    }
    return first.toLong() or
        (second.toLong() shl 8) or
        (third.toLong() shl 16) or
        (fourth.toLong() shl 24)
}

private fun File.nativeZipDataOffsets(): Map<String, Long> {
    val offsets = linkedMapOf<String, Long>()
    RandomAccessFile(this, "r").use { archive ->
        var localHeaderOffset = 0L
        scan@ while (localHeaderOffset + 4 <= archive.length()) {
            archive.seek(localHeaderOffset)
            when (val signature = archive.readUnsignedIntLittleEndian()) {
                0x04034B50L -> {
                    archive.readUnsignedShortLittleEndian()
                    val flags = archive.readUnsignedShortLittleEndian()
                    val compression = archive.readUnsignedShortLittleEndian()
                    archive.readUnsignedShortLittleEndian()
                    archive.readUnsignedShortLittleEndian()
                    archive.readUnsignedIntLittleEndian()
                    val compressedSize = archive.readUnsignedIntLittleEndian()
                    archive.readUnsignedIntLittleEndian()
                    val nameLength = archive.readUnsignedShortLittleEndian()
                    val extraLength = archive.readUnsignedShortLittleEndian()
                    check((flags and 0x08) == 0) {
                        "ZIP data descriptors are forbidden in deterministic AAR $path"
                    }
                    val namePayload = ByteArray(nameLength)
                    archive.readFully(namePayload)
                    val name = String(namePayload, Charsets.US_ASCII)
                    archive.seek(archive.filePointer + extraLength)
                    val dataOffset = archive.filePointer
                    if (name.startsWith("jni/") && name.endsWith(".so")) {
                        check(compression == 0) {
                            "Native AAR member must be stored: $name"
                        }
                        offsets[name] = dataOffset
                    }
                    localHeaderOffset = dataOffset + compressedSize
                }
                0x02014B50L,
                0x06054B50L
                -> break@scan
                else -> error(
                    "Unexpected ZIP signature 0x${signature.toString(16)} " +
                        "at 0x${localHeaderOffset.toString(16)} in $path",
                )
            }
        }
    }
    check(offsets.isNotEmpty()) { "AAR contains no native members: $path" }
    return offsets
}

private fun File.requireNativeZipAlignment() {
    val misaligned =
        nativeZipDataOffsets().filterValues { offset ->
            offset % playerNativeZipAlignment != 0L
        }
    check(misaligned.isEmpty()) {
        "Native AAR members are not ${playerNativeZipAlignment.toInt()}-byte aligned: " +
            misaligned.entries.joinToString { (name, offset) ->
                "$name=0x${offset.toString(16)}"
            }
    }
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
        buildConfigField("String", "OPERIT_MARKET_COMPAT_VERSION", "\"1.12.1+3\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            // The parent Kiyori APK currently has one explicit native product contract.
            // Other modules may support additional ABIs in isolated builds, but they are not
            // packaged into this application artifact.
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
            // These files are launched as standalone PIE executables. Stripping the packaged
            // files changes their verified bytes and breaks the runtime provenance contract.
            keepDebugSymbols +=
                setOf(
                    "**/libkiyori_mihomo.so",
                    "**/libkiyori_mihomo_launcher.so",
                )
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
        val gplv3License = rootProject.layout.projectDirectory.file("LICENSE")
        inputs.file(mpvThinAar)
        inputs.file(ffmpegArm64Aar)
        inputs.file(gplv3License)
        doLast {
            val mpvAar = mpvThinAar.asFile
            check(mpvAar.isFile) {
                "Missing ${mpvAar.path}; run ci/script/prepare_mpv_player_dependency.py"
            }
            val mpvSha256 = mpvAar.sha256Hex()
            check(mpvSha256 == playerMpvThinSha256) {
                "Unexpected mpv thin AAR SHA-256: $mpvSha256"
            }
            mpvAar.requireNativeZipAlignment()
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
                playerMpvFfmpegVersionNamespaces.forEach { (libraryName, marker) ->
                    val member = "jni/arm64-v8a/$libraryName"
                    val payload = requireNotNull(mpvNativePayloads[member])
                    check(payload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "$member lacks FFmpeg 9 namespace $marker"
                    }
                }
                val mpvAvutilPayload =
                    requireNotNull(mpvNativePayloads["jni/arm64-v8a/libmputil.so"])
                check(mpvAvutilPayload.containsByteSequence("n9.0.1".toByteArray(Charsets.US_ASCII))) {
                    "namespaced mpv libavutil does not report FFmpeg n9.0.1"
                }
                check(!mpvAvutilPayload.containsByteSequence("n8.1.2".toByteArray(Charsets.US_ASCII))) {
                    "namespaced mpv libavutil still contains FFmpeg n8.1.2"
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
                "Missing ${ffmpegAar.path}, derived from $playerFfmpegSourceIdentity; " +
                    "run ci/script/prepare_mpv_player_dependency.py"
            }
            val ffmpegSha256 = ffmpegAar.sha256Hex()
            check(ffmpegSha256 == playerFfmpegArm64Sha256) {
                "Unexpected player FFmpegKit AAR SHA-256: $ffmpegSha256"
            }
            ffmpegAar.requireNativeZipAlignment()
            check(playerFfmpegKitLibraryNames.intersect(playerMpvNativeLibraryNames).isEmpty()) {
                "FFmpegKit normal-name and mpv namespaced native owners overlap"
            }
            val expectedFfmpegMembers =
                playerFfmpegKitLibraryNames.mapTo(mutableSetOf()) { library ->
                    "jni/arm64-v8a/$library"
                }
            ZipFile(ffmpegAar).use { archive ->
                val packagedGplv3License =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("res/raw/license_gplv3.txt")) {
                            "FFmpegKit AAR is missing res/raw/license_gplv3.txt"
                        },
                    ).use { stream -> stream.readBytes() }
                check(packagedGplv3License.contentEquals(gplv3License.asFile.readBytes())) {
                    "FFmpegKit GPLv3 license resource differs from the repository LICENSE"
                }
                val nativeMembers =
                    archive.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("jni/") && it.endsWith(".so") }
                        .toSet()
                check(nativeMembers == expectedFfmpegMembers) {
                    "Unexpected player FFmpegKit native member list: $nativeMembers"
                }
                val ffmpegNativePayloads =
                    nativeMembers.associateWith { member ->
                        archive.getInputStream(requireNotNull(archive.getEntry(member))).use { stream ->
                            stream.readBytes()
                        }
                    }
                playerFfmpegVersionNamespaces.forEach { (libraryName, marker) ->
                    val member = "jni/arm64-v8a/$libraryName"
                    val payload = requireNotNull(ffmpegNativePayloads[member])
                    check(payload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "$member lacks FFmpeg 9 namespace $marker"
                    }
                }
                val ffmpegAvutilPayload =
                    requireNotNull(ffmpegNativePayloads["jni/arm64-v8a/libavutil.so"])
                playerFfmpegRequiredBuildMarkers.forEach { marker ->
                    check(ffmpegAvutilPayload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "FFmpegKit libavutil lacks build marker $marker"
                    }
                }
                check(!ffmpegAvutilPayload.containsByteSequence("n8.1.2".toByteArray(Charsets.US_ASCII))) {
                    "FFmpegKit libavutil still contains FFmpeg n8.1.2"
                }
                val ffmpegAvcodecPayload =
                    requireNotNull(ffmpegNativePayloads["jni/arm64-v8a/libavcodec.so"])
                playerFfmpegQualifiedCodecMarkers.forEach { marker ->
                    check(ffmpegAvcodecPayload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "FFmpegKit libavcodec lacks qualified h264_aac_mp4 marker $marker"
                    }
                }
                val ffmpegAvformatPayload =
                    requireNotNull(ffmpegNativePayloads["jni/arm64-v8a/libavformat.so"])
                playerFfmpegQualifiedMp4Markers.forEach { marker ->
                    check(ffmpegAvformatPayload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "FFmpegKit libavformat lacks qualified h264_aac_mp4 marker $marker"
                    }
                }
                val ffmpegAvfilterPayload =
                    requireNotNull(ffmpegNativePayloads["jni/arm64-v8a/libavfilter.so"])
                playerFfmpegRequiredFilterMarkers.forEach { marker ->
                    check(ffmpegAvfilterPayload.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "FFmpegKit libavfilter lacks required filter marker $marker"
                    }
                }
                val ffmpegkitPayload =
                    requireNotNull(ffmpegNativePayloads["jni/arm64-v8a/libffmpegkit.so"])
                check(
                    ffmpegkitPayload.containsByteSequence(
                        playerFfmpegKitWrapperMarker.toByteArray(Charsets.US_ASCII),
                    ),
                ) {
                    "libffmpegkit.so lacks wrapper marker $playerFfmpegKitWrapperMarker"
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
                val apkMpvAvutil =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libmputil.so")),
                    ).use { stream -> stream.readBytes() }
                listOf("n9.0.1", "LIBAVUTIL_61").forEach { marker ->
                    check(apkMpvAvutil.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK namespaced mpv libavutil lacks M9 marker $marker"
                    }
                }
                check(!apkMpvAvutil.containsByteSequence("n8.1.2".toByteArray(Charsets.US_ASCII))) {
                    "Debug APK namespaced mpv libavutil still contains FFmpeg n8.1.2"
                }
                val apkFfmpegAvutil =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libavutil.so")),
                    ).use { stream -> stream.readBytes() }
                listOf("n9.0.1", "LIBAVUTIL_61").forEach { marker ->
                    check(apkFfmpegAvutil.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK FFmpegKit libavutil lacks M9 marker $marker"
                    }
                }
                check(!apkFfmpegAvutil.containsByteSequence("n8.1.2".toByteArray(Charsets.US_ASCII))) {
                    "Debug APK FFmpegKit libavutil still contains FFmpeg n8.1.2"
                }
                val apkFfmpegAvcodec =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libavcodec.so")),
                    ).use { stream -> stream.readBytes() }
                playerFfmpegQualifiedCodecMarkers.forEach { marker ->
                    check(apkFfmpegAvcodec.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK FFmpegKit libavcodec lacks qualified h264_aac_mp4 marker $marker"
                    }
                }
                val apkFfmpegAvformat =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libavformat.so")),
                    ).use { stream -> stream.readBytes() }
                playerFfmpegQualifiedMp4Markers.forEach { marker ->
                    check(apkFfmpegAvformat.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK FFmpegKit libavformat lacks qualified h264_aac_mp4 marker $marker"
                    }
                }
                val apkFfmpegAvfilter =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libavfilter.so")),
                    ).use { stream -> stream.readBytes() }
                playerFfmpegRequiredFilterMarkers.forEach { marker ->
                    check(apkFfmpegAvfilter.containsByteSequence(marker.toByteArray(Charsets.US_ASCII))) {
                        "Debug APK FFmpegKit libavfilter lacks required filter marker $marker"
                    }
                }
                val apkFfmpegKit =
                    archive.getInputStream(
                        requireNotNull(archive.getEntry("lib/arm64-v8a/libffmpegkit.so")),
                    ).use { stream -> stream.readBytes() }
                check(
                    apkFfmpegKit.containsByteSequence(
                        playerFfmpegKitWrapperMarker.toByteArray(Charsets.US_ASCII),
                    ),
                ) {
                    "Debug APK libffmpegkit.so lacks wrapper marker $playerFfmpegKitWrapperMarker"
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

val verifyDebugScriptProxyRuntimePackaging =
    tasks.register("verifyDebugScriptProxyRuntimePackaging") {
        description = "Verifies the pinned Mihomo runtime and parent-death launcher in the Debug APK."
        val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
        inputs.file(debugApk)
        doLast {
            val apk = debugApk.get().asFile
            check(apk.isFile) { "Debug APK is missing: $apk" }
            ZipFile(apk).use { archive ->
                val nativeEntries =
                    archive.entries().asSequence()
                        .filter { entry -> !entry.isDirectory && entry.name.endsWith(".so") }
                        .toList()
                val duplicateBasenames =
                    nativeEntries.groupBy { entry -> entry.name.substringAfterLast('/') }
                        .filterValues { entries -> entries.size > 1 }
                        .mapValues { (_, entries) -> entries.map { entry -> entry.name } }
                check(duplicateBasenames.isEmpty()) {
                    "Debug APK contains duplicate native basenames: $duplicateBasenames"
                }

                val required =
                    linkedMapOf(
                        "lib/arm64-v8a/libkiyori_mihomo.so" to scriptProxyMihomoSha256,
                        "lib/arm64-v8a/libkiyori_mihomo_launcher.so" to
                            scriptProxyLauncherSha256,
                    )
                required.forEach { (path, expectedSha256) ->
                    val matches = nativeEntries.filter { entry -> entry.name == path }
                    check(matches.size == 1) {
                        "Debug APK must contain exactly one $path, found ${matches.size}"
                    }
                    val bytes = archive.getInputStream(matches.single()).use { stream -> stream.readBytes() }
                    val actualSha256 = bytes.sha256Hex()
                    check(actualSha256 == expectedSha256) {
                        "$path SHA-256 mismatch: expected=$expectedSha256 actual=$actualSha256"
                    }
                    bytes.requireAndroidArm64Elf(path)
                }
            }
        }
    }

tasks.matching { task -> task.name == "assembleDebug" }.configureEach {
    finalizedBy(
        verifyDebugPlayerRuntimePackaging,
        verifySingleDebugLauncher,
        verifyDebugScriptProxyRuntimePackaging,
    )
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
        outputRelativePath.set("operit_shell_exec")
        componentLabel.set("shell identity launcher")
    }

val prepareMihomoRuntime =
    tasks.register<PrepareMihomoRuntimeTask>("prepareMihomoRuntime") {
        description = "Downloads and verifies the pinned arm64 Mihomo runtime."
        releaseUrl.set(
            "https://github.com/MetaCubeX/mihomo/releases/download/v1.19.30/" +
                "mihomo-android-arm64-v8-v1.19.30.gz"
        )
        archiveSize.set(17_932_346L)
        archiveSha256.set("19AFEB40FCA190FC2E3906A4E3B87C74A0C2120626FD3CB3AE0CF4092CB780AD")
        executableSize.set(52_586_488L)
        executableSha256.set("94344144936968F25E7089BBEAC2D87F3CAF67574BA433511424724AD7435DAD")
        cacheFile.set(layout.projectDirectory.file("libs/mihomo-android-arm64-v8-v1.19.30.gz"))
        outputDirectory.set(layout.buildDirectory.dir("generated/mihomoRuntime/jniLibs"))
    }

val buildMihomoParentDeathLauncher =
    tasks.register<BuildShellIdentityLauncherTask>("buildMihomoParentDeathLauncher") {
        description = "Builds the arm64 launcher that terminates Mihomo with the Kiyori process."
        sourceFile.set(
            rootProject.layout.projectDirectory.file("tools/mihomo_parent_launcher/native-lib.cpp")
        )
        androidApiLevel.set(26)
        ndkVersion.set(providers.gradleProperty("kiyori.android.ndkVersion"))
        ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
        outputDirectory.set(layout.buildDirectory.dir("generated/mihomoParentLauncher/jniLibs"))
        outputRelativePath.set("arm64-v8a/libkiyori_mihomo_launcher.so")
        componentLabel.set("Mihomo parent-death launcher")
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
        jniLibs.addGeneratedSourceDirectory(prepareMihomoRuntime) {
            it.outputDirectory
        }
        jniLibs.addGeneratedSourceDirectory(buildMihomoParentDeathLauncher) {
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

    implementation(project(":dragonbones"))
    implementation(project(":terminal"))
    implementation(project(":mnn"))
    implementation(project(":llama"))
    implementation(project(":mmd"))
    implementation(project(":fbx"))
    implementation(project(":showerclient"))
    implementation(project(":quickjs"))

    // glTF runtime rendering (Filament)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)
    implementation(libs.filament.utils)
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
    
    // libsu - root access library
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)
    
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
    implementation(libs.coil.svg)
    
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
    implementation(libs.onnxruntime.android)

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
    implementation(libs.tasker.plugin)
    
    // WorkManager for scheduled workflows
    implementation(libs.work.runtime.ktx)

    // Network dependencies
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.jsoup)
    implementation(libs.snakeyaml.engine)

    // DataStore dependencies
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.core)

    // Debug dependencies
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.mockwebserver)
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

    // Additional Android test infrastructure
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

    implementation(libs.mcp.sdk)
    
    // PDFBox Android still declares the older jdk15to18 line. Keep one current
    // BouncyCastle family so PKIX, utility, and provider classes cannot diverge.
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
    }

    // Security
    implementation(libs.security.crypto)
    
    // BouncyCastle - explicitly include jdk18on version to avoid conflicts
    implementation(libs.bouncycastle.bcprov)
    implementation(files(sanitizeBcpkix.flatMap { it.archiveFile }).builtBy(sanitizeBcpkix))
    implementation(libs.bouncycastle.bcutil)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp.logging)


    // Accompanist
    implementation(libs.accompanist.systemuicontroller)

    // Glance for Widgets (Compose for Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
