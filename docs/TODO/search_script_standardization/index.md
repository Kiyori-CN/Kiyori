# Search 内置脚本规范化与四家官方 API 完善

## 1. 状态与任务契约

- 当前状态：`M0-M6 MAIN DELIVERED / DEVICE VERIFICATION PENDING`
- 基线：`main@e5f5f01cd25205fa37aa2cb384617d59bf912d7f`
- 目标：规范全部 `Search` 分组内置脚本，并把 Tavily、SerpApi、Brave、智谱重写为可供后续
  脚本开发复用的双语示例。
- 非目标：不调整 Search 之外的脚本业务，不操作设备，不引入代理端点、备用域名、默认 Key、
  运行时降级路径或 AI 可传入的凭据参数。
- 发布假设：Kiyori 当前未正式发布；本轮直接清理旧 ID、旧文件名和旧示例，不保留别名或双 ID。
- 授权：允许访问四家官方文档、使用用户本轮提供的临时凭据调用官方 API、最终提交并推送
  `origin/main`；不授权 Release、部署、安装、ADB 或模拟器操作。

完成必须同时满足：源码/生成 JS/assets 同步，专项契约测试与真实官方 API 测试通过，Debug APK
构建及产物审计通过，敏感内容和暂存范围审计通过，提交推送后本地 HEAD、tracking ref 与远端
`refs/heads/main` 一致。

## 2. 已验证事实

### 2.1 AI 如何发现并调用 JS 工具

1. `PackageManager.toPromptCategory()` 与 `generatePackageSystemPrompt()` 把包名、包说明和简要工具
   目录提供给模型，供模型决定是否调用 `use_package(package_name)`。
2. 包激活后，`PackageToolPromptCategory.toString()` 注入真实工具名、工具说明、参数名、类型、
   必填性和参数说明；结构化调用经 `package_proxy` 转发。
3. `JsToolManager.convertToolParameters()` 按元数据把字符串转换成 `number`、`boolean`、`array`
   或 `object`，再由 QuickJS 调用同名 `exports.<tool>`。
4. 因此包说明只回答“何时选这个包”；工具说明回答“何时选这个官方能力”；参数说明精确表达
   官方字段、枚举、依赖关系与互斥关系。说明不是产品宣传或人工教程。

### 2.2 源码与生成物所有权

- 根级 `examples/*.ts` 是可维护源码；`examples/tsconfig.json` 编译成同目录 `.js`。
- `tools/example_packages/sync_example_packages.py` 在同步前运行 TypeScript 编译，再按
  `tools/example_packages/packages_whitelist.txt` 把 `.js` 复制到
  `app/src/main/assets/packages/`。
- 当前八个 Search 示例 JS 与 assets 逐字节相同。实现只修改 `.ts`，再用项目同步入口生成和复制，
  不手工维护三份漂移实现。

### 2.3 多 Key 状态语义

- 普通内置脚本由 `JsToolManager` 的 4 个 `JsEngine` 池执行，并不固定绑定一个引擎。
- 只在模块作用域保存 `let cursor` 会形成至多四份局部游标，无法提供进程内严格轮询。
- 目标实现增加一个进程级、线程安全、按命名空间隔离的原子轮询所有者；脚本只传产品命名空间
  和有效 Key 数量，不传 Key 内容。应用进程重启后从零开始是明确语义，不把游标写入用户数据。

## 3. Search 包 ID 与显示名

| 源文件 | 最终 ID | 中文名 | 操作 |
| --- | --- | --- | --- |
| `tavily_search.ts/js` | `tavily_search` | `Tavily 搜索` | 由 `tavily` 改名并重写 |
| `serpapi_search.ts/js` | `serpapi_search` | `SerpApi 搜索` | 新增 |
| `brave_search.ts/js` | `brave_search` | `Brave搜索` | 新增 |
| `zhipu_search.ts/js` | `zhipu_search` | `智谱 搜索` | 保留 ID、改名并重写 |
| `google_search.ts/js` | `google_search` | `Google 搜索` | 统一元数据格式 |
| `duckduckgo_search.ts/js` | `duckduckgo_search` | `DuckDuckGo 搜索` | 由 `duckduckgo` 改名 |
| `crossref_search.ts/js` | `crossref_search` | `Crossref 搜索` | 由 `crossref` 改名 |
| `various_search.ts/js` | `various_search` | `多平台搜索` | 保持 ID，统一格式 |

所有条目统一使用 `category: "Search"`，删除 `author`，中英文 `display_name`、`description`、
工具说明、参数说明和环境变量说明均使用相同对象格式。同步更新 whitelist；旧文件从 examples、
assets 和生产清单中消失。

## 4. 官方接口矩阵

资料冻结日期为 2026-08-22。实现阶段仍需在真实测试前重新读取官方结构；接口变化必须先更新本
文档和契约测试，不能静默猜测。

### 4.1 Tavily

官方索引：<https://docs.tavily.com/llms.txt>
官方 OpenAPI：<https://docs.tavily.com/documentation/api-reference/openapi.json>

| 导出工具 | 官方接口 | 覆盖重点 |
| --- | --- | --- |
| `search` | `POST /search` | depth 四档、chunks、topic 三类、时间/日期、answer/raw、图片/说明/favicon、域名、country、auto、exact、usage、safe search |
| `extract` | `POST /extract` | 单个或多个 URL、query、chunks、depth、图片/favicon、format、timeout、usage |
| `crawl` | `POST /crawl` | instructions、depth/breadth/limit、路径/域名选择和排除、external、提取字段、timeout、usage |
| `map` | `POST /map` | instructions、depth/breadth/limit、路径/域名选择和排除、external、timeout、usage |
| `create_research` | `POST /research` | input、mini/pro/auto、stream、output schema、citation format、域名、长度、files |
| `get_research` | `GET /research/{request_id}` | 用 create_research 返回的 `keyIndex` 固定同一 Key，查询异步研究状态与结果 |
| `usage` | `GET /usage` | 可选 `X-Project-ID` |
| `logs` | `POST /logs` | limit、日期、endpoints、project、按 Key 过滤 |
| `organization_usage` | `POST /org-usage` | organization、日期、project、depth |
| `test_keys` | 认证检查 | 对每个 Key 并行调用官方 Usage，逐项报告 |

Research 的 `stream=true` 响应按 SSE 事件解析并返回事件数组/最终状态，不能把事件流当普通 JSON。

### 4.2 SerpApi

官方资料：<https://serpapi.com/search-api>、<https://serpapi.com/search-archive-api>、
<https://serpapi.com/locations-api>、<https://serpapi.com/account-api>、
<https://serpapi.com/search-index-api>

| 导出工具 | 官方接口 | 设计 |
| --- | --- | --- |
| `search` | `/search.json`、`/search`、`/search.md` | 必填 `engine`，`params` 对象原样 URL 编码；禁止 `params.api_key` 覆盖环境凭据 |
| `get_search` | `/searches/{search_id}.json`、`/searches/{search_id}` | 用 search 返回的 `keyIndex` 固定同一 Key；支持归档 JSON、HTML 与 pixel-position JSON |
| `locations` | `/locations.json` | `q` 与 `limit` |
| `account` | `/account.json` | 当前 Key 的账户、额度和用量 |
| `test_keys` | `/account.json` | 免费 Account API 并行检查每个 Key |

透传设计覆盖所有当前和未来官方引擎字段，包括 `async`、`no_cache`、`zero_trace`、`output` 与
`json_restrictor`，不硬编码少数 Google 子引擎。脚本负责剔除 `api_key`、`serp_api_key` 等凭据
字段并稳定编码标量和数组；对象值使用 JSON 字符串，不擅自改变官方参数含义。

### 4.3 Brave Search API

官方文档：<https://api-dashboard.search.brave.com/app/documentation>
官方 API 参考入口：<https://api-dashboard.search.brave.com/api-reference/web/search/get>

| 导出工具 | 官方能力/接口 |
| --- | --- |
| `web_search` | GET/POST `/res/v1/web/search` |
| `llm_context` | GET/POST `/res/v1/llm/context` |
| `news_search` | GET/POST `/res/v1/news/search` |
| `video_search` | GET/POST `/res/v1/videos/search` |
| `image_search` | GET `/res/v1/images/search` |
| `local_pois` | GET `/res/v1/local/pois` |
| `place_search` | GET `/res/v1/local/place_search` |
| `poi_descriptions` | GET `/res/v1/local/descriptions` |
| `rich_search` | GET `/res/v1/web/rich` |
| `summarizer` | GET `/res/v1/summarizer/search`、`summary`、`summary_streaming`、`title`、`enrichments`、`followups`、`entity_info` |
| `answers` | POST `/res/v1/chat/completions` |
| `autosuggest` | GET `/res/v1/suggest/search` |
| `spellcheck` | GET `/res/v1/spellcheck/search` |
| `test_keys` | 按四个产品类别并行调用各自最小官方请求 |

每个搜索型工具接收官方 `params` 对象并转发全部正式查询字段；支持 POST 的端点增加 `method`
选择，GET 使用 query string，POST 使用 JSON body。`summarizer` 用 `resource` 枚举七个官方
Summarizer 端点，透传初始 Web Search 返回的不透明 `key`，其中 `summary_streaming` 按 SSE
解析。该产品虽已由官方标记为 deprecated，仍按“尽可能实现所有功能”的任务范围保留；不把它
作为 Answers 失败后的运行时降级路径。Answers 接收 `messages`、model、stream、web search
options、地区/语言/safesearch、entities、citations、research 及 research 限额；流式响应按 SSE
解析。

Brave 凭据严格分四个环境变量，均接受英文逗号分隔的多个 Key：

- `BRAVE_SEARCH_API_KEYS`：Web、LLM Context、News、Videos、Images、Local、Place、POI、Rich。
- `BRAVE_ANSWERS_API_KEYS`：Answers。
- `BRAVE_SUGGEST_API_KEYS`：Autosuggest。
- `BRAVE_SPELLCHECK_API_KEYS`：Spellcheck。

### 4.4 智谱 Web Search

官方索引：<https://docs.bigmodel.cn/llms.txt>
官方接口：<https://docs.bigmodel.cn/api-reference/工具-api/网络搜索.md>

| 导出工具 | 官方接口 | 全部字段 |
| --- | --- | --- |
| `search` | `POST https://open.bigmodel.cn/api/paas/v4/web_search` | `search_query`、`search_engine`、`search_intent`、`count`、`search_domain_filter`、`search_recency_filter`、`content_size`、`request_id`、`user_id` |
| `test_keys` | 同一官方接口 | 每个 Key 使用最小 `search_std` 请求并行检查 |

支持的引擎为 `search_std`、`search_pro`、`search_pro_sogou`、`search_pro_quark`。本脚本只负责
官方独立结构化 Web Search API；不把 Chat Completions、Search Agent 或本地拼装的 news、extract、
multi-search 冒充成该接口能力。

## 5. 统一多 Key 与 HTTP 契约

### 5.1 Key 解析

1. 只从对应环境变量读取，不接受 AI 参数传入或覆盖。
2. 按英文逗号切分，trim，删除空项，按首次出现顺序稳定去重。
3. 空列表立即返回可操作的配置错误；日志和错误不得输出原始 Key。
4. 对外只显示 `keyIndex`（从 1 开始）和掩码。掩码保留极少前后字符，短 Key 只显示星号。

### 5.2 原子轮询与故障切换

1. 每个产品环境变量对应独立 namespace；进程级原子计数器返回本次起始索引。
2. 请求先用起始 Key；只有明确属于凭据/额度/限流的响应才按环形顺序尝试剩余 Key，且每个 Key
   最多一次。
3. 典型 Key 级信号是 `401`、`403`、`429`，以及官方明确的无效凭据、额度或速率错误码；
   网络失败、超时、业务参数 `400/404/422`、服务端未知错误和非幂等语义不盲目切换。
4. 成功结果包含实际使用的 Key 序号但不包含凭据；所有 Key 均不可用时返回逐次脱敏摘要。
5. 轮询计数器不落盘，应用进程重启归零；Key 顺序变化立即按新有效列表工作。
6. 上游响应产生的 `request_id`、`search_id`、POI ID、`callback_key` 或 Summarizer key 可能与
   凭据绑定；后续工具必须接收上一步返回的 `keyIndex`，固定同一 Key 且不执行跨 Key 重试。

### 5.3 并行连通性检查

- `test_keys` 对全部有效 Key 构造独立 Promise，并使用 `Promise.all` 同时等待。
- 单个 Promise 自己捕获错误，保证一项失败不取消其他项。
- 每项包含 `category`、`keyIndex`、`maskedKey`、`success`、`statusCode`、`durationMs`、
  `message`；Brave 顶层按四类凭据分组并给出总计。
- 检查只调用官方端点，不使用代理、备用域名或未经官方定义的健康检查。

### 5.4 HTTP 与响应

- 统一使用宿主 `OkHttp`，明确 connect/read/write timeout，开启重定向，关闭 OkHttp 自身的隐式
  连接重试，由脚本状态机决定是否换 Key。
- JSON、文本、HTML、Markdown、pixel JSON 与 SSE 按 Content-Type/工具契约解析；保留官方响应
  的完整业务字段，不重排或压缩成会丢信息的自定义摘要。
- 非 2xx 返回状态、官方错误主体和 request ID；错误消息截断到合理长度并清理凭据。
- 每个导出工具 `catch` 后必须 `console.error` 记录脱敏上下文，再返回结构化失败。

## 6. 元数据与面向 AI 的说明规范

- 包说明控制在一至两句：供应商、适用场景、可选择的官方能力。
- 工具说明第一句写“何时调用”，第二句只写关键区别或依赖。例如研究创建与状态查询分开，
  Search 与 LLM Context 分开，POI/Rich 明确要求上一步返回的临时 ID/callback key。
- 参数名尽量与官方字段完全一致；通用透传使用 `params: object`，说明中列出关键字段和禁止项。
- 不把 Key、curl 教程、价格、宣传语、长示例或面向 UI 用户的操作说明注入模型上下文。
- metadata 工具列表和 `exports` 必须一一对应；测试会同时解析两者并验证无隐藏导出或空实现。

## 7. 实施阶段、风险与回滚点

### M2：统一 Search 清单与共享轮询所有者

- 状态：`DONE`。
- 已新增进程级原子轮询类及并发单元测试。
- 已重命名 Tavily、DuckDuckGo、Crossref 源文件/生成物/assets，更新 whitelist。
- 已统一八个包的 ID、显示名、分类、作者字段和双语格式。
- 回滚点：只涉及命名、清单和轮询基础设施；未进入供应商 HTTP 实现。

### M3：四个核心脚本

- 状态：`DONE`。
- 已按 Tavily、SerpApi、Brave、智谱顺序实现 TypeScript。
- 已形成每个文件内部的 Key 解析、掩码、请求、错误分类、SSE 与结果包装辅助函数；脚本保持
  自包含，不新增相互依赖或第二份配置所有者。
- 已编译并同步到 examples JS 与 assets，八个 Search 包逐字节一致。
- 回滚点：每个供应商文件独立，可定位单一实现差异。

### M4：自动化与真实官方验证

- 状态：`DONE`。
- 已新增 Search 资产合同测试：ID 后缀、无作者、显示名、环境变量、工具/exports 对应、端点与关键
  字段、轮询调用、并行检查、examples/assets 哈希。
- TypeScript 编译、example package sync/dry-run 和 7 项专项 JVM 测试通过。
- 临时 Key 只进入测试进程环境；最终真实矩阵 `47/47 accepted`，逐 Key 并行连通性为 Tavily
  `14/14`、SerpApi `4/4`、Brave 四类 `4/4`、智谱 `1/1`。
- Tavily Search/Extract/Map/Crawl/Usage/Research 创建、同 Key 查询和 Research SSE，SerpApi
  Search/Locations/Account/三类归档，Brave 正式 Search/LLM/News/Video/Image/Local/Rich/Answers/
  Autosuggest/Spellcheck，以及智谱 Web Search 均取得真实 `200`。
- Tavily Logs 因当前套餐权限返回真实 `403`，Organization Usage 对测试组织返回真实 `404`；
  Brave 七条已弃用 Summarizer 端点在当前订阅下返回真实 `400/422`。这些结果只证明路径、认证与
  参数已送达官方服务，不声明受限业务成功，也不触发跨 Key 或替代服务路径。
- 真实测试只输出脱敏状态摘要，不保存 Key 或响应正文。

### M5：仓库与 APK 验证

- 状态：`DONE`。
- 定向单元测试、formal readiness、本地文档目标、凭据扫描和 `git diff --check` 已通过。
- `./gradlew :app:assembleDebug --no-daemon --console=plain` 在 3 分 28 秒内完成 232 个任务，零失败。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间 `2026-08-22 15:25:52 +08:00`，
  `472737951` bytes，SHA-256
  `BDF7DFB3935FB305A804FC4F4A29B76539E258AA7D22F79C1B611FA858F85555`。
- 包/版本/SDK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer 与 16 KiB ZIP 对齐通过。
- APK 共 5506 个零重复条目，只包含 `arm64-v8a`，51 个 `.so` 零重复 basename；加上
  `assets/operit_shell_exec` 共 52 个 ELF64/AArch64，153 个 `PT_LOAD` 为 `0x4000 × 151` 与
  `0x10000 × 2`，没有低于 16 KiB 的 runtime ELF。
- 八个最终 Search assets 均存在且与生产 assets 逐字节一致；`tavily.js`、`duckduckgo.js`、
  `crossref.js` 三个旧 ID 资产不存在。

### M6：提交与推送

- 状态：`DONE`。
- 全树与暂存区检查临时 Key、凭据模式、构建产物、缓存、嵌套 `.git`、子模块和异常大文件。
- 只提交本任务文件，不处理可选未初始化子模块。
- 推送 `main` 前 `fetch --prune` 并确认可 fast-forward；若远端变化，重新审计与验证，不强推。
- 推送后核对 local HEAD、`origin/main` 和 `git ls-remote origin refs/heads/main`。
- 实现提交 `bb5715028768b43e7f42f1aa1f88d6a9ed4410b8` 已推送；该提交推送后 local HEAD、
  `origin/main` 与远端 `refs/heads/main` 三方一致。

## 8. 验收矩阵

| 验收项 | 自动证据 | 真实证据 |
| --- | --- | --- |
| 全部 Search ID 以 `_search` 结尾且无作者 | metadata 合同测试、APK assets 检查 | 抽屉显示仍需用户/设备验收 |
| 中英文说明能正确发现和激活工具 | prompt category/metadata 合同测试 | 代表性模型调用为设备后续验收 |
| 四家官方字段与端点完整 | 官方快照矩阵、静态合同、TS 编译 | 代表性官方请求 |
| 多 Key 真轮询 | 原子计数并发测试、脚本调用合同 | 连续请求记录脱敏 Key 序号 |
| 并行 Key 检查 | Promise 结构合同、失败隔离测试 | 用户临时 Key 的逐项结果 |
| 不泄露凭据 | 源码/文档/暂存/全树扫描 | 测试日志仅含掩码 |
| 可安装产物 | Debug 构建与 APK 静态审计 | 本轮未授权设备安装，保留待验收 |

## 9. 未决项处理原则

- 官方文档与实际响应冲突时，以真实官方端点响应和 request ID 为证据，先更新矩阵再改实现。
- 某个临时 Key 无套餐权限不等于脚本实现失败；需区分认证通过、产品未订阅、额度耗尽与参数错误。
- 不为通过测试放宽字段、吞掉错误、减少官方能力或添加任何备用服务。
