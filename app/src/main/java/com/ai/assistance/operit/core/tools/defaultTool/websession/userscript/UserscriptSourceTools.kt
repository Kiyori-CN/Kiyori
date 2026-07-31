package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript

import android.content.Context
import com.ai.assistance.operit.core.tools.javascript.OperitQuickJsEngine
import java.nio.charset.StandardCharsets
import org.json.JSONArray

internal class UserscriptSourceTools(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun validateSyntax(source: String): String? =
        runCatching {
            withTerser { engine ->
                engine.callFunction<Boolean>(
                    functionName = VALIDATE_FUNCTION_NAME,
                    argsJson = JSONArray().put(source).toString(),
                    callSite = "userscript/source-validate",
                )
            }
        }.exceptionOrNull()?.message

    fun format(source: String): String =
        withTerser { engine ->
            engine.callFunction<String>(
                functionName = FORMAT_FUNCTION_NAME,
                argsJson = JSONArray().put(source).toString(),
                callSite = "userscript/source-format",
            ) ?: error("Terser returned no formatted userscript source")
        }

    private fun <T> withTerser(block: (OperitQuickJsEngine) -> T): T {
        val terserSource =
            appContext.assets.open(TERSER_ASSET_PATH).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        require(terserSource.isNotBlank()) { "Terser bundle is empty: $TERSER_ASSET_PATH" }
        return OperitQuickJsEngine().use { engine ->
            engine.evaluate<Any?>(terserSource, TERSER_ASSET_PATH)
            engine.evaluate<Any?>(BOOTSTRAP, "userscript/source-tools.js")
            block(engine)
        }
    }

    private companion object {
        private const val TERSER_ASSET_PATH = "js/terser.bundle.min.js"
        private const val VALIDATE_FUNCTION_NAME = "__kiyoriUserscriptValidate"
        private const val FORMAT_FUNCTION_NAME = "__kiyoriUserscriptFormat"

        private val BOOTSTRAP =
            """
            (function(root) {
                if (!root.Terser || typeof root.Terser.minify_sync !== "function") {
                    throw new Error("Terser minify_sync is not available");
                }

                function splitSource(source) {
                    var text = String(source).replace(/\r\n?/g, "\n");
                    var match = text.match(/^[ \t]*\/\/[ \t]*==UserScript==\s*$[\s\S]*?^[ \t]*\/\/[ \t]*==\/UserScript==\s*$/m);
                    if (!match) {
                        throw new Error("Missing userscript metadata block");
                    }
                    return {
                        metadata: match[0],
                        body: text.slice(match.index + match[0].length).trim()
                    };
                }

                function parseBody(source) {
                    var split = splitSource(source);
                    if (!split.body) {
                        throw new Error("Userscript body is empty");
                    }
                    var result = root.Terser.minify_sync(split.body, {
                        ecma: 2022,
                        compress: false,
                        mangle: false,
                        format: {
                            comments: true
                        },
                        sourceMap: false
                    });
                    if (!result || typeof result.code !== "string") {
                        throw new Error("Terser did not return JavaScript output");
                    }
                    return { split: split, result: result };
                }

                root.__kiyoriUserscriptValidate = function(source) {
                    parseBody(source);
                    return true;
                };

                root.__kiyoriUserscriptFormat = function(source) {
                    var parsed = parseBody(source);
                    var formatted = root.Terser.minify_sync(parsed.split.body, {
                        ecma: 2022,
                        compress: false,
                        mangle: false,
                        format: {
                            beautify: true,
                            braces: true,
                            comments: true,
                            semicolons: true
                        },
                        sourceMap: false
                    });
                    if (!formatted || typeof formatted.code !== "string" || !formatted.code) {
                        throw new Error("Terser did not return formatted JavaScript");
                    }
                    return parsed.split.metadata + "\n\n" + formatted.code + "\n";
                };
            })(typeof globalThis !== "undefined" ? globalThis : this);
            """.trimIndent()
    }
}
