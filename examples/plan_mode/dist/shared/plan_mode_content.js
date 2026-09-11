"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizePlanContent = normalizePlanContent;
function normalizePlanContent(content) {
    const normalized = content.replace(/\r\n/g, "\n").trim();
    if (!normalized)
        throw new Error("plan content is empty");
    return `${normalized}\n`;
}
