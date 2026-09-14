package com.kiyori.buildlogic.tasks

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Builds pinned Go sources using the host Android NDK toolchain.")
abstract class PrepareMihomoRuntimeTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty
    @get:Input abstract val goExecutable: Property<String>
    @get:Input abstract val ndkVersion: Property<String>
    @get:Internal abstract val ndkDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val input = sourceDirectory.get().asFile
        val pins = Properties().apply { input.resolve("source.properties").inputStream().use(::load) }
        fun pin(name: String) = requireNotNull(pins.getProperty(name)) { "Missing Mihomo source pin: $name" }
        val env = mutableMapOf(
            "GOTOOLCHAIN" to pin("go.version"), "GOWORK" to "off", "GOFLAGS" to "",
            "GOOS" to "android", "GOARCH" to "arm64", "CGO_ENABLED" to "1",
        )
        val work = temporaryDir.resolve("source")
        check(!work.exists() || work.deleteRecursively()) { "Unable to clear generated source: $work" }
        check(work.mkdirs())
        fun go(dir: File, vararg arguments: String): String {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                workingDir(dir)
                executable(goExecutable.get())
                args(*arguments)
                environment(env)
                standardOutput = output
            }.assertNormalExitValue()
            return output.toString(Charsets.UTF_8)
        }
        check(go(work, "env", "GOVERSION").trim() == pin("go.version")) { "Go toolchain pin mismatch" }
        go(work, "mod", "init", "kiyori.invalid/mihomo-source-verification")
        fun module(name: String): File {
            val path = "github.com/metacubex/$name"
            val result = JsonSlurper().parseText(go(work, "mod", "download", "-json", "$path@${pin("$name.version")}")) as Map<*, *>
            check(result["Sum"] == pin("$name.sum")) { "$name source checksum mismatch" }
            go(work, "mod", "edit", "-require", "$path@${pin("$name.version")}")
            // A recorded module sum alone does not detect edits to an already-extracted cache.
            check(go(work, "mod", "verify").contains("all modules verified"))
            val cached = File(result["Dir"] as String)
            val destination = work.resolve(name)
            // Never patch the shared module cache: each build owns these disposable copies.
            cached.copyRecursively(destination, overwrite = false)
            destination.walkTopDown().filter { it.isFile }.forEach { it.setWritable(true) }
            return destination
        }
        val mihomo = module("mihomo")
        val sing = module("sing")
        val original = sing.resolve("common/bufio/splice_linux.go")
        check(calculateSha256(original) == pin("sing.splice.sha256")) { "Unexpected upstream splice implementation" }
        // Validate the original dependency graph before replacing the single reviewed source file.
        go(mihomo, "mod", "download")
        check(go(mihomo, "mod", "verify").contains("all modules verified"))
        Files.copy(input.resolve("splice_linux.go").toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
        // Upstream release CI populates this otherwise empty embed. Preserve the existing release's trust roots.
        val certificates = input.resolve("ca-certificates.crt")
        check(calculateSha256(certificates) == pin("ca.sha256")) { "Embedded CA bundle checksum mismatch" }
        Files.copy(certificates.toPath(), mihomo.resolve("component/ca/ca-certificates.crt").toPath(), StandardCopyOption.REPLACE_EXISTING)
        go(mihomo, "mod", "edit", "-replace", "github.com/metacubex/sing=../sing")
        val osName = System.getProperty("os.name").lowercase()
        val hostTag = when {
            osName.contains("windows") -> "windows-x86_64"
            osName.contains("linux") -> "linux-x86_64"
            osName.contains("mac") || osName.contains("darwin") -> "darwin-x86_64"
            else -> error("Unsupported Mihomo build host: $osName")
        }
        val suffix = if (osName.contains("windows")) ".cmd" else ""
        val compiler = ndkDirectory.get().asFile.resolve("toolchains/llvm/prebuilt/$hostTag/bin/aarch64-linux-android34-clang$suffix")
        check(compiler.isFile) { "Android compiler is missing: $compiler" }
        env["CC"] = "\"${compiler.absolutePath}\""
        val candidate = work.resolve("libkiyori_mihomo.so")
        go(mihomo, "build", "-mod=readonly", "-tags", "with_gvisor", "-trimpath", "-buildvcs=false",
            "-ldflags", "-w -s -buildid= -extldflags=-Wl,-z,max-page-size=16384 " +
                "-X github.com/metacubex/mihomo/constant.Version=${pin("runtime.version")} " +
                "-X github.com/metacubex/mihomo/constant.BuildTime=source-pinned",
            "-o", candidate.absolutePath, ".")
        validateAndroidArm64Elf(candidate)
        val destination = outputDirectory.get().asFile.resolve("arm64-v8a/libkiyori_mihomo.so")
        check(destination.parentFile.mkdirs() || destination.parentFile.isDirectory)
        Files.move(candidate.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        logger.lifecycle("Verified source-built Mihomo ${pin("runtime.version")}: SHA-256=${calculateSha256(destination)}")
    }

    internal fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    internal fun validateAndroidArm64Elf(file: File) {
        val bytes = file.readBytes()
        check(
            bytes.size >= 64 &&
                bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)),
        ) {
            "Mihomo runtime has no ELF magic"
        }
        check(bytes[4] == 2.toByte() && bytes[5] == 1.toByte()) {
            "Mihomo runtime must be little-endian ELF64"
        }
        fun ushort(offset: Int): Int =
            (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        fun uint(offset: Int): Long =
            (0 until 4).fold(0L) { value, index ->
                value or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8))
            }
        fun long(offset: Int): Long =
            (0 until 8).fold(0L) { value, index ->
                value or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8))
            }
        check(ushort(16) == 3) { "Mihomo runtime must be ET_DYN/PIE" }
        check(ushort(18) == 183) { "Mihomo runtime must target AArch64" }
        val programHeaderOffset = long(32).toInt()
        val entrySize = ushort(54)
        val entryCount = ushort(56)
        val alignments = buildList {
            repeat(entryCount) { index ->
                val offset = programHeaderOffset + index * entrySize
                check(offset >= 0 && offset + entrySize <= bytes.size) {
                    "Mihomo program header exceeds the ELF"
                }
                if (uint(offset) == 1L) add(long(offset + 48))
            }
        }
        check(alignments.isNotEmpty() && alignments.all { it >= 0x4000L }) {
            "Mihomo PT_LOAD alignment must be at least 0x4000: $alignments"
        }
        val text = bytes.toString(Charsets.ISO_8859_1)
        check("/system/bin/linker64" in text) { "Mihomo runtime has the wrong interpreter" }
        listOf("liblog.so", "libdl.so", "libc.so").forEach { dependency ->
            check(dependency in text) { "Mihomo runtime is missing dependency marker $dependency" }
        }
    }
}
