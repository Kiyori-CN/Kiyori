package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPackageContractTest {
    private data class CorePackageSpec(
        val displayNameZh: String,
        val environmentNames: Set<String>,
        val toolNames: Set<String>,
        val officialMarkers: Set<String>,
    )

    private val corePackages =
        mapOf(
            "tavily_search" to
                CorePackageSpec(
                    displayNameZh = "Tavily 搜索",
                    environmentNames = setOf("TAVILY_API_KEYS"),
                    toolNames =
                        setOf(
                            "search",
                            "extract",
                            "crawl",
                            "map",
                            "create_research",
                            "get_research",
                            "usage",
                            "logs",
                            "organization_usage",
                            "test_keys",
                        ),
                    officialMarkers =
                        setOf(
                            "https://api.tavily.com",
                            "\"/search\"",
                            "\"/extract\"",
                            "\"/crawl\"",
                            "\"/map\"",
                            "\"/research\"",
                            "\"/usage\"",
                            "\"/logs\"",
                            "\"/org-usage\"",
                            "safe_search",
                            "output_schema",
                        ),
                ),
            "serpapi_search" to
                CorePackageSpec(
                    displayNameZh = "SerpApi 搜索",
                    environmentNames = setOf("SERPAPI_API_KEYS"),
                    toolNames = setOf("search", "get_search", "locations", "account", "test_keys"),
                    officialMarkers =
                        setOf(
                            "https://serpapi.com",
                            "/search.json",
                            "/search.md",
                            ".json_with_pixel_position",
                            "/locations.json",
                            "/account.json",
                            "search_index",
                            "json_restrictor",
                        ),
                ),
            "brave_search" to
                CorePackageSpec(
                    displayNameZh = "Brave搜索",
                    environmentNames =
                        setOf(
                            "BRAVE_SEARCH_API_KEYS",
                            "BRAVE_ANSWERS_API_KEYS",
                            "BRAVE_SUGGEST_API_KEYS",
                            "BRAVE_SPELLCHECK_API_KEYS",
                        ),
                    toolNames =
                        setOf(
                            "web_search",
                            "llm_context",
                            "news_search",
                            "video_search",
                            "image_search",
                            "local_pois",
                            "place_search",
                            "poi_descriptions",
                            "rich_search",
                            "summarizer",
                            "answers",
                            "autosuggest",
                            "spellcheck",
                            "test_keys",
                        ),
                    officialMarkers =
                        setOf(
                            "/res/v1/web/search",
                            "/res/v1/llm/context",
                            "/res/v1/news/search",
                            "/res/v1/videos/search",
                            "/res/v1/images/search",
                            "/res/v1/local/pois",
                            "/res/v1/local/place_search",
                            "/res/v1/local/descriptions",
                            "/res/v1/web/rich",
                            "/res/v1/summarizer/",
                            "/res/v1/chat/completions",
                            "/res/v1/suggest/search",
                            "/res/v1/spellcheck/search",
                            "summary_streaming",
                        ),
                ),
            "zhipu_search" to
                CorePackageSpec(
                    displayNameZh = "智谱 搜索",
                    environmentNames = setOf("ZHIPU_SEARCH_API_KEYS"),
                    toolNames = setOf("search", "test_keys"),
                    officialMarkers =
                        setOf(
                            "https://open.bigmodel.cn/api/paas/v4/web_search",
                            "search_query",
                            "search_engine",
                            "search_intent",
                            "search_domain_filter",
                            "search_recency_filter",
                            "content_size",
                            "request_id",
                            "user_id",
                            "search_pro_sogou",
                            "search_pro_quark",
                        ),
                ),
        )

    @Test
    fun `all bundled Search packages use canonical ids and omit author`() {
        val packagesDirectory = repositoryDirectory("app/src/main/assets/packages")
        val searchPackages =
            packagesDirectory
                .listFiles { file -> file.isFile && file.extension == "js" }
                .orEmpty()
                .mapNotNull { file ->
                    val source = file.readText()
                    val metadata = metadataText(source) ?: return@mapNotNull null
                    if (!SEARCH_CATEGORY_PATTERN.containsMatchIn(metadata)) return@mapNotNull null
                    file to metadata
                }
                .sortedBy { (file, _) -> file.name }

        assertEquals(
            setOf(
                "brave_search.js",
                "duckduckgo_search.js",
                "google_search.js",
                "serpapi_search.js",
                "tavily_search.js",
                "various_search.js",
                "zhipu_search.js",
            ),
            searchPackages.map { (file, _) -> file.name }.toSet(),
        )

        searchPackages.forEach { (file, metadata) ->
            val id =
                PACKAGE_NAME_PATTERN.find(metadata)?.groupValues?.get(1)
                    ?: throw AssertionError("Package name missing in ${file.name}")
            assertTrue("Search ID must end with _search: $id", id.endsWith("_search"))
            assertEquals(file.nameWithoutExtension, id)
            assertFalse("Search package must not declare author: $id", AUTHOR_PATTERN.containsMatchIn(metadata))
        }
    }

    @Test
    fun `core Search packages expose exact metadata tools and official capabilities`() {
        corePackages.forEach { (packageId, spec) ->
            val source = repositoryFile("examples/$packageId.js").readText()
            val metadataText = requireNotNull(metadataText(source))
            val metadata = JSONObject(metadataText)

            assertEquals(packageId, metadata.getString("name"))
            assertEquals("Search", metadata.getString("category"))
            assertEquals(spec.displayNameZh, metadata.getJSONObject("display_name").getString("zh"))
            assertFalse(metadata.has("author"))

            val environments = metadata.getJSONArray("env")
            val environmentNames =
                (0 until environments.length())
                    .map { index -> environments.getJSONObject(index).getString("name") }
                    .toSet()
            assertEquals(spec.environmentNames, environmentNames)

            val tools = metadata.getJSONArray("tools")
            val metadataTools =
                (0 until tools.length())
                    .map { index -> tools.getJSONObject(index).getString("name") }
                    .toSet()
            val exportedTools =
                EXPORT_PATTERN.findAll(source).map { match -> match.groupValues[1] }.toSet()
            assertEquals(spec.toolNames, metadataTools)
            assertEquals(spec.toolNames, exportedTools)

            assertTrue(source.contains("ApiKeyRoundRobin"))
            assertTrue(source.contains("Promise.all"))
            assertFalse(source.contains("DEFAULT_API_KEY"))
            assertFalse(source.contains("author:"))
            assertFalse(source.contains("String(raw || \"\")"))
            assertFalse(source.contains("as unknown as"))
            spec.officialMarkers.forEach { marker ->
                assertTrue("$packageId is missing official capability marker $marker", source.contains(marker))
            }

            assertBilingual(metadata.getJSONObject("display_name"), "$packageId display_name")
            assertBilingual(metadata.getJSONObject("description"), "$packageId description")
            for (index in 0 until environments.length()) {
                assertBilingual(
                    environments.getJSONObject(index).getJSONObject("description"),
                    "$packageId environment $index",
                )
            }
            for (toolIndex in 0 until tools.length()) {
                val tool = tools.getJSONObject(toolIndex)
                assertBilingual(tool.getJSONObject("description"), "$packageId tool ${tool.getString("name")}")
                val parameters = tool.getJSONArray("parameters")
                for (parameterIndex in 0 until parameters.length()) {
                    val parameter = parameters.getJSONObject(parameterIndex)
                    assertBilingual(
                        parameter.getJSONObject("description"),
                        "$packageId parameter ${parameter.getString("name")}",
                    )
                }
            }
        }
    }

    @Test
    fun `generated examples and bundled Search assets are byte identical`() {
        val packageIds =
            setOf(
                "brave_search",
                "duckduckgo_search",
                "google_search",
                "serpapi_search",
                "tavily_search",
                "various_search",
                "zhipu_search",
            )
        packageIds.forEach { packageId ->
            val example = repositoryFile("examples/$packageId.js")
            val asset = repositoryFile("app/src/main/assets/packages/$packageId.js")
            assertTrue("Generated example missing: $packageId", example.isFile)
            assertTrue("Bundled asset missing: $packageId", asset.isFile)
            assertTrue("Example/asset drift: $packageId", example.readBytes().contentEquals(asset.readBytes()))
        }
    }

    @Test
    fun `Brave only requires the search product key`() {
        val source = repositoryFile("examples/brave_search.js").readText()
        val metadata = JSONObject(requireNotNull(metadataText(source)))
        val environments = metadata.getJSONArray("env")
        val requiredByName =
            (0 until environments.length()).associate { index ->
                val environment = environments.getJSONObject(index)
                environment.getString("name") to environment.getBoolean("required")
            }

        assertEquals(
            mapOf(
                "BRAVE_SEARCH_API_KEYS" to true,
                "BRAVE_ANSWERS_API_KEYS" to false,
                "BRAVE_SUGGEST_API_KEYS" to false,
                "BRAVE_SPELLCHECK_API_KEYS" to false,
            ),
            requiredByName,
        )
    }

    private fun assertBilingual(value: JSONObject, label: String) {
        assertTrue("$label has no zh text", value.getString("zh").isNotBlank())
        assertTrue("$label has no en text", value.getString("en").isNotBlank())
    }

    private fun metadataText(source: String): String? =
        METADATA_PATTERN.find(source)?.groupValues?.get(1)

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
        private val METADATA_PATTERN =
            Regex("""/\*\s*METADATA\s*(.*?)\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val SEARCH_CATEGORY_PATTERN =
            Regex("""[\"']?category[\"']?\s*:\s*[\"']Search[\"']""")
        private val PACKAGE_NAME_PATTERN =
            Regex("""[\"']?name[\"']?\s*:\s*[\"']?([A-Za-z0-9_]+)[\"']?""")
        private val AUTHOR_PATTERN = Regex("""[\"']?author[\"']?\s*:""")
        private val EXPORT_PATTERN = Regex("""\bexports\.([A-Za-z0-9_]+)\s*=""")
    }
}
