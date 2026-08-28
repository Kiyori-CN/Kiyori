# Academic 学术脚本分组与五源 API

## 0. 2026-08-28 五源深度优化

### 0.1 当前状态与任务契约

- 当前状态：`LOCAL IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`。
- 目标：基于统一主题搜索和同篇论文详情交叉核验，主动审查并修复 arXiv、Crossref、PubMed、
  Semantic Scholar、OpenAlex 五个生成脚本在相关性、参数、解析、分页、输出体积、错误诊断和
  数据口径上的真实缺陷。
- 范围：五个 `examples/*.ts` 唯一源码、对应生成 JS 和 APK assets、Academic 合同测试、现有
  ToolPkg CI 步骤、本文与总 TODO 索引；语义合同变化时同步检查根 `CONTEXT.md` 和 README。
- 非目标：不新增代理、备用域名、自动重试、跨库自动匹配或引用数换算；不把 OpenAlex
  污染样本写成通用自动判假规则；不安装或操作设备，不执行 Release 或启用远端 Actions。
- 发布与兼容：Kiyori 仍是未公开发行的开发版，本轮是在五个既有包 ID 和工具名上的正常迭代；保留
  `arxiv_search`、`crossref_search`、`pubmed_search`、`semantic_scholar_search`、
  `openalex_search` 及其现有导出名，不保留被修复实现的并行旧路径。
- 授权：用户明确授权使用本轮临时提供的官方 API 凭据做低频真实测试，并授权验证完成后提交和推送
  `origin/main`。凭据只进入测试进程环境，不写入源码、文档、日记、日志或构建产物；Semantic
  Scholar 按官方初始 `1 RPS` 限制串行执行必要的搜索与详情调用。

完成条件：生成 JS 的模拟运行时测试覆盖请求 URL、参数组合、200 业务错误、输出塑形和错误日志；
五包 TypeScript 编译与 examples/assets 同步无漂移；低频官方搜索/详情矩阵取得可解释结果；相关 JVM、
Node、formal readiness、Markdown、fresh clone 和 Debug APK 检查通过；候选树敏感内容与产物审计
通过；提交后 local/tracking/远端 `main` 一致。

### 0.2 已验证事实与根因

1. 当前 `AcademicPackageContractTest` 只检查 metadata、导出名、官方字符串和 examples/assets 字节
   一致性，没有执行生成 JS；因此 URL 构造、参数组合、二段请求、响应解析和输出体积回归均可漏过。
2. 五包的页码/数量函数直接对输入执行 `Math.floor` 与夹取；`NaN` 或无穷值会生成非法官方参数，
   而不是在请求前形成明确错误。
3. OpenAlex 官方搜索默认按 `relevance_score` 降序，显式 `sort` 会改写这一顺序。当前脚本允许
   `search` 与无约束 `cited_by_count:desc` 同用，统一主题实测会返回完全不相关的全库高引记录。
   当前 `get_work` 未传 `select` 时还会返回完整作品对象，与搜索路径的紧凑默认不一致。
4. OpenAlex 官方说明 works 的 `search` 搜索 title、abstract 和 fulltext；当前 Agent 参数说明误写为
   同时搜索作者、机构和概念。OpenAlex 还明确把来源文本视为未清洗的外部数据，因此异常 DOI、作者、
   机构和引用数必须保留来源级核验提示，不能凭单条启发式规则自动判假。
5. Crossref 五条工具直接返回原始 work。已知 DOI 响应会携带 JATS abstract 和完整 `reference` 数组，
   单条详情即可挤占工具输出；主题检索的 `query.bibliographic` 适合出版元数据发现，但不能承诺与专门
   学术图谱相同的主题相关性。
6. arXiv 官方 Atom 错误也可能使用 HTTP 200 和单个 error entry 返回；当前解析器会把它当成功论文。
   官方示例允许 `<entry ...>` 带 namespace 属性，而当前正则只匹配精确 `<entry>`。官方还明确 DOI
   仅在作者提供时出现，指定 `id_list=<id>vN` 才保证取特定版本；空 DOI 不是跨库缺失修复信号。
7. PubMed ESearch 可在 HTTP 200 的 `esearchresult` 内返回 `ERROR`/`errorlist`；当前实现会把该形状
   转为“零结果成功”，并可能产生 `NaN` total。ESearch 后的 ESummary 二段请求和
   `querytranslation` 则是现有正确 owner，应保留。
8. Semantic Scholar 当前搜索和详情共用包含 abstract 的默认字段，导致多结果搜索体积不必要地放大；
   任意 `fields` 还可请求大型嵌套集合。官方 API Key 初始限制为 `1 RPS`，HTTP 429 必须原样可观察，
   不增加重试或换源。
9. Semantic Scholar、Crossref 和 OpenAlex 的年份、引用数来自不同记录合并和统计口径；脚本只能声明
   当前来源，不能把跨库数值表示为可直接比较的统一指标。

官方证据（2026-08-28 复核）：

- [OpenAlex Search](https://help.openalex.org/api/searching/)、
  [Sort](https://help.openalex.org/api/sorting/) 和
  [Select Fields](https://help.openalex.org/api/selecting-fields/)；
- [Crossref REST API](https://www.crossref.org/documentation/retrieve-metadata/rest-api/)；
- [arXiv API User's Manual](https://info.arxiv.org/help/api/user-manual.html)；
- [Semantic Scholar API Overview](https://www.semanticscholar.org/product/api)；
- [NCBI E-utilities Usage Guidelines](https://eutilities.github.io/site/API_Key/usageandkey/)。

### 0.3 采用方案与影响文件

| 领域 | 采用方案 | 主要文件 |
| --- | --- | --- |
| 可执行合同 | 新增 Node `vm` 模拟宿主，直接执行提交的 CommonJS JS，记录请求并注入官方响应；接入现有 ToolPkg CI 步骤 | `tools/example_packages/academic_packages.test.mjs`、`package.json`、`.github/workflows/{pr-check,android-build}.yml` |
| arXiv | 把普通查询拆成明确的 `all:` term AND 表达式；兼容带属性 entry；识别 200 error feed；公开 arXiv ID、版本、主分类和作者提供 DOI 的语义 | `examples/arxiv_search.ts` |
| Crossref | 默认把 raw work 映射为紧凑出版记录、清洗并限制 abstract；只在显式请求时返回受数量限制的规范化 references；搜索结果不返回 references | `examples/crossref_search.ts` |
| PubMed | 识别 ESearch 200 业务错误；严格验证有限整数；PMID 稳定去重；保留 ESearch/ESummary 与 query translation | `examples/pubmed_search.ts` |
| Semantic Scholar | 搜索默认省略 abstract，详情默认包含；自定义 fields 只接受轻量白名单并稳定去重；返回来源内指标提示 | `examples/semantic_scholar_search.ts` |
| OpenAlex | 显式 `sort` 必须同时有非空 `filter`；搜索与详情都使用紧凑 `select`；默认包含 relevance score，修正文案并返回来源/质量提示 | `examples/openalex_search.ts` |
| 生成与交付 | `tsc` 生成 JS，白名单同步到 APK assets；扩展 JVM 静态合同；更新语义文档和最终证据 | 五个生成 JS、五个 assets、`AcademicPackageContractTest.kt`、本文、`CONTEXT.md` |

不抽取五包共享运行时代码：普通脚本必须保持单文件可安装，跨包复制的少量参数/错误函数由可执行合同
统一约束；创建共享脚本模块会引入新的打包和加载 owner，收益不足以覆盖协议复杂度。

### 0.4 阶段、风险与验证

1. `M0 - DONE`：读取规则、Git/历史/源码/测试/同步链，运行 formal readiness，复核五家官方文档，
   冻结本任务契约。
2. `M1 - DONE`：增加失败用例和模拟宿主，证明基线实现在 OpenAlex sort、arXiv error feed、
   Crossref 大响应、PubMed 200 error、字段体积与非有限分页参数上的缺口。
3. `M2 - DONE`：逐包实现最小闭环修复，编译 JS，同步五个 APK assets，扩展 JVM 静态合同。
4. `M3 - DONE`：运行 Node 模拟矩阵、TypeScript、`node --check`、同步 dry-run、Academic JVM、
   `git diff --check`、formal readiness 和 Markdown 链接检查。
5. `M4 - DONE`：使用临时进程环境执行五源低频官方搜索/详情；Semantic Scholar 串行且请求间隔
   不低于官方初始限制，不记录 key/email/原始大响应。PubMed、arXiv、OpenAlex、Crossref 的第二阶段
   8 个调用均为 HTTP 200；第一阶段的 PubMed 搜索/详情与 Semantic Scholar 搜索/详情均通过夹具的
   成功断言，随后未重复请求 Semantic Scholar。
6. `M5 - DONE`：串行 `:app:assembleDebug --no-daemon --console=plain` 通过，核验 APK 身份、Debug
   V2 单签名、16 KiB 对齐及五个 assets；fresh-clone、候选树/敏感内容/子模块/大文件审计通过，进入
   精确提交推送与远端 ref 对账。

主要失败模式与控制：

- 过度压缩丢失出版字段：由 Crossref mock 详情和真实 DOI 对照固定核心卷期页、许可、标识和 reference
  计数；完整 references 必须显式请求。
- 相关性策略变成隐藏行为：OpenAlex 无 filter 的显式 sort 返回参数错误，不静默删除 sort；arXiv
  查询构造在返回对象中公开实际 `search_query`。
- 来源污染被误判：OpenAlex 只返回可审计提示，不依据引用阈值、作者字符串或 DOI 外观删除记录。
- 外部限流：不重试、不换源；记录 HTTP 状态和结构，429 或额度变化作为外部证据而非本地失败掩盖。
- 回滚点：本轮首个项目写入前的干净 `main@05ffa94de26529cb744cb1bd8338558419037725`；禁止用
  destructive Git 命令回滚，实际撤回只通过后续精确提交完成。

### 0.5 当前验证证据（2026-08-29）

- Node `npm run test:examples:academic`：`8/8` 通过；覆盖请求 URL、arXiv 停用词与 HTTP 200 error
  feed、Crossref compact/reference limit、PubMed 200 业务错误与 ID 去重、Semantic Scholar 字段白名单、
  OpenAlex sort/filter/select 和五包非有限分页。
- TypeScript：`npm run build:examples:academic` 通过；五个生成 JS `node --check` 通过；同步脚本
  `--no-hot-reload` 报告 `whitelist=49, resolved=49, missing=0`，五个 Academic examples/assets
  逐字节同步。
- JVM：`:app:testDebugUnitTest --tests com.ai.assistance.operit.core.tools.packTool.AcademicPackageContractTest`
  通过；静态合同锁定 metadata、官方端点、紧凑投影、来源指标提示和新错误路径。
- JVM：`:app:testDebugUnitTest --no-daemon --console=plain` 通过，未报告失败、错误或跳过测试。
- 正式准备：`check_formal_readiness.py --repository . --require-main` 通过；同步脚本报告
  `whitelist=49, resolved=49, missing=0`，五个 Academic examples/assets 已逐字节一致。
- 真实官方第二阶段：PubMed PMID `38866050` / DOI `10.1038/s41586-024-07618-3` 的搜索与详情、
  arXiv `chain of thought` 搜索（5 条）与 `2201.11903` v6 详情、OpenAlex 搜索与同 DOI 详情、
  Crossref 关键词与同 DOI 详情均成功；OpenAlex 无 sort 默认返回 CoT 结果，Crossref 详情未返回完整
  references。响应正文仅在进程内读入并摘要，未保存凭据或原始 payload。
- Debug APK：`:app:assembleDebug --no-daemon --console=plain` 通过 235 个任务；APK 为
  `com.kiyori 0.1.0 (45)`，`503676753` 字节，SHA-256
  `1C8DFC15B0576DD0E3C401D596FB1A9502600227D0BCE4B9CF98D2DC46ADD598`；`apksigner verify --verbose`
  报告 Debug V2 单 signer，`zipalign -c -P 16 -v 4` 通过；APK 内五个 Academic assets 与 examples
  逐字节一致。
- Fresh clone：`check_fresh_clone.py --repository .` 通过（基线 `05ffa94de26529cb744cb1bd8338558419037725`）。
- 待完成：精确提交推送与 local/tracking/远端 `main` ref 对账；目标设备的扩展抽屉显示和真实 AI
  调用仍不属于本轮自动证据，保持 `verification_pending`。

## 1. 状态与任务契约

- 当前状态：`IMPLEMENTATION VERIFIED; DEVICE RETEST PENDING`
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
- 2026-08-23 真机 AI 审计中，`pubmed_search:search` 的复杂检索、最简检索和
  `pubmed_search:get_articles` 均返回 HTTP 400；同一设备通过通用 HTTP 工具直接请求无 Key 的
  ESearch/ESummary 均为 HTTP 200。结合最初 Key 被 NCBI 判定无效、更新 Key 在直连/代理/生成脚本
  适配器中均为 HTTP 200 的证据，根因范围收敛到设备当前的 `PUBMED_API_KEY` 配置，最可能是仍保存
  失效值。脚本现会
  解析 NCBI 的结构化 `API key invalid` 响应，并明确要求更新或清空可选 Key；不会静默忽略凭据或
  自动重复请求。审计按安全设计不导出环境变量值，新 APK 仍需在设备更新配置后复测。
- `:app:assembleDebug --no-daemon --console=plain` 通过 235 个任务；APK 为 `com.kiyori 0.1.0 (45)`，
  V2 单签名、16 KB ZIP 对齐通过，6 个相关脚本在 APK 中各一份且与源码 assets 哈希一致。

## 7. 验收边界

| 证据 | 本轮目标 | 限制 |
| --- | --- | --- |
| 静态 metadata/exports/端点合同 | 五包可发现、格式一致、分类正确 | 不等同于模型现场调用 |
| TypeScript 编译与同步 | examples/assets 无漂移 | 不等同于设备安装 |
| 官方 HTTP | 五源搜索和详情按脚本实际参数解析最小成功结构；三项凭据均取得成功响应 | 凭据额度、出口和外部限流随时间变化；Semantic Scholar 连续调用可能返回 429 |
| Debug APK | 构建成功且 assets 打包 | 不等同于真机视觉/交互验收 |
| 真机 PubMed | 失效 Key 的 HTTP 400 可定位为明确配置错误；更新或清空 Key 后搜索与详情均成功 | 新 APK 与设备配置组合仍待用户复测 |
| Git 交付 | 只提交本任务文件，`main` 三方一致 | 不启用远端 Actions、不做 Release |
