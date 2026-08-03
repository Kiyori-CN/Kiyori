package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.DeltaType
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

internal const val BROWSER_PAGE_SOURCE_MAX_CHARS = 1_500_000
internal const val BROWSER_PAGE_SOURCE_INLINE_AI_MAX_CHARS = 80_000
internal const val BROWSER_PAGE_SOURCE_FORMAT_MAX_CHARS = 400_000
internal const val BROWSER_PAGE_SOURCE_LONG_LINE_CHARS = 160

private const val BROWSER_PAGE_SOURCE_DETAILED_DIFF_MAX_CHARS = 300_000
private const val BROWSER_PAGE_SOURCE_DETAILED_DIFF_MAX_LINES = 8_000
private const val BROWSER_PAGE_SOURCE_DOCUMENT_TOKEN_PROPERTY =
    "__kiyoriPageSourceDocumentToken"

internal enum class BrowserPageSourceValidationFailure {
    EMPTY,
    TOO_LARGE,
    CONTAINS_NULL,
}

internal data class BrowserPageSourceCapture(
    val source: String,
    val pageUrl: String,
    val pageTitle: String,
    val documentToken: String?,
)

internal data class BrowserPageSourceCaptureResult(
    val capture: BrowserPageSourceCapture? = null,
    val errorCode: String? = null,
    val sourceLength: Int? = null,
)

internal data class BrowserPageSourceApplyResult(
    val applied: Boolean,
    val documentToken: String? = null,
    val pageUrl: String = "",
    val pageTitle: String = "",
    val errorCode: String? = null,
)

internal data class BrowserPageSourceDiff(
    val originalCharacters: Int,
    val revisedCharacters: Int,
    val originalLines: Int,
    val revisedLines: Int,
    val additions: Int?,
    val deletions: Int?,
    val unifiedDiff: String,
    val hasDetailedDiff: Boolean,
)

internal data class BrowserPageSourceEditorSnapshot(
    val sessionId: String,
    val pageUrl: String,
    val pageTitle: String,
    val source: String,
    val hasChanges: Boolean,
)

internal data class BrowserPageSourceLayoutStats(
    val lineCount: Int,
    val longestLineCharacters: Int,
    val longLineCount: Int,
)

internal fun validateBrowserPageSource(
    source: String,
): BrowserPageSourceValidationFailure? =
    when {
        source.isBlank() -> BrowserPageSourceValidationFailure.EMPTY
        source.length > BROWSER_PAGE_SOURCE_MAX_CHARS ->
            BrowserPageSourceValidationFailure.TOO_LARGE
        '\u0000' in source -> BrowserPageSourceValidationFailure.CONTAINS_NULL
        else -> null
    }

internal fun analyzeBrowserPageSourceLayout(
    source: String,
): BrowserPageSourceLayoutStats {
    if (source.isEmpty()) {
        return BrowserPageSourceLayoutStats(
            lineCount = 0,
            longestLineCharacters = 0,
            longLineCount = 0,
        )
    }

    var lineCount = 1
    var currentLineCharacters = 0
    var longestLineCharacters = 0
    var longLineCount = 0

    fun finishLine() {
        if (currentLineCharacters > longestLineCharacters) {
            longestLineCharacters = currentLineCharacters
        }
        if (currentLineCharacters > BROWSER_PAGE_SOURCE_LONG_LINE_CHARS) {
            longLineCount++
        }
        currentLineCharacters = 0
    }

    source.forEach { character ->
        if (character == '\n') {
            finishLine()
            lineCount++
        } else {
            currentLineCharacters++
        }
    }
    finishLine()

    return BrowserPageSourceLayoutStats(
        lineCount = lineCount,
        longestLineCharacters = longestLineCharacters,
        longLineCount = longLineCount,
    )
}

internal fun browserPageSourceLineStartOffset(
    source: String,
    lineNumber: Int,
): Int {
    require(lineNumber >= 1) { "lineNumber must be at least 1" }
    if (lineNumber == 1) {
        return 0
    }

    var currentLine = 1
    source.forEachIndexed { index, character ->
        if (character == '\n') {
            currentLine++
            if (currentLine == lineNumber) {
                return index + 1
            }
        }
    }
    require(currentLine == lineNumber) {
        "lineNumber $lineNumber exceeds source line count $currentLine"
    }
    return source.length
}

internal fun buildBrowserPageSourceCaptureScript(
    documentToken: String?,
): String {
    val tokenExpression = documentToken?.let(::quoteBrowserPageSourceJsString) ?: "null"
    return """
        (function() {
            try {
                const root = document.documentElement;
                if (!root) {
                    return JSON.stringify({ ok: false, error: "empty_document" });
                }
                const clone = root.cloneNode(true);
                clone.querySelectorAll("[data-operit-selection-ui='true']").forEach(function(node) {
                    node.remove();
                });
                const doctype = document.doctype;
                let doctypeText = "";
                if (doctype) {
                    doctypeText = "<!DOCTYPE " + doctype.name;
                    if (doctype.publicId) {
                        doctypeText += ' PUBLIC "' + doctype.publicId + '"';
                    }
                    if (doctype.systemId) {
                        doctypeText += ' "' + doctype.systemId + '"';
                    }
                    doctypeText += ">\n";
                }
                const source = doctypeText + clone.outerHTML;
                if (source.length > $BROWSER_PAGE_SOURCE_MAX_CHARS) {
                    return JSON.stringify({
                        ok: false,
                        error: "source_too_large",
                        sourceLength: source.length
                    });
                }
                const requestedToken = $tokenExpression;
                if (requestedToken !== null) {
                    window["$BROWSER_PAGE_SOURCE_DOCUMENT_TOKEN_PROPERTY"] = requestedToken;
                }
                return JSON.stringify({
                    ok: true,
                    source: source,
                    url: String(location.href || ""),
                    title: String(document.title || ""),
                    documentToken: requestedToken
                });
            } catch (error) {
                return JSON.stringify({
                    ok: false,
                    error: "capture_failed",
                    detail: String(error && error.message ? error.message : error)
                });
            }
        })();
    """.trimIndent()
}

internal fun parseBrowserPageSourceCaptureResult(
    rawValue: String?,
): BrowserPageSourceCaptureResult {
    val payload = decodeBrowserPageSourcePayload(rawValue)
        ?: return BrowserPageSourceCaptureResult(errorCode = "invalid_result")
    return runCatching {
        val json = JsonParser.parseString(payload).asJsonObject
        if (!json.boolean("ok")) {
            return@runCatching BrowserPageSourceCaptureResult(
                errorCode = json.string("error").ifBlank { "capture_failed" },
                sourceLength =
                    json.int("sourceLength", -1)
                        .takeIf { length -> length >= 0 },
            )
        }
        BrowserPageSourceCaptureResult(
            capture =
                BrowserPageSourceCapture(
                    source = json.string("source"),
                    pageUrl = json.string("url"),
                    pageTitle = json.string("title"),
                    documentToken =
                        json.string("documentToken")
                            .takeIf(String::isNotBlank),
                ),
        )
    }.getOrElse {
        BrowserPageSourceCaptureResult(errorCode = "invalid_result")
    }
}

internal fun buildBrowserPageSourceApplyScript(
    source: String,
    expectedDocumentToken: String,
    nextDocumentToken: String,
): String {
    val sourceExpression = quoteBrowserPageSourceJsString(source)
    val expectedTokenExpression = quoteBrowserPageSourceJsString(expectedDocumentToken)
    val nextTokenExpression = quoteBrowserPageSourceJsString(nextDocumentToken)
    return """
        (function() {
            try {
                const expectedToken = $expectedTokenExpression;
                if (window["$BROWSER_PAGE_SOURCE_DOCUMENT_TOKEN_PROPERTY"] !== expectedToken) {
                    return JSON.stringify({ ok: false, error: "document_changed" });
                }
                const source = $sourceExpression;
                if (!source.trim()) {
                    return JSON.stringify({ ok: false, error: "empty_source" });
                }
                if (source.length > $BROWSER_PAGE_SOURCE_MAX_CHARS) {
                    return JSON.stringify({ ok: false, error: "source_too_large" });
                }
                if (source.indexOf("\u0000") >= 0) {
                    return JSON.stringify({ ok: false, error: "invalid_source" });
                }
                const parsed = new DOMParser().parseFromString(source, "text/html");
                if (!parsed.documentElement) {
                    return JSON.stringify({ ok: false, error: "invalid_source" });
                }

                // Rebuild the current Document instead of navigating or creating another WebView.
                // This intentionally executes scripts from the edited source, so UI confirmation is
                // required before this script is invoked.
                document.open();
                document.write(source);
                document.close();

                const nextToken = $nextTokenExpression;
                window["$BROWSER_PAGE_SOURCE_DOCUMENT_TOKEN_PROPERTY"] = nextToken;
                return JSON.stringify({
                    ok: true,
                    documentToken: nextToken,
                    url: String(location.href || ""),
                    title: String(document.title || "")
                });
            } catch (error) {
                return JSON.stringify({
                    ok: false,
                    error: "apply_failed",
                    detail: String(error && error.message ? error.message : error)
                });
            }
        })();
    """.trimIndent()
}

internal fun parseBrowserPageSourceApplyResult(
    rawValue: String?,
): BrowserPageSourceApplyResult {
    val payload = decodeBrowserPageSourcePayload(rawValue)
        ?: return BrowserPageSourceApplyResult(
            applied = false,
            errorCode = "invalid_result",
    )
    return runCatching {
        val json = JsonParser.parseString(payload).asJsonObject
        if (!json.boolean("ok")) {
            return@runCatching BrowserPageSourceApplyResult(
                applied = false,
                errorCode = json.string("error").ifBlank { "apply_failed" },
            )
        }
        val documentToken = json.string("documentToken")
        if (documentToken.isBlank()) {
            return@runCatching BrowserPageSourceApplyResult(
                applied = false,
                errorCode = "invalid_result",
            )
        }
        BrowserPageSourceApplyResult(
            applied = true,
            documentToken = documentToken,
            pageUrl = json.string("url"),
            pageTitle = json.string("title"),
        )
    }.getOrElse {
        BrowserPageSourceApplyResult(
            applied = false,
            errorCode = "invalid_result",
        )
    }
}

internal fun buildBrowserPageSourceDiff(
    original: String,
    revised: String,
): BrowserPageSourceDiff {
    val originalLines = original.split('\n')
    val revisedLines = revised.split('\n')
    val detailed =
        original.length + revised.length <= BROWSER_PAGE_SOURCE_DETAILED_DIFF_MAX_CHARS &&
            originalLines.size <= BROWSER_PAGE_SOURCE_DETAILED_DIFF_MAX_LINES &&
            revisedLines.size <= BROWSER_PAGE_SOURCE_DETAILED_DIFF_MAX_LINES
    if (!detailed || original == revised) {
        return BrowserPageSourceDiff(
            originalCharacters = original.length,
            revisedCharacters = revised.length,
            originalLines = originalLines.size,
            revisedLines = revisedLines.size,
            additions = if (original == revised) 0 else null,
            deletions = if (original == revised) 0 else null,
            unifiedDiff = "",
            hasDetailedDiff = original == revised,
        )
    }

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
            "captured-page.html",
            "edited-page.html",
            originalLines,
            patch,
            3,
        ).joinToString("\n")
    return BrowserPageSourceDiff(
        originalCharacters = original.length,
        revisedCharacters = revised.length,
        originalLines = originalLines.size,
        revisedLines = revisedLines.size,
        additions = additions,
        deletions = deletions,
        unifiedDiff = unified,
        hasDetailedDiff = true,
    )
}

private fun decodeBrowserPageSourcePayload(
    rawValue: String?,
): String? {
    if (rawValue.isNullOrBlank() || rawValue == "null") {
        return null
    }
    return runCatching {
        val decoded = JsonParser.parseString(rawValue)
        when {
            decoded.isJsonPrimitive && decoded.asJsonPrimitive.isString ->
                decoded.asString
            decoded.isJsonObject ->
                decoded.toString()
            else ->
                null
        }
    }.getOrNull()
}

private fun quoteBrowserPageSourceJsString(
    value: String,
): String = JsonPrimitive(value).toString()

private fun JsonObject.boolean(
    name: String,
): Boolean =
    get(name)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asJsonPrimitive
        ?.takeIf { primitive -> primitive.isBoolean }
        ?.asBoolean == true

private fun JsonObject.string(
    name: String,
): String =
    get(name)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asJsonPrimitive
        ?.takeIf { primitive -> primitive.isString }
        ?.asString
        .orEmpty()

private fun JsonObject.int(
    name: String,
    defaultValue: Int,
): Int =
    get(name)
        ?.takeIf { element -> element.isJsonPrimitive }
        ?.asJsonPrimitive
        ?.takeIf { primitive -> primitive.isNumber }
        ?.asInt
        ?: defaultValue
