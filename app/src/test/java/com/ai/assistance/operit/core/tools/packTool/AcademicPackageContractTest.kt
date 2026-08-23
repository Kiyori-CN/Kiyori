package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicPackageContractTest {
    private data class AcademicPackageSpec(
        val environmentRequirements: Map<String, Boolean>,
        val toolNames: Set<String>,
        val officialMarkers: Set<String>,
    )

    private val packages =
        mapOf(
            "arxiv_search" to
                AcademicPackageSpec(
                    environmentRequirements = emptyMap(),
                    toolNames = setOf("search", "get_paper"),
                    officialMarkers =
                        setOf(
                            "https://export.arxiv.org/api/query",
                            "search_query",
                            "id_list",
                            "max_results",
                            "sortBy",
                            "sortOrder",
                            "opensearch:totalResults",
                            "application/atom+xml",
                        ),
                ),
            "crossref_search" to
                AcademicPackageSpec(
                    environmentRequirements = emptyMap(),
                    toolNames =
                        setOf(
                            "search_by_doi",
                            "search_by_keyword",
                            "search_by_author",
                            "search_by_title",
                            "search_by_issn",
                        ),
                    officialMarkers =
                        setOf(
                            "https://api.crossref.org",
                            "/works/",
                            "query.bibliographic",
                            "query.author",
                            "query.title",
                            "/journals/",
                        ),
                ),
            "pubmed_search" to
                AcademicPackageSpec(
                    environmentRequirements =
                        mapOf(
                            "PUBMED_API_KEY" to false,
                            "PUBMED_EMAIL" to false,
                        ),
                    toolNames = setOf("search", "get_articles"),
                    officialMarkers =
                        setOf(
                            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
                            "esearch.fcgi",
                            "esummary.fcgi",
                            "retmode",
                            "api_key",
                            "configuredValue(\"PUBMED_API_KEY\")",
                            "tool: \"Kiyori\"",
                            "NCBI rejected PUBMED_API_KEY as invalid",
                            "clear the optional value",
                        ),
                ),
            "semantic_scholar_search" to
                AcademicPackageSpec(
                    environmentRequirements = mapOf("SEMANTIC_SCHOLAR_API_KEY" to false),
                    toolNames = setOf("search", "get_paper"),
                    officialMarkers =
                        setOf(
                            "https://api.semanticscholar.org/graph/v1",
                            "/paper/search",
                            "/paper/",
                            "x-api-key",
                            "getEnv(\"SEMANTIC_SCHOLAR_API_KEY\")",
                            "citationCount",
                            "openAccessPdf",
                        ),
                ),
            "openalex_search" to
                AcademicPackageSpec(
                    environmentRequirements = mapOf("OPENALEX_API_KEY" to false),
                    toolNames = setOf("search", "get_work"),
                    officialMarkers =
                        setOf(
                            "https://api.openalex.org",
                            "/works",
                            "api_key",
                            "getEnv(\"OPENALEX_API_KEY\")",
                            "per_page",
                            "open_access",
                            "authorships",
                        ),
                ),
        )

    @Test
    fun `bundled Academic category contains exactly the five scholarly packages`() {
        val assets = repositoryDirectory("app/src/main/assets/packages")
        val academicIds =
            assets
                .listFiles { file -> file.isFile && file.extension == "js" }
                .orEmpty()
                .mapNotNull { file ->
                    file.nameWithoutExtension.takeIf {
                        ACADEMIC_CATEGORY_PATTERN.containsMatchIn(file.readText())
                    }
                }
                .toSet()

        assertEquals(packages.keys, academicIds)
    }

    @Test
    fun `Academic scripts use one metadata and runtime contract`() {
        packages.forEach { (packageId, spec) ->
            val source = repositoryFile("examples/$packageId.js").readText()
            val metadata = requireNotNull(metadata(source))

            assertEquals(packageId, metadata.getString("name"))
            assertEquals("Academic", metadata.getString("category"))
            assertTrue(metadata.getBoolean("enabledByDefault"))
            assertFalse(metadata.has("author"))
            assertBilingual(metadata.getJSONObject("display_name"), "$packageId display_name")
            assertBilingual(metadata.getJSONObject("description"), "$packageId description")

            val environments = metadata.optJSONArray("env")
            val requirementByName =
                if (environments == null) {
                    emptyMap()
                } else {
                    (0 until environments.length()).associate { index ->
                        val environment = environments.getJSONObject(index)
                        assertBilingual(
                            environment.getJSONObject("description"),
                            "$packageId environment ${environment.getString("name")}",
                        )
                        environment.getString("name") to environment.getBoolean("required")
                    }
                }
            assertEquals(spec.environmentRequirements, requirementByName)

            val tools = metadata.getJSONArray("tools")
            val metadataTools =
                (0 until tools.length()).map { index ->
                    val tool = tools.getJSONObject(index)
                    assertBilingual(
                        tool.getJSONObject("description"),
                        "$packageId tool ${tool.getString("name")}",
                    )
                    val parameters = tool.getJSONArray("parameters")
                    for (parameterIndex in 0 until parameters.length()) {
                        val parameter = parameters.getJSONObject(parameterIndex)
                        assertBilingual(
                            parameter.getJSONObject("description"),
                            "$packageId parameter ${parameter.getString("name")}",
                        )
                    }
                    tool.getString("name")
                }.toSet()
            val exportedTools =
                EXPORT_PATTERN.findAll(source).map { match -> match.groupValues[1] }.toSet()

            assertEquals(spec.toolNames, metadataTools)
            assertEquals(spec.toolNames, exportedTools)
            assertTrue(source.contains("OkHttp.newBuilder()"))
            assertTrue(source.contains("retryOnConnectionFailure(false)"))
            assertTrue(source.contains("console.error"))
            assertFalse(source.contains("DEFAULT_API_KEY"))
            spec.officialMarkers.forEach { marker ->
                assertTrue("$packageId is missing official marker $marker", source.contains(marker))
            }
        }
    }

    @Test
    fun `generated Academic examples and bundled assets are byte identical and whitelisted`() {
        val whitelist =
            repositoryFile("tools/example_packages/packages_whitelist.txt")
                .readLines()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()

        packages.keys.forEach { packageId ->
            val example = repositoryFile("examples/$packageId.js")
            val asset = repositoryFile("app/src/main/assets/packages/$packageId.js")
            assertTrue("Generated example missing: $packageId", example.isFile)
            assertTrue("Bundled asset missing: $packageId", asset.isFile)
            assertTrue("Academic package is not whitelisted: $packageId", "$packageId.js" in whitelist)
            assertTrue(
                "Example/asset drift: $packageId",
                example.readBytes().contentEquals(asset.readBytes()),
            )
        }
    }

    private fun assertBilingual(value: JSONObject, label: String) {
        assertTrue("$label has no zh text", value.getString("zh").isNotBlank())
        assertTrue("$label has no en text", value.getString("en").isNotBlank())
    }

    private fun metadata(source: String): JSONObject? =
        METADATA_PATTERN.find(source)?.groupValues?.get(1)?.let(::JSONObject)

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
        private val ACADEMIC_CATEGORY_PATTERN = Regex("""[\"']category[\"']\s*:\s*[\"']Academic[\"']""")
        private val METADATA_PATTERN =
            Regex("""/\*\s*METADATA\s*(.*?)\*/""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val EXPORT_PATTERN = Regex("""\bexports\.([A-Za-z0-9_]+)\s*=""")
    }
}
