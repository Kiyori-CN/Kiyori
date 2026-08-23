/* METADATA
{
  "name": "pubmed_search",
  "display_name": {
    "zh": "PubMed 医学文献",
    "en": "PubMed Medical Literature"
  },
  "description": {
    "zh": "通过 NCBI 官方 E-utilities 搜索 PubMed 医学文献并获取文章摘要元数据。",
    "en": "Search PubMed medical literature and retrieve article summary metadata through the official NCBI E-utilities."
  },
  "env": [
    {
      "name": "PUBMED_API_KEY",
      "description": { "zh": "可选 NCBI API Key；配置后使用官方提高的请求额度。", "en": "Optional NCBI API key for the official higher request-rate allowance." },
      "required": false
    },
    {
      "name": "PUBMED_EMAIL",
      "description": { "zh": "可选联系邮箱，按 NCBI E-utilities 建议随请求发送。", "en": "Optional contact email sent according to NCBI E-utilities recommendations." },
      "required": false
    }
  ],
  "category": "Academic",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "search",
      "description": { "zh": "搜索 PubMed 并返回 PMID、标题、作者、期刊和发表信息。", "en": "Search PubMed and return PMIDs, titles, authors, journals, and publication information." },
      "parameters": [
        { "name": "term", "description": { "zh": "PubMed 检索式，支持 NCBI 字段标签和布尔语法。", "en": "PubMed query supporting NCBI field tags and Boolean syntax." }, "type": "string", "required": true },
        { "name": "retmax", "description": { "zh": "返回数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "retstart", "description": { "zh": "结果起始偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false },
        { "name": "sort", "description": { "zh": "可选 PubMed 官方排序值：relevance、pub_date、Author 或 JournalName。", "en": "Optional official PubMed sort: relevance, pub_date, Author, or JournalName." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_articles",
      "description": { "zh": "按一组 PMID 获取 PubMed ESummary 官方元数据。", "en": "Retrieve official PubMed ESummary metadata for a list of PMIDs." },
      "parameters": [
        { "name": "ids", "description": { "zh": "1-100 个 PMID 字符串数组。", "en": "Array of 1-100 PMID strings." }, "type": "array", "required": true }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const PubMedSearch = (function () {
    const BASE_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils";
    const MAX_RESULTS = 100;
    const PUBMED_SORT_VALUES = ["pub_date", "Author", "JournalName", "relevance"];
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
    function configuredValue(name) {
        const value = getEnv(name);
        return value ? value.trim() : "";
    }
    function encodeQuery(params) {
        return Object.entries(params)
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join("&");
    }
    function requestParams(params) {
        const result = { ...params, tool: "Kiyori" };
        const email = configuredValue("PUBMED_EMAIL");
        const apiKey = configuredValue("PUBMED_API_KEY");
        if (email)
            result.email = email;
        if (apiKey)
            result.api_key = apiKey;
        return result;
    }
    async function requestJson(path, params) {
        const response = await client.get(`${BASE_URL}/${path}?${encodeQuery(requestParams(params))}`, {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: `PubMed request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        return response.json();
    }
    function normalizedCount(value) {
        return Math.min(Math.max(Math.floor(value ?? 10), 1), MAX_RESULTS);
    }
    function normalizedStart(value) {
        return Math.max(Math.floor(value ?? 0), 0);
    }
    function normalizedSort(value) {
        const trimmed = value?.trim();
        if (!trimmed)
            return undefined;
        if (!PUBMED_SORT_VALUES.includes(trimmed)) {
            throw new Error("sort must be pub_date, Author, JournalName, or relevance.");
        }
        return trimmed;
    }
    function summaries(payload, ids) {
        const values = [];
        ids.forEach((id) => {
            const entry = payload.result[id];
            if (entry && !Array.isArray(entry))
                values.push(entry);
        });
        return values;
    }
    async function fetchSummaries(ids) {
        const payload = await requestJson("esummary.fcgi", {
            db: "pubmed",
            id: ids.join(","),
            retmode: "json",
        });
        if ("success" in payload)
            return payload;
        const data = summaries(payload, ids);
        return { success: true, message: `PubMed returned ${data.length} article summary record(s).`, data, ids, count: data.length };
    }
    async function search(params) {
        if (!params.term || params.term.trim() === "")
            return { success: false, message: "term is required." };
        const query = {
            db: "pubmed",
            term: params.term.trim(),
            retmode: "json",
            retmax: String(normalizedCount(params.retmax)),
            retstart: String(normalizedStart(params.retstart)),
        };
        const sort = normalizedSort(params.sort);
        if (sort)
            query.sort = sort;
        const payload = await requestJson("esearch.fcgi", query);
        if ("success" in payload)
            return payload;
        const searchResult = payload.esearchresult;
        const ids = searchResult.idlist ?? [];
        if (ids.length === 0) {
            return {
                success: true,
                message: "PubMed returned no matching articles.",
                data: [],
                ids: [],
                count: 0,
                total: Number(searchResult.count),
                retstart: Number(searchResult.retstart),
                query_translation: searchResult.querytranslation,
            };
        }
        const result = await fetchSummaries(ids);
        return {
            ...result,
            total: Number(searchResult.count),
            retstart: Number(searchResult.retstart),
            query_translation: searchResult.querytranslation,
        };
    }
    async function getArticles(params) {
        if (!Array.isArray(params.ids) || params.ids.length === 0)
            return { success: false, message: "ids must contain at least one PMID." };
        if (params.ids.length > MAX_RESULTS)
            return { success: false, message: "ids accepts at most 100 PMIDs." };
        if (!params.ids.every((id) => typeof id === "string"))
            return { success: false, message: "ids must contain only PMID strings." };
        const ids = params.ids.map((id) => id.trim()).filter((id) => id !== "");
        if (ids.length !== params.ids.length)
            return { success: false, message: "ids must contain only non-empty PMID strings." };
        if (ids.some((id) => !/^\d+$/.test(id)))
            return { success: false, message: "ids must contain only decimal PMID values." };
        return fetchSummaries(ids);
    }
    async function runTool(toolName, action) {
        try {
            complete(await action());
        }
        catch (error) {
            console.error(`pubmed_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `PubMed request failed: ${errorText(error)}` });
        }
    }
    return {
        search: (params) => runTool("search", () => search(params)),
        get_articles: (params) => runTool("get_articles", () => getArticles(params)),
    };
})();
exports.search = PubMedSearch.search;
exports.get_articles = PubMedSearch.get_articles;
