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

    @Test fun categoryDirectorySymlinkCannotRedirectArtifactsOutsideConfiguredRoot() {
        val base = temporary.newFolder("root")
        val outside = temporary.newFolder("outside")
        java.nio.file.Files.createSymbolicLink(File(base, "browser").toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactPathRules.reserveCategorizedFile(base, "browser", "report.txt")
        }
        assertFalse(File(outside, "report.txt").exists())
    }

    @Test fun terminalCommandQuotesLiteralPathsAndRequiresSuccessfulCreation() {
        assertEquals("mkdir -p -- '/work/a'\\''b \$(x)' && cd -- '/work/a'\\''b \$(x)'",
            ArtifactPathRules.initialDirectoryCommand("/work/a'b \$(x)"))
    }

    @Test fun workspaceProjectionIsEnvironmentSpecificAndDoesNotReadOverriddenDefault() {
        assertEquals("/root/project", ArtifactPathRules.projectRoot({ error("must not read invalid default") }, "linux", "/root//project/", " Linux "))
        assertEquals("/android/default", ArtifactPathRules.projectRoot({ "/android/default" }, "android", "/root/project", "linux"))
        assertEquals("/data/user/0/app/workspace", ArtifactPathRules.projectRoot({ error("unused") }, "android", "/data/user/0/app/workspace", null))
        assertEquals("/workspace", ArtifactPathRules.projectRoot({ "/workspace" }, "linux", null, null))
    }

    @Test fun uriAndNetworkWorkspacesNeverBecomeLocalPaths() {
        for (path in listOf("content://provider/tree/primary%3AFiles", "repo:workspace", "~/project", "/work/../root", "/")) {
            assertThrows(path, IllegalArgumentException::class.java) {
                ArtifactPathRules.projectRoot({ "/default" }, "android", path, "android")
            }
        }
        for (env in listOf("sftp", "smb", "ssh:test")) {
            assertThrows(IllegalArgumentException::class.java) { ArtifactPathRules.projectRoot({ "/default" }, "linux", "/remote", env) }
        }
        for (path in listOf("content://provider/tree/folder", "C:/folder", "report?.pdf")) {
            assertThrows(IllegalArgumentException::class.java) { ArtifactPathRules.androidRoot(path, external) }
        }
    }

    @Test fun primaryDocumentTreeSelectionPreservesUnicodeButRejectsOtherProvidersAndRoots() {
        assertEquals("$external/Documents/季度 报告", ArtifactPathRules.primaryTreeRoot("com.android.externalstorage.documents", "primary:Documents/季度 报告", external))
        for (id in listOf("primary:", "primary:Download", "primary:Android/data", "primary:../escape", "1234-ABCD:Reports")) {
            assertThrows(IllegalArgumentException::class.java) { ArtifactPathRules.primaryTreeRoot("com.android.externalstorage.documents", id, external) }
        }
        assertThrows(IllegalArgumentException::class.java) { ArtifactPathRules.primaryTreeRoot("cloud.provider", "primary:Reports", external) }
    }

    @Test fun virtualLinuxFilesystemsCannotBeSelected() {
        for (path in listOf("/proc", "/proc/self", "/dev/shm", "/sys/kernel", "/boot", "/run", "/lib")) {
            assertThrows(path, IllegalArgumentException::class.java) { ArtifactPathRules.linuxRoot(path) }
        }
    }
}
