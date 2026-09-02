package com.ai.assistance.operit.ui.common.displays

import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.util.AppLogger

internal const val JLATEXMATH_ANDROID_VERSION = "0.2.0"
internal const val JLATEXMATH_CORE_VERSION = "1.0.3"
internal const val JLATEXMATH_BACKEND_DESCRIPTION =
    "JLatexMath Android $JLATEXMATH_ANDROID_VERSION (core $JLATEXMATH_CORE_VERSION)"

internal data class PreparedLatexFormula(
    val original: String,
    val rendered: String,
    val transformations: List<String>,
)

internal class LatexCompatibilityException(
    message: String,
    val sourceIndex: Int,
    val command: String? = null,
) : IllegalArgumentException("$message at source index $sourceIndex")

internal data class LatexFailureDetails(
    val errorType: String,
    val errorMessage: String,
    val command: String?,
    val line: Int?,
    val column: Int?,
    val sourceIndex: Int?,
)

private val SUPPORTED_DISPLAY_ENVIRONMENT_PATTERN =
    Regex("\\\\begin\\u007b(equation\\*?|displaymath|align\\*?)\\u007d")

private fun unwrapSupportedDisplayEnvironment(source: String): Pair<String, List<String>> {
    val firstContent = source.indexOfFirst { !it.isWhitespace() }
    if (firstContent < 0) return source to emptyList()
    val lastContent = source.indexOfLast { !it.isWhitespace() }
    val core = source.substring(firstContent, lastContent + 1)
    val begin = SUPPORTED_DISPLAY_ENVIRONMENT_PATTERN.matchAt(core, 0) ?: return source to emptyList()
    val environment = begin.groupValues[1]
    val endToken = "\\end{$environment}"
    val endStart = core.lastIndexOf(endToken)
    if (endStart < begin.range.last + 1 || core.substring(endStart + endToken.length).any { !it.isWhitespace() }) {
        // Leave incomplete or non-wrapper environments untouched so the backend
        // can report the original failure instead of silently dropping content.
        return source to emptyList()
    }

    val body = core.substring(begin.range.last + 1, endStart)
    val unwrapped = source.substring(0, firstContent) + body + source.substring(lastContent + 1)
    return unwrapped to listOf("environment:$environment@$firstContent")
}

/**
 * 将当前 JLaTeXMath 缺失的命令和受限语法转换为同一后端可解析的标准 LaTeX。
 *
 * 这里按字符扫描公式主体，避免全局删除反斜杠或误改代码区以外的 Markdown 内容。
 */
internal fun prepareLatexForJLatexMath(source: String): PreparedLatexFormula {
    val (environmentPreparedSource, environmentTransformations) = unwrapSupportedDisplayEnvironment(source)
    val rendered = StringBuilder(environmentPreparedSource.length)
    val transformations = environmentTransformations.toMutableList()
    var index = 0

    while (index < environmentPreparedSource.length) {
        if (environmentPreparedSource[index] != '\\') {
            rendered.append(environmentPreparedSource[index])
            index++
            continue
        }

        if (index + 1 >= environmentPreparedSource.length) {
            rendered.append('\\')
            index++
            continue
        }

        when (environmentPreparedSource[index + 1]) {
            ' ', '\t' -> {
                // JLaTeXMath 1.0.3 会把反斜杠后的空白读成未知单字符命令。
                // 改为后端已支持的标准数学间距，既保留可见空白，也避免整块公式失败。
                rendered.append("\\;")
                transformations += "control-space@$index"
                index += 2
                continue
            }

            '\n' -> {
                // TeX 中“反斜杠 + 行尾”具有控制空格语义；用已支持的间距命令近似，
                // 同时保留物理换行，便于后端错误位置和开发日志继续对应源码结构。
                rendered.append("\\;\n")
                transformations += "control-line-break@$index"
                index += 2
                continue
            }

            '\r' -> {
                val consumed = if (index + 2 < environmentPreparedSource.length && environmentPreparedSource[index + 2] == '\n') 3 else 2
                rendered.append("\\;\n")
                transformations += "control-line-break@$index"
                index += consumed
                continue
            }
        }

        if (!environmentPreparedSource[index + 1].isAsciiLetter()) {
            rendered.append('\\').append(environmentPreparedSource[index + 1])
            index += 2
            continue
        }

        var commandEnd = index + 2
        while (commandEnd < environmentPreparedSource.length && environmentPreparedSource[commandEnd].isAsciiLetter()) {
            commandEnd++
        }
        val command = environmentPreparedSource.substring(index + 1, commandEnd)

        val delimiterExpansion =
            when (command) {
                "lvert" -> "\\mathopen{\\vert}"
                "rvert" -> "\\mathclose{\\vert}"
                "lVert" -> "\\mathopen{\\Vert}"
                "rVert" -> "\\mathclose{\\Vert}"
                else -> null
            }
        if (delimiterExpansion != null) {
            rendered.append(delimiterExpansion)
            transformations += "$command@$index"
            index = commandEnd
            continue
        }

        if (command == "ce") {
            var argumentStart = commandEnd
            while (argumentStart < environmentPreparedSource.length && environmentPreparedSource[argumentStart].isWhitespace()) {
                argumentStart++
            }
            if (argumentStart >= environmentPreparedSource.length || environmentPreparedSource[argumentStart] != '{') {
                throw LatexCompatibilityException(
                    message = "\\ce must be followed by a braced chemical expression",
                    sourceIndex = index,
                    command = CHEMISTRY_COMMAND,
                )
            }

            val group =
                readBalancedGroup(
                    source = environmentPreparedSource,
                    openingIndex = argumentStart,
                    opening = '{',
                    closing = '}',
                    diagnosticCommand = CHEMISTRY_COMMAND,
                )
            rendered.append(
                convertChemicalEquationToLatex(
                    source = group.content,
                    sourceOffset = argumentStart + 1,
                )
            )
            transformations += "ce@$index"
            index = group.endExclusive
            continue
        }

        rendered.append(environmentPreparedSource, index, commandEnd)
        index = commandEnd
    }

    return PreparedLatexFormula(
        original = source,
        rendered = rendered.toString(),
        transformations = transformations,
    )
}

internal fun diagnoseLatexFailure(
    formula: String,
    error: Throwable,
): LatexFailureDetails {
    val messageChain = throwableMessageChain(error)
    val compatibilityError =
        generateSequence(error) { it.cause }
            .filterIsInstance<LatexCompatibilityException>()
            .firstOrNull()
    val parserPosition =
        PARSER_POSITION_PATTERN.find(messageChain)?.let { match ->
            match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
        }
    val compatibilityIndex = compatibilityError?.sourceIndex
    val commandFromMessage =
        UNKNOWN_COMMAND_PATTERN.find(messageChain)?.groupValues?.getOrNull(1)
            ?: PROBLEM_COMMAND_PATTERN.find(messageChain)?.groupValues?.getOrNull(1)
    val commandIndex =
        compatibilityIndex
            ?: commandFromMessage
                ?.takeIf { it.isNotEmpty() }
                ?.let { formula.indexOf("\\$it").takeIf { found -> found >= 0 } }
    val derivedPosition = commandIndex?.let { lineAndColumnAt(formula, it) }
    val command =
        compatibilityError?.command
            ?: commandFromMessage?.let { "\\$it" }
            ?: commandIndex?.let { commandAt(formula, it) }

    return LatexFailureDetails(
        errorType = error::class.java.name,
        errorMessage = messageChain.ifBlank { error::class.java.simpleName },
        command = command,
        line = parserPosition?.first ?: derivedPosition?.first,
        column = parserPosition?.second ?: derivedPosition?.second,
        sourceIndex = commandIndex,
    )
}

internal fun formatLatexFailureLog(
    surface: String,
    originalFormula: String,
    preparedFormula: PreparedLatexFormula?,
    error: Throwable,
    includeFormulaContent: Boolean,
): String {
    val inspectedFormula = preparedFormula?.rendered ?: originalFormula
    val details = diagnoseLatexFailure(inspectedFormula, error)

    return buildString {
        append("LaTeX render failed")
        append(" | surface=").append(surface)
        append(" | backend=").append(JLATEXMATH_BACKEND_DESCRIPTION)
        append(" | errorType=").append(details.errorType)
        append(" | error=").append(details.errorMessage)
        details.command?.let { append(" | command=").append(it) }
        if (details.line != null && details.column != null) {
            append(" | position=").append(details.line).append(':').append(details.column)
        }
        details.sourceIndex?.let { append(" | sourceIndex=").append(it) }
        append(" | positionSource=")
            .append(if (preparedFormula == null) "original" else "preprocessed")

        if (includeFormulaContent) {
            append("\noriginal=").append(originalFormula)
            append("\npreprocessed=").append(preparedFormula?.rendered ?: "<preprocessing failed>")
            append("\ntransformations=")
                .append(
                    preparedFormula
                        ?.transformations
                        ?.takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?: "<none>"
                )
        }
    }
}

internal fun logLatexRenderFailure(
    surface: String,
    originalFormula: String,
    preparedFormula: PreparedLatexFormula?,
    error: Throwable,
) {
    AppLogger.w(
        "LatexFormulaSupport",
        formatLatexFailureLog(
            surface = surface,
            originalFormula = originalFormula,
            preparedFormula = preparedFormula,
            error = error,
            includeFormulaContent = BuildConfig.DEBUG,
        ),
        error,
    )
}

private data class BalancedGroup(
    val content: String,
    val endExclusive: Int,
)

private enum class ChemicalTokenKind {
    SPECIES,
    PLUS,
    ARROW,
    DOWN,
    UP,
    DOT,
}

private data class ChemicalToken(
    val kind: ChemicalTokenKind,
    val text: String,
    val sourceIndex: Int,
)

private const val CHEMISTRY_COMMAND = "\\ce"

private val CHEMICAL_ARROWS =
    linkedMapOf(
        "<=>" to "\\rightleftharpoons",
        "<->" to "\\leftrightarrow",
        "->" to "\\rightarrow",
        "<-" to "\\leftarrow",
        "=>" to "\\Rightarrow",
        "<=" to "\\Leftarrow",
    )

private val PARSER_POSITION_PATTERN = Regex("""at position\s+(\d+):(\d+)""")
private val UNKNOWN_COMMAND_PATTERN =
    Regex("""Unknown symbol or command or predefined TeXFormula:\s*'([^']+)'""")
private val PROBLEM_COMMAND_PATTERN = Regex("""Problem with command\s+\\?([A-Za-z]+)""")
private val CHEMICAL_STATE_PATTERN = Regex("""\([a-z]{1,8}\)""")

private fun convertChemicalEquationToLatex(
    source: String,
    sourceOffset: Int,
): String {
    if (source.isBlank()) {
        throw LatexCompatibilityException(
            message = "\\ce chemical expression cannot be empty",
            sourceIndex = sourceOffset,
            command = CHEMISTRY_COMMAND,
        )
    }

    val tokens = tokenizeChemicalEquation(source, sourceOffset)
    return buildString {
        var previousKind: ChemicalTokenKind? = null

        tokens.forEach { token ->
            when (token.kind) {
                ChemicalTokenKind.SPECIES -> {
                    if (previousKind == ChemicalTokenKind.SPECIES) {
                        append("\\,")
                    }
                    append(renderChemicalSpecies(token.text, token.sourceIndex))
                }

                ChemicalTokenKind.PLUS -> append("\\; + \\;")
                ChemicalTokenKind.ARROW -> {
                    append("\\;")
                    append(requireNotNull(CHEMICAL_ARROWS[token.text]))
                    append("\\;")
                }

                ChemicalTokenKind.DOWN -> append("\\,\\downarrow")
                ChemicalTokenKind.UP -> append("\\,\\uparrow")
                ChemicalTokenKind.DOT -> append("\\,\\cdot\\,")
            }
            previousKind = token.kind
        }
    }
}

private fun tokenizeChemicalEquation(
    source: String,
    sourceOffset: Int,
): List<ChemicalToken> {
    val tokens = mutableListOf<ChemicalToken>()
    var index = 0

    while (index < source.length) {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
        if (index >= source.length) break

        val arrow = matchChemicalArrow(source, index)
        if (arrow != null) {
            tokens +=
                ChemicalToken(
                    kind = ChemicalTokenKind.ARROW,
                    text = arrow,
                    sourceIndex = sourceOffset + index,
                )
            index += arrow.length
            continue
        }

        when (source[index]) {
            '+' -> {
                tokens +=
                    ChemicalToken(
                        kind = ChemicalTokenKind.PLUS,
                        text = "+",
                        sourceIndex = sourceOffset + index,
                    )
                index++
                continue
            }

            '·', '.' -> {
                tokens +=
                    ChemicalToken(
                        kind = ChemicalTokenKind.DOT,
                        text = source[index].toString(),
                        sourceIndex = sourceOffset + index,
                    )
                index++
                continue
            }
        }

        val tokenStart = index
        var braceDepth = 0
        var parenthesisDepth = 0
        var bracketDepth = 0

        while (index < source.length) {
            if (
                index > tokenStart &&
                    braceDepth == 0 &&
                    parenthesisDepth == 0 &&
                    bracketDepth == 0
            ) {
                if (source[index].isWhitespace() || matchChemicalArrow(source, index) != null) {
                    break
                }
                if (source[index] == '+' && isReactionPlus(source, index)) {
                    break
                }
            }

            when (source[index]) {
                '\\' -> {
                    index = skipLatexCommand(source, index)
                    continue
                }

                '{' -> braceDepth++
                '}' -> {
                    braceDepth--
                    if (braceDepth < 0) {
                        throw LatexCompatibilityException(
                            message = "Unexpected closing brace in \\ce expression",
                            sourceIndex = sourceOffset + index,
                            command = CHEMISTRY_COMMAND,
                        )
                    }
                }

                '(' -> parenthesisDepth++
                ')' -> {
                    parenthesisDepth--
                    if (parenthesisDepth < 0) {
                        throw LatexCompatibilityException(
                            message = "Unexpected closing parenthesis in \\ce expression",
                            sourceIndex = sourceOffset + index,
                            command = CHEMISTRY_COMMAND,
                        )
                    }
                }

                '[' -> bracketDepth++
                ']' -> {
                    bracketDepth--
                    if (bracketDepth < 0) {
                        throw LatexCompatibilityException(
                            message = "Unexpected closing bracket in \\ce expression",
                            sourceIndex = sourceOffset + index,
                            command = CHEMISTRY_COMMAND,
                        )
                    }
                }
            }
            index++
        }

        if (braceDepth != 0 || parenthesisDepth != 0 || bracketDepth != 0) {
            throw LatexCompatibilityException(
                message = "Unclosed group in \\ce expression",
                sourceIndex = sourceOffset + tokenStart,
                command = CHEMISTRY_COMMAND,
            )
        }

        val text = source.substring(tokenStart, index)
        val kind =
            when (text) {
                "v" -> ChemicalTokenKind.DOWN
                "^" -> ChemicalTokenKind.UP
                else -> ChemicalTokenKind.SPECIES
            }
        tokens +=
            ChemicalToken(
                kind = kind,
                text = text,
                sourceIndex = sourceOffset + tokenStart,
            )
    }

    return tokens
}

private fun renderChemicalSpecies(
    source: String,
    sourceOffset: Int,
): String {
    if (CHEMICAL_STATE_PATTERN.matches(source)) {
        return "\\mathrm{$source}"
    }

    val rendered = StringBuilder(source.length * 2)
    var index = 0

    while (index < source.length && source[index].isDigit()) {
        rendered.append(source[index])
        index++
    }

    while (index < source.length) {
        when (val char = source[index]) {
            '\\' -> {
                if (index + 1 < source.length) {
                    when (source[index + 1]) {
                        ' ', '\t' -> {
                            rendered.append("\\;")
                            index += 2
                            continue
                        }

                        '\n' -> {
                            rendered.append("\\;\n")
                            index += 2
                            continue
                        }

                        '\r' -> {
                            rendered.append("\\;\n")
                            index +=
                                if (index + 2 < source.length && source[index + 2] == '\n') {
                                    3
                                } else {
                                    2
                                }
                            continue
                        }
                    }
                }

                val commandEnd = skipLatexCommand(source, index)
                rendered.append(source, index, commandEnd)
                index = commandEnd
                while (index < source.length && source[index] == '{') {
                    val group =
                        readBalancedGroup(
                            source = source,
                            openingIndex = index,
                            opening = '{',
                            closing = '}',
                            sourceIndexOffset = sourceOffset,
                            diagnosticCommand = CHEMISTRY_COMMAND,
                        )
                    rendered.append(source, index, group.endExclusive)
                    index = group.endExclusive
                }
            }

            '^', '_' -> {
                rendered.append(char)
                index++
                if (index >= source.length) {
                    throw LatexCompatibilityException(
                        message = "Chemical script marker must be followed by a value",
                        sourceIndex = sourceOffset + index - 1,
                        command = CHEMISTRY_COMMAND,
                    )
                }

                if (source[index] == '{') {
                    val group =
                        readBalancedGroup(
                            source = source,
                            openingIndex = index,
                            opening = '{',
                            closing = '}',
                            sourceIndexOffset = sourceOffset,
                            diagnosticCommand = CHEMISTRY_COMMAND,
                        )
                    rendered.append(source, index, group.endExclusive)
                    index = group.endExclusive
                } else if (source[index] == '\\') {
                    val commandEnd = skipLatexCommand(source, index)
                    rendered.append(source, index, commandEnd)
                    index = commandEnd
                } else {
                    val valueStart = index
                    if (char == '^') {
                        while (
                            index < source.length &&
                                (source[index].isDigit() || source[index] == '+' || source[index] == '-')
                        ) {
                            index++
                        }
                    } else {
                        index++
                    }
                    val value = source.substring(valueStart, index)
                    if (value.length == 1) {
                        rendered.append(value)
                    } else {
                        rendered.append('{').append(value).append('}')
                    }
                }
            }

            in 'A'..'Z' -> {
                val symbolStart = index
                index++
                while (index < source.length && source[index] in 'a'..'z') {
                    index++
                }
                rendered.append("\\mathrm{")
                    .append(source, symbolStart, index)
                    .append('}')

                val countStart = index
                while (index < source.length && source[index].isDigit()) {
                    index++
                }
                if (index > countStart) {
                    rendered.append("_{")
                        .append(source, countStart, index)
                        .append('}')
                }
            }

            in 'a'..'z' -> {
                val textStart = index
                index++
                while (index < source.length && source[index] in 'a'..'z') {
                    index++
                }
                rendered.append("\\mathrm{")
                    .append(source, textStart, index)
                    .append('}')
            }

            '(', '[' -> {
                val closing = if (char == '(') ')' else ']'
                val group =
                    readBalancedGroup(
                        source = source,
                        openingIndex = index,
                        opening = char,
                        closing = closing,
                        sourceIndexOffset = sourceOffset,
                        diagnosticCommand = CHEMISTRY_COMMAND,
                    )
                val fullGroup = source.substring(index, group.endExclusive)
                if (CHEMICAL_STATE_PATTERN.matches(fullGroup)) {
                    rendered.append("\\mathrm{").append(fullGroup).append('}')
                } else {
                    rendered.append(char)
                    rendered.append(
                        renderChemicalSpecies(
                            source = group.content,
                            sourceOffset = sourceOffset + index + 1,
                        )
                    )
                    rendered.append(closing)
                }
                index = group.endExclusive

                val countStart = index
                while (index < source.length && source[index].isDigit()) {
                    index++
                }
                if (index > countStart) {
                    rendered.append("_{")
                        .append(source, countStart, index)
                        .append('}')
                }
            }

            '+', '-' -> {
                var chargeEnd = index
                while (chargeEnd < source.length && source[chargeEnd] == char) {
                    chargeEnd++
                }
                if (chargeEnd == source.length) {
                    val count = chargeEnd - index
                    rendered.append("^{")
                    if (count > 1) rendered.append(count)
                    rendered.append(char).append('}')
                    index = chargeEnd
                } else {
                    rendered.append(char)
                    index++
                }
            }

            '·', '.' -> {
                rendered.append("\\cdot")
                index++
            }

            '#' -> {
                rendered.append("\\equiv")
                index++
            }

            '{' -> {
                val group =
                    readBalancedGroup(
                        source = source,
                        openingIndex = index,
                        opening = '{',
                        closing = '}',
                        sourceIndexOffset = sourceOffset,
                        diagnosticCommand = CHEMISTRY_COMMAND,
                    )
                rendered.append('{')
                    .append(
                        renderChemicalSpecies(
                            source = group.content,
                            sourceOffset = sourceOffset + index + 1,
                        )
                    )
                    .append('}')
                index = group.endExclusive
            }

            '}', ')', ']' -> {
                throw LatexCompatibilityException(
                    message = "Unexpected closing delimiter in chemical species",
                    sourceIndex = sourceOffset + index,
                    command = CHEMISTRY_COMMAND,
                )
            }

            else -> {
                rendered.append(char)
                index++
            }
        }
    }

    return rendered.toString()
}

private fun readBalancedGroup(
    source: String,
    openingIndex: Int,
    opening: Char,
    closing: Char,
    sourceIndexOffset: Int = 0,
    diagnosticCommand: String? = null,
): BalancedGroup {
    require(openingIndex < source.length && source[openingIndex] == opening)
    var depth = 1
    var index = openingIndex + 1

    while (index < source.length) {
        when (source[index]) {
            '\\' -> {
                index = skipLatexCommand(source, index)
            }

            opening -> {
                depth++
                index++
            }

            closing -> {
                depth--
                if (depth == 0) {
                    return BalancedGroup(
                        content = source.substring(openingIndex + 1, index),
                        endExclusive = index + 1,
                    )
                }
                index++
            }

            else -> index++
        }
    }

    throw LatexCompatibilityException(
        message = "Unclosed $opening$closing group",
        sourceIndex = sourceIndexOffset + openingIndex,
        command = diagnosticCommand,
    )
}

private fun skipLatexCommand(
    source: String,
    commandStart: Int,
): Int {
    if (commandStart + 1 >= source.length) return source.length
    var index = commandStart + 1
    if (source[index].isAsciiLetter()) {
        index++
        while (index < source.length && source[index].isAsciiLetter()) {
            index++
        }
        return index
    }
    return index + 1
}

private fun matchChemicalArrow(
    source: String,
    index: Int,
): String? {
    return CHEMICAL_ARROWS.keys.firstOrNull { arrow ->
        source.regionMatches(index, arrow, 0, arrow.length)
    }
}

private fun isReactionPlus(
    source: String,
    index: Int,
): Boolean {
    val nextIndex = (index + 1 until source.length).firstOrNull { !source[it].isWhitespace() }
        ?: return false
    return source[nextIndex].isDigit() ||
        source[nextIndex] in 'A'..'Z' ||
        source[nextIndex] in 'a'..'z' ||
        source[nextIndex] == '\\' ||
        source[nextIndex] == '(' ||
        source[nextIndex] == '[' ||
        source[nextIndex] == '^'
}

private fun throwableMessageChain(error: Throwable): String {
    return generateSequence(error) { it.cause }
        .mapNotNull(Throwable::message)
        .distinct()
        .joinToString(" | ")
}

private fun commandAt(
    formula: String,
    sourceIndex: Int,
): String? {
    if (sourceIndex !in formula.indices || formula[sourceIndex] != '\\') return null
    if (sourceIndex + 1 >= formula.length) return "\\<end-of-input>"
    val next = formula[sourceIndex + 1]
    if (next == '\r' || next == '\n') return "\\<line-break>"
    if (next == ' ') return "\\<space>"
    if (!next.isAsciiLetter()) return "\\$next"

    var end = sourceIndex + 2
    while (end < formula.length && formula[end].isAsciiLetter()) {
        end++
    }
    return formula.substring(sourceIndex, end)
}

private fun lineAndColumnAt(
    source: String,
    sourceIndex: Int,
): Pair<Int, Int> {
    var line = 1
    var column = 1
    var index = 0

    while (index < sourceIndex.coerceAtMost(source.length)) {
        if (source[index] == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        index++
    }
    return line to column
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
