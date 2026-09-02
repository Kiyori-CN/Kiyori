#include "StreamMarkdownPlugin.h"

#include <algorithm>

namespace streamnative {

namespace {
inline int countRun(const char16_t* /*chars*/, int /*len*/, int /*i*/, char16_t /*ch*/) { return 0; }

enum class MatchState {
    Match,
    InProgress,
    NoMatch,
};

struct PrefixMatcher {
    std::u16string pattern;
    int i = 0;

    explicit PrefixMatcher(std::u16string p) : pattern(std::move(p)) {}

    void reset() { i = 0; }

    MatchState process(char16_t c) {
        if (pattern.empty()) {
            return MatchState::NoMatch;
        }
        if (c == pattern[static_cast<size_t>(i)]) {
            i++;
            if (i == static_cast<int>(pattern.size())) {
                // leave matcher ready for next use; Kotlin reset() is called explicitly
                return MatchState::Match;
            }
            return MatchState::InProgress;
        }
        // no partial fallback (enough for our 1-2 char delimiters)
        i = 0;
        return MatchState::NoMatch;
    }
};

inline bool isDigit(char16_t c) { return c >= u'0' && c <= u'9'; }
}

// --- Fenced code block ---
StreamMarkdownFencedCodeBlockPlugin::StreamMarkdownFencedCodeBlockPlugin(bool includeFences)
        : includeFences_(includeFences),
          state_(PluginState::IDLE),
          openingIndent_(0),
          openingFenceLength_(0),
          openingRunLength_(0),
          openingLine_(false),
          closingCandidate_(false),
          closingIndent_(0),
          closingRunLength_(0),
          closingTrailingOnly_(true) {
    reset();
}

PluginState StreamMarkdownFencedCodeBlockPlugin::state() const { return state_; }

bool StreamMarkdownFencedCodeBlockPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownFencedCodeBlockPlugin::reset() {
    state_ = PluginState::IDLE;
    openingIndent_ = 0;
    openingFenceLength_ = 0;
    openingRunLength_ = 0;
    openingLine_ = false;
    closingCandidate_ = false;
    closingIndent_ = 0;
    closingRunLength_ = 0;
    closingTrailingOnly_ = true;
}

bool StreamMarkdownFencedCodeBlockPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (state_ == PluginState::PROCESSING) {
        // The block becomes PROCESSING on the third opening tick.  Keeping the
        // remainder of that line in this state prevents INLINE_CODE, LaTeX and
        // XML plugins from claiming the language token or first content chunk.
        if (openingLine_) {
            if (c == u'\n') {
                openingLine_ = false;
                closingCandidate_ = false;
            }
            return includeFences_;
        }

        // A closing fence is valid only at a physical line boundary, with at
        // most three spaces of indentation and only spaces/tabs after the run.
        // We keep the candidate state until LF/CRLF confirms the whole line.
        if (atStartOfLine) {
            closingCandidate_ = true;
            closingIndent_ = 0;
            closingRunLength_ = 0;
            closingTrailingOnly_ = true;
        }

        if (!closingCandidate_) {
            return true;
        }

        if (c == u'\n') {
            const bool closes =
                    closingRunLength_ >= openingFenceLength_ &&
                    closingRunLength_ >= 3 &&
                    closingTrailingOnly_;
            closingCandidate_ = false;
            closingIndent_ = 0;
            closingRunLength_ = 0;
            closingTrailingOnly_ = true;
            if (closes) {
                reset();
            }
            return closes ? includeFences_ : true;
        }

        if (closingRunLength_ == 0 && c == u' ' && closingIndent_ < 3) {
            closingIndent_ += 1;
            return includeFences_;
        }

        if (c == u'`') {
            closingRunLength_ += 1;
            return includeFences_;
        }

        if (closingRunLength_ > 0 && (c == u' ' || c == u'\t' || c == u'\r')) {
            return includeFences_;
        }

        // This is an ordinary code line; candidate characters already emitted
        // remain opaque code content and the rest of the line passes through.
        closingCandidate_ = false;
        closingTrailingOnly_ = false;
        return true;
    }

    // IDLE/TRYING: only a line-start run with at most three ASCII spaces opens a
    // block.  Inline triple-backtick spans are intentionally left to INLINE_CODE.
    if (state_ == PluginState::IDLE) {
        if (!atStartOfLine) {
            return true;
        }
        if (c == u' ') {
            openingIndent_ = 1;
            state_ = PluginState::TRYING;
            return includeFences_;
        }
        if (c == u'`') {
            openingRunLength_ = 1;
            state_ = PluginState::TRYING;
            return includeFences_;
        }
        return true;
    }

    if (state_ == PluginState::TRYING) {
        if (openingRunLength_ == 0) {
            if (c == u' ' && openingIndent_ < 3) {
                openingIndent_ += 1;
                return includeFences_;
            }
            if (c == u'`') {
                openingRunLength_ = 1;
                return includeFences_;
            }
            reset();
            return true;
        }

        if (c == u'`') {
            openingRunLength_ += 1;
            if (openingRunLength_ >= 3) {
                openingFenceLength_ = openingRunLength_;
                openingLine_ = true;
                closingCandidate_ = false;
                state_ = PluginState::PROCESSING;
            }
            return includeFences_;
        }

        // A run shorter than three ticks is not a block opener.
        reset();
        return true;
    }

    return true;
}

// --- Inline code ---
StreamMarkdownInlineCodePlugin::StreamMarkdownInlineCodePlugin(bool includeTicks)
        : includeTicks_(includeTicks), state_(PluginState::IDLE), tickLen_(0), endMatch_(0) {
    reset();
}

PluginState StreamMarkdownInlineCodePlugin::state() const { return state_; }

bool StreamMarkdownInlineCodePlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownInlineCodePlugin::reset() {
    state_ = PluginState::IDLE;
    tickLen_ = 0;
    endMatch_ = 0;
}

bool StreamMarkdownInlineCodePlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::PROCESSING && c == u'\n') {
        reset();
        return true;
    }

    if (state_ == PluginState::PROCESSING) {
        if (c == u'`') {
            endMatch_ += 1;
            if (endMatch_ == tickLen_) {
                reset();
                return includeTicks_;
            }
            return includeTicks_;
        }
        endMatch_ = 0;
        return true;
    }

    // IDLE/TRYING: collect the complete opening backtick run before the first code character.
    if (c == u'`') {
        if (state_ == PluginState::IDLE) {
            state_ = PluginState::TRYING;
            tickLen_ = 1;
            return includeTicks_;
        }
        if (state_ == PluginState::TRYING) {
            tickLen_ += 1;
            return includeTicks_;
        }
    }

    if (state_ == PluginState::TRYING) {
        // need a non-tick, non-newline char to confirm start
        if (c != u'`' && c != u'\n') {
            state_ = PluginState::PROCESSING;
            endMatch_ = 0;
            return true;
        }
        // newline invalidates
        if (c == u'\n') {
            reset();
            return true;
        }
    }

    return true;
}

// --- Bold ---
StreamMarkdownBoldPlugin::StreamMarkdownBoldPlugin(bool includeAsterisks)
        : includeAsterisks_(includeAsterisks), state_(PluginState::IDLE), startMatch_(0), endMatch_(0) {
    reset();
}

PluginState StreamMarkdownBoldPlugin::state() const { return state_; }

bool StreamMarkdownBoldPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownBoldPlugin::reset() {
    state_ = PluginState::IDLE;
    startMatch_ = 0;
    endMatch_ = 0;
}

bool StreamMarkdownBoldPlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::PROCESSING) {
        if (c == u'*') {
            endMatch_ += 1;
            if (endMatch_ == 2) {
                reset();
                return includeAsterisks_;
            }
            return includeAsterisks_;
        }
        endMatch_ = 0;
        return true;
    }

    // IDLE/TRYING (Kotlin: literal("**") + noneOf('*','\n'))
    if (state_ == PluginState::IDLE) {
        if (c == u'*') {
            state_ = PluginState::TRYING;
            startMatch_ = 1;
            return includeAsterisks_;
        }
        return true;
    }

    if (state_ == PluginState::TRYING) {
        if (startMatch_ == 1) {
            if (c == u'*') {
                startMatch_ = 2;
                return includeAsterisks_;
            }
            reset();
            return true;
        }
        if (startMatch_ == 2) {
            if (c != u'*' && c != u'\n') {
                state_ = PluginState::PROCESSING;
                endMatch_ = 0;
                startMatch_ = 0;
                return true;
            }
            // Third '*' ("***") or newline => not bold start in Kotlin.
            reset();
            return true;
        }
        reset();
        return true;
    }

    return true;
}

// --- Italic ---
StreamMarkdownItalicPlugin::StreamMarkdownItalicPlugin(bool includeAsterisks)
        : includeAsterisks_(includeAsterisks),
          state_(PluginState::IDLE),
          startMatch_(0),
          endMatch_(0),
          lastChar_(0),
          hasLastChar_(false) {
    reset();
}

PluginState StreamMarkdownItalicPlugin::state() const { return state_; }

bool StreamMarkdownItalicPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownItalicPlugin::reset() {
    state_ = PluginState::IDLE;
    startMatch_ = 0;
    endMatch_ = 0;
    hasLastChar_ = false;
    lastChar_ = 0;
}

bool StreamMarkdownItalicPlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (hasLastChar_ && lastChar_ == u'*' && c == u'*') {
        // Kotlin special-case to avoid treating ** as italics
        hasLastChar_ = false;
        reset();
        return true;
    }
    lastChar_ = c;
    hasLastChar_ = true;

    if (state_ == PluginState::PROCESSING) {
        if (c == u'\n') {
            reset();
            return true;
        }
        if (c == u'*') {
            reset();
            return includeAsterisks_;
        }
        return true;
    }

    // IDLE/TRYING
    if (c == u'*') {
        state_ = PluginState::TRYING;
        startMatch_ = 1;
        return includeAsterisks_;
    }

    if (state_ == PluginState::TRYING) {
        // Kotlin: noneOf('*','\n',' ') after '*'
        if (c != u'*' && c != u'\n' && c != u' ') {
            state_ = PluginState::PROCESSING;
            return true;
        }
        reset();
        return true;
    }

    return true;
}

// --- Header ---
StreamMarkdownHeaderPlugin::StreamMarkdownHeaderPlugin(bool includeMarker)
        : includeMarker_(includeMarker), state_(PluginState::IDLE), hashCount_(0), inMatch_(false) {
    reset();
}

PluginState StreamMarkdownHeaderPlugin::state() const { return state_; }

bool StreamMarkdownHeaderPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownHeaderPlugin::reset() {
    state_ = PluginState::IDLE;
    hashCount_ = 0;
    inMatch_ = false;
}

bool StreamMarkdownHeaderPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (state_ == PluginState::PROCESSING) {
        if (c == u'\n') {
            reset();
        }
        return true;
    }

    if (atStartOfLine) {
        inMatch_ = true;
        hashCount_ = 0;
        state_ = PluginState::IDLE;
    }

    if (!inMatch_ && state_ != PluginState::TRYING) {
        return true;
    }

    if (c == u'#') {
        hashCount_ += 1;
        state_ = PluginState::TRYING;
        return includeMarker_;
    }

    if (c == u' ' && hashCount_ >= 1 && hashCount_ <= 6) {
        state_ = PluginState::PROCESSING;
        inMatch_ = false;
        return includeMarker_;
    }

    // invalid header
    reset();
    return true;
}

// --- Link ---
StreamMarkdownLinkPlugin::StreamMarkdownLinkPlugin() : state_(PluginState::IDLE), phase_(0) {
    reset();
}

PluginState StreamMarkdownLinkPlugin::state() const { return state_; }

bool StreamMarkdownLinkPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownLinkPlugin::reset() {
    state_ = PluginState::IDLE;
    phase_ = 0;
}

bool StreamMarkdownLinkPlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::IDLE) {
        if (c == u'[') {
            state_ = PluginState::TRYING;
            phase_ = 1;
        }
        return true;
    }

    // phase: 1=inside [text], 2=after ]( expecting url, 3=inside url
    if (state_ == PluginState::TRYING || state_ == PluginState::PROCESSING) {
        if (c == u'\n') {
            reset();
            return true;
        }
        if (phase_ == 1) {
            if (c == u']') {
                phase_ = 2;
                state_ = PluginState::PROCESSING;
            }
            return true;
        }
        if (phase_ == 2) {
            if (c == u'(') {
                phase_ = 3;
                return true;
            }
            reset();
            return true;
        }
        if (phase_ == 3) {
            if (c == u')') {
                reset();
                return true;
            }
            return true;
        }
    }

    return true;
}

// --- Block quote ---
StreamMarkdownBlockQuotePlugin::StreamMarkdownBlockQuotePlugin(bool includeMarker)
        : includeMarker_(includeMarker),
          state_(PluginState::IDLE),
          matchIndex_(0),
          stripContinuationSpace_(false) {
    reset();
}

PluginState StreamMarkdownBlockQuotePlugin::state() const { return state_; }

bool StreamMarkdownBlockQuotePlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownBlockQuotePlugin::reset() {
    state_ = PluginState::IDLE;
    matchIndex_ = 0;
    stripContinuationSpace_ = false;
}

bool StreamMarkdownBlockQuotePlugin::processChar(char16_t c, bool atStartOfLine) {
    if (c == u'\n') {
        stripContinuationSpace_ = false;
        if (state_ == PluginState::PROCESSING) {
            state_ = PluginState::WAITFOR;
        } else {
            reset();
        }
        return true;
    }

    if (state_ == PluginState::WAITFOR) {
        if (atStartOfLine) {
            if (c == u'>') {
                state_ = PluginState::PROCESSING;
                matchIndex_ = 1;
                stripContinuationSpace_ = true;
                return includeMarker_;
            }
            reset();
            return true;
        }
    }

    if (atStartOfLine) {
        // match "> "
        if (matchIndex_ == 0) {
            if (c == u'>') {
                matchIndex_ = 1;
                state_ = PluginState::TRYING;
                return includeMarker_;
            }
            return true;
        }
        if (matchIndex_ == 1) {
            if (c == u' ') {
                state_ = PluginState::PROCESSING;
                matchIndex_ = 0;
                return includeMarker_;
            }
            reset();
            return true;
        }
    }

    if (state_ == PluginState::PROCESSING) {
        if (stripContinuationSpace_) {
            stripContinuationSpace_ = false;
            if (c == u' ') {
                return includeMarker_;
            }
        }
        return true;
    }

    if (state_ == PluginState::TRYING) {
        // continue matching if needed
        if (matchIndex_ == 1) {
            if (c == u' ') {
                state_ = PluginState::PROCESSING;
                matchIndex_ = 0;
                return includeMarker_;
            }
            reset();
            return true;
        }
    }

    return true;
}

// --- Horizontal rule ---
StreamMarkdownHorizontalRulePlugin::StreamMarkdownHorizontalRulePlugin(bool includeMarker)
        : includeMarker_(includeMarker), state_(PluginState::IDLE), currentMarker_(0), hasMarker_(false), markerCount_(0) {
    reset();
}

PluginState StreamMarkdownHorizontalRulePlugin::state() const { return state_; }

bool StreamMarkdownHorizontalRulePlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownHorizontalRulePlugin::reset() {
    state_ = PluginState::IDLE;
    currentMarker_ = 0;
    hasMarker_ = false;
    markerCount_ = 0;
}

bool StreamMarkdownHorizontalRulePlugin::processChar(char16_t c, bool atStartOfLine) {
    if (c == u'\n') {
        const bool isMatch = (state_ == PluginState::TRYING || state_ == PluginState::PROCESSING) && markerCount_ >= 3;
        const bool shouldEmit = isMatch && includeMarker_;
        reset();
        return isMatch ? shouldEmit : true;
    }

    if (state_ == PluginState::IDLE) {
        if (atStartOfLine) {
            if (c == u'-' || c == u'*' || c == u'_') {
                state_ = PluginState::TRYING;
                currentMarker_ = c;
                hasMarker_ = true;
                markerCount_ = 1;
                return includeMarker_;
            }
        }
        return true;
    }

    if (hasMarker_ && (c == currentMarker_ || c == u' ' || c == u'\t')) {
        if (c == currentMarker_) {
            markerCount_++;
        }
        if (markerCount_ >= 3) {
            state_ = PluginState::PROCESSING;
        }
        return includeMarker_;
    }

    reset();
    return true;
}

} // namespace streamnative

namespace streamnative {

// --- Ordered list ---
StreamMarkdownOrderedListPlugin::StreamMarkdownOrderedListPlugin(bool includeMarker)
        : includeMarker_(includeMarker), state_(PluginState::IDLE), matchState_(0) {
    reset();
}

PluginState StreamMarkdownOrderedListPlugin::state() const { return state_; }

bool StreamMarkdownOrderedListPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownOrderedListPlugin::reset() {
    state_ = PluginState::IDLE;
    matchState_ = 0;
}

bool StreamMarkdownOrderedListPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (state_ == PluginState::PROCESSING) {
        if (c == u'\n') {
            reset();
        }
        return true;
    }

    if (atStartOfLine) {
        // start matching
        matchState_ = 0;
        state_ = PluginState::IDLE;
    }

    if (!atStartOfLine && state_ != PluginState::TRYING) {
        return true;
    }

    // match digits+ '.' ' '
    if (matchState_ == 0) {
        if (isDigit(c)) {
            state_ = PluginState::TRYING;
            matchState_ = 1;
            return includeMarker_;
        }
        reset();
        return true;
    }

    if (matchState_ == 1) {
        if (isDigit(c)) {
            state_ = PluginState::TRYING;
            return includeMarker_;
        }
        if (c == u'.') {
            matchState_ = 2;
            state_ = PluginState::TRYING;
            return includeMarker_;
        }
        reset();
        return true;
    }

    if (matchState_ == 2) {
        if (c == u' ') {
            state_ = PluginState::PROCESSING;
            matchState_ = 0;
            return includeMarker_;
        }
        reset();
        return true;
    }

    reset();
    return true;
}

// --- Unordered list ---
StreamMarkdownUnorderedListPlugin::StreamMarkdownUnorderedListPlugin(bool includeMarker)
        : includeMarker_(includeMarker), state_(PluginState::IDLE), matchState_(0) {
    reset();
}

PluginState StreamMarkdownUnorderedListPlugin::state() const { return state_; }

bool StreamMarkdownUnorderedListPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownUnorderedListPlugin::reset() {
    state_ = PluginState::IDLE;
    matchState_ = 0;
}

bool StreamMarkdownUnorderedListPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (state_ == PluginState::PROCESSING) {
        if (c == u'\n') {
            reset();
        }
        return true;
    }

    if (atStartOfLine) {
        matchState_ = 0;
        state_ = PluginState::IDLE;
    }

    if (!atStartOfLine && state_ != PluginState::TRYING) {
        return true;
    }

    // match anyOf('-', '+', '*') then ' '
    if (matchState_ == 0) {
        if (c == u'-' || c == u'+' || c == u'*') {
            state_ = PluginState::TRYING;
            matchState_ = 1;
            return includeMarker_;
        }
        reset();
        return true;
    }

    if (matchState_ == 1) {
        if (c == u' ') {
            state_ = PluginState::PROCESSING;
            matchState_ = 0;
            return includeMarker_;
        }
        reset();
        return true;
    }

    reset();
    return true;
}

// --- Strikethrough ---
StreamMarkdownStrikethroughPlugin::StreamMarkdownStrikethroughPlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), startState_(0), endState_(0) {
    reset();
}

PluginState StreamMarkdownStrikethroughPlugin::state() const { return state_; }

bool StreamMarkdownStrikethroughPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownStrikethroughPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = 0;
    endState_ = 0;
}

bool StreamMarkdownStrikethroughPlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::PROCESSING) {
        // end matcher for "~~"
        if (endState_ == 0) {
            if (c == u'~') {
                endState_ = 1;
                return includeDelimiters_;
            }
            return true;
        }
        if (endState_ == 1) {
            if (c == u'~') {
                reset();
                return includeDelimiters_;
            }
            // fail mid end-match
            endState_ = 0;
            return true;
        }
        endState_ = 0;
        return true;
    }

    // start matcher for "~~" + noneOf('~','\n')
    if (startState_ == 0) {
        if (c == u'~') {
            startState_ = 1;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        return true;
    }
    if (startState_ == 1) {
        if (c == u'~') {
            startState_ = 2;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        // fail
        reset();
        return true;
    }
    if (startState_ == 2) {
        if (c != u'~' && c != u'\n') {
            state_ = PluginState::PROCESSING;
            startState_ = 0;
            endState_ = 0;
            return true;
        }
        reset();
        return true;
    }

    reset();
    return true;
}

// --- Underline ---
StreamMarkdownUnderlinePlugin::StreamMarkdownUnderlinePlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), startState_(0), endState_(0) {
    reset();
}

PluginState StreamMarkdownUnderlinePlugin::state() const { return state_; }

bool StreamMarkdownUnderlinePlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownUnderlinePlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = 0;
    endState_ = 0;
}

bool StreamMarkdownUnderlinePlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::PROCESSING) {
        if (endState_ == 0) {
            if (c == u'_') {
                endState_ = 1;
                return includeDelimiters_;
            }
            return true;
        }
        if (endState_ == 1) {
            if (c == u'_') {
                reset();
                return includeDelimiters_;
            }
            endState_ = 0;
            return true;
        }
        endState_ = 0;
        return true;
    }

    // start matcher for "__" + noneOf('_','\n')
    if (startState_ == 0) {
        if (c == u'_') {
            startState_ = 1;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        return true;
    }
    if (startState_ == 1) {
        if (c == u'_') {
            startState_ = 2;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        reset();
        return true;
    }
    if (startState_ == 2) {
        if (c != u'_' && c != u'\n') {
            state_ = PluginState::PROCESSING;
            startState_ = 0;
            endState_ = 0;
            return true;
        }
        reset();
        return true;
    }

    reset();
    return true;
}

// --- Inline LaTeX ($...$) ---
StreamMarkdownInlineLaTeXPlugin::StreamMarkdownInlineLaTeXPlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), startState_(0), endState_(0) {
    reset();
}

PluginState StreamMarkdownInlineLaTeXPlugin::state() const { return state_; }

bool StreamMarkdownInlineLaTeXPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownInlineLaTeXPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = 0;
    endState_ = 0;
}

bool StreamMarkdownInlineLaTeXPlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::PROCESSING) {
        if (endState_ == 0) {
            if (c == u'$') {
                endState_ = 1;
                reset();
                return includeDelimiters_;
            }
            return true;
        }
        return true;
    }

    if (startState_ == 0) {
        if (c == u'$') {
            startState_ = 1;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        return true;
    }

    if (startState_ == 1) {
        if (c != u'$' && c != u'\n') {
            state_ = PluginState::PROCESSING;
            startState_ = 0;
            endState_ = 0;
            return true;
        }
        reset();
        return true;
    }

    reset();
    return true;
}

// --- Inline LaTeX (\\(...\\)) ---
StreamMarkdownInlineParenLaTeXPlugin::StreamMarkdownInlineParenLaTeXPlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), startState_(0), endState_(0) {
    reset();
}

PluginState StreamMarkdownInlineParenLaTeXPlugin::state() const { return state_; }

bool StreamMarkdownInlineParenLaTeXPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownInlineParenLaTeXPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = 0;
    endState_ = 0;
}

bool StreamMarkdownInlineParenLaTeXPlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::PROCESSING) {
        if (endState_ == 0) {
            if (c == u'\\') {
                endState_ = 1;
                return includeDelimiters_;
            }
            return true;
        }
        if (endState_ == 1) {
            if (c == u')') {
                reset();
                return includeDelimiters_;
            }
            endState_ = 0;
            return true;
        }
        endState_ = 0;
        return true;
    }

    // start matcher: "\\(" then noneOf('\n')
    if (startState_ == 0) {
        if (c == u'\\') {
            startState_ = 1;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        return true;
    }
    if (startState_ == 1) {
        if (c == u'(') {
            startState_ = 2;
            state_ = PluginState::TRYING;
            return includeDelimiters_;
        }
        reset();
        return true;
    }
    if (startState_ == 2) {
        if (c != u'\n') {
            state_ = PluginState::PROCESSING;
            startState_ = 0;
            endState_ = 0;
            return true;
        }
        reset();
        return true;
    }

    reset();
    return true;
}

// --- Block LaTeX ($$...$$) ---
StreamMarkdownBlockLaTeXPlugin::StreamMarkdownBlockLaTeXPlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), startState_(0), endState_(0) {
    reset();
}

PluginState StreamMarkdownBlockLaTeXPlugin::state() const { return state_; }

bool StreamMarkdownBlockLaTeXPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownBlockLaTeXPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = 0;
    endState_ = 0;
    linePrefixOnly_ = true;
}

void StreamMarkdownBlockLaTeXPlugin::advanceLinePrefix(char16_t c) {
    if (c == u'\n') {
        linePrefixOnly_ = true;
    } else if (!(linePrefixOnly_ && (c == u' ' || c == u'\t'))) {
        linePrefixOnly_ = false;
    }
}

bool StreamMarkdownBlockLaTeXPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (atStartOfLine) {
        linePrefixOnly_ = true;
    }

    if (state_ == PluginState::PROCESSING) {
        if (endState_ == 0) {
            if (c == u'$') {
                endState_ = 1;
                return includeDelimiters_;
            }
            return true;
        }
        if (endState_ == 1) {
            if (c == u'$') {
                reset();
                linePrefixOnly_ = false;
                return includeDelimiters_;
            }
            endState_ = 0;
            return true;
        }
        endState_ = 0;
        return true;
    }

    if (startState_ == 0) {
        // $$ is a block delimiter. Restrict its opening edge to the current
        // line prefix so shell snippets such as "kill -9 $$" stay plain text.
        if (c == u'$' && linePrefixOnly_) {
            startState_ = 1;
            state_ = PluginState::TRYING;
            linePrefixOnly_ = false;
            return includeDelimiters_;
        }
        advanceLinePrefix(c);
        return true;
    }
    if (startState_ == 1) {
        if (c == u'$') {
            state_ = PluginState::PROCESSING;
            startState_ = 0;
            endState_ = 0;
            return includeDelimiters_;
        }
        reset();
        advanceLinePrefix(c);
        return true;
    }

    reset();
    return true;
}

// --- Block LaTeX (\\[...\\]) ---
StreamMarkdownBlockBracketLaTeXPlugin::StreamMarkdownBlockBracketLaTeXPlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), startState_(0), endState_(0) {
    reset();
}

PluginState StreamMarkdownBlockBracketLaTeXPlugin::state() const { return state_; }

bool StreamMarkdownBlockBracketLaTeXPlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownBlockBracketLaTeXPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = 0;
    endState_ = 0;
    linePrefixOnly_ = true;
}

void StreamMarkdownBlockBracketLaTeXPlugin::advanceLinePrefix(char16_t c) {
    if (c == u'\n') {
        linePrefixOnly_ = true;
    } else if (!(linePrefixOnly_ && (c == u' ' || c == u'\t'))) {
        linePrefixOnly_ = false;
    }
}

bool StreamMarkdownBlockBracketLaTeXPlugin::processChar(char16_t c, bool atStartOfLine) {
    if (atStartOfLine) {
        linePrefixOnly_ = true;
    }

    if (state_ == PluginState::PROCESSING) {
        if (endState_ == 0) {
            if (c == u'\\') {
                endState_ = 1;
                return includeDelimiters_;
            }
            return true;
        }
        if (endState_ == 1) {
            if (c == u']') {
                reset();
                linePrefixOnly_ = false;
                return includeDelimiters_;
            }
            endState_ = 0;
            return true;
        }
        endState_ = 0;
        return true;
    }

    if (startState_ == 0) {
        // Like $$, \\[ opens a display block only at a line boundary. This
        // prevents escaped shell/path text in tool payloads from becoming math.
        if (c == u'\\' && linePrefixOnly_) {
            startState_ = 1;
            state_ = PluginState::TRYING;
            linePrefixOnly_ = false;
            return includeDelimiters_;
        }
        advanceLinePrefix(c);
        return true;
    }
    if (startState_ == 1) {
        if (c == u'[') {
            state_ = PluginState::PROCESSING;
            startState_ = 0;
            endState_ = 0;
            return includeDelimiters_;
        }
        reset();
        advanceLinePrefix(c);
        return true;
    }

    reset();
    return true;
}

// --- Image ![alt](url) ---
StreamMarkdownImagePlugin::StreamMarkdownImagePlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters), state_(PluginState::IDLE), phase_(0) {
    reset();
}

PluginState StreamMarkdownImagePlugin::state() const { return state_; }

bool StreamMarkdownImagePlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownImagePlugin::reset() {
    state_ = PluginState::IDLE;
    phase_ = 0;
}

bool StreamMarkdownImagePlugin::processChar(char16_t c, bool /*atStartOfLine*/) {
    if (state_ == PluginState::IDLE) {
        if (c == u'!') {
            state_ = PluginState::TRYING;
            phase_ = 1;
            return includeDelimiters_;
        }
        return true;
    }

    if (state_ == PluginState::TRYING || state_ == PluginState::PROCESSING) {
        if (c == u'\n') {
            reset();
            return true;
        }
        // phase: 1 expect '[', 2 in alt until ']', 3 expect '(', 4 in url until ')'
        if (phase_ == 1) {
            if (c == u'[') {
                phase_ = 2;
                state_ = PluginState::PROCESSING;
                return includeDelimiters_;
            }
            reset();
            return true;
        }
        if (phase_ == 2) {
            if (c == u']') {
                phase_ = 3;
                return includeDelimiters_;
            }
            return includeDelimiters_;
        }
        if (phase_ == 3) {
            if (c == u'(') {
                phase_ = 4;
                return includeDelimiters_;
            }
            reset();
            return true;
        }
        if (phase_ == 4) {
            if (c == u')') {
                reset();
                return includeDelimiters_;
            }
            return includeDelimiters_;
        }
    }

    return true;
}

// --- Table ---
StreamMarkdownTablePlugin::StreamMarkdownTablePlugin(bool includeDelimiters)
        : includeDelimiters_(includeDelimiters),
          state_(PluginState::IDLE),
          tableRowCount_(0),
          foundHeaderSeparator_(false),
          headerSepMatchState_(0) {
    reset();
}

PluginState StreamMarkdownTablePlugin::state() const { return state_; }

bool StreamMarkdownTablePlugin::initPlugin() {
    reset();
    return true;
}

void StreamMarkdownTablePlugin::reset() {
    state_ = PluginState::IDLE;
    tableRowCount_ = 0;
    foundHeaderSeparator_ = false;
    headerSepMatchState_ = 0;
}

bool StreamMarkdownTablePlugin::processChar(char16_t c, bool atStartOfLine) {
    if (c == u'\n') {
        if (state_ == PluginState::PROCESSING) {
            state_ = PluginState::WAITFOR;
        }
        return true;
    }

    if (state_ == PluginState::WAITFOR) {
        if (atStartOfLine) {
            if (c == u'|') {
                state_ = PluginState::PROCESSING;
                tableRowCount_ += 1;
                headerSepMatchState_ = 0;
                return includeDelimiters_;
            }
            // other starters end table
            if (c == u'$' || c == u'`' || c == u'#' || c == u'>' || c == u'*' || c == u'-' || c == u'+') {
                reset();
                return true;
            }
            reset();
            return true;
        }
    }

    if (atStartOfLine) {
        if (c == u'|') {
            if (state_ == PluginState::IDLE) {
                state_ = PluginState::PROCESSING;
                tableRowCount_ = 1;
                foundHeaderSeparator_ = false;
            } else if (state_ == PluginState::PROCESSING) {
                tableRowCount_ += 1;
            }
            headerSepMatchState_ = 0;
            return includeDelimiters_;
        }
        if (state_ == PluginState::PROCESSING) {
            reset();
        }
        return true;
    }

    if (state_ == PluginState::PROCESSING) {
        if (tableRowCount_ == 2 && !foundHeaderSeparator_) {
            // Very lightweight header separator detection: accept only - : space |\t
            if (headerSepMatchState_ == 0) {
                // first non-SOL char already processed; keep collecting
                headerSepMatchState_ = 1;
            }
            if (c == u'|' || c == u'-' || c == u':' || c == u' ' || c == u'\t') {
                // keep
            } else {
                // not a separator line
            }
        }

        if (includeDelimiters_) {
            return true;
        }
        return c != u'|';
    }

    return true;
}

} // namespace streamnative
