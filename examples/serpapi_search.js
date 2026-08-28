/* METADATA
{
  "name": "serpapi_search",
  "display_name": {
    "zh": "SerpApi 搜索",
    "en": "SerpApi Search"
  },
  "description": {
    "zh": "需要调用 SerpApi 任意搜索引擎、读取搜索归档、查询地点或账户额度时使用。engine 与 params 直接对应官方参数。",
    "en": "Use for any SerpApi search engine, search archives, locations, or account usage. engine and params map directly to official parameters."
  },
  "enabledByDefault": false,
  "env": [
    {
      "name": "SERPAPI_API_KEYS",
      "description": {
        "zh": "一个或多个 SerpApi API Key；多个 Key 用英文逗号分隔并按请求轮询。",
        "en": "One or more SerpApi API keys; separate multiple keys with commas for request rotation."
      },
      "required": true
    }
  ],
  "category": "Search",
  "tools": [
    {
      "name": "search",
      "description": {
        "zh": "调用任意 SerpApi 引擎。使用官方 engine 名，其他引擎参数放入 params；可返回 JSON、原始 HTML 或 Markdown。",
        "en": "Call any SerpApi engine. Use an official engine name and put all other engine parameters in params; returns JSON, raw HTML, or Markdown."
      },
      "parameters": [
        { "name": "engine", "description": { "zh": "官方引擎名，例如 google、google_scholar、bing、baidu、search_index。", "en": "Official engine name, e.g. google, google_scholar, bing, baidu, or search_index." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "该引擎的官方参数对象，包括 q、location、hl、gl、async、no_cache、zero_trace、json_restrictor 等；禁止 api_key、engine、output。", "en": "Official engine parameters such as q, location, hl, gl, async, no_cache, zero_trace, and json_restrictor; api_key, engine, and output are forbidden here." }, "type": "object", "required": false },
        { "name": "output", "description": { "zh": "输出格式：json（默认）、html 或 markdown。", "en": "Output format: json (default), html, or markdown." }, "type": "string", "required": false, "default": "json" }
      ]
    },
    {
      "name": "get_search",
      "description": { "zh": "按 search_id 读取已完成或异步搜索归档。", "en": "Retrieve a completed or asynchronous search archive by search_id." },
      "parameters": [
        { "name": "search_id", "description": { "zh": "SerpApi 搜索 ID。", "en": "SerpApi search ID." }, "type": "string", "required": true },
        { "name": "key_index", "description": { "zh": "search 结果中的 keyIndex；归档必须使用发起搜索的同一 Key。", "en": "keyIndex from search; the archive must use the same key that started the search." }, "type": "number", "required": true },
        { "name": "output", "description": { "zh": "归档格式：json（默认）、html 或 pixel（带位置的 JSON）。", "en": "Archive format: json (default), html, or pixel-position JSON." }, "type": "string", "required": false, "default": "json" }
      ]
    },
    {
      "name": "locations",
      "description": { "zh": "查询 SerpApi 支持的规范地点；用于生成搜索的 location 参数。", "en": "Query canonical SerpApi locations for a later search location parameter." },
      "parameters": [
        { "name": "query", "description": { "zh": "可选地点关键词。", "en": "Optional location query." }, "type": "string", "required": false },
        { "name": "limit", "description": { "zh": "可选最大结果数。", "en": "Optional maximum result count." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "account",
      "description": { "zh": "读取轮询选中 Key 的账户、套餐、搜索额度与当月用量。", "en": "Read account, plan, search quota, and current usage for the rotated key." },
      "parameters": []
    },
    {
      "name": "test_keys",
      "description": { "zh": "并行调用官方 Account API，检查 SERPAPI_API_KEYS 中每个 Key 并返回脱敏结果。", "en": "Check every SERPAPI_API_KEYS entry concurrently through the official Account API and return redacted results." },
      "parameters": []
    }
  ]
}
*/
/// <reference path="./types/index.d.ts" />
const SerpApiSearch = (function () {
    const BASE_URL = "https://serpapi.com";
    const ENV_NAME = "SERPAPI_API_KEYS";
    const ROTATION_NAMESPACE = "serpapi_search:SERPAPI_API_KEYS";
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
            throw new Error("SERPAPI_API_KEYS is empty; configure one or more comma-separated SerpApi API keys.");
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
    function encodeQuery(params) {
        const parts = [];
        Object.keys(params).forEach((name) => {
            const value = params[name];
            if (value === undefined || value === null)
                return;
            const values = Array.isArray(value) ? value : [value];
            values.forEach((entry) => {
                const encoded = typeof entry === "object" ? JSON.stringify(entry) : String(entry);
                parts.push(encodeURIComponent(name) + "=" + encodeURIComponent(encoded));
            });
        });
        return parts.length === 0 ? "" : "?" + parts.join("&");
    }
    function searchParams(engine, params) {
        const result = { engine };
        if (!params)
            return result;
        Object.keys(params).forEach((name) => {
            const normalized = name.toLowerCase();
            if (["api_key", "serp_api_key", "engine", "output"].indexOf(normalized) >= 0) {
                throw new Error(name + " must not be supplied in params; use the dedicated tool field or environment configuration.");
            }
            const value = params[name];
            if (value !== undefined)
                result[name] = value;
        });
        return result;
    }
    function parseJson(text) {
        try {
            return JSON.parse(text);
        }
        catch (error) {
            console.error("SerpApi returned invalid JSON: " + errorText(error));
            return text;
        }
    }
    function keyError(statusCode, content) {
        if ([401, 403, 429].indexOf(statusCode) >= 0)
            return true;
        const normalized = content.toLowerCase();
        return normalized.indexOf("invalid api key") >= 0 ||
            normalized.indexOf("out of searches") >= 0 ||
            normalized.indexOf("account limit") >= 0 ||
            normalized.indexOf("rate limit") >= 0;
    }
    async function requestWithKey(path, query, key) {
        const fullQuery = {};
        Object.keys(query).forEach((name) => { fullQuery[name] = query[name]; });
        fullQuery.api_key = key;
        return client.get(BASE_URL + path + encodeQuery(fullQuery), { "Accept": "*/*" });
    }
    async function requestWithRotation(path, query, expectsJson, preferredKeyIndex) {
        const keys = configuredKeys();
        if (preferredKeyIndex !== undefined && (!Number.isInteger(preferredKeyIndex) || preferredKeyIndex < 1 || preferredKeyIndex > keys.length)) {
            throw new Error("key_index must identify a configured SerpApi key (1-" + keys.length + ").");
        }
        const start = preferredKeyIndex === undefined ? nextStartIndex(keys.length) : preferredKeyIndex - 1;
        const attemptCount = preferredKeyIndex === undefined ? keys.length : 1;
        const attempts = [];
        for (let offset = 0; offset < attemptCount; offset += 1) {
            const keyIndex = (start + offset) % keys.length;
            const key = keys[keyIndex];
            let response;
            try {
                response = await requestWithKey(path, query, key);
            }
            catch (error) {
                console.error("SerpApi network request failed without key switching: " + errorText(error));
                throw error;
            }
            const content = response.text();
            const credentialFailure = keyError(response.statusCode, content);
            if (response.isSuccessful() && !credentialFailure) {
                return {
                    success: true,
                    provider: "serpapi",
                    endpoint: path,
                    statusCode: response.statusCode,
                    keyIndex: keyIndex + 1,
                    data: expectsJson ? parseJson(content) : content
                };
            }
            attempts.push({
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                statusCode: response.statusCode,
                message: sanitize(content || response.statusMessage || "Request failed", keys)
            });
            if (!credentialFailure) {
                return {
                    success: false,
                    message: "SerpApi request failed with a business or request error; no key switch was attempted.",
                    provider: "serpapi",
                    endpoint: path,
                    statusCode: response.statusCode,
                    attempts
                };
            }
        }
        return {
            success: false,
            message: preferredKeyIndex === undefined
                ? "All configured SerpApi keys were rejected, rate-limited, or out of searches."
                : "The SerpApi key selected by key_index was rejected, rate-limited, or out of searches.",
            provider: "serpapi",
            endpoint: path,
            attempts
        };
    }
    async function runTool(name, action) {
        try {
            return await action();
        }
        catch (error) {
            const message = errorText(error);
            console.error("serpapi_search." + name + " failed: " + message);
            return { success: false, message, provider: "serpapi", tool: name };
        }
    }
    function searchPath(output) {
        const normalized = (output ?? "json").toLowerCase();
        if (normalized === "json")
            return { path: "/search.json", json: true };
        if (normalized === "html")
            return { path: "/search", json: false };
        if (normalized === "markdown" || normalized === "md")
            return { path: "/search.md", json: false };
        throw new Error("output must be json, html, or markdown.");
    }
    function archivePath(searchId, output) {
        const base = "/searches/" + encodeURIComponent(searchId);
        const normalized = (output ?? "json").toLowerCase();
        if (normalized === "json")
            return { path: base + ".json", json: true };
        if (normalized === "html")
            return { path: base, json: false };
        if (normalized === "pixel")
            return { path: base + ".json_with_pixel_position", json: true };
        throw new Error("output must be json, html, or pixel.");
    }
    async function search(params) {
        return runTool("search", () => {
            const target = searchPath(params.output);
            return requestWithRotation(target.path, searchParams(params.engine, params.params), target.json);
        });
    }
    async function getSearch(params) {
        return runTool("get_search", () => {
            const target = archivePath(params.search_id, params.output);
            return requestWithRotation(target.path, {}, target.json, params.key_index);
        });
    }
    async function locations(params) {
        const query = {};
        if (params.query)
            query.q = params.query;
        if (params.limit !== undefined)
            query.limit = params.limit;
        return runTool("locations", () => requestWithRotation("/locations.json", query, true));
    }
    async function account() {
        return runTool("account", () => requestWithRotation("/account.json", {}, true));
    }
    async function testOneKey(key, keyIndex, allKeys) {
        const startedAt = Date.now();
        try {
            const response = await requestWithKey("/account.json", {}, key);
            const content = response.text();
            const success = response.isSuccessful() && !keyError(response.statusCode, content);
            return {
                category: "serpapi",
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success,
                statusCode: response.statusCode,
                durationMs: Date.now() - startedAt,
                message: success ? "Official Account API is reachable." : sanitize(content, allKeys)
            };
        }
        catch (error) {
            const message = errorText(error);
            console.error("serpapi_search.test_keys key " + (keyIndex + 1) + " failed: " + message);
            return {
                category: "serpapi",
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
                provider: "serpapi",
                allKeysReachable: passed === results.length,
                total: results.length,
                passed,
                results
            };
        });
    }
    return { search, get_search: getSearch, locations, account, test_keys: testKeys };
})();
exports.search = SerpApiSearch.search;
exports.get_search = SerpApiSearch.get_search;
exports.locations = SerpApiSearch.locations;
exports.account = SerpApiSearch.account;
exports.test_keys = SerpApiSearch.test_keys;
