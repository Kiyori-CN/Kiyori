package com.ai.assistance.operit.core.tools.skill

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillImportFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `archive names cannot escape the skill root`() {
        listOf("../outside", "..\\outside", "/absolute", "C:\\outside", ".", "..", "", ".import_tmp_collision").forEach { name ->
            try { resolveSkillImportTarget(temporary.root, name); fail("Accepted invalid directory name") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(temporary.root.resolve("中文 skill"), resolveSkillImportTarget(temporary.root, "中文 skill"))
    }

    @Test fun `publishing a complete staging directory moves its contents`() {
        val staging = temporary.newFolder(".import_tmp_test")
        staging.resolve("SKILL.md").writeText("complete")
        val target = publishSkillImportDirectory(staging, temporary.root, "my-skill")
        assertEquals("complete", target.resolve("SKILL.md").readText())
        assertFalse(staging.exists())
    }

    @Test fun `existing target stays intact and staging is retained on failure`() {
        val target = temporary.newFolder("skill")
        target.resolve("SKILL.md").writeText("original")
        val staging = temporary.newFolder(".import_tmp_test")
        staging.resolve("SKILL.md").writeText("new")
        try { publishSkillImportDirectory(staging, temporary.root, "skill"); fail("Existing target overwritten") }
        catch (_: java.nio.file.FileAlreadyExistsException) { }
        assertEquals("original", target.resolve("SKILL.md").readText())
        assertTrue(staging.exists())
    }

    @Test fun `market update publishes marker with prepared contents`() {
        val target = temporary.newFolder("skill")
        target.resolve("SKILL.md").writeText("old")
        val staging = temporary.newFolder(".import_tmp_new")
        staging.resolve("SKILL.md").writeText("new")
        val result = publishSkillImportDirectory(staging, temporary.root, "skill", SkillImportPublication(
            existingDirectory = target,
            validateExisting = { assertEquals("old", it.resolve("SKILL.md").readText()) },
            prepareDirectory = { it.resolve("marker").writeText("new-version") },
        ))
        assertEquals("new", result.resolve("SKILL.md").readText())
        assertEquals("new-version", result.resolve("marker").readText())
        assertEquals(listOf("skill"), temporary.root.list()!!.toList())
    }

    @Test fun `marker preparation or source validation failure preserves old installation`() {
        val target = temporary.newFolder("skill")
        target.resolve("SKILL.md").writeText("old")
        val staging = temporary.newFolder(".import_tmp_new")
        staging.resolve("SKILL.md").writeText("new")
        listOf(
            SkillImportPublication(target, { error("Source changed") }, {}),
            SkillImportPublication(target, {}, { error("Marker write failed") }),
            SkillImportPublication(temporary.root.resolve("other"), {}, {}),
        ).forEach { publication ->
            try { publishSkillImportDirectory(staging, temporary.root, "skill", publication); fail("Invalid update succeeded") }
            catch (_: IllegalStateException) { }
            assertEquals("old", target.resolve("SKILL.md").readText())
            assertEquals("new", staging.resolve("SKILL.md").readText())
        }
    }

    @Test fun `deleting a skill does not follow links outside it`() {
        val root = temporary.newFolder("skill")
        val outside = temporary.newFolder("outside")
        outside.resolve("keep").writeText("keep")
        try { Files.createSymbolicLink(root.toPath().resolve("link"), outside.toPath()) }
        catch (_: java.io.IOException) { org.junit.Assume.assumeTrue("Symbolic links unavailable", false) }
        catch (_: UnsupportedOperationException) { org.junit.Assume.assumeTrue("Symbolic links unavailable", false) }
        deleteSkillTree(root)
        assertFalse(root.exists())
        assertEquals("keep", outside.resolve("keep").readText())
    }
}
