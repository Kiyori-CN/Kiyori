/* METADATA
{
  "name": "brave_search",
  "display_name": {
    "zh": "Brave搜索",
    "en": "Brave Search"
  },
  "description": {
    "zh": "需要 Brave 官方网页/LLM/新闻/视频/图片/地点/富结果、Summarizer、Answers、联想词或拼写检查时使用。",
    "en": "Use for official Brave web/LLM/news/video/image/place/rich search, Summarizer, Answers, suggestions, or spellcheck."
  },
  "env": [
    {
      "name": "BRAVE_SEARCH_API_KEYS",
      "description": { "zh": "搜索类 Key：Web、LLM Context、News、Videos、Images、Local、Place、Rich、Summarizer；多个 Key 用英文逗号分隔。", "en": "Search-product keys for Web, LLM Context, News, Videos, Images, Local, Place, Rich, and Summarizer; comma-separated." },
      "required": true
    },
    {
      "name": "BRAVE_ANSWERS_API_KEYS",
      "description": { "zh": "Answers API Key；多个 Key 用英文逗号分隔。", "en": "Answers API keys; comma-separated." },
      "required": false
    },
    {
      "name": "BRAVE_SUGGEST_API_KEYS",
      "description": { "zh": "Autosuggest API Key；多个 Key 用英文逗号分隔。", "en": "Autosuggest API keys; comma-separated." },
      "required": false
    },
    {
      "name": "BRAVE_SPELLCHECK_API_KEYS",
      "description": { "zh": "Spellcheck API Key；多个 Key 用英文逗号分隔。", "en": "Spellcheck API keys; comma-separated." },
      "required": false
    }
  ],
  "category": "Search",
  "tools": [
    {
      "name": "web_search",
      "description": { "zh": "面向人类阅读的综合网页搜索，可返回网页、地点、讨论、FAQ、富结果提示等。", "en": "Human-facing general web search that can return web, place, discussion, FAQ, and rich-result hints." },
      "parameters": [
        { "name": "q", "description": { "zh": "搜索查询。", "en": "Search query." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "可选官方字段，如 count、offset、country、search_lang、ui_lang、safesearch、freshness、text_decorations、spellcheck、result_filter、goggles、units、extra_snippets、summary、enable_rich_callback。", "en": "Optional official fields such as count, offset, country, search_lang, ui_lang, safesearch, freshness, text_decorations, spellcheck, result_filter, goggles, units, extra_snippets, summary, and enable_rich_callback." }, "type": "object", "required": false },
        { "name": "method", "description": { "zh": "GET（默认）或 POST。", "en": "GET (default) or POST." }, "type": "string", "required": false, "default": "GET" }
      ]
    },
    {
      "name": "llm_context",
      "description": { "zh": "为 AI/Agent 检索排序后的正文片段和来源；需要模型消费搜索上下文时优先于 web_search。", "en": "Retrieve ranked content chunks and sources for AI/agents; prefer this over web_search when a model consumes the context." },
      "parameters": [
        { "name": "q", "description": { "zh": "搜索查询。", "en": "Search query." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "可选官方字段：country、search_lang、count、freshness、maximum_number_of_urls/tokens/snippets、各 URL 限额、context_threshold_mode、safesearch、enable_local、goggles、enable_source_metadata。", "en": "Optional official fields: country, search_lang, count, freshness, maximum URL/token/snippet limits, per-URL limits, context_threshold_mode, safesearch, enable_local, goggles, and enable_source_metadata." }, "type": "object", "required": false },
        { "name": "headers", "description": { "zh": "可选官方定位/版本请求头，如 X-Loc-Lat、X-Loc-Long、X-Loc-City、X-Loc-State、X-Loc-Country、Api-Version；禁止凭据头。", "en": "Optional official location/version headers such as X-Loc-Lat, X-Loc-Long, X-Loc-City, X-Loc-State, X-Loc-Country, and Api-Version; credential headers are forbidden." }, "type": "object", "required": false },
        { "name": "method", "description": { "zh": "GET（默认）或 POST。", "en": "GET (default) or POST." }, "type": "string", "required": false, "default": "GET" }
      ]
    },
    {
      "name": "news_search",
      "description": { "zh": "搜索新闻报道；使用 freshness 等新闻字段控制时效。", "en": "Search news articles, using fields such as freshness for recency." },
      "parameters": [
        { "name": "q", "description": { "zh": "新闻查询。", "en": "News query." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "官方 News Search 参数对象。", "en": "Official News Search parameter object." }, "type": "object", "required": false },
        { "name": "method", "description": { "zh": "GET（默认）或 POST。", "en": "GET (default) or POST." }, "type": "string", "required": false, "default": "GET" }
      ]
    },
    {
      "name": "video_search",
      "description": { "zh": "搜索视频及其缩略图、时长、发布者和播放信息。", "en": "Search videos with thumbnail, duration, publisher, and playback metadata." },
      "parameters": [
        { "name": "q", "description": { "zh": "视频查询。", "en": "Video query." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "官方 Video Search 参数对象。", "en": "Official Video Search parameter object." }, "type": "object", "required": false },
        { "name": "method", "description": { "zh": "GET（默认）或 POST。", "en": "GET (default) or POST." }, "type": "string", "required": false, "default": "GET" }
      ]
    },
    {
      "name": "image_search",
      "description": { "zh": "搜索图片及来源页面、尺寸和缩略图。", "en": "Search images with source page, dimensions, and thumbnails." },
      "parameters": [
        { "name": "q", "description": { "zh": "图片查询。", "en": "Image query." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "官方 Image Search 参数对象。", "en": "Official Image Search parameter object." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "local_pois",
      "description": { "zh": "用 Web/Place Search 返回的临时 POI ID 获取地点详情；ID 约 8 小时后失效。", "en": "Get place details from temporary POI IDs returned by Web/Place Search; IDs expire after about eight hours." },
      "parameters": [
        { "name": "ids", "description": { "zh": "最多 20 个 POI ID。", "en": "Up to 20 POI IDs." }, "type": "array", "required": true },
        { "name": "key_index", "description": { "zh": "产生这些 ID 的 Web/Place Search 结果中的 keyIndex。", "en": "keyIndex from the Web/Place Search result that produced these IDs." }, "type": "number", "required": true },
        { "name": "params", "description": { "zh": "可选 search_lang、ui_lang、units。", "en": "Optional search_lang, ui_lang, and units." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "place_search",
      "description": { "zh": "搜索真实地点、商家、地标或 POI；可用经纬度或 location 定位，q 可省略。", "en": "Search physical places, businesses, landmarks, or POIs; anchor with coordinates or location, and q may be omitted." },
      "parameters": [
        { "name": "params", "description": { "zh": "官方字段：q（可选）、latitude+longitude 或 location、radius、count、country、search_lang、ui_lang、spellcheck。", "en": "Official fields: optional q, latitude+longitude or location, radius, count, country, search_lang, ui_lang, and spellcheck." }, "type": "object", "required": true }
      ]
    },
    {
      "name": "poi_descriptions",
      "description": { "zh": "用临时 POI ID 获取 AI 生成的地点描述。", "en": "Get AI-generated place descriptions from temporary POI IDs." },
      "parameters": [
        { "name": "ids", "description": { "zh": "最多 20 个 POI ID。", "en": "Up to 20 POI IDs." }, "type": "array", "required": true },
        { "name": "key_index", "description": { "zh": "产生这些 ID 的 Web/Place Search 结果中的 keyIndex。", "en": "keyIndex from the Web/Place Search result that produced these IDs." }, "type": "number", "required": true }
      ]
    },
    {
      "name": "rich_search",
      "description": { "zh": "用 web_search(enable_rich_callback=1) 返回的 callback_key 读取天气、股票、体育等富结果。", "en": "Fetch weather, stock, sports, and other rich results using callback_key from web_search(enable_rich_callback=1)." },
      "parameters": [
        { "name": "callback_key", "description": { "zh": "Web Search 返回的不透明 callback_key。", "en": "Opaque callback_key returned by Web Search." }, "type": "string", "required": true },
        { "name": "key_index", "description": { "zh": "产生 callback_key 的 web_search 结果中的 keyIndex。", "en": "keyIndex from the web_search result that produced callback_key." }, "type": "number", "required": true }
      ]
    },
    {
      "name": "summarizer",
      "description": { "zh": "读取旧 Summarizer 流程的聚合或专用结果；只用于仍有 Pro AI 权限的 Key，不作为 Answers 的替代路径。", "en": "Read aggregate or specialized legacy Summarizer results for keys that retain Pro AI access; it is not an Answers substitute path." },
      "parameters": [
        { "name": "resource", "description": { "zh": "search、summary、summary_streaming、title、enrichments、followups 或 entity_info。", "en": "search, summary, summary_streaming, title, enrichments, followups, or entity_info." }, "type": "string", "required": true },
        { "name": "key", "description": { "zh": "web_search(summary=1) 返回的不透明 summarizer.key。", "en": "Opaque summarizer.key returned by web_search(summary=1)." }, "type": "string", "required": true },
        { "name": "key_index", "description": { "zh": "产生 summarizer.key 的 web_search 结果中的 keyIndex。", "en": "keyIndex from the web_search result that produced summarizer.key." }, "type": "number", "required": true },
        { "name": "params", "description": { "zh": "可选官方字段，如 inline_references、entity_info。", "en": "Optional official fields such as inline_references and entity_info." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "answers",
      "description": { "zh": "生成实时联网的直接答案；需要引用或多轮研究时必须 stream=true。", "en": "Generate direct, web-grounded answers; citations or multi-search research require stream=true." },
      "parameters": [
        { "name": "messages", "description": { "zh": "OpenAI 格式消息数组，至少包含用户消息。", "en": "OpenAI-format message array with at least one user message." }, "type": "array", "required": true },
        { "name": "model", "description": { "zh": "brave（默认）或 brave-pro。", "en": "brave (default) or brave-pro." }, "type": "string", "required": false, "default": "brave" },
        { "name": "stream", "description": { "zh": "是否返回解析后的 SSE 事件。引用和 research 模式要求 true。", "en": "Whether to return parsed SSE events. Citations and research mode require true." }, "type": "boolean", "required": false, "default": false },
        { "name": "options", "description": { "zh": "其他官方字段，如 country、language、enable_citations、enable_research 及 API 参考中的 research 限额。", "en": "Other official fields such as country, language, enable_citations, enable_research, and research limits from the API reference." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "autosuggest",
      "description": { "zh": "根据未完成查询返回搜索联想词。", "en": "Return search suggestions for an incomplete query." },
      "parameters": [
        { "name": "q", "description": { "zh": "未完成查询。", "en": "Incomplete query." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "官方 Autosuggest 参数对象。", "en": "Official Autosuggest parameter object." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "spellcheck",
      "description": { "zh": "检查查询拼写并返回修正建议。", "en": "Check query spelling and return corrections." },
      "parameters": [
        { "name": "q", "description": { "zh": "待检查查询。", "en": "Query to check." }, "type": "string", "required": true },
        { "name": "params", "description": { "zh": "官方 Spellcheck 参数对象。", "en": "Official Spellcheck parameter object." }, "type": "object", "required": false }
      ]
    },
    {
      "name": "test_keys",
      "description": { "zh": "按搜索、Answers、Autosuggest、Spellcheck 四类并行检查每个 Key，返回分组脱敏结果。", "en": "Check every key concurrently across Search, Answers, Autosuggest, and Spellcheck categories and return grouped redacted results." },
      "parameters": []
    }
  ]
}
*/

/// <reference path="./types/index.d.ts" />

type BravePrimitive = string | number | boolean | null;
type BraveValue = BravePrimitive | BraveObject | BraveValue[];
interface BraveObject {
    [key: string]: BraveValue | undefined;
}

interface BraveAttempt extends BraveObject {
    category: string;
    keyIndex: number;
    maskedKey: string;
    statusCode: number;
    message: string;
}

interface BraveRequest {
    method: "GET" | "POST";
    path: string;
    params?: BraveObject;
    headers?: BraveObject;
    stream?: boolean;
}

const BraveSearch = (function () {
    const BASE_URL = "https://api.search.brave.com";
    const ENV_BY_CATEGORY: Record<string, string> = {
        search: "BRAVE_SEARCH_API_KEYS",
        answers: "BRAVE_ANSWERS_API_KEYS",
        suggest: "BRAVE_SUGGEST_API_KEYS",
        spellcheck: "BRAVE_SPELLCHECK_API_KEYS"
    };
    const client = OkHttp.newBuilder()
        .connectTimeout(15000)
        .readTimeout(180000)
        .writeTimeout(180000)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build();

    function errorText(error: unknown): string {
        return error instanceof Error ? error.message : String(error);
    }

    function configuredKeys(category: string, required: boolean): string[] {
        const envName = ENV_BY_CATEGORY[category];
        if (!envName) throw new Error("Unknown Brave key category: " + category);
        const raw = getEnv(envName);
        const keys: string[] = [];
        const seen: Record<string, boolean> = {};
        (raw ?? "").split(",").forEach((entry) => {
            const key = entry.trim();
            if (key && !seen[key]) {
                seen[key] = true;
                keys.push(key);
            }
        });
        if (required && keys.length === 0) {
            throw new Error(envName + " is empty; configure one or more comma-separated Brave API keys.");
        }
        return keys;
    }

    function maskedKey(key: string): string {
        if (key.length < 10) return "*".repeat(Math.max(4, key.length));
        return key.slice(0, 4) + "..." + key.slice(-4);
    }

    function nextStartIndex(category: string, keyCount: number): number {
        return Number(Java.callStatic(
            "com.ai.assistance.operit.core.tools.javascript.ApiKeyRoundRobin",
            "nextIndex",
            "brave_search:" + ENV_BY_CATEGORY[category],
            keyCount
        ));
    }

    function sanitize(text: string, keys: string[]): string {
        let sanitized = text;
        keys.forEach((key) => {
            if (key) sanitized = sanitized.split(key).join("[REDACTED]");
        });
        return sanitized.length > 2500 ? sanitized.slice(0, 2500) + "..." : sanitized;
    }

    function cleanParams(params?: BraveObject, reserved?: string[]): BraveObject {
        const result: BraveObject = {};
        const blocked = ["api_key", "x-subscription-token", "authorization"].concat(reserved ?? []);
        if (!params) return result;
        Object.keys(params).forEach((name) => {
            if (blocked.indexOf(name.toLowerCase()) >= 0) {
                throw new Error(name + " must not be supplied in parameters; use the dedicated field or environment configuration.");
            }
            const value = params[name];
            if (value !== undefined) result[name] = value;
        });
        return result;
    }

    function cleanHeaders(headers?: BraveObject): Record<string, string> {
        const result: Record<string, string> = {};
        if (!headers) return result;
        Object.keys(headers).forEach((name) => {
            const normalized = name.toLowerCase();
            if (normalized === "x-subscription-token" || normalized === "authorization") {
                throw new Error("Credential headers must be configured through the matching Brave environment variable.");
            }
            const value = headers[name];
            if (value !== undefined && value !== null) result[name] = String(value);
        });
        return result;
    }

    function encodeQuery(params: BraveObject): string {
        const parts: string[] = [];
        Object.keys(params).forEach((name) => {
            const value = params[name];
            if (value === undefined || value === null) return;
            const values = Array.isArray(value) ? value : [value];
            values.forEach((entry) => {
                const encoded = typeof entry === "object" ? JSON.stringify(entry) : String(entry);
                parts.push(encodeURIComponent(name) + "=" + encodeURIComponent(encoded));
            });
        });
        return parts.length === 0 ? "" : "?" + parts.join("&");
    }

    function parseJson(text: string): BraveValue {
        try {
            return JSON.parse(text) as BraveValue;
        } catch (error) {
            console.error("Brave API returned invalid JSON: " + errorText(error));
            return text;
        }
    }

    function parseSse(text: string): BraveValue[] {
        const events: BraveValue[] = [];
        text.split(/\r?\n/).forEach((line) => {
            if (!line.startsWith("data:")) return;
            const payload = line.slice(5).trim();
            if (!payload || payload === "[DONE]") return;
            try {
                events.push(JSON.parse(payload) as BraveValue);
            } catch (error) {
                console.error("Brave SSE event was not JSON: " + errorText(error));
                events.push(payload);
            }
        });
        return events;
    }

    function method(value?: string): "GET" | "POST" {
        const normalized = (value ?? "GET").toUpperCase();
        if (normalized === "GET" || normalized === "POST") return normalized;
        throw new Error("method must be GET or POST.");
    }

    async function execute(spec: BraveRequest, key: string): Promise<OkHttpResponse> {
        const params = spec.params ?? {};
        const url = BASE_URL + spec.path + (spec.method === "GET" ? encodeQuery(params) : "");
        let builder = client.newRequest()
            .url(url)
            .method(spec.method)
            .headers(cleanHeaders(spec.headers))
            .header("Accept", spec.stream ? "text/event-stream" : "application/json")
            .header("X-Subscription-Token", key);
        if (spec.method === "POST") builder = builder.jsonBody(params);
        const request = builder.build();
        if (spec.stream) return client.streamExecute(request, () => undefined);
        return client.execute(request);
    }

    function isKeyError(statusCode: number): boolean {
        return [401, 402, 403, 429].indexOf(statusCode) >= 0;
    }

    async function requestWithRotation(category: string, spec: BraveRequest, preferredKeyIndex?: number): Promise<BraveObject> {
        const keys = configuredKeys(category, true);
        if (preferredKeyIndex !== undefined && (!Number.isInteger(preferredKeyIndex) || preferredKeyIndex < 1 || preferredKeyIndex > keys.length)) {
            throw new Error("key_index must identify a configured Brave " + category + " key (1-" + keys.length + ").");
        }
        const start = preferredKeyIndex === undefined ? nextStartIndex(category, keys.length) : preferredKeyIndex - 1;
        const attemptCount = preferredKeyIndex === undefined ? keys.length : 1;
        const attempts: BraveAttempt[] = [];

        for (let offset = 0; offset < attemptCount; offset += 1) {
            const keyIndex = (start + offset) % keys.length;
            const key = keys[keyIndex];
            let response: OkHttpResponse;
            try {
                response = await execute(spec, key);
            } catch (error) {
                console.error("Brave network request failed without key switching: " + errorText(error));
                throw error;
            }
            const content = response.text();
            if (response.isSuccessful()) {
                return {
                    success: true,
                    provider: "brave",
                    category,
                    endpoint: spec.path,
                    statusCode: response.statusCode,
                    keyIndex: keyIndex + 1,
                    data: spec.stream ? parseSse(content) : parseJson(content)
                };
            }

            attempts.push({
                category,
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                statusCode: response.statusCode,
                message: sanitize(content || response.statusMessage || "Request failed", keys)
            });
            if (!isKeyError(response.statusCode)) {
                return {
                    success: false,
                    message: "Brave request failed with a business or request error; no key switch was attempted.",
                    provider: "brave",
                    category,
                    endpoint: spec.path,
                    statusCode: response.statusCode,
                    attempts
                };
            }
        }

        return {
            success: false,
            message: preferredKeyIndex === undefined
                ? "All configured Brave " + category + " keys were rejected, rate-limited, or out of quota."
                : "The Brave " + category + " key selected by key_index was rejected, rate-limited, or out of quota.",
            provider: "brave",
            category,
            endpoint: spec.path,
            attempts
        };
    }

    async function runTool(name: string, action: () => Promise<BraveObject>): Promise<BraveObject> {
        try {
            return await action();
        } catch (error) {
            const message = errorText(error);
            console.error("brave_search." + name + " failed: " + message);
            return { success: false, message, provider: "brave", tool: name };
        }
    }

    function querySpec(path: string, q: string, params?: BraveObject, requestedMethod?: string, headers?: BraveObject): BraveRequest {
        return {
            method: method(requestedMethod),
            path,
            params: Object.assign(cleanParams(params, ["q"]), { q }),
            headers
        };
    }

    async function webSearch(params: { q: string; params?: BraveObject; method?: string }): Promise<BraveObject> {
        return runTool("web_search", () => requestWithRotation("search", querySpec("/res/v1/web/search", params.q, params.params, params.method)));
    }

    async function llmContext(params: { q: string; params?: BraveObject; headers?: BraveObject; method?: string }): Promise<BraveObject> {
        return runTool("llm_context", () => requestWithRotation("search", querySpec("/res/v1/llm/context", params.q, params.params, params.method, params.headers)));
    }

    async function newsSearch(params: { q: string; params?: BraveObject; method?: string }): Promise<BraveObject> {
        return runTool("news_search", () => requestWithRotation("search", querySpec("/res/v1/news/search", params.q, params.params, params.method)));
    }

    async function videoSearch(params: { q: string; params?: BraveObject; method?: string }): Promise<BraveObject> {
        return runTool("video_search", () => requestWithRotation("search", querySpec("/res/v1/videos/search", params.q, params.params, params.method)));
    }

    async function imageSearch(params: { q: string; params?: BraveObject }): Promise<BraveObject> {
        return runTool("image_search", () => requestWithRotation("search", querySpec("/res/v1/images/search", params.q, params.params, "GET")));
    }

    async function localPois(params: { ids: string[]; key_index: number; params?: BraveObject }): Promise<BraveObject> {
        return runTool("local_pois", () => requestWithRotation("search", {
            method: "GET",
            path: "/res/v1/local/pois",
            params: Object.assign(cleanParams(params.params, ["ids"]), { ids: params.ids })
        }, params.key_index));
    }

    async function placeSearch(params: { params: BraveObject }): Promise<BraveObject> {
        return runTool("place_search", () => requestWithRotation("search", {
            method: "GET",
            path: "/res/v1/local/place_search",
            params: cleanParams(params.params)
        }));
    }

    async function poiDescriptions(params: { ids: string[]; key_index: number }): Promise<BraveObject> {
        return runTool("poi_descriptions", () => requestWithRotation("search", {
            method: "GET",
            path: "/res/v1/local/descriptions",
            params: { ids: params.ids }
        }, params.key_index));
    }

    async function richSearch(params: { callback_key: string; key_index: number }): Promise<BraveObject> {
        return runTool("rich_search", () => requestWithRotation("search", {
            method: "GET",
            path: "/res/v1/web/rich",
            params: { callback_key: params.callback_key }
        }, params.key_index));
    }

    async function summarizer(params: { resource: string; key: string; key_index: number; params?: BraveObject }): Promise<BraveObject> {
        const allowed = ["search", "summary", "summary_streaming", "title", "enrichments", "followups", "entity_info"];
        const resource = params.resource.toLowerCase();
        return runTool("summarizer", () => {
            if (allowed.indexOf(resource) < 0) throw new Error("Unsupported Summarizer resource: " + resource);
            return requestWithRotation("search", {
                method: "GET",
                path: "/res/v1/summarizer/" + resource,
                params: Object.assign(cleanParams(params.params, ["key"]), { key: params.key }),
                stream: resource === "summary_streaming"
            }, params.key_index);
        });
    }

    async function answers(params: { messages: BraveValue[]; model?: string; stream?: boolean; options?: BraveObject }): Promise<BraveObject> {
        return runTool("answers", () => {
            const options = cleanParams(params.options, ["messages", "model", "stream"]);
            const stream = params.stream === true;
            if ((options.enable_citations === true || options.enable_research === true) && !stream) {
                throw new Error("Brave Answers requires stream=true when enable_citations or enable_research is enabled.");
            }
            return requestWithRotation("answers", {
                method: "POST",
                path: "/res/v1/chat/completions",
                params: Object.assign(options, {
                    messages: params.messages,
                    model: params.model ?? "brave",
                    stream
                }),
                stream
            });
        });
    }

    async function autosuggest(params: { q: string; params?: BraveObject }): Promise<BraveObject> {
        return runTool("autosuggest", () => requestWithRotation("suggest", querySpec("/res/v1/suggest/search", params.q, params.params, "GET")));
    }

    async function spellcheck(params: { q: string; params?: BraveObject }): Promise<BraveObject> {
        return runTool("spellcheck", () => requestWithRotation("spellcheck", querySpec("/res/v1/spellcheck/search", params.q, params.params, "GET")));
    }

    function testSpec(category: string): BraveRequest {
        if (category === "search") return querySpec("/res/v1/web/search", "Kiyori", { count: 1 }, "GET");
        if (category === "answers") {
            return {
                method: "POST",
                path: "/res/v1/chat/completions",
                params: { messages: [{ role: "user", content: "Reply with OK." }], model: "brave", stream: false }
            };
        }
        if (category === "suggest") return querySpec("/res/v1/suggest/search", "Kiyori", { count: 1 }, "GET");
        return querySpec("/res/v1/spellcheck/search", "Kiyori", {}, "GET");
    }

    async function testOneKey(category: string, key: string, keyIndex: number, allKeys: string[]): Promise<BraveObject> {
        const startedAt = Date.now();
        try {
            const response = await execute(testSpec(category), key);
            const content = response.text();
            const success = response.isSuccessful();
            return {
                category,
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success,
                statusCode: response.statusCode,
                durationMs: Date.now() - startedAt,
                message: success ? "Official " + category + " endpoint is reachable." : sanitize(content, allKeys)
            };
        } catch (error) {
            const message = errorText(error);
            console.error("brave_search.test_keys " + category + " key " + (keyIndex + 1) + " failed: " + message);
            return {
                category,
                keyIndex: keyIndex + 1,
                maskedKey: maskedKey(key),
                success: false,
                statusCode: 0,
                durationMs: Date.now() - startedAt,
                message: sanitize(message, allKeys)
            };
        }
    }

    async function testKeys(): Promise<BraveObject> {
        return runTool("test_keys", async () => {
            const categories = ["search", "answers", "suggest", "spellcheck"];
            const categoryKeys: Record<string, string[]> = {};
            const allKeys: string[] = [];
            categories.forEach((category) => {
                categoryKeys[category] = configuredKeys(category, false);
                categoryKeys[category].forEach((key) => allKeys.push(key));
            });

            const checks: Array<Promise<BraveObject>> = [];
            categories.forEach((category) => {
                categoryKeys[category].forEach((key, index) => {
                    checks.push(testOneKey(category, key, index, allKeys));
                });
            });
            const completed = await Promise.all(checks);
            const grouped: BraveObject = {};
            categories.forEach((category) => {
                const results = completed.filter((result) => result.category === category);
                grouped[category] = results.length > 0 ? results : [{
                    category,
                    keyIndex: 0,
                    maskedKey: "",
                    success: false,
                    statusCode: 0,
                    durationMs: 0,
                    message: ENV_BY_CATEGORY[category] + " is not configured."
                }];
            });
            const total = completed.length;
            const passed = completed.filter((result) => result.success === true).length;
            return {
                success: true,
                provider: "brave",
                allKeysReachable: total > 0 && passed === total && categories.every((category) => categoryKeys[category].length > 0),
                total,
                passed,
                groups: grouped
            };
        });
    }

    return {
        web_search: webSearch,
        llm_context: llmContext,
        news_search: newsSearch,
        video_search: videoSearch,
        image_search: imageSearch,
        local_pois: localPois,
        place_search: placeSearch,
        poi_descriptions: poiDescriptions,
        rich_search: richSearch,
        summarizer,
        answers,
        autosuggest,
        spellcheck,
        test_keys: testKeys
    };
})();

exports.web_search = BraveSearch.web_search;
exports.llm_context = BraveSearch.llm_context;
exports.news_search = BraveSearch.news_search;
exports.video_search = BraveSearch.video_search;
exports.image_search = BraveSearch.image_search;
exports.local_pois = BraveSearch.local_pois;
exports.place_search = BraveSearch.place_search;
exports.poi_descriptions = BraveSearch.poi_descriptions;
exports.rich_search = BraveSearch.rich_search;
exports.summarizer = BraveSearch.summarizer;
exports.answers = BraveSearch.answers;
exports.autosuggest = BraveSearch.autosuggest;
exports.spellcheck = BraveSearch.spellcheck;
exports.test_keys = BraveSearch.test_keys;
