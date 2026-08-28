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
      "description": { "zh": "按关键词搜索 OpenAlex works，支持官方 filter、sort 和 select 字段。", "en": "Search OpenAlex works by keyword with official filter, sort, and select fields." },
      "parameters": [
        { "name": "search", "description": { "zh": "作品、作者、机构、概念等全文搜索词。", "en": "Full-text search across work, author, institution, and concept fields." }, "type": "string", "required": true },
        { "name": "per_page", "description": { "zh": "每页数量，默认 10，范围 1-100。", "en": "Results per page; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "page", "description": { "zh": "从 1 开始的页码，默认 1。", "en": "One-based page number; defaults to 1." }, "type": "number", "required": false },
        { "name": "filter", "description": { "zh": "OpenAlex 官方 filter 表达式，例如 publication_year:2024。", "en": "Official OpenAlex filter expression, for example publication_year:2024." }, "type": "string", "required": false },
        { "name": "sort", "description": { "zh": "官方 sort 表达式，例如 cited_by_count:desc。", "en": "Official sort expression, for example cited_by_count:desc." }, "type": "string", "required": false },
        { "name": "select", "description": { "zh": "逗号分隔的官方字段选择，减少返回字段。", "en": "Comma-separated official field selection to reduce returned fields." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_work",
      "description": { "zh": "按 OpenAlex work ID、URL 或 DOI 获取一条作品的完整元数据。", "en": "Retrieve one OpenAlex work by work ID, URL, or DOI." },
      "parameters": [
        { "name": "work_id", "description": { "zh": "例如 W2741809807、https://openalex.org/W2741809807、doi:10.7717/peerj.4375 或 DOI URL。", "en": "For example W2741809807, https://openalex.org/W2741809807, doi:10.7717/peerj.4375, or a DOI URL." }, "type": "string", "required": true },
        { "name": "select", "description": { "zh": "可选逗号分隔的官方字段选择。", "en": "Optional comma-separated official field selection." }, "type": "string", "required": false }
      ]
    }
  ]
}*/

/// <reference path="./types/index.d.ts" />

interface OpenAlexAuthor {
    author?: { id?: string; display_name?: string; orcid?: string };
    author_position?: string;
    institutions?: Array<{ id?: string; display_name?: string; ror?: string; country_code?: string; type?: string }>;
    is_corresponding?: boolean;
}

interface OpenAlexLocation {
    is_oa?: boolean;
    landing_page_url?: string;
    pdf_url?: string;
    source?: { id?: string; display_name?: string; issn_l?: string; issn?: string[]; type?: string };
    license?: string;
    version?: string;
}

interface OpenAlexWork {
    id?: string;
    doi?: string;
    title?: string;
    publication_year?: number;
    publication_date?: string;
    type?: string;
    cited_by_count?: number;
    referenced_works?: string[];
    authorships?: OpenAlexAuthor[];
    primary_location?: OpenAlexLocation;
    locations?: OpenAlexLocation[];
    open_access?: { is_oa?: boolean; oa_status?: string; oa_url?: string; any_repository_has_fulltext?: boolean };
    concepts?: Array<{ id?: string; display_name?: string; score?: number; level?: number; wikidata?: string }>;
    topics?: Array<{ id?: string; display_name?: string; score?: number; subfield?: { display_name?: string }; field?: { display_name?: string }; domain?: { display_name?: string } }>;
}

interface OpenAlexSearchPayload {
    meta: { count: number; per_page: number; page: number; next_cursor?: string };
    results: OpenAlexWork[];
}

interface OpenAlexSearchParams {
    search: string;
    per_page?: number;
    page?: number;
    filter?: string;
    sort?: string;
    select?: string;
}

interface OpenAlexWorkParams {
    work_id: string;
    select?: string;
}

interface OpenAlexResult {
    success: boolean;
    message: string;
    data?: OpenAlexWork[] | OpenAlexWork;
    total?: number;
    count?: number;
    page?: number;
    per_page?: number;
    statusCode?: number;
}

const OpenAlexSearch = (function () {
    const BASE_URL = "https://api.openalex.org";
    const DEFAULT_SELECT = "id,doi,title,authorships,publication_year,publication_date,primary_location,open_access,cited_by_count,type";
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

    function selectedFields(value: string | undefined): string {
        const selected = value?.trim() || DEFAULT_SELECT;
        if (!/^[A-Za-z0-9_,.-]+$/.test(selected)) {
            throw new Error("select must be a comma-separated OpenAlex field list.");
        }
        return selected;
    }

    function page(value: number | undefined): number {
        return Math.max(Math.floor(value ?? 1), 1);
    }

    function perPage(value: number | undefined): number {
        return Math.min(Math.max(Math.floor(value ?? 10), 1), 100);
    }

    function normalizedWorkId(value: string): string {
        const trimmed = value.trim();
        return /^10\.\d{4,9}\/\S+$/i.test(trimmed) ? `doi:${trimmed}` : trimmed;
    }

    function queryParams(params: Record<string, string>): Record<string, string> {
        const result = { ...params };
        const apiKey = getEnv("OPENALEX_API_KEY");
        if (apiKey?.trim()) result.api_key = apiKey.trim();
        return result;
    }

    async function requestJson<T extends object>(path: string, params: Record<string, string>): Promise<T | OpenAlexResult> {
        const query = encodeQuery(queryParams(params));
        const response = await client.get(`${BASE_URL}${path}${query ? `?${query}` : ""}`, {
            Accept: "application/json",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: `OpenAlex request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        return response.json() as T;
    }

    async function search(params: OpenAlexSearchParams): Promise<OpenAlexResult> {
        if (!params.search || params.search.trim() === "") return { success: false, message: "search is required." };
        const query: Record<string, string> = {
            search: params.search.trim(),
            per_page: String(perPage(params.per_page)),
            page: String(page(params.page)),
            select: selectedFields(params.select),
        };
        if (params.filter?.trim()) query.filter = params.filter.trim();
        if (params.sort?.trim()) query.sort = params.sort.trim();
        const payload = await requestJson<OpenAlexSearchPayload>("/works", query);
        if ("success" in payload) return payload;
        return {
            success: true,
            message: `OpenAlex returned ${payload.results.length} work(s).`,
            data: payload.results,
            total: payload.meta.count,
            count: payload.results.length,
            page: payload.meta.page,
            per_page: payload.meta.per_page,
        };
    }

    async function getWork(params: OpenAlexWorkParams): Promise<OpenAlexResult> {
        if (!params.work_id || params.work_id.trim() === "") return { success: false, message: "work_id is required." };
        const query: Record<string, string> = {};
        if (params.select?.trim()) query.select = selectedFields(params.select);
        const workId = normalizedWorkId(params.work_id);
        const payload = await requestJson<OpenAlexWork>(`/works/${encodeURIComponent(workId)}`, query);
        if ("success" in payload) return payload;
        return { success: true, message: "OpenAlex returned one work.", data: payload, count: 1, total: 1 };
    }

    async function runTool<T>(toolName: string, action: () => Promise<T>): Promise<void> {
        try {
            complete(await action());
        } catch (error: unknown) {
            console.error(`openalex_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `OpenAlex request failed: ${errorText(error)}` });
        }
    }

    return {
        search: (params: OpenAlexSearchParams) => runTool("search", () => search(params)),
        get_work: (params: OpenAlexWorkParams) => runTool("get_work", () => getWork(params)),
    };
})();

exports.search = OpenAlexSearch.search;
exports.get_work = OpenAlexSearch.get_work;
