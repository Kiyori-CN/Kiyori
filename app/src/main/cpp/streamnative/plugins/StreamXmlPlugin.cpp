#include "StreamXmlPlugin.h"

namespace streamnative {

StreamXmlPlugin::StreamXmlPlugin(bool includeTagsInOutput, bool allowProtocolTagsAnywhere)
        : includeTagsInOutput_(includeTagsInOutput),
          allowProtocolTagsAnywhere_(allowProtocolTagsAnywhere),
          state_(PluginState::IDLE),
          startState_(StartState::WAIT_LT),
          allowStartAfterEndTag_(false),
          allowStartAfterPunctuation_(false),
          haveEndPattern_(false) {
    reset();
}

PluginState StreamXmlPlugin::state() const {
    return state_;
}

bool StreamXmlPlugin::initPlugin() {
    reset();
    return true;
}

void StreamXmlPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = StartState::WAIT_LT;
    tagName_.clear();
    endMatcher_.reset();
    endPattern_.clear();
    haveEndPattern_ = false;
    lastChar_ = 0;
    unanchoredStartCandidate_ = false;
    attributeQuote_ = 0;
}

bool StreamXmlPlugin::isAsciiLetter(char16_t c) {
    return (c >= u'A' && c <= u'Z') || (c >= u'a' && c <= u'z');
}

bool StreamXmlPlugin::isTagNameContinuationChar(char16_t c) {
    return isAsciiLetter(c) || (c >= u'0' && c <= u'9') || c == u'_';
}

bool StreamXmlPlugin::isPunctuationTrigger(char16_t c) {
    switch (c) {
        case u'\uFF0C': // ，
        case u'\u3002': // 。
        case u'\uFF1F': // ？
        case u'\uFF01': // ！
        case u'\uFF1A': // ：
        case u'\uFF08': // （
        case u'\uFF09': // ）
        case u'\u3010': // 【
        case u'\u3011': // 】
        case u'\u300A': // 《
        case u'\u300B': // 》
        case u':':
        case u',':
        case u'.':
        case u'?':
        case u'!':
        case u'~':
        case u'\uFF5E': // ～
            return true;
        default:
            return false;
    }
}

bool StreamXmlPlugin::isEmojiTrigger(char16_t c) {
    // Most modern emojis are surrogate pairs in UTF-16.
    if (c >= u'\xD800' && c <= u'\xDFFF') {
        return true;
    }

    // Common BMP emoji/symbol blocks (e.g. ☀, ❤, ✨, etc.).
    if ((c >= u'\x2300' && c <= u'\x23FF') ||
        (c >= u'\x2600' && c <= u'\x27BF') ||
        (c >= u'\x2B00' && c <= u'\x2BFF')) {
        return true;
    }

    return false;
}

bool StreamXmlPlugin::isEmojiContinuationChar(char16_t c) {
    switch (c) {
        case u'\u200D': // ZERO WIDTH JOINER
        case u'\uFE0E': // text presentation selector
        case u'\uFE0F': // emoji presentation selector
        case u'\u20E3': // combining enclosing keycap
            return true;
        default:
            return false;
    }
}

bool StreamXmlPlugin::isProtocolTagName(const std::u16string& tagName) {
    std::u16string lower;
    lower.reserve(tagName.size());
    for (char16_t c : tagName) {
        if (c >= u'A' && c <= u'Z') {
            lower.push_back(static_cast<char16_t>(c - u'A' + u'a'));
        } else {
            lower.push_back(c);
        }
    }

    if (lower == u"tool" || lower == u"tool_result") {
        return true;
    }

    if (lower.rfind(u"tool_result_", 0) == 0) {
        return lower.size() > std::u16string(u"tool_result_").size();
    }

    if (lower.rfind(u"tool_", 0) == 0) {
        const std::u16string suffix = lower.substr(std::u16string(u"tool_").size());
        // Match ChatMarkupRegex: a generic tool suffix may contain "result" only
        // when it is not the reserved `result` token or its underscored form.
        return !suffix.empty() &&
               !(suffix == u"result" || suffix.rfind(u"result_", 0) == 0);
    }

    // These are the protocol tags consumed by the chat projection/rendering
    // layer. Unknown XML-like prose stays anchored and therefore remains text.
    return lower == u"think" || lower == u"thinking" || lower == u"search" ||
           lower == u"status" || lower == u"html" || lower == u"mood" ||
           lower == u"font" || lower == u"details" || lower == u"detail" ||
           lower == u"meta" || lower == u"plan" || lower == u"emotion" ||
           lower == u"memory" || lower == u"reply_to" || lower == u"attachment" ||
           lower == u"workspace_attachment" || lower == u"proxy_sender";
}

bool StreamXmlPlugin::handleDefaultCharacter(char16_t c) {
    updatePunctuationAllowance(c);
    return true;
}

void StreamXmlPlugin::updatePunctuationAllowance(char16_t c) {
    if (isPunctuationTrigger(c) || isEmojiTrigger(c)) {
        allowStartAfterPunctuation_ = true;
    } else if (c == u' ' || c == u'\t' || isEmojiContinuationChar(c)) {
        // keep
    } else {
        allowStartAfterPunctuation_ = false;
    }
}

bool StreamXmlPlugin::processStartMatcher(char16_t c) {
    switch (startState_) {
        case StartState::WAIT_LT: {
            if (c == u'<') {
                tagName_.clear();
                startState_ = StartState::WAIT_FIRST_LETTER;
                state_ = PluginState::TRYING;
            }
            return false;
        }
        case StartState::WAIT_FIRST_LETTER: {
            if (isAsciiLetter(c)) {
                tagName_.push_back(c);
                startState_ = StartState::IN_TAG_NAME;
                state_ = PluginState::TRYING;
                return false;
            }
            startState_ = StartState::WAIT_LT;
            state_ = PluginState::IDLE;
            return false;
        }
        case StartState::IN_TAG_NAME: {
            if (c == u' ' || c == u'\t' || c == u'\r' || c == u'\n') {
                startState_ = StartState::IN_ATTRS;
                state_ = PluginState::TRYING;
                return false;
            }
            if (c == u'>') {
                if (unanchoredStartCandidate_ && !isProtocolTagName(tagName_)) {
                    reset();
                    return false;
                }
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::TRYING;
                return true;
            }
            if (!isTagNameContinuationChar(c)) {
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::IDLE;
                tagName_.clear();
                return false;
            }
            tagName_.push_back(c);
            state_ = PluginState::TRYING;
            return false;
        }
        case StartState::IN_ATTRS: {
            if (attributeQuote_ != 0) {
                if (c == attributeQuote_) {
                    attributeQuote_ = 0;
                }
                return false;
            }
            if (c == u'"' || c == u'\'') {
                attributeQuote_ = c;
                return false;
            }
            if (c == u'>') {
                if (unanchoredStartCandidate_ && !isProtocolTagName(tagName_)) {
                    reset();
                    return false;
                }
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::TRYING;
                return true;
            }
            state_ = PluginState::TRYING;
            return false;
        }
    }
    return false;
}

void StreamXmlPlugin::buildEndPattern() {
    endPattern_.clear();
    endPattern_.reserve(tagName_.size() + 3);
    endPattern_.push_back(u'<');
    endPattern_.push_back(u'/');
    for (const char16_t c : tagName_) {
        if (c >= u'A' && c <= u'Z') {
            endPattern_.push_back(static_cast<char16_t>(c - u'A' + u'a'));
        } else {
            endPattern_.push_back(c);
        }
    }
    endPattern_.push_back(u'>');
    endMatcher_.setPattern(endPattern_);
    haveEndPattern_ = true;
}

bool StreamXmlPlugin::processChar(char16_t c, bool atStartOfLine) {
    const char16_t prevChar = lastChar_;
    auto finish = [&](bool result) {
        lastChar_ = c;
        return result;
    };

    if (state_ == PluginState::PROCESSING) {
        if (haveEndPattern_) {
            char16_t endMatchChar = c;
            if (endMatchChar >= u'A' && endMatchChar <= u'Z') {
                endMatchChar = static_cast<char16_t>(endMatchChar - u'A' + u'a');
            }
            if (endMatcher_.process(endMatchChar)) {
                allowStartAfterEndTag_ = true;
                allowStartAfterPunctuation_ = false;
                reset();
                return finish(includeTagsInOutput_);
            }
        }
        return finish(includeTagsInOutput_);
    }

    if (state_ == PluginState::IDLE && !atStartOfLine) {
        const bool allowStart = allowStartAfterEndTag_ || allowStartAfterPunctuation_;
        if (!allowStart) {
            if (allowProtocolTagsAnywhere_ && c == u'<') {
                // Defer the decision until the complete opening tag is known;
                // this avoids turning ordinary comparisons such as "a < b"
                // into XML while still protecting protocol tags in prose.
                unanchoredStartCandidate_ = true;
            } else {
                return finish(handleDefaultCharacter(c));
            }
        }
        if (c == u' ' || c == u'\t' || isEmojiContinuationChar(c)) {
            return finish(handleDefaultCharacter(c));
        }
    }

    const PluginState previousState = state_;
    const bool startMatched = processStartMatcher(c);

    if (startMatched) {
        if (prevChar == u'/') {
            // Treat self-closing tags like <br/> as plain text to avoid entering XML mode.
            reset();
            return finish(true);
        }
        state_ = PluginState::PROCESSING;
        allowStartAfterEndTag_ = false;
        allowStartAfterPunctuation_ = false;
        buildEndPattern();
        startState_ = StartState::WAIT_LT;
        return finish(includeTagsInOutput_);
    }

    if (state_ == PluginState::TRYING) {
        allowStartAfterPunctuation_ = false;
        return finish(includeTagsInOutput_);
    }

    if (previousState == PluginState::TRYING) {
        reset();
    }
    allowStartAfterEndTag_ = false;
    allowStartAfterPunctuation_ = false;
    return finish(handleDefaultCharacter(c));
}

} // namespace streamnative
