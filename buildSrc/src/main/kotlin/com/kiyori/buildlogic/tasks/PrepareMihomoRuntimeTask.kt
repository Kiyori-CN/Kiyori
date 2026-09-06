package com.kiyori.buildlogic.tasks

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class PrepareMihomoRuntimeTask : DefaultTask() {
    @get:Input
    abstract val releaseUrl: Property<String>

    @get:Input
    abstract val archiveSize: Property<Long>

    @get:Input
    abstract val archiveSha256: Property<String>

    @get:Input
    abstract val executableSize: Property<Long>

    @get:Input
    abstract val executableSha256: Property<String>

    @get:LocalState
    abstract val cacheFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val archive = cacheFile.get().asFile
        if (!isValidArchive(archive)) {
            downloadArchive(archive)
        }
        check(isValidArchive(archive)) {
            "Cached Mihomo archive failed the pinned size or SHA-256 contract: $archive"
        }

        val generatedRoot = outputDirectory.get().asFile
        check(!generatedRoot.exists() || generatedRoot.deleteRecursively()) {
            "Unable to clear generated Mihomo runtime directory: $generatedRoot"
        }
        val executable = generatedRoot.resolve("arm64-v8a/libkiyori_mihomo.so")
        check(executable.parentFile.mkdirs() || executable.parentFile.isDirectory) {
            "Unable to create generated Mihomo ABI directory: ${executable.parentFile}"
        }
        val temporary = executable.resolveSibling("${executable.name}.tmp")
        GZIPInputStream(archive.inputStream().buffered()).use { input ->
            temporary.outputStream().buffered().use(input::copyTo)
        }
        check(temporary.length() == executableSize.get()) {
            "Mihomo ELF size mismatch: expected=${executableSize.get()} actual=${temporary.length()}"
        }
        val actualSha256 = calculateSha256(temporary)
        check(actualSha256.equals(executableSha256.get(), ignoreCase = true)) {
            "Mihomo ELF SHA-256 mismatch: $actualSha256"
        }
        validateAndroidArm64Elf(temporary)
        Files.move(temporary.toPath(), executable.toPath())
        logger.lifecycle(
            "Verified Mihomo runtime: size=${executable.length()} SHA-256=${calculateSha256(executable)}"
        )
    }

    private fun isValidArchive(file: File): Boolean =
        file.isFile &&
            file.length() == archiveSize.get() &&
            calculateSha256(file).equals(archiveSha256.get(), ignoreCase = true)

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun downloadArchive(destination: File) {
        check(destination.parentFile.mkdirs() || destination.parentFile.isDirectory) {
            "Unable to create Mihomo cache directory: ${destination.parentFile}"
        }
        val temporary = destination.resolveSibling("${destination.name}.download")
        if (temporary.exists()) check(temporary.delete()) { "Unable to replace $temporary" }
        var current = URI(releaseUrl.get())
        repeat(6) { redirectIndex ->
            check(current.scheme.equals("https", ignoreCase = true)) {
                "Mihomo download must remain HTTPS: $current"
            }
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Kiyori-Mihomo-Build/1")
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> {
                        connection.inputStream.use { input ->
                            temporary.outputStream().buffered().use { output ->
                                val buffer = ByteArray(1024 * 1024)
                                var written = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    written += read
                                    check(written <= archiveSize.get()) {
                                        "Mihomo archive exceeded the pinned size"
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        check(isValidArchive(temporary)) {
                            "Downloaded Mihomo archive failed the pinned size or SHA-256 contract"
                        }
                        Files.move(
                            temporary.toPath(),
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                        return
                    }
                    in 300..399 -> {
                        val location = connection.getHeaderField("Location")
                            ?: error("Mihomo redirect has no Location header")
                        current = current.resolve(location)
                    }
                    else -> error("Mihomo download failed with HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
            check(redirectIndex < 5) { "Mihomo download exceeded the redirect limit" }
        }
        error("Mihomo download did not produce an archive")
    }

    private fun validateAndroidArm64Elf(file: File) {
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
