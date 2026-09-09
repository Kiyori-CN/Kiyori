package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StandalonePackageFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `new import cannot overwrite another package using the same filename`() {
        val directory = temporary.newFolder("packages")
        val target = File(directory, "same.js").apply { writeText("original") }
        val source = temporary.newFile("same.js").apply { writeText("incoming") }
        val staging = prepareStandalonePackageFile(source, directory)
        assertThrows(IllegalArgumentException::class.java) {
            publishStandalonePackageFile(staging, target, null, { fail("Must not publish state") }, {})
        }
        assertEquals("original", target.readText())
        assertEquals("incoming", source.readText())
    }

    @Test fun `state publication failure restores the old source and registry callback`() {
        val directory = temporary.newFolder("packages")
        val target = File(directory, "package.js").apply { writeText("original") }
        val source = temporary.newFile("update.js").apply { writeText("incoming") }
        val staging = prepareStandalonePackageFile(source, directory)
        var state = "original"
        val error = IllegalStateException("registration failed")
        val thrown = assertThrows(IllegalStateException::class.java) {
            publishStandalonePackageFile(staging, target, target,
                { state = "incoming"; throw error }, { state = "original" })
        }
        assertSame(error, thrown)
        assertEquals("original", target.readText())
        assertEquals("original", state)
        assertEquals(listOf("package.js"), directory.listFiles()!!.map { it.name })
    }

    @Test fun `extension update publishes only the complete new file and removes backup`() {
        val directory = temporary.newFolder("packages")
        val previous = File(directory, "package.js").apply { writeText("original") }
        val target = File(directory, "package.ts")
        val source = temporary.newFile("update.ts").apply { writeText("incoming") }
        val staging = prepareStandalonePackageFile(source, directory)
        assertFalse(isStandalonePackageFileName(staging.name))
        var publications = 0
        publishStandalonePackageFile(staging, target, previous, {
            assertEquals("original", previous.readText())
            assertEquals("incoming", target.readText())
            publications++
        }, {})
        assertEquals(1, publications)
        assertEquals("incoming", target.readText())
        assertFalse(previous.exists())
        assertEquals(listOf("package.ts"), directory.listFiles()!!.map { it.name })
    }

    @Test fun `missing staging file after backup creation preserves the previous package`() {
        val directory = temporary.newFolder("packages")
        val previous = File(directory, "package.hjson").apply { writeText("original") }
        val missingStaging = File(directory, ".missing.pending")
        assertThrows(java.io.IOException::class.java) {
            publishStandalonePackageFile(missingStaging, previous, previous, { fail("Must not publish state") }, {})
        }
        assertEquals("original", previous.readText())
        assertEquals(listOf("package.hjson"), directory.listFiles()!!.map { it.name })
    }

    @Test fun `extension update failure removes the new source and preserves the old source`() {
        val directory = temporary.newFolder("packages")
        val previous = File(directory, "package.js").apply { writeText("original") }
        val target = File(directory, "package.hjson")
        val source = temporary.newFile("update.hjson").apply { writeText("incoming") }
        val staging = prepareStandalonePackageFile(source, directory)
        var restored = false
        assertThrows(IllegalStateException::class.java) {
            publishStandalonePackageFile(staging, target, previous,
                { throw IllegalStateException("registration failed") }, { restored = true })
        }
        assertTrue(restored)
        assertEquals("original", previous.readText())
        assertFalse(target.exists())
        assertEquals(listOf("package.js"), directory.listFiles()!!.map { it.name })
    }

    @Test fun `canceled preparation removes its staging file and keeps source untouched`() {
        val directory = temporary.newFolder("packages")
        val source = temporary.newFile("source.hjson").apply { writeText("original") }
        assertThrows(CancellationException::class.java) {
            prepareStandalonePackageFile(source, directory) { throw CancellationException() }
        }
        assertEquals("original", source.readText())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `publication refuses a target outside the package directory`() {
        val directory = temporary.newFolder("packages")
        val source = temporary.newFile("source.js").apply { writeText("incoming") }
        val staging = prepareStandalonePackageFile(source, directory)
        val outside = temporary.newFile("outside.js").apply { writeText("original") }
        assertThrows(IllegalArgumentException::class.java) {
            publishStandalonePackageFile(staging, outside, outside, { fail("Must not publish state") }, {})
        }
        assertEquals("original", outside.readText())
    }
}
