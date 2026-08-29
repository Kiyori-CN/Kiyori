"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.strings = strings;
exports.errorMessage = errorMessage;
const ZH = {
    title: "OpenAI 搜索",
    subtitle: "独立的 OpenAI Responses hosted web_search 搜索绑定",
    independenceNotice: "主聊天模型不会改变本插件的 endpoint、模型、Key 或配置来源。DeepSeek、Gemini、Claude、本地模型等都可以调用同一个搜索工具。",
    localValidation: "本地配置校验",
    validateButton: "校验本地配置",
    validating: "正在校验…",
    probeTitle: "可选中转站兼容探测",
    probeWarning: "仅用于 RESPONSES_RELAY_STRICT 的可选诊断。环境变量配置有效后普通 search 可直接使用；此按钮会真实调用远端接口，可能产生费用。",
    probeButton: "运行可能计费的兼容探测",
    probing: "正在探测…",
    currentBinding: "当前搜索绑定",
    openConfiguration: "打开原生配置",
    openingConfiguration: "正在打开…",
    readiness: "配置就绪状态",
    configurationIncomplete: "配置未完成",
    validationPassed: "本地配置有效，未发送网络请求。",
    probePassed: "兼容探测通过，已记录当前绑定指纹。",
    noStatus: "尚未读取到有效绑定状态。",
    compatibilityNotRequired: "无需探测",
    compatibilityMissing: "尚未探测",
    compatibilityStale: "需要重新探测",
    compatibilityValid: "探测有效",
    compatibilityFailed: "最近探测失败",
    compatibilityMissingHint: "当前严格中转绑定尚未运行兼容探测。配置有效后普通 search 可直接使用；探测只用于验证中转站的 Web Search 证据合同。",
    compatibilityStaleHint: "endpoint、模型、认证、headers、reasoning、联网模式或响应 schema 已变化。普通 search 仍按当前配置执行；如需更新诊断记录，可重新运行兼容探测。",
    compatibilityFailedHint: "最近一次兼容探测未满足完整 Responses Web Search 证据合同。普通 search 仍会返回真实结果或结构化错误；修正中转站后可再次运行探测。",
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
    evidenceModeNone: "没有可验证网页证据",
};
const EN = {
    title: "OpenAI Search",
    subtitle: "Independent OpenAI Responses hosted web_search binding",
    independenceNotice: "The primary chat model never changes this plugin's endpoint, model, key, or configuration source. DeepSeek, Gemini, Claude, local models, and others can call the same search tool.",
    localValidation: "Local configuration validation",
    validateButton: "Validate local configuration",
    validating: "Validating…",
    probeTitle: "Optional relay compatibility probe",
    probeWarning: "Optional diagnostics for RESPONSES_RELAY_STRICT. Ordinary search is available as soon as the environment is valid; this button sends a real request and may incur charges.",
    probeButton: "Run potentially billable probe",
    probing: "Probing…",
    currentBinding: "Current search binding",
    openConfiguration: "Open native configuration",
    openingConfiguration: "Opening…",
    readiness: "Configuration readiness",
    configurationIncomplete: "Configuration incomplete",
    validationPassed: "Local configuration is valid. No network request was sent.",
    probePassed: "Compatibility probe passed and recorded the current binding fingerprint.",
    noStatus: "No valid binding status has been loaded.",
    compatibilityNotRequired: "Not required",
    compatibilityMissing: "Not probed",
    compatibilityStale: "Probe required again",
    compatibilityValid: "Probe valid",
    compatibilityFailed: "Last probe failed",
    compatibilityMissingHint: "This strict relay binding has not been probed. Ordinary search is available when the environment is valid; the probe only verifies the relay's Web Search evidence contract.",
    compatibilityStaleHint: "The endpoint, model, authentication, headers, reasoning, web access, or response schema changed. Ordinary search still uses the current valid binding; rerun the probe only to refresh diagnostics.",
    compatibilityFailedHint: "The last compatibility probe did not satisfy the complete Responses Web Search evidence contract. Ordinary search still returns the real result or a structured error; correct the relay before probing again.",
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
    evidenceModeNone: "No verifiable web evidence",
};
function strings() {
    const language = typeof getLang === "function" ? getLang().trim().toLowerCase() : "";
    return language.startsWith("en") ? EN : ZH;
}
function errorMessage(error) {
    const message = error.message.trim() || "OpenAI Search operation failed";
    const errorWithCode = error;
    const code = typeof errorWithCode.code === "string" ? errorWithCode.code.trim() : "";
    return code && !message.startsWith(`[${code}]`) ? `[${code}] ${message}` : message;
}
