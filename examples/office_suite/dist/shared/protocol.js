"use strict";
/**
 * ToolPkg 薄层协议：sentinel 解析、固定错误码、结果归一化。
 *
 * 本文件不含任何 OOXML/PDF 格式逻辑；格式处理全部在 Python 侧。
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.OFFICE_ERROR_CODES = exports.OFFICE_END = exports.OFFICE_BEGIN = void 0;
exports.asText = asText;
exports.parseEnvelope = parseEnvelope;
exports.isRegisteredCode = isRegisteredCode;
exports.toFailure = toFailure;
exports.envelopeFailure = envelopeFailure;
exports.OFFICE_BEGIN = "__KIYORI_OFFICE_BEGIN__";
exports.OFFICE_END = "__KIYORI_OFFICE_END__";
exports.OFFICE_ERROR_CODES = [
    "E_ENV_MISSING",
    "E_PATH_INVALID",
    "E_PATH_EXISTS",
    "E_INPUT_SCHEMA",
    "E_FORMAT_UNSUPPORTED",
    "E_DOC_CORRUPT",
    "E_ANCHOR_NOT_FOUND",
    "E_VALIDATION_FAILED",
    "E_ENGINE_FAILED",
    "E_TIMEOUT",
    "E_BUDGET_EXCEEDED",
    "E_PROTOCOL"
];
function asText(value) {
    return typeof value === "string" ? value : value == null ? "" : String(value);
}
function parseEnvelope(output) {
    const start = output.indexOf(exports.OFFICE_BEGIN);
    const end = output.lastIndexOf(exports.OFFICE_END);
    if (start < 0 || end < 0) {
        throw new Error(`E_PROTOCOL: office runtime sentinel missing; output tail=${output.slice(-400)}`);
    }
    const raw = output.slice(start + exports.OFFICE_BEGIN.length, end);
    let parsed;
    try {
        parsed = JSON.parse(raw);
    }
    catch (error) {
        throw new Error(`E_PROTOCOL: office runtime envelope is not valid JSON: ${asText(error && error.message)}`);
    }
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)
        || typeof parsed.ok !== "boolean"
        || typeof parsed.command !== "string") {
        throw new Error("E_PROTOCOL: office runtime envelope root must be an object");
    }
    return parsed;
}
function isRegisteredCode(code) {
    return exports.OFFICE_ERROR_CODES.indexOf(code) >= 0;
}
/** 把失败信封归一化成模型可读的固定结构，避免错误码在 JS 侧被改写。 */
function toFailure(command, error) {
    const raw = asText(error && error.message ? error.message : error);
    const match = raw.match(/^(E_[A-Z_]+):\s*([\s\S]*)$/);
    const code = match && isRegisteredCode(match[1]) ? match[1] : "E_ENGINE_FAILED";
    return {
        success: false,
        message: match ? match[2] : raw,
        code,
        command
    };
}
function envelopeFailure(envelope) {
    const error = envelope.error;
    if (!error || !isRegisteredCode(error.code)) {
        return toFailure(envelope.command, new Error("E_PROTOCOL: 失败信封缺少已登记的错误码"));
    }
    return { success: false, command: envelope.command, code: error.code,
        message: `${error.code}: ${error.message}${error.remedy ? `\n${error.remedy}` : ""}`,
        detail: error.detail, remedy: error.remedy,
        data: { ...envelope.data, error: { ...error } } };
}
