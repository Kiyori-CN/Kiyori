package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.core.tools.javascript.OperitQuickJsEngine
import java.nio.charset.StandardCharsets
import org.json.JSONArray

internal data class LatexConversionRequest(
    val latex: String,
    val displayMode: Boolean,
)

internal object LatexMathMlConverter {
    private const val TAG = "LatexMathMlConverter"
    private const val ASSET_PATH = "js/katex.min.js"
    private const val FUNCTION_NAME = "__operitLatexToMathMlBatch"

    private val lock = Any()

    @Volatile
    private var engine: OperitQuickJsEngine? = null

    fun convertAll(context: Context, requests: List<LatexConversionRequest>): List<String> {
        if (requests.isEmpty()) return emptyList()

        return try {
            val formulas = requests.map(LatexConversionRequest::latex)
            val displayModes = requests.map(LatexConversionRequest::displayMode)
            val results =
                getEngine(context)
                    .callFunction<List<Any?>>(
                        functionName = FUNCTION_NAME,
                        argsJson =
                            JSONArray()
                                .put(JSONArray(formulas))
                                .put(JSONArray(displayModes))
                                .toString(),
                        callSite = "latex-to-mathml-batch"
                    )
                    ?: error("KaTeX returned null")

            requests.mapIndexed { index, request ->
                val mathMl = results.getOrNull(index) as? String
                if (mathMl == null) {
                    AppLogger.w(TAG, "KaTeX could not parse formula: ${request.latex}")
                    return@mapIndexed request.latex
                }
                try {
                    MathMlPlainTextConverter.convert(mathMl)
                } catch (error: Exception) {
                    AppLogger.e(TAG, "Failed to convert MathML for formula: ${request.latex}", error)
                    request.latex
                }
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to convert LaTeX batch", error)
            requests.map(LatexConversionRequest::latex)
        }
    }

    private fun getEngine(context: Context): OperitQuickJsEngine {
        engine?.let { return it }
        return synchronized(lock) {
            engine ?: createEngine(context.applicationContext).also { engine = it }
        }
    }

    private fun createEngine(context: Context): OperitQuickJsEngine {
        val source =
            context.assets.open(ASSET_PATH).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        require(source.isNotBlank()) { "KaTeX bundle is empty: $ASSET_PATH" }

        return OperitQuickJsEngine().also { quickJs ->
            quickJs.evaluate<Any?>(
                "var exports = {}; var module = { exports: exports };\n$source",
                ASSET_PATH
            )
            quickJs.evaluate<Any?>(BOOTSTRAP, "latex-to-mathml-batch.js")
        }
    }

    private val BOOTSTRAP =
        """
        (function(root) {
            var katex = root.katex || (root.module && root.module.exports);
            if (!katex || typeof katex.renderToString !== "function") {
                throw new Error("KaTeX runtime is unavailable");
            }
            root.$FUNCTION_NAME = function(formulas, displayModes) {
                return formulas.map(function(latex, index) {
                    try {
                        return katex.renderToString(String(latex), {
                            displayMode: displayModes[index] === true,
                            output: "mathml",
                            throwOnError: true,
                            strict: "ignore"
                        });
                    } catch (error) {
                        return null;
                    }
                });
            };
        })(typeof globalThis !== "undefined" ? globalThis : this);
        """.trimIndent()
}
