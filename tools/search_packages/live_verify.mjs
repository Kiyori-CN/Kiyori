import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const TOOL_DIRECTORY = path.dirname(fileURLToPath(import.meta.url));
const REPOSITORY_ROOT = path.resolve(TOOL_DIRECTORY, "..", "..");
const cursors = new Map();

class ResponseAdapter {
  constructor(response, content) {
    this.raw = { statusCode: response.status, content };
    this.statusCode = response.status;
    this.statusMessage = response.statusText;
    this.headers = Object.fromEntries(response.headers.entries());
    this.content = content;
    this.contentType = response.headers.get("content-type") || "";
    this.size = Buffer.byteLength(content);
  }

  json() {
    return JSON.parse(this.content);
  }

  text() {
    return this.content;
  }

  bodyAsBase64() {
    return Buffer.from(this.content).toString("base64");
  }

  isSuccessful() {
    return this.statusCode >= 200 && this.statusCode < 300;
  }
}

class RequestBuilder {
  constructor(client) {
    this.client = client;
    this.request = { method: "GET", headers: {} };
  }

  url(value) {
    this.request.url = value;
    return this;
  }

  method(value) {
    this.request.method = value;
    return this;
  }

  header(name, value) {
    this.request.headers[name] = value;
    return this;
  }

  headers(values) {
    Object.assign(this.request.headers, values || {});
    return this;
  }

  body(value, type = "text") {
    this.request.body = typeof value === "string" ? value : JSON.stringify(value);
    if (type === "json") this.request.headers["Content-Type"] = "application/json";
    return this;
  }

  jsonBody(value) {
    this.request.body = JSON.stringify(value);
    this.request.headers["Content-Type"] = "application/json";
    return this;
  }

  build() {
    return { ...this.request, execute: () => this.client.execute(this.request) };
  }
}

class ClientAdapter {
  constructor(config) {
    this.config = config;
  }

  newRequest() {
    return new RequestBuilder(this);
  }

  async execute(request) {
    const timeout = Math.max(this.config.connectTimeout, this.config.readTimeout, 1000);
    const response = await fetch(request.url, {
      method: request.method,
      headers: request.headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
      redirect: this.config.followRedirects ? "follow" : "manual",
      signal: AbortSignal.timeout(timeout),
    });
    return new ResponseAdapter(response, await response.text());
  }

  get(url, headers = {}) {
    return this.execute({ url, method: "GET", headers });
  }

  post(url, body, headers = {}) {
    return this.execute({
      url,
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: typeof body === "string" ? body : JSON.stringify(body),
    });
  }

  put(url, body, headers = {}) {
    return this.execute({
      url,
      method: "PUT",
      headers: { "Content-Type": "application/json", ...headers },
      body: typeof body === "string" ? body : JSON.stringify(body),
    });
  }

  delete(url, headers = {}) {
    return this.execute({ url, method: "DELETE", headers });
  }

  streamExecute(request) {
    return this.execute(request);
  }
}

class ClientBuilder {
  constructor() {
    this.config = {
      connectTimeout: 15000,
      readTimeout: 120000,
      writeTimeout: 120000,
      followRedirects: true,
      retryOnConnectionFailure: false,
    };
  }

  connectTimeout(value) {
    this.config.connectTimeout = Number(value);
    return this;
  }

  readTimeout(value) {
    this.config.readTimeout = Number(value);
    return this;
  }

  writeTimeout(value) {
    this.config.writeTimeout = Number(value);
    return this;
  }

  followRedirects(value) {
    this.config.followRedirects = Boolean(value);
    return this;
  }

  retryOnConnectionFailure(value) {
    this.config.retryOnConnectionFailure = Boolean(value);
    return this;
  }

  addInterceptor() {
    return this;
  }

  build() {
    return new ClientAdapter({ ...this.config });
  }
}

const OkHttp = {
  newClient: () => new ClientBuilder().build(),
  newBuilder: () => new ClientBuilder(),
};

const Java = {
  callStatic(className, methodName, namespace, keyCount) {
    if (
      className !== "com.ai.assistance.operit.core.tools.javascript.ApiKeyRoundRobin" ||
      methodName !== "nextIndex"
    ) {
      throw new Error(`Unsupported Java bridge call: ${className}.${methodName}`);
    }
    const count = Number(keyCount);
    if (!String(namespace).trim() || !Number.isInteger(count) || count <= 0) {
      throw new Error("Invalid round-robin arguments");
    }
    const current = cursors.get(namespace) || 0;
    cursors.set(namespace, current === Number.MAX_SAFE_INTEGER ? 0 : current + 1);
    return current % count;
  },
};

async function loadPackage(packageId) {
  const source = await fs.readFile(path.join(REPOSITORY_ROOT, "examples", `${packageId}.js`), "utf8");
  const logs = [];
  const sandbox = {
    exports: {},
    getEnv: (name) => process.env[name],
    Java,
    OkHttp,
    console: {
      log: (...values) => logs.push({ level: "log", message: values.map(String).join(" ") }),
      info: (...values) => logs.push({ level: "info", message: values.map(String).join(" ") }),
      warn: (...values) => logs.push({ level: "warn", message: values.map(String).join(" ") }),
      error: (...values) => logs.push({ level: "error", message: values.map(String).join(" ") }),
    },
  };
  vm.runInContext(source, vm.createContext(sandbox), { filename: `${packageId}.js` });
  return { tools: sandbox.exports, logs };
}

function deepFind(value, key) {
  if (!value || typeof value !== "object") return undefined;
  if (Object.prototype.hasOwnProperty.call(value, key)) return value[key];
  for (const child of Object.values(value)) {
    const found = deepFind(child, key);
    if (found !== undefined) return found;
  }
  return undefined;
}

function resultSummary(result) {
  if (!result || typeof result !== "object") return { success: false, message: String(result) };
  return {
    success: result.success === true,
    allKeysReachable: result.allKeysReachable,
    statusCode: result.statusCode,
    keyIndex: result.keyIndex,
    total: result.total,
    passed: result.passed,
    endpoint: result.endpoint,
    message: result.message,
  };
}

const reports = [];

async function check(label, operation, options = {}) {
  const startedAt = Date.now();
  try {
    const result = await operation();
    const summary = resultSummary(result);
    const accepted = options.acceptHttpResponse
      ? summary.success || Number(summary.statusCode || deepFind(result, "statusCode")) > 0
      : summary.success;
    reports.push({ label, accepted, durationMs: Date.now() - startedAt, ...summary });
    return result;
  } catch (error) {
    reports.push({
      label,
      accepted: false,
      success: false,
      durationMs: Date.now() - startedAt,
      message: error instanceof Error ? error.message : String(error),
    });
    return null;
  }
}

async function verifyTavily() {
  const { tools } = await loadPackage("tavily_search");
  await check("tavily.test_keys", () => tools.test_keys({}));
  await check("tavily.search", () => tools.search({
    query: "OpenAI official documentation",
    options: { search_depth: "basic", max_results: 2, include_usage: true, safe_search: true },
  }));
  await check("tavily.extract", () => tools.extract({
    urls: ["https://example.com"],
    options: { extract_depth: "basic", format: "markdown", include_usage: true },
  }));
  await check("tavily.map", () => tools.map({
    url: "https://example.com",
    options: { max_depth: 1, max_breadth: 2, limit: 2, include_usage: true },
  }));
  await check("tavily.crawl", () => tools.crawl({
    url: "https://example.com",
    options: { max_depth: 1, max_breadth: 2, limit: 1, format: "markdown", include_usage: true },
  }));
  await check("tavily.usage", () => tools.usage({}));
  await check("tavily.logs", () => tools.logs({ options: { limit: 1 } }), { acceptHttpResponse: true });
  await check("tavily.organization_usage", () => tools.organization_usage({
    organization_name: "kiyori-live-verification",
    options: {},
  }), { acceptHttpResponse: true });
  const research = await check("tavily.create_research", () => tools.create_research({
    input: "Summarize what example.com is used for.",
    options: { model: "mini", output_length: "short" },
  }), { acceptHttpResponse: true });
  const requestId = deepFind(research && research.data, "request_id") || deepFind(research && research.data, "id");
  if (requestId) {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    await check("tavily.get_research", () => tools.get_research({ request_id: String(requestId), key_index: research.keyIndex }), { acceptHttpResponse: true });
  } else {
    reports.push({ label: "tavily.get_research", accepted: false, success: false, message: "create_research returned no request ID" });
  }
  await check("tavily.create_research.stream", () => tools.create_research({
    input: "Briefly explain the purpose of example.com.",
    options: { model: "mini", output_length: "short", stream: true },
  }), { acceptHttpResponse: true });
}

async function verifySerpApi() {
  const { tools } = await loadPackage("serpapi_search");
  await check("serpapi.test_keys", () => tools.test_keys({}));
  const search = await check("serpapi.search", () => tools.search({
    engine: "google",
    params: { q: "OpenAI official documentation", num: 2, no_cache: true },
    output: "json",
  }));
  await check("serpapi.locations", () => tools.locations({ query: "Shanghai", limit: 2 }));
  await check("serpapi.account", () => tools.account({}));
  const searchId = deepFind(search && search.data, "id");
  if (searchId) {
    await check("serpapi.get_search.json", () => tools.get_search({ search_id: String(searchId), key_index: search.keyIndex, output: "json" }));
    await check("serpapi.get_search.pixel", () => tools.get_search({ search_id: String(searchId), key_index: search.keyIndex, output: "pixel" }), { acceptHttpResponse: true });
    await check("serpapi.get_search.html", () => tools.get_search({ search_id: String(searchId), key_index: search.keyIndex, output: "html" }), { acceptHttpResponse: true });
  } else {
    reports.push({ label: "serpapi.get_search", accepted: false, success: false, message: "search returned no search ID" });
  }
}

async function verifyBrave() {
  const { tools } = await loadPackage("brave_search");
  await check("brave.test_keys", () => tools.test_keys({}));
  await check("brave.web_search.get", () => tools.web_search({ q: "OpenAI official documentation", params: { count: 2 }, method: "GET" }));
  await check("brave.web_search.post", () => tools.web_search({ q: "OpenAI official documentation", params: { count: 2 }, method: "POST" }));
  await check("brave.llm_context.get", () => tools.llm_context({ q: "OpenAI official documentation", params: { count: 2, maximum_number_of_tokens: 1024 }, method: "GET" }));
  await check("brave.llm_context.post", () => tools.llm_context({ q: "OpenAI official documentation", params: { count: 2, maximum_number_of_tokens: 1024 }, method: "POST" }));
  await check("brave.news_search.get", () => tools.news_search({ q: "OpenAI", params: { count: 2 }, method: "GET" }));
  await check("brave.news_search.post", () => tools.news_search({ q: "OpenAI", params: { count: 2 }, method: "POST" }));
  await check("brave.video_search.get", () => tools.video_search({ q: "OpenAI", params: { count: 2 }, method: "GET" }));
  await check("brave.video_search.post", () => tools.video_search({ q: "OpenAI", params: { count: 2 }, method: "POST" }));
  await check("brave.image_search", () => tools.image_search({ q: "OpenAI logo", params: { count: 2, safesearch: "strict" } }));
  const places = await check("brave.place_search", () => tools.place_search({
    params: { q: "coffee", location: "shanghai china", count: 2 },
  }));
  const poiId = deepFind(places && places.data, "id");
  if (poiId) {
    await check("brave.local_pois", () => tools.local_pois({ ids: [String(poiId)], key_index: places.keyIndex, params: { search_lang: "en" } }));
    await check("brave.poi_descriptions", () => tools.poi_descriptions({ ids: [String(poiId)], key_index: places.keyIndex }));
  } else {
    reports.push({ label: "brave.local_pois", accepted: false, success: false, message: "place_search returned no POI ID" });
    reports.push({ label: "brave.poi_descriptions", accepted: false, success: false, message: "place_search returned no POI ID" });
  }
  const richSeed = await check("brave.rich_seed", () => tools.web_search({
    q: "weather in Shanghai",
    params: { count: 2, enable_rich_callback: 1 },
    method: "GET",
  }));
  const callbackKey = deepFind(richSeed && richSeed.data, "callback_key");
  if (callbackKey) {
    await check("brave.rich_search", () => tools.rich_search({ callback_key: String(callbackKey), key_index: richSeed.keyIndex }));
  } else {
    reports.push({ label: "brave.rich_search", accepted: false, success: false, message: "web_search returned no callback_key" });
  }
  const summarySeed = await check("brave.summarizer_seed", () => tools.web_search({
    q: "what is example.com",
    params: { count: 2, summary: 1 },
    method: "GET",
  }), { acceptHttpResponse: true });
  const summaryKey = deepFind(summarySeed && summarySeed.data, "key");
  const summarizerKey = summaryKey ? String(summaryKey) : "live-verification-invalid-key";
  for (const resource of ["search", "summary", "summary_streaming", "title", "enrichments", "followups", "entity_info"]) {
    await check(`brave.summarizer.${resource}`, () => tools.summarizer({
      resource,
      key: summarizerKey,
      key_index: summarySeed.keyIndex,
      params: resource === "search" ? { inline_references: true } : {},
    }), { acceptHttpResponse: true });
  }
  await check("brave.answers", () => tools.answers({
    messages: [{ role: "user", content: "What is example.com?" }],
    model: "brave",
    stream: false,
  }));
  await check("brave.answers.stream", () => tools.answers({
    messages: [{ role: "user", content: "What is example.com?" }],
    model: "brave",
    stream: true,
    options: { enable_citations: true },
  }));
  await check("brave.autosuggest", () => tools.autosuggest({ q: "OpenA", params: { count: 2 } }));
  await check("brave.spellcheck", () => tools.spellcheck({ q: "opneai" }));
}

async function verifyZhipu() {
  const { tools } = await loadPackage("zhipu_search");
  await check("zhipu.test_keys", () => tools.test_keys({}));
  await check("zhipu.search", () => tools.search({
    search_query: "OpenAI official documentation",
    search_engine: "search_std",
    search_intent: false,
    options: {
      count: 2,
      search_recency_filter: "oneMonth",
      content_size: "medium",
      request_id: "kiyori-live-verify",
      user_id: "kiyori-test-user",
    },
  }));
}

await verifyTavily();
await verifySerpApi();
await verifyBrave();
await verifyZhipu();

const accepted = reports.filter((report) => report.accepted).length;
const output = {
  completedAt: new Date().toISOString(),
  total: reports.length,
  accepted,
  failed: reports.length - accepted,
  reports,
};
process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
process.exitCode = accepted === reports.length ? 0 : 1;
