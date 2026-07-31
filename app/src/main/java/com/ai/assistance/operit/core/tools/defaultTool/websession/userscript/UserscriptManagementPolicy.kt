package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.DeltaType
import java.net.URI

internal data class UserscriptUpdateDiff(
    val addedGrants: List<String>,
    val removedGrants: List<String>,
    val addedConnects: List<String>,
    val removedConnects: List<String>,
    val addedPageRules: List<String>,
    val removedPageRules: List<String>,
    val removedExclusions: List<String>,
    val sourceIdentityChanged: Boolean,
    val permissionExpanded: Boolean,
)

internal data class UserscriptUpdateCandidate(
    val scriptId: Long,
    val currentVersion: String,
    val preview: UserscriptInstallPreview,
    val diff: UserscriptUpdateDiff,
    val safeToAutoApply: Boolean,
)

internal data class UserscriptSourceDiff(
    val additions: Int,
    val deletions: Int,
    val unifiedDiff: String,
)

internal data class UserscriptEditorReview(
    val preview: UserscriptInstallPreview?,
    val syntaxError: String?,
    val updateDiff: UserscriptUpdateDiff?,
    val sourceDiff: UserscriptSourceDiff,
) {
    val canApply: Boolean
        get() =
            preview != null &&
                syntaxError == null &&
                preview.blockedReasons.isEmpty() &&
                preview.unknownGrants.isEmpty()
}

internal data class UserscriptEditableMetadata(
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val matches: String,
    val includes: String,
    val excludes: String,
    val excludeMatches: String,
    val grants: String,
    val connects: String,
    val runAt: String,
    val injectInto: String,
    val noFrames: Boolean,
)

internal object UserscriptManagementPolicy {
    fun buildUpdateCandidate(
        current: UserscriptListItem,
        preview: UserscriptInstallPreview,
    ): UserscriptUpdateCandidate {
        val diff = buildUpdateDiff(current, preview)
        return UserscriptUpdateCandidate(
            scriptId = current.id,
            currentVersion = current.version,
            preview = preview,
            diff = diff,
            safeToAutoApply =
                !diff.sourceIdentityChanged &&
                    !diff.permissionExpanded &&
                    preview.blockedReasons.isEmpty() &&
                    preview.unknownGrants.isEmpty(),
        )
    }

    fun buildUpdateDiff(
        current: UserscriptListItem,
        preview: UserscriptInstallPreview,
    ): UserscriptUpdateDiff {
        val next = preview.metadata
        val currentPageRules = current.matches + current.includes
        val nextPageRules = next.matches + next.includes
        val currentExclusions = current.excludes + current.excludeMatches
        val nextExclusions = next.excludes + next.excludeMatches
        val addedGrants = next.grants.minus(current.grants.toSet())
        val addedConnects = next.connects.minus(current.connects.toSet())
        val addedPageRules = nextPageRules.minus(currentPageRules.toSet())
        val removedExclusions = currentExclusions.minus(nextExclusions.toSet())
        val sourceIdentityChanged =
            current.name != next.name ||
                current.namespace != next.namespace ||
                sourceHostChanged(
                    expectedUrl = current.updateUrl ?: current.downloadUrl ?: current.sourceUrl,
                    actualUrl = preview.sourceUrl,
                )
        return UserscriptUpdateDiff(
            addedGrants = addedGrants,
            removedGrants = current.grants.minus(next.grants.toSet()),
            addedConnects = addedConnects,
            removedConnects = current.connects.minus(next.connects.toSet()),
            addedPageRules = addedPageRules,
            removedPageRules = currentPageRules.minus(nextPageRules.toSet()),
            removedExclusions = removedExclusions,
            sourceIdentityChanged = sourceIdentityChanged,
            permissionExpanded =
                addedGrants.isNotEmpty() ||
                    addedConnects.isNotEmpty() ||
                    addedPageRules.isNotEmpty() ||
                    removedExclusions.isNotEmpty(),
        )
    }

    fun buildSourceDiff(
        original: String,
        revised: String,
        originalLabel: String = "active.user.js",
        revisedLabel: String = "draft.user.js",
    ): UserscriptSourceDiff {
        if (original == revised) {
            return UserscriptSourceDiff(
                additions = 0,
                deletions = 0,
                unifiedDiff = "",
            )
        }
        val originalLines = original.split('\n')
        val revisedLines = revised.split('\n')
        val patch = DiffUtils.diff(originalLines, revisedLines)
        var additions = 0
        var deletions = 0
        patch.deltas.forEach { delta ->
            when (delta.type) {
                DeltaType.INSERT -> additions += delta.target.lines.size
                DeltaType.DELETE -> deletions += delta.source.lines.size
                DeltaType.CHANGE -> {
                    additions += delta.target.lines.size
                    deletions += delta.source.lines.size
                }
                DeltaType.EQUAL -> Unit
            }
        }
        val unified =
            UnifiedDiffUtils.generateUnifiedDiff(
                originalLabel,
                revisedLabel,
                originalLines,
                patch,
                3,
            ).joinToString("\n")
        return UserscriptSourceDiff(
            additions = additions,
            deletions = deletions,
            unifiedDiff = unified,
        )
    }

    private fun sourceHostChanged(
        expectedUrl: String?,
        actualUrl: String?,
    ): Boolean {
        if (expectedUrl.isNullOrBlank() || actualUrl.isNullOrBlank()) {
            return false
        }
        val expectedHost = runCatching { URI(expectedUrl).host?.lowercase() }.getOrNull()
        val actualHost = runCatching { URI(actualUrl).host?.lowercase() }.getOrNull()
        return expectedHost != null && actualHost != null && expectedHost != actualHost
    }
}

internal object UserscriptMetadataEditorPolicy {
    private val metadataBlockRegex =
        Regex(
            """(?m)^[ \t]*//[ \t]*==UserScript==\s*$([\s\S]*?)(?m)^[ \t]*//[ \t]*==/UserScript==\s*$""",
        )
    private val metadataLineRegex =
        Regex("""^[ \t]*//[ \t]*@([A-Za-z0-9:_-]+)(?:[ \t]+(.*))?$""")
    private val editableKeys =
        setOf(
            "name",
            "namespace",
            "version",
            "description",
            "match",
            "include",
            "exclude",
            "exclude-match",
            "grant",
            "connect",
            "run-at",
            "inject-into",
            "noframes",
        )

    fun read(source: String): UserscriptEditableMetadata {
        val metadata = UserscriptMetadataParser.parse(source)
        return UserscriptEditableMetadata(
            name = metadata.name,
            namespace = metadata.namespace.orEmpty(),
            version = metadata.version,
            description = metadata.description.orEmpty(),
            matches = metadata.matches.joinToString("\n"),
            includes = metadata.includes.joinToString("\n"),
            excludes = metadata.excludes.joinToString("\n"),
            excludeMatches = metadata.excludeMatches.joinToString("\n"),
            grants = metadata.grants.joinToString("\n"),
            connects = metadata.connects.joinToString("\n"),
            runAt = metadata.runAt.rawValue,
            injectInto = metadata.injectInto.rawValue,
            noFrames = metadata.noFrames,
        )
    }

    fun apply(
        source: String,
        metadata: UserscriptEditableMetadata,
    ): String {
        require(metadata.name.isNotBlank()) { "Userscript name is required" }
        require(metadata.version.isNotBlank()) { "Userscript version is required" }
        require(UserscriptRunAt.fromRaw(metadata.runAt) != UserscriptRunAt.UNSUPPORTED) {
            "Unsupported @run-at value"
        }
        require(UserscriptInjectInto.fromRaw(metadata.injectInto) != UserscriptInjectInto.UNSUPPORTED) {
            "Unsupported @inject-into value"
        }
        val match =
            metadataBlockRegex.find(source)
                ?: throw IllegalArgumentException("Missing userscript metadata block")
        val originalBlock = match.value
        val preservedLines =
            originalBlock
                .lineSequence()
                .drop(1)
                .toList()
                .dropLast(1)
                .filter { line ->
                    metadataLineRegex.find(line)?.groupValues?.get(1) !in editableKeys
                }
        val canonicalLines =
            buildList {
                add("// ==UserScript==")
                addMetadataLine("name", metadata.name)
                addMetadataLine("namespace", metadata.namespace)
                addMetadataLine("version", metadata.version)
                addMetadataLine("description", metadata.description)
                splitValues(metadata.matches).forEach { value -> addMetadataLine("match", value) }
                splitValues(metadata.includes).forEach { value -> addMetadataLine("include", value) }
                splitValues(metadata.excludes).forEach { value -> addMetadataLine("exclude", value) }
                splitValues(metadata.excludeMatches).forEach { value ->
                    addMetadataLine("exclude-match", value)
                }
                addMetadataLine("run-at", UserscriptRunAt.fromRaw(metadata.runAt).rawValue)
                addMetadataLine(
                    "inject-into",
                    UserscriptInjectInto.fromRaw(metadata.injectInto).rawValue,
                )
                if (metadata.noFrames) {
                    add("// @noframes")
                }
                splitValues(metadata.grants).forEach { value -> addMetadataLine("grant", value) }
                splitValues(metadata.connects).forEach { value -> addMetadataLine("connect", value) }
                addAll(preservedLines)
                add("// ==/UserScript==")
            }.joinToString("\n")
        return source.replaceRange(match.range, canonicalLines)
    }

    private fun MutableList<String>.addMetadataLine(
        key: String,
        value: String,
    ) {
        value.trim().takeIf(String::isNotBlank)?.let { normalized ->
            add("// @$key $normalized")
        }
    }

    private fun splitValues(raw: String): List<String> =
        raw.lineSequence().map(String::trim).filter(String::isNotBlank).distinct().toList()
}
