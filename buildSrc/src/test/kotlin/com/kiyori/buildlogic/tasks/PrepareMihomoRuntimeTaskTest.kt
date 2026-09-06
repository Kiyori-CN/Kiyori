package com.kiyori.buildlogic.tasks

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrepareMihomoRuntimeTaskTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun elf(alignment: Long = 0x4000): ByteArray {
        // Minimal ELF headers exercise packaging validation without invoking a native executable.
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 2, 1))
        buffer.putShort(16, 3)
        buffer.putShort(18, 183)
        buffer.putLong(32, 64)
        buffer.putShort(54, 56)
        buffer.putShort(56, 1)
        buffer.putInt(64, 1)
        buffer.putLong(112, alignment)
        buffer.position(120)
        buffer.put("/system/bin/linker64\u0000liblog.so\u0000libdl.so\u0000libc.so\u0000".toByteArray())
        return buffer.array()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun task(payload: ByteArray): PrepareMihomoRuntimeTask {
        val project = ProjectBuilder.builder().withProjectDir(temporary.root).build()
        val archive = File(temporary.root, "runtime.gz")
        GZIPOutputStream(archive.outputStream()).use { it.write(payload) }
        return project.tasks.register("prepareRuntime", PrepareMihomoRuntimeTask::class.java).get().apply {
            releaseUrl.set("https://unused.invalid/runtime.gz")
            archiveSize.set(archive.length())
            archiveSha256.set(sha256(archive.readBytes()))
            executableSize.set(payload.size.toLong())
            executableSha256.set(sha256(payload))
            cacheFile.set(archive)
            outputDirectory.set(File(temporary.root, "generated"))
        }
    }

    @Test
    fun preparesPinnedCachedRuntimeWithoutNetwork() {
        val payload = elf()
        task(payload).prepare()
        assertArrayEquals(payload, File(temporary.root, "generated/arm64-v8a/libkiyori_mihomo.so").readBytes())
    }

    @Test
    fun rejectsIncorrectExecutableHashBeforePublishing() {
        val task = task(elf()).apply { executableSha256.set("0".repeat(64)) }
        val error = assertThrows(IllegalStateException::class.java) { task.prepare() }
        assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
        assertFalse(File(temporary.root, "generated/arm64-v8a/libkiyori_mihomo.so").exists())
    }

    @Test
    fun rejectsNativePageMisalignment() {
        val task = task(elf(0x1000))
        val error = assertThrows(IllegalStateException::class.java) { task.prepare() }
        assertTrue(error.message.orEmpty().contains("PT_LOAD alignment"))
        assertFalse(File(temporary.root, "generated/arm64-v8a/libkiyori_mihomo.so").exists())
    }
}
