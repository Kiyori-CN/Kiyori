---
status: verification_pending
owner: PackageManager + examples
---

# 内置脚本与插件规范化

## 2026-09-12 12306 与扩展执行边界深化

基线 `main@90b16fce9`，工作区干净。本轮优先解决 12306 全部八个查询入口的实际故障，
再修复同类插件参数、异步完成和业务结果问题。允许重新设计内部实现，保持包名、导出名、
既有参数及文本结果兼容；补充机器可读详情与稳定错误分类，版本仍为 `1.0.0`。

1. 用官网公开只读页面/接口及合成夹具复现初始化耦合、动态查询路径、日期、座席和分页问题。
2. 分离车站与直达/中转初始化，明确服务器拒绝与登录要求；重做查询校验、日期和结果投影。
3. 覆盖全部入口和并发、错误、空结果、跨日/多页边界；检查相关插件的终态和参数合同。
4. 同步生产资产，定向自动验证与串行 Debug APK；精确清单审计后提交推送 main。

公开接口只用于查询，不登录、订票、发消息或操作设备，不绕过登录/访问控制。
网络、模拟宿主和 Android 现场证据分开记录；回滚点为本轮独立提交的父提交。

### 已实现与本地证据

- 删除旧成都东假编码，按官网 `@` 记录解析车站；车站查询不再依赖中转初始化。
  官网直达初始化当前声明 `leftTicket/queryG`，实现读取声明而非猜测或硬编码查询后缀。
  保留原八工具及文本输出，增加结构化详情、可区分的失败阶段与代码。
- 余票不依赖票价存在；上车日期、跨日排序、两程时间与总历时校验保持一致。
  中转先筛选再限数，去重、防重复游标，并明确 10 页/500 方案的扫描边界。
- UI 子代理尊重宿主业务失败，保留并行部分结果、禁止共享虚拟屏会话，并只有一次终态。
  上下文限制器共用严格整数解析，等待写盘后完成，写入错误不暴露原始配置内容。
- 共享 HTTP 封装补传连接恢复重试配置；multipart 使用宿主真实字段并继承超时、重定向、
  重试和拦截器策略；响应头不再截断 URL/时间中的冒号。
- `node --test --test-reporter=spec tools/example_packages/*.test.mjs`：265 项，250 通过、
  15 环境条件跳过、0 失败；新增铁路 23 项、插件执行 6 项、HTTP 桥接 5 项，共 34 项全部通过。
  Windows 监听测试首次出现 `ECONNREFUSED`，随后两次单项及后续全量回归未复现；
  首次失败的根因尚未确认，未通过删除测试、延长超时或吞错处理。
- 根 examples 与 context_limiter_c TypeScript `--noEmit` 均通过；正式同步
  `--mode normal --no-hot-reload`：52/52，首次复制 2 项、重打包 1 项；最后补充非数字输入
  拒绝后再执行增量同步，重打包 1 项，缺项/删除均为 0，相关 6 项插件测试再次通过。
- 2026-09-12 12:21（Asia/Shanghai）公开只读验证：上海日期、四个车站入口、
  2026-09-13 北京南至上海虹桥直达查询成功（显式取 3 条），首条车次返回 13 个经停站。
  官网车站资源、初始化、`queryG` 与经停接口 HTTP 200；中转初始化 HTTP 302 登录页，
  正确返回 `AUTH_REQUIRED`。该证据来自同一 JS facade + curl 适配器，未使用 Android 宿主。
- `check_formal_readiness.py --repository . --require-main` 通过；文档工作区检查 521 文件、
  0 问题，`git diff --check` 通过。
- 定向 `:app:testDebugUnitTest`：BuiltInPackageMetadata、SearchPackage、
  PackageManagerRefreshLifecycle、PackageScanPublicationGate 四类共 16 项，零失败/错误/跳过。
- 串行 `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 2m 27s`，
  238 个任务。APK 为 `app/build/outputs/apk/debug/app-debug.apk`，488519234 bytes，SHA-256：
  `32c05e3a31efcef12b0f455d8c737c06d8f5038fcc75dba56f6d6f46d80209ae`。
  身份 `com.kiyori / 45 / 0.1.0`，V2 单签名与 16 KiB ZIP 对齐通过。
- APK 精确包含 52 项生产资产：38 个 JS、14 个 ToolPkg、包内 233 个运行文件，均与对应
  源码/生成资产逐字节匹配；共享 `OkHttp3.js` 也与本次源码一致。无重复 ZIP 条目、私密
  `.env` 或根 TS 源目录，14 个 manifest 均为 `1.0.0`。未改动 `terminal` 子模块。

以上为本轮本地与公开只读证据；候选提交链接、新鲜克隆和远端 ref 在 Git 交付步骤独立核对。

真实中转数据仍待官网允许的访问条件；Android 设备上的网络、UI 子代理和长对话 Hook
仍为 `verification_pending`。不把登录边界验证计作中转业务数据成功，也不把模拟执行计作设备验收。

## 2026-09-12 全量质量完善

本轮以当前生产白名单的 52 项为范围，继续复用 PackageManager、ToolPkgManager 和已有宿主 API。
用户明确要求全部内置包版本固定 `1.0.0`，本轮覆盖办公套件旧版本例外；协议/schema、第三方依赖
版本不随之修改。允许完成本轮审计后提交推送 `main`，不包含部署、安装或设备操作。

1. 清点全部入口、manifest、工具参数、生成资产与已有回归覆盖，逐项记录可验证的问题。
2. 修复返回状态、输入边界、异步结束和资源释放，统一准确的 Kiyori 名称、说明与版本。
3. 运行模拟宿主的成功/失败回归、TypeScript 与相关 JVM 检查，并通过正式同步入口生成资产。
4. 串行构建 Debug APK，核验全部生产资产；审阅暂存树后提交推送并核对远端 ref。

基线为干净的 `main` / `6323f71828bf839d9884afca564a1a9268cb40ed`。主要风险是脚本把业务失败
包装为成功、超时后的副作用身份丢失和元数据偏离真实参数。以既有执行所有者为边界修复，
不加入自动重做或第二执行通道；回滚点为该基线及本轮独立提交。真实第三方、设备与长对话
现场结果继续单独标记 `verification_pending`。

### 全清单审计覆盖

全部 52 项均纳入 HJSON/manifest、双语名称与说明、默认启用状态、版本、分类、
工具导出及参数声明检查。下表记录额外阅读的执行边界及针对性验证，不表示真实服务已验收。

| 范围 | 内置入口 | 本轮重点与证据层级 |
| --- | --- | --- |
| 搜索与文献 | arxiv_search、brave_search、crossref_search、duckduckgo_search、google_search、openalex_search、pubmed_search、semantic_scholar_search、serpapi_search、tavily_search、various_search、zhipu_search、openai_web_search | 声明/导出、HTTP 与结果解析、已有供应商及学术回归；修复 DuckDuckGo 空结果终止和正文失败被包装为成功 |
| 图片与视频生成 | nanobanana_draw、minimax_draw、qwen_draw、siliconflow_draw、xai_draw、openai_draw、zhipu_draw | 七个供应商的目录失败均在提交前暴露；四个异步入口先检查轮询参数；Nano Banana 终态、认证和解析失败保留任务 ID |
| 设备与生活 | 12306、automatic_ui_base、automatic_ui_subagent、daily_life、system_tools、time | 12306 初始化发布、日期/Cookie/分页边界；日历/闹钟人工确认；清理同一虚拟屏状态的重复工具声明 |
| 文件、媒体与办公 | extended_file_tools、ffmpeg、file_converter、bilibili_toolkit、office_suite | 否定存在查询、宿主返回类型、转换及保存路径；已有 Bilibili/办公模拟回归；办公包版本统一为 1.0.0 |
| 网络、开发与远程 | extended_http_tools、code_runner、super_admin、github、kiyori_editor、browser、browser_development、windows_control、linux_ssh、apktool、remote_kiyori | Cookie 字符串合同、大响应统一落盘、失败后的副作用提示、终端和远端既有回归；GitHub 源目录构建纳入正式同步 |
| 对话与常驻插件 | extended_chat、extended_memory_tools、workflow、context_limiter_c、deepsearching、message_insert、worldbook、qqbot、thinking_guidance、plan_mode | 宿主发送终态、Hook 注册、计时器/等待清理、图遍历出口、世界书损坏文件保护；保留各插件唯一状态所有者 |

### 本轮本地验证

- `node --test --test-reporter=spec tools/example_packages/*.test.mjs`：231 项，216 通过、
  15 跳过、0 失败；其中新增 `builtin_reliability.test.mjs` 20 项全部通过。
- `node node_modules/typescript/bin/tsc -p examples/tsconfig.json --pretty false --noEmit` 通过；
  正式同步同时编译目录子项目，生产白名单 52/52，无缺项或额外删除。
- `python -m unittest ci.test.test_toolpkg_sync`：16 项通过，包含 JS bundle 子项目的
  构建、缓存及源码变更失效回归。
- 定向 `:app:testDebugUnitTest`：BuiltInPackageMetadata、SearchPackage、
  PackageManagerRefreshLifecycle、PackageScanPublicationGate 四个合同类共 16 项通过，
  failures/errors/skipped 均为 0。
  新检查已发现并修复 UI 子代理状态重复声明；esbuild getter 导出按实际输出格式验证。
- `check_formal_readiness.py --require-main` 与 `git diff --check` 通过。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 45s`，238 个任务。
  APK 为 `app/build/outputs/apk/debug/app-debug.apk`，485554475 bytes，SHA-256：
  `0be497fa68d3096f86a3ee93e13a0db40ef815366daf15a755d8725d865de125`。
  包身份 `com.kiyori / 45 / 0.1.0`，V2 单签名与 16 KiB ZIP 对齐通过。
- APK 生产目录精确匹配 52 项白名单：38 个 JS 与 examples/assets 字节一致，14 个 ToolPkg
  与 Gradle 生成资产一致，包内 233 个运行文件与来源一致，manifest 均为 `1.0.0`；
  无重复 ZIP 条目、私密 `.env` 或根 TS 源码目录。Windows companion 的 `src/*.js`
  属于其正式运行文件，按 manifest 打包。
- 文档工作区检查为 521 个文件、0 个问题。设备权限、真实供应商、长对话连续调用、
  抽屉显示仍为 `verification_pending`；15 项 Linux/Shell 环境条件跳过不计作功能通过。
  本轮不执行安装、设备操作、Release 或真实账号/付费调用；候选提交与远端 ref 在交付时独立核对。

## 既有范围与规范

本专项统一 AI 对话左抽屉“扩展”下随包分发的普通 JS/TS 脚本和目录型 ToolPkg 插件。范围以
`tools/example_packages/packages_whitelist.txt` 为准，覆盖 `examples/` 可维护源码、生成的 JS、
`app/src/main/assets/packages/` 生产资产、目录型 `manifest.json`/注册脚本以及 PackageManager 的
静态合同测试。每个包的说明都是注入模型上下文的 Agent 能力提示，必须短、准确、以调用条件和结果
为中心，不写作者、宣传语、教程或凭据。

用户可见版本按 Kiyori 尚未公开发行处理。内部包名、ToolPkg ID、环境变量名、宿主服务 API、市场
协议、存储键和历史数据保持兼容；本专项不重命名协议标识，也不增加别名、回退或第二状态所有者。

## 规范合同

### 普通脚本

- 文件名、metadata `name`、导出函数名使用稳定 `snake_case`；Search 分组包名统一以 `_search` 结尾。
- metadata 必须包含双语 `display_name`、一至两句双语 `description`、`category`、显式
  `enabledByDefault` 和完整 `tools`；不包含 `author`。
- `description` 只说明供应商/能力、适用时机和返回内容；工具说明先写何时调用，再写关键限制或前置
  结果；参数名、类型和必填性与实现一致。
- 只要 `env` 非空，`enabledByDefault` 必须为 `false`。这样首次安装不会在凭据或地址未配置时
  激活网络/设备能力；用户保存配置后仍可在扩展页主动启用。
- `catch` 必须记录脱敏上下文并返回结构化失败，不在 metadata 中暴露 API Key、作者或内部路径。

### 目录型 ToolPkg 插件

- manifest 必须包含双语 `display_name`、简洁双语 `description`、显式 `enabled_by_default`；
  不包含 `author`。子包 metadata 遵循普通脚本合同。
- Kiyori 尚未公开发行，所有示例 ToolPkg manifest 及其关联运行时 `package.json`/握手常量统一使用
  `1.0.0`；依赖版本、协议 schema revision 和业务数据版本不属于此产品版本合同。
- `environment` 非空时容器必须 `enabled_by_default: false`；子包是否默认启用仍由其自身状态和
  容器开关共同决定。
- Plugin/Script 的用户可见分类和文案使用同一词汇；ToolPkg 内部 ID、注册 route、Hook 和宿主服务
  名称不随可见文案迁移。

## OpenAI 搜索迁移

`com.kiyori.openai_web_search`、`openai_web_search`、`ToolPkg.services.openAIWebSearch` 和
`OPENAI_WEB_SEARCH_*` 是兼容标识，继续保留。manifest 与子包显示名改为“OpenAI 搜索”/“OpenAI
Search”，子包工具改为 `openai_search`，完整工具名为 `openai_web_search:openai_search`；宿主解析、
结果卡和持久化历史只按内部包 ID 工作，因此不需要协议别名或数据迁移。

## 统一分类与命名

分类使用现有稳定键：`Search`、`Academic`、`Draw`、`Chat`、`File`、`Network`、`Memory`、
`Media`、`Life`、`Automatic`、`Development`、`System`、`ToolPkg` 等。中文显示名中的品牌/服务名
与能力词之间使用一个空格（例如“Brave 搜索”“Google 搜索”“OpenAI 搜索”“Tavily 搜索”）；英文
显示名使用自然 Title Case。业务数据字段中的 `author`（论文、评论、GitHub 内容等）不是 metadata
作者字段，必须保留。

## 实施阶段与验收

1. **M0 研究与冻结**：完成白名单、资产、manifest、默认启用路径和 OpenAI 宿主合同盘点，记录本文件。
2. **M1 元数据规范化**：修正所有生产普通脚本和 ToolPkg manifest 的字段、说明、分类、作者字段和
   环境变量默认启用状态；生成 JS/assets 只能通过项目同步入口更新。
3. **M2 OpenAI 搜索**：更新显示名、子包工具名、UI 文案和调用合同；宿主服务/内部 ID 保持不变。
4. **M3 自动审计**：新增静态合同检查，覆盖白名单闭包、examples/assets 字节一致、双语文案、无作者、
   环境变量默认关闭、分类白名单、`1.0.0` 版本、Search 命名、工具/exports 对应、manifest 子包闭包
   和敏感内容。
5. **M4 构建与交付**：运行定向测试、正式准备检查、差异检查和 `:app:assembleDebug`，核验 APK 资产；
   审计本轮授权的全部当前改动后提交 `main`、推送 `origin/main` 并核对三方 ref。

## 验收矩阵

| 项目 | 自动证据 | 仍需现场证据 |
| --- | --- | --- |
| 首次安装环境脚本关闭 | metadata/manifest 合同测试与 PackageManager 默认状态测试 | 设备清数据安装后扩展页开关状态 |
| OpenAI 搜索命名与调用 | 源码、生成 JS、assets、宿主合同测试 | 设备模型真实调用与结果卡展示 |
| 全部内置资产规范 | 白名单闭包、HJSON/JSON 解析、文案/作者/分类/exports 审计 | 抽屉视觉密度与本地化显示 |
| 代码质量与错误路径 | TypeScript 编译、定向 JVM、静态反向检查 | 真实第三方额度、设备权限与网络行为 |
| 交付 | Debug APK、敏感内容/暂存树审计、local/tracking/remote ref 对账 | Release、远端 Actions、真机验收不由本专项替代 |

状态在设备或用户验收完成前保持 `verification_pending`。

## 本地完成证据（2026-08-28）

- 同步入口最终报告 `whitelist=49`、`resolved=49`、`copied=0`、`packed=0`、`missing=0`；37 个普通
  JS 和 12 个 ToolPkg 与 APK 源资产无漂移。
- `:app:testDebugUnitTest` 为 `1815` tests，failures/errors/skipped 均为 `0`；新增合同覆盖版本、分类、
  双语简介、无作者、环境变量首装关闭、外部 ToolPkg 启用语义和文件转换 Shell 参数边界。
- `check_formal_readiness.py --require-main`、`check_fresh_clone.py`、网络代理 Python 合同 13 tests 和
  `git diff --check` 全部通过。
- `:app:assembleDebug` 成功；`app-debug.apk` 为 `com.kiyori / 45 / 0.1.0`，V2 Debug 单签名和 16 KiB
  ZIP 对齐通过。APK 内 49 个扩展资产与各自源资产 SHA-256 一致，12 个 ToolPkg 均为 `1.0.0`、无
  manifest 作者，且不包含 `.env` 或 `src/` 条目。
- 未执行 Release、远端 Actions、ADB、模拟器或真机安装。首次安装开关、抽屉显示、真实 OpenAI
  调用和第三方服务现场行为继续保持 `verification_pending`。
