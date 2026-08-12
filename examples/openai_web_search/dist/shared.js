"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.strings = strings;
exports.errorMessage = errorMessage;
const ZH = {
    title: "OpenAI Web Search",
    subtitle: "独立的 OpenAI Responses hosted web_search 搜索绑定",
    independenceNotice: "主聊天模型不会改变本插件的 endpoint、模型、Key 或配置来源。DeepSeek、Gemini、Claude、本地模型等都可以调用同一个搜索工具。",
    localValidation: "本地配置校验",
    validateButton: "校验本地配置",
    validating: "正在校验…",
    probeTitle: "中转站兼容探测",
    probeWarning: "只在 RESPONSES_RELAY_STRICT 模式使用。此按钮会真实调用配置的远端接口，可能产生费用；不会自动触发。",
    probeButton: "运行可能计费的兼容探测",
    probing: "正在探测…",
    currentBinding: "当前搜索绑定",
    configurationIncomplete: "配置未完成",
    validationPassed: "本地配置有效，未发送网络请求。",
    probePassed: "兼容探测通过，已记录当前绑定指纹。",
    noStatus: "尚未读取到有效绑定状态。",
    compatibilityNotRequired: "无需探测",
    compatibilityMissing: "尚未探测",
    compatibilityStale: "需要重新探测",
    compatibilityValid: "探测有效",
    compatibilityFailed: "最近探测失败",
    compatibilityMissingHint: "当前严格中转绑定尚未运行兼容探测。普通 search 会返回 RELAY_PROBE_REQUIRED；请确认费用后点击下方按钮。",
    compatibilityStaleHint: "endpoint、模型、认证、headers、reasoning、联网模式或响应 schema 已变化。必须重新运行兼容探测。",
    compatibilityFailedHint: "最近一次真实兼容探测未满足完整 Responses Web Search 证据合同。修正中转站后重新运行探测。",
    probeErrorCode: "错误码",
    probeHttpStatus: "HTTP 状态",
    probeFailureMessage: "失败原因",
    providerErrorType: "上游错误类型",
    providerErrorCode: "上游错误码",
    providerRequestId: "上游请求 ID",
    evidenceMode: "探测证据",
    evidenceModeCitationsAndSources: "URL 引用与完整来源",
    evidenceModeCitations: "URL 引用",
    evidenceModeSources: "完整 URL 来源",
    evidenceModeFeeds: "结构化实时数据",
};
const EN = {
    title: "OpenAI Web Search",
    subtitle: "Independent OpenAI Responses hosted web_search binding",
    independenceNotice: "The primary chat model never changes this plugin's endpoint, model, key, or configuration source. DeepSeek, Gemini, Claude, local models, and others can call the same search tool.",
    localValidation: "Local configuration validation",
    validateButton: "Validate local configuration",
    validating: "Validating…",
    probeTitle: "Relay compatibility probe",
    probeWarning: "Use only with RESPONSES_RELAY_STRICT. This button sends a real request to the configured endpoint and may incur charges; it is never triggered automatically.",
    probeButton: "Run potentially billable probe",
    probing: "Probing…",
    currentBinding: "Current search binding",
    configurationIncomplete: "Configuration incomplete",
    validationPassed: "Local configuration is valid. No network request was sent.",
    probePassed: "Compatibility probe passed and recorded the current binding fingerprint.",
    noStatus: "No valid binding status has been loaded.",
    compatibilityNotRequired: "Not required",
    compatibilityMissing: "Not probed",
    compatibilityStale: "Probe required again",
    compatibilityValid: "Probe valid",
    compatibilityFailed: "Last probe failed",
    compatibilityMissingHint: "This strict relay binding has not been probed. Ordinary search returns RELAY_PROBE_REQUIRED until you explicitly run the potentially billable probe below.",
    compatibilityStaleHint: "The endpoint, model, authentication, headers, reasoning, web access, or response schema changed. Run the compatibility probe again.",
    compatibilityFailedHint: "The last real compatibility probe did not satisfy the complete Responses Web Search evidence contract. Correct the relay and run the probe again.",
    probeErrorCode: "Error code",
    probeHttpStatus: "HTTP status",
    probeFailureMessage: "Failure reason",
    providerErrorType: "Provider error type",
    providerErrorCode: "Provider error code",
    providerRequestId: "Provider request ID",
    evidenceMode: "Probe evidence",
    evidenceModeCitationsAndSources: "URL citations and full sources",
    evidenceModeCitations: "URL citations",
    evidenceModeSources: "Full URL sources",
    evidenceModeFeeds: "Structured live data",
};
function strings() {
    const language = typeof getLang === "function" ? getLang().trim().toLowerCase() : "";
    return language.startsWith("en") ? EN : ZH;
}
function errorMessage(error) {
    const message = error.message.trim() || "OpenAI Web Search operation failed";
    const errorWithCode = error;
    const code = typeof errorWithCode.code === "string" ? errorWithCode.code.trim() : "";
    return code && !message.startsWith(`[${code}]`) ? `[${code}] ${message}` : message;
}
