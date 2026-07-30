package com.ai.assistance.operit.ui.features.chat.components

import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportDialogsPathPolicyTest {
    @Test
    fun `zip entry resolver keeps nested paths inside extraction root`() {
        val root = Files.createTempDirectory("kiyori-export-root").toFile()

        try {
            val target = resolveZipEntryTarget(root, "data/flutter_assets/index.html")

            assertEquals(
                root.resolve("data/flutter_assets/index.html").canonicalFile,
                target,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IOException::class)
    fun `zip entry resolver rejects parent traversal`() {
        val root = Files.createTempDirectory("kiyori-export-root").toFile()

        try {
            resolveZipEntryTarget(root, "../outside.txt")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IOException::class)
    fun `zip entry resolver rejects windows parent traversal`() {
        val root = Files.createTempDirectory("kiyori-export-root").toFile()

        try {
            resolveZipEntryTarget(root, "..\\outside.txt")
        } finally {
            root.deleteRecursively()
        }
    }
}
