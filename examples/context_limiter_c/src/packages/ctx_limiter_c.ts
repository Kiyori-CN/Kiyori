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

import { DEFAULT_FLOOR_LIMIT, ENV_KEYS } from "../constants";

interface FloorLimitParams {
  n?: string | number;
}

function normalizeLimit(value: string | number | undefined): number {
  const parsed = Number.parseInt(String(value), 10);
  return parsed;
}

export function set_floor_limit(params: FloorLimitParams) {
  const limit = normalizeLimit(params.n);
  if (!Number.isFinite(limit) || limit < 1) {
    complete({ success: false, error: "n 必须是大于 0 的整数" });
    return;
  }

  Tools.SoftwareSettings.writeEnvironmentVariable(ENV_KEYS.floorLimit, String(limit))
    .then(() => {
      complete({
        success: true,
        floor_limit: limit,
        message: `已设置保留最近 ${limit} 个楼层`,
      });
    })
    .catch((error: unknown) => {
      complete({
        success: false,
        error: error instanceof Error ? error.message : String(error),
      });
    });
}

export function get_floor_limit() {
  let current = DEFAULT_FLOOR_LIMIT;
  if (typeof getEnv === "function") {
    const raw = getEnv(ENV_KEYS.floorLimit);
    const parsed = Number.parseInt(String(raw ?? ""), 10);
    if (Number.isFinite(parsed) && parsed > 0) {
      current = parsed;
    }
  }

  complete({
    success: true,
    floor_limit: current,
    message: `当前保留最近 ${current} 个楼层`,
  });
}
