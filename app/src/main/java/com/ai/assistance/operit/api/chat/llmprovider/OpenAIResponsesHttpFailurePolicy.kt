package com.ai.assistance.operit.api.chat.llmprovider

/**
 * Background Responses 在取得 response ID 前后具有不同的重试安全边界。
 *
 * 提交阶段只有 429 能明确说明本次请求被限流拒绝；408、409 与 5xx 都可能发生在服务端已接受
 * 请求之后，自动重新 POST 会创建第二次长推理。取得 response ID 后，重试始终是同一 response
 * 的 GET，因此瞬时错误可以安全重试。
 */
internal object OpenAIResponsesHttpFailurePolicy {
    enum class SubmissionAction {
        RETRY_EXPLICIT_REJECTION,
        FAIL,
        SUBMISSION_UNKNOWN,
    }

    enum class ResumeAction {
        RETRY_SAME_RESPONSE,
        FAIL,
        EXPIRE,
    }

    fun classifySubmission(statusCode: Int): SubmissionAction =
        when {
            statusCode == 429 -> SubmissionAction.RETRY_EXPLICIT_REJECTION
            statusCode == 408 || statusCode == 409 || statusCode in 500..599 ->
                SubmissionAction.SUBMISSION_UNKNOWN
            else -> SubmissionAction.FAIL
        }

    fun classifyResume(statusCode: Int): ResumeAction =
        when {
            statusCode == 404 || statusCode == 410 -> ResumeAction.EXPIRE
            statusCode == 408 ||
                statusCode == 409 ||
                statusCode == 429 ||
                statusCode in 500..599 -> ResumeAction.RETRY_SAME_RESPONSE
            else -> ResumeAction.FAIL
        }
}
