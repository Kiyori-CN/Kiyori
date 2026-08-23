/* METADATA
{
  "name": "crossref_search",
  "display_name": {
    "zh": "Crossref 学术搜索",
    "en": "Crossref Academic Search"
  },
  "description": {
    "zh": "通过 Crossref 官方 REST API 查询 DOI、关键词、作者、标题、期刊 ISSN 和作品元数据。",
    "en": "Query DOI, keyword, author, title, journal ISSN, and scholarly work metadata through the official Crossref REST API."
  },
  "category": "Academic",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "search_by_doi",
      "description": { "zh": "按 DOI 查询一条作品的完整 Crossref 元数据。", "en": "Retrieve one work's Crossref metadata by DOI." },
      "parameters": [
        { "name": "doi", "description": { "zh": "例如 10.1038/nature12373。", "en": "For example 10.1038/nature12373." }, "type": "string", "required": true }
      ]
    },
    {
      "name": "search_by_keyword",
      "description": { "zh": "在 Crossref works 中按书目关键词搜索。", "en": "Search Crossref works by bibliographic keywords." },
      "parameters": [
        { "name": "query", "description": { "zh": "关键词或短语。", "en": "Keyword or phrase." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "sort", "description": { "zh": "relevance、score、updated、deposited、indexed、published 或 created。", "en": "relevance, score, updated, deposited, indexed, published, or created." }, "type": "string", "required": false },
        { "name": "order", "description": { "zh": "asc 或 desc。", "en": "asc or desc." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "search_by_author",
      "description": { "zh": "按作者姓名搜索 Crossref works。", "en": "Search Crossref works by author name." },
      "parameters": [
        { "name": "author", "description": { "zh": "作者姓名。", "en": "Author name." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "search_by_title",
      "description": { "zh": "按作品标题或标题短语搜索 Crossref works。", "en": "Search Crossref works by title or title phrase." },
      "parameters": [
        { "name": "title", "description": { "zh": "作品标题或标题关键词。", "en": "Work title or title keywords." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "search_by_issn",
      "description": { "zh": "按期刊 ISSN 查询该期刊的 Crossref works。", "en": "Retrieve Crossref works published in a journal by ISSN." },
      "parameters": [
        { "name": "issn", "description": { "zh": "例如 1476-4687。", "en": "For example 1476-4687." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const CrossrefSearch = (function () {
    const BASE_URL = "https://api.crossref.org";
    const MAX_ROWS = 100;
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
    function rows(value) {
        return Math.min(Math.max(Math.floor(value ?? 10), 1), MAX_ROWS);
    }
    function validateSort(sort) {
        return ["relevance", "score", "updated", "deposited", "indexed", "published", "created"].includes(sort);
    }
    function validateOrder(order) {
        return order === "asc" || order === "desc";
    }
    function requestError(response) {
        return { success: false, message: `Crossref request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
    }
    async function request(path, params) {
        const query = encodeQuery(params);
        const response = await client.get(`${BASE_URL}${path}${query ? `?${query}` : ""}`, {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful())
            return requestError(response);
        return response.json();
    }
    function worksResult(payload) {
        const message = payload.message;
        const items = message.items ?? [];
        return {
            success: true,
            message: `Crossref returned ${items.length} work(s).`,
            data: items,
            total: message["total-results"],
            count: items.length,
        };
    }
    async function searchByDoi(params) {
        if (!params.doi || params.doi.trim() === "")
            return { success: false, message: "doi is required." };
        const payload = await request(`/works/${encodeURIComponent(params.doi.trim())}`, {});
        if ("success" in payload)
            return payload;
        const work = payload.message;
        return { success: true, message: "Crossref returned one work.", data: work, count: 1, total: 1 };
    }
    async function searchByKeyword(params) {
        if (!params.query || params.query.trim() === "")
            return { success: false, message: "query is required." };
        const sort = params.sort ?? "relevance";
        const order = params.order ?? "desc";
        if (!validateSort(sort))
            return { success: false, message: "sort is not a supported Crossref value." };
        if (!validateOrder(order))
            return { success: false, message: "order must be asc or desc." };
        const payload = await request("/works", {
            "query.bibliographic": params.query.trim(),
            rows: String(rows(params.rows)),
            sort,
            order,
        });
        return "success" in payload ? payload : worksResult(payload);
    }
    async function searchByAuthor(params) {
        if (!params.author || params.author.trim() === "")
            return { success: false, message: "author is required." };
        const payload = await request("/works", { "query.author": params.author.trim(), rows: String(rows(params.rows)) });
        return "success" in payload ? payload : worksResult(payload);
    }
    async function searchByTitle(params) {
        if (!params.title || params.title.trim() === "")
            return { success: false, message: "title is required." };
        const payload = await request("/works", { "query.title": params.title.trim(), rows: String(rows(params.rows)) });
        return "success" in payload ? payload : worksResult(payload);
    }
    async function searchByIssn(params) {
        if (!params.issn || params.issn.trim() === "")
            return { success: false, message: "issn is required." };
        const payload = await request(`/journals/${encodeURIComponent(params.issn.trim())}/works`, { rows: String(rows(params.rows)) });
        return "success" in payload ? payload : worksResult(payload);
    }
    async function runTool(toolName, action) {
        try {
            complete(await action());
        }
        catch (error) {
            console.error(`crossref_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `Crossref request failed: ${errorText(error)}` });
        }
    }
    return {
        search_by_doi: (params) => runTool("search_by_doi", () => searchByDoi(params)),
        search_by_keyword: (params) => runTool("search_by_keyword", () => searchByKeyword(params)),
        search_by_author: (params) => runTool("search_by_author", () => searchByAuthor(params)),
        search_by_title: (params) => runTool("search_by_title", () => searchByTitle(params)),
        search_by_issn: (params) => runTool("search_by_issn", () => searchByIssn(params)),
    };
})();
exports.search_by_doi = CrossrefSearch.search_by_doi;
exports.search_by_keyword = CrossrefSearch.search_by_keyword;
exports.search_by_author = CrossrefSearch.search_by_author;
exports.search_by_title = CrossrefSearch.search_by_title;
exports.search_by_issn = CrossrefSearch.search_by_issn;
