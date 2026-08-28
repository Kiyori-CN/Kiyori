import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const REPOSITORY_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const COMMON_QUERY = "large language model reasoning chain of thought";
const ARXIV_DETAIL_ID = "2201.11903";
const SKIP_SEMANTIC_SCHOLAR = process.env.ACADEMIC_LIVE_SKIP_SEMANTIC === "1";
const PUBMED_NATURE_QUERY = '("large language model"[Title/Abstract]) AND Nature[Journal]';
const REQUIRED_ENVIRONMENT = [
    "SEMANTIC_SCHOLAR_API_KEY",
    "OPENALEX_API_KEY",
    "PUBMED_API_KEY",
    "PUBMED_EMAIL",
];

function requireEnvironment() {
    const required = SKIP_SEMANTIC_SCHOLAR
        ? REQUIRED_ENVIRONMENT.filter((name) => name !== "SEMANTIC_SCHOLAR_API_KEY")
        : REQUIRED_ENVIRONMENT;
    const missing = required.filter((name) => !process.env[name]?.trim());
    if (missing.length > 0) throw new Error(`Missing live-test environment variable(s): ${missing.join(", ")}`);
}

function delay(milliseconds) {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function responseFromFetch(response, content) {
    return {
        statusCode: response.status,
        statusMessage: response.statusText,
        headers: Object.fromEntries(response.headers.entries()),
        content,
        contentType: response.headers.get("content-type") ?? "",
        size: Buffer.byteLength(content),
        isSuccessful: () => response.ok,
        json: () => JSON.parse(content),
        text: () => content,
        bodyAsBase64: () => Buffer.from(content).toString("base64"),
    };
}

async function invokePackage(packageId, toolName, params) {
    const requests = [];
    const completions = [];
    const errors = [];
    const client = {
        get: async (url, headers = {}) => {
            const response = await fetch(url, {
                headers,
                redirect: "follow",
                signal: AbortSignal.timeout(65000),
            });
            const content = await response.text();
            requests.push({ statusCode: response.status, responseBytes: Buffer.byteLength(content) });
            return responseFromFetch(response, content);
        },
    };
    const builder = {
        connectTimeout: () => builder,
        readTimeout: () => builder,
        writeTimeout: () => builder,
        followRedirects: () => builder,
        retryOnConnectionFailure: () => builder,
        build: () => client,
    };
    const context = vm.createContext({
        exports: {},
        OkHttp: { newBuilder: () => builder },
        getEnv: (name) => process.env[name],
        complete: (value) => completions.push(value),
        console: {
            error: (message) => errors.push(String(message)),
            log: () => {},
            warn: () => {},
        },
    });
    const source = await readFile(path.join(REPOSITORY_ROOT, "examples", `${packageId}.js`), "utf8");
    new vm.Script(source, { filename: `${packageId}.js` }).runInContext(context);
    const exportedTool = context.exports[toolName];
    assert.equal(typeof exportedTool, "function", `Missing export ${packageId}.${toolName}`);

    await exportedTool(params);
    assert.equal(completions.length, 1, `${packageId}.${toolName} must complete exactly once`);
    const output = JSON.parse(JSON.stringify(completions[0]));
    return { output, requests, errors };
}

function firstRecord(output) {
    if (Array.isArray(output.data)) return output.data[0];
    return output.data;
}

function summarize(packageId, toolName, invocation) {
    const record = firstRecord(invocation.output);
    return {
        package: packageId,
        tool: toolName,
        success: invocation.output.success === true,
        message: invocation.output.message,
        http_statuses: invocation.requests.map((request) => request.statusCode),
        response_bytes: invocation.requests.reduce((total, request) => total + request.responseBytes, 0),
        count: invocation.output.count,
        total: invocation.output.total,
        title: record?.title,
        identifier: record?.paperId ?? record?.arxiv_id ?? record?.uid ?? record?.doi ?? record?.id,
        logged_errors: invocation.errors.length,
    };
}

function requireSuccess(label, invocation) {
    if (invocation.output.success !== true) throw new Error(`${label} failed: ${invocation.output.message}`);
}

requireEnvironment();
const report = [];

const pubmedSearch = await invokePackage("pubmed_search", "search", {
    term: PUBMED_NATURE_QUERY,
    retmax: 5,
    sort: "relevance",
});
requireSuccess("PubMed search", pubmedSearch);
report.push(summarize("pubmed_search", "search", pubmedSearch));
const natureRecord = firstRecord(pubmedSearch.output);
const naturePmid = natureRecord?.uid;
const natureDoi = natureRecord?.articleids?.find((identifier) => identifier.idtype === "doi")?.value;
if (!naturePmid || !natureDoi) throw new Error("PubMed Nature result did not expose both PMID and DOI for cross-source detail verification.");

const pubmedDetail = await invokePackage("pubmed_search", "get_articles", { ids: [naturePmid] });
requireSuccess("PubMed detail", pubmedDetail);
report.push(summarize("pubmed_search", "get_articles", pubmedDetail));

if (SKIP_SEMANTIC_SCHOLAR) {
    report.push({ package: "semantic_scholar_search", skipped: true, reason: "already tested in the preceding low-frequency live phase" });
} else {
    const semanticSearch = await invokePackage("semantic_scholar_search", "search", {
        query: COMMON_QUERY,
        limit: 5,
    });
    requireSuccess("Semantic Scholar search", semanticSearch);
    report.push(summarize("semantic_scholar_search", "search", semanticSearch));
    await delay(1200);
    const semanticDetail = await invokePackage("semantic_scholar_search", "get_paper", {
        paper_id: `PMID:${naturePmid}`,
    });
    requireSuccess("Semantic Scholar detail", semanticDetail);
    report.push(summarize("semantic_scholar_search", "get_paper", semanticDetail));
}

const arxivSearch = await invokePackage("arxiv_search", "search", {
    query: "chain of thought",
    max_results: 5,
});
requireSuccess("arXiv search", arxivSearch);
report.push(summarize("arxiv_search", "search", arxivSearch));
await delay(3200);
const arxivDetail = await invokePackage("arxiv_search", "get_paper", { paper_id: ARXIV_DETAIL_ID });
requireSuccess("arXiv detail", arxivDetail);
report.push(summarize("arxiv_search", "get_paper", arxivDetail));

const openAlexSearch = await invokePackage("openalex_search", "search", {
    search: COMMON_QUERY,
    per_page: 5,
});
requireSuccess("OpenAlex search", openAlexSearch);
report.push(summarize("openalex_search", "search", openAlexSearch));
const openAlexDetail = await invokePackage("openalex_search", "get_work", { work_id: natureDoi });
requireSuccess("OpenAlex detail", openAlexDetail);
report.push(summarize("openalex_search", "get_work", openAlexDetail));

const crossrefSearch = await invokePackage("crossref_search", "search_by_keyword", {
    query: COMMON_QUERY,
    rows: 5,
});
requireSuccess("Crossref search", crossrefSearch);
report.push(summarize("crossref_search", "search_by_keyword", crossrefSearch));
const crossrefDetail = await invokePackage("crossref_search", "search_by_doi", {
    doi: natureDoi,
    include_references: false,
});
requireSuccess("Crossref detail", crossrefDetail);
report.push(summarize("crossref_search", "search_by_doi", crossrefDetail));

process.stdout.write(
    `${JSON.stringify(
        {
            common_query: COMMON_QUERY,
            cross_source_record: {
                pmid: naturePmid,
                doi: natureDoi,
                title: natureRecord.title,
            },
            semantic_scholar_skipped: SKIP_SEMANTIC_SCHOLAR,
            calls: report,
        },
        null,
        2,
    )}\n`,
);
