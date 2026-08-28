import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const REPOSITORY_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

function response({ statusCode = 200, statusMessage = "OK", content = "", payload }) {
    return {
        statusCode,
        statusMessage,
        headers: {},
        content,
        contentType: payload === undefined ? "application/atom+xml" : "application/json",
        size: content.length,
        isSuccessful: () => statusCode >= 200 && statusCode < 300,
        json: () => payload,
        text: () => content,
        bodyAsBase64: () => Buffer.from(content).toString("base64"),
    };
}

async function invokePackage(packageId, toolName, params, plannedResponses = [], environment = {}) {
    const requests = [];
    const completions = [];
    const errors = [];
    const responses = [...plannedResponses];
    const client = {
        get: async (url, headers = {}) => {
            requests.push({ url, headers });
            const nextResponse = responses.shift();
            assert.ok(nextResponse, `Unexpected HTTP request from ${packageId}.${toolName}: ${url}`);
            return nextResponse;
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
        getEnv: (name) => environment[name],
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
    assert.equal(responses.length, 0, `${packageId}.${toolName} did not consume every planned response`);

    return {
        output: JSON.parse(JSON.stringify(completions[0])),
        requests,
        errors,
    };
}

function queryOf(request) {
    return new URL(request.url).searchParams;
}

const ARXIV_SUCCESS_XML = `<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:arxiv="http://arxiv.org/schemas/atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
  <opensearch:totalResults>1</opensearch:totalResults>
  <entry xmlns="http://www.w3.org/2005/Atom" xmlns:arxiv="http://arxiv.org/schemas/atom">
    <id>https://arxiv.org/abs/2201.11903v6</id>
    <updated>2023-01-28T01:38:04Z</updated>
    <published>2022-01-28T02:29:57Z</published>
    <title>Chain of Thought Prompting Elicits Reasoning</title>
    <summary>Reasoning summary.</summary>
    <author><name>Jason Wei</name></author>
    <category term="cs.CL" scheme="http://arxiv.org/schemas/atom"/>
    <arxiv:primary_category term="cs.CL"/>
    <link href="https://arxiv.org/abs/2201.11903v6" rel="alternate" type="text/html"/>
    <link title="pdf" href="https://arxiv.org/pdf/2201.11903v6" rel="related" type="application/pdf"/>
  </entry>
</feed>`;

const ARXIV_ERROR_XML = `<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
  <opensearch:totalResults>1</opensearch:totalResults>
  <entry xmlns="http://www.w3.org/2005/Atom">
    <id>http://arxiv.org/api/errors#incorrect_id_format_for_bad-id</id>
    <title>Error</title>
    <summary>incorrect id format for bad-id</summary>
    <author><name>arXiv api core</name></author>
  </entry>
</feed>`;

test("arXiv builds an explicit all-term query and parses namespaced version metadata", async () => {
    const result = await invokePackage(
        "arxiv_search",
        "search",
        { query: "large language model reasoning", max_results: 5 },
        [response({ content: ARXIV_SUCCESS_XML })],
    );

    assert.equal(result.output.success, true);
    assert.equal(
        queryOf(result.requests[0]).get("search_query"),
        "all:large AND all:language AND all:model AND all:reasoning",
    );
    assert.equal(result.output.search_query, "all:large AND all:language AND all:model AND all:reasoning");
    assert.equal(result.output.data[0].arxiv_id, "2201.11903v6");
    assert.equal(result.output.data[0].version, 6);
    assert.equal(result.output.data[0].primary_category, "cs.CL");
    assert.equal(result.output.data[0].doi_provided, false);
});

test("arXiv omits common stop words from an unquoted query", async () => {
    const result = await invokePackage(
        "arxiv_search",
        "search",
        { query: "chain of thought", max_results: 1 },
        [response({ content: ARXIV_SUCCESS_XML })],
    );

    assert.equal(queryOf(result.requests[0]).get("search_query"), "all:chain AND all:thought");
    assert.equal(result.output.search_query, "all:chain AND all:thought");
});

test("arXiv treats a successful HTTP error feed as a failed tool result", async () => {
    const result = await invokePackage(
        "arxiv_search",
        "get_paper",
        { paper_id: "bad-id" },
        [response({ content: ARXIV_ERROR_XML })],
    );

    assert.equal(result.output.success, false);
    assert.match(result.output.message, /incorrect id format/);
});

test("Crossref DOI output is compact and references are explicit and bounded", async () => {
    const work = {
        DOI: "10.1038/example",
        title: ["Example paper"],
        author: [{ given: "Ada", family: "Lovelace", ORCID: "https://orcid.org/0000-0000" }],
        published: { "date-parts": [[2025, 6, 12]] },
        "container-title": ["Nature"],
        URL: "https://doi.org/10.1038/example",
        type: "journal-article",
        publisher: "Springer Science and Business Media LLC",
        abstract: "<jats:p>We test <jats:bold>reasoning</jats:bold> &amp; results.</jats:p>",
        "is-referenced-by-count": 227,
        ISSN: ["1476-4687"],
        volume: "642",
        issue: "1",
        page: "451-457",
        reference: [{ DOI: "10.1/first", key: "ref1" }, { DOI: "10.1/second", key: "ref2" }],
    };
    const compact = await invokePackage(
        "crossref_search",
        "search_by_doi",
        { doi: work.DOI },
        [response({ payload: { status: "ok", message: work } })],
    );

    assert.equal(compact.output.success, true);
    assert.equal(compact.output.data.reference_count, 2);
    assert.equal(compact.output.data.citation_count, 227);
    assert.equal(compact.output.data.abstract, "We test reasoning & results.");
    assert.equal("reference" in compact.output.data, false);
    assert.equal("references" in compact.output.data, false);

    const withReferences = await invokePackage(
        "crossref_search",
        "search_by_doi",
        { doi: work.DOI, include_references: true, reference_limit: 1 },
        [response({ payload: { status: "ok", message: work } })],
    );
    assert.equal(withReferences.output.data.references.length, 1);
    assert.equal(withReferences.output.data.omitted_reference_count, 1);
});

test("PubMed reports HTTP 200 ESearch business errors", async () => {
    const result = await invokePackage(
        "pubmed_search",
        "search",
        { term: "invalid[field]" },
        [
            response({
                payload: {
                    esearchresult: {
                        count: "0",
                        retmax: "0",
                        retstart: "0",
                        idlist: [],
                        ERROR: "Invalid field was used in a search field tag.",
                    },
                },
            }),
        ],
    );

    assert.equal(result.output.success, false);
    assert.match(result.output.message, /Invalid field/);
});

test("Semantic Scholar uses compact search fields and bounded custom fields", async () => {
    const search = await invokePackage(
        "semantic_scholar_search",
        "search",
        { query: "chain of thought", limit: 3 },
        [response({ payload: { total: 1, offset: 0, data: [] } })],
    );
    const searchFields = queryOf(search.requests[0]).get("fields").split(",");
    assert.equal(searchFields.includes("abstract"), false);
    assert.equal(searchFields.includes("citationCount"), true);

    const detail = await invokePackage(
        "semantic_scholar_search",
        "get_paper",
        { paper_id: "DOI:10.1038/example" },
        [response({ payload: { paperId: "paper-1", title: "Example", abstract: "Detail" } })],
    );
    assert.equal(queryOf(detail.requests[0]).get("fields").split(",").includes("abstract"), true);
    assert.match(detail.output.metric_notice, /Semantic Scholar/);

    const rejected = await invokePackage(
        "semantic_scholar_search",
        "search",
        { query: "chain of thought", fields: "paperId,citations" },
    );
    assert.equal(rejected.output.success, false);
    assert.equal(rejected.requests.length, 0);
    assert.match(rejected.output.message, /citations/);
});

test("OpenAlex rejects unfiltered explicit sort and keeps both paths compact", async () => {
    const rejected = await invokePackage(
        "openalex_search",
        "search",
        { search: "chain of thought", sort: "cited_by_count:desc" },
    );
    assert.equal(rejected.output.success, false);
    assert.equal(rejected.requests.length, 0);
    assert.match(rejected.output.message, /filter/);

    const accepted = await invokePackage(
        "openalex_search",
        "search",
        {
            search: "chain of thought",
            filter: "publication_year:2022-2026",
            sort: "cited_by_count:desc",
        },
        [
            response({
                payload: {
                    meta: { count: 1, per_page: 10, page: 1 },
                    results: [{ id: "https://openalex.org/W1", title: "Example", relevance_score: 10 }],
                },
            }),
        ],
    );
    const searchQuery = queryOf(accepted.requests[0]);
    assert.equal(searchQuery.get("sort"), "cited_by_count:desc");
    assert.equal(searchQuery.get("select").split(",").includes("relevance_score"), true);
    assert.match(accepted.output.data_quality_notice, /verify/i);
    assert.match(accepted.output.metric_notice, /OpenAlex/);

    const detail = await invokePackage(
        "openalex_search",
        "get_work",
        { work_id: "W1" },
        [response({ payload: { id: "https://openalex.org/W1", title: "Example" } })],
    );
    assert.ok(queryOf(detail.requests[0]).get("select"));
});

test("all Academic packages reject non-finite pagination before HTTP", async () => {
    const cases = [
        ["arxiv_search", "search", { query: "reasoning", max_results: Number.NaN }],
        ["crossref_search", "search_by_keyword", { query: "reasoning", rows: Number.POSITIVE_INFINITY }],
        ["pubmed_search", "search", { term: "reasoning", retmax: Number.NaN }],
        ["semantic_scholar_search", "search", { query: "reasoning", limit: Number.NEGATIVE_INFINITY }],
        ["openalex_search", "search", { search: "reasoning", per_page: Number.NaN }],
    ];

    for (const [packageId, toolName, params] of cases) {
        const result = await invokePackage(packageId, toolName, params);
        assert.equal(result.output.success, false, packageId);
        assert.equal(result.requests.length, 0, packageId);
        assert.match(result.output.message, /finite/i, packageId);
    }
});
