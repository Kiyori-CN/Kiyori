"use strict";
/* METADATA
{
    "name": "ctx_limiter_c",
    "display_name": {
        "zh": "上下文限制器",
        "en": "Context Limiter"
    },
    "description": {
        "zh": "为无缓存模型保留系统消息和最近 N 层用户/助手对话，减少发送给模型的历史上下文。",
        "en": "For uncached models, keep system messages and the latest N user/assistant turns to reduce transmitted conversation history."
    },
    "category": "Chat",
    "enabledByDefault": false,
    "env": [
        {
            "name": "CTX_LIMITER_C_FLOOR_LIMIT",
            "description": {
                "zh": "保留最近多少层对话，默认 5",
                "en": "How many recent turns to keep, default 5"
            },
            "required": false
        },
        {
            "name": "CTX_LIMITER_C_ENABLED",
            "description": {
                "zh": "是否启用上下文限制器，true/false，默认 true",
                "en": "Whether the limiter is enabled, true/false, default true"
            },
            "required": false
        }
    ],
    "tools": [
        {
            "name": "set_floor_limit",
            "description": {
                "zh": "设置保留的最近楼层数",
                "en": "Set how many recent turns to keep"
            },
            "parameters": [
                {
                    "name": "n",
                    "description": {
                        "zh": "保留最近N个楼层（默认5）",
                        "en": "Keep the latest N turns (default 5)"
                    },
                    "type": "number",
                    "required": true
                }
            ]
        },
        {
            "name": "get_floor_limit",
            "description": {
                "zh": "查看当前楼层数限制",
                "en": "Get the current turn limit"
            },
            "parameters": []
        }
    ]
}
*/
Object.defineProperty(exports, "__esModule", { value: true });
exports.set_floor_limit = set_floor_limit;
exports.get_floor_limit = get_floor_limit;
const constants_1 = require("../constants");
async function set_floor_limit(params) {
    const limit = (0, constants_1.parseFloorLimit)(params?.n);
    if (limit === undefined) {
        complete({ success: false, message: "n 必须是大于 0 的整数", error: "INVALID_ARGUMENT" });
        return;
    }
    try {
        await Tools.SoftwareSettings.writeEnvironmentVariable(constants_1.ENV_KEYS.floorLimit, `${limit}`);
    }
    catch (_error) {
        console.error('context_limiter_c: floor limit persistence failed');
        complete({ success: false, message: '保存楼层数失败，请检查设置后重试。', error: 'WRITE_FAILED' });
        return;
    }
    complete({ success: true, floor_limit: limit, message: `已设置保留最近 ${limit} 个楼层` });
}
function get_floor_limit() {
    let current = constants_1.DEFAULT_FLOOR_LIMIT;
    if (typeof getEnv === "function") {
        const raw = getEnv(constants_1.ENV_KEYS.floorLimit);
        current = (0, constants_1.parseFloorLimit)(raw) ?? constants_1.DEFAULT_FLOOR_LIMIT;
    }
    complete({
        success: true,
        floor_limit: current,
        message: `当前保留最近 ${current} 个楼层`,
    });
}
