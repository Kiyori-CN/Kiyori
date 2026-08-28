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
  "enabledByDefault": false,
  "tools": [
    {
      "name": "search",
      "description": { "zh": "按关键词搜索 Semantic Scholar 论文，返回指定官方 fields。", "en": "Search Semantic Scholar papers by keyword and return the requested official fields." },
      "parameters": [
        { "name": "query", "description": { "zh": "论文搜索关键词。", "en": "Paper search query." }, "type": "string", "required": true },
        { "name": "limit", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "offset", "description": { "zh": "结果起始偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false },
        { "name": "fields", "description": { "zh": "可选逗号分隔轻量字段；搜索默认省略摘要，避免多结果输出膨胀。", "en": "Optional comma-separated lightweight fields; search omits abstracts by default to keep multi-result output compact." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_paper",
      "description": { "zh": "按 Semantic Scholar paperId、DOI、ArXiv ID、PMID 等官方 paper identifier 获取详情。", "en": "Retrieve paper details by a supported Semantic Scholar identifier such as paperId, DOI, arXiv ID, or PMID." },
      "parameters": [
        { "name": "paper_id", "description": { "zh": "例如 DOI:10.1038/nature12373、ARXIV:2403.02240 或 Semantic Scholar paperId。", "en": "For example DOI:10.1038/nature12373, ARXIV:2403.02240, or a Semantic Scholar paperId." }, "type": "string", "required": true },
        { "name": "fields", "description": { "zh": "可选逗号分隔轻量字段；详情默认包含摘要。", "en": "Optional comma-separated lightweight fields; detail includes the abstract by default." }, "type": "string", "required": false }
      ]
    }
  ]
}*/

/// <reference path="./types/index.d.ts" />

interface SemanticAuthor {
    authorId?: string;
    name?: string;
}

interface SemanticPaper {
    paperId?: string;
    title?: string;
    abstract?: string;
    year?: number;
    authors?: SemanticAuthor[];
    url?: string;
    externalIds?: Record<string, string | number>;
    citationCount?: number;
    referenceCount?: number;
    influentialCitationCount?: number;
    isOpenAccess?: boolean;
    openAccessPdf?: { url?: string; status?: string; license?: string };
    fieldsOfStudy?: string[];
    publicationDate?: string;
    publicationTypes?: string[];
    venue?: string;
}

interface SemanticSearchPayload {
    total: number;
    offset: number;
    next?: number;
    data: SemanticPaper[];
}

interface SemanticSearchParams {
    query: string;
    limit?: number;
    offset?: number;
    fields?: string;
}

interface SemanticPaperParams {
    paper_id: string;
    fields?: string;
}

interface SemanticResult {
    success: boolean;
    message: string;
    data?: SemanticPaper[] | SemanticPaper;
    total?: number;
    count?: number;
    offset?: number;
    next?: number;
    source?: string;
    metric_notice?: string;
    statusCode?: number;
}

const SemanticScholarSearch = (function () {
    const BASE_URL = "https://api.semanticscholar.org/graph/v1";
    const DEFAULT_SEARCH_FIELDS = "paperId,title,year,authors,url,externalIds,citationCount,referenceCount,influentialCitationCount,isOpenAccess,openAccessPdf,fieldsOfStudy,publicationDate,publicationTypes,venue";
    const DEFAULT_DETAIL_FIELDS = `${DEFAULT_SEARCH_FIELDS},abstract`;
    const ALLOWED_FIELDS = new Set(DEFAULT_DETAIL_FIELDS.split(","));
    const METRIC_NOTICE = "Semantic Scholar citation counts are source-specific and should only be compared within Semantic Scholar.";
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

    function encodeQuery(params: Record<string, string>): string {
        return Object.entries(params)
            .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
            .join("&");
    }

    function headers(): Record<string, string> {
        const result: Record<string, string> = {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        };
        const apiKey = getEnv("SEMANTIC_SCHOLAR_API_KEY");
        if (apiKey?.trim()) result["x-api-key"] = apiKey.trim();
        return result;
    }

    function fields(value: string | undefined, defaults: string): string {
        const configured = value?.trim();
        const selected = configured && configured !== "" ? configured : defaults;
        const unique: string[] = [];
        const seen = new Set<string>();
        selected.split(",").forEach((rawField) => {
            const field = rawField.trim();
            if (field === "") throw new Error("fields must not contain empty entries.");
            if (!ALLOWED_FIELDS.has(field)) throw new Error(`Unsupported Semantic Scholar field: ${field}.`);
            if (seen.has(field)) return;
            seen.add(field);
            unique.push(field);
        });
        return unique.join(",");
    }

    async function requestJson<T extends object>(path: string, params: Record<string, string>): Promise<T | SemanticResult> {
        const query = encodeQuery(params);
        const response = await client.get(`${BASE_URL}${path}${query ? `?${query}` : ""}`, headers());
        if (!response.isSuccessful()) {
            return { success: false, message: `Semantic Scholar request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        return response.json() as T;
    }

    function finiteInteger(value: number | undefined, defaultValue: number, minimum: number, maximum: number, name: string): number {
        const candidate = value ?? defaultValue;
        if (!Number.isFinite(candidate)) throw new Error(`${name} must be a finite number.`);
        return Math.min(Math.max(Math.floor(candidate), minimum), maximum);
    }

    function limit(value: number | undefined): number {
        return finiteInteger(value, 10, 1, 100, "limit");
    }

    function offset(value: number | undefined): number {
        return finiteInteger(value, 0, 0, Number.MAX_SAFE_INTEGER, "offset");
    }

    async function search(params: SemanticSearchParams): Promise<SemanticResult> {
        if (!params.query || params.query.trim() === "") return { success: false, message: "query is required." };
        const payload = await requestJson<SemanticSearchPayload>("/paper/search", {
            query: params.query.trim(),
            limit: String(limit(params.limit)),
            offset: String(offset(params.offset)),
            fields: fields(params.fields, DEFAULT_SEARCH_FIELDS),
        });
        if ("success" in payload) return payload;
        return {
            success: true,
            message: `Semantic Scholar returned ${payload.data.length} paper(s).`,
            data: payload.data,
            total: payload.total,
            count: payload.data.length,
            offset: payload.offset,
            next: payload.next,
            source: "Semantic Scholar",
            metric_notice: METRIC_NOTICE,
        };
    }

    async function getPaper(params: SemanticPaperParams): Promise<SemanticResult> {
        if (!params.paper_id || params.paper_id.trim() === "") return { success: false, message: "paper_id is required." };
        const payload = await requestJson<SemanticPaper>(`/paper/${encodeURIComponent(params.paper_id.trim())}`, {
            fields: fields(params.fields, DEFAULT_DETAIL_FIELDS),
        });
        if ("success" in payload) return payload;
        return {
            success: true,
            message: "Semantic Scholar returned one paper.",
            data: payload,
            count: 1,
            total: 1,
            source: "Semantic Scholar",
            metric_notice: METRIC_NOTICE,
        };
    }

    async function runTool<T>(toolName: string, action: () => Promise<T>): Promise<void> {
        try {
            complete(await action());
        } catch (error: unknown) {
            console.error(`semantic_scholar_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `Semantic Scholar request failed: ${errorText(error)}` });
        }
    }

    return {
        search: (params: SemanticSearchParams) => runTool("search", () => search(params)),
        get_paper: (params: SemanticPaperParams) => runTool("get_paper", () => getPaper(params)),
    };
})();

exports.search = SemanticScholarSearch.search;
exports.get_paper = SemanticScholarSearch.get_paper;
