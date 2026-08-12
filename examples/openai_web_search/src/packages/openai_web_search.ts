/* METADATA
{
  "name": "openai_web_search",
  "display_name": {
    "zh": "OpenAI Web Search",
    "en": "OpenAI Web Search"
  },
  "description": {
    "zh": "通过 Kiyori 宿主调用独立配置的 OpenAI Responses hosted web_search，并返回答案、搜索动作、引用、来源与用量。",
    "en": "Invoke independently configured OpenAI Responses hosted web_search through the Kiyori host and return the answer, search actions, citations, sources, and usage."
  },
  "enabledByDefault": true,
  "category": "Search",
  "tools": [
    {
      "name": "search",
      "description": {
        "zh": "使用独立 OpenAI Web Search 绑定搜索互联网。适用于需要最新网页事实、直接来源和引用的请求；返回结构化 answer、search_actions、citations、sources 与 usage。",
        "en": "Search the web through the independent OpenAI Web Search binding. Use for current web facts, direct sources, and citations; returns structured answer, search_actions, citations, sources, and usage."
      },
      "parameters": [
        {
          "name": "query",
          "description": {
            "zh": "清晰完整的搜索问题。",
            "en": "A clear and complete search question."
          },
          "type": "string",
          "required": true
        },
        {
          "name": "context_size",
          "description": {
            "zh": "可选搜索上下文大小：low、medium 或 high。",
            "en": "Optional search context size: low, medium, or high."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "allowed_domains",
          "description": {
            "zh": "可选允许域名 JSON 字符串数组，只能收窄插件配置的允许列表。",
            "en": "Optional JSON string array of allowed domains. It may only narrow the plugin-configured allowlist."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "blocked_domains",
          "description": {
            "zh": "可选屏蔽域名 JSON 字符串数组。",
            "en": "Optional JSON string array of blocked domains."
          },
          "type": "string",
          "required": false
        },
        {
          "name": "use_configured_location",
          "description": {
            "zh": "是否使用插件配置的 approximate location，true 或 false。",
            "en": "Whether to use the plugin-configured approximate location: true or false."
          },
          "type": "string",
          "required": false
        }
      ]
    }
  ]
}
*/

type SearchToolParams = {
  query: string;
  context_size?: string;
  allowed_domains?: string;
  blocked_domains?: string;
  use_configured_location?: string;
};

function parseDomainArray(rawValue: string | undefined, fieldName: string): string[] {
  if (rawValue === undefined || rawValue.trim() === "") {
    return [];
  }
  const parsed = JSON.parse(rawValue);
  if (!Array.isArray(parsed) || parsed.some(value => typeof value !== "string")) {
    throw new Error(`${fieldName} must be a JSON string array`);
  }
  return parsed;
}

function parseBoolean(rawValue: string | undefined, fieldName: string): boolean {
  if (rawValue === undefined || rawValue.trim() === "") {
    return false;
  }
  const normalized = rawValue.trim().toLowerCase();
  if (normalized === "true") {
    return true;
  }
  if (normalized === "false") {
    return false;
  }
  throw new Error(`${fieldName} must be true or false`);
}

function parseContextSize(
  rawValue: string | undefined
): ToolPkg.OpenAIWebSearchContextSize | undefined {
  if (rawValue === undefined || rawValue.trim() === "") {
    return undefined;
  }
  const normalized = rawValue.trim().toLowerCase();
  if (normalized === "low" || normalized === "medium" || normalized === "high") {
    return normalized;
  }
  throw new Error("context_size must be low, medium, or high");
}

export async function search(
  params: SearchToolParams
): Promise<ToolPkg.OpenAIWebSearchResult> {
  try {
    const request: ToolPkg.OpenAIWebSearchRequest = {
      query: params.query,
      allowed_domains: parseDomainArray(params.allowed_domains, "allowed_domains"),
      blocked_domains: parseDomainArray(params.blocked_domains, "blocked_domains"),
      use_configured_location: parseBoolean(
        params.use_configured_location,
        "use_configured_location"
      ),
    };
    const contextSize = parseContextSize(params.context_size);
    if (contextSize !== undefined) {
      request.context_size = contextSize;
    }
    return await ToolPkg.services.openAIWebSearch.search(request);
  } catch (error) {
    console.error("[openai_web_search] search failed", error);
    throw error;
  }
}
