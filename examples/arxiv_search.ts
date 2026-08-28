/* METADATA
{
  "name": "arxiv_search",
  "display_name": {
    "zh": "arXiv 学术搜索",
    "en": "arXiv Academic Search"
  },
  "description": {
    "zh": "通过 arXiv 官方 Atom API 搜索预印本并按 arXiv ID 获取论文元数据。",
    "en": "Search preprints and retrieve paper metadata through the official arXiv Atom API."
  },
  "category": "Academic",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "search",
      "description": { "zh": "按关键词搜索 arXiv 论文并返回标题、作者、摘要、分类和链接。", "en": "Search arXiv papers by query and return titles, authors, abstracts, categories, and links." },
      "parameters": [
        { "name": "query", "description": { "zh": "空格分隔的关键词；脚本以 AND 连接每个 all 字段词项并忽略常见英语停用词，双引号可保留精确短语。", "en": "Space-separated terms combined with AND across arXiv's all field; common English stop words are omitted and double quotes preserve an exact phrase." }, "type": "string", "required": true },
        { "name": "max_results", "description": { "zh": "返回数量，默认 10，范围 1-50。", "en": "Number of results; defaults to 10 and is clamped to 1-50." }, "type": "number", "required": false },
        { "name": "start", "description": { "zh": "结果起始偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false },
        { "name": "sort_by", "description": { "zh": "官方排序：relevance、lastUpdatedDate 或 submittedDate。", "en": "Official sort field: relevance, lastUpdatedDate, or submittedDate." }, "type": "string", "required": false },
        { "name": "sort_order", "description": { "zh": "官方排序方向：ascending 或 descending。", "en": "Official sort order: ascending or descending." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_paper",
      "description": { "zh": "按 arXiv ID 或带版本的 ID 获取一篇论文的官方元数据。", "en": "Retrieve official metadata for one arXiv ID, including an optional version suffix." },
      "parameters": [
        { "name": "paper_id", "description": { "zh": "例如 2403.02240、2403.02240v5 或 cond-mat/0207270。", "en": "For example 2403.02240, 2403.02240v5, or cond-mat/0207270." }, "type": "string", "required": true }
      ]
    }
  ]
}*/

/// <reference path="./types/index.d.ts" />

interface ArxivSearchParams {
    query: string;
    max_results?: number;
    start?: number;
    sort_by?: string;
    sort_order?: string;
}

interface ArxivPaperParams {
    paper_id: string;
}

interface ArxivPaper {
    id: string;
    arxiv_id: string;
    version?: number;
    url: string;
    pdf_url: string;
    title: string;
    summary: string;
    authors: string[];
    published: string;
    updated: string;
    categories: string[];
    primary_category: string;
    doi: string;
    doi_provided: boolean;
    journal_ref: string;
    comment: string;
}

interface ArxivResult {
    success: boolean;
    message: string;
    data?: ArxivPaper[] | ArxivPaper;
    count?: number;
    total?: number;
    search_query?: string;
    statusCode?: number;
}

interface ArxivIdentity {
    arxivId: string;
    version?: number;
}

const ArxivSearch = (function () {
    const BASE_URL = "https://export.arxiv.org/api/query";
    const MAX_RESULTS = 50;
    const STOP_WORDS = new Set(["a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is", "of", "on", "or", "that", "the", "to", "with"]);
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

    function decodeXml(value: string): string {
        return value
            .replace(/&#x([0-9a-f]+);/gi, (_match, hex: string) => String.fromCodePoint(parseInt(hex, 16)))
            .replace(/&#([0-9]+);/g, (_match, decimal: string) => String.fromCodePoint(parseInt(decimal, 10)))
            .replace(/&amp;/g, "&")
            .replace(/&lt;/g, "<")
            .replace(/&gt;/g, ">")
            .replace(/&quot;/g, '"')
            .replace(/&apos;/g, "'");
    }

    function tagValue(source: string, tagName: string): string {
        const match = source.match(new RegExp(`<${tagName}(?:\\s[^>]*)?>([\\s\\S]*?)</${tagName}>`));
        return match?.[1] ? decodeXml(match[1].trim()) : "";
    }

    function tagValues(source: string, tagName: string): string[] {
        const values: string[] = [];
        const pattern = new RegExp(`<${tagName}(?:\\s[^>]*)?>([\\s\\S]*?)</${tagName}>`, "g");
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(source)) !== null) {
            if (match[1]) values.push(decodeXml(match[1].trim()));
        }
        return values;
    }

    function categoryValues(source: string): string[] {
        const values: string[] = [];
        const pattern = /<category\s+[^>]*term="([^"]+)"[^>]*\/>/g;
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(source)) !== null) {
            if (match[1]) values.push(decodeXml(match[1]));
        }
        return values;
    }

    function attributeValue(source: string, tagName: string, attributeName: string): string {
        const tag = source.match(new RegExp(`<${tagName}(?:\\s[^>]*)?\\/?>`));
        if (!tag?.[0]) return "";
        const attribute = tag[0].match(new RegExp(`${attributeName}="([^"]+)"`));
        return attribute?.[1] ? decodeXml(attribute[1]) : "";
    }

    function linkValue(source: string, attributeName: string, attributeValue: string): string {
        const pattern = /<link\s+[^>]*\/>/g;
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(source)) !== null) {
            const linkTag = match[0];
            const attribute = linkTag.match(new RegExp(`${attributeName}="([^"]+)"`));
            if (attribute?.[1] !== attributeValue) continue;
            const href = linkTag.match(/href="([^"]+)"/);
            if (href?.[1]) return decodeXml(href[1]);
        }
        return "";
    }

    function parsePapers(xml: string): ArxivPaper[] {
        const papers: ArxivPaper[] = [];
        const pattern = /<entry(?:\s[^>]*)?>([\s\S]*?)<\/entry>/g;
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(xml)) !== null) {
            const entry = match[1] ?? "";
            const id = tagValue(entry, "id");
            const identity = arxivIdentity(id);
            const url = linkValue(entry, "rel", "alternate");
            const doi = tagValue(entry, "arxiv:doi");
            papers.push({
                id,
                arxiv_id: identity.arxivId,
                version: identity.version,
                url,
                pdf_url: linkValue(entry, "title", "pdf"),
                title: tagValue(entry, "title").replace(/\s+/g, " "),
                summary: tagValue(entry, "summary").replace(/\s+/g, " "),
                authors: tagValues(entry, "name"),
                published: tagValue(entry, "published"),
                updated: tagValue(entry, "updated"),
                categories: categoryValues(entry),
                primary_category: attributeValue(entry, "arxiv:primary_category", "term"),
                doi,
                doi_provided: doi !== "",
                journal_ref: tagValue(entry, "arxiv:journal_ref"),
                comment: tagValue(entry, "arxiv:comment"),
            });
        }
        return papers;
    }

    function arxivIdentity(id: string): ArxivIdentity {
        const match = id.match(/\/abs\/(.+?)(?:v([1-9][0-9]*))?$/);
        if (!match?.[1]) return { arxivId: id };
        if (!match[2]) return { arxivId: match[1] };
        return { arxivId: `${match[1]}v${match[2]}`, version: Number(match[2]) };
    }

    function arxivError(xml: string): string {
        const pattern = /<entry(?:\s[^>]*)?>([\s\S]*?)<\/entry>/g;
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(xml)) !== null) {
            const entry = match[1] ?? "";
            const id = tagValue(entry, "id");
            const title = tagValue(entry, "title");
            if (!id.includes("/api/errors#") && title.toLowerCase() !== "error") continue;
            const summary = tagValue(entry, "summary");
            return summary !== "" ? summary : "arXiv returned an error feed.";
        }
        return "";
    }

    function parseTotalResults(xml: string): number | undefined {
        const value = tagValue(xml, "opensearch:totalResults");
        if (value === "") return undefined;
        const total = Number(value);
        return Number.isFinite(total) ? total : undefined;
    }

    function finiteInteger(value: number | undefined, defaultValue: number, minimum: number, maximum: number, name: string): number {
        const candidate = value ?? defaultValue;
        if (!Number.isFinite(candidate)) throw new Error(`${name} must be a finite number.`);
        return Math.min(Math.max(Math.floor(candidate), minimum), maximum);
    }

    function normalizedCount(value: number | undefined): number {
        return finiteInteger(value, 10, 1, MAX_RESULTS, "max_results");
    }

    function normalizedStart(value: number | undefined): number {
        return finiteInteger(value, 0, 0, Number.MAX_SAFE_INTEGER, "start");
    }

    // Plain model input must not rely on arXiv's ambiguous whitespace parsing: each token gets
    // an explicit field and boolean operator, while quoted phrases remain one term.
    function buildSearchQuery(value: string): string {
        const quoteCount = value.split('"').length - 1;
        if (quoteCount % 2 !== 0) throw new Error("query contains an unmatched double quote.");
        const terms: string[] = [];
        const pattern = /"([^"]+)"|(\S+)/g;
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(value)) !== null) {
            const phrase = match[1];
            const rawTerm = phrase ?? match[2] ?? "";
            const term = rawTerm.trim();
            if (term === "") continue;
            if (phrase === undefined && STOP_WORDS.has(term.toLowerCase())) continue;
            const escaped = term.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
            terms.push(`all:${phrase === undefined ? escaped : `"${escaped}"`}`);
        }
        if (terms.length === 0) throw new Error("query must contain at least one search term.");
        return terms.join(" AND ");
    }

    async function request(params: Record<string, string>): Promise<ArxivResult> {
        const response = await client.get(`${BASE_URL}?${encodeQuery(params)}`, {
            Accept: "application/atom+xml",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: `arXiv request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        // arXiv reports malformed queries as an Atom error entry with HTTP 200.
        const feedError = arxivError(response.content);
        if (feedError !== "") {
            return { success: false, message: `arXiv request failed: ${feedError}`, statusCode: response.statusCode };
        }
        const papers = parsePapers(response.content);
        const result: ArxivResult = {
            success: true,
            message: `arXiv returned ${papers.length} paper(s).`,
            data: papers,
            count: papers.length,
            total: parseTotalResults(response.content),
        };
        if (params.search_query) result.search_query = params.search_query;
        return result;
    }

    async function search(params: ArxivSearchParams): Promise<ArxivResult> {
        if (!params.query || params.query.trim() === "") {
            return { success: false, message: "query is required." };
        }
        const sortBy = params.sort_by ?? "relevance";
        const sortOrder = params.sort_order ?? "descending";
        if (!["relevance", "lastUpdatedDate", "submittedDate"].includes(sortBy)) {
            return { success: false, message: "sort_by must be relevance, lastUpdatedDate, or submittedDate." };
        }
        if (!["ascending", "descending"].includes(sortOrder)) {
            return { success: false, message: "sort_order must be ascending or descending." };
        }
        const searchQuery = buildSearchQuery(params.query.trim());
        return request({
            search_query: searchQuery,
            start: String(normalizedStart(params.start)),
            max_results: String(normalizedCount(params.max_results)),
            sortBy,
            sortOrder,
        });
    }

    async function getPaper(params: ArxivPaperParams): Promise<ArxivResult> {
        if (!params.paper_id || params.paper_id.trim() === "") {
            return { success: false, message: "paper_id is required." };
        }
        const result = await request({ id_list: params.paper_id.trim(), max_results: "1" });
        if (!result.success) return result;
        const papers = Array.isArray(result.data) ? result.data : [];
        if (papers.length === 0) {
            return { success: true, message: "arXiv returned no matching paper.", count: 0, total: 0 };
        }
        return { success: true, message: "arXiv returned one paper.", data: papers[0], count: 1, total: 1 };
    }

    async function runTool<T>(toolName: string, action: () => Promise<T>): Promise<void> {
        try {
            complete(await action());
        } catch (error: unknown) {
            console.error(`arxiv_search.${toolName} failed: ${errorText(error)}`);
            complete({ success: false, message: `arXiv request failed: ${errorText(error)}` });
        }
    }

    return {
        search: (params: ArxivSearchParams) => runTool("search", () => search(params)),
        get_paper: (params: ArxivPaperParams) => runTool("get_paper", () => getPaper(params)),
    };
})();

exports.search = ArxivSearch.search;
exports.get_paper = ArxivSearch.get_paper;
