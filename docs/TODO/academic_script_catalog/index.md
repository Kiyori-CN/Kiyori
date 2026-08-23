# Academic 学术脚本分组与五源 API

## 1. 状态与任务契约

- 当前状态：`IMPLEMENTATION VERIFIED`
- 目标：在“扩展 -> 脚本”中新增 `Academic` 学术学习分组，使用独立图标和明暗配色，并提供
  arXiv、Crossref、PubMed、Semantic Scholar、OpenAlex 五个官方 API 脚本。
- 分组命名决策：使用 `Academic`。它能覆盖预印本、DOI 元数据、生物医学索引、学术图谱和开放学术
  索引；`Study` 偏向学习过程，`Academy` 是机构名词，`Scholar` 更像角色/用户身份，均不如
  `Academic` 适合作为稳定分类键。
- 非目标：不新增代理或备用域名，不修改 JS 宿主安全模型，不把凭据写入源码/文档/日记/构建产物，
  不改动与本任务无关的网络代理工作树变更。
- 发布假设：Kiyori 仍是未公开发行的开发版；本轮直接把现有 `crossref_search` 从 `Search` 移到
  `Academic`，不保留重复分类或旧分类别名。
- 授权：用户已授权访问官方文档和接口、使用本轮临时凭据进行真实请求，并在本轮验证后提交和推送
  `main`；不包含设备安装、ADB、Release、部署或启用远端 CI。

完成条件：五个 JS 包均能通过 PackageManager 元数据解析；examples 与 APK assets 逐字节一致；
Academic 分类有唯一 `MenuBook` 图标和固定的明暗 palette；Brave 只有 `BRAVE_SEARCH_API_KEYS`
必填，其余三个 Brave Key 可选；定向契约测试、formal readiness、Debug APK 构建和敏感内容审计通过；
提交后 local/tracking/远端 `main` 三方一致。

## 2. 已验证的仓库事实

1. 根级 `examples/*.ts` 是脚本源码，`npm exec -- tsc -p examples/tsconfig.json` 生成同目录 JS；
   `tools/example_packages/sync_example_packages.py` 按白名单同步到
   `app/src/main/assets/packages/`。提交的 JS 与 assets 必须逐字节相同。
2. `/* METADATA */` 中的 `category` 是脚本分组唯一来源。`PackageCategoryUiPolicy.kt` 对预设分类提供
   唯一语义图标与 light/dark palette，未知分类才使用 hash 动态色；Academic 必须成为正式预设。
3. `JsToolManager` 将元数据工具名转为 `packageName:toolName`，参数的 `string/number/boolean/object/array`
   类型由宿主转换，脚本入口是 CommonJS `exports.<tool>`。
4. `OkHttp.newBuilder()` 是脚本官方 HTTP 入口；所有请求显式设置超时、User-Agent/Accept、状态码检查，
   `catch` 必须 `console.error` 并返回结构化失败对象。

## 3. 官方接口矩阵（2026-08-23 复核）

| 脚本 | 官方资料/端点 | 认证与主要字段 | 计划工具 |
| --- | --- | --- | --- |
| `arxiv_search` | [arXiv API User's Manual](https://info.arxiv.org/help/api/user-manual.html)，`https://export.arxiv.org/api/query` | Atom GET；`search_query`、`id_list`、`start`、`max_results`、`sortBy`、`sortOrder`；无 Key | `search`、`get_paper` |
| `crossref_search` | [Crossref REST API](https://www.crossref.org/documentation/retrieve-metadata/rest-api/)，`https://api.crossref.org` | JSON GET；`/works/{doi}`、`/works`、`/journals/{issn}/works`；polite pool 可用 `mailto`，本脚本不要求 Key | `search_by_doi`、`search_by_keyword`、`search_by_author`、`search_by_title`、`search_by_issn` |
| `pubmed_search` | [NCBI E-utilities 参数说明](https://www.ncbi.nlm.nih.gov/books/NBK25499/) 与 [Usage Guidelines and API Key](https://eutilities.github.io/site/API_Key/usageandkey/)，`https://eutils.ncbi.nlm.nih.gov/entrez/eutils` | `PUBMED_API_KEY` 可选；无 Key 默认最多 3 req/s，Key 默认最多 10 req/s；`PUBMED_EMAIL` 与固定 `tool` 标识调用方；先 `esearch.fcgi` JSON，再 `esummary.fcgi` JSON | `search`、`get_articles` |
| `semantic_scholar_search` | [Semantic Scholar API Overview](https://www.semanticscholar.org/product/api) 与 [Graph API Swagger](https://api.semanticscholar.org/api-docs/graph)，`https://api.semanticscholar.org/graph/v1` | 公共端点允许无认证但共享限流；官方建议每次发送 `x-api-key`，初始 Key 限额 1 RPS；`/paper/search`、`/paper/{paper_id}` | `search`、`get_paper` |
| `openalex_search` | [OpenAlex API](https://help.openalex.org/api/) 与 [Authentication](https://help.openalex.org/api/authentication/)，`https://api.openalex.org` | 2026-08-19 更新的官方说明确认无 Key 可试用，免费 Key 将日预算提高 10 倍；脚本用可选 `api_key` 查询参数；`/works`、`/works/{id}`；`search`、`per_page`、`page`、`filter`、`sort`、`select`；裸 DOI 按官方 `doi:` 标识规范化 | `search`、`get_work` |

实时请求证据（2026-08-23）：arXiv 查询与 ID、Crossref works/DOI/ISSN、OpenAlex works 搜索与四种
单条作品标识、PubMed ESearch/ESummary 均返回 HTTP 200 并解析到目标结构。OpenAlex 的有 Key、无 Key
直连和有 Key 代理请求均为 HTTP 200。Semantic Scholar 使用当前 Key 的 `/paper/search` 在直连和
`127.0.0.1:7897` 代理路径均取得 HTTP 200；无 Key 直连为 HTTP 429，连续验证后的有 Key 请求也可能
被服务端节流为 HTTP 429，符合官方公共共享限流和初始 Key 1 RPS 说明。PubMed 当前 Key 在直连与代理
ESearch 均为 HTTP 200，ESummary 为 HTTP 200；无 Key的直连与代理 ESearch 也均为 HTTP 200，符合
Key 仅提升请求额度的官方契约。以上日志只记录状态和结构，不记录凭据。

## 4. 统一脚本格式

- 文件名、metadata `name`、导出函数名保持稳定 snake_case；每个参数的名字与官方字段一致。
- metadata 始终包含双语 `display_name`、`description`、`category: "Academic"`、`enabledByDefault: true`、
  双语 `tools` 与 `env` 说明；不声明 `author`。
- 每个脚本使用同一结构：`METADATA`、类型定义、固定超时的 OkHttp client、参数校验、query 编码、
  官方响应解析、错误脱敏/日志、`runTool` 完成包装和显式 `exports`。
- 成功返回 `{ success: true, message, data, ... }`，保留官方结果中的核心字段；HTTP/解析/配置错误返回
  `{ success: false, message, statusCode? }`，不吞异常，不切换到非官方接口。
- 凭据只从 `getEnv` 读取。学术 API 的 Key/email 按官方可选能力声明；Brave 保持
  `BRAVE_SEARCH_API_KEYS` 必填，`BRAVE_ANSWERS_API_KEYS`、`BRAVE_SUGGEST_API_KEYS`、
  `BRAVE_SPELLCHECK_API_KEYS` 可选。Brave 只有调用对应 Answers/Suggest/Spellcheck 工具时才要求对应 Key。

## 5. 实施阶段与验证

1. `M0` 研究与方案：完成仓库/运行时/官方 API/当前凭据状态调查，写入本文件和 TODO 索引。
2. `M1` 分类与现有包：加入 Academic 预设、更新视觉测试，修改 Crossref 的 category，并修正 Brave
   环境变量 required 标记。
3. `M2` 五个脚本：新增/重写 TypeScript 源码，编译 JS，同步白名单和 APK assets；为每个导出建立元数据
   与官方端点静态契约。
4. `M3` 自动验证：运行 TypeScript 编译、example package sync/dry-run、Academic/分类/脚本契约测试、
   `git diff --check` 和 formal readiness；真实 API 测试只输出状态和脱敏摘要。
5. `M4` 构建与交付：串行执行 `./gradlew :app:assembleDebug --no-daemon --console=plain`，核验 APK，
   审阅精确允许清单/凭据/构建产物/子模块，提交并推送 `main`，核对三方 ref。

## 6. 本轮验证结果

- `npm exec -- tsc -p examples/tsconfig.json --pretty false` 与 6 个生成 JS 的 `node --check` 通过。
- `sync_example_packages.py --no-hot-reload` 只复制 Brave 与五个 Academic 普通脚本；未重打包、删除或
  热更新设备内容，6 个 examples/assets 文件逐字节一致。
- `AcademicPackageContractTest`、`SearchPackageContractTest`、`PackageCategoryUiPolicyTest` 合计
  15/15 通过；分类清单、metadata/exports/env、固定视觉与 assets 契约均受测试覆盖。
- `check_formal_readiness.py --repository . --require-main` 与 `git diff --check` 通过。
- 五源官方 HTTP 搜索/详情和生成脚本适配运行通过；Semantic Scholar 的成功与 429 证据按第 3 节边界
  解释，不把外部额度状态写成永久可用保证。
- `:app:assembleDebug --no-daemon --console=plain` 通过 235 个任务；APK 为 `com.kiyori 0.1.0 (45)`，
  V2 单签名、16 KB ZIP 对齐通过，6 个相关脚本在 APK 中各一份且与源码 assets 哈希一致。

## 7. 验收边界

| 证据 | 本轮目标 | 限制 |
| --- | --- | --- |
| 静态 metadata/exports/端点合同 | 五包可发现、格式一致、分类正确 | 不等同于模型现场调用 |
| TypeScript 编译与同步 | examples/assets 无漂移 | 不等同于设备安装 |
| 官方 HTTP | 五源搜索和详情按脚本实际参数解析最小成功结构；三项凭据均取得成功响应 | 凭据额度、出口和外部限流随时间变化；Semantic Scholar 连续调用可能返回 429 |
| Debug APK | 构建成功且 assets 打包 | 不等同于真机视觉/交互验收 |
| Git 交付 | 只提交本任务文件，`main` 三方一致 | 不启用远端 Actions、不做 Release |
