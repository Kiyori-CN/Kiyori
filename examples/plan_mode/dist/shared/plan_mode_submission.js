"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PlanSubmissionCoordinator = void 0;
const plan_mode_content_js_1 = require("./plan_mode_content.js");
/** 所有窗口的 IPC 进入同一插件实例；先占用，再进行任何异步文件或发送操作。 */
class PlanSubmissionCoordinator {
    constructor() {
        this.receipts = new Map();
        this.busyWorkspaces = new Set();
    }
    workspaceKey(binding) {
        return JSON.stringify([binding.workspaceEnv ?? "android", binding.workspacePath]);
    }
    key(binding, content) {
        return JSON.stringify([binding.chatId, this.workspaceKey(binding), (0, plan_mode_content_js_1.normalizePlanContent)(content)]);
    }
    status(binding, content) {
        return this.receipts.get(this.key(binding, content)) ?? { success: false, status: "idle" };
    }
    async start(binding, content, deps) {
        const frozenBinding = { ...binding };
        const normalized = (0, plan_mode_content_js_1.normalizePlanContent)(content);
        const key = this.key(frozenBinding, normalized);
        const previous = this.receipts.get(key);
        if (previous)
            return previous;
        const workspaceKey = this.workspaceKey(frozenBinding);
        if (this.busyWorkspaces.has(workspaceKey))
            return { success: false, status: "preparing" };
        this.busyWorkspaces.add(workspaceKey);
        this.receipts.set(key, { success: false, status: "preparing" });
        let sideEffectStarted = false;
        try {
            const existing = await deps.read(frozenBinding);
            if (existing?.trim() && (0, plan_mode_content_js_1.normalizePlanContent)(existing) === normalized) {
                // 文件不能证明发送是否成功。重建后的相同计划保持未知，禁止自动再次发送。
                const result = { success: false, status: "unknown" };
                this.receipts.set(key, result);
                return result;
            }
            sideEffectStarted = true;
            await deps.write(frozenBinding, normalized);
            await deps.disable(frozenBinding.chatId);
            await deps.send(frozenBinding);
            const result = { success: true, status: "submitted" };
            this.receipts.set(key, result);
            return result;
        }
        catch (error) {
            // 文件/发送 API 抛错不能证明未产生副作用，保留占用，避免重复实施。
            const result = {
                success: false,
                status: sideEffectStarted ? "unknown" : "idle",
                error: error instanceof Error ? error.message : "Plan submission failed",
            };
            console.error(`[plan_mode] submission failed at ${sideEffectStarted ? "write-or-send" : "read"}`);
            if (sideEffectStarted)
                this.receipts.set(key, result);
            else
                this.receipts.delete(key);
            return result;
        }
        finally {
            this.busyWorkspaces.delete(workspaceKey);
        }
    }
}
exports.PlanSubmissionCoordinator = PlanSubmissionCoordinator;
