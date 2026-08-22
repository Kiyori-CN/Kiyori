/* METADATA
{
  "name": "zhipu_search",
  "display_name": {
    "zh": "智谱 搜索",
    "en": "Zhipu Search"
  },
  "description": {
    "zh": "需要智谱独立 Web Search API 的结构化全网搜索、多引擎选择或意图识别时使用。",
    "en": "Use for structured web search, engine selection, or intent detection through Zhipu's standalone Web Search API."
  },
  "env": [
    {
      "name": "ZHIPU_SEARCH_API_KEYS",
      "description": {
        "zh": "一个或多个智谱 API Key；多个 Key 用英文逗号分隔并按请求轮询。",
        "en": "One or more Zhipu API keys; separate multiple keys with commas for request rotation."
      },
      "required": true
    }
  ],
  "category": "Search",
  "tools": [
    {
      "name": "search",
      "description": {
        "zh": "调用智谱官方独立 Web Search，返回原始结构化意图和搜索结果。",
        "en": "Call the official standalone Zhipu Web Search and return its structured intent and search results."
      },
      "parameters": [
        { "name": "search_query", "description": { "zh": "搜索内容，官方建议不超过 70 个字符。", "en": "Search query; the official recommendation is at most 70 characters." }, "type": "string", "required": true },
        { "name": "search_engine", "description": { "zh": "search_std（默认）、search_pro、search_pro_sogou 或 search_pro_quark。", "en": "search_std (default), search_pro, search_pro_sogou, or search_pro_quark." }, "type": "string", "required": false, "default": "search_std" },
        { "name": "search_intent", "description": { "zh": "是否先识别搜索意图；false（默认）表示直接搜索。", "en": "Whether to detect search intent first; false (default) searches directly." }, "type": "boolean", "required": false, "default": false },
        { "name": "options", "description": { "zh": "可选官方字段：count、search_domain_filter、search_recency_filter、content_size、request_id、user_id。", "en": "Optional official fields: count, search_domain_filter, search_recency_filter, content_size, request_id, and user_id." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "test_keys",
      "description": { "zh": "并行执行最小 search_std 请求，检查 ZHIPU_SEARCH_API_KEYS 中每个 Key 并返回脱敏结果。", "en": "Run a minimal search_std request for every ZHIPU_SEARCH_API_KEYS entry concurrently and return redacted results." },
      "parameters": []
    }
  ]
}
*/
/// <reference path="./types/index.d.ts" />
const ZhipuSearch = (function () {
    const API_URL = "https://open.bigmodel.cn/api/paas/v4/web_search";
    const ENV_NAME = "ZHIPU_SEARCH_API_KEYS";
    const ROTATION_NAMESPACE = "zhipu_search:ZHIPU_SEARCH_API_KEYS";
    const KEY_ERROR_CODES = [
        "1000", "1001", "1003", "1005", "1113", "1302", "1308", "1309", "1310",
        "1311", "1313", "1314", "1315", "1316", "1317", "1318", "1319", "1320", "1321"
    ];
    const client = OkHttp.newBuilder()
        .connectTimeout(15000)
        .readTimeout(90000)
        .writeTimeout(90000)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build();
    function errorText(error) {
        return error instanceof Error ? error.message : String(error);
    }
    function configuredKeys() {
        const raw = getEnv(ENV_NAME);
        const keys = [];
        const seen = {};
        (raw ?? "").split(",").forEach((entry) => {
            const key = entry.trim();
            if (key && !seen[key]) {
                seen[key] = true;
                keys.push(key);
            }
        });
        if (keys.length === 0) {
            throw new Error("ZHIPU_SEARCH_API_KEYS is empty; configure one or more comma-separated Zhipu API keys.");
        }
        return keys;
    }
    function maskedKey(key) {
        if (key.length < 10)
            return "*".repeat(Math.max(4, key.length));
        return key.slice(0, 4) + "..." + key.slice(-4);
    }
    function nextStartIndex(keyCount) {
        return Number(Java.callStatic("com.ai.assistance.operit.core.tools.javascript.ApiKeyRoundRobin", "nextIndex", ROTATION_NAMESPACE, keyCount));
    }
    function sanitize(text, keys) {
        let sanitized = text;
        keys.forEach((key) => {
            if (key)
                sanitized = sanitized.split(key).join("[REDACTED]");
        });
        return sanitized.length > 2000 ? sanitized.slice(0, 2000) + "..." : sanitized;
    }
    function parseJson(text) {
        try {
            return JSON.parse(text);
        }
        catch (error) {
            console.error("Zhipu Web Search returned invalid JSON: " + errorText(error));
            return text;
        }
    }
    function errorCode(content) {
        try {
            const parsed = JSON.parse(content);
            return parsed.error && parsed.error.code !== undefined ? String(parsed.error.code) : "";
        }
        catch (error) {
            console.error("Zhipu error body was not JSON: " + errorText(error));
            return "";
        }
    }
    function isKeyError(statusCode, content) {
        const code = errorCode(content);
        if (code === "1305")
            return false;
        if (code && KEY_ERROR_CODES.indexOf(code) >= 0)
            return true;
        return !code && [401, 403, 429].indexOf(statusCode) >= 0;
    }
    function requestBody(params) {
        const body = {};
        if (params.options) {
            Object.keys(params.options).forEach((name) => {
                const normalized = name.toLowerCase();
                if (["api_key", "search_query", "search_engine", "search_intent"].indexOf(normalized) >= 0) {
                    throw new Error(name + " must not be supplied in options; use the dedicated field or environment configuration.");
                }
                const value = params.options && params.options[name];
                if (value !== undefined)
                    body[name] = value;
            });
        }
        body.search_query = params.search_query;
        body.search_engine = params.search_engine ?? "search_std";
        body.search_intent = params.search_intent === true;
        return body;
    }
    async function execute(body, key) {
        const request = client.newRequest()
            .url(API_URL)
            .method("POST")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + key)
            .jsonBody(body)
            .build();
        return client.execute(request);
    }
    async function requestWithRotation(body) {
        const keys = configuredKeys();
        const start = nextStartIndex(keys.length);
        const attempts = [];
        for (let offset = 0; offset < keys.length; offset += 1) {
            const keyIndex = (start + offset) % keys.length;
            const key = keys[keyIndex];
            let response;
            try {
                response = await execute(body, key);
            }
            catch (error) {
                console.error("Zhipu network request failed without key switching: " + errorText(error));
                throw error;
            }
            const content = response.text();
            if (response.isSuccessful()) {
                return {
                    success: true,
                    provider: "zhipu",
                    endpoint: "/api/paas/v4/web_search",
                    statusCode: response.statusCode,
                    keyIndex: keyIndex + 1,
                    data: parseJson(content)
                };
            }
            const credentialFailure = isKeyError(response.statusCode, content);
            attempts.push({
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                statusCode: response.statusCode,
                message: sanitize(content || response.statusMessage || "Request failed", keys)
            });
            if (!credentialFailure) {
                return {
                    success: false,
                    message: "Zhipu request failed with a business, safety, concurrency, or service error; no key switch was attempted.",
                    provider: "zhipu",
                    endpoint: "/api/paas/v4/web_search",
                    statusCode: response.statusCode,
                    attempts
                };
            }
        }
        return {
            success: false,
            message: "All configured Zhipu API keys were rejected, account-limited, or out of quota.",
            provider: "zhipu",
            endpoint: "/api/paas/v4/web_search",
            attempts
        };
    }
    async function runTool(name, action) {
        try {
            return await action();
        }
        catch (error) {
            const message = errorText(error);
            console.error("zhipu_search." + name + " failed: " + message);
            return { success: false, message, provider: "zhipu", tool: name };
        }
    }
    async function search(params) {
        return runTool("search", () => requestWithRotation(requestBody(params)));
    }
    async function testOneKey(key, keyIndex, allKeys) {
        const startedAt = Date.now();
        const body = {
            search_query: "Kiyori",
            search_engine: "search_std",
            search_intent: false,
            count: 1,
            content_size: "medium"
        };
        try {
            const response = await execute(body, key);
            const content = response.text();
            const success = response.isSuccessful();
            return {
                category: "zhipu",
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success,
                statusCode: response.statusCode,
                durationMs: Date.now() - startedAt,
                message: success ? "Official Web Search API is reachable." : sanitize(content, allKeys)
            };
        }
        catch (error) {
            const message = errorText(error);
            console.error("zhipu_search.test_keys key " + (keyIndex + 1) + " failed: " + message);
            return {
                category: "zhipu",
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success: false,
                statusCode: 0,
                durationMs: Date.now() - startedAt,
                message: sanitize(message, allKeys)
            };
        }
    }
    async function testKeys() {
        return runTool("test_keys", async () => {
            const keys = configuredKeys();
            const results = await Promise.all(keys.map((key, index) => testOneKey(key, index, keys)));
            const passed = results.filter((result) => result.success === true).length;
            return {
                success: true,
                provider: "zhipu",
                allKeysReachable: passed === results.length,
                total: results.length,
                passed,
                results
            };
        });
    }
    return { search, test_keys: testKeys };
})();
exports.search = ZhipuSearch.search;
exports.test_keys = ZhipuSearch.test_keys;
