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
        { "name": "query", "description": { "zh": "关键词或短语；脚本在 arXiv 的 all 字段中搜索。", "en": "Keyword or phrase searched through arXiv's all field." }, "type": "string", "required": true },
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
    url: string;
    pdf_url: string;
    title: string;
    summary: string;
    authors: string[];
    published: string;
    updated: string;
    categories: string[];
    doi: string;
    journal_ref: string;
    comment: string;
}

interface ArxivResult {
    success: boolean;
    message: string;
    data?: ArxivPaper[] | ArxivPaper;
    count?: number;
    total?: number;
    statusCode?: number;
}

const ArxivSearch = (function () {
    const BASE_URL = "https://export.arxiv.org/api/query";
    const MAX_RESULTS = 50;
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
        const pattern = /<entry>([\s\S]*?)<\/entry>/g;
        let match: RegExpExecArray | null;
        while ((match = pattern.exec(xml)) !== null) {
            const entry = match[1] ?? "";
            const id = tagValue(entry, "id");
            const url = linkValue(entry, "rel", "alternate");
            const doiMatch = entry.match(/<arxiv:doi>([\s\S]*?)<\/arxiv:doi>/);
            const journalMatch = entry.match(/<arxiv:journal_ref>([\s\S]*?)<\/arxiv:journal_ref>/);
            const commentMatch = entry.match(/<arxiv:comment>([\s\S]*?)<\/arxiv:comment>/);
            papers.push({
                id,
                url,
                pdf_url: linkValue(entry, "title", "pdf"),
                title: tagValue(entry, "title").replace(/\s+/g, " "),
                summary: tagValue(entry, "summary").replace(/\s+/g, " "),
                authors: tagValues(entry, "name"),
                published: tagValue(entry, "published"),
                updated: tagValue(entry, "updated"),
                categories: categoryValues(entry),
                doi: doiMatch?.[1] ? decodeXml(doiMatch[1].trim()) : "",
                journal_ref: journalMatch?.[1] ? decodeXml(journalMatch[1].trim()) : "",
                comment: commentMatch?.[1] ? decodeXml(commentMatch[1].trim()) : "",
            });
        }
        return papers;
    }

    function parseTotalResults(xml: string): number | undefined {
        const value = tagValue(xml, "opensearch:totalResults");
        if (value === "") return undefined;
        const total = Number(value);
        return Number.isFinite(total) ? total : undefined;
    }

    function normalizedCount(value: number | undefined): number {
        const candidate = value ?? 10;
        return Math.min(Math.max(Math.floor(candidate), 1), MAX_RESULTS);
    }

    function normalizedStart(value: number | undefined): number {
        return Math.max(Math.floor(value ?? 0), 0);
    }

    async function request(params: Record<string, string>): Promise<ArxivResult> {
        const response = await client.get(`${BASE_URL}?${encodeQuery(params)}`, {
            Accept: "application/atom+xml",
            "User-Agent": "Kiyori/0.1.0 (https://github.com/Kiyori-CN/Kiyori)",
        });
        if (!response.isSuccessful()) {
            return { success: false, message: `arXiv request failed: HTTP ${response.statusCode} ${response.statusMessage}`, statusCode: response.statusCode };
        }
        const papers = parsePapers(response.content);
        return {
            success: true,
            message: `arXiv returned ${papers.length} paper(s).`,
            data: papers,
            count: papers.length,
            total: parseTotalResults(response.content),
        };
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
        return request({
            search_query: `all:${params.query.trim()}`,
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
