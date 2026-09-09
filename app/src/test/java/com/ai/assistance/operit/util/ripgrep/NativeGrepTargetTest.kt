package com.ai.assistance.operit.util.ripgrep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeGrepTargetTest {
    private val rootfs = "/data/user/0/com.kiyori/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu"

    private fun linux(path: String) = NativeGrepTarget.linux(path) { rootfs + it }

    @Test fun rootFileUsesHostPathAndReturnsReusableGuestPath() {
        val target = linux("/root/build_final.py")
        assertEquals("$rootfs/root/build_final.py", target.searchPath)
        assertEquals("/root/build_final.py", target.displayFilePath(target.searchPath))
    }

    @Test fun directoryMatchesKeepRelativeSuffix() {
        val target = linux("/home/project/")
        assertEquals("/home/project/src/a.py", target.displayFilePath("$rootfs/home/project/src/a.py"))
    }

    @Test fun homeExpansionReturnsAbsolutePaths() {
        assertEquals("/root", linux("~").displayPath)
        assertEquals("/root/build_final.py", linux("~/build_final.py").displayPath)
    }

    @Test fun rootAndDotSegmentsStayInsideGuestRoot() {
        assertEquals("$rootfs/", linux("/").searchPath)
        assertEquals("$rootfs/etc/hosts", linux("/../../etc/./hosts").searchPath)
        assertEquals("/root/a.py", linux("//root/tmp/../a.py").displayPath)
    }

    @Test fun rootDirectoryDoesNotDuplicateSlash() {
        assertEquals("/root/a.py", linux("/").displayFilePath("$rootfs/root/a.py"))
    }

    @Test fun mountedStorageUsesProvidedMappingAndKeepsGuestAlias() {
        val target = NativeGrepTarget.linux("/sdcard/项目") { guest ->
            guest.replaceFirst("/sdcard", "/storage/emulated/0")
        }
        assertEquals("/storage/emulated/0/项目", target.searchPath)
        assertEquals("/sdcard/项目/a [1].py", target.displayFilePath("/storage/emulated/0/项目/a [1].py"))
    }

    @Test fun spacesQuotesAndUnicodeAreData() {
        val path = "/root/项目 a'\"[1].py"
        assertEquals(path, linux(path).displayFilePath(rootfs + path))
    }

    @Test fun invalidRelativeNamedHomeAndNulPathsAreRejected() {
        listOf("file.py", "~other/file.py", "", "/root/a\u0000.py").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { linux(path) }
        }
    }

    @Test fun prefixCollisionCannotLeakHostPath() {
        val target = linux("/root/app")
        assertThrows(IllegalArgumentException::class.java) {
            target.displayFilePath("$rootfs/root/application/a.py")
        }
    }

    @Test fun androidPathsRemainUnmapped() {
        val target = NativeGrepTarget("/sdcard/Download", "/sdcard/Download")
        assertEquals("/sdcard/Download/a.py", target.displayFilePath("/sdcard/Download/a.py"))
        assertEquals("/etc/hosts", NativeGrepTarget("/", "/").displayFilePath("/etc/hosts"))
    }
}
