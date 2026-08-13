package com.ai.assistance.operit.api.chat.llmprovider

internal object OpenAIHostedWebSearchAnswerNormalizer {
    data class NormalizedCitation(
        val sourceId: String,
        val title: String,
        val url: String,
        val startIndex: Int,
        val endIndex: Int,
    )

    data class Result(
        val answer: String,
        val citations: List<NormalizedCitation>,
    )

    fun normalize(
        answer: String,
        citations: List<OpenAIHostedWebSearchCitation>,
    ): Result {
        val removed = BooleanArray(answer.length)
        val protected = BooleanArray(answer.length)
        markProtectedCode(answer, protected)
        EMPTY_MARKDOWN_LINK.findAll(answer).forEach { match ->
            if (match.range.all { index -> !protected[index] }) {
                match.range.forEach { index -> removed[index] = true }
            }
        }

        val prefixRemoved = IntArray(answer.length + 1)
        val normalizedAnswer = StringBuilder(answer.length)
        answer.forEachIndexed { index, character ->
            prefixRemoved[index + 1] = prefixRemoved[index] + if (removed[index]) 1 else 0
            if (!removed[index]) {
                normalizedAnswer.append(character)
            }
        }

        val normalizedCitations =
            citations.map { citation ->
                val coveredRemoved =
                    (citation.startIndex until citation.endIndex).any { index ->
                        index !in answer.indices || removed[index]
                    }
                if (coveredRemoved) {
                    throw OpenAIHostedWebSearchException(
                        code = OpenAIHostedWebSearchErrorCode.CITATION_INVALID,
                        message =
                            "OpenAI Web Search citation overlaps removed Markdown syntax.",
                    )
                }
                NormalizedCitation(
                    sourceId = citation.sourceId,
                    title = citation.title,
                    url = citation.url,
                    startIndex = citation.startIndex - prefixRemoved[citation.startIndex],
                    endIndex = citation.endIndex - prefixRemoved[citation.endIndex],
                )
            }
        return Result(
            answer = normalizedAnswer.toString(),
            citations = normalizedCitations,
        )
    }

    private fun markProtectedCode(answer: String, protected: BooleanArray) {
        var index = 0
        var fenced = false
        var inline = false
        while (index < answer.length) {
            if (answer.startsWith("```", index)) {
                val end = minOf(answer.length, index + 3)
                for (protectedIndex in index until end) {
                    protected[protectedIndex] = true
                }
                fenced = !fenced
                index = end
                continue
            }
            if (!fenced && answer[index] == '`') {
                protected[index] = true
                inline = !inline
                index += 1
                continue
            }
            if (fenced || inline) {
                protected[index] = true
            }
            index += 1
        }
    }

    private val EMPTY_MARKDOWN_LINK =
        Regex("""!?\[\]\(\s*\)""")
}
