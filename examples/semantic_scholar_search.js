/* METADATA
{
  "name": "semantic_scholar_search",
  "display_name": {
    "zh": "Semantic Scholar 学术搜索",
    "en": "Semantic Scholar Academic Search"
  },
  "description": {
    "zh": "通过 Semantic Scholar 官方 Academic Graph API 搜索论文并获取论文详情、引用统计和开放访问字段。",
    "en": "Search papers and retrieve paper details, citation metrics, and open-access fields through the official Semantic Scholar Academic Graph API."
  },
  "env": [
    {
      "name": "SEMANTIC_SCHOLAR_API_KEY",
      "description": { "zh": "可选 Semantic Scholar API Key；配置后通过 x-api-key 请求头发送。", "en": "Optional Semantic Scholar API key sent through the x-api-key request header." },
      "required": false
    }
  ],
  "category": "Academic",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "search",
      "description": { "zh": "按关键词搜索 Semantic Scholar 论文，返回指定官方 fields。", "en": "Search Semantic Scholar papers by keyword and return the requested official fields." },
      "parameters": [
        { "name": "query", "description": { "zh": "论文搜索关键词。", "en": "Paper search query." }, "type": "string", "required": true },
        { "name": "limit", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "offset", "description": { "zh": "结果起始偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false },
        { "name": "fields", "description": { "zh": "可选逗号分隔官方字段；未填写时返回标题、作者、摘要、年份、标识、链接和引用统计。", "en": "Optional comma-separated official fields; defaults to title, authors, abstract, year, identifiers, links, and citation metrics." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_paper",
      "description": { "zh": "按 Semantic Scholar paperId、DOI、ArXiv ID、PMID 等官方 paper identifier 获取详情。", "en": "Retrieve paper details by a supported Semantic Scholar identifier such as paperId, DOI, arXiv ID, or PMID." },
      "parameters": [
        { "name": "paper_id", "description": { "zh": "例如 DOI:10.1038/nature12373、ARXIV:2403.02240 或 Semantic Scholar paperId。", "en": "For example DOI:10.1038/nature12373, ARXIV:2403.02240, or a Semantic Scholar paperId." }, "type": "string", "required": true },
        { "name": "fields", "description": { "zh": "可选逗号分隔官方字段。", "en": "Optional comma-separated official fields." }, "type": "string", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const SemanticScholarSearch = (function () {
    const BASE_URL = "https://api.semanticscholar.org/graph/v1";
    const DEFAULT_FIELDS = "paperId,title,abstract,year,authors,url,externalIds,citationCount,referenceCount,influentialCitationCount,isOpenAccess,openAccessPdf,fieldsOfStudy,publicationDate,publicationTypes,venue";
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
    function headers() {
        const result = {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        };
        const apiKey = getEnv("SEMANTIC_SCHOLAR_API_KEY");
        if (apiKey?.trim())
            result["x-api-key"] = apiKey.trim();
        return result;
    }
    function fields(value) {
        const selected = value?.trim() || DEFAULT_FIELDS;
        if (!/^[A-Za-z0-9,._-]+$/.test(selected)) {
            throw new Error("fields must be a comma-separated Semantic Scholar field list.");
        }
        return selected;
    }
    async function requestJson(path, params) {
        const query = encodeQuery(params);
        const response = await client.get(`${BASE_URL}${path}${query ? `?${query}` : ""}`, headers());
        if (!response.isSuccessful()) {
            return { success: false, message: `Semantic Scholar request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        return response.json();
    }
    function limit(value) {
        return Math.min(Math.max(Math.floor(value ?? 10), 1), 100);
    }
    function offset(value) {
        return Math.max(Math.floor(value ?? 0), 0);
    }
    async function search(params) {
        if (!params.query || params.query.trim() === "")
            return { success: false, message: "query is required." };
        const payload = await requestJson("/paper/search", {
            query: params.query.trim(),
            limit: String(limit(params.limit)),
            offset: String(offset(params.offset)),
            fields: fields(params.fields),
        });
        if ("success" in payload)
            return payload;
        return {
            success: true,
            message: `Semantic Scholar returned ${payload.data.length} paper(s).`,
            data: payload.data,
            total: payload.total,
            count: payload.data.length,
            offset: payload.offset,
            next: payload.next,
        };
    }
    async function getPaper(params) {
        if (!params.paper_id || params.paper_id.trim() === "")
            return { success: false, message: "paper_id is required." };
        const payload = await requestJson(`/paper/${encodeURIComponent(params.paper_id.trim())}`, { fields: fields(params.fields) });
        if ("success" in payload)
            return payload;
        return { success: true, message: "Semantic Scholar returned one paper.", data: payload, count: 1, total: 1 };
    }
    async function runTool(toolName, action) {
        try {
            complete(await action());
        }
        catch (error) {
            console.error(`semantic_scholar_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `Semantic Scholar request failed: ${errorText(error)}` });
        }
    }
    return {
        search: (params) => runTool("search", () => search(params)),
        get_paper: (params) => runTool("get_paper", () => getPaper(params)),
    };
})();
exports.search = SemanticScholarSearch.search;
exports.get_paper = SemanticScholarSearch.get_paper;
