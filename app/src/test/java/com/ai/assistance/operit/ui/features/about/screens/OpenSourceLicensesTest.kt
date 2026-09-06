package com.ai.assistance.operit.ui.features.about.screens

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {
    @Test
    fun `inventory entries contain unique legal metadata and web addresses`() {
        val libraries = getOpenSourceLibraries()

        assertTrue(libraries.size >= 140)
        assertEquals(libraries.size, libraries.map(OpenSourceLibrary::name).toSet().size)
        assertTrue(libraries.all { library -> library.name.isNotBlank() })
        assertTrue(libraries.all { library -> library.description.isNotBlank() })
        assertTrue(libraries.all { library -> library.license.isNotBlank() })
        assertTrue(libraries.all { library -> library.website.matches(HTTP_URL_PATTERN) })
        assertTrue(libraries.all { library -> library.licenseUrl.matches(HTTP_URL_PATTERN) })
        assertEquals(
            libraries.mapNotNull(OpenSourceLibrary::ffmpegKitSourceId).size,
            libraries.mapNotNull(OpenSourceLibrary::ffmpegKitSourceId).toSet().size,
        )
        assertEquals(
            openSourceCategoryOrder.toSet(),
            libraries.map(::openSourceLibraryCategory).toSet(),
        )
    }

    @Test
    fun `inventory covers every source locked into the FFmpegKit closure`() {
        val sourceLock =
            JsonParser.parseString(
                repositoryFile("tools/ffmpegkit_native_build/source_lock.json").readText(),
            ).asJsonObject
        val lockedSourceIds =
            sourceLock
                .getAsJsonArray("sources")
                .map { element -> element.asJsonObject.get("name").asString }
                .toSet()
        val inventorySourceIds =
            getOpenSourceLibraries().mapNotNull(OpenSourceLibrary::ffmpegKitSourceId).toSet()

        assertEquals(lockedSourceIds, inventorySourceIds)
    }

    @Test
    fun `license links preserve the declared GPL and LGPL major versions`() {
        val libraries = getOpenSourceLibraries().associateBy(OpenSourceLibrary::name)

        assertEquals(
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html",
            libraries.getValue("libplacebo").licenseUrl,
        )
        assertEquals(
            "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html",
            libraries.getValue("mpv").licenseUrl,
        )
        assertEquals(
            "https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html",
            libraries.getValue("LAME").licenseUrl,
        )
        assertEquals(
            "https://www.wtfpl.net/about/",
            libraries.getValue("zimg").licenseUrl,
        )
        assertFalse(libraries.containsKey("ML Kit"))
    }

    @Test
    fun `inventory covers bundled native and direct runtime projects`() {
        val names = getOpenSourceLibraries().map(OpenSourceLibrary::name).toSet()

        assertTrue(
            names.containsAll(
                setOf(
                    "AndroidX WebKit",
                    "Apache MINA",
                    "XMLBeans",
                    "CurvesAPI",
                    "FFmpeg",
                    "Smart Exception (FFmpegKit)",
                    "KleidiAI",
                    "Saba",
                    "GLM",
                    "spdlog",
                    "stb",
                    "tinyddsloader",
                    "ncnn",
                    "OpenFST",
                    "Kaldi Native Fbank",
                    "kaldifst",
                    "Wasm Micro Runtime",
                    "KiyoriTerminalCore",
                    "mpv-android-anime4k",
                    "Operit",
                    "hikerView",
                    "OperitTerminal",
                ),
            ),
        )
    }

    private companion object {
        val HTTP_URL_PATTERN = Regex("https?://[^\\s]+")
    }

    private fun repositoryFile(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isFile == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository file not found: $relativePath")
    }
}
