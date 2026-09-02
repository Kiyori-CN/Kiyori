package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.*

/**
 * A collection of StreamPlugins for parsing various Markdown constructs.
 *
 * To use these plugins, create instances of the desired plugins and pass them as a list to the
 * `splitBy` stream operator.
 *
 * ### Important Note on Plugin Order: For Markdown features with overlapping delimiters (e.g., `*`
 * for italic and `**` for bold), the order of plugins in the list passed to `splitBy` is crucial.
 * The plugin for the longer delimiter must come before the plugin for the shorter one.
 *
 * **Correct Order Example:**
 * ```kotlin
 * val markdownPlugins = listOf(
 *     StreamMarkdownBoldPlugin(),      // For **...**
 *     StreamMarkdownItalicPlugin()     // For *...*
 * )
 * myCharStream.splitBy(markdownPlugins)
 * ```
 * This ensures that `**text**` is correctly identified as bold, not as two consecutive italics.
 */
private const val GROUP_DELIMITER = 1
private const val GROUP_HEADER_HASHES = 1
private const val GROUP_TEXT = 2
private const val GROUP_URL = 3

/**
 * A stream plugin for identifying Markdown fenced code blocks. This plugin identifies a block
 * starting with three or more backticks and ending with a fence at least as long as the opener.
 * The parser is line-anchored and keeps the first info-string token available to the renderer.
 * @param includeFences If true, the fences are included in the output.
 */
class StreamMarkdownFencedCodeBlockPlugin(private val includeFences: Boolean = true) :
        StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // A fenced block is a line-oriented construct.  Keeping the state explicit is
    // important here: a three-backtick prefix must win over INLINE_CODE before the
    // language token arrives, while a shorter/indented run must remain plain text.
    private var openingIndent = 0
    private var openingFenceLength = 0
    private var openingRunLength = 0
    private var openingLine = false
    private var closingCandidate = false
    private var closingIndent = 0
    private var closingRunLength = 0
    private var closingTrailingOnly = true

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            // Consume the info string as part of the opening line.  The block is
            // already PROCESSING from the third backtick, so INLINE_CODE cannot
            // steal the prefix while the language token is still streaming.
            if (openingLine) {
                if (c == '\n') {
                    openingLine = false
                    closingCandidate = false
                }
                return includeFences
            }

            // A closing fence can only start at a physical line boundary.  The
            // candidate stays buffered in the plugin state until LF/CRLF proves
            // that the remainder contains spaces/tabs only.
            if (atStartOfLine) {
                closingCandidate = true
                closingIndent = 0
                closingRunLength = 0
                closingTrailingOnly = true
            }

            if (!closingCandidate) {
                return true
            }

            if (c == '\n') {
                val closes =
                        closingRunLength >= openingFenceLength &&
                                closingRunLength >= 3 &&
                                closingTrailingOnly
                closingCandidate = false
                closingIndent = 0
                closingRunLength = 0
                closingTrailingOnly = true
                if (closes) {
                    reset()
                }
                return if (closes) includeFences else true
            }

            if (closingRunLength == 0 && c == ' ' && closingIndent < 3) {
                closingIndent++
                return includeFences
            }

            if (c == '`') {
                closingRunLength++
                return includeFences
            }

            if (closingRunLength > 0 && (c == ' ' || c == '\t')) {
                return includeFences
            }

            // Keep CR pending until the following LF confirms a CRLF line end.
            // The splitter may deliver the pair in separate pushes.
            if (closingRunLength > 0 && c == '\r') {
                return includeFences
            }

            // Any other character makes this a normal code line.  The already
            // emitted candidate characters remain opaque code content.
            closingCandidate = false
            closingTrailingOnly = false
            return true
        }

        // IDLE/TRYING: only a line-start run with at most three ASCII spaces can
        // open a block.  Inline triple backticks are intentionally ignored here.
        if (state == PluginState.IDLE) {
            if (!atStartOfLine) {
                return true
            }
            if (c == ' ') {
                openingIndent = 1
                state = PluginState.TRYING
                return includeFences
            }
            if (c == '`') {
                openingRunLength = 1
                state = PluginState.TRYING
                return includeFences
            }
            return true
        }

        if (state == PluginState.TRYING) {
            if (openingRunLength == 0) {
                if (c == ' ' && openingIndent < 3) {
                    openingIndent++
                    return includeFences
                }
                if (c == '`') {
                    openingRunLength = 1
                    return includeFences
                }
                reset()
                return true
            }

            if (c == '`') {
                openingRunLength++
                if (openingRunLength >= 3) {
                    openingFenceLength = openingRunLength
                    openingLine = true
                    closingCandidate = false
                    state = PluginState.PROCESSING
                }
                return includeFences
            }

            // A run shorter than three ticks is not a block opener.  Once three
            // ticks have been seen the state is PROCESSING and this branch is not
            // reached; the rest of the opening line is handled above.
            reset()
            return true
        }

        return true
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        openingIndent = 0
        openingFenceLength = 0
        openingRunLength = 0
        openingLine = false
        closingCandidate = false
        closingIndent = 0
        closingRunLength = 0
        closingTrailingOnly = true
    }
}

/**
 * A stream plugin for identifying Markdown inline code snippets (`code` or ``code``). It matches a
 * starting sequence of backticks and looks for a closing sequence of the same length. The snippet
 * cannot contain newlines.
 *
 * @param includeTicks If true, the ` backticks are included in the output.
 */
class StreamMarkdownInlineCodePlugin(private val includeTicks: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private var tickLength = 0
    private var endMatchLength = 0

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING && c == '\n') {
            reset()
            return true
        }

        if (state == PluginState.PROCESSING) {
            if (c == '`') {
                endMatchLength += 1
                if (endMatchLength == tickLength) {
                    reset()
                    return includeTicks
                }
                return includeTicks
            }
            endMatchLength = 0
            return true
        }

        if (c == '`') {
            when (state) {
                PluginState.IDLE -> {
                    state = PluginState.TRYING
                    tickLength = 1
                }
                PluginState.TRYING -> {
                    tickLength += 1
                }
                else -> Unit
            }
            return includeTicks
        }

        if (state == PluginState.TRYING) {
            if (c == '\n') {
                reset()
                return true
            }
            state = PluginState.PROCESSING
            endMatchLength = 0
        }
        return true
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        tickLength = 0
        endMatchLength = 0
    }
}

/**
 * A stream plugin for identifying bold text using double asterisks (`**text**`).
 *
 * @param includeAsterisks If true, the `**` delimiters are included in the output.
 */
class StreamMarkdownBoldPlugin(private val includeAsterisks: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        internal set

    private var startMatcher: StreamKmpGraph
    private var endMatcher: StreamKmpGraph

    init {
        val builder = StreamKmpGraphBuilder()
        startMatcher =
                builder.build(
                        kmpPattern {
                            literal("**")
                            noneOf('*', '\n')
                        }
                )
        endMatcher = builder.build(kmpPattern { literal("**") })
        reset()
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeAsterisks
                }
                is StreamKmpMatchResult.InProgress -> return includeAsterisks
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeAsterisks
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }
        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        startMatcher.reset()
        endMatcher.reset()
        state = PluginState.IDLE
    }
}

/**
 * A stream plugin for identifying italic text using single asterisks (`*text*`)。
 *
 * @param includeAsterisks If true, the `*` delimiters are included in the output.
 */
class StreamMarkdownItalicPlugin(private val includeAsterisks: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        internal set

    private var startMatcher: StreamKmpGraph
    private var endMatcher: StreamKmpGraph
    private var lastChar: Char? = null

    init {
        val builder = StreamKmpGraphBuilder()
        startMatcher =
                builder.build(
                        kmpPattern {
                            literal("*")
                            noneOf('*', '\n',' ')
                        }
                )
        endMatcher = builder.build(kmpPattern { literal("*") })
        reset()
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (lastChar == '*' && c == '*') {
            lastChar = null
            reset()

            return true
        }
        lastChar = c

        if (state == PluginState.PROCESSING) {
            if (c == '\n') {
                reset()
                return true
            }
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeAsterisks
                }
                is StreamKmpMatchResult.InProgress -> return includeAsterisks
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeAsterisks
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }

        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        startMatcher.reset()
        endMatcher.reset()
        state = PluginState.IDLE
    }
}

/**
 * Identifies ATX-style Markdown headers (e.g., "# My Header"). A header is defined as 1-6 '#'
 * characters at the beginning of a line, followed by a space, and extends to the end of the line.
 *
 * @param includeMarker If true, includes the '#' characters and the following space in the group.
 */
class StreamMarkdownHeaderPlugin(private val includeMarker: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private val headerMatcher =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                group(GROUP_HEADER_HASHES) {
                                    char('#')
                                    greedyStar { char('#') }
                                }
                                char(' ')
                            }
                    )

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            if (c == '\n') {
                reset()
            }
            return true // Return true to emit the character in the header content
        }

        if (atStartOfLine) {
            return handleMatch(c)
        }

        if (state == PluginState.TRYING) {
            // We are already in the middle of a potential match, continue feeding.
            return handleMatch(c)
        }

        // Not at start of line and not trying, so just pass through.
        return true
    }

    private fun handleMatch(c: Char): Boolean {
        return when (val result = headerMatcher.processChar(c)) {
            is StreamKmpMatchResult.Match -> {
                val hashes = result.groups[GROUP_HEADER_HASHES]
                if (hashes != null && hashes.length in 1..6) {
                    state = PluginState.PROCESSING
                    includeMarker
                } else {
                    // e.g. "####### " is not a valid header. Fail the match.
                    resetInternal()
                    true // The buffered chars in splitBy will be re-processed as default.
                }
            }
            is StreamKmpMatchResult.InProgress -> {
                state = PluginState.TRYING
                includeMarker
            }
            is StreamKmpMatchResult.NoMatch -> {
                // e.g. "#foo" or "##bar"
                resetInternal()
                true // The buffered chars will be re-processed as default.
            }
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    // The problematic call from splitBy will now only perform a soft reset of the
    // matching state, without incorrectly assuming it's at the start of a line.
    override fun reset() {
        resetInternal()
    }

    // Internal reset that doesn't touch atStartOfLine
    private fun resetInternal() {
        state = PluginState.IDLE
        headerMatcher.reset()
    }
}

/**
 * A stream plugin for identifying Markdown links in the format [text](url).
 *
 * @param includeDelimiters If true, the link delimiters are included in the output.
 */
class StreamMarkdownLinkPlugin : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private val startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                literal("[")
                            }
                    )

    private val linkContentMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                group(GROUP_TEXT) { greedyStar { noneOf(']', '\n') } }
                                literal("](")
                                group(GROUP_URL) { greedyStar { noneOf(')', '\n') } }
                                char(')')
                            }
                    )

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        when (state) {
            PluginState.IDLE -> {
                when (startMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        state = PluginState.TRYING
                        return true
                    }
                    else -> {
                        return true
                    }
                }
            }
            PluginState.TRYING -> {
                when (linkContentMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        reset()
                        return true
                    }
                    is StreamKmpMatchResult.InProgress -> {
                        state = PluginState.PROCESSING
                        return true
                    }
                    is StreamKmpMatchResult.NoMatch -> {
                        reset()
                        return true
                    }
                }
            }
            PluginState.PROCESSING -> {
                when (linkContentMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        reset()
                        return true
                    }
                    is StreamKmpMatchResult.InProgress -> {
                        return true
                    }
                    is StreamKmpMatchResult.NoMatch -> {
                        reset()
                        return true
                    }
                }
            }
            else -> return true
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        linkContentMatcher.reset()
    }
}

/**
 * A stream plugin for identifying Markdown images in the format ![alt text](url).
 *
 * @param includeDelimiters If true, the image delimiters are included in the output.
 */
class StreamMarkdownImagePlugin(private val includeDelimiters: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // Matcher for the start of an image markdown "!"
    private val startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                literal("!")
                            }
                    )
    
    // Matcher for the rest of the image markdown, starting from "["
    private val imageContentMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                char('[')
                                group(GROUP_TEXT) { greedyStar { noneOf(']', '\n') } }
                                char(']')
                                char('(')
                                group(GROUP_URL) { greedyStar { noneOf(')', '\n') } }
                                char(')')
                            }
                    )

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        when (state) {
            PluginState.IDLE -> {
                when (startMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        state = PluginState.TRYING
                        // Consume '!' but don't emit yet, wait for full match
                        return includeDelimiters
                    }
                    else -> {
                        // Not the start of an image, do nothing
                        return true
                    }
                }
            }
            PluginState.TRYING -> {
                when (imageContentMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        // Full image matched
                        reset() // Reset for next potential image
                        return includeDelimiters
                    }
                    is StreamKmpMatchResult.InProgress -> {
                        // It's looking like an image, transition to PROCESSING
                        state = PluginState.PROCESSING
                        return includeDelimiters
                    }
                    is StreamKmpMatchResult.NoMatch -> {
                        // It was a false alarm (e.g., "! not followed by [")
                        reset()
                        // The buffered characters (including '!') and the current char
                        // will be re-processed as default text by the multiplexer.
                        return true 
                    }
                }
            }
            PluginState.PROCESSING -> {
                when (imageContentMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        // Full image matched
                        reset()
                        return includeDelimiters
                    }
                    is StreamKmpMatchResult.InProgress -> {
                        // Continue processing
                        return includeDelimiters
                    }
                    is StreamKmpMatchResult.NoMatch -> {
                        // Pattern broke mid-way
                        reset()
                        return true
                    }
                }
            }
            else -> {
                // Should not happen in this plugin's logic
                return true
            }
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        imageContentMatcher.reset()
    }
}

/**
 * A stream plugin for identifying Markdown blockquotes (lines starting with >).
 *
 * @param includeMarker If true, includes the '>' character in the output.
 */
class StreamMarkdownBlockQuotePlugin(private val includeMarker: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set
    private var stripContinuationSpace = false

    private val blockQuoteMatcher =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                char('>')
                                char(' ') // 符合 Markdown 规范的引用块后面应该有一个空格
                            }
                    )

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (c == '\n') {
            stripContinuationSpace = false
            if (state == PluginState.PROCESSING) {
                state = PluginState.WAITFOR
            } else {
                reset()
            }
            return true
        }

        if (state == PluginState.WAITFOR) {
            if (atStartOfLine) {
                if (c == '>') {
                    state = PluginState.PROCESSING
                    stripContinuationSpace = true
                    return includeMarker
                } else {
                    reset()
                    return true
                }
            }
        }

        if (atStartOfLine) {
            return handleMatch(c)
        }

        if (state == PluginState.TRYING) {
            return handleMatch(c)
        }

        if (state == PluginState.PROCESSING && stripContinuationSpace) {
            stripContinuationSpace = false
            if (c == ' ') {
                return includeMarker
            }
        }

        return true
    }

    private fun handleMatch(c: Char): Boolean {
        return when (val result = blockQuoteMatcher.processChar(c)) {
            is StreamKmpMatchResult.Match -> {
                state = PluginState.PROCESSING
                includeMarker
            }
            is StreamKmpMatchResult.InProgress -> {
                state = PluginState.TRYING
                includeMarker
            }
            is StreamKmpMatchResult.NoMatch -> {
                resetInternal()
                true
            }
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        state = PluginState.IDLE
        stripContinuationSpace = false
        blockQuoteMatcher.reset()
    }
}

/**
 * A stream plugin for identifying Markdown horizontal rules (---, ***, ___).
 *
 * @param includeMarker If true, includes the horizontal rule marker in the output.
 */
class StreamMarkdownHorizontalRulePlugin(private val includeMarker: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private var currentMarker: Char? = null
    private var markerCount = 0

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (c == '\n') {
            val isMatch =
                    (state == PluginState.TRYING || state == PluginState.PROCESSING) &&
                            markerCount >= 3
            val shouldEmit = isMatch && includeMarker

            resetInternal()

            return if (isMatch) shouldEmit else true
        }

        if (state == PluginState.IDLE) {
            if (atStartOfLine) {
                if (c == '-' || c == '*' || c == '_') {
                    state = PluginState.TRYING
                    currentMarker = c
                    markerCount = 1
                    return includeMarker
                }
            }
            return true
        }

        if (c == currentMarker || c == ' ' || c == '\t') {
            if (c == currentMarker) {
                markerCount++
            }
            if (markerCount >= 3) {
                state = PluginState.PROCESSING
            }
            return includeMarker
        }

        resetInternal()
        return true
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        state = PluginState.IDLE
        currentMarker = null
        markerCount = 0
    }
}

/**
 * A stream plugin for identifying Markdown strikethrough text using double tildes (`~~text~~`).
 *
 * @param includeDelimiters If true, the `~~` delimiters are included in the output.
 */
class StreamMarkdownStrikethroughPlugin(private val includeDelimiters: Boolean = true) :
        StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        internal set

    private var startMatcher: StreamKmpGraph
    private var endMatcher: StreamKmpGraph

    init {
        val builder = StreamKmpGraphBuilder()
        startMatcher =
                builder.build(
                        kmpPattern {
                            literal("~~")
                            noneOf('~', '\n')
                        }
                )
        endMatcher = builder.build(kmpPattern { literal("~~") })
        reset()
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> return includeDelimiters
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeDelimiters
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }
        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        startMatcher.reset()
        endMatcher.reset()
        state = PluginState.IDLE
    }
}

/**
 * A stream plugin for identifying Markdown underlined text using double underscores (`__text__`).
 *
 * @param includeDelimiters If true, the `__` delimiters are included in the output.
 */
class StreamMarkdownUnderlinePlugin(private val includeDelimiters: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        internal set

    private var startMatcher: StreamKmpGraph
    private var endMatcher: StreamKmpGraph

    init {
        val builder = StreamKmpGraphBuilder()
        startMatcher =
                builder.build(
                        kmpPattern {
                            literal("__")
                            noneOf('_', '\n')
                        }
                )
        endMatcher = builder.build(kmpPattern { literal("__") })
        reset()
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> return includeDelimiters
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE or TRYING
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeDelimiters
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }
        return true // Should be unreachable
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        startMatcher.reset()
        endMatcher.reset()
        state = PluginState.IDLE
    }
}

/**
 * A stream plugin for identifying Markdown ordered lists (lines starting with a number followed by
 * a dot and space).
 *
 * @param includeMarker If true, includes the list marker (e.g., "1. ") in the output.
 */
class StreamMarkdownOrderedListPlugin(private val includeMarker: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private val listMatcher =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                digit() // 至少一个数字
                                greedyStar { digit() }
                                char('.')
                                char(' ')
                            }
                    )

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            if (c == '\n') {
                reset()
            }
            return true
        }

        if (atStartOfLine) {
            return handleMatch(c)
        } else if (state == PluginState.TRYING) {
            return handleMatch(c)
        }

        return true
    }

    private fun handleMatch(c: Char): Boolean {
        return when (val result = listMatcher.processChar(c)) {
            is StreamKmpMatchResult.Match -> {
                state = PluginState.PROCESSING
                includeMarker
            }
            is StreamKmpMatchResult.InProgress -> {
                state = PluginState.TRYING
                includeMarker
            }
            is StreamKmpMatchResult.NoMatch -> {
                resetInternal()
                true
            }
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        state = PluginState.IDLE
        listMatcher.reset()
    }
}

/**
 * A stream plugin for identifying Markdown unordered lists (lines starting with *, - or + followed
 * by a space).
 *
 * @param includeMarker If true, includes the list marker (e.g., "* ") in the output.
 */
class StreamMarkdownUnorderedListPlugin(private val includeMarker: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    private val listMatcher =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                anyOf('-', '+', '*')
                                char(' ')
                            }
                    )

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            if (c == '\n') {
                reset()
            }
            return true
        }

        if (atStartOfLine) {
            return handleMatch(c)
        } else if (state == PluginState.TRYING) {
            return handleMatch(c)
        }

        return true
    }

    private fun handleMatch(c: Char): Boolean {
        return when (val result = listMatcher.processChar(c)) {
            is StreamKmpMatchResult.Match -> {
                state = PluginState.PROCESSING
                includeMarker
            }
            is StreamKmpMatchResult.InProgress -> {
                state = PluginState.TRYING
                includeMarker
            }
            is StreamKmpMatchResult.NoMatch -> {
                resetInternal()
                true
            }
        }
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        state = PluginState.IDLE
        listMatcher.reset()
    }
}

/**
 * A stream plugin for identifying LaTeX inline math expressions using single dollar signs ($...$).
 *
 * @param includeDelimiters If true, the $ delimiters are included in the output.
 */
class StreamMarkdownInlineLaTeXPlugin(private val includeDelimiters: Boolean = true) :
        StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // 模式匹配器用于查找单个美元符号 $ ... $
    private var startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                char('$')
                                noneOf('$', '\n')
                            }
                    )
    private var endMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder().build(kmpPattern { char('$') })

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            // 处理结束匹配符
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> return includeDelimiters
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE或TRYING
            // 处理开始匹配符
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeDelimiters
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }
        return true // 不应该到达这里
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        endMatcher.reset()
    }
}

/**
 * LaTeX inline math using \( ... \) delimiters.
 */
class StreamMarkdownInlineParenLaTeXPlugin(private val includeDelimiters: Boolean = true) :
        StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // 模式匹配器用于查找 \( ... \)
    private var startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                literal("\\(")
                                noneOf('\n')
                            }
                    )
    private var endMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder().build(kmpPattern { literal("\\)") })

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            // 处理结束匹配符
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> return includeDelimiters
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE或TRYING
            // 处理开始匹配符
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    endMatcher.reset()
                    startMatcher.reset()
                    return true
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeDelimiters
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    return true
                }
            }
        }
        return true // 不应该到达这里
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        endMatcher.reset()
    }
}

/**
 * A stream plugin for identifying LaTeX block math expressions using double dollar signs ($$...$$).
 *
 * @param includeDelimiters If true, the $$ delimiters are included in the output.
 */
class StreamMarkdownBlockLaTeXPlugin(private val includeDelimiters: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // 模式匹配器用于查找双美元符号 $$ ... $$
    private var startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder().build(kmpPattern { literal("$$") })
    private var endMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder().build(kmpPattern { literal("$$") })
    private var linePrefixOnly = true

    private fun advanceLinePrefix(c: Char) {
        linePrefixOnly = when {
            c == '\n' -> true
            linePrefixOnly && (c == ' ' || c == '\t') -> true
            else -> false
        }
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (atStartOfLine) {
            linePrefixOnly = true
        }
        if (state == PluginState.PROCESSING) {
            // 处理结束匹配符
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    linePrefixOnly = false
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> return includeDelimiters
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE或TRYING
            if (state == PluginState.IDLE && !linePrefixOnly) {
                advanceLinePrefix(c)
                startMatcher.reset()
                return true
            }
            // 处理开始匹配符
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    linePrefixOnly = false
                    endMatcher.reset()
                    startMatcher.reset()
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeDelimiters
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    advanceLinePrefix(c)
                    return true
                }
            }
        }
        return true // 不应该到达这里
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        endMatcher.reset()
        linePrefixOnly = true
    }
}

/**
 * LaTeX block math using \[ ... \] delimiters.
 */
class StreamMarkdownBlockBracketLaTeXPlugin(private val includeDelimiters: Boolean = true) :
        StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set

    // 模式匹配器用于查找 \\[ ... \\]
    private var startMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder().build(kmpPattern { literal("\\[") })
    private var endMatcher: StreamKmpGraph =
            StreamKmpGraphBuilder().build(kmpPattern { literal("\\]") })
    private var linePrefixOnly = true

    private fun advanceLinePrefix(c: Char) {
        linePrefixOnly = when {
            c == '\n' -> true
            linePrefixOnly && (c == ' ' || c == '\t') -> true
            else -> false
        }
    }

    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (atStartOfLine) {
            linePrefixOnly = true
        }
        if (state == PluginState.PROCESSING) {
            // 处理结束匹配符
            when (endMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    reset()
                    linePrefixOnly = false
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> return includeDelimiters
                is StreamKmpMatchResult.NoMatch -> return true
            }
        } else { // IDLE或TRYING
            if (state == PluginState.IDLE && !linePrefixOnly) {
                advanceLinePrefix(c)
                startMatcher.reset()
                return true
            }
            // 处理开始匹配符
            when (startMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    state = PluginState.PROCESSING
                    linePrefixOnly = false
                    endMatcher.reset()
                    startMatcher.reset()
                    return includeDelimiters
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    return includeDelimiters
                }
                is StreamKmpMatchResult.NoMatch -> {
                    if (state == PluginState.TRYING) {
                        reset()
                    }
                    advanceLinePrefix(c)
                    return true
                }
            }
        }
        return true // 不应该到达这里
    }

    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    override fun destroy() {}

    override fun reset() {
        state = PluginState.IDLE
        startMatcher.reset()
        endMatcher.reset()
        linePrefixOnly = true
    }
}

/**
 * A stream plugin for identifying Markdown tables. It recognizes complete table blocks with
 * multiple rows, starting with pipe characters and maintaining table state across newlines.
 * 
 * @param includeDelimiters If true, the pipe delimiters are included in the output.
 */
class StreamMarkdownTablePlugin(private val includeDelimiters: Boolean = true) : StreamPlugin {
    override var state: PluginState = PluginState.IDLE
        private set
    
    // 用于记录表格状态
    private var tableRowCount = 0
    private var foundHeaderSeparator = false
    private var emptyLineCount = 0 // 用于检测表格结束的空行计数
    
    // 用于匹配表格行开始
    private val tableRowMatcher = 
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                char('|') // 表格行必须以竖线开始
                            }
                    )
    
    // 用于匹配表头分隔符行
    private val headerSeparatorMatcher =
            StreamKmpGraphBuilder()
                    .build(
                            kmpPattern {
                                char('|')
                                greedyStar { anyOf('-', ':', ' ') }
                            }
                    )
    
    // 用于检测其他块元素开始符号
    private val otherBlockStarters = setOf('$', '`', '#', '>', '*', '-', '+')
    
    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        // 处理换行符
        if (c == '\n') {
            if (state == PluginState.PROCESSING) {
                // 在表格处理模式下遇到换行符
                // 进入WAITFOR状态，等待下一行决定去留
                state = PluginState.WAITFOR
                return true
            }
            return true
        }
        
        // WAITFOR状态下处理字符
        if (state == PluginState.WAITFOR) {
            if (atStartOfLine) {
                if (c == '|') {
                    // 确认是表格的下一行，继续处理
                    state = PluginState.PROCESSING
                    tableRowCount++
                    return includeDelimiters
                } else if (c in otherBlockStarters) {
                    // 遇到其他块元素的起始符号，结束表格处理
                    reset()
                    return true
                } else {
                    // 其他非表格行，结束表格处理
                    reset()
                    return true
                }
            }
        }
        
        // 处理行开始
        if (atStartOfLine) {
            // 检查是否是表格行开始
            when (val result = tableRowMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match, is StreamKmpMatchResult.InProgress -> {
                    if (state == PluginState.IDLE) {
                        // 开始新的表格
                        state = PluginState.PROCESSING
                        tableRowCount = 1
                        emptyLineCount = 0
                        tableRowMatcher.reset()
                    } else if (state == PluginState.PROCESSING) {
                        // 继续处理表格的下一行
                        tableRowCount++
                        emptyLineCount = 0
                    }
                    
                    // 检查是否是表头分隔符行
                    if (tableRowCount == 2 && !foundHeaderSeparator) {
                        headerSeparatorMatcher.processChar(c)
                    }
                    
                    return includeDelimiters
                }
                else -> {
                    if (state == PluginState.PROCESSING) {
                        // 在表格处理模式下遇到不是表格行开始的行
                        // 立即结束表格处理
                        reset()
                    }
                    return true
                }
            }
        } else if (state == PluginState.PROCESSING) {
            // 在表格行中间处理字符
            
            // 如果是第二行且可能是分隔符行
            if (tableRowCount == 2 && !foundHeaderSeparator) {
                // 处理头部分隔符检测
                when (val result = headerSeparatorMatcher.processChar(c)) {
                    is StreamKmpMatchResult.Match -> {
                        foundHeaderSeparator = true
                    }
                    is StreamKmpMatchResult.NoMatch -> {
                        // 如果第二行不是分隔符，继续正常处理
                    }
                    else -> { /* 继续收集字符 */ }
                }
            }
            
            // 返回字符是否应该包含在输出中
            return includeDelimiters || c != '|'
        }
        
        return true
    }
    
    override fun initPlugin(): Boolean {
        reset()
        return true
    }
    
    override fun destroy() {}
    
    override fun reset() {
        state = PluginState.IDLE
        tableRowCount = 0
        foundHeaderSeparator = false
        emptyLineCount = 0
        tableRowMatcher.reset()
        headerSeparatorMatcher.reset()
    }
}
