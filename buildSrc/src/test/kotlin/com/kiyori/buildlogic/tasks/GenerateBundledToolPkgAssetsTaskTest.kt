package com.kiyori.buildlogic.tasks

import java.io.File
import java.util.zip.ZipFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenerateBundledToolPkgAssetsTaskTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun task(whitelist: String): GenerateBundledToolPkgAssetsTask {
        val project = ProjectBuilder.builder().withProjectDir(temporary.root).build()
        val examples = File(temporary.root, "examples").apply { mkdirs() }
        val manifest = File(temporary.root, "packages.txt").apply { writeText(whitelist) }
        return project.tasks.register("bundle", GenerateBundledToolPkgAssetsTask::class.java).get().apply {
            whitelistFile.set(manifest)
            examplesDirectory.set(examples)
            outputDirectory.set(File(temporary.root, "generated"))
        }
    }

    private fun packageSource(name: String) {
        val directory = File(temporary.root, "examples/$name").apply { mkdirs() }
        File(directory, "manifest.json").writeText("{\"name\":\"fixture\"}")
        File(directory, "main.js").writeText("export const value = 1;")
    }

    @Test
    fun archivesAreDeterministicAndStalePackagesAreRemoved() {
        val task = task("fixture\nplain.js\n")
        packageSource("fixture")
        task.generate()
        val archive = File(temporary.root, "generated/packages/fixture.toolpkg")
        val first = archive.readBytes()
        ZipFile(archive).use { zip ->
            assertEquals(listOf("main.js", "manifest.json"), zip.entries().asSequence().map { it.name }.toList())
        }
        val stale = File(archive.parentFile, "stale.toolpkg").apply { writeText("old") }
        task.generate()
        assertArrayEquals(first, archive.readBytes())
        assertFalse(stale.exists())
    }

    @Test
    fun rejectsWhitelistTraversal() {
        val task = task("../outside\n")
        val error = assertThrows(IllegalStateException::class.java) { task.generate() }
        assertTrue(error.message.orEmpty().contains("escapes examples/"))
    }

    @Test
    fun rejectsDuplicateArchiveNamesAcrossDirectories() {
        val task = task("one/fixture\ntwo/fixture\n")
        packageSource("one/fixture")
        packageSource("two/fixture")
        val error = assertThrows(IllegalStateException::class.java) { task.generate() }
        assertTrue(error.message.orEmpty().contains("output name is duplicated"))
    }

    @Test
    fun rejectsMissingRuntime() {
        val task = task("fixture\n")
        packageSource("fixture")
        File(temporary.root, "examples/fixture/main.js").delete()
        val error = assertThrows(IllegalStateException::class.java) { task.generate() }
        assertTrue(error.message.orEmpty().contains("no built main runtime"))
    }

    @Test
    fun rejectsPrivateConfigurationInsideRuntimeAssets() {
        val task = task("fixture\n")
        packageSource("fixture")
        val assets = File(temporary.root, "examples/fixture/assets").apply { mkdirs() }
        File(assets, ".env").writeText("fixture=true")
        val error = assertThrows(IllegalStateException::class.java) { task.generate() }
        assertTrue(error.message.orEmpty().contains("blocked file"))
    }
}
