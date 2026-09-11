package com.kiyori.platform.storage

import java.io.File
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtifactPathRulesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val external = "/storage/emulated/0"

    @Test fun androidDefaultsAndBothLegacyInputsResolveToTheSameAbsolutePath() {
        val expected = "$external/Download/Kiyori/workspace"
        for (input in listOf(null, "", "Download/Kiyori/workspace", "Kiyori/workspace", expected, "/sdcard/Download/Kiyori/workspace")) {
            assertEquals(expected, ArtifactPathRules.androidRoot(input, external))
        }
        assertEquals("$external/Documents/我的报告", ArtifactPathRules.androidRoot("$external/Documents/我的报告", external))
        assertEquals("$external/Download/v1..2", ArtifactPathRules.androidRoot("v1..2", external))
    }

    @Test fun invalidRootsCannotEscapeOrSelectSystemDirectories() {
        for (input in listOf("../escape", "foo/../escape", "\\root", "foo\\..\\escape", "/data/local", external, "Download", "$external/Android/data/app", "ok\n", "bad\u0000name")) {
            assertThrows(input, IllegalArgumentException::class.java) { ArtifactPathRules.androidRoot(input, external) }
        }
        for (input in listOf("/", "/root", "/root/./", "workspace", "/workspace/../root", "/a\\..\\b", "/tmp", "/a\n")) {
            assertThrows(input, IllegalArgumentException::class.java) { ArtifactPathRules.linuxRoot(input) }
        }
        assertEquals("/workspace", ArtifactPathRules.linuxRoot(null))
        assertEquals("/root/项目 outputs", ArtifactPathRules.linuxRoot("/root//项目 outputs/"))
    }

    @Test fun duplicateAndConcurrentOutputsAreReservedWithoutOverwriting() {
        val base = temporary.newFolder("outputs")
        val first = ArtifactPathRules.reserveUniqueFile(base, "报告/季度总结.txt")
        first.writeText("keep this")
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..24).map { pool.submit<File> { ArtifactPathRules.reserveUniqueFile(base, "报告/季度总结.txt") } }
            val files = futures.map { it.get() }
            assertEquals(24, files.map { it.canonicalPath }.toSet().size)
            assertTrue(files.all { it.isFile })
            assertEquals("keep this", first.readText())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun relativeOutputCannotClimbOutOfItsRoot() {
        val base = temporary.newFolder("safe")
        for (path in listOf("../x", "sub/../../x", "/absolute", "~/.profile", "x\\..\\y", ".", "a\n")) {
            assertThrows(path, IllegalArgumentException::class.java) { ArtifactPathRules.reserveUniqueFile(base, path) }
        }
    }

    @Test fun terminalCommandQuotesLiteralPathsAndRequiresSuccessfulCreation() {
        assertEquals("mkdir -p -- '/work/a'\\''b \$(x)' && cd -- '/work/a'\\''b \$(x)'",
            ArtifactPathRules.initialDirectoryCommand("/work/a'b \$(x)"))
    }
}
