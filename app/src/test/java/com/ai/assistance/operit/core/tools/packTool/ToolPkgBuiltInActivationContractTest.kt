package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.ToolPackage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgBuiltInActivationContractTest {
    @Test
    fun `built-in container environment disables the container on first install`() {
        val result = parsePackage(isBuiltIn = true, manifestHasEnvironment = true, subpackageHasEnvironment = false)

        assertFalse(result.containerPackage.enabledByDefault)
        assertTrue(result.subpackagePackages.single().enabledByDefault)
    }

    @Test
    fun `built-in subpackage environment disables both subpackage and container`() {
        val result = parsePackage(isBuiltIn = true, manifestHasEnvironment = false, subpackageHasEnvironment = true)

        assertFalse(result.containerPackage.enabledByDefault)
        assertFalse(result.subpackagePackages.single().enabledByDefault)
    }

    @Test
    fun `external package keeps its declared activation state`() {
        val result = parsePackage(isBuiltIn = false, manifestHasEnvironment = false, subpackageHasEnvironment = true)

        assertTrue(result.containerPackage.enabledByDefault)
        assertTrue(result.subpackagePackages.single().enabledByDefault)
    }

    private fun parsePackage(
        isBuiltIn: Boolean,
        manifestHasEnvironment: Boolean,
        subpackageHasEnvironment: Boolean,
    ): ToolPkgLoadResult {
        val manifestEnvironment =
            if (manifestHasEnvironment) {
                """
                ,"environment":[{
                  "name":"HOST_TOKEN",
                  "description":{"zh":"宿主令牌","en":"Host token"},
                  "required":true,
                  "scope":"package",
                  "consumer":"host_service"
                }]
                """.trimIndent()
            } else {
                ""
            }
        val manifest =
            """
            {
              "schema_version":1,
              "toolpkg_id":"com.kiyori.activation_contract",
              "version":"1.0.0",
              "main":"main.js",
              "display_name":{"zh":"启用合同","en":"Activation Contract"},
              "description":{"zh":"测试首装启用合同。","en":"Test first-install activation."},
              "enabled_by_default":true,
              "subpackages":[{"id":"activation_contract","entry":"subpackage.js"}]
              $manifestEnvironment
            }
            """.trimIndent()
        val entries = setOf("manifest.json", "main.js", "subpackage.js")
        val content =
            mapOf(
                "manifest.json" to manifest,
                "main.js" to "ToolPkg.register({});",
                "subpackage.js" to "/* METADATA {} */",
            )
        val environment =
            if (subpackageHasEnvironment) {
                listOf(
                    EnvVar(
                        name = "SCRIPT_TOKEN",
                        description = LocalizedText.of("Script token"),
                    )
                )
            } else {
                emptyList()
            }

        return ToolPkgArchiveParser.parseToolPkgFromIndexedEntries(
            entryIndex =
                ToolPkgEntryIndex(
                    entryNames = entries,
                    entryNamesByNormalizedLowercase = entries.associateBy(String::lowercase),
                ),
            readEntryText = content::get,
            sourceType = if (isBuiltIn) ToolPkgSourceType.ASSET else ToolPkgSourceType.EXTERNAL,
            sourcePath = "activation-contract.toolpkg",
            artifactSha256 = "test",
            isBuiltIn = isBuiltIn,
            parseJsPackage = { _, _ ->
                ToolPackage(
                    name = "activation_contract",
                    description = LocalizedText.of("Activation contract"),
                    tools = emptyList(),
                    env = environment,
                    enabledByDefault = true,
                    displayName = LocalizedText.of("Activation Contract"),
                    category = "Utility",
                )
            },
            parseMainRegistration = { _, _, _, _ ->
                ToolPkgMainRegistrationParseResult.Success(ToolPkgMainRegistration())
            },
            reportPackageLoadError = { key, error ->
                throw AssertionError("Unexpected package error for $key: $error")
            },
        )
    }
}
