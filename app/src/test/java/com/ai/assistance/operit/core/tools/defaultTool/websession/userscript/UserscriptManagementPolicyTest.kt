package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptManagementPolicyTest {
    @Test
    fun `batch update is safe when identity and permissions are unchanged`() {
        val current =
            userscript().copy(
                grants = listOf("GM.getValue"),
                connects = listOf("api.example.com"),
                matches = listOf("https://example.com/*"),
                updateUrl = "https://updates.example.com/script.user.js",
            )
        val preview =
            preview(
                sourceUrl = "https://updates.example.com/script.user.js",
                metadata =
                    metadata().copy(
                        version = "2.0.0",
                        grants = current.grants,
                        connects = current.connects,
                        matches = current.matches,
                    ),
            )

        val candidate = UserscriptManagementPolicy.buildUpdateCandidate(current, preview)

        assertTrue(candidate.safeToAutoApply)
        assertFalse(candidate.diff.permissionExpanded)
        assertFalse(candidate.diff.sourceIdentityChanged)
    }

    @Test
    fun `new grant and broader page rule require confirmation`() {
        val current =
            userscript().copy(
                grants = listOf("GM.getValue"),
                matches = listOf("https://example.com/*"),
            )
        val preview =
            preview(
                metadata =
                    metadata().copy(
                        version = "2.0.0",
                        grants = listOf("GM.getValue", "GM.xmlHttpRequest"),
                        matches = listOf("https://example.com/*", "https://other.example/*"),
                    ),
            )

        val candidate = UserscriptManagementPolicy.buildUpdateCandidate(current, preview)

        assertFalse(candidate.safeToAutoApply)
        assertTrue(candidate.diff.permissionExpanded)
        assertEquals(listOf("GM.xmlHttpRequest"), candidate.diff.addedGrants)
        assertEquals(listOf("https://other.example/*"), candidate.diff.addedPageRules)
    }

    @Test
    fun `redirect to another source host requires confirmation`() {
        val current =
            userscript().copy(
                updateUrl = "https://updates.example.com/script.user.js",
            )
        val candidate =
            UserscriptManagementPolicy.buildUpdateCandidate(
                current,
                preview(sourceUrl = "https://cdn.other.example/script.user.js"),
            )

        assertFalse(candidate.safeToAutoApply)
        assertTrue(candidate.diff.sourceIdentityChanged)
    }

    @Test
    fun `structured metadata edit preserves unknown headers and script body`() {
        val source =
            """
            // ==UserScript==
            // @name Old name
            // @version 1.0.0
            // @custom keep-me
            // @match https://old.example/*
            // ==/UserScript==

            console.log("body");
            """.trimIndent()

        val updated =
            UserscriptMetadataEditorPolicy.apply(
                source,
                UserscriptEditableMetadata(
                    name = "New name",
                    namespace = "kiyori.test",
                    version = "2.0.0",
                    description = "Updated",
                    matches = "https://new.example/*",
                    includes = "",
                    excludes = "",
                    excludeMatches = "",
                    grants = "none",
                    connects = "",
                    runAt = "document-end",
                    injectInto = "auto",
                    noFrames = false,
                ),
            )

        assertTrue(updated.contains("// @name New name"))
        assertTrue(updated.contains("// @custom keep-me"))
        assertTrue(updated.contains("// @match https://new.example/*"))
        assertFalse(updated.contains("https://old.example/*"))
        assertTrue(updated.contains("console.log(\"body\");"))
        assertEquals("New name", UserscriptMetadataParser.parse(updated).name)
    }

    @Test
    fun `source diff reports changed lines and unified context`() {
        val diff =
            UserscriptManagementPolicy.buildSourceDiff(
                original = "one\ntwo\nthree",
                revised = "one\nTWO\nthree\nfour",
            )

        assertEquals(2, diff.additions)
        assertEquals(1, diff.deletions)
        assertTrue(diff.unifiedDiff.contains("-two"))
        assertTrue(diff.unifiedDiff.contains("+TWO"))
        assertTrue(diff.unifiedDiff.contains("+four"))
    }

    private fun preview(
        sourceUrl: String = "https://example.com/script.user.js",
        metadata: ParsedUserscriptMetadata = metadata(),
    ): UserscriptInstallPreview =
        UserscriptInstallPreview(
            metadata = metadata,
            rawSource = "",
            sourceType = UserscriptInstallSourceType.UPDATE,
            sourceUrl = sourceUrl,
            isUpdate = true,
            existingScriptId = 1L,
        )

    private fun metadata(): ParsedUserscriptMetadata =
        ParsedUserscriptMetadata(
            name = "Example",
            namespace = "kiyori.test",
            version = "1.0.0",
        )

    private fun userscript(): UserscriptListItem =
        UserscriptListItem(
            id = 1L,
            name = "Example",
            namespace = "kiyori.test",
            version = "1.0.0",
            description = null,
            sourceDisplay = null,
            enabled = true,
            unknownGrants = emptyList(),
            blockedReasons = emptyList(),
            executionWorld = null,
            unsafeWindowMode = UserscriptUnsafeWindowMode.NONE,
            runAt = UserscriptRunAt.DOCUMENT_END,
            grants = emptyList(),
            matches = emptyList(),
            includes = emptyList(),
            excludes = emptyList(),
            excludeMatches = emptyList(),
            connects = emptyList(),
            requires = emptyList(),
            resources = emptyList(),
            homepage = null,
            website = null,
            supportUrl = null,
            icons = UserscriptIconSet(),
            tags = emptyList(),
            injectInto = UserscriptInjectInto.AUTO,
            sandbox = null,
            runIn = null,
            noFrames = false,
            unwrap = false,
            webRequestRules = emptyList(),
            sourceUrl = "https://example.com/script.user.js",
            updateUrl = null,
            downloadUrl = null,
            installedAt = 1L,
            updatedAt = 1L,
        )
}
