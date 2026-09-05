package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.ui.features.packages.screens.PACKAGE_CATEGORY_PRESET_LABELS
import java.io.File
import org.hjson.JsonValue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the production package catalog as one Agent-facing metadata contract. This deliberately
 * scans the whitelist instead of a hand-maintained package list so a newly bundled item cannot
 * bypass naming, bilingual copy, author, or first-install activation rules.
 */
class BuiltInPackageMetadataContractTest {
    @Test
    fun `all unpublished example package versions use 1_0_0`() {
        repositoryDirectory("examples")
            .walkTopDown()
            .filter { file -> file.isFile && file.name == "manifest.json" }
            .forEach { manifest ->
                assertEquals(
                    "${manifest.relativeTo(repositoryDirectory("examples"))} must use version 1.0.0",
                    "1.0.0",
                    JSONObject(manifest.readText()).getString("version"),
                )
            }

        listOf(
            "examples/github/package.json",
            "examples/sidebar_account_book/resources/server_runtime/package.json",
            "examples/toolpkg_wasm_demo/package.json",
            "examples/windows_control/resources/pc_agent/kiyori-pc-agent/package.json",
            "examples/windows_control/resources/pc_agent/kiyori-pc-agent/package-lock.json",
        ).forEach { relativePath ->
            assertEquals(
                "$relativePath must use version 1.0.0",
                "1.0.0",
                JSONObject(repositoryFile(relativePath).readText()).getString("version"),
            )
        }
        assertTrue(
            "Windows control package/agent handshake must use version 1.0.0",
            repositoryFile("examples/windows_control/src/packages/windows_control.ts")
                .readText()
                .contains("WINDOWS_CONTROL_PACKAGE_VERSION = \"1.0.0\""),
        )
    }

    @Test
    fun `all whitelisted ordinary scripts have normalized metadata`() {
        val whitelist = readWhitelist()
        val ordinaryItems = whitelist.filter { it.endsWith(".js") }

        ordinaryItems.forEach { item ->
            val packageId = item.removeSuffix(".js")
            val source = repositoryFile("examples/$packageId.js").readText()
            val asset = repositoryFile("app/src/main/assets/packages/$item")
            assertTrue("Missing generated example: $item", File(repositoryFile("examples/$item").path).isFile)
            assertTrue("Missing bundled asset: $item", asset.isFile)
            assertTrue("Example/asset drift: $item", source.toByteArray().contentEquals(asset.readBytes()))

            val metadata = metadata(source)
            assertTrue("$packageId metadata name is blank", metadata.getString("name").isNotBlank())
            assertTrue("${metadata.getString("name")} must use snake_case", SNAKE_CASE.matches(metadata.getString("name")))
            assertFalse("$packageId must not declare metadata author", metadata.has("author"))
            assertLocalized(metadata.get("display_name"), "$packageId display_name")
            assertAgentFacingDescription(metadata.get("description"), "$packageId description")
            assertTrue("$packageId must declare enabledByDefault", metadata.has("enabledByDefault"))
            assertTrue(
                "$packageId uses unknown category ${metadata.getString("category")}",
                PACKAGE_CATEGORY_PRESET_LABELS.contains(metadata.getString("category")),
            )

            val environment = metadata.optJSONArray("env")
            if (environment != null && environment.length() > 0) {
                assertFalse(
                    "$packageId declares environment variables and must be disabled on first install",
                    metadata.getBoolean("enabledByDefault"),
                )
            }

            val tools = metadata.optJSONArray("tools") ?: JSONArray()
            val names = mutableSetOf<String>()
            for (index in 0 until tools.length()) {
                val tool = tools.getJSONObject(index)
                val name = tool.getString("name")
                assertTrue("$packageId has non-snake-case tool $name", SNAKE_CASE.matches(name))
                assertTrue("$packageId has duplicate tool $name", names.add(name))
                assertLocalized(tool.get("description"), "$packageId tool $name")
            }

            val states = metadata.optJSONArray("states") ?: JSONArray()
            for (stateIndex in 0 until states.length()) {
                val stateTools = states.getJSONObject(stateIndex).optJSONArray("tools") ?: continue
                for (toolIndex in 0 until stateTools.length()) {
                    val tool = stateTools.getJSONObject(toolIndex)
                    val name = tool.getString("name")
                    assertTrue("$packageId state tool is non-snake-case: $name", SNAKE_CASE.matches(name))
                    assertLocalized(tool.get("description"), "$packageId state tool $name")
                }
            }
        }
    }

    @Test
    fun `all whitelisted ToolPkg manifests and subpackages have normalized metadata`() {
        readWhitelist()
            .filterNot { it.endsWith(".js") }
            .forEach { item ->
                val packageDirectory = repositoryDirectory("examples/$item")
                val manifestFile = File(packageDirectory, "manifest.json")
                assertTrue("Missing ToolPkg manifest: $item", manifestFile.isFile)
                val manifest = JSONObject(manifestFile.readText())

                assertTrue("$item must use a Kiyori ToolPkg ID", manifest.getString("toolpkg_id").startsWith("com.kiyori."))
                assertEquals("$item must use pre-release version 1.0.0", "1.0.0", manifest.getString("version"))
                assertFalse("$item must not declare manifest author", manifest.has("author"))
                assertLocalized(manifest.get("display_name"), "$item manifest display_name")
                assertAgentFacingDescription(manifest.get("description"), "$item manifest description")
                assertTrue("$item must declare enabled_by_default", manifest.has("enabled_by_default"))

                val subpackages = manifest.optJSONArray("subpackages") ?: JSONArray()
                var hasEnvironment = manifest.optJSONArray("environment")?.length()?.let { it > 0 } == true
                for (index in 0 until subpackages.length()) {
                    val subpackage = subpackages.getJSONObject(index)
                    val entry = subpackage.getString("entry")
                    val source = File(packageDirectory, entry)
                    assertTrue("Missing subpackage entry: $item/$entry", source.isFile)
                    val metadata = metadata(source.readText())
                    assertEquals(subpackage.getString("id"), metadata.getString("name"))
                    assertFalse("${item}/${metadata.getString("name")} must not declare author", metadata.has("author"))
                    assertLocalized(metadata.get("display_name"), "$item/${metadata.getString("name")} display_name")
                    assertAgentFacingDescription(
                        metadata.get("description"),
                        "$item/${metadata.getString("name")} description",
                    )
                    val category = metadata.optString("category")
                    assertTrue(
                        "$item/${metadata.getString("name")} uses unknown category $category",
                        PACKAGE_CATEGORY_PRESET_LABELS.contains(category),
                    )
                    assertTrue(
                        "${item}/${metadata.getString("name")} must declare enabledByDefault",
                        metadata.has("enabledByDefault"),
                    )
                    val environment = metadata.optJSONArray("env")
                    if (environment != null && environment.length() > 0) {
                        hasEnvironment = true
                        assertFalse(
                            "${item}/${metadata.getString("name")} declares environment variables and must be disabled",
                            metadata.getBoolean("enabledByDefault"),
                        )
                    }
                }

                if (hasEnvironment) {
                    assertFalse(
                        "$item contains environment configuration and must be disabled on first install",
                        manifest.getBoolean("enabled_by_default"),
                    )
                }
            }
    }

    @Test
    fun `normalized Search display names keep the capability separator`() {
        val brave = metadata(repositoryFile("examples/brave_search.js").readText())
        assertEquals("Brave 搜索", brave.getJSONObject("display_name").getString("zh"))
        assertFalse(repositoryFile("examples/brave_search.js").readText().contains("Brave搜索"))
    }

    @Test
    fun `bundled script surface uses Kiyori identifiers`() {
        val whitelist = readWhitelist()
        assertTrue(whitelist.contains("kiyori_editor.js"))
        assertTrue(whitelist.contains("remote_kiyori"))
        assertFalse(whitelist.contains("operit_editor.js"))
        assertFalse(whitelist.contains("remote_operit"))

        assertFalse(repositoryDirectory("examples").resolve("operit_editor.ts").exists())
        assertFalse(repositoryDirectory("examples").resolve("remote_operit").exists())
        assertFalse(repositoryDirectory("app/src/main/assets/packages").resolve("operit_editor.js").exists())
        val scripts = whitelist.flatMap { item ->
            if (item.endsWith(".js")) {
                listOf(repositoryFile("examples/$item"), repositoryFile("app/src/main/assets/packages/$item"))
            } else {
                repositoryDirectory("examples/$item").walkTopDown()
                    .onEnter { it.name !in setOf("node_modules", "build", ".git") }
                    .filter { it.isFile && it.extension in setOf("ts", "js", "json", "hjson", "md", "ps1", "bat") }
                    .toList()
            }
        }
        scripts.forEach { file ->
            val text = file.readText()
            assertFalse("${file.path} exposes legacy script env", text.contains("OPERIT_CLEAN_ON_EXIT_DIR"))
            assertFalse("${file.path} exposes legacy download env", text.contains("OPERIT_DOWNLOAD_DIR"))
            assertFalse("${file.path} exposes legacy script marker", text.contains("__OPERIT_"))
            assertFalse("${file.path} references the old remote package", text.contains("remote_operit"))
            assertFalse("${file.path} references the old editor package", text.contains("operit_editor"))
            assertFalse("${file.path} references the old PC companion", text.contains("operit-pc-agent"))
        }
    }

    @Test
    fun `file converter uses typed shell arguments and explicit terminal timeouts`() {
        val source = repositoryFile("examples/file_converter.ts").readText()
        val packageMetadata = metadata(source)
        val convertTool =
            (0 until packageMetadata.getJSONArray("tools").length())
                .map { index -> packageMetadata.getJSONArray("tools").getJSONObject(index) }
                .single { tool -> tool.getString("name") == "convert_file" }
        val options =
            (0 until convertTool.getJSONArray("parameters").length())
                .map { index -> convertTool.getJSONArray("parameters").getJSONObject(index) }
                .single { parameter -> parameter.getString("name") == "options" }

        assertEquals("array", options.getString("type"))
        assertTrue(source.contains("terminal.exec(sessionId, command, timeoutMs)"))
        assertTrue(source.contains("options?.map(shellQuote).join(' ')"))
        assertTrue(source.contains("CONVERSION_TIMEOUT_MS"))
    }

    private fun assertLocalized(value: Any, label: String) {
        val objectValue = value as? JSONObject
        assertNotNull("$label must be a bilingual object", objectValue)
        assertTrue("$label.zh is blank", objectValue!!.optString("zh").isNotBlank())
        assertTrue("$label.en is blank", objectValue.optString("en").isNotBlank())
    }

    private fun assertAgentFacingDescription(value: Any, label: String) {
        assertLocalized(value, label)
        val description = value as JSONObject
        val zh = description.getString("zh")
        val en = description.getString("en")
        assertEquals("$label.zh must be trimmed", zh.trim(), zh)
        assertEquals("$label.en must be trimmed", en.trim(), en)
        assertFalse("$label.zh must be a compact package summary", zh.contains('\n'))
        assertFalse("$label.en must be a compact package summary", en.contains('\n'))
        assertTrue("$label.zh exceeds 160 characters", zh.length <= 160)
        assertTrue("$label.en exceeds 280 characters", en.length <= 280)
        MARKETING_TERMS.forEach { term ->
            assertFalse("$label contains promotional term: $term", zh.contains(term, ignoreCase = true))
            assertFalse("$label contains promotional term: $term", en.contains(term, ignoreCase = true))
        }
    }

    private fun metadata(source: String): JSONObject {
        val block = METADATA_PATTERN.find(source)?.groupValues?.get(1)
            ?: throw AssertionError("METADATA block is missing")
        return JSONObject(JsonValue.readHjson(block).toString())
    }

    private fun readWhitelist(): List<String> =
        repositoryFile("tools/example_packages/packages_whitelist.txt")
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun repositoryDirectory(relativePath: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(5) {
            val candidate = File(current, relativePath)
            if (candidate.isDirectory) return candidate
            current = current.parentFile ?: current
        }
        throw AssertionError("Repository directory not found: $relativePath")
    }

    private fun repositoryFile(relativePath: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(5) {
            val candidate = File(current, relativePath)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: current
        }
        throw AssertionError("Repository file not found: $relativePath")
    }

    companion object {
        private val METADATA_PATTERN = Regex("""/\*\s*METADATA\s*([\s\S]*?)\*/""")
        // Keep established numeric package IDs such as 12306_ticket compatible while enforcing
        // lowercase words separated by single underscores for all package and tool names.
        private val SNAKE_CASE = Regex("[a-z0-9]+(?:_[a-z0-9]+)*")
        private val MARKETING_TERMS = listOf("强大", "高级", "全面", "效果不错", "advanced", "powerful", "comprehensive")
    }
}
