package com.kiyori.buildlogic.tasks

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.gradle.testfixtures.ProjectBuilder
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

    private fun task(): PrepareMihomoRuntimeTask {
        val project = ProjectBuilder.builder().withProjectDir(temporary.root).build()
        return project.tasks.register("prepareRuntime", PrepareMihomoRuntimeTask::class.java).get()
    }

    @Test
    fun acceptsAndroidPieWithLargePageAlignment() {
        val file = temporary.newFile().apply { writeBytes(elf()) }
        task().validateAndroidArm64Elf(file)
    }

    @Test
    fun rejectsWrongArchitecture() {
        val bytes = elf().apply { this[18] = 62 }
        val file = temporary.newFile().apply { writeBytes(bytes) }
        val error = assertThrows(IllegalStateException::class.java) { task().validateAndroidArm64Elf(file) }
        assertTrue(error.message.orEmpty().contains("AArch64"))
    }

    @Test
    fun rejectsNativePageMisalignment() {
        val file = temporary.newFile().apply { writeBytes(elf(0x1000)) }
        val error = assertThrows(IllegalStateException::class.java) { task().validateAndroidArm64Elf(file) }
        assertTrue(error.message.orEmpty().contains("PT_LOAD alignment"))
    }
}
