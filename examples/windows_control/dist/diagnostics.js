"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.diagnosticMessage = diagnosticMessage;
/** 宿主 bridge 返回普通 {message,data}，跨运行时 Error 也不满足 instanceof Error。 */
function diagnosticMessage(error, fallback, secrets) {
    function extract(value, depth) {
        if (typeof value === "string")
            return value.trim();
        if (!value || typeof value !== "object" || depth > 3)
            return "";
        const record = value;
        for (const key of ["message", "error", "data"]) {
            const message = extract(record[key], depth + 1);
            if (message)
                return message;
        }
        return "";
    }
    let message = extract(error, 0) || fallback;
    // 先脱敏再截断，避免长消息在凭据中间截断后泄露前缀；不序列化整个宿主对象。
    for (const secret of secrets.filter(Boolean)) {
        for (const value of [secret, encodeURIComponent(secret), JSON.stringify(secret).slice(1, -1)]) {
            message = message.split(value).join("[redacted]");
        }
    }
    return message.length > 1200 ? message.slice(0, 1200) + "…" : message;
}
