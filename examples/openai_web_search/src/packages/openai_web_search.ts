/* METADATA
{
  "name": "openai_web_search",
  "display_name": {
    "zh": "OpenAI 搜索",
    "en": "OpenAI Search"
  },
  "description": {
    "zh": "需要最新网页事实、直接来源和引用时调用 OpenAI 搜索；返回 answer、search_actions、citations、sources 和 usage。",
    "en": "Use OpenAI Search for current web facts, direct sources, and citations; returns answer, search_actions, citations, sources, and usage."
  },
  "enabledByDefault": true,
  "category": "Search",
  "tools": [
    {
      "name": "openai_search",
      "description": {
        "zh": "需要最新网页事实、直接来源和引用时调用 OpenAI 搜索；返回 answer、search_actions、citations、sources 和 usage。",
        "en": "Use OpenAI Search for current web facts, direct sources, and citations; returns answer, search_actions, citations, sources, and usage."
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
            "zh": "可选允许域名数组，只能收窄插件配置的允许列表。",
            "en": "Optional array of allowed domains. It may only narrow the plugin-configured allowlist."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "blocked_domains",
          "description": {
            "zh": "可选屏蔽域名数组。",
            "en": "Optional array of blocked domains."
          },
          "type": "array",
          "required": false
        },
        {
          "name": "use_configured_location",
          "description": {
            "zh": "是否使用插件配置的 approximate location。",
            "en": "Whether to use the plugin-configured approximate location."
          },
          "type": "boolean",
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
  allowed_domains?: string[];
  blocked_domains?: string[];
  use_configured_location?: boolean;
};

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

export async function openai_search(
  params: SearchToolParams
): Promise<ToolPkg.OpenAIWebSearchResult> {
  const request: ToolPkg.OpenAIWebSearchRequest = {
    query: params.query,
  };
  const contextSize = parseContextSize(params.context_size);
  if (contextSize !== undefined) {
    request.context_size = contextSize;
  }
  if (params.allowed_domains !== undefined) {
    request.allowed_domains = params.allowed_domains;
  }
  if (params.blocked_domains !== undefined) {
    request.blocked_domains = params.blocked_domains;
  }
  if (params.use_configured_location !== undefined) {
    request.use_configured_location = params.use_configured_location;
  }
  return ToolPkg.services.openAIWebSearch.search(request);
}
