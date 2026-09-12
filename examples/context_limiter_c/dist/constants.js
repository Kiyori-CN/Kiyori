"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PROMPT_TURN_KIND = exports.INPUT_MENU_TOGGLE_ACTION = exports.TOGGLE_IDS = exports.HOOK_IDS = exports.ENV_KEYS = exports.FLOOR_OPTIONS = exports.DEFAULT_LIMITER_ENABLED = exports.DEFAULT_FLOOR_LIMIT = void 0;
exports.parseFloorLimit = parseFloorLimit;
exports.DEFAULT_FLOOR_LIMIT = 5;
// UI、Hook 和工具共用整数解释，不能把 1.5 / 5abc 静默截成合法层数。
function parseFloorLimit(value) {
    if ((typeof value !== 'string' && typeof value !== 'number') || (typeof value === 'string' && !/^\d+$/.test(value.trim())))
        return undefined;
    const parsed = Number(value);
    return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}
exports.DEFAULT_LIMITER_ENABLED = true;
exports.FLOOR_OPTIONS = [3, 5, 8, 10, 15, 20, 30, 50, 100];
exports.ENV_KEYS = {
    floorLimit: "CTX_LIMITER_C_FLOOR_LIMIT",
    enabled: "CTX_LIMITER_C_ENABLED",
};
exports.HOOK_IDS = {
    finalize: "ctx_limiter_c_finalize",
    menu: "ctx_limiter_c_menu",
};
exports.TOGGLE_IDS = {
    limiter: "ctx_limiter_toggle",
    adjust: "ctx_limiter_adjust",
};
exports.INPUT_MENU_TOGGLE_ACTION = {
    create: "create",
    toggle: "toggle",
};
exports.PROMPT_TURN_KIND = {
    SYSTEM: "SYSTEM",
    USER: "USER",
    ASSISTANT: "ASSISTANT",
};
