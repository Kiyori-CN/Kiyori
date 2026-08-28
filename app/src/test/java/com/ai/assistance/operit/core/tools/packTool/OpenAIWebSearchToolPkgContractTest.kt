package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIHostedWebSearchContract
import com.ai.assistance.operit.core.tools.EnvVarConsumer
import com.ai.assistance.operit.core.tools.EnvVarInputType
import com.ai.assistance.operit.core.tools.EnvVarScope
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class OpenAIWebSearchToolPkgContractTest {
    @Test
    fun `manifest exposes one disabled container and one enabled search subpackage`() {
        val sourceDirectory = repositoryDirectory("examples/openai_web_search")
        val manifest =
            ToolPkgArchiveParser.parseManifest(
                File(sourceDirectory, "manifest.json").readText(),
                "manifest.json",
            )

        assertEquals(OpenAIHostedWebSearchContract.TOOLPKG_ID, manifest.toolpkgId)
        assertEquals(OpenAIHostedWebSearchContract.TOOLPKG_VERSION, manifest.version)
        assertFalse(manifest.enabledByDefault)
        assertEquals(1, manifest.subpackages.size)
        assertEquals(
            OpenAIHostedWebSearchContract.SUBPACKAGE_NAME,
            manifest.subpackages.single().id,
        )
        assertEquals(
            "dist/packages/openai_web_search.js",
            manifest.subpackages.single().entry,
        )

        val metadata = readSubpackageMetadata(sourceDirectory)
        assertEquals(OpenAIHostedWebSearchContract.SUBPACKAGE_NAME, metadata.getString("name"))
        assertTrue(metadata.getBoolean("enabledByDefault"))
        val tools = metadata.getJSONArray("tools")
        assertEquals(1, tools.length())
        assertEquals("openai_search", tools.getJSONObject(0).getString("name"))
        val parameters = tools.getJSONObject(0).getJSONArray("parameters")
        val parameterTypes =
            (0 until parameters.length())
                .associate { index ->
                    val parameter = parameters.getJSONObject(index)
                    parameter.getString("name") to parameter.getString("type")
                }
        assertEquals("array", parameterTypes.getValue("allowed_domains"))
        assertEquals("array", parameterTypes.getValue("blocked_domains"))
        assertEquals("boolean", parameterTypes.getValue("use_configured_location"))
        assertEquals(
            OpenAIHostedWebSearchContract.TOOL_NAME,
            "${metadata.getString("name")}:${tools.getJSONObject(0).getString("name")}",
        )
    }

    @Test
    fun `all environment variables are package scoped and host only`() {
        val sourceDirectory = repositoryDirectory("examples/openai_web_search")
        val manifest =
            ToolPkgArchiveParser.parseManifest(
                File(sourceDirectory, "manifest.json").readText(),
                "manifest.json",
            )

        assertEquals(20, manifest.environment.size)
        assertEquals(
            OpenAIHostedWebSearchContract.ENVIRONMENT_NAMES.toSet(),
            manifest.environment.map { environment -> environment.name }.toSet(),
        )
        manifest.environment.forEach { environment ->
            assertEquals(EnvVarScope.PACKAGE, environment.scope)
            assertEquals(EnvVarConsumer.HOST_SERVICE, environment.consumer)
        }

        val apiKey =
            manifest.environment.single { environment ->
                environment.name == OpenAIHostedWebSearchContract.ENV_API_KEY
            }
        assertTrue(apiKey.sensitive)
        assertEquals(EnvVarInputType.PASSWORD, apiKey.inputType)
        assertFalse(apiKey.required)
        assertEquals(null, apiKey.defaultValue)
    }

    @Test
    fun `runtime JavaScript can only call the dedicated host service`() {
        val distDirectory = repositoryDirectory("examples/openai_web_search/dist")
        val runtimeJavaScript =
            distDirectory
                .walkTopDown()
                .filter { file -> file.isFile && file.extension.equals("js", ignoreCase = true) }
                .sortedBy(File::getPath)
                .joinToString("\n") { file -> file.readText() }

        assertTrue(runtimeJavaScript.contains("ToolPkg.services.openAIWebSearch"))
        assertFalse(runtimeJavaScript.contains("parseDomainArray"))
        assertFalse(runtimeJavaScript.contains("parseBoolean"))
        assertFalse(runtimeJavaScript.contains("[openai_web_search] search failed"))
        listOf(
            "getEnv(",
            "fetch(",
            "XMLHttpRequest",
            "Java.type",
            "Java.com",
            "registerAiProvider",
        ).forEach { forbidden ->
            assertFalse("Unexpected JavaScript capability: $forbidden", runtimeJavaScript.contains(forbidden))
        }
    }

    @Test
    fun `settings runtime exposes compatibility states and visible error codes`() {
        val sharedSource =
            File(
                repositoryDirectory("examples/openai_web_search"),
                "src/shared.ts",
            ).readText()
        val settingsSource =
            File(
                repositoryDirectory("examples/openai_web_search"),
                "src/ui/index.ui.ts",
            ).readText()
        val bridgeSource =
            repositoryFile(
                "app/src/main/java/com/ai/assistance/operit/core/tools/javascript/" +
                    "JsToolPkgRegistration.kt"
            ).readText()

        listOf("missing", "stale", "valid", "failed").forEach { state ->
            assertTrue(settingsSource.contains("\"$state\""))
        }
        assertTrue(settingsSource.contains("await loadStatus();"))
        assertTrue(settingsSource.contains("compatibility.error_code"))
        assertTrue(settingsSource.contains("compatibility.provider_error_type"))
        assertTrue(settingsSource.contains("compatibility.provider_request_id"))
        assertTrue(settingsSource.contains("status.api_key_revision"))
        assertTrue(settingsSource.contains("status.auth_scheme_present"))
        assertTrue(settingsSource.contains("status.auth_scheme_kind"))
        assertTrue(sharedSource.contains("errorWithCode.code"))
        assertTrue(bridgeSource.contains("message = '[' + code + '] ' + message;"))
        assertTrue(bridgeSource.contains("error.sourceDiagnostics"))
    }

    @Test
    fun `builder and scanner accept a credential free runtime only archive`() {
        val root = Files.createTempDirectory("openai-web-search-toolpkg").toFile()
        try {
            val cacheDirectory = File(root, "cache").apply { mkdirs() }
            val context = mock<Context> { on { cacheDir } doReturn cacheDirectory }
            val result =
                ToolPkgArtifactBuilder(context)
                    .build(repositoryDirectory("examples/openai_web_search"))

            assertTrue(result.report.findings.toString(), result.report.accepted)
            assertEquals(OpenAIHostedWebSearchContract.TOOLPKG_ID, result.report.toolPkgId)
            assertEquals(
                OpenAIHostedWebSearchContract.TOOLPKG_VERSION,
                result.report.toolPkgVersion,
            )

            ZipFile(result.archiveFile).use { archive ->
                val entries =
                    archive
                        .entries()
                        .asSequence()
                        .filterNot { entry -> entry.isDirectory }
                        .map { entry -> entry.name }
                        .toSet()
                assertEquals(
                    setOf(
                        "manifest.json",
                        "dist/main.js",
                        "dist/shared.js",
                        "dist/packages/openai_web_search.js",
                        "dist/ui/index.ui.js",
                    ),
                    entries,
                )
                entries.forEach { entry ->
                    val normalized = entry.lowercase()
                    assertFalse(normalized.startsWith("src/"))
                    assertFalse(normalized == ".env" || normalized.startsWith(".env."))
                    assertFalse(normalized.endsWith(".ts"))
                }
                val archiveText =
                    entries
                        .filter { entry -> entry.endsWith(".json") || entry.endsWith(".js") }
                        .sorted()
                        .joinToString("\n") { entry ->
                            archive.getInputStream(archive.getEntry(entry)).use { input ->
                                input.readBytes().toString(StandardCharsets.UTF_8)
                            }
                        }
                assertFalse(archiveText.contains("OPENAI_WEB_SEARCH_API_KEY="))
                assertFalse(archiveText.contains("\"api_key\""))
                assertFalse(archiveText.contains("\"Authorization\":\"Bearer "))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun readSubpackageMetadata(sourceDirectory: File): JSONObject {
        val source =
            File(sourceDirectory, "dist/packages/openai_web_search.js")
                .readText()
        val metadata =
            Regex(
                """/\*\s*METADATA\s*(\{.*?})\s*\*/""",
                setOf(RegexOption.DOT_MATCHES_ALL),
            ).find(source)
                ?.groupValues
                ?.get(1)
                ?: throw AssertionError("OpenAI Web Search subpackage metadata not found")
        return JSONObject(metadata)
    }

    private fun repositoryDirectory(relativePath: String): File {
        var current: File? =
            File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(4) {
            val candidate = current?.let { directory -> File(directory, relativePath) }
            if (candidate?.isDirectory == true) {
                return candidate
            }
            current = current?.parentFile
        }
        throw AssertionError("Repository directory not found: $relativePath")
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
