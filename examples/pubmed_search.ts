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

interface PubMedAuthor {
    name?: string;
    authtype?: string;
    clusterid?: string;
}

interface PubMedArticleSummary {
    uid?: string;
    pubdate?: string;
    epubdate?: string;
    source?: string;
    authors?: PubMedAuthor[];
    lastauthor?: string;
    title?: string;
    sortfirstauthor?: string;
    volume?: string;
    issue?: string;
    pages?: string;
    lang?: string[];
    nlmuniqueid?: string;
    issn?: string;
    essn?: string;
    pubtype?: string[];
    articleids?: Array<{ idtype?: string; idtypen?: number; value?: string }>;
    fulljournalname?: string;
    elocationid?: string;
    doctype?: string;
}

interface PubMedSearchPayload {
    esearchresult: {
        count: string;
        retmax: string;
        retstart: string;
        idlist: string[];
        querytranslation?: string;
    };
}

interface PubMedSummaryPayload {
    result: {
        uids: string[];
        [key: string]: string[] | PubMedArticleSummary;
    };
}

interface PubMedSearchParams {
    term: string;
    retmax?: number;
    retstart?: number;
    sort?: string;
}

interface PubMedArticleParams {
    ids: string[];
}

interface PubMedResult {
    success: boolean;
    message: string;
    data?: PubMedArticleSummary[];
    ids?: string[];
    total?: number;
    count?: number;
    retstart?: number;
    query_translation?: string;
    statusCode?: number;
}

interface PubMedErrorPayload {
    error?: string;
}

interface PubMedRequest {
    params: Record<string, string>;
    apiKeyConfigured: boolean;
}

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

    function errorText(error: unknown): string {
        if (error instanceof Error) return error.message;
        if (typeof error === "string") return error;
        const serialized = JSON.stringify(error);
        return serialized ?? "Unknown error";
    }

    function configuredValue(name: string): string {
        const value = getEnv(name);
        return value ? value.trim() : "";
    }

    function encodeQuery(params: Record<string, string>): string {
        return Object.entries(params)
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join("&");
    }

    function requestParams(params: Record<string, string>): PubMedRequest {
        const result: Record<string, string> = { ...params, tool: "Kiyori" };
        const email = configuredValue("PUBMED_EMAIL");
        const apiKey = configuredValue("PUBMED_API_KEY");
        if (email) result.email = email;
        if (apiKey) result.api_key = apiKey;
        return { params: result, apiKeyConfigured: apiKey !== "" };
    }

    function failureMessage(response: OkHttpResponse, apiKeyConfigured: boolean): string {
        const statusMessage = response.statusMessage.trim();
        const status = statusMessage ? `HTTP ${response.statusCode} ${statusMessage}` : `HTTP ${response.statusCode}`;
        const genericMessage = `PubMed request failed: ${status}`;
        if (response.statusCode !== 400 || !apiKeyConfigured) return genericMessage;

        try {
            const payload = response.json() as PubMedErrorPayload;
            if (typeof payload.error === "string" && payload.error.trim().toLowerCase() === "api key invalid") {
                return `${genericMessage}. NCBI rejected PUBMED_API_KEY as invalid; update it or clear the optional value to use the official unauthenticated request allowance.`;
            }
        } catch (error: unknown) {
            console.error(`pubmed_search could not parse the NCBI error response: ${errorText(error)}`);
        }
        return genericMessage;
    }

    async function requestJson<T extends object>(path: string, params: Record<string, string>): Promise<T | PubMedResult> {
        const request = requestParams(params);
        const response = await client.get(`${BASE_URL}/${path}?${encodeQuery(request.params)}`, {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: failureMessage(response, request.apiKeyConfigured), statusCode: response.statusCode };
        }
        return response.json() as T;
    }

    function normalizedCount(value: number | undefined): number {
        return Math.min(Math.max(Math.floor(value ?? 10), 1), MAX_RESULTS);
    }

    function normalizedStart(value: number | undefined): number {
        return Math.max(Math.floor(value ?? 0), 0);
    }

    function normalizedSort(value: string | undefined): string | undefined {
        const trimmed = value?.trim();
        if (!trimmed) return undefined;
        if (!PUBMED_SORT_VALUES.includes(trimmed)) {
            throw new Error("sort must be pub_date, Author, JournalName, or relevance.");
        }
        return trimmed;
    }

    function summaries(payload: PubMedSummaryPayload, ids: string[]): PubMedArticleSummary[] {
        const values: PubMedArticleSummary[] = [];
        ids.forEach((id) => {
            const entry = payload.result[id];
            if (entry && !Array.isArray(entry)) values.push(entry);
        });
        return values;
    }

    async function fetchSummaries(ids: string[]): Promise<PubMedResult> {
        const payload = await requestJson<PubMedSummaryPayload>("esummary.fcgi", {
            db: "pubmed",
            id: ids.join(","),
            retmode: "json",
        });
        if ("success" in payload) return payload;
        const data = summaries(payload, ids);
        return { success: true, message: `PubMed returned ${data.length} article summary record(s).`, data, ids, count: data.length };
    }

    async function search(params: PubMedSearchParams): Promise<PubMedResult> {
        if (!params.term || params.term.trim() === "") return { success: false, message: "term is required." };
        const query: Record<string, string> = {
            db: "pubmed",
            term: params.term.trim(),
            retmode: "json",
            retmax: String(normalizedCount(params.retmax)),
            retstart: String(normalizedStart(params.retstart)),
        };
        const sort = normalizedSort(params.sort);
        if (sort) query.sort = sort;
        const payload = await requestJson<PubMedSearchPayload>("esearch.fcgi", query);
        if ("success" in payload) return payload;
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

    async function getArticles(params: PubMedArticleParams): Promise<PubMedResult> {
        if (!Array.isArray(params.ids) || params.ids.length === 0) return { success: false, message: "ids must contain at least one PMID." };
        if (params.ids.length > MAX_RESULTS) return { success: false, message: "ids accepts at most 100 PMIDs." };
        if (!params.ids.every((id) => typeof id === "string")) return { success: false, message: "ids must contain only PMID strings." };
        const ids = params.ids.map((id) => id.trim()).filter((id) => id !== "");
        if (ids.length !== params.ids.length) return { success: false, message: "ids must contain only non-empty PMID strings." };
        if (ids.some((id) => !/^\d+$/.test(id))) return { success: false, message: "ids must contain only decimal PMID values." };
        return fetchSummaries(ids);
    }

    async function runTool<T>(toolName: string, action: () => Promise<T>): Promise<void> {
        try {
            complete(await action());
        } catch (error: unknown) {
            console.error(`pubmed_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `PubMed request failed: ${errorText(error)}` });
        }
    }

    return {
        search: (params: PubMedSearchParams) => runTool("search", () => search(params)),
        get_articles: (params: PubMedArticleParams) => runTool("get_articles", () => getArticles(params)),
    };
})();

exports.search = PubMedSearch.search;
exports.get_articles = PubMedSearch.get_articles;
