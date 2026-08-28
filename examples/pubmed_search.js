/* METADATA
{
  "name": "pubmed_search",
  "display_name": {
    "zh": "PubMed 文献搜索",
    "en": "PubMed Literature Search"
  },
  "description": {
    "zh": "通过 NCBI 官方 E-utilities 搜索 PubMed 医学文献并获取文章摘要元数据。",
    "en": "Search PubMed medical literature and retrieve article summary metadata through the official NCBI E-utilities."
  },
  "env": [
    {
      "name": "PUBMED_API_KEY",
      "description": { "zh": "可选 NCBI API Key；无 Key 使用官方基础额度，配置错误的 Key 会被 NCBI 拒绝。", "en": "Optional NCBI API key; requests use the official base allowance without one, while NCBI rejects an invalid configured key." },
      "required": false
    },
    {
      "name": "PUBMED_EMAIL",
      "description": { "zh": "可选联系邮箱，按 NCBI E-utilities 建议随请求发送。", "en": "Optional contact email sent according to NCBI E-utilities recommendations." },
      "required": false
    }
  ],
  "category": "Academic",
  "enabledByDefault": false,
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
        return { params: result, apiKeyConfigured: apiKey !== "" };
    }
    function failureMessage(response, apiKeyConfigured) {
        const statusMessage = response.statusMessage.trim();
        const status = statusMessage ? `HTTP ${response.statusCode} ${statusMessage}` : `HTTP ${response.statusCode}`;
        const genericMessage = `PubMed request failed: ${status}`;
        if (response.statusCode !== 400 || !apiKeyConfigured)
            return genericMessage;
        try {
            const payload = response.json();
            if (typeof payload.error === "string" && payload.error.trim().toLowerCase() === "api key invalid") {
                return `${genericMessage}. NCBI rejected PUBMED_API_KEY as invalid; update it or clear the optional value to use the official unauthenticated request allowance.`;
            }
        }
        catch (error) {
            console.error(`pubmed_search could not parse the NCBI error response: ${errorText(error)}`);
        }
        return genericMessage;
    }
    async function requestJson(path, params) {
        const request = requestParams(params);
        const response = await client.get(`${BASE_URL}/${path}?${encodeQuery(request.params)}`, {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: failureMessage(response, request.apiKeyConfigured), statusCode: response.statusCode };
        }
        return response.json();
    }
    function finiteInteger(value, defaultValue, minimum, maximum, name) {
        const candidate = value ?? defaultValue;
        if (!Number.isFinite(candidate))
            throw new Error(`${name} must be a finite number.`);
        return Math.min(Math.max(Math.floor(candidate), minimum), maximum);
    }
    function normalizedCount(value) {
        return finiteInteger(value, 10, 1, MAX_RESULTS, "retmax");
    }
    function normalizedStart(value) {
        return finiteInteger(value, 0, 0, Number.MAX_SAFE_INTEGER, "retstart");
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
    function stableUniqueIds(ids) {
        const seen = new Set();
        const unique = [];
        ids.forEach((id) => {
            if (seen.has(id))
                return;
            seen.add(id);
            unique.push(id);
        });
        return unique;
    }
    function searchPayloadError(payload) {
        const result = payload.esearchresult;
        const errors = [];
        if (result.ERROR?.trim())
            errors.push(result.ERROR.trim());
        const fieldsNotFound = result.errorlist?.fieldsnotfound ?? [];
        if (fieldsNotFound.length > 0)
            errors.push(`Unknown field(s): ${fieldsNotFound.join(", ")}`);
        const phrasesNotFound = result.errorlist?.phrasesnotfound ?? [];
        if (phrasesNotFound.length > 0)
            errors.push(`Invalid phrase(s): ${phrasesNotFound.join(", ")}`);
        return errors.join("; ");
    }
    function responseInteger(value, name) {
        const parsed = Number(value);
        if (!Number.isFinite(parsed) || parsed < 0)
            throw new Error(`PubMed returned an invalid ${name} value.`);
        return Math.floor(parsed);
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
        return { success: true, message: `PubMed returned ${data.length} article summary record(s).`, data, ids, count: data.length, source: "PubMed" };
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
        // NCBI can encode query errors inside an otherwise successful HTTP 200 ESearch response.
        const payloadError = searchPayloadError(payload);
        if (payloadError !== "") {
            return { success: false, message: `PubMed search rejected the query: ${payloadError}`, statusCode: 200 };
        }
        const ids = stableUniqueIds(searchResult.idlist ?? []);
        const total = responseInteger(searchResult.count, "count");
        const retstart = responseInteger(searchResult.retstart, "retstart");
        if (ids.length === 0) {
            return {
                success: true,
                message: "PubMed returned no matching articles.",
                data: [],
                ids: [],
                count: 0,
                total,
                retstart,
                query_translation: searchResult.querytranslation,
                source: "PubMed",
            };
        }
        const result = await fetchSummaries(ids);
        return {
            ...result,
            total,
            retstart,
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
        const normalizedIds = params.ids.map((id) => id.trim()).filter((id) => id !== "");
        if (normalizedIds.length !== params.ids.length)
            return { success: false, message: "ids must contain only non-empty PMID strings." };
        if (normalizedIds.some((id) => !/^\d+$/.test(id)))
            return { success: false, message: "ids must contain only decimal PMID values." };
        return fetchSummaries(stableUniqueIds(normalizedIds));
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
