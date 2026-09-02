package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.*

/**
 * A stream processing plugin to identify and process XML-formatted data streams. This
 * version has a known limitation: it does not handle nested tags of the same name. Opening tags
 * are recognized at any position, while attribute quotes protect embedded angle brackets.
 *
 * @param includeTagsInOutput If true, the XML tags themselves (`<tag>`, `</tag>`) will be included
 * in the output stream. If false, they will be filtered out, leaving only the content between the
 * tags.
 */
class StreamXmlPlugin(private val includeTagsInOutput: Boolean = true) : StreamPlugin {

    private enum class StartState {
        WAIT_LT,
        WAIT_FIRST_LETTER,
        IN_TAG_NAME,
        IN_ATTRIBUTES,
    }

    override var state: PluginState = PluginState.IDLE
        private set

    private var endTagMatcher: StreamKmpGraph? = null
    private var startState = StartState.WAIT_LT
    private val tagName = StringBuilder()
    private var attributeQuote: Char? = null
    private var lastChar: Char = '\u0000'

    init {
        reset()
    }

    /**
     * Processes a single character for XML stream parsing and decides if it should be emitted. The
     * return value is used by `splitBy` as a filter.
     */
    override fun processChar(c: Char, @Suppress("UNUSED_PARAMETER") atStartOfLine: Boolean): Boolean {
        fun finish(result: Boolean): Boolean {
            lastChar = c
            return result
        }

        if (state == PluginState.PROCESSING) {
            // We are inside a tag, looking for the end tag.
            val matcher = requireNotNull(endTagMatcher)
            val result = matcher.processChar(c.asciiLowercase())

            return when (result) {
                is StreamKmpMatchResult.Match -> {
                    // End tag fully matched. Reset state and filter this last character if needed.
                    StreamLogger.i("StreamXmlPlugin", "Found end tag. Switching to IDLE.")
                    reset()
                    finish(includeTagsInOutput)
                }
                is StreamKmpMatchResult.InProgress -> {
                    // We are in the middle of matching the end tag (e.g., '</', '</t', etc.).
                    // The emission of these characters depends on the flag.
                    finish(includeTagsInOutput)
                }
                is StreamKmpMatchResult.NoMatch -> {
                    // The character `c` did not match the next char of the end tag.
                    // This means it's regular content between tags.
                    finish(true)
                }
            }
        }

        val previousState = state
        if (processStartTag(c)) {
            if (lastChar == '/') {
                // Self-closing tags stay plain in this generic splitter so HTML breaks and
                // ordinary inline tags do not create an unterminated XML group.
                reset()
                return finish(true)
            }

            val matchedTagName = tagName.toString()
            StreamLogger.i(
                "StreamXmlPlugin",
                "Found start tag '$matchedTagName'. Switching to PROCESSING."
            )
            state = PluginState.PROCESSING
            endTagMatcher =
                StreamKmpGraphBuilder()
                    .build(
                        kmpPattern {
                            literal("</")
                            literal(matchedTagName.map { it.asciiLowercase() }.joinToString(""))
                            char('>')
                        }
                    )
            startState = StartState.WAIT_LT
            return finish(includeTagsInOutput)
        }

        if (state == PluginState.TRYING) {
            return finish(includeTagsInOutput)
        }

        if (previousState == PluginState.TRYING) {
            reset()
        }
        return finish(true)
    }

    private fun processStartTag(c: Char): Boolean {
        return when (startState) {
            StartState.WAIT_LT -> {
                if (c == '<') {
                    tagName.clear()
                    startState = StartState.WAIT_FIRST_LETTER
                    state = PluginState.TRYING
                }
                false
            }

            StartState.WAIT_FIRST_LETTER -> {
                if (c.isAsciiLetter()) {
                    tagName.append(c)
                    startState = StartState.IN_TAG_NAME
                    state = PluginState.TRYING
                } else {
                    startState = StartState.WAIT_LT
                    state = PluginState.IDLE
                }
                false
            }

            StartState.IN_TAG_NAME -> {
                when {
                    c == ' ' || c == '\t' || c == '\r' || c == '\n' -> {
                        startState = StartState.IN_ATTRIBUTES
                        state = PluginState.TRYING
                        false
                    }

                    c == '>' -> true

                    c.isTagNameContinuation() -> {
                        tagName.append(c)
                        state = PluginState.TRYING
                        false
                    }

                    else -> {
                        startState = StartState.WAIT_LT
                        state = PluginState.IDLE
                        tagName.clear()
                        false
                    }
                }
            }

            StartState.IN_ATTRIBUTES -> {
                if (attributeQuote != null) {
                    if (c == attributeQuote) {
                        attributeQuote = null
                    }
                    false
                } else {
                    when (c) {
                        '\"', '\'' -> {
                            attributeQuote = c
                            false
                        }

                        '>' -> true
                        else -> {
                            state = PluginState.TRYING
                            false
                        }
                    }
                }
            }
        }
    }

    /** Initializes the plugin to its default state. */
    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    /** Destroys the plugin. No-op as listener is removed. */
    override fun destroy() {}

    /** Resets the plugin state. */
    override fun reset() {
        endTagMatcher = null
        startState = StartState.WAIT_LT
        tagName.clear()
        attributeQuote = null
        state = PluginState.IDLE
        lastChar = '\u0000'
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    private fun Char.isTagNameContinuation(): Boolean =
        isAsciiLetter() || this in '0'..'9' || this == '_'

    private fun Char.asciiLowercase(): Char =
        if (this in 'A'..'Z') (code + ('a'.code - 'A'.code)).toChar() else this
}
