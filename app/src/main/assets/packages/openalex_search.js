/* METADATA
{
  "name": "openalex_search",
  "display_name": {
    "zh": "OpenAlex 学术搜索",
    "en": "OpenAlex Academic Search"
  },
  "description": {
    "zh": "通过 OpenAlex 官方 API 搜索开放学术索引中的 works，并获取作品、作者和开放访问元数据。",
    "en": "Search works in the OpenAlex scholarly index and retrieve work, author, and open-access metadata through the official API."
  },
  "env": [
    {
      "name": "OPENALEX_API_KEY",
      "description": { "zh": "可选 OpenAlex API Key；配置后通过官方 api_key 查询参数发送。", "en": "Optional OpenAlex API key sent through the official api_key query parameter." },
      "required": false
    }
  ],
  "category": "Academic",
  "enabledByDefault": false,
  "tools": [
    {
      "name": "search",
      "description": { "zh": "按标题、摘要和全文关键词搜索 OpenAlex works；显式 sort 必须同时使用 filter 收窄范围。", "en": "Search OpenAlex works across title, abstract, and full text; an explicit sort requires a filter that narrows the corpus." },
      "parameters": [
        { "name": "search", "description": { "zh": "作品标题、摘要和全文搜索词；默认按 OpenAlex relevance_score 排序。", "en": "Search terms for work titles, abstracts, and full text; defaults to OpenAlex relevance_score ordering." }, "type": "string", "required": true },
        { "name": "per_page", "description": { "zh": "每页数量，默认 10，范围 1-100。", "en": "Results per page; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "page", "description": { "zh": "从 1 开始的页码，默认 1。", "en": "One-based page number; defaults to 1." }, "type": "number", "required": false },
        { "name": "filter", "description": { "zh": "OpenAlex 官方 filter 表达式，例如 publication_year:2024。", "en": "Official OpenAlex filter expression, for example publication_year:2024." }, "type": "string", "required": false },
        { "name": "sort", "description": { "zh": "官方 sort 表达式，例如 cited_by_count:desc；传入时 filter 必填，否则会覆盖主题相关性。", "en": "Official sort expression such as cited_by_count:desc; filter is required because sort replaces topical relevance ordering." }, "type": "string", "required": false },
        { "name": "select", "description": { "zh": "可选逗号分隔轻量顶层字段。", "en": "Optional comma-separated lightweight top-level fields." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_work",
      "description": { "zh": "按 OpenAlex work ID、URL 或 DOI 获取一条紧凑作品记录。", "en": "Retrieve one compact OpenAlex work record by work ID, URL, or DOI." },
      "parameters": [
        { "name": "work_id", "description": { "zh": "例如 W2741809807、https://openalex.org/W2741809807、doi:10.7717/peerj.4375 或 DOI URL。", "en": "For example W2741809807, https://openalex.org/W2741809807, doi:10.7717/peerj.4375, or a DOI URL." }, "type": "string", "required": true },
        { "name": "select", "description": { "zh": "可选逗号分隔轻量顶层字段；未填写也会使用紧凑默认。", "en": "Optional comma-separated lightweight top-level fields; a compact default is always used when omitted." }, "type": "string", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const OpenAlexSearch = (function () {
    const BASE_URL = "https://api.openalex.org";
    const DEFAULT_SEARCH_SELECT = "id,doi,title,authorships,publication_year,publication_date,primary_location,open_access,cited_by_count,type,relevance_score";
    const DEFAULT_WORK_SELECT = "id,doi,title,authorships,publication_year,publication_date,primary_location,open_access,cited_by_count,type,topics";
    const ALLOWED_SELECT_FIELDS = new Set(`${DEFAULT_SEARCH_SELECT},topics,concepts`.split(","));
    const METRIC_NOTICE = "OpenAlex citation counts are source-specific and should only be compared within OpenAlex.";
    const DATA_QUALITY_NOTICE = "OpenAlex aggregates external metadata; verify DOI, title, authorship, affiliation, and anomalous metrics against the publisher or another authoritative source.";
    const MAX_REQUEST_URL_LENGTH = 4094;
    const client = OkHttp.newBuilder()
        .connectTimeout(15000)
        .readTimeout(60000)
        .writeTimeout(30000)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build();
    function errorText(error) {
        if (error instanceof Error)
            return error.message;
        if (typeof error === "string")
            return error;
        const serialized = JSON.stringify(error);
        return serialized ?? "Unknown error";
    }
    function encodeQuery(params) {
        return Object.entries(params)
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join("&");
    }
    function selectedFields(value, defaults) {
        const configured = value?.trim();
        const selected = configured && configured !== "" ? configured : defaults;
        const fields = [];
        const seen = new Set();
        selected.split(",").forEach((rawField) => {
            const field = rawField.trim();
            if (field === "")
                throw new Error("select must not contain empty entries.");
            if (!ALLOWED_SELECT_FIELDS.has(field))
                throw new Error(`Unsupported OpenAlex select field: ${field}.`);
            if (seen.has(field))
                return;
            seen.add(field);
            fields.push(field);
        });
        return fields.join(",");
    }
    function finiteInteger(value, defaultValue, minimum, maximum, name) {
        const candidate = value ?? defaultValue;
        if (!Number.isFinite(candidate))
            throw new Error(`${name} must be a finite number.`);
        return Math.min(Math.max(Math.floor(candidate), minimum), maximum);
    }
    function page(value) {
        return finiteInteger(value, 1, 1, Number.MAX_SAFE_INTEGER, "page");
    }
    function perPage(value) {
        return finiteInteger(value, 10, 1, 100, "per_page");
    }
    function normalizedWorkId(value) {
        const trimmed = value.trim();
        return /^10\.\d{4,9}\/\S+$/i.test(trimmed) ? `doi:${trimmed}` : trimmed;
    }
    function queryParams(params) {
        const result = { ...params };
        const apiKey = getEnv("OPENALEX_API_KEY");
        if (apiKey?.trim())
            result.api_key = apiKey.trim();
        return result;
    }
    async function requestJson(path, params) {
        const query = encodeQuery(queryParams(params));
        const url = `${BASE_URL}${path}${query ? `?${query}` : ""}`;
        if (url.length > MAX_REQUEST_URL_LENGTH) {
            return { success: false, message: `OpenAlex request URL exceeds the official ${MAX_REQUEST_URL_LENGTH}-byte limit; shorten search, filter, sort, or select.` };
        }
        const response = await client.get(url, {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: `OpenAlex request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        return response.json();
    }
    async function search(params) {
        if (!params.search || params.search.trim() === "")
            return { success: false, message: "search is required." };
        const filter = params.filter?.trim();
        const sort = params.sort?.trim();
        if (sort && !filter) {
            return { success: false, message: "OpenAlex sort requires filter when search is present because explicit sorting replaces relevance ordering." };
        }
        const query = {
            search: params.search.trim(),
            per_page: String(perPage(params.per_page)),
            page: String(page(params.page)),
            select: selectedFields(params.select, DEFAULT_SEARCH_SELECT),
        };
        if (filter)
            query.filter = filter;
        if (sort)
            query.sort = sort;
        const payload = await requestJson("/works", query);
        if ("success" in payload)
            return payload;
        return {
            success: true,
            message: `OpenAlex returned ${payload.results.length} work(s).`,
            data: payload.results,
            total: payload.meta.count,
            count: payload.results.length,
            page: payload.meta.page,
            per_page: payload.meta.per_page,
            source: "OpenAlex",
            metric_notice: METRIC_NOTICE,
            data_quality_notice: DATA_QUALITY_NOTICE,
        };
    }
    async function getWork(params) {
        if (!params.work_id || params.work_id.trim() === "")
            return { success: false, message: "work_id is required." };
        const query = { select: selectedFields(params.select, DEFAULT_WORK_SELECT) };
        const workId = normalizedWorkId(params.work_id);
        const payload = await requestJson(`/works/${encodeURIComponent(workId)}`, query);
        if ("success" in payload)
            return payload;
        return {
            success: true,
            message: "OpenAlex returned one compact work record.",
            data: payload,
            count: 1,
            total: 1,
            source: "OpenAlex",
            metric_notice: METRIC_NOTICE,
            data_quality_notice: DATA_QUALITY_NOTICE,
        };
    }
    async function runTool(toolName, action) {
        try {
            complete(await action());
        }
        catch (error) {
            console.error(`openalex_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `OpenAlex request failed: ${errorText(error)}` });
        }
    }
    return {
        search: (params) => runTool("search", () => search(params)),
        get_work: (params) => runTool("get_work", () => getWork(params)),
    };
})();
exports.search = OpenAlexSearch.search;
exports.get_work = OpenAlexSearch.get_work;
