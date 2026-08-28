/* METADATA
{
  "name": "tavily_search",
  "display_name": {
    "zh": "Tavily 搜索",
    "en": "Tavily Search"
  },
  "description": {
    "zh": "需要 Tavily 的搜索、网页提取、站点爬取/映射、Research 或用量审计时使用。覆盖 Tavily 官方 REST API。",
    "en": "Use for Tavily search, extraction, crawl/map, Research, or usage auditing. Covers the official Tavily REST API."
  },
  "enabledByDefault": false,
  "env": [
    {
      "name": "TAVILY_API_KEYS",
      "description": {
        "zh": "一个或多个 Tavily API Key；多个 Key 用英文逗号分隔并按请求轮询。",
        "en": "One or more Tavily API keys; separate multiple keys with commas for request rotation."
      },
      "required": true
    }
  ],
  "category": "Search",
  "tools": [
    {
      "name": "search",
      "description": {
        "zh": "搜索网页、新闻或金融信息。需要答案、原文、图片、日期/域名过滤或安全搜索时在 options 中传官方字段。",
        "en": "Search web, news, or finance. Put official answer, raw-content, image, date/domain, and safe-search fields in options."
      },
      "parameters": [
        { "name": "query", "description": { "zh": "搜索问题或关键词。", "en": "Search question or query." }, "type": "string", "required": true },
        { "name": "options", "description": { "zh": "可选官方字段：search_depth、chunks_per_source、max_results、topic、time_range、start_date、end_date、include_answer、include_raw_content、include_images、include_image_descriptions、include_favicon、include_domains、exclude_domains、country、auto_parameters、exact_match、include_usage、safe_search。", "en": "Optional official fields: search_depth, chunks_per_source, max_results, topic, time_range, start_date, end_date, include_answer, include_raw_content, include_images, include_image_descriptions, include_favicon, include_domains, exclude_domains, country, auto_parameters, exact_match, include_usage, safe_search." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "extract",
      "description": { "zh": "从一个或多个 URL 提取适合模型使用的正文。", "en": "Extract model-ready content from one or more URLs." },
      "parameters": [
        { "name": "urls", "description": { "zh": "单个 URL 或 URL 数组。", "en": "One URL or an array of URLs." }, "type": "array", "required": true },
        { "name": "options", "description": { "zh": "可选官方字段：query、chunks_per_source、extract_depth、include_images、include_favicon、format、timeout、include_usage。", "en": "Optional official fields: query, chunks_per_source, extract_depth, include_images, include_favicon, format, timeout, include_usage." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "crawl",
      "description": { "zh": "按规则爬取站点并提取页面内容；需要正文时使用。", "en": "Crawl a site under explicit rules and extract page content." },
      "parameters": [
        { "name": "url", "description": { "zh": "爬取根 URL。", "en": "Root URL to crawl." }, "type": "string", "required": true },
        { "name": "options", "description": { "zh": "可选官方字段：instructions、chunks_per_source、max_depth、max_breadth、limit、select_paths、select_domains、exclude_paths、exclude_domains、allow_external、include_images、extract_depth、format、include_favicon、timeout、include_usage。", "en": "Optional official fields: instructions, chunks_per_source, max_depth, max_breadth, limit, select_paths, select_domains, exclude_paths, exclude_domains, allow_external, include_images, extract_depth, format, include_favicon, timeout, include_usage." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "map",
      "description": { "zh": "发现站点 URL 结构而不提取全文。", "en": "Discover a site's URL structure without extracting full content." },
      "parameters": [
        { "name": "url", "description": { "zh": "映射根 URL。", "en": "Root URL to map." }, "type": "string", "required": true },
        { "name": "options", "description": { "zh": "可选官方字段：instructions、max_depth、max_breadth、limit、select_paths、select_domains、exclude_paths、exclude_domains、allow_external、timeout、include_usage。", "en": "Optional official fields: instructions, max_depth, max_breadth, limit, select_paths, select_domains, exclude_paths, exclude_domains, allow_external, timeout, include_usage." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "create_research",
      "description": { "zh": "创建需要综合检索和引用的 Tavily Research；异步结果用 get_research 查询。", "en": "Create Tavily Research for synthesized, cited research; query asynchronous results with get_research." },
      "parameters": [
        { "name": "input", "description": { "zh": "研究问题或任务。", "en": "Research question or task." }, "type": "string", "required": true },
        { "name": "options", "description": { "zh": "可选官方字段：model(mini/pro/auto)、stream、output_schema、citation_format、include_domains、exclude_domains、output_length、files。stream=true 时返回解析后的 SSE 事件。", "en": "Optional official fields: model (mini/pro/auto), stream, output_schema, citation_format, include_domains, exclude_domains, output_length, files. stream=true returns parsed SSE events." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "get_research",
      "description": { "zh": "用 create_research 返回的 request_id 查询研究状态或最终结果。", "en": "Get Research status or final output using the request_id returned by create_research." },
      "parameters": [
        { "name": "request_id", "description": { "zh": "Research 请求 ID。", "en": "Research request ID." }, "type": "string", "required": true },
        { "name": "key_index", "description": { "zh": "create_research 结果中的 keyIndex；Research ID 需要由创建它的同一 Key 查询。", "en": "keyIndex from create_research; a Research ID must be queried with the same key that created it." }, "type": "number", "required": true }
      ]
    },
    {
      "name": "usage",
      "description": { "zh": "读取当前 Key 的账户用量；可按项目过滤。", "en": "Read account usage for the current key, optionally scoped to a project." },
      "parameters": [
        { "name": "project_id", "description": { "zh": "可选 X-Project-ID。", "en": "Optional X-Project-ID." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "logs",
      "description": { "zh": "查询 Tavily 请求用量日志。", "en": "Query Tavily request usage logs." },
      "parameters": [
        { "name": "options", "description": { "zh": "可选官方字段：limit、start_date、end_date、endpoints、project_id、filter_by_api_key。", "en": "Optional official fields: limit, start_date, end_date, endpoints, project_id, filter_by_api_key." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "organization_usage",
      "description": { "zh": "用组织所有者的个人 Key 查询组织级用量。", "en": "Query organization-wide usage with the organization owner's personal key." },
      "parameters": [
        { "name": "organization_name", "description": { "zh": "组织名称。", "en": "Organization name." }, "type": "string", "required": true },
        { "name": "options", "description": { "zh": "可选官方字段：start_date、end_date、project_id、depth。", "en": "Optional official fields: start_date, end_date, project_id, depth." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "test_keys",
      "description": { "zh": "并行检查 TAVILY_API_KEYS 中每个 Key 的官方 Usage 连通性并返回脱敏结果。", "en": "Check every TAVILY_API_KEYS entry concurrently through the official Usage endpoint and return redacted results." },
      "parameters": []
    }
  ]
}
*/

/// <reference path="./types/index.d.ts" />

type JsonPrimitive = string | number | boolean | null;
type JsonValue = JsonPrimitive | JsonObject | JsonValue[];
interface JsonObject {
    [key: string]: JsonValue | undefined;
}

interface ApiAttempt extends JsonObject {
    keyIndex: number;
    maskedKey: string;
    statusCode: number;
    message: string;
}

interface RequestSpec {
    method: "GET" | "POST";
    path: string;
    body?: JsonObject;
    headers?: Record<string, string>;
    stream?: boolean;
}

const TavilySearch = (function () {
    const BASE_URL = "https://api.tavily.com";
    const ENV_NAME = "TAVILY_API_KEYS";
    const ROTATION_NAMESPACE = "tavily_search:TAVILY_API_KEYS";
    const KEY_ERROR_STATUSES = [401, 403, 429, 432, 433];
    const client = OkHttp.newBuilder()
        .connectTimeout(15000)
        .readTimeout(120000)
        .writeTimeout(120000)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build();

    function errorText(error: unknown): string {
        return error instanceof Error ? error.message : String(error);
    }

    function configuredKeys(): string[] {
        const raw = getEnv(ENV_NAME);
        const keys: string[] = [];
        const seen: Record<string, boolean> = {};
        (raw ?? "").split(",").forEach((entry) => {
            const key = entry.trim();
            if (key && !seen[key]) {
                seen[key] = true;
                keys.push(key);
            }
        });
        if (keys.length === 0) {
            throw new Error("TAVILY_API_KEYS is empty; configure one or more comma-separated Tavily API keys.");
        }
        return keys;
    }

    function maskedKey(key: string): string {
        if (key.length < 10) return "*".repeat(Math.max(4, key.length));
        return key.slice(0, 5) + "..." + key.slice(-4);
    }

    function nextStartIndex(keyCount: number): number {
        return Number(Java.callStatic(
            "com.ai.assistance.operit.core.tools.javascript.ApiKeyRoundRobin",
            "nextIndex",
            ROTATION_NAMESPACE,
            keyCount
        ));
    }

    function sanitize(text: string, keys: string[]): string {
        let sanitized = text;
        keys.forEach((key) => {
            if (key) sanitized = sanitized.split(key).join("[REDACTED]");
        });
        return sanitized.length > 2000 ? sanitized.slice(0, 2000) + "..." : sanitized;
    }

    function copyOptions(options?: JsonObject): JsonObject {
        const result: JsonObject = {};
        if (!options) return result;
        Object.keys(options).forEach((key) => {
            if (key.toLowerCase() === "api_key") {
                throw new Error("API credentials must be configured through TAVILY_API_KEYS, not tool parameters.");
            }
            const value = options[key];
            if (value !== undefined) result[key] = value;
        });
        return result;
    }

    function parseJson(text: string): JsonValue {
        try {
            return JSON.parse(text) as JsonValue;
        } catch (error) {
            console.error("Tavily returned non-JSON content: " + errorText(error));
            return text;
        }
    }

    function parseSse(text: string): JsonValue[] {
        const events: JsonValue[] = [];
        text.split(/\r?\n/).forEach((line) => {
            if (!line.startsWith("data:")) return;
            const payload = line.slice(5).trim();
            if (!payload || payload === "[DONE]") return;
            try {
                events.push(JSON.parse(payload) as JsonValue);
            } catch (error) {
                console.error("Tavily Research SSE event was not JSON: " + errorText(error));
                events.push(payload);
            }
        });
        return events;
    }

    async function executeRequest(spec: RequestSpec, key: string): Promise<OkHttpResponse> {
        let builder = client.newRequest()
            .url(BASE_URL + spec.path)
            .method(spec.method)
            .header("Accept", spec.stream ? "text/event-stream" : "application/json")
            .header("Authorization", "Bearer " + key);
        if (spec.headers) builder = builder.headers(spec.headers);
        if (spec.body !== undefined) builder = builder.jsonBody(spec.body);
        const request = builder.build();
        if (spec.stream) {
            return client.streamExecute(request, () => undefined);
        }
        return client.execute(request);
    }

    function isKeyError(statusCode: number, path: string): boolean {
        if (statusCode === 403 && path === "/logs") return false;
        return KEY_ERROR_STATUSES.indexOf(statusCode) >= 0;
    }

    async function requestWithRotation(spec: RequestSpec, preferredKeyIndex?: number): Promise<JsonObject> {
        const keys = configuredKeys();
        if (preferredKeyIndex !== undefined && (!Number.isInteger(preferredKeyIndex) || preferredKeyIndex < 1 || preferredKeyIndex > keys.length)) {
            throw new Error("key_index must identify a configured Tavily key (1-" + keys.length + ").");
        }
        const start = preferredKeyIndex === undefined ? nextStartIndex(keys.length) : preferredKeyIndex - 1;
        const attemptCount = preferredKeyIndex === undefined ? keys.length : 1;
        const attempts: ApiAttempt[] = [];

        for (let offset = 0; offset < attemptCount; offset += 1) {
            const keyIndex = (start + offset) % keys.length;
            const key = keys[keyIndex];
            let response: OkHttpResponse;
            try {
                response = await executeRequest(spec, key);
            } catch (error) {
                console.error("Tavily network request failed without key switching: " + errorText(error));
                throw error;
            }
            const content = response.text();
            if (response.isSuccessful()) {
                return {
                    success: true,
                    provider: "tavily",
                    endpoint: spec.path,
                    statusCode: response.statusCode,
                    keyIndex: keyIndex + 1,
                    data: spec.stream ? parseSse(content) : parseJson(content)
                };
            }

            const message = sanitize(content || response.statusMessage || "Request failed", keys);
            attempts.push({
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                statusCode: response.statusCode,
                message
            });
            if (!isKeyError(response.statusCode, spec.path)) {
                return {
                    success: false,
                    message: "Tavily request failed with a business or request error; no key switch was attempted.",
                    provider: "tavily",
                    endpoint: spec.path,
                    statusCode: response.statusCode,
                    attempts
                };
            }
        }

        return {
            success: false,
            message: preferredKeyIndex === undefined
                ? "All configured Tavily API keys were rejected, rate-limited, or out of quota."
                : "The Tavily API key selected by key_index was rejected, rate-limited, or out of quota.",
            provider: "tavily",
            endpoint: spec.path,
            attempts
        };
    }

    async function runTool(name: string, action: () => Promise<JsonObject>): Promise<JsonObject> {
        try {
            return await action();
        } catch (error) {
            const message = errorText(error);
            console.error("tavily_search." + name + " failed: " + message);
            return { success: false, message, provider: "tavily", tool: name };
        }
    }

    async function search(params: { query: string; options?: JsonObject }): Promise<JsonObject> {
        return runTool("search", () => requestWithRotation({
            method: "POST",
            path: "/search",
            body: Object.assign(copyOptions(params.options), { query: params.query })
        }));
    }

    async function extract(params: { urls: string[]; options?: JsonObject }): Promise<JsonObject> {
        return runTool("extract", () => requestWithRotation({
            method: "POST",
            path: "/extract",
            body: Object.assign(copyOptions(params.options), { urls: params.urls })
        }));
    }

    async function crawl(params: { url: string; options?: JsonObject }): Promise<JsonObject> {
        return runTool("crawl", () => requestWithRotation({
            method: "POST",
            path: "/crawl",
            body: Object.assign(copyOptions(params.options), { url: params.url })
        }));
    }

    async function map(params: { url: string; options?: JsonObject }): Promise<JsonObject> {
        return runTool("map", () => requestWithRotation({
            method: "POST",
            path: "/map",
            body: Object.assign(copyOptions(params.options), { url: params.url })
        }));
    }

    async function createResearch(params: { input: string; options?: JsonObject }): Promise<JsonObject> {
        const options = copyOptions(params.options);
        return runTool("create_research", () => requestWithRotation({
            method: "POST",
            path: "/research",
            body: Object.assign(options, { input: params.input }),
            stream: options.stream === true
        }));
    }

    async function getResearch(params: { request_id: string; key_index: number }): Promise<JsonObject> {
        return runTool("get_research", () => requestWithRotation({
            method: "GET",
            path: "/research/" + encodeURIComponent(params.request_id)
        }, params.key_index));
    }

    async function usage(params: { project_id?: string }): Promise<JsonObject> {
        const headers: Record<string, string> = {};
        if (params.project_id) headers["X-Project-ID"] = params.project_id;
        return runTool("usage", () => requestWithRotation({ method: "GET", path: "/usage", headers }));
    }

    async function logs(params: { options?: JsonObject }): Promise<JsonObject> {
        return runTool("logs", () => requestWithRotation({
            method: "POST",
            path: "/logs",
            body: copyOptions(params.options)
        }));
    }

    async function organizationUsage(params: { organization_name: string; options?: JsonObject }): Promise<JsonObject> {
        return runTool("organization_usage", () => requestWithRotation({
            method: "POST",
            path: "/org-usage",
            body: Object.assign(copyOptions(params.options), { organization_name: params.organization_name })
        }));
    }

    async function testOneKey(key: string, keyIndex: number, allKeys: string[]): Promise<JsonObject> {
        const startedAt = Date.now();
        try {
            const response = await executeRequest({ method: "GET", path: "/usage" }, key);
            const content = response.text();
            return {
                category: "tavily",
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success: response.isSuccessful(),
                statusCode: response.statusCode,
                durationMs: Date.now() - startedAt,
                message: response.isSuccessful() ? "Official Usage endpoint is reachable." : sanitize(content, allKeys)
            };
        } catch (error) {
            const message = errorText(error);
            console.error("tavily_search.test_keys key " + (keyIndex + 1) + " failed: " + message);
            return {
                category: "tavily",
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success: false,
                statusCode: 0,
                durationMs: Date.now() - startedAt,
                message: sanitize(message, allKeys)
            };
        }
    }

    async function testKeys(): Promise<JsonObject> {
        return runTool("test_keys", async () => {
            const keys = configuredKeys();
            const results = await Promise.all(keys.map((key, index) => testOneKey(key, index, keys)));
            const passed = results.filter((result) => result.success === true).length;
            return {
                success: true,
                provider: "tavily",
                allKeysReachable: passed === results.length,
                total: results.length,
                passed,
                results
            };
        });
    }

    return {
        search,
        extract,
        crawl,
        map,
        create_research: createResearch,
        get_research: getResearch,
        usage,
        logs,
        organization_usage: organizationUsage,
        test_keys: testKeys
    };
})();

exports.search = TavilySearch.search;
exports.extract = TavilySearch.extract;
exports.crawl = TavilySearch.crawl;
exports.map = TavilySearch.map;
exports.create_research = TavilySearch.create_research;
exports.get_research = TavilySearch.get_research;
exports.usage = TavilySearch.usage;
exports.logs = TavilySearch.logs;
exports.organization_usage = TavilySearch.organization_usage;
exports.test_keys = TavilySearch.test_keys;
