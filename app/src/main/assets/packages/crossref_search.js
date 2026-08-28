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
      "description": { "zh": "按 DOI 查询一条紧凑的 Crossref 出版记录；参考文献需显式请求并限制数量。", "en": "Retrieve one compact Crossref publication record by DOI; references are explicit and bounded." },
      "parameters": [
        { "name": "doi", "description": { "zh": "例如 10.1038/nature12373。", "en": "For example 10.1038/nature12373." }, "type": "string", "required": true },
        { "name": "include_references", "description": { "zh": "是否返回规范化参考文献，默认 false。", "en": "Whether to include normalized references; defaults to false." }, "type": "boolean", "required": false },
        { "name": "reference_limit", "description": { "zh": "显式返回参考文献时的上限，默认 20，范围 1-50。", "en": "Reference limit when explicitly included; defaults to 20 and is clamped to 1-50." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "search_by_keyword",
      "description": { "zh": "在 Crossref 出版元数据中按书目关键词搜索；适合元数据发现，不替代专门学术图谱的主题排序。", "en": "Search Crossref publication metadata by bibliographic terms; this is metadata discovery, not a topical-ranking substitute for scholarly graphs." },
      "parameters": [
        { "name": "query", "description": { "zh": "关键词或短语。", "en": "Keyword or phrase." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "offset", "description": { "zh": "从 0 开始的结果偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false },
        { "name": "filter", "description": { "zh": "可选 Crossref 官方 filter 表达式，用于限制年份、类型等范围。", "en": "Optional official Crossref filter expression for narrowing year, type, and related metadata." }, "type": "string", "required": false },
        { "name": "sort", "description": { "zh": "relevance、score、updated、deposited、indexed、published 或 created。", "en": "relevance, score, updated, deposited, indexed, published, or created." }, "type": "string", "required": false },
        { "name": "order", "description": { "zh": "asc 或 desc。", "en": "asc or desc." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "search_by_author",
      "description": { "zh": "按作者姓名搜索 Crossref works。", "en": "Search Crossref works by author name." },
      "parameters": [
        { "name": "author", "description": { "zh": "作者姓名。", "en": "Author name." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "offset", "description": { "zh": "从 0 开始的结果偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "search_by_title",
      "description": { "zh": "按作品标题或标题短语搜索 Crossref works。", "en": "Search Crossref works by title or title phrase." },
      "parameters": [
        { "name": "title", "description": { "zh": "作品标题或标题关键词。", "en": "Work title or title keywords." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "offset", "description": { "zh": "从 0 开始的结果偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "search_by_issn",
      "description": { "zh": "按期刊 ISSN 查询该期刊的 Crossref works。", "en": "Retrieve Crossref works published in a journal by ISSN." },
      "parameters": [
        { "name": "issn", "description": { "zh": "例如 1476-4687。", "en": "For example 1476-4687." }, "type": "string", "required": true },
        { "name": "rows", "description": { "zh": "结果数量，默认 10，范围 1-100。", "en": "Result count; defaults to 10 and is clamped to 1-100." }, "type": "number", "required": false },
        { "name": "offset", "description": { "zh": "从 0 开始的结果偏移，默认 0。", "en": "Zero-based result offset; defaults to 0." }, "type": "number", "required": false }
      ]
    }
  ]
}*/
/// <reference path="./types/index.d.ts" />
const CrossrefSearch = (function () {
    const BASE_URL = "https://api.crossref.org";
    const MAX_ROWS = 100;
    const MAX_REFERENCE_RESULTS = 50;
    const MAX_ABSTRACT_CHARS = 1500;
    const METRIC_NOTICE = "Crossref citation counts are source-specific and should only be compared within Crossref.";
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
    function finiteInteger(value, defaultValue, minimum, maximum, name) {
        const candidate = value ?? defaultValue;
        if (!Number.isFinite(candidate))
            throw new Error(`${name} must be a finite number.`);
        return Math.min(Math.max(Math.floor(candidate), minimum), maximum);
    }
    function rows(value) {
        return finiteInteger(value, 10, 1, MAX_ROWS, "rows");
    }
    function offset(value) {
        return finiteInteger(value, 0, 0, Number.MAX_SAFE_INTEGER, "offset");
    }
    function referenceLimit(value) {
        return finiteInteger(value, 20, 1, MAX_REFERENCE_RESULTS, "reference_limit");
    }
    function decodeEntities(value) {
        return value
            .replace(/&#x([0-9a-f]+);/gi, (_match, hex) => String.fromCodePoint(parseInt(hex, 16)))
            .replace(/&#([0-9]+);/g, (_match, decimal) => String.fromCodePoint(parseInt(decimal, 10)))
            .replace(/&amp;/g, "&")
            .replace(/&lt;/g, "<")
            .replace(/&gt;/g, ">")
            .replace(/&quot;/g, '"')
            .replace(/&apos;/g, "'");
    }
    function compactText(value, maximumLength) {
        if (!value)
            return { text: "", truncated: false };
        const text = decodeEntities(value.replace(/<[^>]+>/g, " ")).replace(/\s+/g, " ").trim();
        if (text.length <= maximumLength)
            return { text, truncated: false };
        return { text: `${text.slice(0, maximumLength).trimEnd()}...`, truncated: true };
    }
    function dateText(value) {
        const parts = value?.["date-parts"]?.[0];
        if (!parts || parts.length === 0 || !Number.isFinite(parts[0]))
            return "";
        const year = String(Math.floor(parts[0]));
        const month = parts[1];
        const day = parts[2];
        if (!Number.isFinite(month))
            return year;
        const yearMonth = `${year}-${String(Math.floor(month)).padStart(2, "0")}`;
        return Number.isFinite(day) ? `${yearMonth}-${String(Math.floor(day)).padStart(2, "0")}` : yearMonth;
    }
    function publicationAuthor(author) {
        const providedName = author.name?.trim();
        const structuredName = [author.given?.trim(), author.family?.trim()].filter((part) => part).join(" ");
        return {
            name: providedName ?? structuredName,
            orcid: author.ORCID?.trim() ?? "",
        };
    }
    function compactReference(reference) {
        return {
            DOI: reference.DOI,
            key: reference.key,
            "article-title": reference["article-title"],
            author: reference.author,
            year: reference.year,
            "journal-title": reference["journal-title"],
            unstructured: reference.unstructured,
        };
    }
    // Crossref deposits can include large JATS abstracts and full reference lists. The default
    // projection keeps publication identity while references remain an explicit bounded request.
    function publicationRecord(work, includeReferences, maximumReferences) {
        const abstract = compactText(work.abstract, MAX_ABSTRACT_CHARS);
        const rawReferences = work.reference ?? [];
        const depositedReferenceCount = work["references-count"];
        const referenceCount = Number.isFinite(depositedReferenceCount) ? depositedReferenceCount ?? rawReferences.length : rawReferences.length;
        const record = {
            source: "Crossref",
            doi: work.DOI ?? "",
            title: work.title?.join(" ").trim() ?? "",
            subtitle: work.subtitle?.join(" ").trim() ?? "",
            authors: (work.author ?? []).map(publicationAuthor),
            published_date: dateText(work.published),
            published_online_date: dateText(work["published-online"]),
            published_print_date: dateText(work["published-print"]),
            container_title: work["container-title"]?.join(" ").trim() ?? "",
            url: work.URL ?? "",
            type: work.type ?? "",
            publisher: work.publisher ?? "",
            abstract: abstract.text,
            abstract_truncated: abstract.truncated,
            citation_count: work["is-referenced-by-count"] ?? 0,
            reference_count: referenceCount,
            issn: work.ISSN ?? [],
            isbn: work.ISBN ?? [],
            volume: work.volume ?? "",
            issue: work.issue ?? "",
            page: work.page ?? "",
            article_number: work["article-number"] ?? "",
            subjects: work.subject ?? [],
            license_urls: (work.license ?? []).map((license) => license.URL?.trim() ?? "").filter((url) => url !== ""),
        };
        if (includeReferences) {
            record.references = rawReferences.slice(0, maximumReferences).map(compactReference);
            record.omitted_reference_count = Math.max(referenceCount - record.references.length, 0);
        }
        return record;
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
        const payload = response.json();
        if (payload.status !== "ok") {
            return { success: false, message: `Crossref request failed: unexpected response status ${payload.status}.`, statusCode: response.statusCode };
        }
        return payload;
    }
    function worksResult(payload, resultOffset) {
        const message = payload.message;
        const items = message.items ?? [];
        return {
            success: true,
            message: `Crossref returned ${items.length} work(s).`,
            data: items.map((work) => publicationRecord(work, false, 0)),
            total: message["total-results"],
            count: items.length,
            offset: resultOffset,
            metric_notice: METRIC_NOTICE,
        };
    }
    async function searchByDoi(params) {
        if (!params.doi || params.doi.trim() === "")
            return { success: false, message: "doi is required." };
        const payload = await request(`/works/${encodeURIComponent(params.doi.trim())}`, {});
        if ("success" in payload)
            return payload;
        const work = payload.message;
        const includeReferences = params.include_references === true;
        const maximumReferences = includeReferences ? referenceLimit(params.reference_limit) : 0;
        return {
            success: true,
            message: "Crossref returned one compact work record.",
            data: publicationRecord(work, includeReferences, maximumReferences),
            count: 1,
            total: 1,
            metric_notice: METRIC_NOTICE,
        };
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
        const resultOffset = offset(params.offset);
        const query = {
            "query.bibliographic": params.query.trim(),
            rows: String(rows(params.rows)),
            offset: String(resultOffset),
            sort,
            order,
        };
        if (params.filter?.trim())
            query.filter = params.filter.trim();
        const payload = await request("/works", query);
        return "success" in payload ? payload : worksResult(payload, resultOffset);
    }
    async function searchByAuthor(params) {
        if (!params.author || params.author.trim() === "")
            return { success: false, message: "author is required." };
        const resultOffset = offset(params.offset);
        const payload = await request("/works", {
            "query.author": params.author.trim(),
            rows: String(rows(params.rows)),
            offset: String(resultOffset),
        });
        return "success" in payload ? payload : worksResult(payload, resultOffset);
    }
    async function searchByTitle(params) {
        if (!params.title || params.title.trim() === "")
            return { success: false, message: "title is required." };
        const resultOffset = offset(params.offset);
        const payload = await request("/works", {
            "query.title": params.title.trim(),
            rows: String(rows(params.rows)),
            offset: String(resultOffset),
        });
        return "success" in payload ? payload : worksResult(payload, resultOffset);
    }
    async function searchByIssn(params) {
        if (!params.issn || params.issn.trim() === "")
            return { success: false, message: "issn is required." };
        const resultOffset = offset(params.offset);
        const payload = await request(`/journals/${encodeURIComponent(params.issn.trim())}/works`, {
            rows: String(rows(params.rows)),
            offset: String(resultOffset),
        });
        return "success" in payload ? payload : worksResult(payload, resultOffset);
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
