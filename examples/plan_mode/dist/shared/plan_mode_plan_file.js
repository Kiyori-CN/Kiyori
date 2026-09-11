"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizePlanContent = void 0;
exports.resolvePlanFileBinding = resolvePlanFileBinding;
exports.hasPlanFile = hasPlanFile;
exports.readPlanFile = readPlanFile;
exports.readBoundPlanFile = readBoundPlanFile;
exports.writePlanFile = writePlanFile;
exports.writeBoundPlanFile = writeBoundPlanFile;
exports.deletePlanFile = deletePlanFile;
const plan_mode_constants_js_1 = require("./plan_mode_constants.js");
const plan_mode_content_js_1 = require("./plan_mode_content.js");
var plan_mode_content_js_2 = require("./plan_mode_content.js");
Object.defineProperty(exports, "normalizePlanContent", { enumerable: true, get: function () { return plan_mode_content_js_2.normalizePlanContent; } });
const plan_mode_workspace_js_1 = require("./plan_mode_workspace.js");
function fileEnvironment(binding) {
    const environment = binding.workspaceEnv ?? "android";
    if (environment === "android" || environment === "linux")
        return environment;
    throw new Error("Unsupported workspace file environment");
}
function resolvePlanFileBinding(chatId) {
    const binding = (0, plan_mode_workspace_js_1.resolveChatWorkspace)(chatId);
    if (!binding) {
        return null;
    }
    return {
        ...binding,
        path: (0, plan_mode_workspace_js_1.buildPlanFilePath)(binding.workspacePath),
    };
}
async function hasPlanFile(chatId) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        return false;
    }
    const result = await Tools.Files.exists(binding.path, fileEnvironment(binding));
    return result.exists;
}
async function readPlanFile(chatId) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        return null;
    }
    return readBoundPlanFile(binding);
}
async function readBoundPlanFile(binding) {
    const environment = fileEnvironment(binding);
    const exists = await Tools.Files.exists(binding.path, environment);
    if (!exists.exists) {
        return null;
    }
    const result = await Tools.Files.read({ path: binding.path, environment });
    return {
        ...binding,
        content: result.content.replace(/\r\n/g, "\n"),
    };
}
async function writePlanFile(chatId, content) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        throw new Error("workspace is not bound");
    }
    return writeBoundPlanFile(binding, content);
}
async function writeBoundPlanFile(binding, content) {
    const normalized = (0, plan_mode_content_js_1.normalizePlanContent)(content);
    const environment = fileEnvironment(binding);
    await Tools.Files.mkdir(`${binding.workspacePath}/${plan_mode_constants_js_1.PLAN_FILE_DIRECTORY_NAME}`, true, environment);
    await Tools.Files.write(binding.path, normalized, false, environment);
    return {
        ...binding,
        path: binding.path,
        content: normalized,
    };
}
async function deletePlanFile(chatId) {
    const binding = resolvePlanFileBinding(chatId);
    if (!binding) {
        throw new Error("workspace is not bound");
    }
    const environment = fileEnvironment(binding);
    const exists = await Tools.Files.exists(binding.path, environment);
    if (exists.exists) {
        await Tools.Files.deleteFile(binding.path, false, environment);
    }
    return {
        chatId: binding.chatId,
        workspacePath: binding.workspacePath,
        path: binding.path,
        deleted: exists.exists,
    };
}
