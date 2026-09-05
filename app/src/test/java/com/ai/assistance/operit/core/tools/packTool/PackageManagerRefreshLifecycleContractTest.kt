package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageManagerRefreshLifecycleContractTest {
    @Test
    fun `package scans reject late generations before publishing registry state`() {
        val source = repositoryFile(
            "app/src/main/java/com/ai/assistance/operit/core/tools/packTool/PackageManager.kt",
        ).readText()

        val scan = source.substringAfter("private fun scanExternalPackages(")
            .substringBefore("fun resolvePackageForDisplay(")
        assertFalse(scan.contains("externalPackageScanCache ="))
        val publication = source.substringAfter("packageScanPublication.publish(scanGeneration) {")
            .substringBefore("if (!published)")
        assertTrue(publication.contains("externalPackageScanCache = externalScan.cache"))
        assertTrue(publication.contains("assetPackageScanSnapshot = assetSnapshot"))
        assertTrue(publication.contains("applyPackageScanSnapshot(mergedSnapshot)"))
        assertTrue(source.contains("PackageScanPublicationGate(initLock)"))
        val refresh = source.substringAfter("fun getAvailablePackages(forceRefresh:")
            .substringBefore("fun getEnabledPackageNames()")
        val registration = refresh.indexOf("val scanGeneration = packageScanPublication.begin()")
        assertTrue(registration >= 0)
        assertTrue(refresh.indexOf("initializationScope.launch") > registration)
        assertTrue(refresh.contains("scanGeneration = scanGeneration"))
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) return candidate
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
