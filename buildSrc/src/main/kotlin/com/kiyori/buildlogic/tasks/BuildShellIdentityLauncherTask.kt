package com.kiyori.buildlogic.tasks

import java.io.File
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

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

    @get:Input
    abstract val outputRelativePath: Property<String>

    @get:Input
    abstract val componentLabel: Property<String>

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
        val label = componentLabel.get()
        val osName = System.getProperty("os.name").lowercase()
        val hostTag =
            when {
                osName.contains("windows") -> "windows-x86_64"
                osName.contains("linux") -> "linux-x86_64"
                osName.contains("mac") || osName.contains("darwin") -> "darwin-x86_64"
                else -> error("Unsupported $label build host: $osName")
            }
        val compilerName =
            "aarch64-linux-android${androidApiLevel.get()}-clang++" +
                if (osName.contains("windows")) ".cmd" else ""
        val compiler =
            ndkDirectory.get().asFile
                .resolve("toolchains/llvm/prebuilt/$hostTag/bin/$compilerName")
                .canonicalFile
        check(compiler.isFile) {
            "Android compiler for $label was not found: $compiler"
        }

        val generatedRoot = outputDirectory.get().asFile
        check(!generatedRoot.exists() || generatedRoot.deleteRecursively()) {
            "Unable to clear generated $label directory: $generatedRoot"
        }
        check(generatedRoot.mkdirs() || generatedRoot.isDirectory) {
            "Unable to create generated $label directory: $generatedRoot"
        }
        val launcher = generatedRoot.resolve(outputRelativePath.get())
        check(launcher.parentFile.mkdirs() || launcher.parentFile.isDirectory) {
            "Unable to create $label output directory: ${launcher.parentFile}"
        }
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
        check(bytes.size >= 64) { "$label is not a complete ELF file" }
        check(
            bytes[0] == 0x7f.toByte() &&
                bytes[1] == 'E'.code.toByte() &&
                bytes[2] == 'L'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
        ) {
            "$label has no ELF magic"
        }
        check(bytes[4] == 2.toByte() && bytes[5] == 1.toByte()) {
            "$label must be little-endian ELF64"
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
            "$label must target AArch64"
        }
        val programHeaderOffset = readLong(32)
        val programHeaderEntrySize = readUnsignedShort(54)
        val programHeaderCount = readUnsignedShort(56)
        check(programHeaderOffset in 0..Int.MAX_VALUE.toLong()) {
            "$label has an invalid program-header offset"
        }
        val loadAlignments =
            buildList {
                repeat(programHeaderCount) { index ->
                    val offset = programHeaderOffset.toInt() + index * programHeaderEntrySize
                    check(offset >= 0 && offset + programHeaderEntrySize <= bytes.size) {
                        "$label program header exceeds the file"
                    }
                    if (readUnsignedInt(offset) == 1L) {
                        add(readLong(offset + 48))
                    }
                }
            }
        check(loadAlignments.isNotEmpty() && loadAlignments.all { alignment -> alignment >= 0x4000L }) {
            "$label PT_LOAD alignment must be at least 0x4000: $loadAlignments"
        }
        check(bytes.containsSequence("/system/bin/linker64".toByteArray(Charsets.US_ASCII))) {
            "$label does not use the Android arm64 linker"
        }
        check(!bytes.containsSequence("native-lib.cpp".toByteArray(Charsets.US_ASCII))) {
            "$label still contains source-level debug paths"
        }
        check(!bytes.containsSequence("libc++_shared.so".toByteArray(Charsets.US_ASCII))) {
            "$label must not depend on the APK C++ shared runtime"
        }
        logger.lifecycle(
            "Verified $label: SHA-256=${launcher.calculateSha256()}, " +
                "PT_LOAD=${loadAlignments.joinToString { alignment -> "0x${alignment.toString(16)}" }}"
        )
    }
}
