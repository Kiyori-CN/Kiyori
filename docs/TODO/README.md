---
For_Agent: 对项目大规模动工前按本规范协作
---

# Kiyori 开发任务与验证索引

## 当前进行中

- [AI 对话 HTTP attempt 审计与稳定性收口](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)：补齐
  `stream was reset: CANCEL` 等流式传输故障的 attempt、阶段、提交状态、重试决定与取消因果审计，
  并收紧普通 Chat Completions 的未知提交重试边界；M12 本地实现与完整 JVM 验证已完成，正式门禁、
  Debug APK 已验证；真实 Provider 与设备验收保持 `verification_pending`，本轮全部工作树修改已随
  `main` 提交并推送。

- [MT 管理器手机存储复刻](mt_file_manager_replica/index.md)：在共享文件管理器中复刻 MT 管理器
  的双窗格手机存储工作台，保留现有 AITool/SAF 文件能力，明确左上绝对退出按钮和目录级系统 Back
  顺序。本轮按四张 `1260x2800` 真实截图做像素级对齐：状态栏/顶栏 `#303030`、内容/底栏
  `#FAFAFA`、约 `40dp` 行高与 `28dp` 图标、实时水平拖动位移、浅蓝选中、同窗格范围选择、
  跨栏选择隔离、活动栏窄阴影和一黑一白的路径同步箭头；实现与自动化验证完成后，设备视觉/触摸/
  系统 Back 仍保持 `verification_pending`。

- [全软件 UI 风格统一优化](ui_style_unification/index.md)：以设置页风格为基准，统一 AI 对话、
  抽屉、AI 助手设置、浏览器、播放器和下载器的控件/文案层级，并修复可证实的呈现问题。
  最新增量恢复浏览器资源嗅探徽标、AI 对话统计球和各页面横向筛选/切换条的紧凑高度，独立高频
  操作按钮仍保持原有触摸策略。
  设置页标题 owner 收口和文件管理器空动作已在 main 完成；本次修正已补齐根/能力页及 Workspace
  页面与“AI助手-文本转语音”一致的滚动收缩标题，设置根页“设置”保留固定顶栏，并通过本地测试、
  正式门禁和 Debug 构建。
  真机视觉/触摸/输入法及真实 WebView、播放器、下载任务仍保持 `verification_pending`。

## 2026-08-30 Mihomo 运行时指纹内存溢出修复

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

崩溃报告 `5f93fd3f-e65f-4649-8bc6-ec5bab1885bb` 显示，AI HTTP 工具解析应用级代理路由时，
`KiyoriMihomoRuntime.startOrReuseLocked()` 在堆已接近 512 MiB 上限的情况下尝试扩展
`StringBuilder`，因新的 `734128` 字节连续分配失败而触发 `OutOfMemoryError`。现行实现会在每次
runtime 复用检查前，把最大 4 MiB 的 `sanitizedYaml`、测试 URL、路由模式和最多 1000 条自定义
规则拼成第二份完整 `String`，随后 `sha256()` 又为同一内容创建完整 UTF-8 `ByteArray`。Mihomo
输出 collector 只保留 32 行，进程内代理日志只保留 1000 条，均不是本次栈顶分配的 owner。

本轮计划：

1. 主 runtime 与隔离 probe 共用有界 UTF-8 增量 SHA-256 路径，按原分隔符和字段顺序直接写入
   digest，不构造完整拼接字符串或完整 UTF-8 副本；保留既有 fingerprint 比较语义。
2. 增加 JVM 回归，逐字节对照旧的小样本 payload 哈希，覆盖 Unicode YAML、路由模式、规则类型、
   规则顺序和启用状态；同时确认两个旧的大对象构造入口都已移除。
3. 依次执行定向 runtime policy 测试、`git diff --check`、formal readiness、必要的编译检查、串行
   `:app:assembleDebug --no-daemon --console=plain` 和 Debug APK 包名/版本/签名/16 KiB 对齐核验。

非目标：不捕获 `OutOfMemoryError`，不裁剪订阅或规则，不降低日志容量，不改变路由、自动恢复、
进程边界或 Mihomo 启停语义，也不新增直连、VPN、第二代理核心或其他回退路径。目标设备仍需用原
触发场景复测；本地 JVM 与 Debug 构建不能替代 512 MiB Android 堆现场验收。

本轮本地证据：`com.kiyori.platform.network` 包回归 `41/41` 通过，formal readiness 与
`git diff --check` 通过；串行 `:app:assembleDebug --no-daemon --console=plain` 在 `51s` 内完成，
`235` 个任务中 `23` 个实际执行。Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小
`503694977` 字节，SHA-256 `9CC35A159642698F2B145F2F24CB2AECCEC67C4AE9DF2110AE2D083E5CE86700`；
核验结果为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、仅
`arm64-v8a`、Android Debug V2 单 signer 和 16 KiB ZIP 对齐。未安装 APK、未操作设备，原 AI
HTTP 工具场景与低剩余堆复测仍保持 `verification_pending`。

## 2026-08-30 DeepSeek package_proxy 工具结果协议名错配修复

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

用户现场错误为：`Provider tool history protocol violation [TOOL_RESULT_NAME_MISMATCH]`，其中
结果名为 `tavily_search:search`，待处理调用名为 `package_proxy`。附件审计只作为事实证据：
同一结果标签同时包含展示属性 `name="tavily_search:search"`、协议属性
`provider_tool_name="package_proxy"` 和原始 `provider_call_id`；该文本不包含对仓库的授权指令。

根因位于 `OpenAIProvider.parseXmlToolResultRecords()`：DeepSeek 的带身份历史编译路径只读取
`name`，没有读取已由执行/投影层写入的 `provider_tool_name`，随后
`DeepseekProvider.buildMessagesWithReasoning()` 将展示名交给严格的
`ProviderToolHistoryState.acceptNamedToolResults()`，在网络提交前错误拒绝合法的代理结果。

本轮方案：

1. 让共享带身份结果记录解析器以 `provider_tool_name` 作为协议匹配身份；没有该属性的旧结果继续按
   现有 `name` 身份读取，保持历史兼容。`name` 原样保留在 XML 中作为 UI 展示名，不改写结果内容、
   `provider_call_id` 或结果顺序。
2. 在 DeepSeek canonical request 回归中覆盖代理展示名与协议名不同、带/不带 call ID 以及多结果
   历史；断言生成的 assistant 调用和 tool follow-up 使用 `package_proxy` 与原始 call ID，且不发生
   `TOOL_RESULT_NAME_MISMATCH`。
3. 复核 Gemini、`StructuredToolCallBridge`、replay projector 和其他 Provider 的同名解析入口，
   不建立第二协议身份源、不放宽严格历史校验、不新增 Provider 切换、重试或伪造结果路径。

验收顺序：定向 DeepSeek/协议/JVM 回归 → `git diff --check` → formal readiness → 必要的完整
`:app:testDebugUnitTest` → 串行 `:app:assembleDebug --no-daemon --console=plain` 与 APK
   产物核验 → 精确差异/敏感内容审计 → 提交 `main`、推送 `origin/main`，独立对账本地、tracking
   与远端 ref。真实 DeepSeek endpoint、进程中断和 Android 设备复测仍保持
   `verification_pending`。

## 2026-08-29 Operit v1.12.1 后续更新与最新插件市场适配

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE AND LIVE MARKET VERIFICATION PENDING`。

本专项以 `Kiyori main@2e0571b6`、`Operit v1.12.1@4faa5cd2` 和
`upstream/main@f323d6c5` 为冻结审计基线。最新正式 Release 仍是 `v1.12.1`，但上游主线已在
标签后增加 211 个提交、触及 480 个路径；整分支合并会覆盖 Kiyori 的产品壳、浏览器、播放器、
网络代理、存储和正式开发门禁，因此只按功能簇选择性适配。

实时 Operit Market v2 清单于 `2026-08-29T04:08:10Z` 生成，共 1355 项；其中 19 项最新版本
要求 `1.12.1`，2 项要求 `1.12.1+3`。本轮已对这 22 个资产完成只读、内存内 SHA-256 校验和
注册/API 静态扫描，不安装或执行第三方代码。Compose DSL 六模式 picker、ToolPkg logo、聊天
标识和 Market v2 发布协议已接入现有唯一 owner，独立的 Operit 市场兼容版本已提升为
`1.12.1+3`；Kiyori 的 `com.kiyori / 45 / 0.1.0` 产品身份不变。完整 JVM、TypeScript、Lint、
正式准备、架构、fresh-clone 和 Debug APK 审计均已通过；Lint 为 `0 errors / 35 warnings / 0 hints`，
APK 为 Android Debug V2 单 signer、16 KiB ZIP/ELF 对齐、arm64-only，49 项生产 ToolPkg 与白名单
精确一致。真机 picker、第三方插件 UI、语音 provider、长聊天/大导出和市场动态写入保持待验证。

详细上游矩阵、阶段依赖、非目标、风险和验收条件见
[`operit_post_1_12_1_sync/index.md`](operit_post_1_12_1_sync/index.md)。

## 2026-08-29 OpenAI 搜索首次调用不再强制兼容探测

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND REAL RELAY VERIFIED / DEVICE UI VERIFICATION PENDING`。

用户现场确认 `openai_web_search:openai_search` 在环境变量已经配置完整、但尚未点击“运行可能计费的兼容探测”时，普通搜索被宿主硬编码的 `requireRelayProbe=true` 阻断并返回 `RELAY_PROBE_REQUIRED`。本轮将兼容探测恢复为显式诊断能力：普通搜索只执行本地绑定、认证、参数和请求合同校验，配置有效即可发出一次 Responses 请求；探测成功/失败记录仍用于状态展示和用户主动诊断，不再成为普通搜索的执行前置。中转 endpoint 同时接受安全规范化的根地址（如 `https://speed.ai-pixel.online/`）和完整 `/v1/responses` 地址，最终只生成一个 `/v1/responses` POST。

范围：`ToolPkgOpenAIWebSearchBridge`、`OpenAIHostedWebSearchBindingResolver/Policy`、Responses 请求编译、定向 JVM 合同测试、ToolPkg 源码/dist/资产及相关文档。保留 ToolPkg ID、`openai_search` 工具名、package-scoped host-service Key 隔离、证据解析、域名策略、取消生命周期和 at-most-once 提交边界；不新增 provider 切换、自动重试、降级或第二搜索实现。

验收：无 probe 记录的 relay 配置可直接进入 gateway；缺少/非法环境变量仍在提交前返回结构化错误；根地址和完整 endpoint 规范化测试通过；生成资产与 source 一致；定向/全量 JVM、正式准备、新鲜克隆、`git diff --check` 和串行 `:app:assembleDebug --no-daemon --console=plain` 均通过。已使用用户授权的中转地址、`gpt-5.6-terra` 和 Key 完成一次真实非流式请求：HTTP 200、`completed`、1 个 `web_search_call`、1 个 URL citation；未发生未知提交状态。设备 UI 首次使用和结果卡现场验收仍待完成。

## 2026-08-29 DeepSeek Responses 响应头前断流修复

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK VERIFIED / TARGET DEVICE VERIFICATION PENDING`。

目标设备审计确认官方 `https://api.deepseek.com/v1/responses` 的第 4 个 Provider hop 在请求体
提交后、响应头返回前由 OkHttp `Http2Stream.takeHeaders()` 抛出
`StreamResetException: stream was reset: CANCEL`；同一回合没有用户停止审计事件，失败后约 52 秒
才出现下一次用户输入。第一版候选将 Responses 固定到 HTTP/1.1；新审计随后确认该策略已生效，
但第 3 个 Provider hop 又在 `Http1ExchangeCodec.readResponseHeaders()` 以
`EOFException: \n not found` 断开。故障并非 HTTP/2 专属，第一版候选不能作为最终修复。

修订实现继续保持 HTTP/1.1，但 Responses client 不再保留空闲连接，并显式关闭 OkHttp 的连接失败
重试；每个串行工具 hop 都使用新连接，未知提交状态仍不发送第二个 POST。普通 AI client 的
HTTP/2、连接池和重试策略不变。详细证据、实施与验证状态见
[`unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)
的 M9。

## 2026-08-29 Responses 正文中断的部分回答安全收口

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / TARGET DEVICE VERIFICATION PENDING`。

目标设备归档进一步确认，DeepSeek Responses 在已经收到可见 `<think>` 片段后，HTTP/1.1
chunked response body 仍可能以 `EOFException` 截断。M9 的独立 HTTP/1.1、零 idle connection
和关闭 OkHttp 自动连接恢复只改变连接生命周期，不能让没有终态事件的远端执行变成成功；本专项
因此修复消息层的失败收口：从共享流和修订跟踪器取得最后可见文本，经
`AssistantReplayHistoryProjector` 移除未闭合工具事务后，只有非空 replay-safe 正文才写入部分
assistant 投影，并记录 `ASSISTANT_PROJECTION_UPDATED / PARTIAL`。原始
`OpenAIResponsesSubmissionUnknownException`、`SUBMISSION_UNKNOWN` 和失败审计终态保持不变，
不重发未知 POST、不伪造 `response.completed`、不切换 provider/协议/endpoint。

无可安全投影的正文时只保留真实失败与运行态清理，不把空消息标记为成功；失败回合跳过正常
`Completed` 收尾，下一次用户输入复用同一聊天历史但不会继承已结束的流或错误状态。定向测试、
正式门禁、Debug APK 和提交推送完成前，本专项保持 `verification_pending`。

详细根因、影响文件、验收矩阵和证据见
[`unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)
的 M10。

## 2026-08-29 DeepSeek Responses 内容协商、语义终态与真实 hop 诊断

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / TARGET DEVICE VERIFICATION PENDING`。

三组新增目标设备归档确认，DeepSeek Responses 的联网搜索、日常工具和天气会话分别在最后第
`3 / 7 / 7` 个 Provider hop 失败；前两组在请求体提交后、响应头返回前 EOF，天气组在收到
`7` 个可见字符后发生 HTTP/1.1 chunked body EOF。三组请求快照约为 `116375 / 40727 /
47096` bytes，较小请求同样失败，不能把问题归结为单一请求大小阈值。前置工具 hop 均能完成，
也不能把 DeepSeek 工具历史描述为从第一跳起就不兼容。

本轮 M11 补齐三个已证实的客户端合同：Responses 流式 POST 默认发送
`Accept: text/event-stream`，非流式 POST 默认发送 `Accept: application/json`；所有 Responses
流只有观察到 `response.completed` 才能成功返回；失败审计从异常 cause chain 取得真实最后 hop
的 `localExecutionId`，不存在诊断 ID 时保持空值，不再错误沿用回合首 hop。现有 at-most-once、
`SUBMISSION_UNKNOWN` 和 M10 部分回答收口保持不变，不重发未知 POST、不把 EOF 当成功，也不增加
协议/端点切换、压缩或连接头猜测。真实 DeepSeek endpoint 与目标设备复测继续保持待验证。

详细现场矩阵、DeepSeekHarness 对照、实施合同、风险与故障注入验收见
[`unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)
的 M11。“对话详情”诊断中心的信息架构优化是独立 UI 专项，不混入本轮传输协议补丁。

## 2026-08-29 DeepSeekHarness Responses 提交稳定性与第三方协议边界

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

本轮基于 `D:\\01_Environment\\Apps\\DeepSeekHarness` `0.1.2-alpha.1`、官方 DeepSeek
Responses 成功审计和 SiliconFlow 404 审计，修复选择“深度求索”时 Responses 提交被普通五次
重试的问题。DeepSeek Responses 没有已证明的 response resume 合同，但仍必须采用一次性提交状态：
只有明确的 `429` 拒绝允许有限重试；`404/400/401/403/422` 立即失败，`408/409/5xx` 及网络
中断标记为 `SUBMISSION_UNKNOWN`，不创建第二个 POST。

第三方 SiliconFlow 的公开接口是 OpenAI Chat Completions：应显式选择“硅基流动”或在“深度求索”
下选择 `OPENAI_CHAT_COMPLETIONS`，请求端点为 `https://api.siliconflow.cn/v1/chat/completions`。
本轮不根据错误响应静默切换协议、不把 `/v1/chat/completions` 伪装成 Responses；官方 DeepSeek
Responses 继续使用 `https://api.deepseek.com/v1/responses`。DeepSeek Chat 的
`reasoning_content`、原始 `provider_call_id`、工具名和 provider usage cache-hit 桶保持互不
覆盖的语义。

详细影响文件、风险、验收矩阵和本轮证据维护在
[`unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)
的 M8；真实端点、缓存命中率和设备交互仍单独保持 `verification_pending`。

## 2026-08-28 五源学术搜索脚本深度优化

状态：`IN PROGRESS`。

本轮基于统一主题搜索和同篇论文交叉核验，继续优化 arXiv、Crossref、PubMed、Semantic Scholar、
OpenAlex 五个 Academic 脚本。范围包括真实执行生成 JS 的确定性合同测试、OpenAlex 搜索排序约束、
Crossref 默认紧凑响应、arXiv Atom 错误与版本/DOI 语义、PubMed 结构化错误，以及各源分页、字段和
来源口径说明；不新增备用 API、静默重试、跨库自动合并或引用数换算。详细根因、阶段和验收矩阵见
[`academic_script_catalog/index.md`](academic_script_catalog/index.md) 的“2026-08-28 五源深度优化”。

## 2026-08-28 内置脚本与插件目录规范化

状态：`LOCAL IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`。

本专项统一所有随包普通脚本和目录型 ToolPkg 的命名、分类、双语 metadata、Agent 注入说明、作者字段
和环境变量默认状态；含环境变量的包首次安装默认关闭，未发布 ToolPkg 及关联运行时版本统一为
`1.0.0`。OpenAI 搜索显示为“OpenAI 搜索”，包内工具调用名为 `openai_search`，内部 ToolPkg/宿主
协议标识保持兼容。详细合同、阶段、验收矩阵见
[`builtin_script_plugin_standardization`](builtin_script_plugin_standardization/index.md)。

## 2026-08-28 Mihomo Geo* 配置校验超时修复

状态：`LOCAL FIX VERIFIED / TARGET DEVICE VERIFICATION PENDING`。

固定 Mihomo `v1.19.30` 在运行目录缺少 GeoSite/MMDB/ASN 数据时，会因订阅 `GEOSITE`、`GEOIP`、
`IP-ASN` 或远程 `RULE-SET` 依赖而在 `-t` 阶段下载并卡住；20 秒后才被误报为配置无效。清洗器现已
恢复自包含运行配置边界，并覆盖已有保存 YAML 的 runtime 重建；定向 JVM、Python 合同、正式准备、
架构边界和 Debug APK 已验证，真实设备复测仍待完成。详见
[`application_network_proxy`](application_network_proxy/index.md)。

## 2026-08-28 最新日志复核：runtime readiness 与诊断噪声

状态：`LOCAL IMPLEMENTATION VERIFIED / TARGET DEVICE VERIFICATION PENDING`。

最新浏览器日志的 7 个 `PLAYER_HANDOFF` 标识均唯一，未出现新的渲染器退出或应用 FATAL；代理日志的
Mihomo generation 1/2/3 仍以退出码 `0` 自然退出并自动恢复。实现已让旧 WebView readiness 在自然
退出时立即失效，避免恢复窗口访问死端口；相同端点/旁路策略不再重复安装 process-wide override，
console 诊断在交错 session 中聚合并保留 `repeatCount`。具体证据、非目标与设备验收见
[`kiyori_browser_product_completion/19_latest_log_runtime_readiness_and_diagnostic_noise.md`](kiyori_browser_product_completion/19_latest_log_runtime_readiness_and_diagnostic_noise.md)。

## 2026-08-28 Android 16 播放器关闭与重复交接修复

状态：`LOCAL FIX VERIFIED / DEVICE VERIFICATION PENDING`。

最新 vivo Android 16 日志确认两条独立问题：播放器关闭在主线程调用 OkHttp
`ConnectionPool.evictAll()`，触发 `NetworkOnMainThreadException`；同一媒体候选在短时间内产生重复
`PLAYER_HANDOFF`，使全屏 Activity 生命周期出现竞争。媒体桥接同时把上游 TLS 握手失败投影为
502，mpv 最终显示通用 `loading failed`。

本轮把桥接器关闭改为原子一次性提交，主线程只停止监听、清空状态并停止请求 executor；活动请求取消、
连接池/socket 回收在专用后台清理线程完成。同一候选在播放器仍有效且无错误时只复用现有 handoff，已有媒体错误或运行时
死亡时允许用户重新打开以重新建立加载。TLS 失败保留 `UPSTREAM_TLS` 阶段和异常类型，仍返回合法
502，不增加节点切换、直连或重试。定向测试与 Debug 构建属于本地证据，目标设备上的关闭、重复嗅探、
实际节点 TLS 和完整播放仍待复测。

## 2026-08-28 最新安装回归：脚本长按崩溃与代理启动竞态

状态：`LOCAL FIX VERIFIED / DEVICE VERIFICATION PENDING`。

最新安装现场的脚本长按崩溃来自 `KiyoriSettingsSelectionSheet` 在 AI 扩展页脱离设置路由组合，读取严格的
`LocalKiyoriSettingsColors` 默认值；组件现在自带 `KiyoriSettingsTheme` 边界，设置页和扩展页共用同一实现。
启动代理日志显示 Mihomo 已达到 `controllerStatus=200` 与 `mixedPortListening=true`，失败发生在首个 WebView
provider/support-library bridge 初始化前调用 `ProxyController.setProxyOverride`。启动协调现在等待真实 Browser
WebView 完成配置、能力探测和脚本桥接后才开始；Browser 仍等待同一 readiness，不发送未代理的远程主文档请求。

本轮自动证据：完整 `:app:testDebugUnitTest` 通过，Debug `:app:assembleDebug --no-daemon --console=plain` 通过，
唯一 launcher、脚本代理运行时和播放器运行时打包门禁通过；APK 为 `503669293` 字节，SHA-256
`86944F0B9A578B3CBA67BCBC18CBC5FB3DBD355BACDD85D8139CAC05BFAC2D0F`，`com.kiyori / versionCode 45 / versionName 0.1.0`，
Debug V2 签名与 16 KiB 对齐通过。未安装 APK 或操作目标设备，真实长按、冷启动代理和嗅探播放仍保持
`verification_pending`。

本轮结果：`DeepseekCanonicalRequestTest` 及共享协议/replay 定向 suite 通过；完整
`:app:testDebugUnitTest` 为 `1888 tests / 0 failures / 0 errors / 0 skipped`；formal readiness、
`git diff --check` 和 `assembleDebug` 均通过。Debug APK 为
`app/build/outputs/apk/debug/app-debug.apk`，大小 `503694977` bytes，SHA-256
`844712B7C77991497A41763AC410511CD995D9FDC4A825C9632E33C3E3AC89DE`。期间发现的两条浏览器
视觉 token 过时断言已按当前 `40dp` 触摸目标与 `KiyoriUiShapes.control` 合同同步，未改运行时 UI。
真实 DeepSeek endpoint、进程中断和目标设备复测仍待完成。

## 2026-08-27 在线播放、启动代理与脚本规则修复

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / DEVICE VERIFICATION PENDING`。

本轮确认嗅探视频偶发 `Fullscreen Activity cannot launch...` 来自 `PlayerSurfaceLease` 一次性 Activity
请求被重复/过期回调触发，而非媒体 URL 或 mpv 解码；全屏启动现在按租约状态幂等投影，同一媒体从
悬浮切全屏也必须先完成 Surface 转移。应用冷启动代理则改为唯一共享启动协调：Browser 首次远程导航
等待 Mihomo 与 process-wide `ProxyController` 完成，失败时阻止该导航并明确报告，不静默直连；启动协调失败后，设置页
重新协调可发布新的 readiness generation，恢复后续远程导航。

网络代理 schema 升级为 5 并移除未发布的 `moduleModes`；所有模块统一跟随顶层“规则 / 全局 / 直连”，
只有传统 JsEngine 脚本保留包级覆盖。“逐脚本连接模式”统一为“脚本规则”，只显示全部已安装可执行
脚本（含停用项），按扩展页相同分类分组，刷新移到标题栏图标并删除手动包名入口。AI 左抽屉 -> 扩展 -> 脚本中，
短按仍打开详情，长按打开同一 manager/`scriptModes` 持有的规则抽屉。启动代理使用可恢复 readiness generation，
首次协调失败不会静默直连；设置重新协调成功后允许后续导航。详细方案、迁移和验收矩阵见
[`application_network_proxy/index.md`](application_network_proxy/index.md) 的“2026-08-27 在线播放、启动代理与脚本规则重构方案”。

## 2026-08-27 浏览器悬浮播放器关闭后重弹修复

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / DEVICE VERIFICATION PENDING`。

诊断报告中的 `.mp3` URL 实际由 mpv 识别为 `mov/mp4 + H.265/AAC` 视频。当前缺陷来自自动悬浮触发状态的 UI 局部 URL 键和导航清理时序：关闭清空唯一 `PlayerSession` 后，同页候选仍可能满足自动入口；导航先更新 URL、后在 WebView `onPageStarted` 清空候选的窗口也可能把旧媒体当成新页面候选。现已改由每个 `WebSession` 持有文档 token 级消费事实，所有导航方式在发出 WebView 操作前失效旧 token/清空候选，DOM observer 也绑定注入时 token，并要求页面完成加载后才允许自动悬浮。completion 与主框架错误还必须匹配最近 `onPageStarted` URL；SPA history 独立匹配 WebView 当前 URL；没有主框架身份的证书回调只拒绝无效证书，不再覆盖页面状态。不改写媒体 URL/MIME，不改变网页媒体状态，不创建第二播放器，不增加重试或替代路径。

本地证据：浏览器候选与播放器定向 JVM、完整 `:app:testDebugUnitTest`、Python 合同测试 `124/124`、formal readiness、fresh-clone 检查和 `git diff --check` 均通过。最终串行 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 47s`，共 `235` tasks，并通过唯一 launcher、代理和 Player runtime packaging 门禁。Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间 `2026-08-27 21:15:43 +08:00`，`503669293` 字节，SHA-256 `83A76359CBB903EB7CDBD845AB13E9D0DE0238E2F827F146595F65B755EF97DE`；`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`、Android Debug V2 单 signer、16 KiB ZIP 对齐、`5512` 个 ZIP entry 零重复、仅 arm64 和播放器/代理 native/runtime 清单均通过。未安装 APK 或操作目标 vivo Android 16 设备，真实网页关闭、切页和新文档重新自动悬浮仍保持 `verification_pending`。

详细根因、影响文件、风险、回滚点和验收矩阵见 [`kiyori_browser_product_completion/9_browser_sniffer_and_floating_playback.md`](kiyori_browser_product_completion/9_browser_sniffer_and_floating_playback.md) 的“2026-08-27 悬浮播放器关闭后重弹与跨页旧媒体重播修复”。

## 2026-08-27 日志驱动的浏览器、播放器与代理链路修复

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / TARGET DEVICE VERIFICATION PENDING`。

vivo Android 16 真机日志证明当前 Browser 媒体候选交给独立 `:player` 后，播放器进程重新创建了
第二个 Mihomo runtime：主进程为 `mixedPort=40877`，`:player` 为 `mixedPort=42071`。同一时段
目标节点对媒体域名返回 `503 Service Unavailable`，但 `PlayerMediaStreamBridge` 的异常路径只关闭
socket，mpv 最终只能显示 EOF/`loading failed`。本轮方案把 `PLAYER` route 解析和 loopback bridge
移回唯一主进程 `PlayerSession`；`:player` 只消费已解析 target，不读取代理配置、不启动 Mihomo。
同时补齐 bridge HTTP 错误响应，以及 Browser candidate/handoff、Player request 与主进程 proxy
generation 的脱敏关联。完整证据、边界、影响文件、风险、回滚点和验收矩阵见
[`kiyori_browser_product_completion/17_log_driven_browser_player_proxy_repair.md`](kiyori_browser_product_completion/17_log_driven_browser_player_proxy_repair.md)。

## 2026-08-27 浏览器运行时诊断与菜单优化

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / TARGET DEVICE VERIFICATION PENDING`。

在不替换 Android System WebView/Chromium provider、不创建第二 Browser Runtime 的前提下，
本轮将浏览器菜单固定为三行 `5/5/5`：第一行“加书签 / 书签 / 历史 / 下载 / 插件”，第二行
“UA 标识 / 资源嗅探 / 网络日志 / 诊断日志 / 工具箱”，第三行“无痕模式 / 查看源码 / 标记广告 /
网站配置 / AI 对话”。未实现的“阅读模式”占位入口、route、图标与文案会彻底移除；未来阅读模式
从工具箱的浏览器页面工具重新设计。新增诊断抽屉使用唯一 `StandardBrowserSessionTools` 的
进程级有界结构化缓冲区，记录 provider、WebKit 能力、导航、权限、弹窗、userscript、媒体和
渲染错误事件，保留 session/document/Profile 上下文，并按当前/全部会话、级别、类别与查询词筛选，
统一脱敏后才允许查看、复制或 SAF 导出。AndroidX WebKit 从 `1.16.0` 升级到
Google Maven 当前稳定 `1.17.0`；`loadUrl` 导航合同和缓存策略本轮不迁移。定向诊断/菜单测试、
完整 `:app:testDebugUnitTest`、`phase=m03` 架构门禁、正式开发准备门禁、新鲜克隆检查和 Debug
APK 静态审计均已通过；完整方案见
[`kiyori_browser_product_completion/16_browser_runtime_diagnostics_and_menu_optimization.md`](kiyori_browser_product_completion/16_browser_runtime_diagnostics_and_menu_optimization.md)。

本文件顶部记录当前跨领域长期任务，后续段落保留专项实施与历史证据。历史段落中的分支、提交、
APK 哈希、测试数量和“未提交/未推送”等描述只代表当时观察点，不能替代当前 Git、构建或设备状态。

## 2026-08-27 强制页面缩放的 PC UA 双指缩小修复

状态：`IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`。

用户现场确认“设置 -> 网页浏览器 -> 强制页面缩放”在双指向外放大时正常，但在双指向内缩小、尤其是 PC UA 页面上，网页已经处于 WebView 的最低概览比例，继续缩小没有反应。本专项沿同一个 `StandardBrowserSessionTools` / `WebSession` / Android System WebView owner 研究，不创建第二套缩放状态或网页运行时。

已确认的根因与设计边界：

- `BrowserDisplaySettingsSupport.kt` 的 viewport 覆盖只移除 `user-scalable` 与 `maximum-scale`，保留网页自身的 `minimum-scale`；作者下限仍可限制 Chromium page scale。
- `BrowserWebViewSupport.kt` 对桌面 UA 同时开启 `useWideViewPort` 与 `loadWithOverviewMode`。Android 官方定义 overview 为“按宽度缩出以适配屏幕”，Chromium 会据此把最低 page scale 提升到视口宽度与内容宽度之比；因此 PC UA 首帧可能已经在最低比例。
- 修复同时移除 `minimum-scale` 并显式提供 `minimum-scale=0.1`；强制缩放打开时，桌面 UA 保留 `useWideViewPort=true` 以维持 PC 页面布局，但关闭 `loadWithOverviewMode`，使首帧不再抢占整个缩小区间。关闭强制缩放时恢复已有 desktop overview 行为。
- Android、iPhone 等移动 UA 继续使用其响应式 viewport 适配；不会把 UA 改写成另一种设备身份、不会移除 `initial-scale`、不会通过刷新或 JS transform 伪造缩放。

详细阶段、文件、风险和验收矩阵见 [`kiyori_browser_force_zoom/`](kiyori_browser_force_zoom/index.md)。本轮代码、定向测试和 Debug APK 属本地证据；真实 PC UA/Android 页面上的手势与视觉仍需目标设备复测，完成前保持 `verification_pending`。

## 2026-08-23 Academic 学术脚本分组与五源 API

状态：`IMPLEMENTATION VERIFIED`。

本轮在“扩展 -> 脚本”新增 `Academic` 分组，使用独立 `MenuBook` 图标和固定明暗配色；脚本清单为
arXiv、Crossref、PubMed、Semantic Scholar、OpenAlex。现有 `crossref_search` 从 `Search` 移入
`Academic`，五个脚本统一使用双语 metadata、官方端点、结构化返回和显式错误日志。Brave 的
`BRAVE_SEARCH_API_KEYS` 保持必填，Answers/Suggest/Spellcheck 三个 Key 改为可选。完整接口矩阵、
认证字段、实施计划、15 项定向测试、真实 API 与 Debug APK 验证边界见
[`academic_script_catalog/index.md`](academic_script_catalog/index.md)。

## 2026-08-23 Kiyori 应用级网络代理与内嵌 Mihomo（历史方案，已由 2026-08-27 schema 5 方案取代）

状态：`IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`（真机已确认订阅可导入展示；本地已修复 Mihomo 运行配置的外部地理数据依赖并增加代理日志，等待修订 APK 现场复测）。

上一版传统脚本代理正在升级为 Kiyori 唯一的应用级代理 owner。正式入口迁至“设置首页 -> 更多
功能 -> 网络代理”，支持默认连接、逐模块连接和脚本模块内逐脚本规则；AI 服务、AI 工具、
Browser、下载器、播放器、脚本与扩展、Kiyori 在线服务按各自网络栈接入同一配置。

目标订阅实测证明服务端依 User-Agent 返回不同格式：普通 Kiyori/mihomo 身份获得 Base64 URI
列表，`Clash.Meta` 身份获得包含策略组的 YAML。订阅客户端固定 Clash Meta 内容协商，并在安全
清洗后保留策略组；本地地址额度占位节点被隔离并报告，不再阻断整份有效订阅。用户安装首份 APK
后确认 URL/YAML 导入与总开关均无法保存，根因是 Android Keystore 随机化密钥拒绝调用方 IV；
本轮同时把单订阅模型升级为加密多订阅库，覆盖刷新、添加、切换、更新、编辑、复制、删除、按组
展示和单节点/整组测速。最新真机反馈进一步证明订阅结构和 UI 投影正常，但清洗后的 DNS 仍引用
私有工作目录不存在的 GeoSite/GeoIP 数据，主/probe runtime 均在 `mihomo -t` 阶段失败；本地修复
已剥离这些外部数据依赖，并提供可查看、复制、SAF 导出和清空的进程内脱敏代理日志。自动测试和
Debug APK 仍只证明本地实现，真实节点连接需由修订安装包现场确认。

以下界面描述是 2026-08-23 当时的历史实现，已被本文顶部的 2026-08-27 schema 5 方案取代：界面移除外部
mixed-port 的主机/端口/认证表单。代理主页当时保留开关、默认连接、当前节点、订阅管理、模块连接模式、
逐脚本连接模式、代理日志、局域网地址、系统 VPN 并存和重置；当前节点、订阅管理、模块、逐脚本规则和日志分别进入子页面。
当前节点页按横向分组标签、搜索、整组测速、排序和单列节点行组织，订阅页支持两个导入按钮、点击切换和更新/编辑/复制/删除
三点菜单。逐脚本规则只展示已启用传统 JsEngine 包，并保留手动包名入口。外部 Clash 使用 Android VPN 时无需填写；内嵌
Mihomo 与系统 VPN 并存默认阻止，明确授权后才形成双层链路。完整模型、UI、策略组、首次导入、
路由矩阵、安全边界、实施计划和验收项见
[`application_network_proxy/index.md`](application_network_proxy/index.md)。最新用户指令已授权将
当前工作树全部改动提交并推送 `main`；设备安装、ADB、模拟器、Release 和部署仍不在授权内。

## 2026-08-22 全终态工具历史闭合与中断恢复

状态：`IMPLEMENTATION VERIFIED / DEVICE VERIFICATION PENDING`。

用户现场同时确认手动停止和未手动停止都可能在下一次发送时触发
`Provider tool history protocol violation [MISSING_TOOL_RESULT]`。当前根因不是 Provider
校验过严，而是实时可见流、周期持久化快照和 provider replay history 共用了尚未闭合的工具
事务：并行工具仍在执行、工具基础设施异常取消兄弟任务、意外协程取消、普通失败收尾或进程终止
时，持久化消息可能只含 assistant tool call 而没有全部 tool result。自动上下文压缩会先拒绝这段
非法历史，但普通发送仍会继续，随后在下一个 `user_boundary` 被严格协议状态机阻止。

本轮 Goal 是让所有 durable replay surface 在写入和再次消费前都满足工具事务闭合，同时保留
实时 UI 与完整审计事实：

1. 建立唯一 replay-safe 投影，按原始顺序识别带随机后缀的 `tool*` / `tool_result*`、工具名和
   可选 `provider_call_id`；只从首个未闭合或冲突事务起移除后缀，完整工具事务和此前正文不变
2. 周期流式快照只写入 replay-safe 内容；UI 继续消费原始 live stream，审计继续保存真实流事件
3. 正常完成必须证明最终工具事务闭合；普通失败、手动停止和非手动取消只持久化安全前缀，不生成
   `User cancelled`、`[Empty]` 或任何伪造工具结果，也不放宽 Provider 严格校验
4. 下一次发送和自动压缩前，先确认未闭合事务是否被后续持久消息合法闭合；直到 replay boundary
   仍未闭合的历史必须修复，不再依赖 `completedAt` 或同 timestamp 失败审计作为资格
5. 自动压缩必须在修复后的同一 durable history 上规划，提交时继续执行范围 digest、工具闭合、
   route 和相邻 summary 校验；不能建立第二份 compaction 历史
6. 自动测试覆盖纯文本、单/并行工具、部分结果、完整多轮事务、截断 XML、名称/call ID 冲突、
   周期快照、正常终态、失败/取消、旧历史修复与 compaction；随后执行正式门禁、必要回归、
   Debug APK 构建和产物核验
7. 最终审计本轮允许清单并提交到唯一 `main`，推送 `origin/main`，核对 local、tracking 与远端 ref

首轮本地验证后，真实完成回合暴露了第二个必须同批修复的缺陷：
`ToolExecutionManager` 对并行工具按完成时间向 live stream 发射结果，而首版
`AssistantReplayHistoryProjector` 只允许结果名称匹配 pending queue 队首。于是一个已经取得全部
结果并完成后续模型回复的合法回合，会在最终消息落库前以
`TOOL_RESULT_NAME_MISMATCH, pending=4` 被误判，用户看到回复结束后整条对话消失。修订后的投影
必须按 `provider_call_id` 或协议工具名把结果匹配到整组未决调用，并只在 durable replay 副本中
把完整结果规范为原调用顺序；live stream 和 immutable audit 继续保留真实完成顺序。结果 XML
用 `provider_tool_name` 区分原始 invocation 名与 UI 展示名，用 `provider_result_terminal` 排除
同一工具 Flow 的 start/chunk 等中间结果；即时拒绝和实际执行结果也在交回 Provider 前按原
invocation 顺序合并。完成路径保存规范化结果，不能只做检查后继续写回原始错序正文。

OpenAI Responses 的 `message_provider_states` 已完成专项核对：其中的 `outputItemsJson` 只用于
同一个远端 response 的 sequence 续接，`functionCallOutputsJson` 当前恒为 `[]`；普通下一轮请求
和 compaction 都没有读取该表，仍从 `ChatMessage` 编译历史。因此 M7 不改 typed provider state，
避免把 execution 恢复账本错误变成第二份跨轮历史 owner。

第二次现场复测又暴露 `MISSING_TOOL_RESULT, pending=1 at history_end`。源码与现有反向测试共同证明：
恢复策略会拒绝修复 `completedAt != 0` 且缺少同 timestamp 失败审计的未闭合旧消息；当该消息位于
历史末尾时，OpenAI 编译器排入 tool call 后没有后续 tool result，必然在 `history_end` 失败。本次
发送失败的审计属于新回合，不能为旧消息补足相同 timestamp 资格。新修正保留后续消息闭合检查，
但对直到 replay boundary 仍未闭合的事务直接建立安全 revision；`completedAt` 和终态审计只保留
为诊断事实，不再阻止修复。跨消息闭合严格复用请求编译语义：只有 `ai` 消息中的结果标签可成为
`TOOL_RESULT`，普通 `user` / `summary` 正文中的工具样式 XML 不能作为闭合证据；参与合法闭合的
后续 AI 结果消息与原调用消息共同保留，FIFO 顺序、工具名或 call ID 冲突仍按损坏历史修复。

最终反向审查进一步收紧这项跨消息保留：source message 自身必须已经满足 Provider FIFO，不能
包含流式中间结果，也不能存在尚未应用的结果重排或协议名规范化；因此
`call A, call B, result B` 后接 `result A` 仍会进入安全修复。旧 `package_proxy / proxy` 的结果
与调用在同一消息时，可以从调用参数证明真实协议名并补写 `provider_tool_name`；跨消息结果不能
原地改写，展示别名不作为闭合证据。

“插入总结”的切片压缩与通用自动压缩都等待活动回合并执行同一历史修复；单条消息重新生成在
保存新 variant 前执行 `regeneration_completion`；`ConversationService` 把同一并行调用组的连续
`TOOL_RESULT` 合并为一个 Provider turn。OpenAI 请求在 `call.execute()` 前因请求体编译或本地
工具历史校验失败时原样抛出，不再进入五次网络重试或伪装成连接超时；已经开始 HTTP 提交的失败
仍遵守既有传输重试与 at-most-once 合同。

提交前反向审查又确认了一条无需取消即可复现 `MISSING_TOOL_RESULT` 的独立路径：当前 follow-up
把多个工具结果限制在 `64000` 字符，旧实现遇到第一个放不下的结果就 `break`，外层还可能对整段
XML 直接按字符截断。四个大结果因此可能只提交第一个，严格状态机会在下一个 user boundary 报
`Missing 3 tool results`。新合同先为每个真实结果预留完整 XML 信封，再公平分配剩余 payload
预算；结构最小值超限时本地显式失败，不丢结果、不截断 XML、不伪造结果。

最终候选的完整 `:app:testDebugUnitTest` 为 `291 suites / 1715 tests`，
`failures/errors/skipped = 0/0/0`；`:app:compileDebugAndroidTestKotlin`、formal readiness、
architecture `phase=m03` 与差异检查通过。规定的 `:app:assembleDebug` 为
`BUILD SUCCESSFUL in 47s`，232 个任务中 22 个 executed、210 个 up-to-date。Debug APK 于
`2026-08-22 20:32:57 +08:00` 写入 `app/build/outputs/apk/debug/app-debug.apk`，大小
`472737951` bytes，SHA-256
`5317969BAD123305343D957A337ABC35DC99667841C633001156CF42E02B8E2F`；包身份
`com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`、唯一 `MainActivity` launcher、Android Debug V2
单 signer（证书 SHA-256
`E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`）和 16 KB ZIP alignment
通过。APK 共 `5506` 个 file entry、`44` 个 DEX，仅含 `arm64-v8a` 的 `51` 个无重复 basename
`.so`；生产包白名单 `45/45`，其中 `12` 个为生成式 ToolPkg。shell/ripgrep 存在且不含
`libsudo.so`；加 shell 共 `52` 个 ELF64/AArch64，`153` 个 `PT_LOAD` 为
`0x4000 x 151 + 0x10000 x 2`，播放器与 FFmpegKit 的 `19/19` 个 AAR native payload 和 APK
逐字节一致。消息处理 DEX 主方法为 `25438 / 100000`，三个 continuation 为 `35 / 33 / 30`
registers，`violations=[]`；关键投影类、完成/重新生成闭合边界和 Provider 协议错误均已在最终
DEX 中检出，四并行 `40000` 字符结果通过严格 `user_boundary` 闭合，结构化预算与外层超限断言
也已进入 DEX。真实 Provider、进程强杀、手动/非手动中断交互及
自动压缩现场复测继续保持 `verification_pending`。

详细状态机、影响文件、风险、回滚点和验收矩阵继续维护在
[`unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)
的 M7，不创建平行专项。

## 2026-08-22 Search 内置脚本规范化与四家官方 API 完善

状态：`M0-M6 MAIN DELIVERED / DEVICE VERIFICATION PENDING`。

本轮从干净的 `main@e5f5f01cd25205fa37aa2cb384617d59bf912d7f` 开始，目标是规范
AI 对话左抽屉“扩展 -> 脚本”中全部 `Search` 分组内置包，并重写
`tavily_search`、`serpapi_search`、`brave_search`、`zhipu_search`。所有 Search 包 ID
必须以 `_search` 结尾、不得声明作者；四个核心包必须按 2026-08-22 官方文档覆盖正式接口，
使用逗号分隔的多 Key、进程内原子轮询、明确凭据错误换 Key，以及逐 Key 的并行连通性检查。
实时复核还确认 Brave 的 17 个方法变体及七条已弃用但仍在官方文档中的 Summarizer 端点均在
范围内；弃用能力独立暴露，不作为 Answers 的运行时降级路径。

调查已确认包说明会先进入隐藏工具目录；`use_package` 激活后，工具说明和参数说明才作为真实
`packageName:toolName` 契约注入。因此元数据将按“包级选择说明 + 工具级调用说明 + 参数级约束”
分层，避免把面向人的教程和营销文案塞入模型提示词。内置脚本由 4 个 `JsEngine` 池调度，
模块局部游标不能证明全局轮询；实现必须使用共享原子轮询所有者，不得把单引擎计数伪装成完成。

M2-M4 已完成：八个 Search 包 ID 均以 `_search` 结尾且无作者字段；四个核心脚本覆盖冻结的
官方接口矩阵、逗号多 Key、进程级原子轮询、固定 Key 后续请求和逐 Key 并行检查。最终生成 JS
的真实官方 API 矩阵为 `47/47 accepted`，连通性为 Tavily `14/14`、SerpApi `4/4`、Brave
四类 `4/4`、智谱 `1/1`；专项 JVM 测试 `7/7` 通过。Tavily Logs/Organization Usage 与
Brave 已弃用 Summarizer 的权限或业务响应不等于功能成功，精确状态记录在专项文档。Debug APK
已完成规定构建与独立静态审计，大小 `472737951` bytes，SHA-256
`BDF7DFB3935FB305A804FC4F4A29B76539E258AA7D22F79C1B611FA858F85555`。
实现提交 `bb5715028768b43e7f42f1aa1f88d6a9ed4410b8` 已推送到 `origin/main` 并完成三方 ref
对账。抽屉显示和代表性模型现场调用保持设备待验收。

详细范围、接口矩阵、多 Key 状态机、阶段计划与验收规则见
[`search_script_standardization/index.md`](search_script_standardization/index.md)。临时 API Key 只允许
进入真实测试进程，不得写入仓库、文档、日记、构建产物或提交。

## 2026-08-21 DeepSeek、Responses、长上下文缓存与对话统计优化

状态：`M1-M5 LOCAL VERIFIED / M6 LOCAL VALIDATION COMPLETE / ENDPOINT AND DEVICE VERIFICATION PENDING`。

本轮用户要求深度研究 `D:\01_Environment\Apps\DeepSeekHarness` 及 DeepSeek 官方项目，
优化 DeepSeek、Responses、多协议适配、长上下文连续对话、工具调用、自动上下文压缩和 AI
对话顶栏统计。研究与详细、可恢复实施方案已写入
[`unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md`](unified_model_capability_and_resumable_execution/6_deepseek_long_context_cache_and_usage_plan.md)；
实现不得把本地 Token 估算伪装成 provider cache 命中，也不得为缺失工具结果生成伪造文本。
M0 研究与方案已冻结；M1 已完成 provider usage snapshot、同 hop 去重、OpenAI/DeepSeek/
Anthropic/Gemini/ToolPkg usage 解析接入，以及 Room 22→23、消息/变体/归档和 active-chat
恢复链。8 个定向 JVM suite 共 `35` tests 全部通过，AndroidTest Kotlin 编译通过；阶段
Debug APK 为 `472736006` bytes，SHA-256
`DBFE3F454BD36A1C588CBE0F9E8DD53F50F75360E6FCD253A1D3A2AC389FB564`，并通过唯一
Launcher、Android Debug V2 单 signer、16 KiB zipalign、arm64/native/runtime packaging
与 DEX continuation 门禁。Room 22→23 instrumentation、真实 provider endpoint 与设备 UI
仍为 `verification_pending`。

M2 已完成本地实现与自动验证：新增严格 provider tool history state machine；DeepSeek 与
OpenAI Chat/共享 bridge 不再注入 `User cancelled` 或 `[Empty]` 来补协议历史；原始
`provider_call_id`、reasoning、并行调用顺序和结果顺序得到保留；DeepSeek 流式请求固定发送
`stream_options.include_usage=true`；模型参数、工具、schema 和请求 JSON 使用稳定排序/
canonicalization，重名与冲突显式失败。5 个定向 JVM suite 共 `34` tests 全部通过；
AndroidTest Kotlin 编译通过。M2 阶段 Debug APK 为 `472736006` bytes，SHA-256
`5F9372567E0F01CC2BC8D2CE7D77710720D676ABD1097DEA45DD862CDDF733EF`；独立审计确认
`5504` 个 ZIP entry 无重复、`44` 个 DEX、仅 `arm64-v8a`、`51` 个 `.so` basename 无重复，
加 shell 共 `52/52` 个 ELF64/AArch64，`153` 个 `PT_LOAD` 为
`0x4000 × 151 + 0x10000 × 2`，Debug V2 单 signer、16 KiB zipalign、唯一 Launcher 与
DEX continuation 门禁均通过。M2 完成后按计划进入 M3；该阶段尚未修改顶栏 UI 与自动压缩。

M3 已完成本地实现与定向自动验证：DeepSeek Responses 使用 replay-only capability，不获得
OpenAI 官方 `previous_response_id`、background、encrypted reasoning 或
`prompt_cache_key`；OpenAI Responses 的 `reasoning`、`function_call` 和
`function_call_output` 保持 typed history 顺序与稳定 call ID；Anthropic 完整保存并重放
thinking/signature、redacted thinking、tool_use/tool_result content blocks；Gemini 完整保存
原始 Part 顺序与 camelCase `thoughtSignature`，并对多 function call/result 做严格数量、顺序
和名称检查；ToolPkg usage 负值或畸形字段标记为 invalid，不伪装成真实 0 命中。provider
业务链中已无 `User cancelled`、`[Empty]` 或工具结果截断。Gemini 响应 replay 状态已改为
每个 HTTP attempt 局部持有，provider 实例复用和 retry rollback 不会混入旧 Part metadata。
11 个定向 JVM suite 共 `54` tests 全部通过，失败、错误和跳过均为 0；AndroidTest Kotlin
编译和阶段 Debug APK 构建通过。M3 APK 为 `472736006` bytes，SHA-256
`D733CAB690B0E65B0419A64B9AA2F8E97C1FF7E62D1BB28ACAA917784E23A6B8`；唯一 Launcher、
Debug V2 单 signer、16 KiB zipalign、`5504` 个无重复 ZIP entry、`44` 个 DEX、仅
`arm64-v8a`、`51` 个无重复 `.so` basename、12 个生成式 ToolPkg、`52/52` 个 AArch64 ELF
和 DEX continuation 门禁均通过。M3 完成后按计划进入 M4 长上下文自动压缩。

M4 已完成本地实现与自动验证：自动与手动压缩共用强类型 snapshot/commit 合同，选区必须位于
完整工具事务边界；提交前重新校验 chat、范围锚点、历史 digest、工具闭合、有效模型 route 和
相邻 summary，过期结果只记录拒绝原因而不写入历史。summary checkpoint 只保存清理后的纯文本，
provider-private replay metadata、空摘要和工具标记均显式失败；超大工具结果使用保留标签、
名称、状态与 call ID 的 head/middle/tail 投影，并把省略摘要与哈希写入既有对话审计链。

M5 已完成本地实现与自动验证：Room 版本升级到 24，22→23 保存 provider usage projection，
23→24 增加 `providerCacheMetricPromptTokens` 并把无法证明分母的旧 cache metric count 重置为
未报告。缓存命中率只使用明确报告 cache metric 的 provider prompt 分母；Waifu 分段、消息/
变体/聊天、归档、Parcelable、偏好和 active-chat 恢复链均保留该字段。AI 对话顶栏继续消费唯一
`TokenStatisticsDelegate`，显示上下文估算、provider hop、输入/输出、缓存读写、命中率、
usage/cache 覆盖、推理与总计；未知与明确 0 命中保持可区分。七份语言资源新增 12 个统计 key，
XML 和占位符集合一致。

M4/M5 的 8 个核心 JVM suite 共 `41` tests 全部通过；完整
`:app:testDebugUnitTest` 为 `284` suites / `1658` tests，失败、错误和跳过均为 `0`；
AndroidTest Kotlin 编译、formal readiness、architecture `phase=m03`、`git diff --check` 和
七语言资源差分检查通过。最终 `:app:assembleDebug` 为
`BUILD SUCCESSFUL in 2m2s`，`232` 个任务中 `26` 个 executed、`206` 个 up-to-date。
Debug APK 为 `472737774` bytes，SHA-256
`2858892B6B60D4DA1B5A154984196751540ACF18478AEB49F7CFAC131412DC9B`；
`com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 Launcher、
Android Debug V2 单 signer、16 KiB zipalign、`5504` 个无重复 ZIP entry、`44` 个 DEX、
仅 `arm64-v8a`、`51` 个无重复 `.so`、12 个 ToolPkg 与 `52/52` 个 AArch64 ELF 均通过审计。
真实 provider endpoint/cache 命中、Room instrumentation、网络/进程恢复和设备 UI 仍为
`verification_pending`；本轮未提交、未推送、未安装或操作设备。

## 2026-08-21 协议识别、供应商抽屉与 Codex 推理摘要后续修复

状态：`IMPLEMENTATION AND AUTOMATED VALIDATION IN PROGRESS / DEBUG APK AND MAIN DELIVERY PENDING`。

本轮在已推送的供应商/协议基础重构之上继续推进：用户已明确要求彻底删除“API 协议自动
识别”，把当前仓库全部底部 `ModalBottomSheet` 统一为浏览器书签、历史和下载共用的三态
抽屉，并修复 DeepSeek 及其他 Responses 供应商工具调用中
`function_call` / `function_call_output` 被普通消息拆开的共享协议错误。

详细设计与唯一后续验收矩阵见
[`api_provider_configuration_redesign/9_auto_detection_removal_drawer_unification_and_responses_tools.md`](api_provider_configuration_redesign/9_auto_detection_removal_drawer_unification_and_responses_tools.md)；
专项总状态见
[`api_provider_configuration_redesign/index.md`](api_provider_configuration_redesign/index.md)。

当前任务基线：`main@a94d7e8a363202f5ec4a1dbc00417343d8d403ea`，本地与
`origin/main` 分歧为 `0/0`，工作树在首次写入前干净。当前状态为方案完成、实现进行中；
上一轮 Kotlin/JVM/APK/Git 数字是历史交付证据，不能作为本轮完成证据复用。

## 2026-08-21 模型与 API 供应商及协议重构

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND MAIN DELIVERY COMPLETE / DEVICE VERIFICATION PENDING`。
本轮承接 `main@d802052b` 的干净基线，已把模型配置中的供应商身份、协议、端点和模型能力
收敛为一套可验证的选择模型。Kiyori 尚未公开发行，因此允许清理重复的用户可见供应商入口；
`ApiProviderType` 旧值、`apiProviderTypeId`、审计键和既有备份字段在确认无运行时用途前继续保留。

用户复核后要求删除“主流供应商”分类，将 OpenAI、Anthropic 放在国际供应商顶部、DeepSeek
放在国内供应商顶部，并逐一研究所有供应商可选协议和统一命名；“自动识别”只在配置阶段
解析并保存具体协议，不会在运行时失败后隐藏切换协议。当前完整 JVM 回归为 `258` 份 JUnit
XML、`1534` tests、零失败/错误/跳过，formal readiness、fresh clone 和 `git diff --check`
均通过；Debug APK、APK/ELF 静态审计、main 精确审计、提交推送和远端 ref 对账已完成。

详细方案与阶段状态见
[`api_provider_configuration_redesign/`](api_provider_configuration_redesign/index.md)。

## 2026-08-20 AI助手设置全面重构

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND MAIN DELIVERY COMPLETE / DEVICE VERIFICATION PENDING`。
本长期任务在 `main@5a63f2b59074dbc711c715e3f7a294827c035bb9` 的干净工作树开始，
`origin/main` 与本地分歧为 `0/0`，正式开发准备门禁通过。Kiyori 尚未公开发行，因此允许彻底
清理 AI助手设置仍保留的 Operit 旧页面方案；模型、角色、语音、上下文、工具授权、统计与外部
接口的既有 repository、preferences、service 和 `Screen` route 继续作为唯一状态与功能 owner。

### 已确认问题

- `KiyoriApplicationSettingsPages.kt` 的 AI助手根页使用硬编码中文，非中文环境仍显示中文；
  页面标题、根入口标题与 `Screen.titleRes` 还存在多份文案来源。
- 根页当前为六组十四项，模型基础配置位于第三组；“人设卡生成”同时作为根级入口和
  “角色卡编辑”内部动作出现，层级重复；“Waifu模式设置”“外部 HTTP 调用”“Token使用统计”等
  Operit 术语没有表达用户真正要配置的行为、风险或使用频率。
- 虚拟形象、语音唤醒、TTS 与 STT 已接入 Kiyori Settings Surface；模型/API、功能模型、用户资料、
  提示词与角色、角色生成、分句回复、上下文、工具授权、用量统计、外部接口以及 MNN、表情和标签
  子页仍混用 `CustomScaffold`、独立标题、旧卡片、Toast、浮动重置按钮和页面内重复标题。
- 多个页面声明 `onBackPressed` / `navigateBack` 却不在页面自身消费；复杂设置依赖外层 Operit
  TopAppBar，与已经迁入设置内部标题的页面形成两套视觉和导航结构。
- 功能模型“重置全部”没有确认；外部接口令牌明文常驻且重置令牌没有确认；Token 统计读取聊天数量
  时静默吞错；工具授权搜索无结果没有明确状态；角色生成首屏文案按进程 Locale 手写中英分支。
- 角色、标签、群组、导入导出、市场、生成和编辑全部堆叠在一个超长页面；成功/失败反馈同时混用
  Toast、临时底部卡片和 Snackbar，按钮层级与破坏性动作表达不一致。

### 冻结后的信息架构

AI助手根页按用户决策频率固定为五组十三项：

1. `模型与生成`：`模型与 API / 功能模型分配 / 提示词与角色`
2. `个性化与交互`：`用户资料 / 虚拟形象 / 回复与表情`
3. `语音`：`文本转语音 / 语音转文本 / 语音唤醒`
4. `上下文与工具`：`上下文与总结 / AI 工具授权`
5. `服务与用量`：`AI 用量与费用 / 局域网与自动化`

“人设卡生成”不再占用根级重复入口，继续通过“提示词与角色”页面的明确主操作进入；其
`PersonaCardChatHistoryManager`、角色卡写入和模型调用链不变。所有根页分组、标题、说明和条目文案
改为 Android string resource，七份当前语言资源保持键集合一致。

### 页面与弹层合同

- 设置根页“设置”使用同一标题组件的固定模式；其他简单设置页继续使用 `KiyoriCollapsingSettingsPage`。
- 模型管理、角色编辑、统计、外部接口和其他长表单/工作台页面使用新的 Kiyori 紧凑设置工作台：
  页面自己持有与简单设置页相同的折叠标题，页面滚动时标题从 `128dp` 收缩到 `56dp`；同时统一
  Settings 背景、`16dp` 卡片、无阴影层级、底部安全区、Snackbar 与可选页面操作，不再显示外层
  Operit 设置 TopAppBar。
- 普通导航、开关、选择和值展示继续复用 `KiyoriSettingsRow`、`KiyoriSettingsSelectionSheet`
  和 `KiyoriSettingsTheme`；复杂表单复用同一字段、信息提示、空状态、按钮和对话框 token。
- 普通主操作使用 Filled/Tonal 层级，次操作使用 Outlined/Text；删除、重置、清空历史、重置令牌等
  破坏性动作必须有明确确认且只在确认后执行。
- API Key、Bearer Token 等敏感字段默认遮蔽，提供显式可见性按钮；复制、保存、连接测试和重启服务
  分开表达，未保存输入不能伪装为运行中配置。
- 失败必须通过日志和可见 Snackbar/错误状态表达；不静默吞错，不新增回退、兜底、平行路由或
  第二状态 owner。

### 分里程碑实施

1. [DONE] 增加 AI助手本地化资源、五组十三项根页、共享紧凑设置工作台和静态合同测试。
2. [DONE] 迁移用户资料、AI 工具授权、AI 用量与费用、局域网与自动化及其弹窗/按钮；
   修复确认、敏感字段、错误反馈和空状态问题。
3. [DONE] 迁移模型与 API、功能模型分配、上下文与总结、MNN 模型下载；统一配置选择、
   连接测试、自动保存、批量重置和模型能力提示。
4. [DONE] 迁移提示词与角色、角色生成、回复与表情、自定义表情、标签模板；收敛顶栏、标签页、
   排序菜单、编辑器、导入导出、确认弹窗和成功/失败反馈。
5. [DONE] 复核虚拟形象、语音唤醒、TTS、STT 及其最小弹层，统一字段、按钮、状态文案和
   依赖禁用态，不改变语音和 Avatar runtime。
6. [DONE] 同步 `CONTEXT.md`、设置专项、string resource、路由标题和测试；反向检查旧根入口、
   用户可见 `Waifu` 标题、硬编码 AI助手中文和独立 Operit 设置顶栏。
7. [DONE] 按风险执行定向 JVM/Kotlin、architecture、formal readiness、Markdown/XML/
   localization 和 `git diff --check`。
8. [DONE] 串行执行 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，核验 APK
   身份、时间、大小、SHA-256、签名、16 KB 对齐、ABI 与关键 runtime。
9. [DONE] 审计精确候选树、敏感内容、构建产物、文件模式、子模块和远端竞争，提交并正常推送
   `main`，随后独立核对 local、tracking、远端 ref 与分歧。
10. [PENDING DEVICE] 目标设备逐页验证浅深主题、字体缩放、IME、滚动、弹层、Back、表单保存、
    连接测试、权限、语音、角色编辑和外部接口；本任务不安装或操作设备。

### 当前本地实现

- 根页已固定为五组十三项；根入口、分组、说明和页面标题全部使用 Android 资源，七个语言目录
  保持同一键集合；“人设生成”只保留在“提示词与角色”内部。
- 新增 `KiyoriSettingsWorkspacePage`，统一长表单/编辑器/统计页的不透明安全区折叠标题、页面背景、
  Snackbar、底部安全区和可选页面操作；标题通过页面自身滚动容器的嵌套滚动从 128dp 收缩到 56dp，
  与文本转语音页面使用同一标题帧，相关 Operit route 继续使用原 route ID 和唯一状态 owner。
- 用户资料、模型/API、功能模型、上下文、工具授权、用量、局域网服务、提示词/角色、人设生成、
  回复/表情、自定义表情、标签模板和本地模型下载已迁入统一 Settings Surface。
- 模型配置删除、功能模型全部重置、历史媒体重置、令牌重置、个人唤醒模板清除、自动附件删除、
  Avatar 模型/自定义情绪删除及统计重置均使用明确确认；用户资料“清空”文案已与实际默认模板行为一致。
- 外部服务令牌和语音 API Key 默认遮蔽；工具与自动附件稳定排序；工具搜索和本地模型提供明确空态；
  自动新对话分组按真实开关禁用，语音附件窄屏布局不再受旧固定高度截断。
- 聊天数量、自动保存、模型列表/音色、Avatar 预览、个人唤醒录音和语音设置写入异常均记录日志并
  进入 Snackbar 或页面错误状态；个人唤醒录音异常会释放录音状态，不会卡在“录音中”。
- 模型自定义请求头的持久化 JSON 损坏时进入显式不可编辑状态，保留原始配置并停止注册保存与
  自动保存；页面显示稳定修复说明，不再把解析失败伪装成空请求头并覆盖原数据。
- 自定义表情只允许删除非内置分类；角色、群组、标签、ActivePrompt、Avatar、语音和服务状态仍由
  原 repository/preferences/runtime 持有，本轮没有新增平行状态、协议或数据源。

### 当前自动化验证证据

- `check_architecture_boundaries.py --require-main --phase auto`：`PASS (phase=m03)`；ARCH040
  M-05A1 的 Settings theme consumer snapshot 已同步本轮真实新增消费者，未扩大 ownership 或
  design declaration owner。
- CI Python：`220/220`；`check_formal_readiness.py --require-main`：`PASS`；`test_toolpkg_sync`：
  `7/7`；新增 83 个资源键在七份 `strings.xml` 中均为 XML 可解析且各出现一次。
- AI 设置范围的变更 Kotlin 文件无 `Toast` / `CustomScaffold`，根级 `OPEN_PERSONA_GENERATION`
  无引用；`git diff --check` 通过；定向 `KiyoriSettingsPagesTest` 与 `compileDebugKotlin`
  同一 Gradle 命令 `BUILD SUCCESSFUL in 1m 13s`，测试 `18/18`。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 2m 45s`，`232` 个任务中 `22` 个 executed、`210` 个 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过。
- 当前 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，文件大小 `472726918` bytes；
  构建主机记录的产物时间为 `2026-08-21 07:05:48 +08:00`，仅用于产物识别，不用于判定当前
  会话日期；SHA-256 为
  `E8908268C207EF71FF397947DB2C7C8DF9D2531B55E1EF6FE90B70681E4F27FA`；身份为
  `com.kiyori / 0.1.0 (45) / min 26 / target 34 / compile 37`。
- APK 只有一个 Launcher `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2
  单 signer、`zipalign -c -P 16 -v 4` 通过；`5504` 个 ZIP entry 无重复，`44` 个 DEX，只有
  `arm64-v8a`，`51` 个 `.so` 的 basename 无重复，并包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不含 `libsudo.so`。
- 独立 native 审计确认共 `52` 个 ELF64/AArch64，`153` 个 `PT_LOAD` 为
  `0x4000 × 151 / 0x10000 × 2`，无低于 `0x4000` 的段；生产 ToolPkg 白名单为 43 条源条目，
  APK 生成 12 个 `.toolpkg` 档案、嵌套 143 个文件，另含复制型白名单脚本，内容同步门禁通过。
- 实现提交 `9da03846749305cadeb6860ee6f8043a5d57a2d2` 已正常推送到 `origin/main`；推送后
  local、tracking 与远端 `refs/heads/main` 三者一致，分歧为 `0/0`。

### 风险、回滚点与完成标准

- 路由风险：所有 `Screen` 与 `RouteEntrySource.KIYORI_SETTINGS` round-trip 必须保持；每个里程碑
  以当前 `main` 提交或干净基线作为回滚点，不以隐藏旧页面作为回滚方案。
- 状态风险：不得复制 `ModelConfigManager`、`FunctionalConfigManager`、`UserProfileDocumentRepository`、
  `CharacterCardManager`、`WakeWordPreferences`、`ToolPermissionSystem`、`ApiPreferences` 或
  `ExternalHttpApiPreferences`。
- 编辑器风险：用户资料、角色卡、提示词和外部接口存在未保存输入、IME 与滚动状态；Back 必须先处理
  当前页面真实未保存状态，再交还现有 Router/Settings session。
- 安全风险：令牌、API Key、导出配置和局域网监听必须清晰提示暴露范围；UI 不记录或提交真实值。
- 完成标准：五组十三项顺序、所有可达最小页面和弹层、功能逻辑缺陷、文档与自动验证全部闭环；
  最终 Debug APK 和远端提交对账完成。没有目标设备实测时终态保持 `verification_pending`。

详细页面矩阵与视觉/状态所有权见
[`kiyori_settings_information_architecture`](kiyori_settings_information_architecture/index.md)。

## 2026-08-20 权限中心重构与 App Router 顶栏残影根治

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。
Kiyori 尚未发布，本轮允许彻底替换 Settings 权限入口的旧页面方案，不保留并行权限首页。
首次启动和 Settings 共同消费
`KiyoriPermissionId / KiyoriPermissionSnapshot / KiyoriOnboardingPermissions`
作为唯一设备权限目录、真实状态与授权动作来源；`ToolPermissionSystem` 继续只负责 AI 工具授权。

### 已确认根因

- `AppContent` 的缓存内容已经按 `currentScreenKey` 隔离，但 `KiyoriApp` 仍用一个全局
  `topBarActions` 和 `topBarTitleContent` 槽位接收所有保活页面的顶栏注册。route 更新后，
  新页面首帧会先读到旧 AI Home 的四个 actions，随后 `LaunchedEffect(currentScreen)` 才尝试
  清空；AI Home 和 TokenConfig 的特殊保留条件还会进一步扩大旧值生命周期
- `LocalIsCurrentScreen` 只能约束各页面何时注册，不能证明全局槽位属于当前 route。保活页面、
  `LaunchedEffect` 调度和同帧导航组合后，旧页面的晚到注册仍可能覆盖新页面
- Settings 的“权限”当前通过 `RouteEntrySource.KIYORI_SETTINGS` 打开
  `Screen.ShizukuCommands`。这会离开 Settings route surface，进入独立 App Router 顶栏和缓存层，
  因而直接暴露上述跨 route 顶栏竞态
- `ShizukuDemoScreen` 的全页“正在加载应用状态...”同时等待 Root、Shizuku、无障碍和
  `DemoStateManager` 初始化；后者还重复创建/取得 MCP Terminal 会话并探测 Node、Python、pip
  环境，包含重复刷新和固定 `300ms` 延迟。设备权限首屏不应依赖这些开发执行环境检查
- 首次启动第六页已经覆盖 21 项真实设备权限、批量 Android runtime permission、特殊系统访问、
  无障碍 provider、Shizuku、Root 和使用时屏幕捕获；当前问题不是缺少能力，而是这些能力被封装
  在 onboarding 私有 UI 中，Settings 仍指向旧 Demo 页面

### 目标合同

- `AppContent` 内每个缓存 screen key 独立持有 actions/title；TopAppBar 只读取当前 key。
  旧 AI Home 即使继续组合、保活或晚到更新，也不能向任何新 route 投影顶栏内容
- 删除基于 `currentScreen` 的延迟清空策略，不增加遮罩、延迟、预清空点击回调或其他时序补丁
- 新增 `KiyoriSettingsRoute.PERMISSIONS`。路径固定为
  `Settings Home → More Features → Permissions`，Back 逐级原路返回；不进入 Operit Router，
  不改变 AI route stack、Browser WebSession 或 Settings session
- 权限中心首帧立即显示标题、总览、全部权限分组和已有 snapshot，不显示全页加载占位。
  手动刷新和从系统页面恢复时只原位更新状态
- 权限中心分为“应用权限”“系统访问”“高级设备能力”三组，覆盖 onboarding 的全部 21 项；
  每项显示唯一 metadata、真实状态、作用说明和与状态匹配的动作
- `GRANTED / PARTIAL / NOT_GRANTED / REQUIRES_SETUP / NOT_APPLICABLE / ON_DEMAND`
  保持同一语义；不把无需授权或使用时确认伪装成可授予
- Android runtime permissions 通过唯一 `RequestMultiplePermissions` launcher 请求；特殊访问进入
  对应系统页；无障碍、Shizuku、Root 复用既有真实动作；屏幕捕获明确说明由 Android 在使用时确认
- 权限状态不持久化为第二份结果，不复制 `ShizukuDemoViewModel` 或 `DemoStateManager`；
  Settings 和 onboarding 每次都从同一真实系统事实重新生成 snapshot
- `Screen.ShizukuCommands / ShizukuDemoScreen` 暂保留为独立的执行通道、命令和开发诊断页面，
  但不再充当 Settings 权限首页，也不再影响该入口的首屏性能

### UI 与交互方案

- 折叠标题使用“权限与设备能力”，右侧提供非阻塞“重新检查”
- 顶部总览卡展示已就绪、待处理、使用时确认三类数量和真实进度；刷新时保留当前内容，只显示
  小型刷新状态
- 分组卡沿用 `KiyoriSettingsTheme` 的背景、文字、分隔线和语义图标色；权限状态使用紧凑标签，
  不使用旧 Demo 的横向权限级别选择器或命令执行网格
- 未授权/部分授权的 runtime permission 显示“授权”；特殊访问显示“前往设置”；
  无障碍、Shizuku、Root 显示与当前安装/运行/授权阶段相符的动作；已授权项仍允许进入系统管理页
  的，仅显示“管理”，不能管理的显示稳定状态
- 页面生命周期恢复前台时刷新真实状态；一次动作执行期间只锁定对应交互，Back 与其他条目保持
  可预测，不以全页 loading 阻塞

### 实施与验收计划

1. [DONE] 复核 `main`、工作树、正式开发门禁、补充截图、顶栏注册和旧权限页初始化调用链
2. [DONE] 冻结顶栏 route owner、Settings route、共享权限 owner、旧 Demo 边界与性能方案
3. [DONE] 将 App Router 顶栏状态移入 `AppContent`，按缓存 screen key 登记和读取，
   删除 `KiyoriApp` 的全局顶栏槽位与延迟清理 Effect
4. [DONE] 抽取 onboarding 的权限 metadata、状态标签与分组目录为共享 presentation
5. [DONE] 新增 `KiyoriSettingsRoute.PERMISSIONS` 和 Settings 权限中心，接入 runtime、
   系统设置、无障碍、Shizuku、Root 与生命周期刷新
6. [DONE] 将 More Features 的权限入口改为 Shell route；删除该入口的
   `openKiyoriSettingsRoot(Screen.ShizukuCommands)` 桥接
7. [DONE] 增加顶栏旧 owner 隔离、Permissions route push/pop/save-restore、21 项共享目录、
   分组/计数/动作策略和 Settings 首屏不依赖 Demo/Terminal/MCP 的回归测试
8. [DONE] 同步 Settings、onboarding、产品 Shell 架构文档与所有受保护架构快照
9. [DONE] 运行定向 Kotlin/JVM、architecture、formal readiness、Markdown、
   XML 和 `git diff --check`
10. [DONE] 串行执行 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，
    核验 Debug APK 身份、签名、对齐、ABI、关键 runtime 与哈希
11. [DONE] 精确审计候选树与 staged allowlist、敏感内容、构建产物、文件模式、gitlink 和
    远端竞争，形成唯一 `main` 提交候选；最终 commit/push 结果以本轮 Git 证据为准
12. [PENDING DEVICE] 使用新 APK 真机复测无四图标残影、首屏速度、21 项状态/动作、
    系统页返回刷新、Back、浅深主题、系统栏和字体缩放

### 本地验证证据

- 最终 package 状态的 `:app:compileDebugKotlin` 在 `1m48s` 内通过
- 定向 JVM 五套测试合计 `119/119`，零失败、零错误、零跳过：
  `KiyoriSettingsTransitionPolicyTest` `9/9`、`KiyoriSettingsPagesTest` `16/16`、
  `KiyoriShellStateTest` `74/74`、`KiyoriOnboardingContractTest` `15/15`、
  `KiyoriOnboardingPermissionsTest` `5/5`
- architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、
  `check_formal_readiness.py --require-main` 和 `git diff --check` 通过
- 最终 `:app:assembleDebug --no-daemon --console=plain` 在 `2m14s` 内通过，
  `232` 个任务中 `23 executed / 209 up-to-date`；唯一 Launcher 与播放器 runtime packaging
  Gradle 门禁通过
- `app/build/outputs/apk/debug/app-debug.apk` 写入于 `2026-08-21 03:38:23 +08:00`，
  大小 `472649202` bytes，SHA-256
  `751D1C770BE9357E35A471CD89C0216238F6B86780E671FAF512E408104542FA`
- APK 为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，仅含 `arm64-v8a`
- Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过；51 个 `.so` 无重复路径或
  basename，加上 `assets/operit_shell_exec` 共 52 个 AArch64 ELF，153 个 `PT_LOAD`
  为 `0x4000 × 151 / 0x10000 × 2`
- APK 包含 `lib/arm64-v8a/liboperit_ripgrep.so`、`assets/operit_shell_exec` 和 43 个生产
  ToolPkg 资产，不包含 `libsudo.so`

## 2026-08-20 协议入口迁移与文件管理器首页接入

状态：`LOCAL DELIVERY VALIDATED / DEVICE REVERIFY PENDING`。
Kiyori 尚未发布，本轮删除 Toolbox 中的文件管理器与协议可见 host entry，不保留并行旧入口；
复用现有 `KiyoriSettingsNavigationState`、`KiyoriShellState`、`KiyoriLegalDocumentsScreen`、
`FileManagerScreen` 和 `FileManagerViewModel` 作为唯一导航、法律文档和文件操作 owner。

目标合同：

- “设置 → 更多功能 → 用户协议与隐私政策”只读展示当前协议版本、用户协议和隐私政策，Back
  先回更多功能再回设置首页，不改变首次启动同意状态或协议正文版本
- 文件管理首页“手机存储”和设置首页“文件管理器”打开同一个 `FILE_MANAGER` Shell child；
  Settings 来源保留原会话，关闭 child 后恢复原 route
- 文件管理器页面返回关闭 child，目录向上只改变当前目录；继续复用原文件操作、SAF、AITool
  与 ViewModel 链路
- `toolbox.file_manager` 与 `toolbox.agreement` 不再显示，动态 ToolPkg Toolbox 条目保持不变

细化计划：

1. [DONE] 核对未发布边界、Git/门禁、协议与文件管理 owner、Settings/Back 和 Toolbox 注册表
2. [DONE] 清理 Toolbox 两个 host entry，增加 Agreement route 与共享 File Manager child
3. [REGRESSION] 自动检查覆盖了 Settings overlay 与 child 的状态互斥，但真机证明协议页和
   File Manager child 自身仍缺少不透明根背景与系统安全区边界
4. [REGRESSION] 既有 Settings 转场策略测试只验证了“禁止 crossfade”的策略值，没有覆盖
   `AppContent` 对保活非当前屏幕的实际透明度投影；权限页首帧仍会暴露 AI 对话及内部抽屉
5. [DONE] 同步架构快照、权威文档并通过定向 JVM、architecture、formal readiness、
   Markdown 和 `git diff --check`
6. [DONE] 串行执行 `:app:assembleDebug --no-daemon --console=plain` 并核验 Debug APK
7. [REGRESSION] 目标设备已复现法律文档透底/状态栏越界、文件管理器状态栏越界和权限页
   AI 对话抽屉残影；上一轮 APK 与自动验证不能作为当前完成证据

### 现场回归修订方案

已确认根因：

- `AppContent` 在 `allowCrossfadeForActiveTransition == false` 时，对所有缓存屏幕统一给出
  `alpha = 1f`。AI Home 为长期保活屏幕，其内部对话历史抽屉状态也随组合保留，因此进入
  `Screen.ShizukuCommands` 的首帧会把目标权限页和旧 AI 屏幕同时绘制
- `KiyoriLegalDocumentsScreen` 的根 `Column` 没有不透明背景，也没有
  `WindowInsets.safeDrawing` 内容边界；Settings overlay 本身只负责层级，不会替子页补齐
  页面背景与状态栏布局
- `FileManagerScreen` 直接作为全屏 `KiyoriShellChild.FILE_MANAGER` 组合，根 `Box/Column`
  同样未消费安全区，工具栏因此从物理顶边开始布局

修订实施与验收：

1. [DONE] 读取真机截图、历史残影修复、当前 Shell/AppContent/File Manager/法律文档实现，
   重新核对单一 owner、Back 与保活边界
2. [DONE] 修正 `AppContent` 的无 crossfade 合成规则：当前屏幕立即不透明，所有非当前缓存
   屏幕立即透明；该策略保持到下一次真实 route 变化，最终透明度绕过 tween，且不销毁 AI Home
3. [DONE] 为法律文档设置入口增加全尺寸不透明根背景和 `safeDrawing` 内容边界；两份正文、
   版本与首次启动协议 owner 不变
4. [DONE] 为共享文件管理器增加全尺寸不透明根背景和 `safeDrawing` 内容边界；页面 Back、
   目录向上、SAF、AITool 与 `FileManagerViewModel` 不变
5. [DONE] 增加无 crossfade 策略锁定、当前/缓存屏幕最终透明度、页面根与入口合同回归，
   更新受影响架构快照与正式文档，并通过定向 JVM、architecture、formal readiness、
   Markdown parser 和差异检查
6. [DONE] 串行重新构建并核验 Debug APK；旧 APK 哈希与旧测试数量仅保留为历史证据
7. [DONE] 审计精确交付树、敏感内容、构建产物、子模块和远端状态，形成唯一 `main` 提交候选；
   最终 commit/push 结果以收尾 Git 与远端 ref 证据为准
8. [PENDING DEVICE] 使用新 APK 复测三个入口、系统栏、透底、权限首帧、Back、浅深主题和字体缩放

现场修订后的本地证据：

- `KiyoriSettingsTransitionPolicyTest` `8/8`、`KiyoriSettingsPagesTest` `15/15`、
  `KiyoriShellStateTest` `74/74`、`CharacterSelectorVisualContractTest` `4/4`、
  `KiyoriDesignThemeTest` `13/13`，合计 `114/114`，零失败、零错误、零跳过
- architecture 单元测试 `109/109`、architecture boundary `PASS (phase=m03)`、
  `check_formal_readiness.py --require-main`、Markdown parser `7/7` 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 47s`，`232` 个任务中 `22 executed / 210 up-to-date`；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- 最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-08-21 02:11:54 +08:00`，大小 `472649202` bytes，SHA-256
  `3CDD831C5471E9755D67D759EDAE6D3FBA431CF5A4696C1DDCC2E772FC6C34D4`
- APK 为 `com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37`，唯一 Launcher
  `com.ai.assistance.operit.ui.main.MainActivity`，仅 `arm64-v8a`；`51` 个 `.so`、
  `5504` 个 ZIP entry 且无重复，包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，Android Debug V2 单 signer 和
  `zipalign -c -P 16 -v 4` 均通过
- 32 个允许文件精确暂存，未暂存/未跟踪、敏感形状、大 blob、文件模式和 gitlink 异常均为零；
  候选 Markdown 为 `errors=0 / warnings=0`
- 未安装 APK、未操作设备；新 APK 真机复测继续保持 `verification_pending`

受保护架构快照按本轮真实 owner 变化同步：

- ARCH021 `m04b-shell-state-sha256.txt`：
  `412F5C0F9C5FA00BF0F031B87E2FFFCE69129670E101867FB5922B471B0DB712` →
  `09F37ED34C7B7FFFBDAF0AC37314F4C74F9EBEF5B9AE65B2E09202B5E76D5A91`，原因是增加唯一
  `FILE_MANAGER` child、Settings 会话保留和 save/restore 合同
- ARCH024 `m04b-app-shell-normalized-sha256.txt`：
  `82FB551F21437183E68850D84B8F7CD99894DF10E35CC6B0B63DFD58448139ED` →
  `3681ACF33E0673CE554446B50ADE3BF326DD6818013EC1FDDD95EE26F6BF5347`，并在
  `m04b-app-shell-operit-imports.txt` 中登记现有法律文档与文件管理 owner，原因是 Shell
  组合新的 Settings route 与共享文件管理 child
- ARCH026 `m04b-primary-navigation-sha256.txt`：
  `20371E7D8BB106BC326ADFE0F92D7D1D57A7CCCD37152029AA862C0479A35883` →
  `3C27FE9A909A506AF810B54E9762BC6463C3BEBB0983C668C5D4DCD89DF20192`，原因是文件管理首页与
  设置首页共同向 Shell 传递同一个文件管理打开动作

本轮本地自动验证：

- `CharacterSelectorVisualContractTest` `4/4`、`KiyoriSettingsPagesTest` `14/14`、
  `KiyoriShellStateTest` `74/74`、`KiyoriDesignThemeTest` `13/13`，合计 `105/105`，
  零失败、零错误、零跳过
- architecture `PASS (phase=m03)`、architecture 单元测试 `109/109`、
  `check_formal_readiness.py --require-main`、315 份工作树 Markdown 本地链接和
  `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 1m 48s`，`232` 个任务中 `22 executed / 210 up-to-date`；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-08-21 01:20:27 +08:00`，`472649202` bytes，SHA-256
  `CCBBE04F74981189BF47BFEEBA158E4411BC983972D1E072BC088487D604E237`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 launcher、仅 `arm64-v8a`、
  Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过
- 本轮未安装 APK、未操作设备；法律文档逐级 Back、文件管理器页面返回/目录上移、Settings
  来源恢复、真实文件操作、浅深主题和系统字体缩放继续保持 `verification_pending`

## 2026-08-20 AI助手设置入口迁移与 Settings 页面残影修复

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。
本轮复用现有
`KiyoriSettingsNavigationState`、`KiyoriApplicationSettingsPages`、`AppContent` 和
`KiyoriShellState` 作为唯一导航与状态所有者；Kiyori 尚未发布，旧的抽屉“助手配置”入口按未发布
方案清理，不保留并行旧入口或兼容性 UI。

目标合同：

- AI 对话页左抽屉删除“助手配置”，该入口不再属于 AI drawer 的可见导航目录
- 设置首页“AI助手”增加“助手体验”分组，提供“虚拟形象配置”和“语音唤醒”两个独立子选项
- 虚拟形象继续复用 `AssistantConfigViewModel`、`AvatarRepository`、现有模型导入/预览/动作映射
  与虚拟形象偏好；语音唤醒继续复用 `WakeWordPreferences`、麦克风权限和个人化唤醒录入能力
- 两个子页采用 Settings Detail Surface 的折叠标题、分组、卡片、文案、Back 与来源会话，不复制
  第二份配置状态或第二个页面 owner
- 软件首页 → 更多功能 → 权限继续打开唯一 `Screen.ShizukuCommands` owner；从 Settings-owned
  页面进入 Operit route 时不把缓存的 AI 对话页作为上一页参与跨页动画，点击后直接呈现不透明最终页
- 从 AI drawer Settings、底部 Settings、Browser 来源 Settings 进入上述页面时，Back 都按现有
  Settings session / Operit Router owner 逐级返回；不改变 AI 对话、浏览器 WebSession 或权限
 业务逻辑

细化计划：

1. [DONE] 核对未发布边界、当前 `main`、Settings session、AI drawer 目录、AssistantConfig
   功能 owner、权限 route owner 与 AppContent 转场缓存时序
2. [DONE] 确认残影根因是 Settings-owned Operit route 仍参与 AppContent 的上一页保留/动画，
   而不是复制了 AI 对话页或权限页自身创建了第二个 AI host
3. [DONE] 冻结 AI助手分组、两个子路由、唯一状态 owner、来源/Back 链和无残影转场合同
4. [DONE] 同步页面/路由/抽屉清理，拆分虚拟形象与语音唤醒 UI，并接入 source-aware
   无动画切换
5. [DONE] 增加设置页结构、路由 round-trip、残影转场策略和旧入口零引用回归
6. [DONE] 执行 Kotlin 编译、定向 JVM、formal readiness、`git diff --check` 与串行
   `:app:assembleDebug --no-daemon --console=plain`，核验 Debug APK
7. [DONE] 审计候选树、敏感内容、构建产物、子模块与远端竞争，确认唯一 `main` 可按当前授权发布
8. [PENDING DEVICE] 真机视觉、点击时序、系统 Back、设置来源恢复、麦克风权限和个人化唤醒
   录入保持 `verification_pending`

本地证据（截至 2026-08-20，Asia/Shanghai）：

- `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`。
- 定向 `:app:testDebugUnitTest`：`KiyoriSettingsPagesTest 14/14`、
  `KiyoriShellStateTest 72/72`、`KiyoriSettingsTransitionPolicyTest 5/5`，合计
  `91/91`，零 failure/error/skip。
- `ci.test.test_architecture_boundaries`：`109/109`，`check_architecture_boundaries.py
  --require-main`：`PASS (phase=m03)`。
- `check_formal_readiness.py --require-main`、七份 `strings.xml` XML 解析、旧入口零引用
  检查和 `git diff --check` 已通过。
- 受保护架构快照已按真实代码同步：`m04b-ai-drawer-normalized-sha256.txt` 与
  `m05a2-semantic-consumer-imports.txt`；M-05A2 架构测试夹具同步反映新增 Settings consumer。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 3m 30s`，
  `232` 个任务中 `23` 个执行、`209` 个为最新状态；唯一 launcher 与 player runtime packaging
  通过。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-20 20:55:12 +08:00`，`472651494` bytes，SHA-256
  `992A71C2FA3B9D0F02AEB55805F1D3F2C8792EFC549280459FE836E25B500DBF`；
  `com.kiyori`，版本 `45 / 0.1.0`，min/target/compile SDK `26/34/37`，唯一 launcher
  `com.ai.assistance.operit.ui.main.MainActivity`，`arm64-v8a`，Android Debug V2 单签名，
  `zipalign -c -P 16 -v 4` 通过。

## 2026-08-20 全屏搜索页网址行与历史操作尺寸微调

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。本轮继续复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md) 和
[`软件首页与全屏网页搜索`](kiyori_browser_product_completion/2_software_home_and_fullscreen_search.md)
作为唯一状态载体，不改变搜索、网址打开、复制、编辑或历史删除语义。

细化计划：

1. [DONE] 核对 `main`、干净工作树、正式开发门禁、截图和全屏搜索页唯一 Compose 实现
2. [DONE] 冻结视觉范围：网址信息、复制链接和编辑链接不再绘制独立灰色背景，直接使用整页背景
3. [DONE] 小幅缩小复制/编辑图标与文字、历史垃圾桶和编辑态“清空 / 完成”，保留原点击区域
4. [DONE] 更新定向几何回归，执行 Kotlin 编译、formal readiness、`git diff --check`
5. [DONE] 串行构建并核验 Debug APK
6. [PENDING DEVICE] 真机视觉、触控和系统字体缩放保持 `verification_pending`

本地证据：

- `WebSessionSearchUiPolicyTest` 为 `4/4`，零 failure/error/skip；`:app:compileDebugKotlin` 通过
- formal readiness、`git diff --check`、新增 Markdown 链接目标和网址行独立底色反向检查通过
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 36s`，
  `232` 个任务中 `22` 个执行、`210` 个为最新状态；唯一 launcher 与 player runtime packaging 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于 `2026-08-20 19:19:50 +08:00`，
  `472652738` bytes，SHA-256
  `74EC3A7B0B72076F7A19149D35FEFC45A78DE60DB10053A723D3145351560F5C`
- 包身份为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，唯一 launcher、
  Android Debug V2 单 signer 和 16 KiB ZIP 对齐通过；APK 仅含 `arm64-v8a`，`51` 个 `.so`
  与 `5504` 个 ZIP entry 均无重复，包含 `liboperit_ripgrep.so` 和 `assets/operit_shell_exec`

## 2026-08-20 启动、天气、MCP 提示与首次搜索深度优化

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。本轮复用
[`kiyori_startup_performance`](kiyori_startup_performance/index.md)、
[`kiyori_home_ui_refresh`](kiyori_home_ui_refresh/index.md) 和
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md)
三个既有权威载体；跨模块冻结方案见
[`启动调度、首页天气、MCP 提示与首次搜索联合优化`](kiyori_startup_performance/4_runtime_staging_weather_mcp_and_search.md)。

目标合同：

- 软件首页首帧不构造完整 Browser Runtime，不启动 ToolPkg 动态包扫描或重复 MCP 安装状态扫描
- ToolPkg 动态 route 在首帧后完成唯一初始化，初始化前的外部动态 route 请求不丢失
- 首页天气使用已验证持久快照快速首显，短时 last-known 定位与城市/天气并行请求缩短实时刷新
- 当前已有有效天气时，刷新失败不把首页覆盖为失败状态
- MCP 折叠加载提示只在当前可见 AI 页面和 AI drawer 显示
- 软件启动后的第一次关键词搜索在真实搜索结果 URL 可见后显示搜索引擎横向切换条；自定义主页、
  未稳定重定向、普通子页面和直接 URL 导航不显示
- 广告订阅快照缺失或内容变化时使用单遍低峰值解析编译，消除完整 UTF-8 文本、完整 parsed spec
  列表和重复规则编译造成的堆峰值

细化计划：

1. [DONE] 核对 `main@cc398f61`、干净工作树、正式开发门禁、历史合同和当前状态 owner
2. [DONE] 定位 Browser Runtime 首帧构造、ToolPkg/MCP 相邻扫描、天气串行链路和首次搜索投影缺口
3. [DONE] 冻结单 owner、调度顺序、天气状态机、AI 可见性和 session hydration 方案
4. [DONE] 分小批次实现并增加定向回归测试、架构/持久化快照和相称文档
5. [DONE] Kotlin 编译、正式门禁、architecture、Markdown、差异检查、定向 JVM 和 Debug APK 审计
6. [DONE] 精确候选树、敏感内容、构建产物、文件模式和子模块审计
7. [DONE] 根据现场反馈补充搜索结果 URL 页面门禁，删除全屏搜索提交时直接显示横向条的路径
8. [DONE] 根据 `f462782c-9749-4a17-b694-e39004004aba` 的 512 MiB 堆 OOM 栈，将广告订阅刷新和
   缓存缺失改为逐行直接编译与计数
9. [DONE] 重跑架构、正式门禁、完整定向 JVM、Debug APK 和本地产物封板
10. [PENDING DEVICE] 冷启动、天气首显、MCP 页面范围、搜索条页面范围和广告订阅刷新内存保持
   `verification_pending`

本地验证证据：

- architecture boundary `PASS (phase=m03)`，对应 Python fixture `109/109`
- 本轮启动、天气、MCP、搜索/恢复/导航和广告规则 14 组定向 JVM 共 `158/158`，
  零 failure/error/skip，并实际重新执行
  `:app:compileDebugKotlin`
- `check_formal_readiness.py --require-main` 与 `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，`232` 个任务中 `22` 个执行、
  `210` 个为最新状态；唯一 Debug launcher 和 player runtime packaging 校验通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-20 17:58:16 +08:00`，大小 `472652738` bytes，SHA-256
  `D6F038C09FDCD041E037A6862AB7C1BB15B3AB87C42D676CDB0BFB57AA529FD4`
- APK 为 `com.kiyori 45 / 0.1.0`、min `26`、target `34`、compile `37`，
  Application 为 `com.kiyori.app.KiyoriApplication`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`
- Android Debug V2 单 signer、`zipalign -c -P 16 -v 4`、arm64-only、`51` 个 `.so`
  零重复 basename、`52` 个 APK ELF 全部 ELF64/AArch64 且 `PT_LOAD >= 0x4000`
- APK 共 `5504` 个 ZIP entry，无重复项；`153` 个 `PT_LOAD` 为
  `0x4000 × 151 / 0x10000 × 2`，包含 `liboperit_ripgrep.so` 且不含 `libsudo.so`
- mpv 与 FFmpegKit 两份受控 M9 AAR 审计通过；其 `19` 个 native 成员与 APK 逐字节一致，
  且无 `RPATH/RUNPATH`

## 2026-08-20 浏览器源码查看器交互与长源码性能优化

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VERIFICATION COMPLETE / TARGET DEVICE VERIFICATION PENDING`。本轮复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md) 和
[`浏览器菜单真实能力`](kiyori_browser_product_completion/7_browser_menu_capabilities.md)
作为唯一浏览器专项载体，不创建第二个源码页面、编辑器或 Browser Runtime。

目标合同：

- 浏览器下拉菜单第三行第三个“查看源码”打开源码工作台时默认使用“横向浏览”
- “横向浏览”和“自动换行”两种模式都从源码左上角 `(0,0)` 开始；新 session/document
  也重新采用横向默认，不继承其他源码文档的临时模式
- 顶栏搜索打开的查找栏同时提供“上一个”和“下一个”；循环查找、替换后的游标和选区
  必须保持一致，不把替换后的新长度误当成旧查询长度
- 页面源码工具条把当前模式改成明确的双选控件，保留现有撤销、重做、格式化、复制、
  跳转行、差异和应用状态，不改变源码应用协议
- 长源码继续由同一个 `NativeCodeEditor` 和渲染线程持有；横向绘制只扫描可见区附近，
  不因横向拖动或双指缩放从长行行首重复扫描
- 双指缩放期间只更新字体几何和视口，自动换行的视觉布局在缩放结束后统一重建；
  大布局重建不得在每个缩放事件的主线程调用链中发生

细化计划：

1. [DONE] 对照当前源码工作台、下拉菜单、查找状态、`CanvasCodeEditorView`、视觉布局、
   缩放事件和既有测试确认状态所有者与性能根因
2. [DONE] 确认 Kiyori 尚未发布，本轮属于现有源码工作台的正常 UI/性能迭代，不保留旧
   默认模式兼容开关或并行路径
3. [DONE] 冻结模式状态、查找游标、长行 checkpoint、缩放延迟重排和渲染线程滚动校正方案
4. [DONE] 实现源码工作台默认横向、双模式左上角、上一个查找、明确模式控件和
   替换后查找状态修复
5. [DONE] 实现横向长行可见区索引、缩放期间布局缓存策略和滚动边界校正，补充回归测试
6. [DONE] 同步 `CONTEXT.md`、浏览器菜单能力合同、相关语言资源和本轮验证证据
7. [DONE] 运行定向 JVM、Kotlin 编译、正式门禁、`git diff --check`、串行 Debug APK
   构建及 APK 静态核验
8. [PENDING DEVICE] 候选树按本轮授权精确提交并推送唯一 `main`；真机视觉、双指手势、
   长源码交互和真实网页验收继续保持 `verification_pending`

2026-08-20 本地证据：定向源码回归 `25/25`、完整 Debug JVM `247 suites / 1458 tests`
且 failures/errors/skipped 均为 `0`，Kotlin 编译、formal readiness、architecture `phase=m03`、
七语种 XML 和 `git diff --check` 通过。规定的 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 39s`，
`232` tasks（`22` executed / `210` up-to-date）；APK 为
`app/build/outputs/apk/debug/app-debug.apk`，`472652738` bytes，SHA-256
`563E98259762B2C6F152EF02C075FEDBACB58018168289FCABD98FBB61F517CE`。包身份为
`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，唯一 launcher、
Android Debug v2 单 signer 和 16 KB ZIP 对齐通过。APK 仅含 arm64-v8a：`51` 个 `.so`、
零重复 basename；加 `assets/operit_shell_exec` 共 `52` 个 ELF64/AArch64，`153` 个
`PT_LOAD` 为 `0x4000 × 151 / 0x10000 × 2`。

## 浏览器当前域名网站配置与全局设置匹配

状态：本地实现、定向验证、最终门禁、Debug APK、候选审计与 `main` 提交推送均已完成；目标设备
现场验收保持 `verification_pending`。浏览器菜单、`WebSessionBrowserSettingsStore`、
`BrowserAdBlockStore`、userscript runtime、WebView 权限/导航/凭据、嗅探与自动悬浮播放调用链
均已核对。参考图只用于确认“按当前域名进一步禁用”的产品方向；UI 与状态继续复用 Kiyori 的共享
Browser Runtime、可拖动浏览器子抽屉和中性浏览器配色，不复制 hikerView 的 Activity、X5/TBS、
LitePal 或并行设置架构。

目标合同：

- 浏览器底部菜单第三行第五个“网站配置”从空占位升级为当前 HTTP(S) 域名的真实可拖动子抽屉；
  打开时冻结当前完整 host，不在抽屉显示期间随 AI 或其他入口的后台导航静默切换配置目标
- 站点配置全部使用负向开关且新域名默认全关：未保存任何站点规则时严格继承全局设置；有效能力
  恒为 `globalEnabled && !siteDisabled`，站点规则不能启用全局已经关闭的能力
- 当前域名提供 11 个真实禁用项：
  `广告拦截 / 用户脚本 / 返回不重载 / 左右滑动前进后退 / 强制页面缩放 / 网页元素长按菜单 /
  网页打开应用 / 网页获取位置 / 网站密码保存 / 搜索栏嗅探入口 / 自动悬浮播放`
- 广告拦截继续把站点禁用写入唯一 `BrowserAdBlockStore` 白名单；其余十项由唯一
  `WebSessionBrowserSettingsStore` 持久化域名规则。空规则必须从持久化集合删除，不保留第二状态源
- 主页、启动恢复、多窗口、网页文字比例、UA 与下载器策略不进入站点开关：它们分别属于全局
  生命周期、数值配置、已有独立站点 UA 规则或下载 owner，不能安全解释成“当前域名简单禁用”
- “设置首页 → 网页浏览器”同步优化为明确的全局上限页面：增加广告拦截总开关/管理入口和网站密码
  保存总开关，相关分组说明站点配置只能进一步关闭，不能反向覆盖全局禁用
- 返回不重载按即将返回的真实历史目标域名计算；左右滑动前进后退、强制页面缩放、网页元素长按和
  密码捕获按当前页面域名生效，其中缩放、长按和密码脚本会在站点规则变化后重新应用到全部已打开
  WebSession；外部应用、定位、嗅探入口和自动悬浮播放在每次实际决策时读取当前页面/请求域名
- 用户脚本站点禁用立即阻止后续 bootstrap、bridge、菜单命令和脚本网络规则；已经执行的纯 DOM
  修改没有可逆 owner，必须明确提示用户刷新当前页后才能清理，禁止伪造无损即时撤销
- `about:blank`、`file:` 和其他非 HTTP(S) 页面打开同一抽屉空态，但不允许创建无域名规则

细化计划：

1. [DONE] 核对当前 `main`、工作树、正式开发门禁、参考图、菜单占位、全局设置和十一项运行时消费点
2. [DONE] 冻结负向覆盖公式、完整 host 规则、唯一 owner、即时生效与需刷新边界
3. [DONE] 为 `WebSessionBrowserSettingsStore` 增加稳定 feature ID、规范化域名、严格 JSON
   编解码、空规则清理和纯策略解析
4. [DONE] 把“网站配置”接入独立 drawer route 和打开时的域名快照，完成现代化标题、继承说明、
   四组十一项开关、全局/站点/有效状态文案和“清除当前域名单独配置”
5. [DONE] 将十项站点规则逐一接入 display、Back、滑屏导航、WebView 外部导航、定位、凭据、
   长按、userscript、嗅探入口与自动悬浮播放；广告拦截复用现有白名单和 `ruleRevision`
6. [DONE] 优化网页浏览器全局设置为八组真实能力，增加全局规则说明、广告拦截总开关/管理入口
   和网站密码保存开关，不复制已有广告或密码状态 owner
7. [DONE] 增加域名规范化、持久化 round-trip、全局优先级、路由、设置分组、userscript、
   display/Back/权限/凭据和 UI 状态回归测试
8. [DONE] 同步 `CONTEXT.md`、README、浏览器设置里程碑和四行菜单能力合同
9. [DONE] 运行定向 JVM、Kotlin 编译、formal readiness、architecture boundaries、
   资源 XML、`git diff --check` 和规定的串行 Debug APK 构建与静态产物核验；候选提交形成后再以
   base/candidate 模式执行 Markdown、本地化和仓库卫生门禁
10. [DONE] 审计 38 文件精确候选树、敏感内容、构建产物、文件模式、子模块和远端竞争状态；
    实现提交 `81cd5e987edd3a6effe54ea4f4e03210d481cbf8` 已正常推送唯一 `main`
11. [PENDING] 在目标设备验收浅深主题、窄屏滚动、抽屉拖动、系统 Back、十一项真实站点行为、
    重启持久化、普通/无痕窗口和 AI 并发导航；完成前保持 `verification_pending`

本轮最终本地验证证据：

- 扩展定向矩阵覆盖 12 个 JVM 测试类、79 个测试，`failures/errors/skipped = 0/0/0`；补强竞态和
  Back 目标域名后又分别完成 21 个与 9 个相关回归，均为零失败
- `check_formal_readiness.py --require-main` 与
  `check_architecture_boundaries.py --require-main` 通过；七份本轮变更的 `strings.xml` 可解析，
  网站配置占位标识残留为零，`git diff --check` 无空白错误
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 在 `4m26s` 内成功，232 个任务中
  23 个执行、209 个缓存命中；`:app:verifySingleDebugLauncher` 与
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-08-20 03:55:13 +08:00`，大小 `472652190` bytes，SHA-256
  `6A1EE73BE434484DB8B7A3A7AB196455AC133FDC96EDFCDD3841C3836842E848`
- 独立 Build Tools 审计确认
  `com.kiyori / versionCode 45 / versionName 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，
  唯一 launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer
  与 `zipalign -c -P 16 -v 4` 均通过
- APK 仅包含 `arm64-v8a`，51 个 `.so` basename 全部唯一；连同
  `assets/operit_shell_exec` 共 `52/52` 个 `ELF64/AArch64`，153 个 `PT_LOAD` 分布为
  `0x4000 × 151` 与 `0x10000 × 2`，最小 alignment 为 `0x4000`
- 38 文件候选树无高置信敏感形状、无构建产物、无异常大文件或 reparse point；`terminal` 子模块
  干净且 gitlink 未变。推送实现提交后，本地 `HEAD`、tracking `origin/main` 与
  `git ls-remote origin refs/heads/main` 均为
  `81cd5e987edd3a6effe54ea4f4e03210d481cbf8`，ahead/behind 为 `0/0`
- 本轮未安装 APK，未操作 ADB、MuMu、模拟器或真机；视觉、手势、真实网页逐功能和应用重启后的
  持久化现场验收均不由上述自动证据替代

## 网络日志图片分类与 SVG 显示修复

状态：本地实现、定向自动验证、正式门禁、Debug APK 构建与静态产物核验已完成；目标设备上的
真实 WebView 请求分类、SVG 缩略图和共享全屏查看仍为 `verification_pending`。用户现场确认
网络日志“图片”筛选中 SVG 缩略图和共享全屏查看均进入加载失败，同时 HTML、JavaScript 等
非图片资源会误入图片分类。本轮继续复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md)
及其
[`浏览器菜单真实能力`](kiyori_browser_product_completion/7_browser_menu_capabilities.md)
作为唯一浏览器专项载体，不创建平行网络日志或图片查看器。

细化计划：

1. [DONE] 核对当前 `main`、干净工作树、正式开发门禁、网络请求采集、资源聚合、分类策略、
   缩略图、共享图片查看器、二维码识别和全局 Coil `ImageLoader`
2. [DONE] 确认误分类根因：现有策略先用 `Accept.contains("image/")` 判为图片，之后才检查
   HTML、JavaScript、CSS、JSON 等扩展名；浏览器导航请求的混合 `Accept` 因包含图片媒体范围而
   抢占真实格式
3. [DONE] 确认 SVG 根因：网络日志缩略图、共享图片查看器和二维码识别都使用进程级唯一 Coil
   `ImageLoader`，但该 loader 只注册 GIF/平台位图解码器，没有注册 Coil `SvgDecoder`
4. [DONE] 把资源分类改为：主框架固定为网页；已知 URL 扩展名优先；无已知扩展名时解析
   `Accept` 的媒体范围、质量值和顺序；通配或无法确认的资源保持“其他”
5. [DONE] 补齐常见网页、脚本、样式、数据、字体、图片、音视频扩展名，并增加混合导航
   `Accept`、SVG、HTML、JavaScript、CSS、JSON、字体和未知资源的回归测试
6. [DONE] 引入与现有 Coil `2.5.0` 对齐的 `coil-svg` 模块，并在
   `KiyoriApplication` 的唯一全局 `ImageLoader` 注册 `SvgDecoder.Factory()`；不建立第二
   ImageLoader 或第二图片缓存
7. [DONE] 同步 `CONTEXT.md`、README、正式浏览器架构和阶段 7 合同
8. [DONE] 运行定向 JVM、Kotlin 编译、formal readiness、architecture boundaries、
   `git diff --check` 和规定的串行 Debug APK 构建与静态产物核验
9. [PENDING] 在目标设备复测真实 SVG、带查询参数 SVG、登录态 SVG、HTML/JS/CSS/JSON 分类和
   网络日志缩略图/查看器；完成前保持 `verification_pending`

实现边界：

- Android WebView 没有提供响应 MIME 或响应体时，不把不透明 URL 猜成具体格式；无法由主框架、
  已知扩展名或明确 `Accept` 证据确认的资源归入“其他”
- 图片请求继续使用活动 WebSession 的 User-Agent、Profile Cookie 和 HTTP(S) Referer；
  不改变 Browser Runtime、资源目录、下载 owner 或共享图片查看器手势
- 本轮不提交、不推送、不安装 APK、不调用 ADB、模拟器或真实设备

本地验证：

- `BrowserNetworkLogPolicyTest` `15/15` 与 `BrowserInteractionContractTest` `5/5`，合计
  `20/20`，零失败、零错误、零跳过；覆盖混合导航 `Accept`、扩展名优先级、SVG、HTML、
  JavaScript、CSS、JSON、字体、质量值、通配和全局 `SvgDecoder` 接线
- formal readiness、architecture boundaries `phase=m03`、工作树 Markdown links
  `errors=0` 与 `git diff --check` 通过；M-03 受控 Application 快照已更新为
  `FE81FB2D78D46E2EB86E3BF21D71B5B50A5B173B0C9BF5F3662272CE546B3C44`
- 串行 `:app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 2m 46s`，`232` 个任务中 `80 executed / 152 up-to-date`；
  唯一 Debug Launcher 与 Player Runtime packaging 门禁通过
- 最终 Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；宿主文件时间观测为
  `2026-08-20 01:44:18 +08:00`，大小 `464647701` bytes，SHA-256
  `C11B566D56912C327F26F7A32F3ECF95918AAF46E0FE60A934229B216366CB12`
- APK 为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37 /
  arm64-v8a`，唯一 Launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug
  V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过，DEX 中存在
  `coil.decode.SvgDecoder` 和 `coil.decode.SvgDecoder.Factory`
- 未提交、未推送、未安装 APK、未调用 ADB/模拟器/真实设备；普通 SVG、带查询参数 SVG、
  登录态/防盗链 SVG 及真实网页分类仍需目标设备复测

## 浏览器网页元素长按菜单与设置开关优化

状态：本地实现、定向自动验证、正式门禁、Debug APK 静态核验与 `main/origin/main` 实现交付
已完成；目标设备交互仍为 `verification_pending`。本轮复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md)
及其
[`浏览器菜单真实能力`](kiyori_browser_product_completion/7_browser_menu_capabilities.md)
作为唯一浏览器菜单专项载体，不创建平行 TODO。Kiyori 尚未发布，本轮属于现有网页元素长按能力的
正常迭代：保留输入框系统原生选区和现有 Browser Runtime/规则/下载/窗口 owner，重构普通网页元素
动作策略与弹层 UI，并增加默认开启的持久化总开关。

详细计划：

1. [DONE] 核对当前 `main`、干净工作树、正式开发门禁、参考截图、Kiyori 当前长按注入/bridge/
   Host/Compose 链路，以及本地 hikerView 的链接、图片链接、纯图片和未知元素菜单分支
2. [DONE] 冻结唯一 owner：窗口继续由 WebSession registry 创建，广告规则继续写入
   `BrowserAdBlockStore`，图片保存继续进入 `BrowserDownloadManager`，浏览器设置继续由
   `WebSessionBrowserSettingsStore` 持有；不创建第二 WebView、图片下载器或广告规则仓库
3. [DONE] 制定按真实元素类型裁剪的动作矩阵：
   - 链接：新窗口、后台打开、复制链接、复制文本、外部打开、选择文本、快速拦截、精细标记、
     拦截过滤网址
   - 图片链接：链接动作加全屏查看、保存图片、看图模式、复制图片链接和识别二维码
   - 纯图片/背景图片：全屏查看、保存图片、看图模式、复制图片链接、外部打开、识别二维码、
     快速拦截、精细标记和拦截过滤网址
   - 媒体资源：复制资源链接、外部打开、快速拦截、精细标记和拦截过滤网址，不伪装成图片
   - 普通文本/元素：仅在存在真实文本时显示复制/选择文本，并提供元素拦截能力
4. [DONE] 冻结 UI：使用带拖动柄的现代 Material 3 底部弹层；顶部显示元素类型、标签、
   当前站点和两行摘要，常用动作使用四列紧凑图标卡，内容与拦截动作分组显示；不照搬
   hikerView 左侧窄白色长列表，也不继续使用当前高信息密度居中长弹窗
5. [DONE] 冻结图片能力：把网络日志的 edge-to-edge 分页图片查看器抽成共享 viewer；网页元素的
   “全屏查看”只建立当前图片快照，“看图模式”在用户点击后从当前 document 有界收集图片并从
   当前图片开始；两者共用左右分页、编号、保存、长按保存、单击/上下退出和 `1x..5x` 双指缩放
6. [DONE] 冻结二维码能力：只对真实图片资源显示“识别二维码”，使用活动 WebSession 的
   User-Agent、Profile Cookie 和 HTTP(S) Referer 加载有界软件 Bitmap，再通过仓库现有 ZXing
   依赖执行单一路径 QR 解码；结果弹窗提供完整内容、复制，以及仅对有效 HTTP(S) 内容开放网页打开
7. [DONE] 冻结设置合同：“设置首页 → 网页浏览器 → 网页交互”增加“长按网页元素菜单”开关，
   新安装默认开启；关闭后立即关闭已显示菜单并同步所有已打开 WebView，普通元素不再触发该菜单，
   编辑型 `input`、`textarea` 和有效 `contenteditable` 仍由 Android WebView 系统原生选区持有
8. [DONE] 实现元素类型/动作纯策略、设置持久化和活动 WebView 同步、共享图片 viewer、
   页面图片有界快照、二维码识别状态及现代化底部弹层
9. [DONE] 增加策略、设置、JavaScript bridge、Back、图片快照与二维码解码合同测试，更新
   `CONTEXT.md`、README 和正式架构文档
10. [DONE] 运行定向 JVM 测试、Kotlin 编译、formal readiness、architecture boundaries、
    `git diff --check`、规定的串行 Debug APK 构建/静态核验，以及候选提交 Markdown links
    `errors=0 / warnings=0`
11. [DONE] 审计全部目标改动、敏感内容、构建产物和子模块；实现提交
    `d803c8bb8cb4c638fd66748994959894573990ef` 已推送到 `origin/main`，首次对账时本地、
    tracking 与远端 ref 一致且分歧为 `0/0`
12. [PENDING] 在目标设备复测浅深主题、窄屏滚动、输入框原生选区、开关即时生效、每类元素动作、
    登录态图片、页面看图、二维码结果、下载确认、系统 Back 与手势冲突

实现边界：

- 不安装 APK，不调用 ADB、模拟器或真实设备，不构建 Release/AAB
- 不增加 hikerView 的 X5/TBS、Activity、EventBus、LitePal 或 native ABP 架构
- 不增加回退、重试、备用图片加载器、第二浏览器运行时或第二规则 owner
- 当前本地自动验证和 Debug APK 只能证明实现与构建基线；真实 WebView 命中、视觉和触控保持
  `verification_pending`

本地验证：

- 五个定向 JVM 测试类合计 `39/39`，零失败、零错误、零跳过；`:app:compileDebugKotlin` 通过
- formal readiness 与 architecture boundaries `phase=m03` 通过，`git diff --check` 无错误
- Markdown 候选树检查以 `64016a31d7334267864f7dd2ea507f87ae4bb662` 为 base、以
  `d803c8bb8cb4c638fd66748994959894573990ef` 为 candidate，结果为
  `errors=0 / warnings=0`
- 串行 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 35s`，`232` 个任务中
  `23 executed / 209 up-to-date`
- `app/build/outputs/apk/debug/app-debug.apk` 为 `472553854` bytes，写入于
  `2026-08-20 00:47:25 +08:00`，SHA-256
  `75384DABE734B26DCF5E22AA55DBF1A2D7D7A290AB8E3A650444837134332206`
- APK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37 / arm64-v8a`，唯一 Launcher、
  Android Debug V2 单 signer、16 KB ZIP 对齐及 `52/52` 个 ELF64/AArch64 的
  `PT_LOAD >= 0x4000` 审计通过
- 未安装 APK、未运行 ADB/模拟器/真实设备

## 2026-08-19 浏览器原生选区与图片双指缩放继续修复

状态：用户现场复测证明上一轮输入框蓝色选区与“复制 / 全选 / 取消”属于 Kiyori 自绘假选中，
无法作为系统选区逐字调整，也无法可靠读取剪贴文本。本轮已完成源码纠正、定向 JVM 测试、
Kotlin 编译、正式门禁和规定的 Debug APK 静态核验；目标设备复测仍为
`verification_pending`。

本轮继续复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md)
及其
[`浏览器菜单真实能力`](kiyori_browser_product_completion/7_browser_menu_capabilities.md)
作为唯一浏览器专项载体，不创建平行 TODO。Kiyori 尚未发布，因此直接删除输入框自绘选区路径，
不保留复制、全选、取消自定义操作栏的兼容实现。

细化计划：

1. [DONE] 核对现场截图、当前 dirty 工作树、WebView 长按配置、JavaScript 文字选择 helper、
   Host 自绘操作层和图片查看器单指手势状态机
2. [DONE] 保持 WebView 可长按，并按 `EDIT_TEXT_TYPE` 把编辑控件交给 Android WebView 原生
   `ActionMode`、系统剪切/复制/粘贴和原生选区手柄；普通网页元素继续进入“网页元素操作”
3. [DONE] 在 JavaScript 长按定时器、`selectAtPoint()` 和全选入口阻止编辑控件进入 Kiyori
   自绘选择，并删除自定义 `state.control`、控件矩形、控件全选和控件文本读取路径
4. [DONE] 在现有 edge-to-edge 图片查看器手势状态机中增加双指缩放，范围固定为 `1x..5x`，
   双指期间消费事件，切换图片后重置为 `1x`
5. [DONE] 保持单指左右分页、上下拖动透明退出、单击退出、长按保存、编号和保存按钮语义
6. [DONE] 更新源码合同测试、`CONTEXT.md`、README、正式架构与阶段 7 文档
7. [DONE] 定向 `BrowserNetworkLogPolicyTest`、`BrowserInteractionContractTest` 与
   `:app:compileDebugKotlin` 通过
8. [DONE] 运行 formal readiness、architecture boundaries、Markdown links、
   `git diff --check` 和规定的串行 Debug APK 构建/静态产物核验
9. [PENDING] 在目标设备复测输入框系统选区菜单、逐字拖动手柄、剪切/复制/粘贴，以及图片双指
   放大/缩小、单指切图、上下退出、点击退出和保存

本地验证：

- `BrowserInteractionContractTest` `3/3` 与 `BrowserNetworkLogPolicyTest` `13/13`，
  合计 `16/16`，零失败、零错误、零跳过；`:app:compileDebugKotlin` 通过
- formal readiness、architecture boundaries `phase=m03`、Markdown links
  `errors=0 / warnings=0` 与 `git diff --check` 通过；输入框自绘
  `selectControlContents / renderControlSelection / state.control` 残留搜索为零
- 规定的串行 `:app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 1s`，`232` 个任务中 `22` executed / `210` up-to-date；
  唯一 Debug Launcher 与 Player Runtime packaging 门禁通过
- `app/build/outputs/apk/debug/app-debug.apk` 写入于
  `2026-08-19 23:25:53 +08:00`，`472553854` bytes，SHA-256
  `3E7B488E966C77E5DCB606E2B6238190B889F7CE9E081A027F96917256FD64B9`
- APK 为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37 /
  arm64-v8a`，唯一 Launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；
  Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未调用 ADB/模拟器/真实设备；Android WebView provider 的原生选区菜单、
  选区手柄与图片双指缩放手感仍需用户在目标设备复测

## 2026-08-19 浏览器输入文字长按与网络日志图片查看器修复

状态：本地实现、定向自动验证、Kotlin 编译、正式开发准备门禁、架构边界检查和规定的
Debug APK 构建/静态产物核验已完成；目标设备交互验收保持 `verification_pending`。

本轮继续复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md)
及其
[`浏览器菜单真实能力`](kiyori_browser_product_completion/7_browser_menu_capabilities.md)
作为唯一浏览器菜单专项载体，不创建平行 TODO。Kiyori 尚未发布，当前图片查看器属于未发布的
浏览器内部交互，可按用户给出的目标方案直接替换，不保留旧居中黑窗、关闭叉号或并行查看路径。

细化计划：

1. [DONE] 核对当前 `main`、干净工作树、正式开发门禁、五张参考/实机图、现有网络日志与网页
   元素长按调用链
2. [DONE] 确认输入框长按根因：文字选择 helper 先调用网页元素动作，`input` 返回已处理后阻断
   现有选择流程
3. [DONE] 确认图片查看器根因：通用模态宿主的安全区和 `20dp` 内边距把所谓全屏限制为居中窗口；
   当前 painter 状态分支没有使用受布局约束的图片内容节点，请求身份也未补齐活动 WebSession 的
   User-Agent、Cookie 与 Referer
4. [DONE] 让编辑型 `input`、`textarea` 与可编辑内容优先进入现有文字选择 helper，
   非编辑元素继续进入“网页元素操作”
5. [DONE] 将图片查看器替换为 edge-to-edge 全屏窗口，按打开时的图片筛选结果建立稳定快照，
   左右滑动切换并在左下角显示当前编号和总数
6. [DONE] 增加右下角保存、长按“保存原图”、单击退出，以及上下拖动时降低黑色背景透明度并
   在松手后退出；保存继续调用唯一浏览器下载 owner
7. [DONE] 增加请求身份、图片集合/索引、拖动透明度和长按选择优先级自动合同，更新
   `CONTEXT.md`、README、正式架构与阶段 7 文档
8. [DONE] 运行定向测试、Kotlin 编译、formal readiness、架构门禁、差异检查和规定的
   Debug APK 构建与产物核验
9. [PENDING] 在目标设备复测输入框选区手柄、复制/全选、图片加载、左右切换、上下退出、
   单击退出、保存、长按保存、系统 Back、浅深主题和普通/无痕 Profile

本地验证：

- `BrowserNetworkLogPolicyTest` 与 `BrowserInteractionContractTest` 共 `15/15`，零失败、零错误、
  零跳过；覆盖图片请求身份、查看集合/初始索引、拖动透明度/退出阈值、编辑控件长按优先权、
  edge-to-edge Dialog、分页、图片加载节点和保存派发时序
- `:app:compileDebugKotlin --no-daemon --console=plain` 通过；最终定向测试与 Debug 构建均重新编译
  本轮 Kotlin 源码
- formal readiness、architecture boundaries `phase=m03`、Markdown links
  `errors=0 / warnings=0` 与 `git diff --check` 通过；仅保留两个既有源码文件的 CRLF 到 LF 提示
- 最终串行 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 58s`，
  `232` 个任务中 `22` executed / `210` up-to-date；唯一 Debug Launcher 和 Player Runtime
  packaging 门禁通过
- `app/build/outputs/apk/debug/app-debug.apk` 写入于 `2026-08-19 22:23:53 +08:00`，
  `472553854` bytes，SHA-256
  `9C442B3B8C65AF71A1C5746354FE189C3BF6C6872D304DA61AFD6FEA38C19367`
- APK 为 `com.kiyori / 45 / 0.1.0 / arm64-v8a`，唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer 与
  `zipalign -c -P 16 4` 均通过
- 未安装 APK、未调用 ADB/模拟器/真实设备；输入选区、真实站点图片防盗链、左右/上下手势、
  保存确认层和亮暗系统栏视觉仍需目标设备验收

## 2026-08-19 播放器真机日志驱动的全链路优化

状态：本地实现、定向自动验证、App Lint、正式门禁和 Debug APK 静态核验已完成；目标设备上的
首帧、手势、悬浮接续和缩略图视觉/性能验收仍为 `verification_pending`。四个附件中后两个报告的
SHA-256 相同，因此有效现场样本为三类：完整缓存直链 MP4、从悬浮转全屏的直链视频，以及 HLS
全屏播放。三类均已到达 `FILE_LOADED` 和 `PLAYBACK_RESTART`，本轮没有把成功链路误报为网络、
TLS、解码或 native 崩溃。

本轮继续复用
[`kiyori_browser_product_completion`](kiyori_browser_product_completion/index.md)
及其
[`播放器在线可靠性合同`](kiyori_browser_product_completion/13_player_online_playback_reliability_and_compatibility.md)
作为唯一播放器专项载体，不创建平行 TODO 目录。

细化计划：

1. [DONE] 核对三份唯一诊断报告、当前 `PlayerSession` / `:player` / Surface lease /
   Browser candidate owner、播放器设置页和正式开发门禁
2. [DONE] 缩短点击候选到首帧的本地串行路径；保留
   `mpv_initialize -> Surface attach ACK -> loadfile` 的 native 安全顺序，不增加预请求、
   重试、代理或 URL 改写；移除首播前相同 mpv 设置重复写入，并让加载提示延迟 `160ms` 出现
3. [DONE] 修复悬浮播放器跨页面持续显示、即时退出，以及旧视频退出后接续当前页面新候选；
   关闭动作只拒绝实际被关闭的自动候选页面
4. [DONE] 修复悬浮全画面横滑读取旧进度、seek 提交后旧快照拉回的问题；同一 load 只保留一个
   mpv seek 在途，快速连续操作合并为最新目标，并让小窗进度条与全屏共用同一待确认语义
5. [DONE] 将长按加速和松手恢复的中间提示分别限制为一秒；提示消失不结束临时加速状态
6. [DONE] 允许已经由唯一 mpv request 证明 `FULL_VIDEO COMPLETE` 的网络直链使用快速
   seek preview；只从同一 demuxer 的缓存导出目标附近私有小片段，普通在线媒体继续禁止无请求头
   的第二连接
7. [DONE] 审计视频播放器设置 25 项的持久化、依赖状态、运行时消费、文案与可见状态，修复
   必要问题并增加自动合同
8. [DONE] 修复悬浮与全屏相互切换的进度/缓存流转：同一 request 只转移 Surface，位置、
   待提交 seek、暂停/速度、完整缓存、load/runtime generation 与 runtime PID 均保持；全屏
   `CLOSE` 后同页候选不会被自动路径重新打开，新页面候选和同页手动播放仍可用
9. [DONE] 运行播放器/候选/设置定向测试、formal readiness、差异反向审查和规定 Debug APK
   构建与产物核验
10. [PENDING] 在目标 vivo Android 16 设备复测首帧、小窗退出/接续、双向无缝进度/缓存流转、
    横滑 seek、长按提示和完整缓存缩略图；设备操作不在本轮授权内，完成前保持
    `verification_pending`

本地验证：

- 五个定向 JVM 测试类共 `81/81`，零失败、零错误、零跳过；播放器 Python 静态门禁 `6/6`
- `:app:lintDebug` 通过；30 条 warning 与 1 条 hint 均未命中本轮播放器改动文件，未新增
  suppress、禁用检查或扩张 Lint baseline
- formal readiness、architecture boundaries 和 `git diff --check` 通过；差异检查仅保留
  `WebSessionBrowserScreen.kt` 的既有 CRLF 到 LF 提示
- 规定的串行 `:app:assembleDebug` 于 `2026-08-19 20:22:21 +08:00` 完成，`232` 个任务中
  `23` 个 executed、`209` 个 up-to-date；唯一 Launcher 与播放器 runtime packaging 门禁通过
- `app/build/outputs/apk/debug/app-debug.apk` 为 `472553854` bytes，SHA-256
  `E4BA494F49BFB2959B8E4B9A359EF2820F5E64A38CC3223F8FC8A8D4429C1E1C`；包名/版本为
  `com.kiyori / 45 / 0.1.0`，仅 `arm64-v8a`，Android Debug V2 单 signer，16 KB ZIP 对齐通过
- APK 内 51 个 `.so` 无重复 basename；加上 `assets/operit_shell_exec` 共 52 个 AArch64
  ELF，153 个 `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`，零低于 16 KB

## 2026-08-19 AI 角色选择与左抽屉快捷入口调整

状态：第二轮纠正后的本地实现、定向自动检查、正式门禁和 Debug APK 核验已完成；目标设备视觉
与返回链验收仍保持 `verification_pending`。

本轮复用
[`kiyori_product_shell`](kiyori_product_shell/2_modal_ai_drawer_and_settings_ownership.md)
与
[`kiyori_global_ui_visual_unification`](kiyori_global_ui_visual_unification/index.md)
作为唯一导航与视觉载体，不创建平行 TODO。

细化计划：

1. [DONE] 核对角色选择弹层、排序菜单、抽屉导航目录、工具箱目录与现有语义色合同
2. [DONE] 将角色卡和群组的未选中项改为中性 surface，选中项及顶栏默认头像改为蓝色语义
3. [DONE] 将角色排序菜单设为 `12dp` 圆角、`0dp` tonal/shadow elevation，并增加
   `0.5dp outlineVariant` 细描边
4. [DONE] 将抽屉高频入口改为“扩展 / 工具箱 / 工作流”，删除权限状态查询与短标签资源
5. [DONE] 从唯一 `AppNavigationModel` 统计 `NavigationSurface.TOOLBOX` 宿主与 ToolPkg 条目，
   作为工具箱右上角数量；不建立第二计数 owner
6. [DONE] 新增 `MORE_FEATURES` 设置路由和统一设置风格子页；“权限”复用原
   `Screen.ShizukuCommands` 与当前 settings session，Back 先回更多功能再回设置首页
7. [DONE] 重新运行正式门禁、差异检查和规定 Debug APK 构建核验
8. [PENDING] 目标设备验收浅深主题角色卡、排序弹窗四角与描边、设置返回链、窄屏三卡布局和
   动态 ToolPkg 数量

权限入口已经从 AI 抽屉移动到“设置 - 更多功能 - 权限”。更多功能页不复制权限开关或状态，
只通过现有导航进入 `Screen.ShizukuCommands` 唯一设备能力 owner。

本轮最终本地证据：

- `CharacterSelectorVisualContractTest` `4/4`、`KiyoriSettingsPagesTest` `14/14`、
  `KiyoriShellStateTest` `71/71`，零失败、零错误、零跳过；任务包含
  `:app:compileDebugKotlin`
- architecture boundary `PASS (phase=m03)`，architecture 单元测试 `109/109`，
  formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 2m 2s`，`232` 个任务中 `23 executed / 209 up-to-date`；
  `verifySingleDebugLauncher` 和 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，写入时间
  `2026-08-19 16:00:14 +08:00`，`472553854` bytes，SHA-256
  `8C08C8D7150BBDB56EE1018BFE1C24FDB07C70F453A3F0AB9ABA74DF267A87E1`
- APK 为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，唯一
  launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`，仅 `arm64-v8a`，
  Android Debug V2 单 signer 与 16 KB ZIP 对齐通过

## 2026-08-19 AI 对话崩溃与表格/图表滚动回归修复

状态：设备边界复测修正与本地自动门禁完成，目标设备再次复测待验证。用户提供的
`APP_FATAL 158cdc96-af02-4045-9170-efa03d778440` 已定位到 AI 首页根
`KiyoriAiHomePagerGestureBridge`：子组件的 nested `postFling` 在没有完整首页手势会话时仍会
调用首页 `FlingBehavior`，现有 `sessionComplete` 致命断言因此终止主线程。此前内容横向 owner
又通过 `aiHomeGestureBlocked` 在 `DOWN` 后动态禁用祖先 `scrollable`，会在同一手势中途更新
输入节点，造成参考截图中的宽表格无法稳定左右拖动，纵向手势也不能可靠交还消息列表。

本轮复用
[`home_pager_gesture_consistency`](home_pager_gesture_consistency/index.md)
作为唯一专项载体。冻结方向是：保持首页祖先输入节点稳定；只有完整真实首页手势进入严格吸附
策略，普通子 nested fling 不生成页面目标；表格改用标准 Compose 横向滚动；Mermaid 在异步渲染
完成后按最终 SVG 建立内部水平/垂直滚动与缩放范围。保留唯一 `PagerState`、永久 AI 根、现有
Markdown/JLaTeXMath 路径和 Mermaid/HTML 入口，不增加第二渲染器、回退路径或依赖升级。

实施与验收顺序：

1. [DONE] 核对崩溃栈、Git/正式门禁、首页 bridge、内容 ownership、表格和 Mermaid/HTML 链路；
2. [DONE] 修复首页会话与 nested fling 合同，并锁定未完成会话不进入吸附策略；
3. [DONE] 保持祖先 `scrollable` 稳定，把内容占用改为 bridge 内部位移/吸附门禁；
4. [DONE] 修复表格标准横向滚动、父级纵向滚动和末列可达；
5. [DONE] 修复 Mermaid 最终尺寸、内部四向滚动、缩放后完整平移与 WebView 手势所有权；
6. [DONE] 根据设备复测补齐表格边界惯性 delta 门禁，重新运行专项测试、AndroidTest
   编译、正式/架构门禁、差异检查和 Debug APK 构建核验；
7. [PENDING] 目标设备复测崩溃、宽表格、长流程图及连续交互，完成前保持
   `verification_pending`。

## 2026-08-19 负一屏书签与历史入口 Back owner 崩溃修复

状态：本地修复完成，设备复测待验证。用户提供的 `APP_FATAL f5fe7a19-c9a1-415f-8836-e97bcc234fa4`
已经确认：负一屏共享历史抽屉在 `KiyoriBrowserHome` 之外组合
`WebSessionHistorySheet`，但 Sheet 直接读取只由 Browser Home 提供的 strict
`LocalWebSessionBrowserSystemBackEnabled`，因此首次点击即抛出
`IllegalStateException`。共享书签抽屉复用同一模式，存在相同崩溃条件。

细化计划：

1. [DONE] 核对崩溃栈、两个负一屏宿主、Browser 内调用点、关闭动画挂载周期和系统 Back
   状态机；确认不关闭 strict Local、不创建第二 Browser Runtime
2. [DONE] 将书签与历史 Sheet 的 `systemBackEnabled` 改为必填宿主参数；Browser 内
   传共享 owner，负一屏宿主只在抽屉真实可见时传 `true`
3. [DONE] 增加 JVM 源码结构合同和无 Browser provider 的 Compose Android smoke test，
   覆盖书签与历史两条入口
4. [DONE] 运行定向测试、AndroidTest 编译、formal readiness、架构门禁、差异检查和规定 Debug APK
   构建与产物核验
5. [PENDING] 目标设备复测负一屏书签/历史首次打开、内部 Back、关闭动画和重复打开；设备执行
   不在本轮授权内，完成前保持 `verification_pending`

本轮本地证据：

- `WebSessionHistorySheet` 与 `WebSessionBookmarkSheet` 现在都要求宿主显式传入
  `systemBackEnabled`，不再直接访问 strict CompositionLocal；Browser Screen 传共享 owner，
  负一屏两个宿主传 `isVisible`。关闭动画期间 Sheet 继续组合，但内部 BackHandler 已禁用。
- `KiyoriSoftwareHomeSearchTest`：`10/10`；`:app:compileDebugAndroidTestKotlin`
  与 `:app:compileDebugAndroidTestJavaWithJavac`：通过；新增
  `KiyoriBrowserBackOwnershipAndroidTest` 覆盖无 Browser provider 的书签/历史组合路径。
- `check_formal_readiness.py --repository . --require-main`：PASS；
  `check_architecture_boundaries.py --repository . --require-main`：PASS (`phase=m03`)；
  `git diff --check`：PASS。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 2m 28s`，`232 actionable tasks`，`22 executed / 210 up-to-date`。
  Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`467107608` bytes，SHA-256
  `4D758AC8581EBEED6DB15828D2BA9BF9EF7A67DE4B829EA8184B68DB78AE400E`；
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，仅
  `arm64-v8a`，Android Debug V2 单 signer 和 `zipalign -c -P 16 -v 4` 均通过。
- 本轮未安装 APK、未连接设备、未执行真实点击或系统 Back 验收；上述本地证据不能替代目标设备
  对负一屏书签/历史打开、内部状态返回、关闭动画和重复点击的现场复测。

## 2026-08-18 三页首页横向手势、全屏搜索与 AI 内容横向交互修复

状态：根因修复已经本地实现，保持 `verification_pending`。负一屏与软件首页继续由原生
`HorizontalPager` 接收触摸；永久 AI 根改用公开 API bridge，为每次按下建立新会话，并按严格
大于半页、`400dp/s`、最多一页、LTR/RTL、边界和 bounded spring 合同释放，不再把依赖 Pager
私有手势元数据的默认 fling 交给普通 `scrollable`。软件首页的 Full-Screen Web Search child
现在独立参与 Shell overlay 可见性，点击搜索框后不再只隐藏底栏而看不到搜索页。后续现场崩溃
进一步确认：该页复用 Browser 搜索组件，却不属于 `KiyoriBrowserHome` 的 retained subtree，
不能依赖其 CompositionLocal provider。搜索组件现要求每个宿主显式传入系统 Back owner：
Full-Screen Search 固定传入当前可见 child 的 `true`，Browser 内搜索传入共享 Browser owner；
strict CompositionLocal 继续在 Browser 宿主漏接时抛出开发期错误。

AI 消息中的宽表格、关闭自动换行的代码、横向公式、Mermaid 和 HTML 预览接入同一多 owner
横向手势占用状态；任一内容 owner 或历史快速滚动 owner 活跃时，首页 bridge 让出当前手势。
当前专项 JVM `85/85`、完整 App JVM `239 suites / 1404 tests`、AndroidTest Kotlin/Java 编译、
architecture `phase=m03`、formal readiness、`git diff --check` 与规定 Debug 构建均通过。
APK 为 `467107608` bytes，SHA-256
`67F8F4D73367981591A2C0C73C25CB4F3B698C658238C682DA040E8E4533B16D`；包/版本/SDK、唯一
launcher、arm64-only、V2 单一 Android Debug signer 与 16 KB ZIP 对齐已核验。真机手感、IME、
TalkBack、系统边缘 Back 和真实内容滚动不由本地自动化替代。

详细根因、方案取舍、目标架构、影响文件、阶段、自动差分矩阵、目标设备验收和完成定义见
[`home_pager_gesture_consistency/index.md`](home_pager_gesture_consistency/index.md)。

## 2026-08-18 AI 对话详情与完整审计

状态：产品、数据、UI、安全、导入导出和生命周期合同已经通过三轮 Grill Me 冻结；Room 22、
审计链、消息修订、Provider/Hook/工具接线、分页三视图、v3 归档和导入导出已经完成本地实现。
项目 Python、完整架构、formal/fresh-clone、聚合 JVM/AndroidTest/Lint 和规定 Debug APK
构建与静态产物审计均已通过；目标设备、真实 Provider/ToolPkg/工具现场链保持
`verification_pending`；本轮已完成精确 staged 交付，提交
`f2e78c2bb083ba6bdd54c9db5d3b95badcfefa20` 已推送并与 `main`、`origin/main` 和远端
`refs/heads/main` 对齐。

当前方案以独立的 `ConversationAuditRepository` 作为不可覆盖审计事件、消息修订、加密
payload、完整性链和审计导出的唯一 owner。现有聊天消息继续作为当前投影，Provider execution
表继续负责可恢复执行，两者向审计账本提供事实但不取代审计真相。AI 对话顶栏将固定为
“浏览栏 / 终端 / 对话详情 / 工作区”，详情提供时间线、对话和 Raw 三视图，并按 sequence
分页读取永久事件；用户与 AI 可见文本通过修订事件编辑，工具、错误和 Provider 原始事件只读。
最终一致性补强还包括：历史重建与完整审计导入单事务提交、导出在同一聊天锁内封印并验证稳定
快照、事件哈希绑定事件 ID/落盘时间/payload 元数据、本机链段公钥锚定 Android Keystore、
流式文本以前缀关系区分增量与完整修订，以及对话视图读取完整持久化消息而非聊天显示窗口。

详细数据模型、事件分类、存储安全、接线、迁移、导出、UI、实施阶段和验证矩阵见
[`ai_conversation_audit/index.md`](ai_conversation_audit/index.md)。

### 2026-08-30 对话详情诊断中心增量

状态：`verification_pending`。在既有审计 owner、三视图和一致性快照合同上，重新设计详情页为设置页
同款诊断中心：压缩顶部信息、统一三视图搜索、逐条展开/收起、增加对话搜索，并把导出动作
明确拆分为可直接交给 AI 的明文 Markdown 与保留签名链的 `.kiyori-audit` 完整包。详细设计、
验收清单和设备边界见 [`ai_conversation_audit/index.md`](ai_conversation_audit/index.md) 的
“16. 2026-08-30 诊断中心 UI 与明文 AI 导出优化”。

## 2026-08-17 设置返回、浏览器窗口与会话恢复

状态：设置导航、普通网页窗口策略、popup 临时解析、配置主页跨站 opener、边缘滑屏和普通窗口
启动恢复已经完成本地实现；目标设备与真实网页矩阵尚未执行，保持 `verification_pending`。

当前冻结合同：

- 底部设置、Browser Menu 与 AI 左抽屉共用一个 `KiyoriSettingsNavigationState`。分类和子页按
  `KiyoriSettingsRoute` 逐级返回，Browser/AI 来源最终恢复原 Browser Home/WebSession 或原 AI
  页面/路由栈
- 普通网页同站、跨站、用户 `_blank` 和用户 `window.open()` 默认在当前窗口导航；自动 popup 和
  无稳定 HTTP(S) 目标的 popup 被拒绝。配置主页根上的真实用户跨站跳转是唯一自动保留主页窗口的例外
- “网页浏览器”设置为 `4/3/3/2/1/4/3` 七组 20 项；“滑屏前进后退 / 恢复上次的搜索结果 /
  询问是否恢复页面 / 保留多窗口”四项默认关闭，“长按网页元素菜单”默认开启并即时同步现有
  WebView，编辑控件继续使用 Android WebView 系统原生选区
- 普通窗口恢复只保存最小 URL 级投影；无痕窗口、Cookie、请求头、DOM、表单、正文、截图、密码和
  网络日志不进入恢复文件。搜索窗口离开已加载结果页后立即失去搜索恢复资格
- 2026-08-18 回归修正按入口来源恢复了设置首页底部五按钮，并把 Shell/Operit 设置的系统 Back
  处理器放到各自底层宿主之后、具体页面之前；Operit 深层子导航继承同一
  `KIYORI_SETTINGS` session，只有离开分类根页时才恢复设置首页。最终 App JVM 为
  `230 suites / 1376 tests`，formal readiness、architecture `phase=m03` 与
  `git diff --check` 均通过；Debug APK 为 `494168730` bytes，SHA-256
  `98B59B9BA408ABC3373AAD17BDE3141671B9314EDA778E07D26C6F92EC0D12D0`
- 2026-08-18 目标设备继续复测证明 Browser 来源设置返回并非只有前景层级问题：Browser
  首次创建后，其长期保留的系统 Back 回调可能比 Shell/Operit 设置回调更新，从而越过可见设置页，
  先消费隐藏网页历史，再把 Browser 退回软件首页。本次在保留前景 presentation 修正的同时，
  新增唯一 Browser 子树 Back owner：设置首页、Shell 设置子页和 Operit 设置详情可见时，
  Browser 根、搜索、书签与历史回调全部让位；只有
  `SUSPENDED_FOR_BROWSER_WORKSPACE` 重新归 Browser。Router pop 与设置 presentation 恢复也改为
  同一事务，缓存转场中的用户偏好页只在当前 route 消费 Back。不重置 AI Router，不修改当前网页、
  WebSession、窗口或网页历史。`KiyoriShellStateTest 67/67`、完整 App JVM
  `230 suites / 1379 tests`、architecture `phase=m03`、formal readiness、Markdown 本地链接和
  `git diff --check` 均通过；Debug APK 为 `494168730` bytes，SHA-256
  `A8EE926FCD4C68B6B4B50DB689112936376ADFA53ED5577D8BD21174A0C155ED`。目标设备保持
  `verification_pending`
- 2026-08-18 设置返回转场残影继续按双入口四象限取证：底部入口的 Shell 设置详情返回时，
  退出中的 Settings overlay 会读取已经恢复的 `HOME` route，与 Primary Root 设置首页重复绘制；
  Browser Menu/AI 抽屉来源的 Operit 设置详情返回时，Settings overlay 会从不可见重新进入，
  让恢复出的设置首页再次执行 `fadeIn + slideIn`。本次将 Settings surface 从 Shell child
  `AnimatedVisibility` 中彻底拆出：该动画宿主只承载 Full-Screen Search；底部设置首页只由
  Primary Root 绘制，来源设置首页和 Shell 设置详情只由直接、不透明的 Settings surface 绘制，
  Operit 设置详情只由 App Router 绘制。不改 Back owner、Router 恢复事务、AI Host 层级、
  Browser Runtime、WebSession 或主题所有权。`KiyoriShellStateTest 69/69`、完整 App JVM
  `239 suites / 1405 tests`、AndroidTest Kotlin/Java 编译、architecture `phase=m03`、
  formal readiness 与 `git diff --check` 均通过；Debug APK 为 `467107608` bytes，SHA-256
  `BFA17A75523E74EF7C0A44651443D52D660D9EDB15EB458314A3317E76DBB0F6`，包/版本/SDK、
  唯一 launcher、arm64-only、V2 单 signer 和 16 KB ZIP 对齐通过。目标设备视觉复测仍为
  `verification_pending`
- 项目 Python `220/220`、完整 JVM `229 suites / 1362 tests`、AndroidTest Kotlin/Java 编译、
  formal readiness、architecture `phase=m03` 与完整 Lint 均通过。Lint 最终只显示
  `GradleDependency 5 / NewerVersionAvailable 15 / UseKtx 3` 共 `23` 条既有范围诊断，
  未新增 suppress 或扩大 baseline
- 最终 Debug APK 为 `494168730` bytes，SHA-256
  `1A03F576811C482F4F1CB366532564B340553DCA2267CAB394B86F5067630E2F`；
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`、唯一 launcher、Android Debug V2 单 signer、
  16 KB ZIP 对齐、arm64-only 与 `52/52` 个 ELF64/AArch64 审计通过。提交和推送以本轮最终
  Git 远端对账为准

详细设计、实现对账、验证矩阵和设备验收边界见
[`kiyori_browser_navigation_and_session_restoration/index.md`](kiyori_browser_navigation_and_session_restoration/index.md)。

## 2026-08-17 FFmpeg 运行时、API 与 native closure 完善

状态：r5 安装包的真实 AI/设备复测已确认 Android FFmpeg 核心媒体链可完成流复制、软件与
MediaCodec H.264/HEVC、VP8/VP9/AV1/Theora/MPEG-2、14 类视频滤镜、音频、截图/GIF、拼接、
循环和 metadata/faststart；同时确认 `ffmpeg_convert` 把 constrained baseline 数值 `578`
误判、能力列表 stdout 丢失、`eq/boxblur` 受 GPL gate 缺失、`drawtext` 需要绝对 `fontfile`，
以及 Shell 管道被误传给 `ffmpeg_execute`。当前已完成 r6：profile 合同、能力 stdout bridge、
Shell token 校验、私有 FFprobe JSON、公开 `ffmpeg_probe`、分区 `ffmpeg_info`、signed AVERROR、
ToolPkg 结构化错误，以及 GPL/HarfBuzz/`drawtext`/`eq`/`boxblur` closure 和 GPLv3 资源；
source/thin/product promotion、native audit、52 项 FFmpeg 定向 Python、完整 Python
`218/218`、Gradle/JVM/AndroidTest/Lint `431` tasks、WebChat、ToolPkg、formal readiness、
fresh clone、architecture `phase=m03`、串行 Debug APK 构建和独立 APK 审计均已通过。最终
APK 为 `494168730` bytes，SHA-256
`5C12152E9F180B1454AFAF8FD72F370840B409E8703902E9BEDE6A434250285E`。r6 目标设备未重新
安装验收，保持 `verification_pending`。

详细目标、三执行面边界、缺陷清单、设计合同、验证矩阵、风险和后续优先级见
[`ffmpeg_runtime_completion/index.md`](ffmpeg_runtime_completion/index.md)；长期架构权威见
[`FFMPEG_ARCHITECTURE.md`](../doc-src/dev-core/FFMPEG_ARCHITECTURE.md)。

## 2026-08-15 广告拦截器、标记广告与网页元素操作

状态：本地实现、定向自动验证、正式门禁、Debug APK 构建与独立静态产物审计已完成；
目标设备触控、视觉和真实网页验收待完成，保持 `verification_pending`。

### 2026-08-15 五条内置订阅与设置页深化

状态：本地根因修复、真实五列表解析、定向自动验证、正式门禁、Debug APK 构建与独立产物审计已完成；
目标设备真实同步、站点命中、视觉和大列表性能仍待验收，保持 `verification_pending`。目标设备已确认
上一版五条内置订阅全部显示 `0`，手动更新失败，至少一条暴露 `EOFException`；问题已确认不是五个
订阅源失效，而是下载读取把“最多 32 MiB”错误实现成“必须读取 32 MiB + 1 字节”，正常文件到达
EOF 时必然失败。修复仍保持唯一 `BrowserAdBlockStore`，不引入第二广告引擎或旧
`libadblockplus` native 运行时。

#### 冻结方案

1. “广告拦截器 Pro”内置并默认启用：
   - `https://cdn.jsdelivr.net/gh/xun404/adblock@main/ad.txt`
   - `https://filters.adtidy.org/extension/chromium/filters/224.txt`
   - `https://adrules.top/adblock_lite.txt`
2. “Adblock Plus”内置并默认启用：
   - `https://easylist-downloads.adblockplus.org/easylistchina+easylist.txt`
   - `https://easylist-downloads.adblockplus.org/exceptionrules.txt`
3. 两个规则组只负责用户可见的来源、状态和管理分区；请求匹配、页面级例外、元素隐藏、
   白名单、统计、网络日志和现有 WebSession 规则重应用继续由唯一 `BrowserAdBlockStore` 持有。
4. 内置订阅使用稳定 ID，不能被改名、改地址或删除，但可以逐条启停和刷新；自定义订阅继续支持
   添加、编辑、启停、刷新和删除。首次没有本地载荷时自动同步已启用内置订阅，后续按内置更新周期
   在进程启动时检查，设置页同时提供自动更新开关、全部刷新和逐条刷新。
5. 订阅元数据与原始规则载荷分离原子存储。实时取证确认五份列表约为
   `0.63 MB / 0.79 MB / 1.63 MB / 2.63 MB / 18.79 MB`，其中
   `exceptionrules.txt` 曾暴露原先 `8 MB` 单订阅上限；当前载荷上限为 `32 MiB`，并且不得继续把
   全部解析规则写入每次设置变更都会重写的单一状态 JSON。
6. schema v1 严格迁移到 v2：保留总开关、白名单、自定义网址规则、自定义元素规则和已有自定义订阅；
   已缓存的 v1 订阅规则迁入独立载荷文件，五条内置订阅按 URL/稳定 ID 合并，不创建重复项。
7. 订阅解析支持当前 WebView 能够可靠提供证据的 ABP/AdGuard 子集：普通/锚定/通配/正则规则、
   `@@` 例外、`domain`、第一/第三方、常见资源类型、`match-case`、`important`、`badfilter`、
   页面 `document/elemhide/generichide/genericblock` 例外、标准 `##/#@#` 元素规则。
   `redirect/rewrite/csp/removeparam/scriptlet` 等动作型选项和 `#?#/#$#/#%#/#^#` 等扩展元素语法
   在不能正确执行时必须计入忽略，禁止退化成范围更宽的普通阻断规则。
8. `shouldInterceptRequest` 传入主框架、请求头和推导出的资源类型；主文档继续不被广告列表直接阻断，
   子资源才执行规则判定。规则刷新成功后请求 matcher 立即替换，现有 WebSession 的 DOM 规则由
   运行时状态观察者重新应用。
9. “网页元素”管理页按规范化域名分组。域名行显示启用数与总数，可展开/收起对应 selector；
   搜索命中域名或 selector 时自动展开匹配组，单条启停、编辑、删除和浏览器“标记广告”写入路径保留。
10. 设置首页使用现有 Kiyori 折叠设置视觉：状态卡展示总开关、进程拦截数、生效网址/元素规则和
    内置订阅就绪数；订阅页分开显示 Pro、Adblock Plus 和自定义订阅，错误、更新时间、有效规则数与
    忽略行数均可见，不把“已启用但尚未同步”表述为已生效。

#### 实施与验收计划

1. [DONE] 研究 hikerView 两条广告链、六张参考图、五份实时订阅体量与语法分布
2. [DONE] 审计 Kiyori 设置页、`BrowserAdBlockStore`、请求拦截、DOM 注入、网络日志和脏工作树
3. [DONE] 通过 `check_formal_readiness.py --require-main` 并冻结统一运行时方案
4. [DONE] 实现内置目录、schema v2、独立载荷、自动/手动更新和并发状态
5. [DONE] 实现请求上下文、页面级例外、安全选项解析、坏规则禁用和按域索引
6. [DONE] 重构设置页内置规则组、状态与刷新交互，完成元素规则域名折叠分组
7. [DONE] 增加目录、状态迁移、解析、匹配和真实五列表离线兼容性测试
8. [DONE] 同步 `CONTEXT.md` 与浏览器专项文档，运行定向测试、正式门禁、差异检查、
   Markdown 候选树检查和 Debug APK 构建核验
9. [DONE] 把订阅响应体读取改为有上限的分块 EOF 读取，补齐短响应、精确上限、超限和
   错误分类测试；复核五个实时 URL 的 HTTP 状态、体积与解析结果
10. [DONE] 重做订阅页摘要、批量同步进度、组级已开启/已就绪状态、单条可展开详情、
    明确错误和自定义订阅保存后同步逻辑
11. [DONE] 广告拦截专项矩阵、正式准备门禁、差异检查、提交后的官方 Markdown 候选树检查
    和 Debug APK 构建已重跑通过；Markdown 检查结果为 `errors=0 / warnings=0`
12. [PENDING] 在目标设备验证首次同步、真实站点命中、浅深主题、窄屏、展开收起、Back 和大列表性能

### 2026-08-15 广告拦截启动稳定性与网页加载性能

状态：卡死/闪退与请求热路径的本地根因修复、广告/设置/浏览器定向矩阵、
formal readiness、architecture `phase=m03`、差异检查、最终 Debug APK 构建与独立产物审计
已经通过；目标设备上的设置页反复进入、真实网页流畅度、内存峰值与长时间浏览仍保持
`verification_pending`。

现场症状是应用启动后首次进入“设置－广告拦截器”经常卡死或闪退，启用五条订阅后网页子资源
加载明显变慢。源码与五份实际载荷取证确认：

- 旧 `BrowserAdBlockStore` 构造函数会在首次调用线程同步读取约 `24.47 MB` 载荷，解析
  `124119` 条网络规则和 `65647` 条元素规则，再编译全部 matcher；设置页主线程与浏览器后台
  首次访问还会竞争同一个单例构造锁
- 每个 `shouldInterceptRequest` 旧实现按多个 rule set 和
  `important exception / important block / normal exception / normal block` 四条分支重复进行
  URL 小写化、host/site 解析、4 字符 substring 扫描和候选集合构造
- 页面元素路径约含 `18707` 条通用 selector；旧实现会在主线程创建完整决策、完整 ELEMENT
  日志列表和单一大型 JavaScript/CSS 文本，并可能为每条网络请求投递一次 Compose 刷新
- 反向审查还确认：旧自定义规则增删会在设置页点击线程重新聚合约 `12.4` 万条订阅规则；
  同一页面首批并发子资源可能同时计算相同 page policy，拦截计数节流尾部也存在最后一次更新
  等待下一条请求才能发布的竞态

本轮冻结实现：

1. [DONE] `BrowserAdBlockStore.getInstance()` 只创建轻量 Store 并立即返回；状态文件读取、schema
   迁移、载荷解析和编译进入单一 IO 初始化生命周期
2. [DONE] 设置页显示 `读取设置 / 编译本地规则 / 已就绪 / 初始化失败`，编译期间禁用修改和刷新
   动作；请求线程只读取当前不可变 matcher，不等待初始化锁
3. [DONE] 五条订阅编译为一个聚合 host/token 索引，自定义规则使用可独立替换的小型索引分区；
   两者仍由同一不可变 matcher 合并到一个候选集合，每个请求只规范化 URL、page host、
   request host 与 party 事实一次，并完成全局四级 ABP 优先级判定。手动规则增删不再扫描全部订阅
4. [DONE] token 索引从固定 4 字符 substring 改为 `4..8` 字符无 substring 的滚动哈希，
   普通 ABP 通配/分隔符规则使用轻量匹配器，只有显式 `/regex/` 保留 JVM Regex
5. [DONE] page policy、top-private-domain 与元素决策使用 matcher 快照内有界缓存；同一 key
   首次 miss 只计算一次，避免并发子资源重复扫描；快照替换即整体失效，不新增第二缓存 owner
   或复杂失效协议
6. [DONE] 元素决策与 CSS 文本在 IO 线程准备，按 `64 KiB` 分块；同一
   `document token + ruleRevision` 只注入一次，MutationObserver 只服务 Hiker 风格 `&&` selector
7. [DONE] 网络日志只把用户创建的元素规则记录为 `ELEMENT`，订阅 cosmetic selector 继续完整执行
   但不伪装成数万条网络请求；网络日志 UI 刷新按 `100 ms` 合并，进程拦截计数按 `500 ms`
   竞态安全地发布，最后一次增量无需等待后续网络请求
8. [DONE] 增加异步启动源码合同、聚合 token 选择性、跨索引分区优先级/元素例外和元素页面缓存测试；
   临时真实列表探针在记录基线和优化结果后删除，不成为仓库测试依赖
9. [DONE] 完整广告/设置/浏览器定向矩阵通过 `124/124`，formal readiness、architecture
   `phase=m03`、Git 候选文件集合 Markdown `305/305` 与 `git diff --check` 通过；
   最终 Debug APK 构建和独立产物核验完成
10. [PENDING] 目标设备连续冷启动并反复进入设置页，使用同一批真实网页对比首屏、滚动、视频、
    动态 DOM、内存峰值、后台切换和长时间浏览稳定性

同机 JVM 临时探针的结构化证据：

- 旧 matcher：编译堆增量 `268233632` bytes；`2000` 次请求匹配 `43397 ms`
  （平均 `21698.5 µs`）；20 个页面元素决策 `783 ms`
- 聚合索引阶段 matcher：相同规则与相同请求命中数仍为 `286`；编译堆增量降为
  `164696560` bytes
  （减少约 `38.6%`）；`2000` 次请求匹配 `1142 ms`（平均 `571 µs`，约快 `38` 倍）；
  20 个页面元素决策 `105 ms`。最终实现复用同一订阅聚合索引，仅追加独立的小型自定义分区，
  不复制订阅规则对象
- 该探针证明 JVM 结构与算法改善，不替代 Android System WebView、目标设备 OOM/ANR、页面视觉
  或真实站点兼容性验收

最终本地交付证据：

- `:app:testDebugUnitTest` 覆盖 9 个广告、设置与浏览器测试类，合计 `124` 项，失败、错误和跳过
  均为 `0`
- `check_formal_readiness.py --require-main` 与 `check_architecture_boundaries.py --require-main`
  通过，architecture 为 `phase=m03`；Git 候选文件集合的 Markdown 等价检查为
  `305` 个文件、`0` 个错误，`git diff --check` 无 whitespace error
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 23s`，
  `232 actionable tasks: 22 executed, 210 up-to-date`；唯一 launcher 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `471058927` bytes，
  SHA-256 `64FE05CB67BBCC040923C476AE7E7825407BA60D49EC55DE5282C3F7E8C4E5B7`。
  宿主文件时间为 `2026-08-16 00:05:55 +08:00`，任务会话日期仍按 `2026-08-15` 记录；
  包名/版本/SDK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`
- APK 使用 Android Debug V2 单 signer，证书 SHA-256 为
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`，
  `zipalign -c -P 16 -v 4` 通过；仅包含 `arm64-v8a` 的 `51` 个 `.so`，无重复 basename，
  包含 `liboperit_ripgrep.so` 与 `assets/operit_shell_exec`，不包含 `libsudo.so`
- `51` 个 `.so` 加 shell launcher 共 `52` 个文件全部为 ELF64/AArch64，`160` 个 `PT_LOAD`
  的最小对齐为 `0x4000`；shell launcher 只依赖 `libandroid.so / liblog.so / libm.so /
  libdl.so / libc.so`，不依赖 `libc++_shared.so`

### 2026-08-18 广告拦截编译快照与启动缓存

状态：五条预置订阅的持久化编译快照、逐订阅 Engine 分区、内容去重刷新、定向自动验证、
正式门禁、Debug APK 构建与静态产物核验已完成；目标设备首次迁移、第二次启动缓存命中、
冷启动性能、真实网页和长时间内存稳定性仍保持 `verification_pending`。

本轮在唯一 `BrowserAdBlockStore`、唯一 Browser Runtime 和不可变 matcher 合同内完成：

1. [DONE] 状态升级为 schema v3，记录已提交原始载荷的 SHA-256、字节数和存储版本；原始载荷
   迁移到 `filesDir/browser_ad_block_subscriptions/<subscription-id>/<payload-sha256>.txt`
   的内容寻址布局
2. [DONE] 派生编译快照进入 `noBackupFilesDir/browser_ad_block_compiled/<format-version>/`
   `<compiler-contract-id>/<subscription-id>/`；二进制 codec 保存完整网络/元素规则语义、
   `badfilter`、host/token/unindexed 网络索引和元素域索引，并校验身份、正文摘要、数量、
   长度、bucket 与 rule index 边界
3. [DONE] 每条订阅拥有独立 Engine 分区。有效缓存命中直接恢复规则和既有索引引用，不读取大型
   原始文本，不进入订阅逐行解析、完整规则编译或 host/token/domain 索引重算；全部分区就绪后
   仍一次性组合并发布唯一 matcher
4. [DONE] 单条快照缺失或损坏只从该条权威原始载荷重建；单条订阅内容变化只替换对应分区，
   其余订阅分区不重新编译
5. [DONE] 订阅刷新先比较精确 SHA-256。下载内容与已提交内容相同时，不解析、不编译、不替换
   Engine、不递增 `ruleRevision`，也不触发重复 DOM 规则应用；只更新同步时间并清除错误
6. [DONE] 设置页区分读取设置、加载本地编译规则、编译已变化规则、已就绪和初始化失败，并显示
   `cacheHitCount / cacheMissCount / cacheInvalidCount / compiledSubscriptionCount` 对应结果
   与编译快照持久化警告

最终本地交付证据：

- 广告、设置与浏览器定向矩阵覆盖 10 个 suite、`131` 项测试，失败、错误和跳过均为 `0`；
  `BrowserAdBlockCompiledCacheTest` 覆盖确定性编码、完整字段 round-trip、显式正则恢复、
  网络/元素匹配等价、`badfilter`、正文 SHA-256、身份不匹配、截断和正文损坏
- `check_formal_readiness.py --repository . --require-main` 与
  `check_architecture_boundaries.py --repository . --require-main` 均通过，architecture 为
  `phase=m03`；`git diff --check` 无 whitespace error
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 15s`，
  `232 actionable tasks: 23 executed, 209 up-to-date`；唯一 launcher 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `494168730` bytes，
  SHA-256 `235A25DB2D1978D66E364F16C6488E53C2D260EB76155B692F684C17292FBECE`，
  文件时间 `2026-08-18 13:06:33 +08:00`；包名/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`
- APK 使用 Android Debug V2 单 signer，证书 SHA-256 为
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`，
  `zipalign -c -P 16 -v 4` 验证通过，ABI 为 `arm64-v8a`

仍需在目标设备确认 schema v2 真实用户数据首次迁移、第二次启动
`cacheHitCount=5 / compiledSubscriptionCount=0`、十次强制停止冷启动、Java heap/PSS/GC/CPU/
首屏帧改善、真实网页阻断/例外/元素隐藏/网络日志兼容、相同内容自动更新不重复应用 DOM 规则、
单条真实内容变化只重建一个分区，以及进程回收和长时间浏览稳定性。详细设计、失效矩阵与验收
清单见 [`kiyori_browser_product_completion/15_adblock_compiled_runtime_cache.md`](kiyori_browser_product_completion/15_adblock_compiled_runtime_cache.md)。

### 任务目标

在唯一 Browser Runtime、WebSession 和 Android System WebView 上建立 Kiyori 原生广告拦截能力：
接通设置首页“广告拦截器”，完成浏览器菜单“网络日志”和“标记广告”，在网络日志“其他”右侧
增加“拦截”筛选，并为网页元素长按提供按目标能力生成的现代化操作弹窗。功能设计参考
`D:\10_Project\hikerView` 与用户提供的七张参考图，但视觉、状态所有权和交互层级必须适配
Kiyori 现有设置、浏览器抽屉和模态体系。

### 非目标与授权边界

- 不移植 hikerView 的 X5/TBS、旧 Activity/EventBus/LitePal 架构或旧版 Adblock Plus native 二进制
- 不创建第二 WebView、第二网络日志、第二浏览器状态或并行广告设置源
- 不在 `shouldInterceptRequest` 后台线程访问 WebView、WebSettings、DOM 或 Compose 状态
- 不写回退、降级、双轨兼容或静默吞错逻辑；解析、下载和持久化失败必须形成明确状态
- 除本轮已核验并默认启用的五条内置列表外，不再预置其他远程订阅；自定义订阅由用户显式添加并刷新
- 不提交、不推送、不安装 APK，不执行 ADB、模拟器或真机操作

### 采用的功能与状态设计

1. `BrowserAdBlockStore` 是唯一持久化 owner，schema v2 状态保存总开关、站点白名单、
   自定义网址规则、自定义元素规则和订阅元数据；原始订阅规则使用独立原子载荷文件保存，
   同时发布不可变编译快照供后台请求线程只读匹配
2. 请求级拦截在现有 `WebViewClient.shouldInterceptRequest` 中先计算广告决定，再把允许或拦截事实
   写入同一个每会话 500 条网络日志；拦截返回明确空响应，不触发第二次网络请求
3. 网址规则兼容 hikerView 常用的普通包含式规则，并支持必要的 Adblock 形式：
   `||host^`、`|prefix`、`suffix|`、`*` 通配和 `@@` 例外；订阅同时解析 `domain##selector`
   元素隐藏规则。无法识别的行不伪装为有效规则，订阅页显示有效与忽略数量
4. 元素隐藏按页面 host 解析标准 CSS selector 与 hiker `&&` 路径规则，在 `onPageFinished`
   和源码工作台 Apply 后注入同一文档；`style` 节点属于 Kiyori 运行时，页面源码快照和标记
   工作台需要排除 Kiyori 自有 UI
5. “标记广告”进入原生 Compose 底部工作台，网页脚本只负责元素命中、稳定 selector、
   HTML 摘要与高亮。工作台作为实时 WebView 下方的紧凑固定高度 sibling 占位，不覆盖页面内容；
   WebView 使用剩余实测高度，页面滚动到底时文档底边与工作台顶边相接。原生触摸层把轻点解释为
   抬手位置选择，把纵向拖动和达到系统阈值的抬手速度解释为滚动与惯性滑动，整个手势不进入 DOM；
   上栏固定为“拦截规则 / 节点 / HTML / 父 / 兄 / 弟 / 子 / 关闭”，下栏固定为
   “保存规则 / 编辑规则 / 预览 / 重置 / 清除拦截 / 拦截网站跳转”。编辑只修改当前草稿，
   底栏复用浏览器底栏 `50dp` 内容高度并使用 `40dp` 文本按钮；清除确认显示当前规范化域名并
   按域清理自定义规则，网站跳转策略使用独立底部抽屉；保存后写入当前 host 的元素规则并立即应用
6. 网页长按沿用现有注入式触摸所有权，按命中目标动态提供：
   “新窗口打开 / 后台打开 / 复制链接 / 复制文本 / 外部打开 / 选择文本 /
   拦截网页元素 / 拦截过滤网址”；没有对应链接、文本或资源 URL 的动作不显示
7. 设置页复用 Kiyori 折叠设置视觉，首页提供总开关、当前统计、网址过滤、网页元素、
   站点白名单和订阅入口；各管理页支持搜索、添加、启停、编辑、删除和明确空态
8. 网络日志新增独立“拦截”筛选，请求拦截与网页元素拦截共同显示；元素条目以 `DOM`、
   当前页面 URL 和可读拦截规则表达，不伪装成 HTTP 请求；列表、详情和操作弹窗显示命中
   来源及规则，并仅对请求条目提供网址规则、下载和外部打开操作

### 细化计划

1. [DONE] 读取 `AGENTS.md`、正式开发准备五份清单、Git 基线、相关任务日记和现有文档合同
2. [DONE] 研究 Kiyori 设置首页、Shell 子页、浏览器菜单、网络日志、请求采集、文本选择和
   页面脚本注入链路
3. [DONE] 研究 hikerView `AdblockHolder`、`AdBlockRule`、`AdBlockUrl`、订阅格式、
   网页长按与七张参考图，确认请求阻断与元素隐藏两层模型
4. [DONE] 实现纯策略模型、唯一规则 store、原子持久化、订阅解析与定向单元测试
5. [DONE] 接入 `shouldInterceptRequest`、网络日志 REQUEST/ELEMENT 拦截字段/筛选/详情和页面元素隐藏注入
6. [DONE] 实现长按元素动作桥、动态操作弹窗与前台/后台新窗口、复制、外部打开和拦截动作
7. [DONE] 实现“标记广告”元素选择脚本、原生工作台、DOM 导航、预览、保存、域级清除和
   三态网站跳转策略；补齐媒体/source/background-image 资源识别、pointer/touch/click 捕获、
   原生触摸独占、轻点选择、纵向拖动/惯性滑动以及任意 scheme、frame、popup 导航硬锁
8. [DONE] 接通设置首页、广告拦截主页面及网址/元素/白名单/订阅管理页
9. [DONE] 同步 `CONTEXT.md`、浏览器菜单能力、设置首页计划、架构文档和资源
10. [DONE] 运行定向 JVM、Kotlin 编译、正式门禁、资源/XML、Markdown、差异检查和
    `:app:assembleDebug`，独立核验 Debug APK 身份、签名、16 KB ZIP 对齐、ABI 与 ELF
11. [PENDING] 目标设备验收长按冲突、标记模式、动图/视频/iframe 点击、动态页面元素、网络日志、
    规则实时生效、浅深主题、窄屏、Back 顺序和真实站点兼容性

### 验收标准

- 总开关、白名单、网址规则、元素规则和订阅只有一个持久化 owner，所有 UI 与 WebView 消费同一快照
- 被命中请求不会继续交给 userscript 或系统网络加载；请求和已应用网页元素规则都可在“拦截”
  筛选中复核规则来源，元素条目不伪装 HTTP 请求
- 元素规则保存后当前页立即隐藏，刷新和同 host 后续页面继续应用；退出标记模式不残留高亮或触摸拦截
- 标记工作台出现时 WebView 使用剩余实际高度，面板左右下贴边，页面滚动到底后最后一个元素仍可
  在工作台上边缘之上点击；标记模式轻点选择、纵向拖动/惯性滑动滚动 WebView，并无条件吞掉
  进入 DOM 的触摸、iframe、自定义 scheme 和 popup 导航。退出标记模式后，
  “默认允许 / 跳转前询问 / 拦截跳转”只控制正常浏览的跨域目标，当前 host 导航保持可用
- 长按链接、图片、普通文本和无动作区域分别显示与目标能力一致的操作，不破坏现有文本选择入口
- 浏览器菜单、来源保持型设置首页和普通设置首页的 Back/子页层级保持现有合同
- 自动测试、静态检查、Debug 构建和 APK 核验通过；设备与真实网页体验未执行时保持
  `verification_pending`

### 当前本地实现证据

- `BrowserAdBlockStore` 是唯一规则持久化 owner；设置页、网络日志、元素长按和标记工作台均消费同一状态
- `shouldInterceptRequest` 的客户端阻断决定写入当前 WebSession 网络日志，DOM 元素规则注入同步
  `ELEMENT` 条目；blocked 行不会伪装成服务端 HTTP 失败
- 当前扩展矩阵覆盖 `BrowserAdBlockPolicyTest` `20/20`、
  `BrowserAdBlockSubscriptionCatalogTest` `4/4`、`BrowserAdMarkingTouchPolicyTest` `4/4`、
  `BrowserNetworkLogPolicyTest` `7/7`、`WebSessionBrowserBackPolicyTest` `3/3`、
  `KiyoriSettingsPagesTest` `13/13`、`KiyoriShellStateTest` `57/57` 与
  `WebSessionBrowserChromeLayoutTest` `7/7`，合计 `119/119`，零失败、零错误、零跳过；
  `:app:compileDebugKotlin` 与注入 JavaScript 语法编译通过
- 正式开发准备检查、architecture boundary `phase=m03`、七份 `strings.xml` 解析和
  `git diff --check` 均通过；官方 Markdown 候选树命令因当前工作树未提交且创建临时 Git index
  被宿主 Git 写入安全策略拒绝未执行，同一解析函数的 dirty-worktree 只读等价检查为
  `errors=0 / warnings=0`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 1m 19s`，`232` 个任务，`22 executed / 210 up-to-date`；
  唯一 Debug launcher 与 player runtime packaging 验证通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-15 21:02:04 +08:00`，`471058927` bytes，SHA-256
  `FA5D003FCBCDCBC01CDB1A56B774E6A0BFFFCE113853588D00B325C923890E5E`
- 独立 `aapt`、`apksigner` 与 `zipalign` 审计确认 APK 为 `com.kiyori`、
  `versionCode 45`、`versionName 0.1.0`、min/target/compile SDK `26 / 34 / 37`、
  `debuggable=true`、唯一 `com.ai.assistance.operit.ui.main.MainActivity` launcher、
  仅 `arm64-v8a`、Android Debug V2 单 signer，`zipalign -c -P 16 -v 4` 为
  `Verification successful`
- ZIP/ELF 审计确认 `51` 个 `.so` basename 零重复，包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`、不含 `libsudo.so`；共 `52/52` 个 ELF64/AArch64，
  `160` 个 `PT_LOAD` 的最小 alignment 为 `0x4000`，零失败
- 未提交、未推送、未安装 APK，未执行 ADB、模拟器或真机操作；真实网页规则命中、
  动图/视频/iframe 点击、远程订阅兼容性、浅深主题、窄屏和 Back 顺序仍待目标设备验收，
  保持 `verification_pending`

## 2026-08-14 历史记录长按操作与批量删除

状态：本地实现、自动验证和 Debug APK 核验已完成；目标设备交互与视觉验收待完成，保持
`verification_pending`。

### 任务目标

为浏览器菜单与负一屏共用的“历史记录”下拉抽屉增加单条长按操作弹窗，并按网页和视频的真实
能力分别设计操作集合；在不创建第二历史、书签、浏览器或播放器 owner 的前提下，补齐单条精确
删除和可选择的批量删除。

### 非目标与边界

- 不改变普通/无痕历史持久化策略、网页访问写入、媒体重播身份、PlayerSession 或 Browser Runtime
- 不把视频直链或本地媒体 URI 当成网页书签；视频只在保存了有效来源网页时显示“打开来源网页”
- 不为当前没有真实写入 owner 的音乐、小说和其他分类虚构专属长按动作
- 不增加回退、兼容开关、第二套历史页面或并行数据库
- 不提交、不推送、不安装 APK，不执行 ADB、模拟器或真机操作

### 细化计划

1. [DONE] 核对 `AGENTS.md`、正式开发门禁、Git 基线、共享历史 owner、浏览器/负一屏宿主、
   网页与媒体重播链路及现有长按弹窗
2. [DONE] 确定 UI 基准：复用 `WebSessionBrowserModalDialog`、
   `WebSessionBrowserDialogSurface`、历史入口语义色和 `48dp` 操作行
3. [DONE] 网页记录提供“打开网页 / 加入书签 / 复制链接 / 复制标题 / 删除记录 /
   批量删除”；加入书签继续打开现有书签编辑弹窗
4. [DONE] 视频记录提供“播放视频 / 打开来源网页（仅有效来源）/ 复制视频链接 /
   复制标题 / 删除记录 / 批量删除”，继续调用唯一 PlayerSession 和 Browser Runtime
5. [DONE] 长按进入操作弹窗；批量删除进入选择模式，保留搜索与分类，支持当前结果全选、
   取消、精确删除和二次确认
6. [DONE] 为 `WebSessionHistoryStore` 增加按 `url / category / visitedAt` 精确身份删除，
   不改变现有序列化 schema 和分时段删除
7. [DONE] 更新 `CONTEXT.md` 与浏览器菜单能力文档，补充网页/视频动作、精确删除和批量
   选择策略测试
8. [DONE] 运行定向 JVM、正式开发准备、资源/XML 与差异检查，串行构建并核验 Debug APK
9. [PENDING] 目标设备验收长按时长、弹窗层级、浅深主题、窄屏、复制 Toast、书签编辑、
   网页/视频打开、单条删除、当前筛选范围批量选择与 Back 顺序

### 验收边界

- 自动测试与 Debug APK 只能证明策略、状态接线和构建产物；长按触感、弹窗视觉、触控热区和
  Browser/Player 现场跳转仍需目标设备验收
- 浏览器菜单与负一屏必须继续组合同一个 `WebSessionHistorySheet`，两处不得复制交互状态或 mutation

### 本地验证证据

- `:app:compileDebugKotlin` 与首轮 `WebSessionHistoryPolicyTest` 通过；历史策略最终为 `5/5`
- `WebSessionHistoryPolicyTest`、`WebSessionBookmarkPolicyTest` 与 `KiyoriShellStateTest`
  合计 `68/68`，零失败、零错误、零跳过
- architecture boundary `107/107`、formal readiness、七语种 `49` 个
  `web_session_history_*` 资源键与占位符一致性、`git diff --check` 均通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 58s`，`232` 个任务，`22 executed / 210 up-to-date`；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-14 23:29:15 +08:00`，`471054375` bytes，SHA-256
  `BDF9312092D368780F256450EE4A2039CA56C59538D7BE2966AD497BBFBAE2A9`
- APK 为 `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、min/target/compile SDK
  `26 / 34 / 37`、`debuggable=true`、仅 `arm64-v8a`；Android Debug V2 单 signer，
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- 未提交、未推送、未安装 APK，未执行 ADB、模拟器或真机操作

## 2026-08-14 设置标题、主题菜单与来源保持导航（历史）

状态：本地实现、自动验证和 Debug APK 构建已完成；浏览器菜单、AI 抽屉、主题菜单间距与返回交互
仍待目标设备验收，保持 `verification_pending`。

### 任务目标

统一设置首页与设置详情页的用户可见名称，压缩主题快捷菜单的横向留白，并让浏览器菜单与 AI
左抽屉都进入同一个来源保持型设置首页：该设置首页覆盖在来源页面之上，不显示软件首页底部五
入口，Back 逐层返回设置首页和原来源页面。

### 非目标与边界

- 不创建第二套设置首页、设置偏好、Browser Runtime、AI 路由 owner 或持久化状态
- 不改变浏览器、播放器、下载器和 AI 设置的业务设置内容
- 不加入回退、兼容开关或并行旧入口；Kiyori 尚未发布，本轮直接替换当前内部导航方案
- 不提交、不推送、不安装 APK，不执行 ADB、模拟器或真机操作

### 细化计划

1. [DONE] 核对 `AGENTS.md`、正式开发准备清单、Git 基线、设置/浏览器/AI 抽屉调用链与既有任务记录
2. [DONE] 将“小说阅读器”统一为“文档阅读器”，让网页浏览器、视频播放器、文件下载器
   详情标题直接复用设置首页对应名称
3. [DONE] 将主题快捷菜单从 `172dp` 缩窄到 `156dp`，保持唯一主题 owner 与三项顺序不变
4. [DONE] 新增来源保持型 `KiyoriShellChild.SETTINGS_HOME`，复用现有
   `KiyoriSettingsHomePage`，在覆盖式展示中提供返回动作并隐藏底部五入口
5. [DONE] 浏览器菜单第四行第三项改为通用“设置”并打开覆盖式设置首页；AI 左抽屉底部同样
   显示“设置”并保持当前 AI 页面/子栈
6. [DONE] 覆盖式设置首页进入浏览器、下载器、播放器详情时使用嵌套子页；进入 AI、账号、
   语音、界面和数据根时压入当前 AI 路由栈，Back 先回设置首页再回来源页
7. [DONE] 更新 `CONTEXT.md`、导航架构/决策与设置视觉文档，增加标题、菜单宽度、底栏与
   来源返回合同测试
8. [DONE] 运行定向 JVM、正式开发准备、资源/XML 与差异检查，串行构建并核验 Debug APK

### 验收边界

- 自动测试与 Debug APK 只能证明静态合同、状态转换和构建产物；浏览器菜单、AI 抽屉、主题弹窗
  间距、返回手势和不同窗口尺寸的视觉仍需目标设备验收
- 浏览器或 AI 页面进入设置首页后，来源页面必须保持原状态；不得以切换到底部设置主目的地模拟返回

### 本地验证证据

- `:app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`
- `KiyoriSettingsPagesTest` `13/13`、`KiyoriShellStateTest` `56/56`、浏览器菜单色彩/布局与
  `MainActivityBrowserActionTest` `13/13`，合计 `82/82`，零失败、零错误、零跳过
- 项目 `.venv` 的 architecture boundary `107/107`、formal readiness、七份 `strings.xml`
  解析、worktree Markdown 链接和 `git diff --check` 均通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 59s`，`232` 个任务，`22 executed / 210 up-to-date`；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-14 22:38:50 +08:00`，`471035851` bytes，SHA-256
  `28BFBC295AEA0433C279FC7EFCF9BE6FF37468E306B5E3C33CCBF4384CAD821C`
- APK 为 `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、min/target/compile SDK
  `26 / 34 / 37`、`debuggable=true`、仅 `arm64-v8a`；Android Debug V2 单 signer，
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- 未提交、未推送、未安装 APK，未执行 ADB、模拟器或真机操作

## 2026-08-14 全项目深度整理与质量优化

状态：`verification_pending`

### 任务目标

在保持 Kiyori 现有产品合同、数据、协议、状态所有者和用户工作不受破坏的前提下，分阶段完成：

1. 深度理解并审计根目录、Gradle 模块、子模块、工具、文档、生成目录和本地构建输入
2. 规范目录与文件命名，消除职责不清、重复入口、过时说明和无效文件
3. 把根 README、英文 README、文档索引、构建与贡献资料整理为专业、可查找、可操作的用户与开发入口
4. 证据驱动清理缓存、旧审计产物和垃圾文件，同时保护本地 AAR、模型、subpack、JNI、`.venv` 和外部链接目标
5. 修复范围内发现的真实代码、Lint、测试、稳定性、性能和包体积问题
6. 运行仓库等价的全量门禁、测试、Lint、构建和 APK 审计
7. 完成敏感内容、暂存树、子模块、异常大文件和远端竞争审计后提交并推送 `main`

### 非目标与硬边界

- 不执行 Release/AAB 发布、部署、商店操作、设备安装、ADB、模拟器或真机交互
- 不通过 fallback、回退、隐式重试、suppression、Lint baseline 扩张、禁用检查或伪造 ABI 掩盖问题
- 不机械全局替换 `com.ai.assistance.operit`、AIDL、JNI、Intent、authority、数据库、备份、ToolPkg、MCP 或市场协议标识
- 不使用 `git clean -X`：该命令会同时列出缓存和本机构建必需输入
- 不在缺少性能采样、调用链或包体证据时进行随机“优化”

### 任务起始基线

- 父仓库：`main@96259e2ae99aab16f1ae6cfb9165ea0fe32e5539`
- 父仓库本地、`origin/main` 与远端 `main`：一致，divergence `0/0`
- `terminal`：`d330366cfaff7ca73383b71504ec1a9a42e8a1c2`，工作树干净
- `tools/hotbuild/OperitNightlyRelease`：未初始化固定 gitlink，不是垃圾目录
- 正式开发准备：`check_formal_readiness.py --require-main` 通过
- Debug APK 基线：`472502409` bytes；压缩后 `assets/` 约 `295026456` bytes，
  `lib/` 约 `78057322` bytes
- 包体最大来源：语音模型、Ubuntu rootfs、Android subpack、apktool ToolPkg、native runtime、
  字体和多 DEX；优化必须保持对应功能合同

### 既有计划复用

本任务不创建平行架构来源，按领域复用并校对以下专项：

- [构建系统重构](refactor_building_sys/index.md)
- [Tools 目录重整](tools_directory_reorganization/index.md)
- [正式开发准备](formal_development_readiness/index.md)
- [全量 UI 与功能逻辑链路审计](kiyori_full_ui_and_function_chain_audit/index.md)
- [启动性能优化](kiyori_startup_performance/index.md)

### 分阶段实施

1. [DONE] **规则与基线**：读取项目规则、正式开发门禁、Git/子模块、历史全仓验证矩阵和任务日记
2. [DONE] **文档与根目录入口**：重写中英文 README、文档索引、文档规范、仓库布局和元数据
3. [DONE] **目录与命名审计**：建立受保护路径、稳定标识、可迁移目录和候选重命名清单，按证据实施
4. [DONE] **垃圾与缓存治理**：解析 Junction/符号链接，核对进程和可再生性后清理构建、CMake、
   Gradle、Node、Python 与旧审计产物
5. [DONE] **代码与构建质量**：修复重复配置、错误、警报、依赖与构建入口问题，不扩大 baseline
6. [DONE] **稳定性与性能**：按构建链、资产重复、依赖解析和包体证据完成当前可验证优化
7. [DONE] **全仓验证**：运行 Python、架构、Markdown、本地化、WebChat、ToolPkg、AndroidTest、
   所有 Gradle 模块测试与 Lint
8. [DONE] **最终 Debug APK**：串行 `:app:assembleDebug`，独立核验身份、签名、ZIP、ABI、ELF、
   launcher、native owner 和主要包体来源
9. [DONE] **交付**：审计最终树、敏感信息、子模块、暂存内容和远端竞争；先提交推送
   `terminal`，再以已推送 gitlink 构造、验证并提交父仓 `main`

### 清理分类

- **可再生成缓存**：`.gradle/`、`.gradle-user-home-*/`、`.kotlin/`、各模块 `build/` 与 `.cxx/`、
  `node_modules/`、`web-chat/dist/`、Python `__pycache__/`
- **受保护本地输入**：`.venv/`、`app/libs/`、`app/src/main/assets/models/`、
  `app/src/main/assets/subpack/`、`app/src/main/jniLibs/`
- **需单独审计**：`work/`、Junction/reparse point、旧日志、下载归档、测试制品与未初始化子模块

### 全量验收矩阵

- 项目 `.venv`：`ci/test`、architecture、formal readiness、fresh clone、Markdown、本地化和仓库卫生
- JavaScript：根工具、WebChat typecheck/build、GitHub 示例确定性生成、ToolPkg/WASM 打包
- Android：根聚合 `testDebugUnitTest`、AndroidTest Kotlin/Java 编译、所有模块 `lintDebug`
- 构建：串行 `:app:assembleDebug --no-daemon --console=plain`
- APK：`aapt`、`apksigner`、`zipalign -P 16`、ZIP 清单、NDK `llvm-readelf`
- Git：父仓库与 `terminal` 的状态、差异、敏感内容、模式、异常大文件、gitlink 和远端 ref 对账

### 当前增量

- 已完成中英文 README、文档中心、文档规范、构建指南、贡献指南、仓库布局、
  Issue Forms 和根工具元数据重构；英文入口统一为 `README.en.md`
- 已删除 `work/`、仓库内 Gradle/Kotlin/CMake/Node/Python 缓存与生成产物；仅 `work/`
  清理约 `3.15 GB / 20805` 个文件。外部 Gradle 缓存、`.venv`、AAR、模型、subpack 和 JNI
  均保持不变
- 已删除 `docs/assets/` 下 `56` 个零引用图片（`26401148` bytes）和三份生成报告；
  文档资源改为按需创建且必须具有正式引用
- 已恢复 ARCH039/041/042/043/044 机器合同，architecture `phase=m03` 与
  Python `186/186` 均通过，没有放宽 owner 或新增 suppression
- 已把散落依赖版本收口到 Gradle version catalog，显式保留
  `hnswlib-core 1.2.1 / hnswlib-utils 0.0.46`，消除隐式冲突选择、重复测试依赖和重复
  `libsu` alias
- 已把 ToolPkg 预构建从混用 pnpm 改为复用 npm lockfile；测试/生产双模式后
  `npm ls` 无 extraneous、未生成 `pnpm-lock.yaml`，生产 assets 无差异
- Android 与 Flutter 工作区模板共用唯一 `4706040` bytes AAPT2 源资产，并在工作区创建时
  物化到原有目标路径；旧两份重复源码资产已删除
- 已把 JNI API 敏感的 llama.cpp 与 MNN 从移动 `master` 收口到精确 commit：
  `885c5bbe8e04dc78db25beb911a2715312ad7b54` 与
  `ea44a3ebd5dd6348eea501047b17c43aa3ecccb6`；正式 readiness 和 CMake 单元测试阻止恢复为移动 ref
- 已修复 llama penalty sampler 的 `n_vocab` API、MNN 非产品 LLM demo/default-all 目标、
  CMake 3.22 的 40 位 SHA 判断，以及 DragonBones 的现代 iterator、`Path` 枚举和死代码；
  DragonBones native 构建同时从四套 ABI 收口到产品唯一 `arm64-v8a`
- 根工具、WebChat、GitHub 示例、WASM ToolPkg、生产 ToolPkg 白名单均已从 lockfile 重建；
  WASM ToolPkg 连续两次生成均为 `3094` bytes、SHA-256
  `A2BE9C593EAAF0C597F16125445762369BAF31890B6DAC0FB1E21DB0C87AB04A`
- 完整 Gradle 聚合矩阵为 `BUILD SUCCESSFUL in 15m 28s`，`431` tasks；
  JVM 汇总 `217 suites / 1251 tests`，失败、错误和跳过均为 `0`，AndroidTest Kotlin/Java
  编译通过，完整日志无 Kotlin/compiler warning
- Lint 临时完整结果为 `5279` 条；结构化交集
  `retained=5256 / stale=24 / current-only=23`，正式 baseline 未新增记录，SHA-256
  `B0A52E2B1C42516B84BB1B22E0940A8DB3130840D5D754E09BDD2444A366D621`
- 最终 App Lint 为 `23 warnings`：`GradleDependency 5 / NewerVersionAvailable 15 / UseKtx 3`；
  Terminal 保留 arm64-only 合同对应的 `ChromeOsAbiSupport 1`
- 最终 `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 11m 57s`，
  `232` tasks（`43 executed / 189 up-to-date`）；唯一 launcher 与 player runtime packaging 通过
- 最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-14 20:26:18 +08:00`，大小 `463101042` bytes，SHA-256
  `F5763BC7C066FB375099D04E1A2B4D6F51B65198FA664F5D208E780BC0279099`
- APK 为 `com.kiyori`、`45 / 0.1.0`、SDK `26 / 34 / 37`，Android Debug V2 单 signer；
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- APK 仅含 `arm64-v8a`；`51` 个 `.so` basename 零重复，加
  `assets/operit_shell_exec` 共 `52/52` 个 ELF64/AArch64，`160` 个 `PT_LOAD` 的全局最小
  alignment 为 `0x4000`
- APK 只含 `assets/templates/shared/android-tools/aapt2-arm64-v8a` 一份 AAPT2，
  SHA-256 `E5B5FF7F0D4F6ECD7FA5D05D77FED3F09F6F1BF80F078B8AADA82BC578848561`；
  两个旧模板路径不存在
- 相对任务起始 APK `472502409` bytes，最终包体减少 `9401367` bytes（`1.9897%`）
- Terminal 已提交并推送 `e11053d60dc02b0d162ee4a3a2f655c28c1afcbb`；本地、tracking 与
  远端 `main` 三方一致，divergence `0/0`
- Terminal gitlink 更新后的父仓候选已通过 repository hygiene、fresh clone、formal readiness、
  architecture `phase=m03` 与 Markdown 链接门禁；包含本段状态更新的最终提交继续执行同一快速门禁
- 交付前增量 `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 22s`，`232` tasks
  （`19 executed / 213 up-to-date`）；APK 时间、大小和 SHA-256 均保持不变
- 本地实现、自动门禁、Debug APK 和 Git 交付完成；Release/AAB、远端 GitHub Actions、设备安装、
  真机交互与用户验收仍保持 `verification_pending`

## 2026-08-14 扩展命名与脚本创建入口整理

状态：最新“扩展”统一命名与 Skill/MCP 顶栏迁移已完成本地实现、定向验证、完整 Lint、
正式开发门禁、Debug APK 构建和独立静态产物审计；目标设备视觉与交互保持
`verification_pending`。

Kiyori 尚未发布，本轮直接把 AI 左抽屉与宿主顶栏的“包管理”统一改为“扩展”，固定按
“脚本 / 插件 / Skill / MCP”显示四个标签，并默认打开“脚本”。只调整用户可见信息架构与
页面级操作位置，不改变内部 `packages` 路由、`PackageManager` 类型、ToolPkg、Skill、MCP
的协议、存储、权限和运行时。

实施与验收门禁：

1. [DONE] 左抽屉、宿主顶栏和市场 Skill 安装说明统一显示“扩展”，七个语言目录均移除名称中的 AI 限定
2. [DONE] 四个标签统一按“脚本 / 插件 / Skill / MCP”显示，默认打开“脚本”，技术语境仍保留“脚本包”
3. [DONE] 删除脚本列表顶部的“快速创作”宣传卡片及零引用说明资源
4. [DONE] 脚本标签右上角加号改为“创建脚本 / 导入脚本包”两项菜单；创建脚本直接进入需求创作流程，插件加号继续直接导入
5. [DONE] 主顶栏及 Skill/MCP 子页面的普通操作统一使用细线图标，状态图标继续保留明确语义
6. [DONE] Skill 页面级动作迁入顶栏，固定为条件错误入口及“市场 / 添加 / 刷新”；删除右下角 FAB 和卡片内重复刷新
7. [DONE] MCP 页面级动作迁入顶栏，固定为“启动 / 市场 / 添加 / 刷新”；删除右下角 FAB 与 `200dp` 遮挡预留
8. [DONE] Skill/MCP 继续持有各自弹窗、加载状态和执行回调，顶栏只投影当前活动标签动作；加载时禁用冲突入口并只在所属动作显示进度
9. [DONE] 修复 Skill 三种导入方式与 MCP 四种导入方式的标签指示器范围
10. [DONE] 全面审查并修正 Skill/MCP 文案、英文硬编码、零引用旧资源和旧快速配置流程
11. [DONE] 同步七个语言目录、README、CONTEXT、市场 Skill 安装说明与设备验收文档
12. [DONE] 重跑定向 JVM、资源解析、Kotlin 编译、formal readiness、完整 Lint 与差异审计
13. [DONE] 串行重建并独立核验最终 Debug APK

本地验证证据：

- `:app:lintDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 8m 57s`，最终
  `0 errors / 12 warnings`；剩余项仅为 `GradleDependency 4`、`NewerVersionAvailable 3`、
  `UseKtx 3` 和 `UseTomlInstead 2`
- `MissingTranslation`、`LocalContextGetResourceValueCall`、`MissingQuantity`、
  `UnusedQuantity`、`UnusedResources` 和 `LintBaselineFixed` 均为 `0`；未扩大 baseline，
  未新增 suppression，未禁用检查
- 三处保留的 `UseKtx` 均需同步 `commit()` 的布尔返回值证明兼容性证据已持久化；KTX
  `edit(commit = true)` 不返回该结果，因此不做会丢失验证语义的机械改写
- `PackageManagerVisualPolicyTest` 为 `BUILD SUCCESSFUL in 27s`，`159` 个任务中
  `4` 个执行、`155` 个为最新状态；标签、加号菜单、Skill/MCP 顶栏动作和导入指示器策略通过
- formal readiness 为 `PASS`；七个语言目录的 `50` 项相关资源唯一、类型和占位符合同一致；
  旧“AI 扩展”前缀、旧快速创作流程、Skill/MCP 页面级 FAB 反向检查均为 `0`
- `git diff --check` 通过；Git 输出的 CRLF/LF 提示是现有换行配置提示，不是差异错误
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 3s`，共
  `238` 个任务，其中 `28` 个执行、`210` 个为最新状态；唯一 Debug launcher 和 Player runtime
  packaging 门禁均通过
- 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于
  `2026-08-14 12:42:32 +08:00`，大小 `472502409` bytes，SHA-256
  `0B2E000BAD7F291FE0FA6FB84A20DBE47C43D5A226D488195890DE23BE6E9115`
- APK 为 `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、min/target/compile SDK
  `26 / 34 / 37`、`debuggable=true`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`
- APK 仅含 `arm64-v8a`；`51` 个 native `.so` basename 唯一，加上
  `assets/operit_shell_exec` 共 `52/52` 个 `ELF64 AArch64`，逐项审计确认所有
  `PT_LOAD >= 0x4000`，全局最小对齐为 `0x4000`
- APK 使用 Android Debug 证书 V2 单 signer 签名，证书 SHA-256 为
  `e72ad950d07adbedfb9c909c48d922fddb3560677012da79b686a127867ae902`；
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- 未提交、未推送、未安装 APK，未运行 ADB、模拟器或真机；目标设备上的视觉、文案、图标、
  菜单、加载态和交互结果仍需现场验收

## 2026-08-12 OpenAI 官方联网搜索插件化接入

### 2026-08-13 revision 7 正式实施

当前已获得本地源码修改授权，正式实施以
[`10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md`](openai_hosted_web_search/10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md)
为唯一 revision `7` 蓝图，不另建并行方案。

实施顺序：

1. R7-M0：使用脱敏 synthetic fixture 锁定 URL 双重编码、tracking-only 来源差异、域名违规、
   零证据与空 Markdown 引用
2. R7-M1：建立唯一 URL identity 与 domain policy owner；官方合同审计已报告行为，relay
   filters 在创建 HTTP Call 前以 `submission_state=not_sent` 拒绝
3. R7-M2 至 R7-M4：实现零证据、answer normalization、强类型 ToolPkg 参数、来源投影与一致遥测
4. R7-M5 至 R7-M6：收口日志隐私、ToolPkg 错误所有权、版本与启动可观察性
5. R7-M7：同步语义与架构文档，执行定向测试、TypeScript 生成、正式门禁、差异审计和串行
   Debug APK 构建

当前边界：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_REVALIDATION_PENDING
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

本轮不调用真实 OpenAI 或 relay/计费接口，不操作设备、模拟器或 ADB，不安装 APK，不加入重试、
后端切换、回退或兜底逻辑。

revision `7` 的本地实现、测试、ToolPkg 生成制品和文档同步已经完成。当前合同为：

```text
ToolPkg version = 1.0.6
host-service environment variables = 20
response schema revision = 7
Hosted Web Search JVM = 26 suites / 142 tests
failures = 0
errors = 0
skipped = 0
```

本地 Kotlin compile、TypeScript strict、dist SHA-256 stability、formal readiness 和 Debug APK
验证在本轮收尾阶段复核。真实 relay revalidation、设备安装/复测和用户 acceptance 仍不在当前授权
范围内。

### Revision 7 hotfix：结果卡载荷分流与探测记录持久化

当前 revision `7` 的本地 hotfix 已落实两项根因修复：

- 实时聊天结果卡使用完整 `ToolResult`，由 `formatToolResultForMessage()` 保留
  `all_sources`、`search_actions`、`query`、`usage` 及完整 evidence schema；后续主模型上下文
  单独使用 `formatToolResultForModel()`，只发送受限 projection。主模型 projection 不再被
  UI evidence parser 当作完整结果卡载荷解析
- compatibility probe 的成功、失败和清理 record-set 均在 callback 交付前同步持久化；相同
  binding fingerprint 在应用重启后可以复用已保存证据，不会因为异步写入尚未落盘而再次要求
  相同的可能计费探测。endpoint、model、认证方式、Key、非秘密 Header 名、reasoning、
  external web access 或 response schema revision 发生变化时，重新探测仍是刻意保留的门禁

用户提供的最新日志 `kiyori_log_20260813_231439.txt` 的脱敏复核仍显示旧现场链路在成功搜索后
因 `all_sources` 缺失而记录 `REQUIRED_FIELD_INVALID`；该日志证明问题现象与本地根因一致，但不
替代本轮 APK 安装、真实 relay revalidation 或用户验收。

当前 Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
472500125 bytes
LastWrite = 2026-08-14 00:11:46 +08:00
SHA-256 = 67BB9EFF412906712186B90C5FE78726F03563AC1AF235001C5F25A549F03204
```

当前生成 ToolPkg：

```text
app/build/generated/bundledToolPkgAssets/packages/openai_web_search.toolpkg
10074 bytes
SHA-256 = 9151D1614E5F1BF88FAA8A0A9306150FDB2C5F03EA8A957D2E767C1EC0120134
manifest version = 1.0.6
response schema revision = 7
```

### Revision 6 历史基线

2026-08-13 已完成 revision `6` 正式实现的 M1 至 M5 本地封板：

- 修复 OkHttp call timeout 被误报为 `REQUEST_CANCELLED`
- 建立取消与 worker 完成共用的原子 lifecycle owner
- 建立普通 search 与 compatibility probe 共用的稳定 FIFO admission controller
- 分离 queue timeout 与 HTTP timeout，默认分别为 `60s` 与 `300s`
- 完整删除本插件旧 `MODEL_CONFIG` 链，只保留 `PACKAGE_ENV`
- 升级 ToolPkg 到 `1.0.5`、二十个 host-service 环境变量和 response schema revision `6`
- 提供九项字段级 readiness 与固定原生配置入口
- 完成 Provider 协议分组、多行摘要、三态 evidence parser、分层结果卡和单工具 L0 分组

2026-08-13 用户随后完成 `29` 次真实 relay 顶层调用，日志中的四批
`5 + 8 + 8 + 8` 与测试报告完全对齐。现场结果为 `23` 次结构化成功、`5` 次预期参数拒绝和
`1` 次非预期零证据失败。revision `6` 的基础搜索质量和生命周期改造有效，但产品功能验收没有
通过，已确认的后续问题包括：

- blocked-domain 不是 relay 下可声明的访问安全边界
- URL raw path 存在确定性双重编码
- tracking-only 差异造成来源身份误报
- 零证据结果被错误提升为硬失败
- 空 Markdown 引用进入用户可见 answer
- domain array 和 location boolean 被公开为 string
- 成功结果缺少 timing、位置和 effective domain diagnostics
- Key 前缀、完整 endpoint、正文 preview 与 ToolPkg 三重错误日志仍需收口

现场分析和 revision `7` 实施蓝图见
[`10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md`](openai_hosted_web_search/10_20260813_field_matrix_deep_analysis_and_revision_7_plan.md)。

四种全局 OpenAI Provider 枚举继续保留。当前同步实现不加入自动重试、后端切换、SSE、
background、轮询或断线续流。调查证据见
[`8_20260813_readonly_analysis_checkpoint.md`](openai_hosted_web_search/8_20260813_readonly_analysis_checkpoint.md)，
实施蓝图见
[`9_20260813_optimization_implementation_plan.md`](openai_hosted_web_search/9_20260813_optimization_implementation_plan.md)。

M4 最新四个 suite 合计 `24` tests，失败、错误和跳过均为 `0`；前台重跑
`BUILD SUCCESSFUL in 37s`。M5 的权威文档、ToolPkg 确定性生成、TypeScript strict、完整 Hosted
Web Search JVM `17` suites / `106` tests、Kotlin 编译和 formal readiness 均已通过。第一次串行
`:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 16s`，共 `238` 个任务；候选 APK 为
`472492653` bytes，SHA-256
`BAEC6E5397101E89FD49439823CFCA8D720E850DA0AC3397E35D982FB9F0FFA3`。

候选 APK 已通过 `com.kiyori / 45 / 0.1.0`、唯一 launcher、arm64-only、Debug V2 单 signer、
16 KB ZIP 对齐和敏感内容审计。`51` 个 `.so` 加 `assets/operit_shell_exec` 共 `52/52` 个
`ELF64 AArch64`，所有 `PT_LOAD >= 0x4000`。ToolPkg 为 `10243` bytes，SHA-256
`A7AFC0C19DD705E28EE29CE763113F7EA1F1C663F3471499A67964FC3F6C2FB7`，与 APK 内唯一条目
逐字节一致。文档证据回填后的稳定树构建为 `BUILD SUCCESSFUL in 26s`，`238` 个任务中 `25`
个执行、`213` 个为最新状态；APK 与 ToolPkg 的大小、时间和 SHA-256 均保持不变。

当前已授权将本轮有效修改提交并推送到 `main`/`origin/main`；仍不调用真实 OpenAI/relay 或计费接口，
不操作设备、模拟器或 ADB，不安装 APK。即使 M5 本地全部通过，状态仍需区分：

```text
LOCAL_IMPLEMENTATION_COMPLETE
REMOTE_RELAY_MATRIX_USER_EXECUTED
FIELD_FUNCTIONAL_ACCEPTANCE_FAILED
REVISION_6_FIELD_FUNCTIONAL_ACCEPTANCE_FAILED
DEVICE_PENDING
USER_ACCEPTANCE_PENDING
```

revision `3` 至 `5` 的历史 relay、parser、ToolPkg 和 APK 证据继续保留在专项文档中，不作为
revision `7` 当前制品。当前详细状态和最终验证证据见：

- [`openai_hosted_web_search/index.md`](openai_hosted_web_search/index.md)
- [`openai_hosted_web_search/4_implementation_and_validation.md`](openai_hosted_web_search/4_implementation_and_validation.md)
- [`openai_hosted_web_search/5_relay_and_environment_configuration.md`](openai_hosted_web_search/5_relay_and_environment_configuration.md)

### 2026-08-12 至 revision 5 历史证据

2026-08-12 使用与当前 Codex 相同的中转站完成了第一轮脱敏真实协议矩阵。旧 compatibility probe 查询
“当前 UTC 日期”，中转实际返回 `web_search_call + output_text + {type:"api", name:...}` 的
结构化实时数据，没有 URL 和 `url_citation`；该问题本身无法验证网页 URL 证据能力。固定查询
`developers.openai.com` 的公开 Web Search 文档后，中转返回标准 `url_citation`；加入用户当前
Codex provider 已配置的 `x-openai-actor-authorization: enabled` 时，非流式响应还能返回完整 URL
`action.sources`，而流式完成响应仍可能只有 `url_citation`。因此 Codex-compatible relay 不能被
强制要求同时提供两种 URL 通道。

schema revision `5` 明确建模 `url_citations_and_action_sources`、`url_citations`、
`action_sources` 与 `structured_feeds` 四种真实证据模式。`url_citation` annotation 是行内引用
权威，`action.sources` 是独立检索审计通道；只返回一种时不伪造另一种，两者 URL 集合不一致时
保留有效 citation 并返回精确差集。`oai-sports`、`oai-weather`、`oai-finance` 与 relay-only 命名
`api` feed 保留为无 URL 来源；只有真实 citation span 才生成 `[S1]`。compatibility probe 固定
公开网页目标并要求真实 URL citation。旧 revision `4` 及更早成功和失败记录不会复用。
`MODEL_CONFIG + RESPONSES_RELAY_STRICT` 同时接受 `OPENAI_RESPONSES` 和
`OPENAI_RESPONSES_GENERIC`，但仍必须完成当前绑定的真实严格探测。

用户安装 revision `3` 后，Pixel 对 Kiyori 当前精确非流式请求返回 HTTP `502`，Sub2api 在
Android 现场返回 HTTP `401`。使用用户明确授权的两个 endpoint、模型与临时 Key 重新执行无重试、
无后端切换的最小矩阵后确认：

- 两个站点的普通 Responses 请求均为 HTTP `200`，排除 Key、Bearer 认证和基础 Responses 路由失效
- 两个站点的 OpenAI 官方最小 `web_search` 请求均为 HTTP `200`，并返回真实
  `web_search_call`、URL citation 和 action sources
- 精确复刻 revision `3` 请求时，Pixel 返回 `upstream_error` HTTP `502`，Sub2api 请求超时
- 保持其他字段完全不变，只省略空的 `filters.blocked_domains` 后，两个站点均为 HTTP `200`，
  URL citation 与 action sources 完整

因此 revision `3` 的共同根因是请求编译器在只配置 `allowed_domains` 时仍发送
`blocked_domains: []`。revision `4` 只序列化实际非空的过滤数组，不改为流式、不自动添加 actor
header、不删除其他字段后重试，也不切换 endpoint、模型、Key 或搜索后端。HTTP 非成功响应同时会
保留有长度上限且脱敏的 provider error type、code、message 与 request/trace ID，避免再次只显示
笼统状态码。

revision `4` 的历史自动证据为 `12` 个定向 suite、`82/82` tests，失败、错误和跳过均为 `0`；
新增用例覆盖空过滤数组、provider 错误脱敏与请求 ID、Key 变化使探测失效和认证分类。
revision `3` 的 `78/78` 没有覆盖真实 relay 兼容差异。revision `3` ToolPkg 为
`9679` bytes，SHA-256 `58025CC245D055D76345F45C9DE67E20D5464240F5033695B4C77380C1B94E24`，
含 `5` 个运行时条目和
`21` 个 host-service 环境变量，凭据特征扫描为 `0`。revision `3` 产出构建
`:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 14s`，`238` 个任务中 `30` 个执行、
`208` 个为最新状态；APK 于 `2026-08-12 19:35:10 +08:00` 生成，为 `472480557` bytes，
SHA-256 `5767522CDEC73096B42A63E6CEA415A704E7F9AE56EA026F8FC17D468B1DF27C`。
文档回填和 ToolPkg 强制重生成后的一次串行复核为 `BUILD SUCCESSFUL in 25s`，
`238` 个任务中 `25` 个执行、`213` 个为最新状态；APK 时间、大小和 SHA-256 保持不变。
包名/版本/SDK 为
`com.kiyori / 0.1.0 (45) / 26 / 34 / 37`，唯一 launcher，仅 `arm64-v8a`，51 个 native
库无重复 basename，Android Debug V2 单 signer 和 16 KB ZIP 对齐通过。该 ToolPkg 与 APK
现在都是修复前失败基线，不得作为 revision `4` 交付。

revision `4` ToolPkg 版本为 `1.0.3`，生成于 `2026-08-12 20:55:48 +08:00`，大小 `9864`
bytes，SHA-256 `B1A5AFFF5D16FD742A941845FB7EE7EC19B59142F98D5681285F440BE84B480B`。
归档恰好包含 `5` 个运行时条目、`21` 个 host-service 环境变量和 `1` 个 subpackage；
用户凭据、两个实测中转域名、长 `sk-*`、Bearer credential、私钥、`.env` 与 TypeScript
源码扫描均为 `0`。新版 APK、签名与 16 KB 审计将在串行构建完成后回填。

用户在 revision `4` APK 上改用 Sekirocloud 后，compatibility probe 和普通搜索能够成功，但新的
11 次现场矩阵只有 2 次成功、9 次旧版 `SOURCE_INVALID`。同一英文请求可以先成功再失败；
有无 `allowed_domains`、中文或英文、`low` 或 `medium` 都不是稳定触发条件，Python、NASA 与
OpenAI 官方站点均可失败。脱敏原始 HTTP `200` 对照精确复现两种响应：

- 3 个 `web_search_call`、2 个 citation URL、18 个 action-source URL，其中 1 个 citation URL
  不在 action sources
- 3 个 `web_search_call`、2 个 citation URL、0 个 action-source URL、1 个 `open_page` URL

旧 parser 把 action sources 错当成 citation 白名单。revision `5` 继续严格验证 citation URL、
HTTP(S)、span、source mapping 和 Responses schema，但把跨通道差异改为
`source_diagnostics`、`CITATION_NOT_IN_ACTION_SOURCES`、`ACTION_SOURCES_MISSING` 或
`ACTION_SOURCES_PARTIAL`，不再丢弃有效 citation。多 Key compatibility 状态改为最多 16 条的
fingerprint record-set，不同 Key 互不覆盖；搜索与 probe 的 Key 游标推进在所有 bridge 实例间
串行化。

revision `5` 完整定向矩阵为 `13` 个 suite、`91/91`，失败、错误和跳过均为 `0`。ToolPkg
`1.0.4` 为 `9864` bytes，SHA-256
`558382BDDE9688F99395F703D3225C7DAB5452326F85A6660DDB557ACEA3B9FB`；归档恰好含 `5` 个运行时
条目、`21` 个 host-service 环境变量和 `1` 个 subpackage，凭据与私有配置扫描为 `0`。
architecture `phase=m03`、架构门禁自身 `107/107`、formal readiness 和 `git diff --check`
均通过。revision `5` Debug APK 于 `2026-08-12 23:39:40 +08:00` 生成，为 `472480557` bytes，
SHA-256 `9F845CC0F71CEBB1929E42148F93C85A489C9FAD20160FAE5E8BFF13EE451536`；包名/版本/SDK 为
`com.kiyori / 0.1.0 (45) / 26 / 34 / 37`，唯一 launcher、arm64-only、51 个 native 库无重复
basename、Android Debug V2 单 signer 和 16 KB ZIP 对齐均通过。51 个 `.so` 加
`assets/operit_shell_exec` 共 52 个 AArch64 ELF，所有 `PT_LOAD >= 0x4000`。APK 内 ToolPkg
与生成制品逐字节一致；长 `sk-*`、Bearer credential、AWS/GitHub token 和完整 PEM 私钥块扫描
均为 `0`。revision `4` 的 `1.0.3` ToolPkg 和 APK 现在都是
`SOURCE_INVALID` 失败基线，不得作为修复版交付。

详细设计与开发门禁：

- [`openai_hosted_web_search/`](openai_hosted_web_search/index.md)

最终 Debug APK 和正式门禁证据记录在专项 TODO 的
[`4_implementation_and_validation.md`](openai_hosted_web_search/4_implementation_and_validation.md)。

## 2026-08-11 首启精确闹钟权限链收口

状态：崩溃根因分析、生产权限链删除、首启状态迁移、用户可见文案同步、架构合同更新、定向测试、
完整架构门禁、正式开发准备、Debug APK 构建和静态产物核验均已完成。目标设备尚未安装和复测，
因此最终状态保持 `verification_pending`。

已确认用户日志中的崩溃进程是 `com.android.settings`，OriginUI 系统设置在精确闹钟开关回调中
对空 Intent 调用 `getComponent()` 触发 NPE；该异常发生在系统设置进程，Kiyori 无法在自身进程
捕获。当前仓库没有 `setExact`、`setExactAndAllowWhileIdle` 或 `setAlarmClock` 消费者，工作流
由 WorkManager 调度，普通设备闹钟继续通过 `ACTION_SET_ALARM` 交给系统处理。

本轮完成：

- 删除首启 `EXACT_ALARM` 枚举、`canScheduleExactAlarms()` 状态读取、
  `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 跳转、权限卡元数据和 Manifest
  `SCHEDULE_EXACT_ALARM` 声明
- 将首启偏好命名空间升级为 `2026-08-11-r9`，不读取可能包含 `EXACT_ALARM` 的旧 r8 选择集合
- 删除七个现有本地化资源中的精确闹钟权限标题/说明，并清理中文、英文、西班牙语、韩语、
  印尼语、马来语和巴西葡萄牙语用户协议中的不再成立能力声明
- 增加首启架构反向门禁，禁止生产代码、Manifest 或本地化资源重新引入已移除合同；同步
  `m03` Manifest 语义快照

本地验证证据：

- 首启 JVM `17/17`，首启架构正反向 Python `3/3`，formal readiness 均通过
- 完整 architecture `phase=m03` 返回 `errors: []`；生产精确闹钟引用和用户可见资源反向搜索为 `0`
- `git diff --check` 无 whitespace error，仅报告 AndroidManifest 既有换行转换提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`；
  `238` 个任务中 `33` 个执行、`205` 个为最新状态，唯一 Debug launcher 与 Player runtime
  packaging 检查通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`475435325` bytes，SHA-256
  `253A6A426A342014BD55432EB9DF3531D6CFD804487C7D6970B01598592FF291`
- APK 为 `com.kiyori`、`0.1.0 (45)`、min/target/compile SDK `26 / 34 / 37`、仅
  `arm64-v8a`；51 个 native `.so` basename 唯一，Android Debug V2 单 signer 和
  16 KB ZIP 对齐验证通过
- 未提交、未推送、未安装 APK、未运行 ADB、未操作手机或模拟器

下一步只剩目标 OEM 设备验收：重新执行六页首启，在第六页处理剩余特殊访问与高级设备能力，
确认不再进入精确闹钟系统设置页，并复测系统设置往返、旋转、进程回收和再次启动后的状态恢复。

## 2026-08-11 Kiyori 存储路径与 ToolPkg 数据治理

状态：只读审计、正式设计、主体实现、本地自动验证、Debug APK 构建和内置 ToolPkg 静态审计
均已完成。统一公共保存、ToolPkg private/cache、制品 scanner/builder、内容寻址 active 事务和
显式旧数据迁移框架已经闭环。兼容标识继续保留；Kiyori 不会自动扫描、复制、合并或删除
`Download/Operit`，第三方“记忆系统”发布包也不在本仓库内伪造修复。

唯一计划、目录合同、API、预算、迁移与验收矩阵见：

- [`kiyori_storage_and_toolpkg_data_governance/`](kiyori_storage_and_toolpkg_data_governance/index.md)

本轮不安装 APK、不操作手机或模拟器。目标设备上的 MediaStore、市场更新、跨包隔离和旧数据导入
仍单独保持 `verification_pending`。

本地验证证据：

- ToolPkg/存储定向 JVM `19/19`、ARCH046 合同 `2/2`、TypeScript、bundled ToolPkg、
  Kotlin 编译、formal readiness、12 份相关 Markdown 链接和 `git diff --check` 均通过
- 完整 architecture `m03` 共 `35/35` 项检查通过，JSON 输出为 `errors: []`；同时完成
  数据库 version、市场偏好 owner、M-04、M-05A2 和 M-05B 的真实合同快照同步
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`，
  `238` 个任务中 `33` 个执行、`205` 个为最新状态；唯一 Debug launcher 与 Player runtime
  packaging 检查通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`485889789` 字节，SHA-256
  `7446903F31154187668B0A3DC556EFC6BE0947987B0A03A2F76A67E47894CAC5`
- APK 为 `com.kiyori`、`0.1.0 (45)`、min/target/compile SDK `26 / 34 / 37`、仅
  `arm64-v8a`；Android Debug V2 单 signer 与 16 KB ZIP 对齐验证通过
- `assets/packages` 共 `42` 个文件，其中 `11` 个 `.toolpkg` 全部具有唯一根 manifest，
  无非法/冲突路径、敏感缓存条目、旧 Operit 物理路径或错误的 app-specific external 路径
- 未执行 APK 安装、ADB、设备、SAF、MediaStore、市场或第三方 ToolPkg 现场验收

## 2026-08-11 初次安装设置默认值

状态：源码修改、默认值回归断言、文档同步、正式门禁、Markdown 链接检查、`git diff --check`、
串行 Debug APK 构建与产物核验已完成。继续使用现有唯一浏览器/播放器设置 owner，不创建第二份
状态源；目标设备上的首装初始值和设置页显示仍保持 `verification_pending`。

本轮将初次安装且没有对应持久化键时的默认值调整为：

- 网页浏览器：主页 `https://go.itab.link`；允许用户脚本、返回不重载、强制页面缩放、网站密码自动保存
  默认开启
- 视频播放器：记忆播放倍速、长按加速、记忆超分模式默认开启；默认超分模式为 `A+`；解码器预设为
  `High Quality`；在线播放缓存为“大缓存”

`about:blank` 仍作为设置页“恢复为空白页”和窗口清空后的空白根语义，未被初次安装主页替换。已有
明确写入的用户设置继续按持久化值读取；本轮不做提交、推送、安装或设备操作。

本地验证证据：

- `PlayerPolicyTest`、`KiyoriSettingsPagesTest`、`KiyoriBrowserPluginSettingsPolicyTest`、
  `WebSessionBrowserWindowPolicyTest` 和 `BrowserHomeNavigationPolicyTest` 共 `45/45` 通过，
  失败、错误和跳过均为 `0`
- 项目 `.venv` 的 formal readiness 为 `PASS`；Markdown 链接检查 `7/7` 通过；
  `git diff --check` 无 whitespace error，仅报告工作树既有文件的 CRLF 转换提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`，
  `238` 个任务中 `29` 个执行、`209` 个为最新状态；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `485889789` bytes，
  SHA-256 `C553DF3494C0A0E56B850FD4822BAFCFEC210EB97241C59D041C3EB6B4B4CC20`
- APK 为 `com.kiyori`、`0.1.0 (45)`、min/target/compile SDK `26 / 34 / 37`、arm64 目标；
  Android Debug V2 单 signer 签名和 `zipalign -c -P 16 -v 4` 验证通过
- 真机验收、APK 安装、提交和推送均未执行

## 2026-08-11 Browser ToolPkg 表单、CSP 与关闭结果一致性

状态：源码修复、公开包入口回归、定向 JVM 合同、AndroidTest 编译、formal readiness、
最终差异审计、Debug APK 构建与产物核验已完成。目标设备上的严格 CSP、输入事件与页面观察
仍保持 `verification_pending`。本轮只修改唯一 `StandardBrowserSessionTools`、现有
WebSession/WebView 执行链和预置 `browser.js`，不创建第二 Browser Runtime、第二页面代理或
第二输入状态源。

已确认根因：

- 公开 `browser:fill_form` 在 `browser.js` 中无条件执行 `field.name.trim()` 和
  `field.type.trim()`，而真实使用面只提供 `ref` 或 `selector` 加 `value`，因此在进入原生工具前
  就抛出缺失属性异常；现有 Android JS 套件只调用内部 `browser_fill_form`，没有覆盖公开包入口
- 共享 `evaluateJavascriptAsync` 使用间接 `eval`，`browser_run_code` 再次使用 `eval` 和
  `AsyncFunction`；严格 CSP 页面会拒绝 `unsafe-eval`，并把依赖该共享执行器的页面观察一并中断
- `run_code` 的 locator fill/select 与 keyboard 自建了简化事件路径；`keyboard.press` 对
  `Tab`、`End` 和字符键可能返回成功但没有实现对应行为，未知 keyboard/locator 方法暴露裸
  `TypeError`
- `browser_close` 与 `browser_tabs action=close` 已经完成 registry 状态变更后，仍直接读取新
  活动页状态和快照；后续观察异常会覆盖已完成的关闭结果

细化计划：

1. [DONE] 公开 `fill_form` 改为每项仅使用 `ref` 或 `selector` 之一并提供
   string/number/boolean `value`；`name` 仅作可选诊断，DOM 决定控件类型
2. [DONE] 同步 `examples/browser.js`、`examples/browser.ts`、`network.d.ts` 和中英文系统工具提示，
   清理旧 `name/type` 强制合同
3. [DONE] Android JS 套件真实调用 `toolCall('browser', 'fill_form', ...)`，覆盖 ref、selector、
   text、checkbox、select、缺 locator 和错误 value 类型
4. [DONE] 共享 WebView 异步执行器改为直接注入表达式；`run_code` 只接受明确函数源码，不使用
   `eval`、`AsyncFunction` 或第二执行引擎
5. [DONE] locator 的 click/hover/fill/selectOption 与 `keyboard.press` 统一复用 `__operitPw`；
   keyboard 能力固定为单字符、Enter、Backspace、Delete，未知成员和未实现按键返回结构化错误
6. [DONE] 标签关闭先输出 closed session、remaining tab count 和 active session，再独立输出页面
   状态与快照观察，观察异常不改变关闭成功
7. [DONE] 增加严格 CSP fixture，覆盖 snapshot、evaluate、run_code、locator 输入、keyboard 和两种
   close 入口；文件上传继续要求公开真实点击后调用 upload
8. [DONE] 串行完成 TypeScript、定向 JVM、AndroidTest 编译、formal readiness、
   Markdown/差异检查和 Debug APK 构建及 APK 元数据、签名、16 KB 对齐核验
9. [PENDING] 在目标 Android 设备运行 Browser Android JS 套件，复测严格 CSP、IME/焦点、网页
   用户激活和关闭后的活动页观察

本地验证证据：

- `pnpm.cmd exec tsc -p examples/tsconfig.json` 通过；预置 `browser.js`、示例
  `examples/browser.js` 和 Browser Android JS 套件均通过 `node --check`
- `BrowserRunCodeContractTest` 共 `8/8` 通过，覆盖 Page/locator/keyboard 合同、函数源码识别、
  严格 CSP 所需的无动态编译脚本生成、结构化不支持错误及关闭结果分层
- `:app:compileDebugAndroidTestKotlin` 与 `:app:compileDebugAndroidTestJavaWithJavac`
  `BUILD SUCCESSFUL`，共 `148` 个任务；严格 CSP 和公开 `browser:fill_form` 回归已编译进入套件，
  但未在设备执行
- formal readiness 为 `PASS`，Markdown 链接单测 `7/7` 通过，`git diff --check` 无
  whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` `BUILD SUCCESSFUL`，共
  `238` 个任务；`verifyPlayerNativeInputs`、唯一 Debug launcher 和
  `verifyDebugPlayerRuntimePackaging` 均通过
- 最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`485889789` 字节，SHA-256
  `6F856D38F77A283F251408BA1B2CA8D36113F02E6199C7419080A45BE0D275C8`；
  `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`、
  `compileSdk 37`、仅 `arm64-v8a`
- APK 仅有 `com.ai.assistance.operit.ui.main.MainActivity` 一个 launcher；V2 Debug 单签名通过，
  证书 SHA-256 为
  `e72ad950d07adbedfb9c909c48d922fddb3560677012da79b686a127867ae902`；
  `zipalign -c -P 16 4` 验证通过
- APK 内唯一 `assets/packages/browser.js`、源码 asset 与 `examples/browser.js` 的 SHA-256
  均为 `58AB2AD64F73B8EDF7E7883015C256A9A845459728F1CFD7AEF44FA48155F1D9`
- APK 内 `51` 个 `.so` 和 `assets/operit_shell_exec` 共 `52` 个 ELF 已逐项使用 NDK 28.2
  `llvm-readelf -lW` 审计；全部 `PT_LOAD` 最小 alignment 不低于 `0x4000`，其中 `51` 个为
  `0x4000`、`libbusybox.so` 为 `0x10000`

本轮边界：

- Kiyori 尚未发布，直接移除旧公开 `type` 字段语义，不维护双字段协议
- `page.dialog` 仍不是属性；对话框继续使用 `page.on/once/off/removeListener('dialog')`
- `run_code` 不伪造文件选择器用户激活，不增加完整 Playwright、隐藏 WebView、隐式重试或另一条
  页面执行路径
- 不安装 APK，不执行 ADB/MuMu/真机操作，不提交，不推送

## 2026-08-10 GPT-5.6 可恢复执行与统一模型能力

状态：Responses 状态机、at-most-once 提交、provider execution 持久化和消息失败所有权已完成；
vivo Android 16 的 native ART 兼容性修复已完成代码拆分与本地 APK 静态验证，目标设备复测仍为
`verification_pending`。用户提供的 tombstone `SIGABRT / Unexpected instruction: unused-e6`
把当前无日志闪退定位到 ART 解释器执行 `MessageProcessingDelegate$sendUserMessage$sendJob$1`
生成的协程状态机，而不是 Kotlin HTTP 异常或应用内 Crash Report 链。第一阶段将启动 lambda
独立出来后，进一步把整轮发送拆成状态载体、准备、请求创建、流收集和收尾边界；Responses
请求语义、单次 POST、同一 `response_id` 续接、`starting_after`、SharedStream 主失败 owner、
次级观察器和取消传播均保持不变。

2026-08-11 真机复测确认 native 闪退已消失，但发送仍可能无正文、无错误即结束，并出现
`Tool 'DEEPSEEK' is unavailable or does not exist`。已确认旧工具正则会把
`provider_name="DEEPSEEK"` 当成工具 `name`；工具处理使用 `launch + join()` 丢失子任务异常，
`processStreamCompletion` 又把异常清成 `Idle`，零内容路径直接标记 `Completed`。本轮按根因
修正独立工具名属性、工具 follow-up 失败所有权、完成阶段错误传播和 Responses 完成快照补齐，
不增加请求重提、模型切换、传输降级或工具禁用。

当前实现已通过本地编译和 Debug APK DEX 审计。原 `executeSendUserMessageTurn` 约
`971 registers / 584520` 方法体，现为 `33 registers / 20755` 方法体；
主流收集 continuation 为 `30 registers`，发送启动 continuation 为 `35 registers`。最新本地
Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `485889789` bytes，SHA-256
为 `E43FB70208FD9F59B87D009D537C46A6524437A3480834A9B5508ED98BFDBE3F`。真实 endpoint、
网络切换、退后台、进程重建后的自动 UI 恢复、instrumentation 和 vivo PD2507 Android 16
现场复测仍保持 `verification_pending`。Kiyori 尚未发布，本轮不提交、不推送、不安装 APK。

后续真机复测确认其他模型的工具调用已恢复，但 GPT-5.6 仍在首包前静默结束。当前开发增量已
修正全量配置刷新越过活跃 `ServiceLease` 关闭 Provider 的生命周期错误，并以 chat/turn ID
区分用户停止、破坏性历史修改和非预期取消。配置刷新不再取消活跃 GPT-5.6 请求，未知或
生命周期类取消会显示错误，不再静默写成 `Idle`。定向与完整 JVM、AndroidTest Kotlin 编译、
formal readiness、Debug APK 构建、签名、16 KB 对齐和 DEX continuation 审计均已通过；
目标设备复测仍待执行。

最新目标设备复测证明上述取消链修复仍未覆盖最终现场路径：GPT-5.6 依旧可能在数秒后无正文、
无错误结束。当前续作把成功不变量放到 `MessageProcessingDelegate.completeAssistantResponse`：
共享流即使正常关闭，也只有在重建后的最终正文非空白时才能进入 `Completed`。零输出显示
`AI_STREAM_EMPTY_TERMINATION`；普通异常写入回合最终 Error 并在 cleanup 后再次提交；没有
命中任何终态时显示 `AI_TURN_TERMINAL_MISSING`。相关 12 个定向 JVM suite、完整 JVM、
AndroidTest Kotlin 编译、formal readiness、差异检查、Debug APK、签名、16 KB 对齐和 DEX
continuation 审计均已通过；vivo Android 16 现场复测仍保持 `verification_pending`。

用户于 2026-08-11 13:02 获取的目标设备日志进一步证明：本次 GPT-5.6 请求在首个
`response.created` 前收到 `/v1/responses` HTTP 502 `Upstream request failed`，随后同一个
`OpenAIResponsesSubmissionUnknownException` 已经经过 SharedStream、`EnhancedAIService` 和
`MessageProcessingDelegate`，最终记录为 `provider_failure`、`chunks=0`、`visibleChars=0`。
剩余无提示表现来自 `ChatServiceCore` 的消息错误回调只写日志，没有把安全用户消息写入主界面
正在观察的唯一 `UiStateDelegate.errorMessage`。当前修复已接通该回调到现有 `ErrorDialog`，
并新增状态写入、清除和生产链合同测试；502 仍保持 `SUBMISSION_UNKNOWN`，不会创建第二个
POST。目标设备能否看到错误弹窗仍需安装新 APK 后复测。

用户于 2026-08-11 13:28 获取的新日志确认错误弹窗已经生效，同时暴露最终 502 请求合同：
Pipio 的 `https://pipio.io/v1/responses` 被历史 `OPENAI_RESPONSES` 配置编译为官方 profile，
请求同时携带 `background=true`、`store=false`、Prompt Cache、Tool Search、
`reasoning.summary=auto`、`reasoning.encrypted_content` 和 strict schema，随后网关返回
`openai_error / bad_response_status_code`。当前增量改为同时核对 provider 类型和实际 endpoint：
只有精确官方 OpenAI Responses 地址声明官方能力；自定义域名保留 GPT-5.6 五档 effort 与
Responses 工具调用，但不自动加入上述官方字段。兼容 endpoint 的 408、409、5xx 与传输未知状态
不再进入普通流式整轮回滚和重新 POST；异常外层文本直接携带安全的 HTTP/传输摘要，因此用户可
看到 502，而不是只有 execution ID。目标设备复测仍为 `verification_pending`。

用户于 2026-08-11 15:14 获取的日志确认 GPT-5.6 已能返回正文，但同一个 Responses
`call_id` 被 `response.completed` 再次投影成第二段工具 XML，随后一次工具 follow-up 502
取消长期共享工具 scope，使当前并行工具和后续新回合持续继承旧异常。当前增量将终态工具项
改为遍历完整 `response.output` 并保留原始位置，以 `call_id` 作为流事件、终态快照、XML、
当前回合执行和历史重放的稳定身份；`function_call_arguments.done.arguments` 在关闭 XML 前
进入同一解析器。相同身份同名同参只保留一次，冲突在副作用前失败。长期工具 scope 使用
`SupervisorJob` 隔离任务，当前 `Deferred.await()` 继续把原始 502 交回消息 owner；兼容
endpoint 没有真实 response ID 时不构造持久化账本身份。目标设备复测仍为
`verification_pending`。

细化计划与验收矩阵见：

- [`unified_model_capability_and_resumable_execution/`](unified_model_capability_and_resumable_execution/index.md)

本轮边界：

- 保留现有“思考模式 + 五档思考深度”用户接口
- 首个里程碑解决 GPT-5.6 Responses 长推理 `stream was reset: CANCEL` 后从头生成
- 后续里程碑依次实现强类型模型能力、请求编译、Prompt Cache、工具发现、usage 与遥测
- 首个官方 Responses 事件固定按 sequence `1` 提交；首包前异常必须穿过共享流和服务层进入
  消息错误链，不能表现为空回复正常结束
- `response.created` 前收到 502 等提交状态未知错误时继续保持 `SUBMISSION_UNKNOWN` 且不发起
  第二次 POST；SharedStream、消息收集 Job 和 Compose 次级观察器都不得把同一异常再次升级
  为 `APP_FATAL`
- 不调用真实付费模型 API，不安装 APK，不操作设备，不提交，不推送

本地验证证据：

- 空响应完成策略、消息最终 Error、UI 错误投影、工具名属性边界、工具 follow-up 失败所有权、
  Responses 终态正文一致性、生产链 loopback 502、服务租约和取消终态共 `12` 个定向 JVM
  suite、`72/72` 项通过；
  502 回归
  仍精确验证请求序列只有
  `POST /v1/responses`、持久化状态为
  `SUBMISSION_UNKNOWN / HTTP_502_SUBMISSION_UNKNOWN`，没有 provider event 或 resume
- 完整 Debug JVM 回归为 `175` 个 suite、`1033/1033` 通过，失败、错误和跳过均为 `0`
- Android migration、DAO、stream state、Tool Search 和 provider tool identity 测试源码
  编译通过；新增原始 output 位置、done-only 完整参数、历史 call ID 一一对应和当前回合身份
  冲突回归，仍不在设备上运行
- `:app:compileDebugAndroidTestKotlin` 通过，`146` 个任务中 `2` 个执行、`144` 个为最新状态
- formal readiness 与 `git diff --check` 通过；确认没有其他 Gradle 进程后，最终串行
  `:app:assembleDebug` 在 `32s` 内完成，`238` 个任务中 `25` 个执行、`213` 个为最新状态
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，`485889789` bytes，SHA-256
  `E43FB70208FD9F59B87D009D537C46A6524437A3480834A9B5508ED98BFDBE3F`
- APK 为 `com.kiyori`、`0.1.0 (45)`、min/target/compile SDK `26/34/37`；仅包含
  `arm64-v8a`，共 `51` 个 `.so`，basename 无重复，包含 native ripgrep 和 shell launcher，
  不包含 `libsudo.so`
- Android Debug V2 单 signer 与 `zipalign -c -P 16 -v 4` 验证通过
- DEX 审计为发送启动 `35 registers`、主发送 `33 registers / 20755` 方法体、主流收集
  `30 registers`，violations 为空

## 2026-08-10 负一屏历史网页与媒体点击修复

状态：网页和媒体入口的根因修复、本地自动验证、Debug APK 构建和静态产物核验已完成；
目标设备上的冷启动、同进程播放和 Back 返回仍保持 `verification_pending`。

细化计划：

1. [DONE] 修正网页、小说和其他 URL 条目的 Shell 状态所有权，避免历史抽屉用旧状态覆盖
   Browser Home 导航
2. [DONE] 负一屏视频和音乐条目直接启动唯一 `PlayerActivity`，Browser Home 内入口继续消费
   原有一次性全屏 presentation 请求
3. [DONE] 新增独立 `HISTORY_REPLAY` 来源，从普通 Profile 的持久 Cookie owner 和当前浏览器
   设置重建在线请求身份，移除冷启动对活动 WebSession 的依赖
4. [DONE] 保持 Browser candidate 的真实 session、下载和悬浮返回语义；历史来源不伪装成
   candidate，本地历史继续使用现有队列解析
5. [DONE] 让 Browser candidate 按来源 Profile 决定是否写共享历史，无痕媒体不持久化
6. [DONE] 增加网页状态所有权、历史来源、source session、Profile 持久化和全屏返回策略测试
7. [DONE] 串行执行定向 JVM、formal readiness、`git diff --check`、Debug APK 构建和产物核验
8. [PENDING] 在目标设备复测网页直达、冷启动和同进程在线视频、本地视频及 Back 返回

本轮不创建隐藏 WebView、第二 PlayerSession、第二播放器、下载旁路或持久化请求头，不安装 APK，
不执行 ADB/MuMu/真机操作，不提交，不推送。

本地验证证据：

- `KiyoriShellStateTest`、`BrowserMediaCandidatePolicyTest`、`WebSessionHistoryPolicyTest`、
  `WebSessionProfilePolicyTest`、`PlayerPolicyTest` 与 `PlayerSurfaceLeasePolicyTest` 共
  `126/126` 通过，失败、错误和跳过均为 `0`
- 项目 `.venv` 的 formal readiness 通过；完整 `git diff --check` 无 whitespace error，
  仅报告其他既有脏文件的 CRLF 转换警告
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 43s`，
  `238` 个任务中 `28` 个执行、`210` 个为最新状态；`verifySingleDebugLauncher` 与
  `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `475435609` 字节，
  SHA-256 `BDDD1DFB558BE6E9BE8A5AB8B133917A15FA17C66A081C4B0A446AC7EF61ECB3`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min/target/compile SDK `26 / 34 / 37`、仅
  `arm64-v8a`；Android Debug v2 单 signer 签名和 `zipalign -c -P 16 -v 4` 验证通过

## 2026-08-10 Markdown/LaTeX 公式兼容性与化学渲染

状态：源码修复、自动化回归用例、文档、本地自动化验证和 Debug APK 构建已完成；
目标设备上的公式视觉、横向拖动与纵向滚动协同、流式输入时序和不同屏宽验收待完成，
交付状态保持 `verification_pending`。
本轮继续复用唯一 Compose/JLaTeXMath 公式链路，不新增 WebView、KaTeX、MathJax 或第二套公式状态源。

细化计划与验收矩阵见：

- [`markdown_latex_rendering_compatibility/`](markdown_latex_rendering_compatibility/index.md)

本轮并行交付边界：

- 本文件下方已有的 Browser 工具视口、链接导航、控制台与 `run_code` 契约修复段落
- 不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

## 2026-08-10 Browser 工具视口、链接导航、控制台与 run_code 契约修复

状态：根因定位、源码修复、自动化回归、本地验证和 Debug APK 构建已完成；目标设备验收
待完成，交付状态保持 `verification_pending`。继续复用唯一
`StandardBrowserSessionTools`、每个 `WebSession` 的真实 WebView、现有 Browser Host 和
userscript runtime；不创建第二套浏览器、标签注册表、页面代理或 console owner。

已确认根因：

- `browser_resize` 只保存请求尺寸并调用 `zoomBy` 模拟宽度，没有改变活动 WebView 的真实布局；
  Browser Home 未挂载时，同一个 WebView 又被放入永久 `1×1` 的透明 background anchor
- `WebSessionBrowserHost.setViewportSize` 把请求宽高按 `240dp / 320dp` 强制下限处理，导致
  `900×600` 在当前设备密度下变成状态中的 `900×1120`
- 普通左键 ref 点击优先向 WebView 派发原生触摸；当 WebView 为 `1×1` 时，页面元素坐标被压到
  `(1,1)`，同时 click settlement 不等待链接应触发的导航，也不报告导航超时
- userscript 的 page 与 isolated 两套 document-start bootstrap 在总授权关闭时分别收到一次
  `userscript_permission_required`，bootstrap 又把内部错误镜像到页面 `console.error`
- `browser_run_code` 的 `page` 仅实现未声明清楚的局部方法；`setContent`、`once` 等缺失方法
  直接暴露 JavaScript `TypeError`，与包级“严格对齐”描述不一致

细化计划：

1. [DONE] 核对 `AGENTS.md`、正式开发准备清单、Git 基线和唯一 Browser Runtime 所有权
2. [DONE] 追踪 resize、页面状态、background anchor、WebView 布局、点击坐标、导航等待、
   userscript bootstrap、console 采集和 `run_code` Page 代理的完整调用链
3. [DONE] 把 viewport 改为每个 session 的真实 CSS 布局合同；移除 dp 强制放大和
   zoom 模拟，只在 Host 边界按 density 转换物理布局尺寸，切换标签时恢复各自尺寸，页面状态读取实际 DOM viewport
4. [DONE] 保留真实点击语义，修正 CSS 到 WebView 坐标映射；链接点击等待导航或新标签，
   并明确区分导航完成、被对话框暂停和超时/阻止
5. [DONE] 总授权关闭时让 userscript bootstrap 返回空脚本集合；内部 bridge/runtime
   诊断不再写入页面 console，已授权脚本在权限被撤销后仍获得结构化权限错误
6. [DONE] 为 `run_code` 增加 `page.setContent`、`page.on/once('dialog')` 及
   alert/confirm/prompt dialog 对象；其他未知 Page API 返回 `Unsupported Playwright API`
7. [DONE] 扩展 browser Android JS 回归套件，覆盖串行/并发 goto+resize、标签独立尺寸、
   页面布局 viewport、按钮与链接点击、导航超时、console 隔离、dialog、fetch/XHR 和多标签绑定
8. [DONE] 增加 viewport、点击导航、userscript 权限和 `run_code` 方法契约 JVM 测试，
   同步 `README.md`、`CONTEXT.md` 和 browser 包双语描述
9. [DONE] 串行执行定向 JVM 测试、AndroidTest 编译、formal readiness、Markdown 链接与差异检查，
   最后构建并核验 Debug APK

本地验证证据：

- Browser 与 Markdown/LaTeX 联合定向 JVM 共 `8` 个测试类、`63/63` 通过，失败、错误和跳过均为 `0`
- `:app:compileDebugAndroidTestKotlin`：`BUILD SUCCESSFUL`，`146` 个任务；Browser Android JS
  回归套件已写入并通过编译/打包，但尚未在目标设备运行
- 项目 `.venv` 的 formal readiness、`git diff --check` 和本轮 `9` 份 Markdown 本地链接检查通过
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`，`238` 个任务；
  Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，SHA-256
  `FA53014E1D66469723AD99F72CC85DC39B22A9C967983B35612A4C725849ED04`
- APK 静态核验：`com.kiyori`、`0.1.0 (45)`、仅 `arm64-v8a`、Android Debug v2 签名通过、
  16 KB ZIP 对齐验证通过

验收边界：

- 不通过伪造页面状态掩盖真实 WebView 仍为 `1×1`
- 不把链接点击改成读取 `href` 后直接调用导航
- 不新增 fallback、第二 Browser Runtime、第二 WebView 或第二 userscript/console 状态源
- 真实 viewport、跨域链接点击、WindowManager 大尺寸 background anchor、页面 console 隔离和
  `run_code` dialog 时序仍需目标设备复测
- 不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

## 2026-08-10 设置页主题快捷入口与负一屏网址直达

状态：本地实现、自动验证和 Debug APK 构建已完成，目标设备视觉与交互验收待完成。本轮在
既有设置首页 16 入口和主题 owner 基础上继续收口，不新增第二主题状态源、不改变已存在的
设置详情状态所有者；负一屏书签和历史 URL 进入同一 Browser Home，不创建悬浮浏览器入口。

细化计划：

1. [DONE] 核对 `AGENTS.md`、正式开发准备清单、现有脏工作树和本轮重叠差异
2. [DONE] 定位 `UserPreferencesManager` 主题 owner、设置首页第 4 个按钮和
   `BrowserPresentationCoordinator` 的负一屏入口顺序
3. [DONE] 将首页与详情页标题统一为“我的账号”“数据备份”，并调整设置首页及详情页图案配色
4. [DONE] 在第 4 个顶栏按钮正下方实现“跟随系统 / 浅色模式 / 深色模式”快捷菜单，点击即时写入
   既有主题偏好，按钮图标随有效主题显示太阳或月亮
5. [DONE] 让负一屏书签和历史中的网页 URL 先进入 Browser Home，再在可见网页宿主中导航；保留
   视频/音乐历史的播放器语义和浏览器其他入口语义
6. [DONE] 增加相关 JVM/源码合同测试，更新 `CONTEXT.md`、设置信息架构与主题视觉文档
7. [DONE] 串行执行 `:app:assembleDebug --no-daemon --console=plain` 并核验 Debug APK
8. [DONE] 审查差异、构建产物和既有工作树，完成任务日记收尾；不提交、不推送

验收边界：

- 本地自动检查和 Debug 构建不替代目标设备上的设置点击、主题切换和 Browser Home 真实导航验收
- 本轮不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

本地验证证据：

- 设置、设计主题和 Browser Home 导航策略 3 个 JVM 测试类共 `29/29` 通过，失败、错误和
  跳过均为 `0`
- `ci.test.test_architecture_boundaries` 共 `106/106` 通过；正式开发准备检查通过；
  `git diff --check` 无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `29` 个执行、`209` 个为最新状态
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`475435609` bytes，SHA-256
  `1108CDB7D0D4CFFC376585D1B141370C7D70785D7F09B0B6CF18347D24E135C8`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min/target/compile SDK `26 / 34 / 37`、唯一
  `arm64-v8a`；包含 `51` 个原生库且 basename 无重复，内置 `assets/accessibility.apk`
  恰好一份
- Android Debug v2 单 signer 签名和 `zipalign -c -P 16 -v 4` 验证通过

## 历史任务记录（2026-08-10 及更早）

## 2026-08-10 Kiyori 首启收口、默认新对话与设置首页16色

状态：本地实现、自动化验证、Debug APK 构建和静态审计完成。范围仅包含首启第一页标题对齐、
六页流程静态与自动化复核、首次 AI 首页真实空白对话初始化、设置首页 16 个入口的文案/图标/
配色，以及对应文档、测试和 Debug APK。不新增设置详情页，不改变已有入口的状态所有者，
不安装 APK、不操作设备。

设计与验收合同：

1. [DONE] 仅将第一页“欢迎使用 Kiyori”居中，带眉题的第 2 至第 4 页标题继续左对齐
2. [DONE] 复核唯一 `HorizontalPager`、六页顺序、协议门禁、权限队列和授权完成直达应用
3. [DONE] 当前对话为空或已不存在时创建并选中真实空白对话；已有有效对话时尊重
   “每次启动新建空白聊天”偏好
4. [DONE] 设置首页顶部固定为“账号连接 / AI助手 / 语音服务 / 小程序”，底部固定为
   “界面定制 / 数据备份 / 开发手册 / 更多功能”
5. [DONE] 16 个入口使用 16 个互不重复的图标和 16 组功能语义配色；浅色、深色下的图标
   前景色与容器色均保持一一对应且互不重复
6. [DONE] 同步设置视觉、信息架构、首启和 AI 首页初始化文档，更新自动约束
7. [DONE] 执行定向 JVM 测试、架构门禁、正式准备、差异检查、Debug 构建和 APK 静态核验
8. [PENDING] 在目标设备验收六页视觉、左右滑动、系统授权往返、首个对话可见性和设置页配色

本地自动验证证据：

- 7 个相关 JVM 测试类共 `50/50` 通过，失败、错误和跳过均为 `0`
- `ci.test.test_architecture_boundaries` 共 `106/106` 通过；首启专属架构检查通过
- 正式开发准备检查通过，`git diff --check` 无 whitespace error，Markdown 链接单测 `7/7` 通过
- 完整 `phase=m03` 门禁仍发现工作树基线已有的市场持久化合同漂移、`AppDatabase` 受控哈希
  漂移和 `UserscriptSourceExportHelper.kt` 的 M-05E 路径消费者漂移；这些不属于本轮首启/
  设置范围
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `29` 个执行、`209` 个为最新状态
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`475435894` bytes，SHA-256
  `ABA70E52CA7099C2C9AE644EFF5295D8670F64AF75625EE2EB97B1CC35CF762C`
- APK 为 `com.kiyori`、`45 / 0.1.0`、target 34、唯一 `arm64-v8a`；包含 `51` 个原生库，
  ZIP 路径和原生库 basename 均无重复，内置 `assets/accessibility.apk` 恰好一份
- Android Debug v2 签名和 `zipalign -c -P 16 -v 4` 验证通过

## 2026-08-10 Kiyori 首启前四页统一版式与16能力图标

状态：中文前四页本地实现、自动验证与 Debug APK 已完成，已修复最新截图中的卡片文案截断、
主视觉图标错配、图标重复和页面间卡片位置漂移；协议、权限和左右滑动流程保持不变，真机
视觉继续保持 `verification_pending`。

本轮前四页统一使用同一个展示骨架：固定主视觉槽、标题槽、介绍槽和 2×2 能力网格基线。
第一页不再显示“AI 浏览器 · 内容工作台”，标题直接贴近主视觉，并把等量纵向节奏转移到介绍
与能力网格之间，因此四页能力网格的常规坐标保持一致；标题下方介绍正文使用两个中文字符
宽度的首行缩进，大字体和极窄屏允许槽位自然增高并由页面滚动承载。

四页只定义一份 `OnboardingFeatureCard` 列表，主视觉与下方卡片共同消费同一列表。产品总览
使用 `Explore / SmartToy / Layers / Extension`，浏览与内容使用
`Language / Download / PlayCircle / MenuBook`，AI 协作使用
`AutoAwesome / RecordVoiceOver / AccountCircle / Widgets`，本地工作区使用
`Folder / Terminal / Apps / BugReport`，四页共 16 个图标互不重复。

能力卡移除固定 86dp 和两行省略限制，改为行内等高、自适应最小高度和完整正文显示；文案
继续保持中文优先，并覆盖网页、下载、视频、音乐、小说、广告拦截、AI、语音、账号、工具箱、
文件、终端、小程序和日志等 Kiyori 能力。

设计、实施步骤与验收入口：

- [`kiyori_first_run_experience/`](kiyori_first_run_experience/index.md)

## 2026-08-09 Kiyori 完整首次启动体验

状态：中文六页信息密度与视觉层级已完成 r5 精修，支持应用图标一致性与首页 UI 已完成 r6 精修，左右滑动和本地自动验证已完成；真机视觉和系统授权往返保持
`verification_pending`。

本轮彻底替换 Operit 风格的协议、宣传、权限等级和完成页流程，建立 Kiyori 自有的六页中文首次
启动体验。流程依次为产品总览、浏览器与内容工作台、AI 与连接服务、本地文件/终端/小程序工作区、双协议
确认、权限选择；第六页完成用户所选授权后直接进入应用，不再保留独立完成页。

前四页不再使用“插画 + 一段正文 + 少量标签”的稀疏宣传布局，改为紧凑插画、主题导语和
2×2 能力卡片。产品总览覆盖浏览与内容、AI 与语音、文件与工具、连接与扩展；后三张介绍页
进一步展示网页浏览器、文件下载器、视频播放器、音乐/小说/广告拦截、AI 助手、语音服务、
账号与连接、工具箱、文件管理器、终端、小程序管理和日志记录器。

六页由同一个横向 Pager 管理，主按钮、顶部返回、左右滑动、步骤进度和持久化当前页保持一致；
单次滑动最多切换一页。未同意协议时不能向前滑入权限页，但可以向右返回上一页；授权处理中锁定
滑动与返回，避免系统授权队列和可见页面分离。

每页只保留一个主操作。协议不要求打开或滚动到底，勾选同意后即可继续；权限取消运行时、特殊
访问、高级能力分区和标准、无障碍、Shizuku、Root 等等级选择，改为统一清单，由用户逐项选择
需要授权的能力。当前只维护中文默认资源，其他语言在六页中文定稿和真机视觉验收后统一处理。

无障碍条目安装的独立支持应用已改为 `Kiyori 无障碍支持`，系统服务名为
`Kiyori UI 自动化服务`，并使用 Kiyori 图标。内部包名和 AIDL action 继续保留原值，因为它们是
主应用与支持应用之间的 IPC 兼容标识，不属于用户可见品牌。

Kiyori 始终未发布，旧 PermissionGuide 页面、ViewModel、资源、重复 `setContent` 和启动时无条件
通知请求直接删除，不保留兼容开关或并行入口。协议许可事实同步修正为 GPL-3.0-or-later。

设计、实施步骤与验收入口：

- [`kiyori_first_run_experience/`](kiyori_first_run_experience/index.md)

## 2026-08-09 包管理顶栏、固定搜索与市场安装刷新

状态：本地实现、自动验证和 Debug APK 已完成；目标设备交互保持 `verification_pending`。

Kiyori 始终未发布，本轮直接删除包管理右下角的环境变量、市场、添加和错误浮动入口，
不保留旧布局、兼容开关或并行入口。包管理顶栏在“包管理”标题右侧按固定顺序显示
“环境变量 / 市场 / 添加 / 刷新”；搜索框单独固定在顶栏下方，四个页签固定在搜索框下方，
只有列表内容滚动。

细化步骤：

1. [DONE] 核对宿主顶栏动作绑定、页面搜索状态、四页签结构和底部浮动按钮
2. [DONE] 定位市场安装成功后页面本地包快照未更新的根因
3. [DONE] 冻结顶栏动作顺序、固定搜索/页签层级、错误提示入口和加载状态
4. [DONE] 抽取唯一包管理快照重载入口，并接通手动刷新和市场安装成功信号
5. [DONE] 删除浮动按钮与旧顶栏搜索绑定，收回列表底部遮挡预留空间
6. [DONE] 增加动作顺序、市场目录修订信号和源码合同测试
7. [DONE] 同步 README、CONTEXT、架构文档并完成正式门禁与 Debug APK 核验
8. [PENDING] 在目标设备验收窄屏顶栏、输入法、搜索、页签、自动刷新和开关触摸

设计与验收入口：

- [`package_manager_header_and_market_refresh/`](package_manager_header_and_market_refresh/index.md)

本轮本地验收证据：

- 包管理相关 JVM 测试 `19/19` 通过，失败、错误和跳过均为 `0`
- 正式开发准备门禁通过；7 个新增资源键在 7 个语言目录中全部存在且 XML 可解析
- 当前工作树 8 份相关 Markdown 本地链接检查通过，`git diff --check` 无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `29` 个执行、`209` 个为最新状态
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`475317754` 字节，
  SHA-256 `8807B82473E086428396132915FD44D40D48ACB43C64A6AA25995A4A84C9227B`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / arm64-v8a`，Debug V2 签名和
  `zipalign -c -P 16 4` 均通过

目标设备上的真实窄屏顶栏、输入法、市场返回路径和安装后即时显示仍需单独验收。

## 2026-08-09 包管理环境变量配置抽屉

状态：首轮抽屉替换、分类视觉、紧凑密度和统一排序均已完成，目标设备交互保持
`verification_pending`。

Kiyori 始终未发布，本轮直接用可拖动底部抽屉替换包管理右下角环境变量按钮打开的旧弹窗，
不保留旧弹窗状态、兼容开关或并行入口。变量声明、读写与保存继续使用现有 `ToolPackage.env`
和 `EnvPreferences`，不改变 ToolPkg 格式、包启用校验或 MCP 环境变量页面。

细化步骤：

1. [DONE] 核对旧弹窗、环境变量 owner、包显示名和浏览器三态抽屉实现
2. [DONE] 冻结标题、搜索、横向分类、工具包折叠、固定操作区和取消/保存语义
3. [DONE] 抽取共享三态底部抽屉基础件，并保持浏览器现有入口与行为
4. [DONE] 实现环境变量搜索、按工具包类型分类、分组折叠、状态摘要和草稿编辑
5. [DONE] 删除旧弹窗专用实现，同步资源、语义文档和自动检查
6. [LOCAL DONE] 运行定向测试、正式开发门禁、差异检查和 Debug APK 构建核验
7. [PENDING] 在目标设备验收抽屉拖动、输入法、搜索、折叠与保存结果
8. [DONE] 为 17 个现有类型建立独立图标与浅深主题配色
9. [DONE] 压缩环境变量工具包头、变量项和横向分类条的垂直密度
10. [DONE] 类型按英文 A 到 Z、类型内按英文或中文拼音首字母排序
11. [DONE] 更新策略测试、语义文档并重新构建核验 Debug APK
12. [DONE] 每次打开时仅自动展开所有存在未填写必填变量的工具包，其余默认收起

设计与验收入口：

- [`package_environment_variables_drawer/`](package_environment_variables_drawer/index.md)

本地验收证据详见
[`package_environment_variables_drawer/`](package_environment_variables_drawer/index.md)；
本轮最终 APK 指纹为 `263252033D05E2E5FBCAECF882CAE1688F2032D329D1248070472CF7AE439776`。

## 2026-08-09 Python 环境与任务日记恢复运行时

状态：本地实现、Skill 全量验证、正式开发准备检查与 Debug APK 核验均已完成。

本轮只调整开发工作流，不改变 Android 产品行为或 Python 项目依赖。Kiyori `.venv` 继续服务
仓库自有 `ci/script` 与测试入口；Codex 全局 Skill 和控制面使用自身记录或指定的绝对运行时，
不向项目 `.venv` 安装与 Kiyori 无关的 `tzdata`。

细化步骤：

1. [DONE] 确认 IANA 失败来自 task-diary 错用项目 `.venv`，不是 Kiyori 运行依赖缺失
2. [DONE] 收窄全局和项目 `AGENTS.md` 的 Python 运行时路由边界
3. [DONE] 让 task-diary 新日记持久化 Python 与恢复命令，旧日记首次成功写入时透明补录
4. [DONE] 让 `resolve/list` 和 IANA 错误路径公开恢复命令来源，不伪称旧日记存在原始运行时
5. [DONE] 运行 task-diary 全量测试、Skill 快速验证、真实隔离恢复演练和数据边界审计
6. [DONE] 运行正式开发准备检查、差异检查和规定的 Debug APK 构建核验

本地验收证据：

- task-diary 全量测试 `65` 项通过；Skill `quick_validate.py`、Python 语法编译与目标差异检查通过
- 私有日记索引共 `187` 份，校验错误 `0`、索引拒绝 `0`；第二次增量索引读取正文 `0`
- 使用 Kiyori `.venv` 复现 IANA 时区失败后，错误信息明确给出已记录的系统 Python 和精确恢复命令；
  使用该命令成功恢复当前日记，未安装 `tzdata`，也未使用固定时差或其他回退逻辑
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功完成 `238` 个任务，
  其中 `25` 个执行、`213` 个为最新状态
- `D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk` 大小
  `475308770` 字节，SHA-256
  `2BEEE13F9CB206F8AC2EE9D5AA0DC85C0C2BA7B39B391863F374B2B20E903B0F`；
  `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`，
  Android Debug v2 签名与 `zipalign -c -P 16 4` 均通过

本轮未改变 Kiyori Python 依赖、未安装 `tzdata`、未安装 APK、未执行设备操作，也未提交或推送。

## 2026-08-09 Operit v1.12.1 必要 AI 插件发布更新

本轮只处理 Kiyori 上次 `1.12.0+9` 审计终点之后的真实增量。上游
`v1.12.1@4faa5cd2` 相对 `93a28251` 只新增市场登记失败后的 GitHub Release 资产保留修复，
同时包含不属于 Kiyori 的 Operit 应用版本号变更。

细化步骤：

1. [DONE] 核验 `v1.12.1` 标签、提交图、发布补丁和 Kiyori `1.12.0+9` 既有能力
2. [DONE] 确认角色卡选中发送等 AI 更新已落地，当前唯一缺口是登记失败后的破坏性资产清理
3. [DONE] 保留已上传 Release/asset，继续返回原有 `RegistrationFailed`
4. [DONE] 增加源码合同测试，保持同名资产上传前替换语义不变
5. [DONE] 执行正式开发门禁、差异检查、Debug APK 构建与产物核验
6. [DONE] 修复完整 Python 门禁中遗留的播放器长按倍速源码合同漂移
7. [DONE] 运行完整 Python、JVM、Lint、正式准备检查和最终 Debug 构建
8. [DONE] 审计敏感内容、构建产物、子模块、Git mode、异常大文件和暂存树
9. [DONE] 提交并推送 `main`，核对本地、跟踪和远端 ref 一致

Kiyori `versionCode/versionName` 和 `OPERIT_MARKET_COMPAT_VERSION=1.12.0+9` 均保持不变。
现有 WASM、A2A、远程 MCP、用户资料、主题、签名、国际化和发布素材不属于本轮必要范围。
本地自动验证与 Debug APK 核验已完成；未创建真实 GitHub Release 或触发线上登记失败，
远端发布链保持 `verification_pending`。
完整记录见
[`operit_1_12_1_ai_update/`](operit_1_12_1_ai_update/index.md)。

## 2026-08-09 网页浏览器返回、缩放、文字与网站密码设置

状态：本地实现与自动验证完成，目标设备网页行为保持 `verification_pending`。继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器产品能力的唯一进度载体，不创建第二套 Browser Runtime、设置 owner 或凭据数据库。
参考 `D:\10_Project\hikerView` 的 WebView 行为和指定截图的信息层级，但界面统一使用当前
`KiyoriSettingsUi` 折叠标题、分组卡片、Material Switch 与页面内子页。

细化步骤：

1. [DONE] 核对当前 `WebSessionBrowserSettingsStore`、网页 Back 状态机、WebView 设置应用点、
   参考源码与四张参考图片
2. [DONE] 将浏览器设置规划为“网页插件与脚本 / 主页与导航 / 网页显示 /
   网站权限与数据 / 音视频嗅探”五组
3. [DONE] 新增“返回不重载”，只改变现有网页历史 Back 的缓存策略；不改变浏览器顶栏返回、
   每窗口主页根、Shell 返回目标或 WebSession 生命周期
4. [DONE] 新增“强制页面缩放”，通过当前页面 viewport 合同解除网页禁止缩放限制；
   新旧页面和设置即时切换继续使用同一 WebView
5. [DONE] 新增“网页文字大小”子页，提供实时文字示例、`50%..200%` 的 `5%` 步进、
   增减与恢复默认操作，并由 `WebSettings.textZoom` 应用到全部现有及后续 WebSession
6. [DONE] 新增“网站密码管理”子页和唯一私有凭据 vault；自动保存默认关闭，只允许普通
   Profile 捕获和填充，按精确 HTTP/HTTPS origin 匹配，支持搜索、查看、复制、编辑和删除
7. [DONE] 凭据使用 Android Keystore AES-GCM 加密，文件位于 `noBackupFilesDir`；
   不记录日志、不进入当前原始快照备份、不向无痕 Profile 或其他网站暴露
8. [DONE] 增加设置结构、持久化约束、Back 缓存策略、缩放脚本、凭据 origin/脚本合同和
   管理行为测试，同步 `CONTEXT.md`、README 与浏览器设置里程碑
9. [LOCAL DONE] 运行定向 JVM、正式开发门禁、资源与差异检查，串行构建并核验 Debug APK；
   目标设备上的网页 Back、双指缩放、站点字体和真实登录表单保持单独验收

本地验收证据：

- 四组策略与设置页面定向 JVM：`22` 项通过，零失败、零错误、零跳过
- Lint 新问题与可见报告均为 `0`；基线保留 `5567` 项，`stale=0`、`current-only=0`
- Lint 清理同步修正五个非英语语言目录中的 `77` 个缺失 key（共 `385` 条翻译）、Compose
  资源读取、KTX、状态类型、Modifier 顺序和复数候选问题，并将 Media3 升级到 `1.11.0`
- 本轮相关 CI Python 测试 `115` 项通过；正式开发准备门禁、AAPT 资源处理和
  `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 于
  `2026-08-09 13:19:22 +08:00` 成功生成
  `D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- APK 大小 `475308770` 字节，SHA-256
  `19DCD1D1F184344E6C196D896D30B70F454D0CAF1567BA8E2AF8ACEF3B96E2AB`；
  `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、
  `targetSdk 34`、`arm64-v8a`，v2 调试签名和 `zipalign -c -P 16 4` 均通过
- 本轮未安装 APK、未执行 ADB/MuMu/真机操作；真实网页连续 Back、动态 viewport、
  双指缩放、站点字体和登录表单保存/自动填充仍待目标设备验收

当前非目标：

- 不改变浏览器顶栏左侧返回 App Shell 入口页的语义
- 不给无痕窗口保存、读取或自动填充网站密码
- 不接入 Android 系统密码管理器、云同步、备份导入或跨设备凭据迁移
- 不增加第二个 WebView、第二套历史、第二份设置状态或任何回退路径
- 不提交、不推送、不安装 APK、不执行 ADB、MuMu 或真机操作

## 2026-08-08 AI 助手模型名称标签管理

状态：第五轮三行折叠、真实隐藏数量标签与清空反馈修复已完成本地实现、定向验证和 Debug APK 核验；Android 目标设备上的视觉与触摸验收仍待执行。继续使用稳定的自定义测量和纯行计划，不恢复高度裁切或已否决的 Flow overflow API。继续保留 `ModelConfigData.modelName` 作为既有序列化边界，不创建第二个模型数据源。

设计与验收入口：

- [`ai_model_tag_management/`](ai_model_tag_management/index.md)

本轮范围：

- 手动输入一个或多个模型名称，按逗号或换行解析后去重追加
- 从当前 API 上游获取模型列表，支持搜索、多选和追加
- 标签点击复制、叉号删除、长按进入纵向排序面板
- 收起时只放置三行完整标签和无叉号的“还有 N 个”，`N` 为真实隐藏数量；展开时显示全部
- 第一项明确显示为测试模型，连接测试结果展示实际测试模型名
- 排序时按模型名修正功能模型与固定角色卡绑定的索引
- 删除已绑定模型时明确提示影响范围并指定新的第一项；唯一且仍被绑定的模型不可直接删除
- 使用现有模型获取、配置保存、连接测试和 `EnhancedAIService` 刷新链路
- 将“添加模型 / 从上游获取 / 更多”统一为同一行、同高度和同圆角的操作栏
- 将“重命名 / 删除 / 测试模型”统一为同一行，并让测试入口保持更明确的主操作层级
- 将上游模型长列表改为设置页风格的底部面板，统一搜索、刷新、全选、列表和底部确认区
- 移除导致搜索提示文字裁切的 `48dp` 强制高度，按 Material3 输入框最小高度布局
- 连接测试仍以第一项模型为测试目标，并覆盖聊天以及已启用的 Tool Call、识图、音频和视频能力
- 上游面板允许取消勾选当前已有模型，应用时只同步当前上游范围内的添加和移除差异
- 上游未返回的手动或历史模型保持原顺序；移除绑定模型继续经过明确确认
- 取消标签固定最大宽度并增加内部留白；撤销高度裁切，使用三行放置计划和完整的“还有 N 个”标签
- 六个模型操作按钮统一为共享的 `40dp` 高度与 `12dp` 圆角
- 清空全部立即显示检查状态；无绑定时确认清空，有绑定时显示明确阻止对话框

本轮非目标：

- 不改写 API 提供商协议、模型获取协议、备份格式或外部 WebChat 的模型索引协议
- 不新建独立模型数据库、第二个模型列表持久化字段或并行状态 owner
- 不改变既有错误处理策略，不引入第二条模型获取或保存路径
- 不提交、不推送、不安装 APK、不执行 ADB、MuMu 或真机操作

本地证据：

- 首轮 4 个相关测试类共 `26` 项测试通过；第二轮 `ModelNameListTest` 与 `ModelNameTagEditorContractTest` 共 `10` 项测试再次通过，失败、错误和跳过均为 `0`
- 正式开发准备门禁通过；`git diff --check` 无 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，238 个任务中 29 个实际执行，并通过单 Launcher 与播放器运行时打包校验
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`com.kiyori`，`0.1.0 (45)`，`471616082` bytes，`arm64-v8a`
- APK SHA-256：`EFB9C79C96B59ED3ABB574E2D868F93FE4F68D3693DF75806562EDF6599CF261`
- Android Debug V2 单签名验证与 `zipalign -c -P 16 -v 4` 验证通过
- 第三轮 `ModelNameListTest` 与 `ModelNameTagEditorContractTest` 共 `12/12` 通过；正式门禁、Markdown 解析器和差异检查通过
- 第三轮 APK：`471619002` bytes，SHA-256 `B343051729C55FCAE71F48A196DA1412788AFACDF4466E3C81BAEC7EC1DBC9E3`，Debug V2 单签名与 16KB zipalign 通过
- 第四轮首次实现改用普通 `FlowRow` 两行上限并删除旧裁切，但其组合阶段读取 `shownItemCount` 会触发 APP_FATAL
- 第四轮定向测试 `12/12`、formal readiness、Markdown 检查器 `7/7`、8 份文档链接扫描和 `git diff --check` 通过
- 第四轮首次 APK（已否决）：`471619002` bytes，SHA-256 `4EB315694445FFD53781DCBD3000FB230946C84D5CAE6D2743E3F22D6CA99B3F`
- APP_FATAL `b79f91c3-11c9-4ea5-87d0-00e34a812f79` 指向 `ModelNameTagEditor.kt:194`；修正版删除全部 Flow overflow API，使用 `Layout` 与 `planModelTagFlow`
- 修正版定向测试 `18/18`、formal readiness、Markdown 检查器 `7/7`、8 份文档链接扫描和 `git diff --check` 通过
- 修正版 APK：`471618990` bytes，SHA-256 `A34675DE5E4745ABE6BD45C9C5C60EB547865CBC5A3D1566EDC626AB8A09554A`，Debug V2 单签名与 16 KB zipalign 通过
- 第五轮将折叠上限改为三行，使用 `SubcomposeLayout` 先测量完整标签和最大计数展开标签，再由纯行计划返回真实隐藏数量；“还有 N 个”标签没有删除叉号
- 第五轮清空全部在启动异步绑定检查前立即显示 `Checking` 模态状态，随后进入 `Confirm` 或 `Blocked`，不再把关键阻止反馈放在长页面末尾
- 第五轮 `ModelNameListTest`、`ModelTagFlowPlanTest` 与 `ModelNameTagEditorContractTest` 合计 `19/19` 通过，失败、错误和跳过均为 `0`
- 第五轮 formal readiness、Markdown 检查器 `7/7`、8 份文档链接扫描和 `git diff --check` 通过
- 第五轮 Debug APK：`471620006` bytes，SHA-256 `4642C2CD74B5F7A82729B44D4F004D879B2737807D5D058BD9DC948E7FE5A857`，Debug V2 单签名与 16 KB zipalign 通过
- 第五轮构建主机文件时间为 `2026-08-09 02:57:45 +08:00`，仅作为产物元数据；当前会话日期仍为 `2026-08-08`

## 2026-08-08 Operit `1.12.0+8/+9` AI 与 ToolPkg 兼容更新

状态：上游版本、实时市场和 Kiyori 公共脚本接口差异审计、必要实现、本地自动验证和
Debug APK 制品核验已完成；目标设备交互保持待验证。
本轮严格参考 Operit `3f146057`、`4bf0138b` 及 `+9` 后同日修复，不整分支合并，不用
Kiyori 产品版本冒充 Operit 生态兼容版本。

实施与验收步骤：

1. [DONE] 核实 `+8/+9` 版本提交、共同祖先、上游后续修复和实时市场最新包
2. [DONE] 下载并校验全部 19 个 `minAppVer=1.12.0+9` 最新 ToolPkg，提取实际 Hook 与
   `Tools.*` API 需求
3. [DONE] 补齐 ChatMessage Hook、Hook 总时限与聊天可见超时提示、角色卡
   `SoftwareSettings`、定位地址参数和市场兼容判断
4. [DONE] 移植待发送队列、超大消息安全读取、选中角色卡发送、默认 Tool Call、
   DeepSeek low、超长粘贴附件化和记忆提取规则
5. [DONE] 同步内置 ToolPkg、TypeScript 声明、插件开发文档、`CONTEXT.md`、README
   与正式开发准备兼容基线
6. [DONE] 增加或移植定向测试，执行正式开发门禁、差异检查和必要的源码/资源检查
7. [DONE] 串行构建并核验 Debug APK；真机市场安装、聊天 Hook、超时提示和插件 UI
   继续单独记录

当前非目标：

- 不移植 A2A 外部服务、发布签名、终端会话恢复、上游目录搬迁、主题系统或 CI 跳过策略
- 不建立第二套 ToolPkg、市场、聊天、角色卡、数据库或记忆状态 owner
- 不增加回退、降级或兼容旁路；不提交、不推送、不安装或操作设备

本地证据：

- `message_insert` TypeScript 编译通过；ToolPkg、市场兼容、队列隔离和粘贴识别 4 个
  JVM suite 共 `14/14` 项通过，零失败、零错误、零跳过
- formal readiness 通过；Markdown 检查器单测 `7/7`；`git diff --check` 无错误
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 31s`，238 个任务中 34 executed / 204 up-to-date；
  唯一 Launcher 与播放器运行时打包校验通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 22:55:52 +08:00`，大小 `471598782` 字节，SHA-256
  `D0B162DAD2AC9669F09292DCC108C9C858C74CFEC30DF9EE3711365A4C48FFE4`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名且
  `zipalign -c -P 16 -v 4` 通过
- APK 内嵌 `message_insert.toolpkg` 与 Gradle 生成物逐字节一致，大小 `18320` 字节，
  SHA-256 `A1EB5B57BBC5168D75A3E6FCBD2906EC1A64B1D0576F0274B0D34D9FFA4CED28`

详细计划见
[`operit_1_12_0_plus_8_plus_9_ai_plugin_update/`](operit_1_12_0_plus_8_plus_9_ai_plugin_update/index.md)。

## 2026-08-08 浏览器插件与负一屏入口配色调整

状态：本地实现、自动验证和 Debug APK 已完成；目标设备视觉验收待完成。Kiyori 当前未发布，本轮直接迭代现有浏览器 18 色身份表和负一屏颜色映射，
不保留旧洋红插件色兼容开关，不增加第二套主题或状态 owner。

本轮细化步骤：

1. [DONE] 审计插件入口、插件中心“本页 / 已安装 / 更新 / 日志”、插件卡主图标、负一屏四张
   数据卡和八个快捷工具的颜色调用链
2. [DONE] 将 `PLUGINS` 从高饱和洋红调整为低饱和深梅紫，并让插件中心各子页的
   插件主图标统一复用该入口色；错误、成功、更新风险等状态提示继续使用状态语义色
3. [DONE] 让负一屏“收藏 / 书签 / 历史 / 下载”分别复用浏览器菜单
   `ADD_BOOKMARK / BOOKMARKS / HISTORY / DOWNLOADS` 的身份色
4. [DONE] 为“新版 / 手册 / 版本 / 搜索 / 工具箱 / 清理 / 备份 / 退出”选择八个
   两两不同且语义接近的现有浏览器入口色
5. [DONE] 增加插件色、负一屏四入口一致性和八色唯一性测试，同步 `README.md`、
   `CONTEXT.md` 与浏览器产品完成清单
6. [DONE] 运行定向 JVM、正式开发门禁、Markdown/架构/差异检查和最终 Debug APK
   构建与制品核验
7. [PENDING] 目标设备验收插件深梅紫、各插件子页、负一屏四入口及八个快捷工具在浅色/
   深色主题下的实际观感与可读性

当前非目标：

- 不改变插件、书签、历史、下载、Browser Runtime、用户脚本或负一屏快捷工具的功能和状态所有权；
  “收藏”继续保持零计数和空点击，专用于未来小程序服务
- 不用插件身份色覆盖错误、危险、执行成功、更新安全性等局部状态反馈
- 不安装 APK、不操作 ADB、MuMu 或真机；不创建提交、不推送远端

本地证据：

- 9 个相关 JVM suite 共 `89/89` 项通过，零失败、零错误、零跳过；其中颜色策略 `5/5`、
  负一屏与设置页 `12/12`、Shell 返回/恢复 `53/53`、顶栏返回策略 `2/2`
- ARCH041 消费者架构正反向测试 `2/2` 通过；完整 `phase=m03` 架构门禁只剩任务开始前已记录且
  本轮未修改的 ARCH046 `UserscriptSourceExportHelper.kt` 消费者快照漂移
- formal readiness 通过；Markdown 单测 `7/7`，257 个工作树 Markdown 文件缺失本地链接为
  `0`；`git diff --check` 和新增 Kotlin 禁用兜底语义扫描通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 52s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 20:08:41 +08:00`，大小 `471581414` 字节，SHA-256
  `B585A865CE7C349EB41897F1B568E78B8802F8FF13DC95F22DB23C3DCEDC9084`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名、
  `zipalign -c -P 16 -v 4` 通过

## 2026-08-08 浏览器顶栏返回入口页

状态：本地实现、自动验证和 Debug APK 已完成；目标设备上的入口页返回、浏览器状态恢复和
网页前进/后退交互待验收。Kiyori 当前未发布，本轮直接修正浏览器顶栏返回语义，不保留旧的
逐级网页回退接线，不增加第二套 Browser Runtime、WebView、窗口或 Shell 导航状态。

本轮细化步骤：

1. [DONE] 将浏览器顶栏左侧返回与系统 Back、底栏网页后退解耦；顶栏直接结束
   Browser Home 展示，系统 Back 和底栏左右按钮继续处理临时界面及网页历史
2. [DONE] 扩展 Shell 的浏览器返回目标，使软件首页三页、AI 对话、微应用、文件管理和设置首页
   进入浏览器后都能返回原入口页
3. [DONE] 复用现有 presentation release 与 `KiyoriShellState.exitBrowser()`，保留唯一
   WebSession、活动 WebView、窗口顺序、网页历史和 Profile 状态
4. [DONE] 增加顶栏 release mode 与各入口页返回目标测试，同步 `README.md`、`CONTEXT.md`
   和浏览器产品完成清单
5. [DONE] 运行定向 JVM、正式开发门禁、Markdown/差异检查和 Debug APK 构建与制品核验
6. [PENDING] 目标设备验收从 AI 对话、设置首页及其他底栏入口进入后的顶栏返回、浏览器状态恢复、
   系统 Back 和底栏网页前进/后退

当前非目标：

- 不改变底栏网页后退/前进、主页按钮、窗口管理、网页历史根或 AI `browser_navigate_back`
- 不改变浏览器菜单“退出浏览器”和最小化 indicator 的既有语义
- 不安装 APK、不操作 ADB、MuMu 或真机；不创建提交、不推送远端

本地证据：

- 顶栏策略、Shell 返回目标和原浏览器 Back 策略 3 个定向 JVM suite 共 `56/56` 项通过，
  零失败、零错误、零跳过
- formal readiness 通过；Markdown 检查器单测 `7/7`，257 个工作树 Markdown 文件缺失本地链接为
  `0`；`git diff --check` 和新增代码禁用兜底语义扫描通过
- 完整 `phase=m03` 架构门禁执行 37 段；本轮触及的 ARCH020、ARCH021、ARCH023、ARCH024
  均通过，只报告任务开始前已记录且本轮未修改的 ARCH046
  `UserscriptSourceExportHelper.kt` 消费者快照漂移
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 22s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 19:35:38 +08:00`，大小 `471581414` 字节，SHA-256
  `B274A738017A4F371A42F4003A503F7137A3964BDC4510334EF48095C802F7E3`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名且
  `zipalign -c -P 16 -v 4` 通过；包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不包含 `libsudo.so`

## 2026-08-08 浏览器四行菜单十八色统一

状态：本地实现、自动验证和 Debug APK 已完成；目标设备上的亮暗主题视觉与触控验收待完成。
Kiyori 当前未发布，本轮直接完善现有浏览器菜单视觉，不保留旧配色兼容开关，不增加回退路径，
也不改变按钮顺序、路由、Browser Runtime 或各功能状态 owner。

本轮细化步骤：

1. [DONE] 审计四行菜单 18 个按钮、共享抽屉标题、弹窗、搜索/筛选强调色、空态和搜索框右侧
   资源数字球的颜色调用链
2. [DONE] 建立浏览器菜单专属 18 色稳定映射，浅色与深色主题下每个入口的图标色和
   低饱和容器色均保持两两不同
3. [DONE] 将书签、历史、下载、插件、悬浮嗅探、UA、网络日志、工具箱、阅读模式、
   查看源码、广告标记和网站配置的子页面身份 UI 统一为对应入口色
4. [DONE] 让搜索框右侧资源数字球直接复用“悬浮嗅探”入口颜色
5. [DONE] 增加颜色唯一性、菜单顺序及入口/子页面/数字球一致性测试，并同步浏览器产品文档
6. [DONE] 运行定向 JVM 测试、正式开发门禁、Markdown/差异检查和 Debug APK 构建与制品核验
7. [PENDING] 目标设备验收四行菜单、各子抽屉/弹窗、亮暗主题和资源数字球的实际颜色与对比度

当前非目标：

- 不改变普通/无痕窗口蓝紫身份色、按钮功能、页面导航、下载/媒体/脚本数据或持久化格式
- 不用入口色替换删除危险、执行成功、媒体分类、网络分类等局部状态语义色
- 不安装 APK、不操作 ADB/MuMu/真机；不创建提交、不推送远端

本地证据：

- `WebSessionBrowserMenuColorPolicyTest` 固定 18 个入口、`5/5/5/3` 四行结构、浅色与深色图标/
  容器/组合两两唯一、全部不低于 `3:1` 非文本对比度，以及资源数字球对
  `FLOATING_SNIFFER` 的直接复用
- 颜色、浏览器视觉、下载、媒体候选、网络日志、页面源码、UA、书签、历史和用户脚本共
  11 个定向 JVM suite、`70/70` 项通过，零失败、零错误、零跳过
- ARCH024 App Shell 与 ARCH041 语义色消费者的 4 个正负契约测试通过；完整 `phase=m03`
  架构检查只剩本轮开始前已记录的 ARCH046
  `UserscriptSourceExportHelper.kt` 消费者快照漂移，本轮未修改其存储 owner
- formal readiness 通过；Markdown 检查器 `7/7`、`git diff --check` 和新文件禁用回退语义
  扫描通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 3s`，238 个任务中 29 executed / 209 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 17:34:23 +08:00`，大小 `471581414` 字节，SHA-256
  `F7DCF31472EC162B7BE4ADAA18C02E76D6DB4D89C8C55F5036E99E1D99ACEE54`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 Launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名且
  `zipalign -c -P 16 -v 4` 通过；包含 `liboperit_ripgrep.so` 与
  `assets/operit_shell_exec`，不包含 `libsudo.so`

## 2026-08-08 首页底栏与浏览器彩色弹窗统一

状态：上一批与用户复核增量均已完成本地实现、自动验证和 Debug APK，目标设备视觉与交互验收待完成。
Kiyori 当前未发布，本轮直接完善现有界面，不保留旧视觉兼容开关，不增加回退路径，也不改变
Browser Runtime、下载、UA、媒体候选或页面源码的状态 owner。

本轮细化步骤：

1. [DONE] 让负一屏或 AI 首页向软件首页拖动时，底部五入口按 Pager 实时偏移立即显现；静止在
   负一屏、AI 首页、AI 深层页面或打开 Shell 子层/抽屉时仍保持隐藏
2. [DONE] 为浏览器窗口总览建立普通蓝色、无痕紫色语义色；顶部保持同一行文字标签和不同颜色
   下划线，不绘制按钮容器；空态、新建按钮、活动卡片、缩略图占位和身份标记继续使用对应色
3. [DONE] 增加共享语义弹窗 Surface；“我的下载”使用绿色抽屉标题与操作弹窗，UA 选择/编辑使用
   蓝色弹窗，媒体候选长按及链接查看使用青色弹窗
4. [DONE] 为页面源码超长行信息条增加右侧关闭按钮；关闭状态只绑定当前 session 与
   Document token，不改变源码、软换行或统计数据
5. [DONE] 同步 `README.md`、`CONTEXT.md` 和浏览器产品能力分项文档
6. [DONE] 运行 Shell/Profile/源码定向 JVM 测试、formal readiness、Markdown/差异检查和
   禁用回退语义反向扫描
7. [DONE] 串行执行 `:app:assembleDebug --no-daemon --console=plain`，核验 Debug APK 的时间、
   大小、SHA-256、包名、版本、唯一 launcher、V2 签名与 16 KB ZIP 对齐
8. [PENDING] 目标设备验收拖动中底栏首帧、普通/无痕亮暗主题、窄屏下载标题动作区、UA 输入法、
   嗅探资源长按弹窗和源码提示关闭/重新抓取

当前非目标：

- 不修改普通/无痕 Profile 隔离、标签创建/清理、下载队列、UA 优先级、媒体候选排序或源码应用语义
- 不创建第二套弹窗状态、下载 owner、Browser Runtime 或持久化字段
- 不安装 APK、不操作 ADB/MuMu/真机；不创建提交、不推送远端

本地证据：

- JDK 21 下 `:app:compileDebugKotlin` 通过；7 个定向 JVM suite 共 `99/99` 项通过，零失败、
  零错误、零跳过，其中 `KiyoriShellStateTest 51`、窗口视觉策略 `1`、下载抽屉策略 `10`、
  UA 策略/路由 `7`、媒体候选策略 `19`、页面源码支持 `11`
- formal readiness 通过；Markdown 检查器单测 `7/7`，8 个本轮修改 Markdown 文件新增缺失本地链接
  为 `0`；`git diff --check` 和新增源码禁用回退语义扫描通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 57s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 15:23:06 +08:00`，大小 `471581414` 字节，SHA-256
  `1922760B24152AF916BECDEC5846CC1017FD7A13B53F79E228B62B0525E2EFA6`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名与
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK，未操作 ADB、MuMu 或真机；拖动首帧、亮暗主题和窄屏弹窗继续保持
  `verification_pending`

### 用户复核增量

1. [DONE] 普通窗口和无痕窗口保持等宽、同一行的文字标签，标题强制单行；普通使用蓝色下划线，
   无痕使用紫色下划线，选中项只改变文字强调、线条透明度和粗细
2. [DONE] 配置自定义首页时，清空当前 Profile 后复用现有 `onNewTab(profile)` 创建主页窗口并
   关闭窗口总览；默认 `about:blank` 继续显示剩余 Profile 或空总览
3. [DONE] `WebSessionDrawerHeader` 增加标题后动作槽位；下载四竖线保留 `36dp` 点击热区，视觉从
   标题后 `4dp` 开始绘制，新增/清理动作仍位于标题栏右侧
4. [DONE] JDK 21 下 Debug Kotlin 编译通过；窗口、主页、Profile 与下载抽屉 5 个 suite 共
   `29/29` 通过，零失败、零错误、零跳过
5. [DONE] 重新运行 formal readiness、Markdown/差异检查和最终 Debug APK 构建与制品核验
6. [PENDING] 目标设备验收 320dp 窄屏单行标签、蓝/紫横线、清空后的自定义首页和下载菜单位置

用户复核增量证据：

- JDK 21 下 `:app:compileDebugKotlin` 为 `BUILD SUCCESSFUL in 1m 26s`；窗口清空、主页导航、
  Profile 与下载抽屉 5 个定向 suite 共 `29/29` 通过，零失败、零错误、零跳过
- formal readiness 通过；Markdown 检查器单测 `7/7`，8 个修改 Markdown 文件缺失本地链接为 `0`；
  `git diff --check` 无 whitespace error，新增代码禁用回退语义扫描零命中
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 55s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-08 15:47:40 +08:00`，大小 `471581414` 字节，SHA-256
  `8E1C37CBD651B36417DDF075741802E58FD086AD82CBA884C600F1FE866785C3`
- APK 为 `com.kiyori / 45 / 0.1.0 / compileSdk 37 / minSdk 26 / targetSdk 34 / arm64-v8a`；
  唯一 launcher、Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 通过

### 用户复核增量：抽屉标题栏与弹窗统一

1. [DONE] 下载四竖线继续位于“载”字右侧并保留 `36dp` 热区，按压反馈通过
   `CircleShape` 裁剪为圆形，图形在圆形反馈内水平和垂直严格居中，不再出现正方形点击阴影
2. [DONE] 书签、历史、下载、网络日志、视频资源、占位功能页、插件和用户脚本子页全部复用
   `WebSessionDrawerHeader`；顶部统一为 `52dp` 高度、`34dp` 语义徽标、`8dp` 图标标题间距和
   同一标题字级，网络日志标题不再紧贴图标
3. [DONE] 网络日志操作/详情、书签编辑/文件夹选择/确认、下载排序/新增/完整链接/删除/长按操作/
   重命名、用户脚本安装/删除/日志详情/元数据/审阅等项目自定义弹窗全部复用
   `WebSessionBrowserDialogSurface`；统一语义色徽标、`20dp` 圆角、细描边、标题分隔线、阴影和
   全窗口遮罩。历史删除时间范围保留底部选择交互，但复用同一弹窗标题头
4. [DONE] 增加浏览器视觉策略测试，固定共享标题栏几何、标题动作圆形反馈及内容严格居中合同；
   最终居中修正后的浏览器视觉与下载抽屉 2 个 suite 共 `13/13` 通过
5. [DONE] JDK 21 下 Debug Kotlin 编译通过；下载、网络日志、媒体、书签、历史、用户脚本和浏览器
   视觉策略 7 个 suite 共 `48/48` 通过，零失败、零错误、零跳过
6. [DONE] formal readiness、Markdown 检查器 `7/7`、8 个修改 Markdown 文件本地链接扫描和
   `git diff --check` 通过
7. [DONE] 串行构建 Debug APK；最终居中修正版 `:app:assembleDebug` 为
   `BUILD SUCCESSFUL in 53s`，238 个任务中 28 executed / 210 up-to-date，唯一 Launcher 与
   播放器运行时打包校验通过
8. [PENDING] 目标设备验收 320dp 窄屏顶部动作、网络日志标题间距、抽屉拖动、亮暗主题及各类
   弹窗的输入法、滚动和系统 Back

本轮制品：

- `app/build/outputs/apk/debug/app-debug.apk`
- 生成时间 `2026-08-08 16:36:26 +08:00`，大小 `471581414` 字节，SHA-256
  `089BBDE3288D410E5B3C731EE9E2A5E0087B6ED0276E72AF959189198F7D5408`
- `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`
- 唯一 Launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单签名，
  `zipalign -c -P 16 -v 4` 通过
- 未安装 APK、未操作 ADB、MuMu 或真机；未创建提交、未推送远端

## 2026-08-03 浏览器插件与脚本模块初步封板

状态：本地实现、自动验证与 Debug APK 完成，目标设备验收待完成。继续复用唯一
`StandardBrowserSessionTools`、`BrowserPluginCenterFacade`、
`UserscriptRepository` 和 `WebSessionUserscriptManager`，不创建第二 Browser Runtime、插件注册表、
脚本仓库、日志 owner 或设置状态源。

本轮只读审查确认：

- 网页浏览器设置当前共显示 `30` 行，其中 `17` 行没有真实 action、owner 或 consumer，只能以禁用占位
  展示；这些选项不应继续占用正式设置界面
- “网页插件与脚本”组的插件中心、油猴脚本管理、权限、当前页诊断和日志入口存在重复导航；
  权限子页又重复提供插件中心、管理、诊断和日志入口
- 油猴工作台在当前 Android System WebView 不支持 userscript runtime 时，仍可从右上角菜单和
  授权横幅请求开启用户脚本
- 插件中心“本页”只渲染 provider 级卡片，但标签数量使用 userscript 条目数，计数语义不一致
- 批量删除 userscript 时，任一删除异常会中断剩余项目且没有明确失败反馈
- userscript 值、草稿和日志按数值 `scriptId` 绑定；删除 registry 后重新安装会获得新 ID，
  因此直接增加“保留数据”开关只会制造无法恢复的孤儿数据

实施与验收顺序：

1. [DONE] 浏览器设置只保留连接真实 owner 的选项，删除全部无 action 的禁用占位；将页面收敛为
   “网页插件与脚本 / 主页与网站数据 / 音视频嗅探”三组
2. [DONE] “网页插件与脚本”收敛为总授权、插件中心、权限与网站范围、诊断与日志四个入口；
   权限子页只保留运行环境和逐脚本权限，不重复导航
3. [DONE] 修复 runtime 不支持时仍可请求授权的问题；插件中心“本页”标签按实际 provider 卡片计数
4. [DONE] 增加已安装 userscript 源码导出，输出 `.user.js` 到 `Download/Kiyori/exports`；
   删除仍明确清理源码、修订、草稿、值和日志，不提供无恢复能力的数据保留伪选项
5. [DONE] 让单项与批量删除逐项处理错误、记录日志并向用户反馈，不因一个失败静默中断后续删除
6. [DONE] 更新设置、插件投影、userscript UI 与删除链路测试；同步 `README.md`、`CONTEXT.md`、
   浏览器插件架构和实施 TODO
7. [DONE] 运行 formal readiness、定向 JVM 测试、资源/XML 与 Markdown 检查、`git diff --check`，
   再串行构建并核验 Debug APK
8. [ ] 目标设备验收设置精简、深层路由、unsupported 状态、源码导出、删除失败反馈、抽屉计数和
   窄屏交互；本轮未获设备操作授权，不安装 APK

当前非目标：

- 不实现 `.kbx`、WebExtension、CRX/Edge 扩展直接安装、background worker 或新插件 provider
- 不扩大 userscript 注入、权限、GM API、匹配和网络能力
- 不把用户脚本、AI ToolPkg、Skill、MCP、工作流或包管理合并
- 不提交、不推送、不部署、不操作设备

本地证据：

- 聚焦 JVM 测试共 `31/31` 通过：`KiyoriSettingsPagesTest 11/11`、
  `KiyoriBrowserPluginSettingsPolicyTest 2/2`、`WebSessionUserscriptUiPolicyTest 2/2`、
  `UserscriptManagementPolicyTest 7/7`、`BrowserPluginCenterFacadeTest 9/9`
- formal readiness 通过；7 份 `strings.xml` 均可解析；7 个本轮修改 Markdown 文件没有新增失效本地链接；
  旧设置 action、旧设置 helper 和旧 `6/7/4/6/6` 当前合同均为零引用
- `git diff --check` 无 whitespace error，仅报告工作树中
  `WebSessionUserscriptManager.kt` 的既有 CRLF -> LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 26s`，238 个任务中 29 executed / 209 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-04 01:04:30 +08:00`，大小 `466522199` 字节，SHA-256
  `51F3B5A558C80C4CEDA11063FBEB0E4358319D1C931476C05E9D37546A304C0F`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / minSdk 26 / targetSdk 34 /
  arm64-v8a`；Android Debug V2 签名与 `zipalign -c -P 16 -v 4` 均通过
- 未执行 APK 安装、ADB、MuMu 或真机测试；未创建提交、未推送远端

### 浏览器设置返回当前标签页插件抽屉

用户复测确认：从当前网页的浏览器菜单第四行第三个“设置”进入网页浏览器设置后，点击“插件中心”
或“脚本诊断与日志”，浏览器内部已经切换到目标插件路由，但设置子页仍覆盖在当前 Browser Home 上方，
因此无法看到当前标签页和浏览器下拉抽屉。

本轮修复计划：

1. [DONE] 追踪 Browser Home、`KiyoriShellChild.BROWSER_SETTINGS`、
   `BrowserPresentationCoordinator`、浏览器插件路由和 `ACTION_OPEN_KIYORI_BROWSER` 的完整链路
2. [DONE] 浏览器设置中的插件中心、userscript 工作台和脚本详情入口先关闭当前
   Browser Settings child，再委托唯一 `BrowserPresentationCoordinator` 打开对应浏览器插件路由
3. [DONE] 锁定关闭 Browser Settings child 会恢复 Browser Home，同时保留当前活动标签、
   `browserReturnTarget` 和 `browserExitPresentation`
4. [DONE] 同步浏览器设置和插件平台文档，执行 Shell/插件定向测试、formal readiness、
   Markdown 与差异检查
5. [DONE] 串行构建并核验新的 Debug APK；设备上的原网页、抽屉动画和系统 Back 复测继续保持
   `verification_pending`

修复不创建第二 Browser Runtime、第二标签状态或设置来源字段，不导航、刷新或重建当前网页，也不增加
没有明确所有权的回退路径。

当前验证证据：

- `KiyoriSettingsPagesTest 12/12`、`KiyoriShellStateTest 50/50`、
  `WebSessionBrowserBackPolicyTest 2/2`、`KiyoriBrowserPluginSettingsPolicyTest 2/2`，
  合计 `66/66` 通过，零失败、零错误、零跳过
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
  通过
- Markdown 检查器单元测试 `7/7` 通过；当前工作树 7 个修改 Markdown 文件的本地链接检查为
  `0` 个缺失目标
- `git diff --check` 无 whitespace error，仅报告工作树中
  `WebSessionUserscriptManager.kt` 的既有 CRLF -> LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 55s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-04 01:32:17 +08:00`，大小 `466522199` 字节，SHA-256
  `7A42A35A16E6E1F8C95A95CF816A2CCD4D20581B4AA469E6D5767F91F6444A93`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / compileSdk 37 / minSdk 26 /
  targetSdk 34 / arm64-v8a`；唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`，Android Debug V2 单签名与
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK，未操作 ADB、MuMu 或真机；当前网页、抽屉动画、拖动和系统 Back 现场复测保持
  `verification_pending`

## 2026-08-03 播放器倍速、小窗与视频嗅探策略优化

状态：本地实现、自动验证与 Debug APK 完成，目标设备验收待完成。继续复用唯一
`PlayerSettingsStore`、`PlayerSession`、
`WebSessionBrowserSettingsStore`、每个 `WebSession` 的 `BrowserMediaCandidate` 和
`BrowserDownloadManager`，不创建第二播放器、第二候选列表或第二设置 owner。

本轮实施与验收顺序：

1. [DONE] 把播放器倍速固定选项扩展为 `0.25x..3.0x` 的 `0.25x` 步进，弹窗保持从高到低且压缩
   选项垂直间距；增加 `0.00x..3.00x`、最多两位小数的自定义倍速输入，其中 `0.00x` 明确执行暂停
2. [DONE] 把未持久化过的“双击手势”默认值改为“暂停/播放”；长按加速按当前倍速分段到
   `1.0x / 2.0x / 3.0x`，松手或取消后恢复按下前速度且不写入倍速记忆
3. [DONE] 让浏览器小窗继续按比例投影横屏播放器的顶部、侧边和底部控制结构，并与全屏播放器共用
   蓝紫进度色、轨道颜色、渐变遮罩和倍速选择控件
4. [DONE] 消除自动小窗对整个候选列表重复等待 `1.2s` 的路径：以最终候选 ID 为稳定键，只保留短暂
   稳定等待；候选 DOM 观察补充尺寸变化事件，不主动请求媒体或改写链接
5. [DONE] 从 DOM 尺寸及原始 URL 中识别 `2160P / 1440P / 1080P / 720P / 480P` 等画质线索；
   保持推荐资格优先，在推荐组内按画质从高到低排序，并让自动小窗选择达到门槛的最高画质候选
6. [DONE] 在浏览器“音视频嗅探”设置组增加“自动悬浮最小时长”：默认 `1 分钟`，提供
   `30 秒 / 1 / 3 / 5 / 10 / 30 / 60 分钟` 和 `1..86400 秒`自定义值；未知时长的非直播候选
   在获得可验证时长前不自动弹出小窗
7. [DONE] 同步 `CONTEXT.md`、`README.md`、播放器和嗅探阶段文档，补充纯策略与设置页面测试，
   再运行 formal readiness、`git diff --check`、相关 JVM 测试以及规定的 Debug APK 构建与制品核验
8. [ ] 在目标设备验收倍速弹窗输入法与滚动、双击/长按手势、小窗出现时延、画质排序、默认最高
   画质、阈值边界、悬浮/全屏往返和进度条视觉

本地证据：

- 聚焦 JVM 测试共 `59/59` 通过：`PlayerPolicyTest 24/24`、
  `BrowserMediaCandidatePolicyTest 19/19`、`KiyoriSettingsPagesTest 11/11`、
  `KiyoriBrowserPluginSettingsPolicyTest 2/2`、`PlayerControlsPolicyTest 3/3`
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
  通过；`git diff --check` 无 whitespace error，仅报告既有 CRLF -> LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 25s`，238 个任务中 28 executed / 210 up-to-date；
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，生成时间
  `2026-08-03 23:59:51 +08:00`，大小 `465086902` 字节，SHA-256
  `1D7FB126D229E443E8362551189E2AD017DD827BCB325E394236C6528B75BB58`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / minSdk 26 / targetSdk 34`；
  Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 均通过
- 未执行 APK 安装、ADB、MuMu 或真机测试；未创建提交、未推送远端

## 2026-08-03 浏览器主页确认与多窗口回退修复

状态：本地实现与自动验证完成，设备交互待验证。实现位于唯一
`StandardBrowserSessionTools` / `WebSession` Browser Runtime 内，没有创建第二套导航、
窗口或 WebView 状态。

本轮实施与验收顺序：

1. “网页主页自定义”中的“恢复为空白页”先显示确认弹窗；取消不写设置，确认后才把唯一
   `WebSessionBrowserSettingsStore.homeUrl` 设为 `about:blank`
2. 在每个现有 `WebSession` 内记录该窗口自己的主页根导航状态，覆盖直接从软件首页搜索、
   书签、用户脚本、AI 或网页弹窗创建且没有主页历史的窗口
3. 系统 Back、浏览器顶栏返回和底栏返回当时统一进入 `WebSessionBrowserHost` 的逐级状态机：
   先关闭临时界面，再后退当前窗口的有效网页历史；历史耗尽且尚未位于当前自定义主页时，
   导航到主页并建立新的历史根；只有已经位于主页根时才按入口关闭或最小化 Browser Home。
   其中顶栏接线已由 2026-08-08 的当前任务替代，现只保留系统 Back、底栏返回和 AI 浏览器后退
4. 切换或关闭窗口后只读取新活动 `WebSession` 自己的主页根与历史，不跨窗口复用返回状态；
   普通与无痕 Profile、窗口顺序、presentation、下载、用户脚本和 AI 共用合同保持不变
5. 增加主页 URL 等价、重定向后主页根、直接目标窗口、历史优先、主页后退出和底栏可用性测试，
   再运行正式开发门禁、差异检查、相关 JVM 测试及规定的 Debug APK 构建与制品核验

本地证据：相关 JVM 测试共 `94` 项通过，主代码 Kotlin 编译、formal readiness、工作树
Markdown 链接、`git diff --check` 与新增代码禁用兜底扫描通过。规定的
`:app:assembleDebug --no-daemon --console=plain` 构建成功，并通过唯一桌面入口与 Player
runtime packaging 校验。Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
`465765311` 字节，SHA-256
`7157434F8938FCB4BD4608098948086C580CC1FDCB5B8910CD7490EF1DF3A732`；
包名/版本为 `com.kiyori / 45 / 0.1.0`，V2 Debug 签名、单 signer 与
`zipalign -c -P 16 4` 通过。

真机上的系统 Back、顶栏/底栏按钮、普通/无痕多窗口切换、主页重定向和旋转恢复仍需设备验收，
本地实现与 Debug APK 不能替代这些交互证据。

## 2026-07-31 Kiyori 项目架构与 Operit 命名重构方案 v3

方案 v3 已于 2026-07-31 获得实施授权。当前方案让 Kiyori 产品代码与
Operit AI 兼容代码形成两个明确包根，先建立依赖边界和数据保护门禁，再按小里程碑迁移。
v3 进一步把备份范围纠正为当前仓库和本机开发状态，不涉及手机、模拟器或 ADB；同时增加
M-01 精确影响清单，并在第一个应用源码里程碑前增加通用架构/稳定合同门禁基线。

方案入口：

- [Kiyori 项目架构与 Operit 命名重构](kiyori_architecture_refactor/index.md)
- [当前架构与命名分类账](kiyori_architecture_refactor/1_current_architecture_and_naming_ledger.md)
- [目标包结构与依赖规则](kiyori_architecture_refactor/2_target_package_architecture.md)
- [分阶段迁移顺序](kiyori_architecture_refactor/3_migration_sequence.md)
- [Operit AI 上游同步策略](kiyori_architecture_refactor/4_upstream_sync_strategy.md)
- [开发数据、备份与回滚](kiyori_architecture_refactor/5_data_backup_and_rollback.md)
- [验证矩阵与批准门禁](kiyori_architecture_refactor/6_validation_and_approval_gate.md)
- [源码所有权与文件迁移矩阵](kiyori_architecture_refactor/7_file_ownership_and_migration_matrix.md)
- [兼容合同与稳定标识清单](kiyori_architecture_refactor/8_compatibility_contract_inventory.md)
- [工作区、基线与备份作战手册](kiyori_architecture_refactor/9_workspace_preflight_and_backup_runbook.md)
- [里程碑执行模板与首批规格](kiyori_architecture_refactor/10_milestone_execution_template.md)
- [风险登记与停止条件](kiyori_architecture_refactor/11_risk_register_and_stop_conditions.md)
- [最终批准与实施就绪清单](kiyori_architecture_refactor/12_approval_and_implementation_readiness.md)
- [架构门禁与机器可读所有权规范](kiyori_architecture_refactor/13_architecture_guard_specification.md)
- [验证命令目录](kiyori_architecture_refactor/14_validation_command_catalog.md)
- [M-01 Application 原包改名精确影响清单](kiyori_architecture_refactor/15_m01_application_rename_exact_manifest.md)
- [M-00 权威文档同步精确清单](kiyori_architecture_refactor/16_m00_document_authority_update_manifest.md)
- [M-02 Application 全局访问平台化精确清单](kiyori_architecture_refactor/17_m02_application_platform_access_manifest.md)
- [M-03 KiyoriApplication 包迁移精确清单](kiyori_architecture_refactor/18_m03_application_package_move_manifest.md)
- [M-04 根组合与 Shell 精确实施清单](kiyori_architecture_refactor/19_m04_root_composition_and_shell_manifest.md)
- [M-05 Design 与 Platform 精确实施清单](kiyori_architecture_refactor/20_m05_design_and_platform_manifest.md)
- [Stage 4 前质量债务与开发就绪精确清单](kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md)

当前状态：`M-05 complete / M-05E sealed / QD-01..QD-07 complete /
local validation complete / delivery audit in progress`。current-only Lint 已从
`27 errors / 287 warnings / 3 hints` 收敛为 0；依赖、资源、平台、WebKit、TLS、
receiver、权限、accessibility、native asset、PTY 与 Kotlin 编译器警报均按
[Stage 4 前质量债务与开发就绪精确清单](kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md)
分批处理。最终完整 baseline 为 `retained=5606 / stale=92 / current-only=0`，SHA-256
`BEC89B4BF52DE60D7E839336080878B03DB154A7DC072D3C1875BDF0DD1748D0`；未新增
suppress、lint disable 或 baseline 条目。552 条项目 Kotlin 编译器警报的实现清理及本地
最终验证已完成：强制完整 Kotlin 编译项目 warning 为 0，Python `174/174`、architecture
`37/37`、JVM `140 suites / 831 tests`、formal readiness、Markdown、diff 与正式 Lint
均通过。RenderX 依赖错误发布的第二个 `LatexView` launcher 已在 Manifest merge 边界
精确移除，并新增最终合并 Manifest 唯一 launcher 门禁。Debug APK 为
`463718677` bytes，SHA-256
`768CAE74E27352DEE038AF0C4C9F8EE5E3C2C97B017B0F3C2BCD3E92080AEF6D`；
`aapt` 只报告唯一 `MainActivity` 桌面入口。当前进入 Git/远端交付封板。
正式实施严格按
“本地备份与安全点 -> M-00 -> G-00 -> M-01 -> M-02 -> M-03 -> M-04”串行推进；
M-00、G-00 和 M-01 的门禁、测试和 Debug APK 验证均已通过；
后续 G-00 加固已关闭 fresh-clone、Java static import、完全限定项目引用和重复
Manifest component 检查缺口，并扩展 Manifest、持久化、AIDL、Room 与 ObjectBox
合同覆盖；M-02 已把 13 个 concrete Application 消费者收口到四个窄 platform 合同，
`ARCH017` 同时阻止残留 concrete dependency、第二 process owner 和 Service Locator。
完整 Python 为 `107/107`，完整 JVM 为 `773/773`，Debug APK、v2 签名、16 KB 对齐与 player
native packaging 均通过。

M-03 已把唯一 Application 移到 `com.kiyori.app.KiyoriApplication`，Manifest 改用绝对类名，
并只为原同包 `ActivityLifecycleManager` 增加必需显式 import。迁移后的 43 个 Operit 装配
import 由一个文件精确、M-07 到期的 ARCH004 exception 暂存，并由 ARCH018、import snapshot
与规范化源码哈希锁死；Python `109/109`、JVM `773/773`、Debug APK、v2 签名、16 KB 对齐和
player native packaging 均通过。下一独立里程碑为 M-04 根 Composable/App Shell 拆分。
namespace、数据、协议、UI 行为、功能和 terminal 未改变；未使用设备，未提交或推送。

M-04A1 已完成 Operit host CompositionLocal 合同拆分。M-04A2 已完成
`OperitApp -> com.kiyori.app.KiyoriApp` 的纯移动与改名；M-04B1 已把 8 个 AI
route/stack policy 符号移入 `com.kiyori.integration.operit.navigation`。M-04B2 已把
Browser exit presentation enum 移入 `com.kiyori.capability.browser.presentation`，并把
唯一 `KiyoriShellState` / Back owner 移到 `com.kiyori.app.shell`，旧 state 路径已删除。
root 的过渡 Operit import 从 45 降到 35；ARCH022/ARCH023 锁定两份源码 SHA、唯一 owner、
三处 capability 消费、9 个 root Shell import 与四处 Operit 文件级过渡桥。完整 Python
`117/117`、JVM `773/773`、formal readiness、Debug APK 与制品审计均通过；APK SHA-256
为 `04C7BEE47A784B8A3C142BD0B614AB76A2EAE013CCEDE78E89D8FC6EDDA4C055`。
M-04B3 已把唯一 `KiyoriAppShell` host 纯移动到 `com.kiyori.app.shell`；548 行 host 的
规范化源码哈希与移动前完全一致，17 个 Operit import、12 个唯一 host/helper owner、
唯一 root call 和测试接线由 ARCH024 锁定。完整 Python `119/119`、JVM `773/773`、
Debug APK 与制品审计均通过；APK SHA-256 为
`F7FAE589513E46A4EF4D955293D3DE2279C810DC61B117D2B2CFB61FED36B7D7`。
M-04B4 已把唯一 Modal AI Drawer host 纯移动到 `com.kiyori.app.shell`；642 行 Drawer
规范化源码与移动前完全一致，13 个 Operit import、唯一 Drawer/tone owner、App Shell
唯一挂载和测试接线由 ARCH025 锁定。完整 Python `121/121`、JVM `773/773` 与 Debug APK
审计通过；APK SHA-256 为
`00E0DCDF8FEB80BA1382DBF4ADA509CAC24F17105A738F60F677BB9AD9F0FCD5`。
M-04B5 已把 primary destination visual、root dispatch、bottom navigation 和三条动画
策略提取到 `com.kiyori.app.shell.KiyoriPrimaryNavigation`；旧混合文件从 1234 行缩减为
912 行，Software Home 与 Browser 搜索未移动。ARCH026、完整 Python `123/123`、
JVM `773/773` 与 Debug APK 审计通过；APK SHA-256 为
`75C53A4031FE0DFBA3A0A81008A66945B69B55E5B1BB439BBFC327324DF23E37`。
M-04B6 已把 Software Home 页面、四个策略 enum、19 个固定视觉参数、四个纯 resolver、
天气/Browser 窗口动作和 Search/AI frame helper 提取到
`com.kiyori.app.shell.KiyoriSoftwareHome`；旧 `KiyoriShellPages.kt` 从 912 行缩减为
161 行，只剩 Full-Screen Browser Search、request model 与 resolver。ARCH027、完整
Python `125/125`、JVM `773/773` 与 Debug APK 审计通过；APK SHA-256 为
`E4DDCF661685E0A49A0010E2BF27110312403C477503E647FBFE08391288A040`。
M-04B7 已把 residual Full-Screen Browser Search 页面、request model 与 resolver 提取到
`com.kiyori.app.shell.KiyoriBrowserSearch`，并删除旧 `KiyoriShellPages.kt`。ARCH028、
完整 Python `127/127`、JVM `773/773` 与受影响 Browser/Shell/Activity JVM 测试通过；
Debug APK 审计通过，SHA-256 为
`F104A5778C17FA518350FA22420E1073DF1A5FDC0540BF0119C7D3CDD8BBD0BF`。
M-04C 已把 `AppRouteCatalog`、唯一 PackageManager-backed navigation revision、ToolPkg
runtime listener、AppRouterGateway/AppRouteDiscoveryGateway lifecycle 和两条 route-root
helper 收口到 `com.kiyori.integration.operit.navigation`；旧 catalog 路径已删除，
KiyoriApp 的 Operit imports 从 32 降到 25。ARCH029、Python `129/129`、JVM `775/775`
和 Debug APK 审计通过；APK SHA-256 为
`71A860FF9957FCAAB290669449159F8269F145C6C904FB94E8E2E3E0942FC402`。M-04D1 已把
MainActivity 内原先分散的 shared files/text、Browser URL/request ID、GitHub OAuth URI、
shortcut、route、Shell destination 与 current-main-navigation 状态收口到唯一
`com.kiyori.app.startup.KiyoriMainPendingRequests`。Activity 仍负责 Intent 解析、时间戳、
下载、OAuth、分享和 Compose host 副作用；ARCH030、Python `131/131` 与 3 条新 JVM
合同测试已通过。完整 JVM `778/778`、formal readiness 与 Debug APK 审计也已通过；
APK SHA-256 为
`3C0FA7301EF846C90D24CCCB0A4A0492D3A2E7374F9B24182640B17A89423F96`。当前进入
M-04D2，提取纯 Intent decoder，不移动 Android 副作用或创建第二 request store。
M-04D2 已新增 `KiyoriMainIntentDecoder`、密封 command 和稳定常量合同；Activity 不再
直接读取 long/string/parcelable/data payload，但仍在原顺序执行 player、download、
Shell、settings、route、OAuth、Browser 与 share 副作用。ARCH031 与 Python `133/133`
已通过。完整 JVM `783/783`、formal readiness 与 Debug APK 审计也已通过；APK
SHA-256 为
`E4F53F4646191A935453252D8B0ACF0093F46F1F609648B437462771018B411F`。当前进入
M-04D3。M-04D3 已把 sustained performance、API 30+ display mode、API 23–29 refresh
rate 与 hardware acceleration 移到无状态 `KiyoriMainDisplayCoordinator`；MainActivity
只保留一次 configure 调用。ARCH032、Python `135/135` 与 3 条刷新率策略测试已通过，
完整 JVM `786/786`、formal readiness 与 Debug APK 审计也已通过；APK SHA-256 为
`53038237D9B31087CBE6BB1172B7943E988F62809366AA3C2798C716EE848E16`。当前进入
M-04D4。M-04D4 已把 pending external files/text 转交、日志、Toast 和成功/失败清理移到
生命周期绑定的 `KiyoriMainSharedContentCoordinator`；既有 `SharedFileHandler` StateFlow
owner 未移动或复制。ARCH033、Python `137/137`、3 条纯转交决策测试、完整 JVM
`789/789`、formal readiness、lint baseline normalization、Markdown links、
`git diff --check` 与 Debug APK 封板均通过；APK 大小为 `470045143` bytes，SHA-256 为
`AE11A63373B55A7B7A65F59C07C00886ECFEF1D23083CED92CC8D96C498DFC45`。当前进入
M-04D5。M-04D5 已把最近任务可见性恢复及其 API/AI foreground-runtime 判定移到无状态
`KiyoriMainTaskVisibilityCoordinator`；MainActivity 只保留原两个调用时机，不再直接依赖
`ActivityManager`、`AIForegroundService` 或 `setExcludeFromRecents`。ARCH034 失败优先
正反向测试、3 条纯判定 JVM 测试、完整 Python `139/139`、完整 JVM `792/792`、完整
architecture、formal readiness、lint、Markdown、diff 与 Debug APK 封板均通过；APK
大小为 `470045623` bytes，SHA-256 为
`F246315DBCCF89522C7AB2C14EA96E67234A79D8F76B84A05F5352409AE743BB`。下一独立切片为
M-04D6。M-04D6 已把 last orientation、dialog visibility、纯状态转换与方向确认对话框
移到唯一 `KiyoriMainOrientationCoordinator`；Activity 仍按原顺序先隐藏 Plugin Loading，
再分发 configuration change，并继续唯一执行 `recreate()`。ARCH035 正反向测试、4 条
方向行为 JVM 测试、完整 architecture、Python `141/141`、完整 JVM `796/796`、formal
readiness、lint、Markdown、diff 与 Debug APK 封板均通过；APK 大小为 `470049571`
bytes，SHA-256 为
`8E8BA68316543E9685743C3C0D893CC74484E3C2243B0DA71F103195C4209149`。当前进入
M-04D7。M-04D7 已把启动阶段 `POST_NOTIFICATIONS` launcher、API 33 判定、rationale、
request 与 result Toast 移到唯一 `KiyoriMainNotificationPermissionCoordinator`；该
coordinator 作为 Activity 直接字段构造，保持 `registerForActivityResult` 在 onCreate 前
注册。ARCH036 正反向测试、4 条纯决策 JVM 测试、完整 architecture、Python `144/144`、
完整 JVM `800/800`、formal readiness、lint、Markdown、diff 与 Debug APK 封板均通过；
APK 大小为 `470051071` bytes，SHA-256 为
`555D0222EC1DDF9B58C69E1A6EF4CE931CADD4BDCC32C1BC157B9A47625E0D0B`。封板时重新生成
完整 lint 结果并通过结构化交集删除 `51` 条失效历史记录，保留 `5792` 条且不吸收
`328` 条 current-only 问题；ARCH018 同步锁定当前 Application lint 路径为旧 0、新 5，
并新增防止失效第 6 条回流的反向测试。M-04D8 已把 agreement / permission guide /
content 三态决策、唯一 `showPermissionGuide` UI 投影与页面分发迁入
`KiyoriMainStartupGateCoordinator`；`AgreementPreferences` 与
`AndroidPermissionPreferences` 继续唯一持有持久事实，Activity 继续执行 300 ms 延迟、
插件加载、lifecycle 与内容 host 副作用。ARCH037 正反向夹具 2/2、定向 JVM 7/7、完整
architecture、Python `146/146`、完整 JVM `807/807`、formal readiness、lint baseline
normalization、255 个 working-tree Markdown 文件与 `git diff --check` 均通过；Debug APK
大小为 `470053617` bytes，SHA-256 为
`1F5835C63B6946182E3F004EF5EC75CC930E9C8F54A380C67237E9587600DDDF`，包名、版本、
Application、稳定 launcher、多进程、native packaging、v2 Debug 签名与 16 KB 对齐均保持。
主机文件系统 mtime 报告未来值 `2026-08-02 00:53:05 +08:00`，与权威任务日期
`2026-08-01` 不一致，因此只作为本机时钟异常记录，不作为里程碑完成日期。当前进入
M-04D9。该切片已新增一次性 content request projection 与唯一
`KiyoriMainContentHost`，按原顺序执行 pending shared-content 交接、
`LocalPluginLoadingState` provider 与唯一 `KiyoriApp` 挂载；未复制 pending/plugin 状态，
也未移动 `setContent`、主题、startup gate、插件启动、方向对话框或 lifecycle。ARCH020、
ARCH030、ARCH033 已同步锁定新的 content-host owner，ARCH019 至 ARCH038、完整 Python
`148/148`、完整 JVM `134 suites / 810/810`、formal readiness、lint baseline 结构化交集、
255 个 working-tree Markdown 文件、`git diff --check` 与 Debug APK 封板均通过。APK 大小
为 `470058476` bytes，SHA-256 为
`A568EBDF498CD531E3E799BBD6A763E36269E6C1008A524F45B8E5B510ABE26F`；包名、版本、
Application、稳定 launcher、多进程、player native packaging、v2 Debug 签名与 16 KB
对齐均保持。严格 `:app:lintDebug` 在 D9 时暴露既有未基线化债务 `31 errors /
289 warnings / 8 hints`；D9 source/test current-only 为 0，MainActivity 仅有既有
`AppBundleLocaleChanges` warning，未把问题吸收进 baseline。

M-04E 已完成。E1 为稳定 Manifest launcher
`com.ai.assistance.operit.ui.main.MainActivity` 建立精确 compatibility owner，删除唯一
到期 ARCH001 文件例外，并由 ARCH039 锁定精确 ownership 优先级、稳定
package/FQCN/MAIN/LAUNCHER、32 条项目 import、`com.kiyori.feature` 零依赖和唯一
content-host 接线；精确文件 record 覆盖宽泛目录 owner，重叠宽泛 glob 仍必须失败。
E2 只修复 M-04 owner 内有明确行为等价依据的 API/Compose lint：primitive Long state、
API 30/33 边界和 Compose resource 读取。严格 lint 降至 `27 errors / 287 warnings /
2 hints`，M-04 owner 只剩已明确转入 M-05/发布策略的两条 warning，M-04 errors/hints
均为 0。最终结构化交集为 `5792 retained / 0 stale / 316 current-only`，baseline
SHA-256 保持
`A71AB39486275083A30C1DB51F0533162E2DB9054BE8D00BAD1EB2878FE8CE4C`。

M-04 总封板已通过 ARCH019 至 ARCH039、完整 Python `152/152`、完整 JVM
`134 suites / 810/810`、formal readiness、255 个 working-tree Markdown 文件、
`git diff --check` 和 Debug APK 制品审计。APK 为 `468983182` bytes，SHA-256 为
`81F6BA9436031AB20CBFB30C23F111A927FF0913D422A4BC602BBDD1CDEC38D5`；
package/version/label、SDK、Application、稳定 launcher、`:crash/:repair/:player`、
arm64 53 个 native、零重复 basename、player packaging、Debug v2 签名与 16 KB 对齐
全部保持。当前进入 M-05 design/theme/platform：先冻结颜色、Typography、Shapes、
system-bar/edge-to-edge、日志、生命周期、权限和路径合同，再建立失败优先门禁，禁止整体
搬迁 `util`、复制偏好/权限状态或创建万能 platform registry。后续继续按 Browser、Player、
Settings、Files、Mini App、Backup/Recovery、Download settings 和 Android system
surfaces 顺序推进。

M-05 只读 owner 审计已完成并形成
[精确实施清单](kiyori_architecture_refactor/20_m05_design_and_platform_manifest.md)。
旧 theme 目录混合纯 Kiyori design、偏好读取、system-bar、AI 字体和 glass，禁止整体移动。
M-05A1 已建立三个纯 `com.kiyori.design.theme` owner，迁移固定 ColorScheme、Browser theme
与 Settings theme，删除两个旧 theme 文件，并让旧 `ThemeColorSchemeResolver` 只保留偏好
决策 adapter。ARCH040 缺失源文件的 failure-first 证据、正反向 fixture、初始 11 个生产消费者
均已实现；2026-08-09 浏览器文字大小与网站密码管理两个设置子页继续复用同一设置主题，
精确 consumer 合同同步增至 13 个。
新旧测试分工与 ownership 许可均已实现；ARCH024 的 App Shell hash 和完整项目 import
snapshot 已同步到批准后的 design import。封板验证通过完整 architecture、Python
`154/154`、JVM `135 suites / 810 tests`、formal readiness、lint 交集
`5792 retained / 0 stale / 316 current-only`、A1 影响文件 `0` lint issues、256 个
working-tree Markdown、`git diff --check` 与规定的 Debug 构建。最终构建为
`233 actionable tasks / 28 executed / 205 up-to-date`，APK 为 `471291682` bytes，
SHA-256 `B8CD99D49D3F93745282C4E04F7FD7C158A875BE8897FF17161EC4D2C7F93646`；
package/version/label、SDK、Application、稳定 launcher、多进程、arm64 53 native、零重复
basename、Debug v2 与 16 KB 对齐全部保持。M-05A1 已封板。M-05A2 已把纯 semantic
enum/data/color resolver 与 Compose `MaterialTheme` adapter 拆为两个
`com.kiyori.design.theme` owner，删除旧 owner 且不保留 facade/typealias/fallback；55 个生产
消费者、4 个测试消费者、102 条 import 与一处完全限定引用已精确迁移。ARCH025/026/027/
040/041、完整 architecture、Python `156/156`、JVM `135 suites / 810 tests`、
formal/fresh-clone readiness 均通过。新鲜 full lint 仍报告仓库既有 `27 errors / 287 warnings /
2 hints`，但两个新 design 文件为 0 命中，唯一影响路径命中来自
`WebSessionUserscriptSheet.kt` 未改正文；baseline 交集保持
`5792 retained / 0 stale / 316 current-only`。规定 Debug 构建为
`233 actionable tasks / 28 executed / 205 up-to-date`；APK 为 `471292358` bytes，
SHA-256 `60D613A5F9BFBD019296E21E05547DF16B0789FCDE73FF2699AFBAC415A8BBC0`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、零重复 basename、Debug v2 和
16 KB 对齐全部保持。M-05A2 已封板。M-05A3 已把旧 `Theme.kt` 拆为纯
`com.kiyori.design.theme.KiyoriTheme/KiyoriTypography`、唯一
`com.kiyori.app.theme.KiyoriTheme` 偏好/字体/Glass host 和唯一
`com.kiyori.platform.window.KiyoriApplicationSystemBars`；旧 `Theme.kt`、`OperitTheme`、
`Theme.Operit` 与旧 root-theme test 已删除，不保留 facade、typealias、fallback 或第二 owner。
MainActivity 与桌面 Widget 配置 Activity 复用同一 app host，6 个 style 声明和 Manifest
6 个引用均为 `Theme.Kiyori`；Type 只保留配置字体、文件 I/O、日志和 AI 局部字体适配，
Liquid/Water Glass 算法与 PlayerActivity fullscreen system-bar 字节不变。ARCH040/041/042、
完整 architecture `phase=m03`、Python `158/158`、JVM `134 suites / 810 tests`、
formal/fresh-clone readiness 与 `git diff --check` 通过。fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints`，四个新 owner 为 0 命中，baseline 交集保持
`5792 retained / 0 stale / 316 current-only`。规定 Debug 构建为
`233 actionable tasks / 28 executed / 205 up-to-date`；APK 为 `477957302` bytes，
SHA-256 `9373518AEB8FA8BD2C02DCFDE5741833653A2D76AFE270D2C27F4D2BF88C0074`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、零重复 basename、Debug v2 和
16 KB 对齐全部保持。M-05A3 已封板。M-05B 也已封板：唯一
`KiyoriLogger/KiyoriLogTextFormatter` owner、旧 `AppLogger` 无状态 facade、8 个 Kiyori
app consumer、Provider/Application 目录绑定、旧 static-mock 和 ARCH043 均已闭环。完整
architecture `phase=m03`、Python `160/160`、JVM `135 suites / 813 tests`、
formal/fresh-clone、Markdown 和 diff 通过。full lint 保持既有
`27 errors / 287 warnings / 2 hints`；新 owner 为 0 命中，baseline 只删除旧
`AppLogger.kt` 已失效的 `StaticFieldLeak`，交集为 `5791/0/316`，SHA-256 为
`AEB75AEE42CF985AC5A8F6A33DE88C04D1B0C50580E952E32542FE8403AAB8C0`。规定 Debug
构建为 `233 actionable tasks / 24 executed / 209 up-to-date`；APK 为 `477957302`
bytes，SHA-256 `3A9BCBC711DB1FB735C3828AA1751F80B347C8C1FC7E34E783F16FFD96D631A1`，
身份、SDK、Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、
零重复 basename、Debug v2 与 16 KB 对齐通过。M-05C 已新增唯一
`KiyoriActivityLifecycle` callback/facts owner 与
`OperitActivityLifecycleIntegration` side-effect owner，旧 `ActivityLifecycleManager`
保留完整 JVM ABI 并成为无状态 facade；13 个旧 FQCN consumer、Application 初始化位置、
plugin/AI/Player/窗口行为保持。ARCH044 failure-first、正反向 fixture、真实与完整
architecture `phase=m03`、Python `162/162`、JVM `136 suites / 817 tests`、
formal/fresh-clone、Markdown 和 diff 通过。fresh full lint 保持既有
`27 errors / 287 warnings / 2 hints`，M-05C 四条路径为 0 命中。规定 Debug 构建稳定为
`233 actionable / 24 executed / 209 up-to-date`；APK 为 `477957302` bytes，SHA-256
`3792DD58C87D1DDD4F977BB8CD4B4407458EB911EC17CB0CB48CB8876184D2D3`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与
16 KB 对齐保持。M-05C 已封板。M-05D 已完成唯一
`KiyoriNotificationPermissionCapability`、无状态 Operit resource bridge 与旧 app
coordinator projection 实现；旧 coordinator 的一参数构造与 `checkAndRequest()` javap
逐项保持，MainActivity 早注册/单调用、6 条日志、2 个 Toast、Manifest 声明和两个非启动
通知检查保持。ARCH045 failure-first、ARCH036/045 正反向 fixture、真实/完整 architecture、
生产编译、完整 Python `164/164`、完整 JVM `136 suites / 817 tests`、formal/fresh-clone
readiness、Markdown、diff、lint baseline normalization、敏感签名与规定 Debug 构建均通过。
fresh full lint 保持既有 `27 errors / 287 warnings / 2 hints`，M-05D 四条路径为 0 命中。
APK 为 `477957302` bytes，SHA-256
`BB370BC2602880CA4DCE488F67DA4AB9F105C1CFF07A0E4D2FF31A3D3CC38B34`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与
16 KB 对齐保持。M-05D 已封板。M-05E 已建立唯一
`com.kiyori.platform.storage.KiyoriPaths` 与纯 `KiyoriBackupPaths`，把旧
`OperitPaths` / `OperitBackupDirs` 收口为完整 ABI 兼容 facade，并按精确边界迁移
`9 / 7 / 37 / 0` consumer 集合。ARCH046 failure-first、正反向 fixture、最终完整
architecture、Python `166/166`、JVM `137 suites / 822 tests`、formal/fresh-clone、
Markdown、diff、敏感审计与 fresh lint 通过；lint 保持既有
`27 errors / 287 warnings / 2 hints`。规定 Debug 构建和 APK 审计通过，APK 为
`477957302` bytes，SHA-256
`E6A5E78CFB4441399DC36D83583BF6F441441E3736A85A17759695D73ACF790C`，身份、SDK、
Application、稳定 launcher、多进程、arm64 53 native、10 个播放器目标库、Debug v2 与
16 KB 对齐保持。M-05E 已封板，M-05 design/theme/platform 阶段完成。设备/UI、
多进程文件写入、真实日志导出、真实 lifecycle side effect、Android 13+ 权限框/通知到达、
路径读写与备份恢复验收继续为 `verification_pending`。

## 2026-08-02 Stage 4 前质量债务与开发就绪收口

M-02 至 M-05 已形成可重现 checkpoint
`6b6493a0bfd12072116e45fb733d551fad13e32b`，该提交通过 fresh clone 和 formal
readiness。阶段 4 Browser 产品域开始前，先按
[质量债务与开发就绪精确清单](kiyori_architecture_refactor/21_quality_debt_and_stage4_readiness_manifest.md)
收口初始 `27 errors / 287 warnings / 3 hints`。QD-01 至 QD-07 的实现清理已完成；
最终完整 baseline 保留 `5606` 条历史记录，结构化交集
`stale=92 / current-only=0`，高风险正确性和安全项已完成根因修复或进入独立设备/发布验收。

执行顺序与当前状态：

1. [DONE] current-only correctness、KTX/SDK/Compose、资源/plurals 与平台合同
2. [DONE] 依赖升级、第三方不安全字节码净化与 compile SDK/Kotlin 对齐
3. [DONE] 552 条项目 Kotlin 编译器警报实现清理
4. [DONE] TLS/WebView/receiver/权限/accessibility/native/PTY 高风险债务
5. [DONE] baseline 交集剪枝：`5606 / 92 / 0`
6. [DONE] 强制完整编译、全量测试、架构/正式门禁、Debug APK 与敏感审计
7. [IN PROGRESS] 审计提交范围并按授权提交、推送和核对远端 ref

禁止通过 suppress、扩大 baseline、关闭 dependency lint、fallback 或行为不明的批量删除
取得表面全绿。设备和 Release 验收继续独立记录。

## 2026-07-31 插件中心信息架构与日志交互优化

本轮继续复用同一个 Browser Plugin Center、`UserscriptRepository` 和
`WebSessionUserscriptManager`，修正插件中心重复展示和日志不可操作的问题。

实施与验收门禁：

1. [DONE] 审计本页、油猴脚本、插件库、日志和浏览器设置的入口与状态所有权
2. [DONE] 插件中心“本页”改为 provider-only；油猴脚本条目只在油猴脚本工作区显示
3. [DONE] 插件库从独立页签迁移到右上角加号，保留 Greasy Fork、ScriptCat、OpenUserJS、
   Userscript.Zone 和 GitHub 五个已核实 HTTPS 来源
4. [DONE] 日志支持单击详情与复制、长按直接复制、复制全部和导出全部；完整保留日志提升到 200 条
5. [DONE] 设置页拆分插件中心、油猴脚本管理、权限与网站范围、当前页诊断和脚本日志入口；
   权限行直接打开对应脚本详情
6. [DONE] 定向测试 `244/244` 通过，零失败、零错误、零跳过
7. [DONE] 更新正式文档、formal readiness、最终 Debug APK 和产物核验
8. [PENDING] 目标 Android WebView 真机复测窄屏、长按、日志弹窗、复制、导出和设置路由

当前非目标：

- 不创建第二 Browser Runtime、第二 userscript 仓库或第二日志状态源
- 不把长按日志设计成重复的二级菜单；长按只作为直接复制快捷操作
- 不提交、不推送、不安装设备

## 2026-07-31 真实 userscript 异常与浏览器插件设置收口

本轮继续复用同一个 Browser Plugin Center、`UserscriptRepository` 与
`WebSessionUserscriptManager`。用户提供的 vivo Android 16 截图显示，“轻小说文库+”
`2.31.2` 已在 wenku8 阅读页进入执行阶段，实际状态是异常而不是未命中：
`GM_info.script` 读取到了 `undefined`。真实脚本源码同时证明其三组
`http*://*.wenku8.com/.net/.cc/*` 匹配规则已经生效。

实施与验收门禁：

1. [DONE] 获取 Greasy Fork 脚本 `539514` 当前真实源码、metadata、GM API 使用集合和设备错误堆栈
2. [DONE] 定位 `GM_info` 根因：bootstrap 只在显式 `@grant GM_info` 或 `@grant none` 时创建信息对象
3. [DONE] 让 `GM_info` 与 `GM.info` 对所有 userscript 提供同一个只读脚本信息对象，同时不扩大
   native host 权限
4. [DONE] 为 `http*://*.wenku8.com/.net/.cc/*` 和脚本实际 grants 增加真实样本回归测试
5. [DONE] 重构网页浏览器设置的第一组，接通“允许用户脚本”“网页插件管理”“插件权限与网站范围”
   和“当前页脚本诊断”
6. [DONE] 设置页只观察现有 userscript 状态流；总授权继续写入现有 registry，不建立第二设置 owner
7. [DONE] 权限页显示 runtime 支持状态、安装/启用数量、每个脚本的执行世界、grant、`@connect`、
   页面范围、未知权限和阻塞原因；脚本声明权限不伪装成可单项开关
8. [DONE] 更新语义文档，执行 userscript 与设置页定向测试、Kotlin 编译、formal readiness、
   `git diff --check` 和最终 Debug APK 构建核验
9. [PENDING] 目标设备安装后复测“轻小说文库+”启动、菜单、存储、资源、GM XHR 与设置入口；
   本轮未获设备操作授权，不安装 APK

本轮新增本地证据：

- userscript runtime、matcher、metadata、storage、插件中心、浏览器 Back 和设置页相关定向测试
  共 `86/86`，零失败、零错误、零跳过
- `python -B ci/script/check_formal_readiness.py --repository . --require-main` 与
  `git diff --check` 通过；差异检查仅报告工作树既有 CRLF 到 LF 提示，没有 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 51s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 21:46:40 +08:00`，大小 `494139942` 字节，SHA-256
  `2956E71105E5672D2F28F61AD341E11407CA82302FEF53467E646DC86D5948F3`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、arm64-v8a；Android Debug V2
  签名与 `zipalign -c -P 16 -v 4` 均通过

当前非目标：

- 不新增第二 Browser Runtime、userscript 仓库或浏览器设置状态源
- 不把脚本声明的 grant 改造成与真实授权模型不一致的逐项开关
- 不承诺未实测的全部第三方脚本兼容，不实现 `.kbx` 或 WebExtension runtime
- 不提交、不推送、不操作设备

## 2026-07-31 浏览器插件中心二次 UI、编辑器与 userscript 匹配诊断

本轮在既有 Browser Plugin Center、`UserscriptRepository` 与
`WebSessionUserscriptManager` 上进行第二次深度收口。目标是修复插件编辑器的系统栏遮挡和
底部操作区过高，消除把“没有当前页面状态”误报为“未命中”，并让常见 userscript 匹配、
权限与状态在界面中可解释。Kiyori 当前仍为未发布内部版本，保留此前同一工作树中的未提交改动；
不创建第二 Browser Runtime、第二 userscript 仓库或兼容回退链。

实施门禁：

1. [DONE] 只读审计编辑器根布局、系统 Insets、输入法、浏览器底栏几何和全部插件按钮回调
2. [DONE] 只读审计 userscript 匹配、页面世界/隔离世界状态回报、`@grant`、`@connect` 和
   当前“未命中”推导链
3. [DONE] 确定写码前设计：编辑器 44dp 顶栏、浏览器同款底部动作几何、输入法期间单层底部
   工具、`NO_ACTIVE_PAGE`/`MATCHED` 状态和统一匹配诊断
4. [DONE] 扩展匹配器：`http*`、端口、常见主机通配、正则 flags、`@connect` 子域和可解释
   的命中/排除规则；保持 query/fragment 语义正确
5. [DONE] 收口运行时状态：页面世界已匹配状态、活动页面 URL、无活动页面与未初始化状态，
   不再用空状态猜测 `NOT_MATCHED`
6. [DONE] 收口权限语义：所有脚本都可读取本地只读 `GM_info / GM.info`，而 `@grant none` 不暴露 native/GM 特权 API；
   安装/详情/更新预览分组展示 grant、
   connect、页面范围、执行世界和不兼容原因
7. [DONE] 重构插件中心按钮：概览添加菜单只进入已实现动作；URL 输入校验；更新/安装确认、
   详情和日志按钮状态与真实异步结果一致
8. [DONE] 优化编辑器：状态栏 Insets、44dp 顶栏、更多菜单、紧凑查找替换、浏览器同款底部
   动作栏、输入法期间符号栏单层显示和 Metadata 错误可见
9. [DONE] 修复详情页规则字段显示，分别展示 `@run-at`、`@inject-into`、`@run-in`、
   `@noframes`，并补充相关文案
10. [DONE] 增加匹配器、状态推导、权限和 UI 投影定向测试；执行 `git diff --check`、
    formal readiness 与项目要求的 Debug APK 构建核验
11. [PENDING] 目标 Android 设备上复测状态栏、输入法、窄屏按钮、真实脚本命中与权限行为；
    本轮不在未获设备操作授权时安装或操作设备

本轮最终本地证据：

- userscript、插件中心、编辑器 UI、浏览器 Back 和 runtime 相关定向测试共 `70/70`，零失败、
  零错误、零跳过；测试任务重新执行了 `:app:compileDebugKotlin`
- 七份实际 `strings.xml` 均可解析，名称集合无重复且包含本轮新增文案键；`formal readiness`
  与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务，
  32 个执行、201 个缓存命中；`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 20:39:03 +08:00`，大小 `494139942` 字节，SHA-256
  `B70F85F46C579419604E3C3870F8296D8E84466F81C2BC13488FE00930BE5324`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、arm64-v8a；Android Debug V2
  签名与 `zipalign -c -P 16 -v 4` 均通过

当前非目标：

- 不实现 `.kbx`、Chrome 扩展包直接安装、新标签页 Provider 或 WebExtension runtime
- 不把未实现的 `@run-in`、`@sandbox`、`@unwrap` 伪装为已支持
- 不提交、不推送、不部署、不覆盖或清理工作树已有改动

## 2026-07-31 浏览器油猴脚本管理闭环与全屏编辑器

本轮在同一个 Browser Plugin Center、`UserscriptRepository` 与 `WebSessionUserscriptManager`
上继续完成 Phase 1 管理闭环，不引入第二 Browser Runtime、第二脚本仓库、WebView 编辑器或回退执行链。
界面继续使用浏览器书签、下载等子抽屉的紧凑标题、搜索、标签、卡片和确认风格；源码编辑器作为浏览器
宿主内全屏原生页面打开。

实施与验收门禁：

1. [DONE] registry v2、应用私有 immutable revision、draft、staging、transaction journal 与原子迁移
2. [DONE] “本页 / 已安装 / 更新 / 日志”四个工作区、脚本搜索、紧凑权限提示与草稿恢复入口
3. [DONE] 脚本详情展示匹配规则、权限、`@connect`、依赖、源码、日志与版本历史
4. [DONE] 已安装脚本长按多选、批量启用、批量禁用与确认后批量删除
5. [DONE] 单项及批量检查更新；检查保持只读，批量应用只提交权限和来源不扩大的安全更新
6. [DONE] 风险更新在应用前展示新增权限、范围变化、来源变化和统一源码差异
7. [DONE] 浏览器内全屏 `NativeCodeEditor` 支持查找替换、撤销重做、格式化和结构化 metadata
8. [DONE] 编辑器私有草稿自动保存、语法/权限检查、差异预览、显式应用与离开决策
9. [DONE] 新建草稿、已安装脚本草稿和中断后未安装草稿均可恢复，不提前覆盖活动 revision
10. [DONE] 定向测试、七语种资源校验、正式开发门禁、差异审查和 Debug APK 构建核验
11. [PENDING] 目标设备验收窄屏四标签、多选、详情/编辑器 Back、草稿恢复、更新审查和真实脚本应用
12. [PENDING] 后续补充 userscript 导出与删除时的脚本数据保留选择

本地验证证据：

- `BrowserPluginCenterFacadeTest`、`WebSessionBrowserBackPolicyTest`、
  `UserscriptStorageTransactionTest`、`UserscriptManagementPolicyTest` 与
  `UserscriptMetadataParserTest` 合计 `23/23`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- 七份 `strings.xml` 均可解析；本轮新增 `76` 个文案键在中文、英语、西班牙语、印尼语、
  韩语、马来语和巴西葡萄牙语中名称集合一致且无本轮重复键
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务，
  `28` 个执行、`205` 个缓存命中，`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 19:12:39 +08:00`，大小 `494137206` 字节，SHA-256
  `C65D30791DA9B054428FFAF63169B58407BEE107D25BDDC1540B25A0820C8267`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36；
  Android Debug V2 签名与 `zipalign -c -P 16 -v 4` 均通过

## 2026-07-31 AI 对话公式编号、方框字形与 Markdown 节点边界修复

本轮只修改 Operit AI 对话已有 Markdown/LaTeX 渲染链，不切换公式引擎，不引入第二套
Markdown AST、WebView 公式渲染器或并行复制链。Kiyori 从未发布，因此直接修正当前内部实现。

实施与验收门禁：

1. [DONE] 为块公式建立共享语义解析，支持顶层 `\tag{...}` 与 `\tag*{...}`
2. [DONE] 公式主体保持居中，编号独立右对齐；窄屏和超宽公式不得重叠
3. [DONE] 流式块公式闭合前不显示 LaTeX 源码，闭合后与静态解析使用同一结果
4. [DONE] 修复 `jlatexmath-android 0.2.0` 的框绘制状态泄漏，使 `\boxed` 与 `\fbox`
   内部及后续字形保持填充
5. [DONE] 复制转换显式区分行内/块级公式，并保留公式编号语义
6. [DONE] 增加解析、复制、绘制状态和消息渲染回归测试
7. [DONE] 执行正式开发门禁、定向测试、`git diff --check` 和 Debug APK 构建核验
8. [DONE] block splitter 在显示公式插件之前识别行内代码，支持不同长度的反引号，
   并把保护片段并回同一段落的 `INLINE_CODE` 子节点
9. [DONE] 引用块每行剥离当前层的一个 `>` 标记，并递归复用同一 block AST，使真实块公式、
   行内代码和围栏代码保持各自语义
10. [DONE] 增加代码 span 中 `\[...\]`、`$$...$$`、多反引号、引用块公式与代码混合回归测试
11. [PENDING] 目标设备复测浅色/深色、流式输出、窄屏编号、复杂方框公式、引用块公式和
    行内代码定界符

本地验证证据：

- 代码 span 分隔符、公式语义与布局、框绘制和批更新定向 JVM 测试 `19/19`，
  零失败、零错误、零跳过
- `:app:compileDebugAndroidTestKotlin` 通过，覆盖 block splitter 代码保护、引用块递归 AST、
  编号复制语义、框命令注册和 Android Paint 状态测试源码
- `:app:externalNativeBuildDebug`、formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务、
  `29` 个执行、`204` 个缓存命中，`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 15:40:12 +08:00`，大小 `494083710` 字节，SHA-256
  `A289D5C685385C00AB5E9C54AC6FFA4144AA4DE98DF8CD9DD7226890D7705F51`
- APK 为 `com.kiyori`、`45 / 0.1.0`、compile 36、target 34、`arm64-v8a`；
  Android Debug V2 签名与 16 KB ZIP 对齐通过

## 2026-07-31 用户脚本运行授权、`unsafeWindow` 特权兼容与插件搜索

本轮继续开发浏览器下拉抽屉“插件”按钮内的 Browser Plugin Center，不涉及 AI 对话页 ToolPkg、
Skill、MCP 或包管理。阻塞
`unsafeWindow cannot be combined with privileged grants in the current runtime`
来自旧执行世界策略：特权 grant 必须留在隔离世界，而旧 bootstrap 只能把当前世界的 `window` 传给
`unsafeWindow`，所以策略直接拒绝两者组合。

本轮完成：

1. [DONE] 在现有 userscript registry 中增加持久化“允许用户脚本”总授权，默认关闭，不建立第二状态源
2. [DONE] 插件概览卡与油猴管理页共用同一授权开关；权限关闭时脚本显示“需要授权”，不误报“不兼容”
3. [DONE] 页面与共享隔离运行时按 WebView 生命周期各注册一次；权限关闭时运行时保持惰性、不返回脚本
   payload，并撤销 token、reply proxy、页面菜单、webRequest 与活动 GM 网络请求
4. [DONE] `auto / content` 下的 `unsafeWindow + privileged grants` 保持隔离执行，通过同步
   页面对象桥访问网页原始 `window`
5. [DONE] 页面对象桥不包含 native bridge、authorization token 或 GM 方法；页面只能观察或影响
   `unsafeWindow` 自身的网页对象操作
6. [DONE] 页面对象桥支持属性读写、方法、构造、回调、Promise、`fetch / Response`、普通对象、
   DOM 节点、`in` 与删除，并限制单次请求/响应和单文档引用数量
7. [DONE] 显式 `@inject-into page + privileged grants` 继续判定不兼容
8. [DONE] 安装预览和脚本详情显示页面世界、隔离世界、页面直连或隔离页面对象桥
9. [DONE] 插件中心搜索同时命中内部脚本；油猴管理页本地搜索覆盖名称、命名空间、描述、来源、
   grant、匹配网站、`@connect` 和标签
10. [DONE] 七语种 UI 文案、正式架构、`CONTEXT.md`、根 `README.md` 和浏览器 TODO 同步完成
11. [DONE] 处理 2026-07-31 vivo Android 16 设备报告：移除按脚本创建世界及权限/列表变化时的原生
    handler/listener 动态拆装，收敛为单 WebView 单隔离世界；WebView 关闭时由紧邻的 `destroy()` 退休注册
12. [DONE] 修复“未命中”：无 `@match/@include` 时按全页面匹配，支持正则 include/exclude，
    `@match` 忽略 query/fragment，document-start 使用当前文档 URL 并校验页面世界 `sourceOrigin`
13. [PENDING] 使用修复版 APK 在 Android System WebView 真机验证脚本命中、连续导航、授权启停、
    `unsafeWindow` 跨 world DOM 事件、iframe 和目标 userscript 不再触发主进程 SIGSEGV

本地验证证据：

- 插件/userscript 定向 JVM 测试 `46/46`，零失败、零错误、零跳过
- 真实 Chrome main world / isolated world 测试通过属性、方法接收者、回调、Promise、
  `fetch / Response`、DOM 参数与返回对象、构造、删除及临时 DOM 标记清理
- formal readiness、7 份本地化 XML 与 16 个新增键、34 个 Markdown 相对链接、
  旧阻塞字符串在运行时代码中零引用、页面对象桥敏感能力零引用和 `git diff --check` 均通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 233 个任务，
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-31 14:01:09 +08:00`，大小 `494083710` 字节，SHA-256
  `21917BD1C1CF80E1B00128A621DABE6CDB9110B56067C6B29331C0F3EA29E90D`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36；Android Debug V2
  签名通过，`zipalign -c -P 16 -v 4` 为 `Verification successful`

## 2026-07-30 浏览器插件中心与内置油猴脚本里程碑一

本轮正式启动浏览器下拉抽屉菜单“插件”按钮对应的 Browser Plugin Center。它属于浏览器产品域，
不连接 AI 对话页的 ToolPkg、Skill、MCP、工作流或包管理。完整长期合同见
[`browser_plugin_platform.md`](../doc-src/architecture/browser_plugin_platform.md)。
后续 Phase 0 至 Phase 8 的详细任务、顺序和独立验收门禁见
[`browser_plugin_platform/`](browser_plugin_platform/index.md)。

Kiyori 与该入口尚未发布，因此直接清理旧 `USERSCRIPTS` UI 路由；已安装 userscript、仓库、
运行状态和日志继续由现有 `UserscriptRepository` 与 `WebSessionUserscriptManager` 唯一持有，
不得迁移、复制或清空。

里程碑一实施与验收门禁：

1. [DONE] 审计 Kiyori 浏览器抽屉、Back 状态机、userscript manager、安装预览和共享
   `StandardBrowserSessionTools` 调用链
2. [DONE] 只读参考 `D:\10_Project\hikerView` 的统一列表、编辑入口和页面生命周期注入原则，
   不复刻缺失 `JSManager`、旧 Activity 或事件总线
3. [DONE] 固化插件、userscript、未来原生插件、WebExtension 与 AI ToolPkg 的产品和运行时边界
4. [DONE] 固化 AI 生成脚本/插件的草稿、检查、权限审查、用户确认和安装事务
5. [DONE] 新增统一 Browser Plugin 模型与纯投影 facade，不建立第二插件仓库
6. [DONE] 把浏览器抽屉 `USERSCRIPTS` 路由替换为 `PLUGINS`
7. [DONE] 增加 `OVERVIEW / USERSCRIPTS` 子页面，菜单进入概览，脚本安装预览直达管理页
8. [DONE] 建立“本页 / 已安装”、搜索、添加和内置“油猴脚本”插件卡
9. [DONE] 增加经过核实的 Greasy Fork、ScriptCat、OpenUserJS、Userscript.Zone 和 GitHub
   userscript 来源快捷弹窗，在共享 Browser Runtime 新标签打开
10. [DONE] 系统 Back 与标题返回从油猴脚本子页先回插件概览，再关闭下拉抽屉
11. [DONE] 补充插件投影、当前页统计、路由和 Back 策略测试
12. [DONE] 更新 `CONTEXT.md`、根 `README.md` 和浏览器能力资料
13. [DONE] formal readiness、5 项定向测试、7 份 strings 解析、旧路由零引用、
   `git diff --check` 和 Debug APK 构建核验均通过
14. [PENDING] 目标设备验收抽屉拖动、窄屏布局、来源弹窗、新标签跳转、安装预览和返回手势

本轮明确不实现通用 WebExtension runtime、CRX/Edge 扩展直接安装、沉浸式翻译移植、原生插件包协议、
AI 自动安装或 userscript 数据/注入引擎重写。

本地验证证据：

- `BrowserPluginCenterFacadeTest`、`WebSessionBrowserBackPolicyTest` 和
  `WebSessionBrowserUserAgentRoutingTest` 合计 `5/5`，零失败、零错误、零跳过
- 7 份 `strings.xml` 均可解析，新插件文案键完整且无重复；旧
  `WebSessionBrowserSheetRoute.USERSCRIPTS`、`onOpenUserscripts` 和
  `openUserscriptSheetOnMain` 引用为零
- formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务、
  零失败，`:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482641438` 字节，
  SHA-256 `F69E8FFB6B9EB5E926F7A0EA9BBA43619D7503B87A30B58F8C8468707B283CEC`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34；Android Debug V2 签名和
  `zipalign -c -P 16 -v 4` 通过

## 2026-07-30 浏览器统一历史下拉抽屉与负一屏入口

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器资料库的唯一进度载体。Kiyori 尚未发布，现有仅展示 WebView 当前会话和网页访问记录的
旧历史抽屉直接替换；不保留第二套历史页面、旧双区结构或并行数据库。

实施与验收门禁：

1. [DONE] 扩展 `WebSessionHistoryStore` 的现有 `history_json`，统一承载网页和真实播放器记录
2. [DONE] 网页只由普通 Profile 的主框架访问回调写入，不把网络日志、媒体候选或视频直链重复记为网页
3. [DONE] 唯一 `PlayerSession` 在接受新媒体请求时写入视频历史，并以“在线视频 / 本地视频”标记来源
4. [DONE] 历史持久化不保存 Cookie、Authorization 或完整请求 headers；在线重播在点击时从活动
   WebSession 读取当前 UA、Profile Cookie 和来源页 Referer
5. [DONE] 用网络日志抽屉的紧凑结构重建历史 UI：标题、数量、搜索、删除、横向
   `全部 / 网页 / 视频 / 音乐 / 小说 / 其他` 筛选和分类空态
6. [DONE] 删除动作打开“过去一小时 / 过去24小时 / 过去一周 / 所有时间 / 取消”底部选择面板，
   只删除当前分类与时间范围匹配的记录
7. [DONE] 浏览器菜单和负一屏“历史”卡挂载同一个共享抽屉；负一屏显示真实历史总数
8. [DONE] 补充 store、分类过滤、删除范围、媒体来源和 Shell 状态测试，更新语义文档
9. [DONE] formal readiness、差异检查、5 组定向测试（104 项）和
   `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 已通过，最终 Debug APK 已生成并核验
10. [IN PROGRESS] 用户已确认新版历史记录界面正常；目标设备仍需继续覆盖搜索、抽屉拖动、
    时间范围删除、普通/无痕隔离、网页打开及在线/本地视频重播

## 2026-07-30 AI 抽屉状态徽标、彩色图标与底栏弹性动效

本轮不改变导航路由、业务状态 owner 或页面结构，只修复当前 UI 的明确遮挡并收口三组图标视觉。
Kiyori 尚未发布，因此直接迭代当前样式，不保留旧蓝色选中态或平行图标方案。

实施与验收门禁：

1. [DONE] 定位 AI 左抽屉包管理、权限、工作流卡片的状态徽标与居中图标重叠根因
2. [DONE] 设计状态徽标独立顶部区域，并保留三个快捷入口的现有计数与权限状态语义
3. [DONE] 将软件首页天气图标与 AI 对话页浏览器、终端、工作区三个顶栏动作接入固定语义色；
   晴天独立使用浅色 `#C57C00`、深色 `#FFD166`
4. [DONE] 为底部五个封闭图标增加精确 `#FFC153` 填充层与页面背景色内部细节层，删除蓝色空心选中态
5. [DONE] 按各 Vector 可见边界补偿选中态终点尺寸，并用仅含内部圆环的设置细节层清除外缘残线
6. [DONE] 每次点击先连续压回较小填充态，再以低阻尼弹簧放大到原图标视觉尺寸；重复点击当前入口同样重播
   - [DONE] 后续微调仅将首页、浏览器、小程序和文件入口阻尼降至 `0.42`，扩大黄色峰值；设置保持 `0.55`
7. [DONE] 更新视觉合同和定向测试
8. [DONE] 执行正式开发门禁、差异检查并构建核验 Debug APK
9. [PENDING] 目标设备浅色、深色、窄屏抽屉、五入口最终尺寸与弹性动效独立验收

本地验证证据：

- `KiyoriThemeTest 11/11`、`KiyoriShellStateTest 47/47`，合计 `58/58`，零失败、零错误、零跳过
- Android 资源合并、`:app:compileDebugKotlin`、formal readiness 与 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 完成 `233` 个任务、零失败，
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482617522` 字节，
  SHA-256 `22C6A2D7A20D28038DB8B549826F56CA7871F18E79D6748F7D4310FC358AC15A`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34；Android Debug V2 签名与
  `zipalign -c -P 16 -v 4` 通过

## 2026-07-30 Operit `1.12.0+5` 上游差异审计与选择性移植

本轮以 `upstream/main@98faa44c` 为上游目标，Kiyori 以
`main@ce0d504f` 为本地基线。双方共同祖先为 `ef00abc5`；Kiyori 已有 46 个独立提交，
上游有 42 个独立提交，不能通过整分支 merge、最终文件覆盖或版本号追平替代逐项审计。

本轮需要移植的修复：

- OpenAI Responses 重放 reasoning item 时始终携带合法 `summary` 数组
- 对话 summary checkpoint 只保存安全纯文本；工具包定义与激活状态继续由真实工具 schema 和运行时 owner 提供，不复制到摘要正文
- 流式 Markdown 表格更新时保留横向滚动状态，并修复工具调用尾部与等长结构变化漏刷
- 用户中断普通聊天输出后，保留部分内容、Token、等待耗时和输出耗时
- 包管理普通脚本包启停完成后，以真实后端结果同步可见开关状态
- 将 WebChat 的 Vite/esbuild 更新到上游已验证的安全边界

本轮明确不合并的上游范围：

- Operit 主题编辑器与运行时 scoped theme snapshot。Kiyori 固定主题和 AI 局部个性化已有独立
  产品合同，不能恢复上游全局主题所有权
- GitHub OAuth broker、Operit 发布/公告、夜间构建和 `1.12.0+5` 发布元数据
- 罗马尼亚语、STT 构建期联网下载和上游 Lint baseline 删除
- MCP bridge 的 uuid 依赖删除。Kiyori 的 `tools/mcp_bridge/index.ts` 仍调用 `uuidv4()`，
  该依赖不是无用依赖，不能照搬上游删除
- ToolPkg 市场来源标记、聊天消息持久化 Hook 与整套发布协议。对应能力完整接入前，
  `OPERIT_MARKET_COMPAT_VERSION` 在该历史任务中继续保持 `1.12.0+4`，不能虚报 `+5`
  兼容；这些前置能力已由 2026-08-08 的 `+8/+9` 专项任务补齐并将当前基线提升为 `+9`

实施与验收门禁：

1. [DONE] 获取上游并完成提交图、路径重叠、最终源补丁和 Kiyori 当前实现审计
2. [DONE] 手工适配选定修复，不执行 merge/cherry-pick，不覆盖 Kiyori 产品域
3. [DONE] 7 个针对性 JVM 测试通过；Responses 和表格 AndroidTest 已编译通过，
   真机运行单列为设备验收边界
4. [DONE] WebChat Vite `6.4.3` 生产构建成功、npm 审计为 0 漏洞、formal readiness 通过、
   `git diff --check` 通过；同步正式 WebChat 资产后重新构建 Debug APK
5. [DONE] 未移植范围、MCP uuid 差异、Git 状态和 `1.12.0+5` 兼容版本升级条件已记录

本轮最终 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，包名 `com.kiyori`，
`versionCode=45`、`versionName=0.1.0`、仅包含 `arm64-v8a`；V2 签名与 16 KiB page alignment
检查通过。APK 内 `assets/web-chat` 的 HTML、CSS、JS SHA-256 已逐项与
`web-chat/dist` 一致，避免只验证未进入 APK 的旁路构建产物。

## 2026-07-30 视频播放器 UI、手势、旋转与 Anime4K 七档闭环

本轮继续复用唯一 `PlayerSession`、唯一非导出 `:player` MPV runtime、现有 Surface lease、
`PlayerSettingsStore` 和浏览器下载 owner。Kiyori 与当前播放器仍未发布，因此直接清理旧的
“关闭 / 流畅 / 均衡 / 高清”四档超分方案，不保留并行枚举、旧 shader 组合或第二状态源。

本轮目标与实现边界：

- 统一播放器按钮、锚定菜单、日志弹窗、加载/错误卡片和手势提示的深色现代视觉；继续使用现有
  播放器专用图标与唯一 Compose 控制层
- 右上角更多菜单增加可直接切换的“自动旋转”开关，写入现有
  `PlayerSettingsStore.followGravityRotation`，由 `PlayerActivity` 当前方向策略实时消费
- 左半屏纵向手势仍控制亮度，但提示条改到右侧；右半屏纵向手势仍控制音量，但提示条改到左侧
- 倍速菜单从上到下固定为 `3.0x / 2.0x / 1.5x / 1.25x / 0.75x / 0.5x`；内部合法倍速继续保留
  `1.0x`，用于默认速度、恢复正常速度和长按加速跨档
- 播放器设置增加“长按加速”开关。按住画面超过阈值后，当前倍速只临时提升到下一档；
  松手、取消手势、界面销毁或媒体会话变化时恢复按下前速度，临时速度不得写入倍速记忆
- Anime4K 严格对照 `D:\10_Project\mpv-android-anime4k@32f5f169` 的 Balanced/M 质量链，
  顺序固定为“关 / A / B / C / A+ / B+ / C+”，文案分别为“原始画质 / 强力重建 /
  柔和重建 / 降噪处理 / 双重强化 / 双重柔和 / 降噪强化”
- 复制参考仓库所需的原始授权文本与 shader 字节并锁定 SHA-256；模式切换必须校验缓存文件、绝对路径、
  MPV `glsl-shaders` 实际属性和诊断日志，任何失败进入现有可见错误链路
- 逐项复核默认/记忆倍速、连播、双击、按钮跳转、精确 seek、章节、缩略图、解码、GPU、
  Vulkan、音量、字幕、保存目录、自动旋转、后台、全屏退出和网络缓存的唯一 owner 与消费路径

串行实施与验收门禁：

1. [DONE] 审计现有播放器 UI、设置、手势、旋转、倍速和 Anime4K 调用链，并与参考仓库逐项映射
2. [DONE] 重建统一播放器弹窗、按钮与中央/双侧手势反馈视觉
3. [DONE] 接通更多菜单自动旋转、亮度音量提示换侧和指定倍速菜单顺序
4. [DONE] 实现长按加速设置、会话级临时倍速 token、松手恢复和冲突手势处理
5. [DONE] 移植七档 Anime4K Balanced/M shader 链、资产哈希校验和 MPV 属性回读
6. [DONE] 更新播放器单元/静态资源门禁、`CONTEXT.md`、`README.md` 和阶段 11 架构文档
7. [DONE] 执行播放器定向测试、资源门禁、Kotlin 编译、formal readiness、
   `git diff --check` 和串行 Debug APK 构建核验
8. [PENDING] 目标设备验收按钮热区、弹窗锚点、亮度音量换侧、双击/拖动提示、长按恢复、
   重力旋转和七档 Anime4K 画质、性能、温度与 MPV 日志

后续视觉修订：

1. [DONE] 移除 `LegacyImageButton` 和 `LegacyTextButton` 的静态圆形/胶囊底色及描边，
   保留原尺寸点击热区、禁用透明度和受控点击涟漪
2. [DONE] 由 `DropdownMenu` 自身统一绘制 `20dp` 深色圆角表面，删除叠加在默认菜单表面上的
   第二层背景，避免四角露出白色延伸
3. [DONE] 执行播放器静态资源门禁、Kotlin 编译、差异检查与 Debug APK 构建核验

本地验证证据：

- 播放器三组定向 JVM 测试 `38/38` 通过，零失败、零错误、零跳过；Anime4K 资产与静态链路
  Python 门禁 `6/6` 通过
- `:app:compileDebugAndroidTestKotlin`、formal readiness 和 `git diff --check` 通过
- `:app:assembleDebug` 完成 `233` 个任务、零失败，`:app:verifyDebugPlayerRuntimePackaging`
  通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482616169` 字节，
  SHA-256 `1679B8140A9B5715AA8D5B0C5633D12185ABE9E6DDD1791150C108887EEBB907`
- APK 为 `com.kiyori`、版本 `45 / 0.1.0`、min 26、target 34、`arm64-v8a`；Android Debug
  V2 签名和 16 KB ZIP 对齐通过
- APK 内十个 `assets/shaders/*.glsl` 数量、文件名和 SHA-256 均与
  `mpv-android-anime4k@32f5f169` 精确一致；真实设备上的 shader 编译、视觉差异、性能和温度
  仍保持 `verification_pending`
- Crash Report `a0f86ddc-8565-497c-8653-ade1904f44a2` 证明已有开发安装仍保存旧
  `off / fast / balanced / quality` ID；读取链现已在严格枚举解析前一次性迁移为
  `OFF / B / A / A_PLUS`，未知值继续严格拒绝
- 后续视觉修订已通过新增静态断言、`:app:compileDebugKotlin`、formal readiness、
  `git diff --check` 和最终 `:app:assembleDebug`；统一按钮入口不再绘制静态底色/描边，
  `PlayerPopupMenu` 仅使用 `DropdownMenu` 自身的深色圆角表面

## 2026-07-30 文件下载器设置主题边界崩溃修复

Crash Report `92a847df-518a-477d-b3cb-870b17273550` 显示，文件下载器设置打开底部选择面板时，
`KiyoriSettingsSelectionSheet` 读取不到 `LocalKiyoriSettingsColors`。折叠列表内部已经提供
`KiyoriSettingsTheme`，但选择面板与列表是同级节点，原主题边界没有覆盖完整设置子页。

本轮修复门禁：

1. [DONE] 在 Kiyori Shell 子页面宿主为浏览器、下载器和播放器设置提供完整主题边界
2. [DONE] 保留缺少主题时的严格异常，不添加默认颜色或回退逻辑
3. [DONE] 增加 Shell 设置子页主题映射回归测试
4. [DONE] 执行定向 JVM、Kotlin 编译、formal readiness、差异检查和 Debug APK 核验
5. [PENDING] 在报告设备复测文件下载器设置的全部选择面板，并顺带复测播放器设置面板

详细根因、实现与验收记录继续写入
[`kiyori_settings_theme_unification/3_implementation_and_validation.md`](kiyori_settings_theme_unification/3_implementation_and_validation.md)。

本地证据：`KiyoriShellStateTest` 与 `KiyoriSettingsPagesTest` 合计 `56/56`，零失败、零错误、
零跳过；测试任务完成 `:app:compileDebugKotlin`。formal readiness、`git diff --check` 和
`:app:assembleDebug` 通过，构建共 233 个任务、零失败，播放器运行时打包校验通过。Debug APK
为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `482615791` 字节，SHA-256
`E3F7D90A72851CBBFB4479A313CD8D0BE50F2B1346D5DD1D4CEBA09835DA721E`；包名
`com.kiyori`、版本 `45 / 0.1.0`、min 26、target 34、`arm64-v8a`，Android Debug V2 签名
与 16 KB ZIP 对齐通过。

## 2026-07-29 全量 UI 与功能逻辑链路审计优化

本阶段以两个目标进行横向封板：覆盖全部用户可达页面、抽屉、弹窗和特殊渲染域的视觉一致性，
并逐条检查现有功能从入口、导航、状态 owner、执行层、持久化到结果反馈的完整链路。只修复现有
功能和真实缺陷，不新增产品能力，不建立第二运行时、第二状态源或任何 fallback。

完整页面矩阵、语义配色、特殊渲染边界、功能链路、实施顺序和验证门禁见
[`kiyori_full_ui_and_function_chain_audit/`](kiyori_full_ui_and_function_chain_audit/index.md)。
本轮持续工作窗口截止到 `2026-07-30 10:00 Asia/Shanghai`；未授权的设备、提交、推送和发布
不在范围内。

当前代码实现、静态反向审查、660 项 JVM 测试、AndroidTest 编译、正式开发准备检查、差异检查、
完整 Lint、Debug APK、播放器运行时打包、Debug 签名、包信息、ABI 和 16 KB ZIP 对齐均已
通过。目标设备上的视觉与真实系统能力验收继续标记为 `verification_pending`。

## 2026-07-29 全局页面视觉一致性与语义彩色图标

本阶段继续收口取消旧全局自定义配色后的剩余影响，建立不依赖用户颜色、只随固定 Kiyori
浅色/深色主题变化的全应用语义色系统。应用壳、正文、卡片和浏览器 chrome 保持中性层级；
蓝、绿、紫、橙、红、青、粉用于图标容器、状态、分组标识和选中边界，不把页面改造成大面积
高饱和彩色界面。

重点覆盖负一屏、AI 模态左抽屉、浏览器菜单及内容抽屉、相关弹窗、AI 对话入口，以及包管理、
权限和工作流页面。设计合同、稳定颜色映射、页面覆盖矩阵、实施顺序和验证门禁见
[`kiyori_global_ui_visual_unification/`](kiyori_global_ui_visual_unification/index.md)。

本地实现与验收已完成：7 个定向 JVM 测试类共 `75/75` 通过，主源码和测试源码编译、
formal readiness、`git diff --check` 与 Debug APK 构建均通过。APK 为 `com.kiyori`、
`45 / 0.1.0`、`arm64-v8a`，Android Debug v2 签名和 16 KB ZIP 对齐通过。未安装 APK、
未操作设备，浅色/深色视觉、抽屉弹窗、工作流画布和触摸热区保持 `verification_pending`。

## 2026-07-29 设置 UI、主题边界与现代化配色统一

本阶段继续整理设置系统：取消用户颜色对 Kiyori 全应用的控制，保留固定浅色、深色和跟随系统
主题；背景、气泡、头像、聊天头部和输入区继续作为 AI 对话局部个性化。设置首页、拆分设置根、
浏览器、下载器、播放器及关键子页使用同一套浅深色 token 和蓝绿紫橙红青粉语义图标色阶。

主题 owner、必须删除的旧状态、设置视觉 token、覆盖页面、实施顺序和验收门禁见
[`kiyori_settings_theme_unification/`](kiyori_settings_theme_unification/index.md)。

本地实施与验收已完成：旧全局颜色路径和资源为零，设置主题覆盖顶栏与正文，定向 JVM 测试
`64/64`、Kotlin 编译、formal readiness、`git diff --check` 和 Debug APK 构建均通过。APK 为
`com.kiyori`、`45 / 0.1.0`、`arm64-v8a`，Android Debug V2 签名与 16 KB ZIP 对齐通过。
未安装 APK、未操作设备，视觉和交互验收保持 `verification_pending`。

## 2026-07-29 设置页信息架构与统一视觉

本轮把原综合 AI 设置按实际 owner 拆分，并以文件下载器设置页为统一视觉标准。Settings Home
继续保持 `4/4/4/4`：第一组固定为“账号与连接 / AI 助手 / 语音服务 / 小程序管理”，删除
“剪贴板口令”和独立“小程序订阅”；“界面定制”和“数据备份与同步”开始承接从 AI 设置移出的
应用级入口，普通网站 Cookie 清理迁入网页浏览器设置。

详细信息架构、状态 owner、返回合同、视觉标准和串行门禁见
[`kiyori_settings_information_architecture/`](kiyori_settings_information_architecture/index.md)。

当前实现和本地验收已完成：设置与 Shell 定向测试 `52/52` 通过，formal readiness、
`git diff --check`、Debug APK 构建、包元数据、v2 签名和 16 KB ZIP 对齐均通过。未安装 APK、
未操作设备，设置页视觉、长语音表单、GitHub 登录弹窗与返回交互保持
`verification_pending`。

## 2026-07-29 文件下载器全链路深度优化与阶段封板

本阶段以现有 `BrowserDownloadManager` 为唯一任务、调度和状态所有者，对浏览器下载、播放器
下载、网页下载确认、Shell/浏览器共享下载抽屉、文件下载器设置、系统下载器桥接、M3U8
离线包、SAF/公开目录交付和 APK 安装清理进行一次封板级审计与闭环。项目尚未发布，因此允许
直接清理未对外形成兼容合同的旧内部方案；不得新建第二下载队列、第二任务数据库、平行状态
owner 或回退逻辑。

审计确认的优先级与失败边界：

- 同名任务必须在进入队列时原子预留最终文件和临时文件，不能让快速连续任务写入同一路径
- 任务快照必须串行、原子落盘，进程终止或并发进度更新不能破坏整个任务历史
- 多个网页下载请求必须排队确认，不能因全局单槽 `check` 触发崩溃或覆盖前一请求
- 断点续传必须使用资源校验器确认服务端表示未变化；不能把不同版本的同名资源拼接到一起
- 错误、日志和用户文案不得泄露带签名参数、Cookie 或授权信息的完整 URL
- 后台下载继续复用唯一 Manager；Android 14 及以上使用用户发起数据传输任务宿主，旧版本使用
  `dataSync` 前台服务宿主，不用第二套 WorkManager 下载数据库
- 下载抽屉和设置页只呈现有真实执行链路的能力；搜索、筛选、批量操作、状态说明、通知与目录
  语义必须一致且可访问
- M3U8、安装包清理和系统下载器能力按真实平台边界实现；不使用计时删除、伪成功或隐式降级

串行实施与验收门禁：

1. [DONE] 数据正确性与安全：
   - 原子化任务状态文件，集中序列化持久化入口
   - 原子预留任务文件名，覆盖队列中尚未创建文件的同名任务
   - 下载确认请求改为 FIFO，逐项确认或取消
   - 传输错误统一脱敏；补充 ETag、Last-Modified 与 `If-Range` 安全续传合同
   - 增加相关单元测试、formal readiness、`git diff --check` 和 Debug APK 构建核验
2. [DONE] 后台运行与通知：
   - 为唯一 Manager 增加平台调度宿主和明确的系统停止/重启状态
   - 建立进行中通知、完成/失败通知、点击进入共享下载抽屉和可解释操作
   - 核验应用重建、进程终止、用户暂停与系统中断不会相互混淆
3. [DONE] 下载抽屉、设置和文案：
   - 增加搜索、状态筛选、全选和批量操作闭环，统一点击热区、空态、错误与进度表达
   - 补充有真实执行链路的网络策略、后台运行与通知设置，删除误导或重复入口
   - 统一资源文案、类型命名和文件命名，删除不再使用的旧 UI/策略代码
4. [DONE-local] 格式能力与最终封板：
   - 完成 M3U8 边界、字节范围、资源规模和离线包完整性审计
   - 把 APK 清理改为基于真实安装结果的持久化闭环，或删除无法兑现的旧选项
   - 同步 `README.md`、`CONTEXT.md` 和下载中心完成度文档
   - 执行风险相称的定向测试、Kotlin 编译、formal readiness、差异/敏感内容审计以及最终
     `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
   - 核验最终 Debug APK 的时间、大小、SHA-256、包信息、签名和 zipalign；设备视觉与真实网络
     验收在未安装 APK 前保持 `verification_pending`

里程碑 1 本地证据：

- `BrowserDownloadPolicyTest 24/24`、`BrowserDownloadTransportTest 18/18`、
  `BrowserDownloadM3u8RuntimeTest 5/5`、`BrowserDownloadDrawerPolicyTest 8/8`，合计
  `55/55`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；差异检查只有既有
  CRLF 到 LF 提示，没有 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 15s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 20:32:39 +08:00`，大小 `482591403` 字节，SHA-256
  `475E1F5E1BDF800549F5902918E3231F0E2C0A867636D78F7925258B399A0260`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34；Android Debug v2 签名和
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未操作设备；真实断点续传、资源变化、快速同名任务和进程终止仍需在后续设备/
  后台运行里程碑统一验收

里程碑 2 本地证据：

- `BrowserDownloadRuntimePolicyTest 5/5`、`BrowserDownloadPolicyTest 24/24`、
  `BrowserDownloadTransportTest 18/18`、`BrowserDownloadM3u8RuntimeTest 5/5`、
  `BrowserDownloadDrawerPolicyTest 8/8`、`MainActivityBrowserActionTest 1/1`、
  `KiyoriShellStateTest 42/42`，合计 `103/103`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；差异检查仍只有
  CRLF 到 LF 提示，没有 whitespace error
- APK 内 Manifest 已由 `aapt dump xmltree` 核验：
  `RUN_USER_INITIATED_JOBS`、受 `BIND_JOB_SERVICE` 保护的 `BrowserDownloadJobService`、
  `dataSync` 类型的 `BrowserDownloadForegroundService`、私有运行时 Action Receiver 和
  Boot Receiver 均存在，导出边界符合设计
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 3m 26s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 21:20:59 +08:00`，大小 `482597303` 字节，SHA-256
  `D81A5C2865A265C0C9E37394401F9A9E32190B05A74BCECD031F35D45F1921A9`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34；Android Debug v2 签名和
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未操作设备；Android 14+ UIDT 调度、Android 13- dataSync 通知、网络切换、
  Task Manager 停止、进程重建、重启恢复和通知动作保持 `verification_pending`

里程碑 3 与最终封板本地证据：

- 设置模型升级为 version 2，下载设置固定为 `5/4/2/2/1` 共 14 行；下载抽屉接入搜索、状态
  筛选、可见目标全选、独立批量取消/删除、筛选空态和可访问性说明
- M3U8 离线包增加 4 MiB 文本上限、`#EXTM3U` 校验、master 循环检测、重复/字节范围资源
  去重、256 项有界批次、KEY/MAP/PART 资源完整性校验，以及独立音轨/画面/字幕 playlist 的
  明确拒绝
- APK 自动清理已删除 90 秒计时路径；任务记录持久化包名、版本和安装前版本，SAF 暂存只用于
  包信息解析并立即清理，最终删除严格依赖 `PACKAGE_ADDED` / `PACKAGE_REPLACED` 的精确包名与
  版本匹配。完成通知的“打开”动作也通过 `MainActivity` 回到唯一 Manager
- `BrowserDownloadPolicyTest 26/26`、`BrowserDownloadTransportTest 19/19`、
  `BrowserDownloadM3u8RuntimeTest 7/7`、`BrowserDownloadDrawerPolicyTest 10/10`、
  `BrowserDownloadRuntimePolicyTest 8/8`、`KiyoriSettingsPagesTest 8/8`、
  `MainActivityBrowserActionTest 1/1`、`KiyoriShellStateTest 42/42`，合计 `121/121`，
  零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness 和 `git diff --check` 通过；敏感内容审计无凭据
  文件或常见密钥模式，唯一 `secret` 命中是既有布尔参数 `secret = false`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 10s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 22:26:17 +08:00`，大小 `482597303` 字节，SHA-256
  `A1ECBF2DCE6301445ECD544885BDA9F467FD28E8503AED84DD9E6C930B651B18`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 36；Android Debug v2
  签名有效，`zipalign -c -P 16 -v 4` 通过。APK Manifest 已核验 UIDT 权限、JobService、
  dataSync 前台服务、运行时/启动接收器和安装结果接收器
- 本阶段本地代码与制品封板完成；未安装 APK、未操作设备。真实 UIDT/dataSync 生命周期、
  网络切换与漫游、服务端表示变化、SAF 写入、系统安装成功/取消广播、抽屉视觉与触摸仍为
  `verification_pending`

## 2026-07-29 浏览器与文件下载器设置页统一

本轮以当前 `KiyoriPlayerSettingsPage` 为唯一设置页视觉和交互基线，把浏览器与文件下载器设置页
统一到同一套分组标题、说明、卡片、双行设置项、Material Switch、禁用态和底部选择面板。继续
复用唯一 `WebSessionBrowserSettingsStore`、`BrowserDownloadSettingsStore` 与
`BrowserDownloadManager`，不创建第二状态 owner、第二下载器或空白功能实现。

共享 UI 契约：

- 页面继续复用 `KiyoriCollapsingSettingsPage` 的大标题折叠头和 `#F5F5F2` 背景
- 分组标题、分组说明、16dp 白色圆角卡片、0.6dp 分隔线以及左右边距完全沿用播放器设置页
- 每个设置项固定为标题与两行内说明，右侧使用紧凑状态摘要、箭头或 Material Switch
- 依赖不满足或尚无真实 owner 的项目使用 42% 透明度和不可点击状态，不再打开空白页面
- 所有单选项共用播放器设置页的 26dp 圆角底部面板，显示标题、当前值、选项说明和蓝色选中标记

浏览器信息架构按使用路径固定为五组：

1. **插件与会话**：网页插件、返回行为和标签恢复
2. **主页、标签与手势**：主页入口、标签样式、导航手势和搜索引擎切换条
3. **音视频嗅探**：搜索栏嗅探入口、自动悬浮播放、悬浮嗅探模式和规则入口
4. **网站权限与数据**：外部应用、位置、翻译、网站配置和密码
5. **显示与高级**：字体、缩放、调试、User-Agent、代理和新窗口

其中主页、搜索栏嗅探入口、自动悬浮播放、允许网页打开应用和允许网页获取位置继续连接现有真实
设置值；其余尚无本页 owner 的项目保留产品信息，但明确显示为未接入状态。浏览器“悬浮嗅探”
面板删除两个设置开关，只负责视频格式筛选、候选列表和播放/下载/复制/查看链接操作；两个开关
只在浏览器设置页出现，避免同一配置存在两个入口。

下载器信息架构按真实执行阶段固定为四组：

1. **下载器与性能**：默认下载器、保存位置、并行任务和普通/M3U8 线程预算
2. **M3U8 与存储**：M3U8 离线包和普通文件读写分块
3. **安装与通知**：安装包清理、下载确认和完成提示
4. **网络协议**：新建内置下载任务使用的 HTTP 协议

“默认保存位置”在同一个选择面板中提供应用下载目录、公开下载目录和 SAF 自定义目录三个明确
选项，底层继续使用既有 `autoTransferToPublicDirectory` 与目录 URI 互斥合同。内置下载器专属
设置在系统下载器生效时显示依赖禁用态；“默认下载器”和“跳过下载确认”保持可操作，内置任务
完成提示则随内置引擎专属设置一起禁用。

实施与验收门禁：

1. [DONE] 提取播放器设置页的分组、设置行和选择面板为共享 Compose 组件，并让播放器页
   无行为变化地复用
2. [DONE] 重构浏览器设置规格、分组说明、动态摘要、真实开关、禁用态和主页子页；把
   “搜索栏嗅探入口”和“自动悬浮播放”从候选面板迁移到“音视频嗅探”分组
3. [DONE] 重构下载器命名、分组说明、状态摘要、依赖禁用态、目录选择和所有选择面板
4. [DONE] 更新 `KiyoriSettingsPagesTest`，覆盖共享尺寸、分组顺序、真实 action 和依赖策略
5. [DONE] 同步 `README.md`、`CONTEXT.md` 与相关浏览器完成度文档
6. [DONE] 执行定向 JVM 测试、Kotlin 编译、formal readiness、`git diff --check` 和最终
   `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
7. [PENDING] 目标设备验收三页滚动折叠、字体与间距、开关热区、底部面板、SAF 目录以及系统/
   内置下载器切换；本轮不安装 APK、不操作设备

本地证据：

- `KiyoriSettingsPagesTest 8/8`、`BrowserDownloadPolicyTest 20/20`、
  `BrowserMediaCandidatePolicyTest 16/16`，合计 `44/44`，零失败、零错误、零跳过
- 主源码 Kotlin 编译通过；formal readiness 和 `git diff --check` 通过，后者只有工作树既有
  CRLF 到 LF 提示，没有 whitespace error
- 静态反向检查确认候选面板中的 `Switch`、“搜索栏嗅探入口”和“自动悬浮播放”均为 `0`，
  旧设置行类型、空白占位 action 和旧下载选择面板常量也均为 `0`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 3m 54s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 18:58:41 +08:00`，大小 `482590795` 字节，SHA-256
  `432FBD73770E59D408F6AEDD2034E76E7C5FAD0D43150543005F789DE559CB6B`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名和
  `zipalign -c -P 16 -v 4` 均通过
- 未安装 APK、未操作设备；三页视觉、横屏/竖屏滚动、SAF 选择器和系统/内置下载器切换保持
  `verification_pending`

## 2026-07-29 播放器保存位置、重力旋转与横屏超分布局

本轮继续复用唯一 `PlayerSession`、`PlayerSettingsStore`、
`BrowserDownloadSettingsStore` 和 `BrowserDownloadManager`，不创建第二播放器、第二下载器或平行
目录状态。目标是让播放器截图和右侧下载按钮都具备可解释、可持久化、不会误释放 SAF 权限的保存
策略，同时修复横屏控制层布局和重力旋转冲突。

设置页按用户决策频率和功能关系重排为六组：

1. **播放与连播**：默认倍速、倍速记忆、自动下一集、队列结束行为
2. **手势与进度**：双击、跳转、精确定位、章节、缩略图
3. **画面与超分**：Anime4K 记忆和默认模式、解码器、GPU Next、Vulkan
4. **音频与字幕**：音量增强、字幕缩放
5. **保存与下载**：截图保存位置、视频下载位置
6. **窗口与在线**：跟随重力旋转、退出全屏、后台行为、在线播放缓存

保存位置契约：

- 两个播放器目录默认均为“跟随文件下载器”，不复制或改写下载器主设置
- 跟随模式实时读取文件下载器当前保存策略：自定义 SAF 目录、公开下载目录或应用下载目录
- 截图可单独选择 SAF 目录；播放器直接把 PNG 写入该目录
- 视频可单独选择 SAF 目录；该次请求继续进入唯一 `BrowserDownloadManager`，并冻结独立目录为
  任务目标。Android 系统下载器不能写入任意 SAF 目录，因此独立目录使用现有内置下载引擎
- 目录 URI 与显示名称成对保存；清除独立目录后恢复跟随，不保留隐形目标
- 释放旧 SAF 权限前同时检查下载器主设置、播放器截图设置、播放器视频设置和现有下载任务，避免
  多个功能共用同一目录时误释放权限

旋转和控制层契约：

- “跟随重力自动旋转”关闭时保持现有默认横屏，底部“旋转”按钮可手动切换横竖屏
- 开启后 Activity 使用 `FULL_SENSOR` 跟随设备重力，底部按钮显示“自动”并进入不可点击状态，
  防止手动方向请求与传感器策略互相覆盖
- 横屏 Anime4K 控件使用固定宽度锚定左下角；“超分”和模式名使用紧凑行高，不改变弹窗实时
  切换逻辑

实施与验收门禁：

1. [DONE] 扩展播放器设置模型、目录持久化和跨功能 SAF 权限所有权检查
2. [DONE] 为播放器视频下载请求增加单次目录目标，并贯穿浏览器 host、确认弹窗和下载任务
3. [DONE] 让播放器截图按独立目录或下载器主策略写入真实目标
4. [DONE] 重排设置页、增加两个目录选择项和重力旋转开关，补齐文案与状态摘要
5. [DONE] 修复横屏超分位置和文字间距，协调自动旋转与手动按钮状态
6. [DONE] 更新单元测试和播放器架构文档，执行定向测试、Kotlin 编译、formal readiness、
   `git diff --check` 和最终 Debug APK 构建核验
7. [PENDING] 目标设备验收横竖屏重力切换、横屏超分位置、截图目录、视频下载目录和共享 SAF
   权限；本轮不安装 APK、不操作设备

本地证据：

- `PlayerPolicyTest 22/22`、`BrowserDownloadPolicyTest 20/20`、
  `PlayerControlsPolicyTest 3/3`、`KiyoriSettingsPagesTest 8/8`，合计 `53/53`，零失败、零错误、
  零跳过
- `ci.test.test_player_assets 6/6`、`:app:compileDebugKotlin`、formal readiness 和
  `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，233 个任务零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 17:26:26 +08:00`，
  大小 `482590795` 字节，SHA-256
  `723E9A6E9093930CB8249045A91AEAF4DB419CAB3D7A6F1C04C5CC202BEBCEAE`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名通过，
  `zipalign -c -P 16 4` 通过
- 未安装 APK、未操作设备；横竖屏视觉、传感器方向、真实 SAF 写入和在线播放下载仍为
  `verification_pending`

## 2026-07-29 播放器超分、手势、章节预览与连播设置闭环

本轮继续保持唯一 `PlayerSession`、唯一 `:player` MPV runtime、现有 Surface lease 与
`PlayerSettingsStore`。播放器当前尚未发布，因此旧的单一“播放结束行为”会直接清理并替换为
队列语义明确的“自动播放下一集”和“队列播完行为”，不保留并行旧方案。参考
`D:\10_Project\mpv-android-anime4k` 的真实 MPV 功能与
`D:\10_Project\kiyori-android` 的系列识别交互，但不移植第二播放器、第二设置 owner、代理或
伪功能。

信息架构按使用频率和因果关系固定为：

1. **播放与连播**：默认倍速、记忆倍速、自动播放下一集、队列播完行为
2. **手势与进度**：双击手势、双击跳转时长、按钮跳转时长、精确进度定位、章节进度条、
   进度条缩略图预览
3. **画面与超分**：记忆超分模式、默认超分模式、解码器预设、GPU Next、Vulkan
4. **音频与字幕**：音量增强、字幕缩放
5. **在线与窗口**：在线播放缓存、全屏退出行为、切到后台时

实施步骤：

1. [DONE] 扩展 `PlayerSettings` 与持久化：
   - 新增“双击暂停/播放”与“左右双击快退/快进”两种手势模式及独立跳转时长
   - 新增章节节点显示和拖动缩略图预览开关
   - 新增自动播放下一集开关与队列播完后的停留、关闭、循环当前项行为
   - “记忆超分模式”开启后才允许选择默认模式；默认模式仍对应真实 Anime4K shader 文件
2. [DONE] 扩展唯一播放会话：
   - 会话保存真实播放队列、当前索引与前后项可用状态
   - 本地文件按同目录、同系列名和自然序生成队列；在线播放只有调用方明确提供队列时才连播
   - 上一集、下一集和自然播放结束都在同一 runtime 内切换媒体
3. [DONE] 扩展唯一 MPV runtime：
   - 从 `chapter-list` 读取章节标题与起始时间并随文件加载事件返回主进程
   - 使用当前 MPV 绑定的 `grabThumbnailFast` 提取最大 `320px` 的拖动预览帧
   - 缩略图使用单线程、时间分桶和仅保留最新请求的调度，结果按 runtime/load/request generation
     校验，避免旧媒体结果污染当前 UI
4. [DONE] 重建播放器控制交互：
   - 左下角“超分”按钮改为锚定弹窗；当前实现已收敛为关、A、B、C、A+、B+、C+ 七档，
     选择后立即应用
   - 双击手势按设置切换暂停/播放或左右跳转
   - 进度条绘制章节节点，显示当前章节名称；拖动时显示视频画面、时间与章节
   - 上一集/下一集按钮按真实队列状态启用
5. [DONE] 重构播放器设置页：
   - 使用五组标题、说明、现代化卡片与统一的选择弹层
   - 条件项保持可理解的禁用态：未开启记忆超分时默认模式不可操作；双击暂停模式下跳转时长不可操作
   - 每一项只连接 `PlayerSettingsStore` 或真实 MPV/session 能力
6. [DONE] 增加设置映射、系列自然排序、队列结束策略、章节定位、缩略图请求时序与 AIDL
   协议测试，并执行针对性 JVM/Python/Kotlin 检查
7. [DONE] 同步播放器语义与阶段文档，执行 formal readiness、`git diff --check`、
   `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`，核验最终 Debug APK
8. [PENDING] 目标设备验收超分实时切换、两种双击、章节节点/名称、拖动预览、本地系列前后项、
   队列播完行为、在线单项播放、横竖屏设置布局与性能；未操作设备前保持
   `verification_pending`

关键约束与失败检查：

- 缩略图提取不得阻塞 MPV 进度事件线程，不得让快速拖动积压无界请求；Binder 只传递受尺寸约束的
  Bitmap
- 媒体切换、runtime 重启、Surface 转移或退出后，旧章节和旧缩略图结果必须被 generation 丢弃
- `loop-file` 固定关闭，由 `PlayerSession` 处理自然 EOF；否则 MPV 自循环会吞掉“最后一集播完”
  事件
- 本地系列识别失败时只保留当前媒体，不跨目录、不把无关视频加入队列；在线播放不推断网页媒体顺序
- 设置页中的开关、文案、启用态和播放器实际行为必须共享同一设置值，禁止只改 UI

本地验证证据：

- `PlayerPolicyTest`、`PlayerRuntimeProtocolPolicyTest`、`KiyoriSettingsPagesTest` 与
  `PlayerControlsPolicyTest` 合计 `37/37` 通过，零失败、零错误、零跳过
- 播放器 Python 资源与原生依赖门禁 `6/6` 通过；`:app:compileDebugAndroidTestKotlin`、
  formal readiness 和 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 4m 2s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 16:21:32 +08:00`，大小 `474822245` 字节，SHA-256
  `B59594BEAE9F25BDF7F63EB8EA9C6DC7882DE2D7C4B77C8C8454C9464FE1C975`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、仅 `arm64-v8a`；播放器专用
  `libmp*.so` 与 `libplayer.so` 九个条目各存在一次。Android Debug v2 签名与
  `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未操作设备；超分实时切换、缩略图 JNI 性能、章节视觉、队列连播和横竖屏设置布局
  保持 `verification_pending`

## 2026-07-29 播放器控制层触摸、按钮与真实设置收口

用户使用 `2026-07-29 14:31 +08:00` 的 vivo V2507A / Android 16 诊断报告确认：同一
Debug APK 已经能够播放在线 MP4，`runtime=ACTIVE`、`paused=false`、Surface 和首帧均正常。
当前故障已经从在线播放链路收敛为全屏 Compose 控制层问题：本地或在线视频开始播放后，控制层自动
隐藏，单击视频区域无法重新显示。

定向源码审计确认根因与关联缺口：

- `PlayerGestureLayer` 把持续变化的 `state.positionSeconds` 放进两个 `pointerInput` key；播放进度
  更新会取消并重启正在识别的触摸协程，导致播放期间单击、双击和拖动都可能在手指抬起前失效
- 单击与拖动由两个并行 detector 持有，缺少一次手势只能由一个明确模式消费的合同
- 三秒自动隐藏只观察播放/暂停与加载，没有观察弹窗、进度拖动、全屏手势和日志 Dialog；按钮操作也
  不会统一重置计时
- 锁定后手势层被整体禁用，缺少 legacy 的“单击屏幕重新显示解锁按钮”行为
- 顶部和底部弹幕入口仍是活跃空回调；更多菜单除“查看日志”外均为空动作
- `PlayerSettingsStore` 已有精确 seek、网络缓存、字幕缩放、后台行为和结束行为等真实字段，但设置页
  没有完整暴露；悬浮播放器仍把显示用跳转步长固定为 `10`

本轮继续保持唯一 `PlayerSession`、唯一 `:player` MPV runtime、现有 Surface lease、浏览器下载 owner
和已验证的在线 Range/header 修复，不增加第二播放器、第二设置 owner、代理、回退或重载路径。

细化步骤：

1. [DONE] 用单一、稳定且不以播放进度为 key 的 pointer detector 重建单击、双击、水平 seek、
   左侧亮度和右侧音量；每次手势开始时读取最新 session state，播放进度更新不得取消当前触摸
2. [DONE] 建立控制层可见性策略：播放中三秒自动隐藏；暂停、加载、弹窗、日志、进度拖动或全屏
   手势期间不隐藏；任意真实按钮操作重置计时
3. [DONE] 锁定时只保留左右解锁按钮，三秒后隐藏；锁定状态单击视频区域重新显示解锁按钮，解锁后
   恢复完整控制层与自动隐藏计时
4. [DONE] 保留字幕、音轨、画面模式、倍速、播放暂停、前后跳、进度、Anime4K、旋转、截图、锁定、
   浏览器下载和日志的真实命令；弹幕在没有真实 owner 前显示为明确禁用态，更多菜单删除活跃空动作
5. [DONE] 播放器设置页删除无 owner 的静态伪设置，只展示并接通
   `PlayerSettingsStore` 的默认/记忆倍速、跳转步长、精确 seek、后台行为、全屏退出、结束行为、网络
   缓存、字幕缩放、解码器、GPU Next、Vulkan、记忆 Anime4K 与音量增强；初始化期设置明确标注下次
   启动播放器生效
6. [DONE] 悬浮播放器读取同一设置 owner 的跳转步长，避免 UI 文案和 `PlayerSession` 实际命令不一致
7. [DONE] 增加控制层策略、设置能力映射和静态触摸门禁测试，执行定向 JVM/Python 检查、Kotlin
   编译、formal readiness、`git diff --check` 与串行 Debug APK 构建核验
8. [PENDING] 目标设备验收本地/在线视频的隐藏后单击恢复、双击、滑动、按钮热区、弹窗计时、锁定、
   横竖屏与设置实时/下次启动生效语义

本地证据：

- 播放器与设置定向 JVM 回归 `58/58`，零失败、零错误、零跳过；播放器 Python 静态资源与触摸门禁
  `6/6` 通过
- `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、formal readiness 与
  `git diff --check` 通过；差异检查只有工作树既有的 CRLF -> LF 提示，没有 whitespace error
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 49s`，
  233 个任务零失败，末尾 `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 15:22:40 +08:00`，大小 `474822245` 字节，SHA-256
  `96726F63381FA7AEDF4AE212E162D3999BF057C2A3AB9EE1C0B6CA95B578788F`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、`arm64-v8a`，Android Debug v2 签名与
  `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未运行设备或模拟器；本轮真机触摸、视觉和设置生效验收保持
  `verification_pending`

## 2026-08-15 浏览器静态资源目录、资源嗅探与在线播放可靠性

状态：前一轮当前文档静态资源目录、图片缩略图/查看器、音视频资源嗅探、多证据媒体身份、在线播放
启动/seek/header/cache/Surface/VPN 诊断的本地实现、自动验证、正式门禁、Debug APK 构建和
静态产物审计已完成。`2026-08-16` 后续六份 vivo Android 16 报告确认 capability 修复和真实播放，
并暴露完整缓存错误依赖 `stream-start`、Surface 内部 seek 被误当成用户 seek、媒体身份需要在
`VIDEO_RECONFIG` 后刷新，以及独立完整缓存开关与普通缓存档位形成双 owner。最终优化设计已冻结，
代码、自动检查、新 Debug APK 和静态产物审计已完成；目标设备、真实站点、VPN 单变量矩阵和
native refresh 保持 `verification_pending`。继续使用
[`13_player_online_playback_reliability_and_compatibility.md`](kiyori_browser_product_completion/13_player_online_playback_reliability_and_compatibility.md)
作为设计与验收权威，不创建第二套 Browser Runtime、网络 owner、播放器、下载器或媒体数据库。

本轮闭环：

1. [DONE] 把“网络日志”改为当前 WebSession/文档的静态资源目录；按 document token 与规范化原始
   URL 聚合，fragment 不参与 identity、query 保留，重复请求累计次数且每行不显示时间；独立上限
   为 `2,000` 个资源 identity
2. [DONE] 分类 `VIDEO / AUDIO / IMAGE / WEB / SCRIPT / STYLE / DATA / FONT / OTHER`；图片使用
   当前捕获的有界 headers 显示 `52dp` 缩略图；该轮旧适配/缩放/平移查看器已由本文件顶部
   `2026-08-19` 的 edge-to-edge 全屏分页查看器替换
3. [DONE] 将“悬浮嗅探”和“视频资源”统一为“资源嗅探”；DOM observer 同时观察 video/audio，
   音频可识别和下载，但在没有音乐播放器 owner 时明确禁用播放
4. [DONE] 媒体身份按 DOM、response MIME、`Content-Disposition`、declared/Accept、URL suffix
   取证；`.mp3` 后缀被 `<video>` 使用时保持原始 URL/headers 进入唯一 `PlayerSession`，不改写、
   不额外探测；mpv 加载后记录真实 container、codec 和 video-track count
5. [DONE] 新安装默认使用 SOFTWARE + FAST、Anime4K OFF、BALANCED cache、普通 keyframe seek、
   关闭在线 thumbnail 第二连接；`MPV_EVENT_SEEK -> PLAYBACK_RESTART` 形成有序 seeking 生命周期，
   全屏和悬浮进度条拖动只在松手时提交一次 seek
6. [DONE] mpv header 只保留端到端身份字段；Range、Accept-Encoding、hop-by-hop 与 Chromium-only
   元数据由 FFmpeg/transport 持有；结构化识别 DNS、TCP refused/timeout、route、TLS 和 HTTP 错误
7. [DONE] `:player` 在唯一 runtime Handler 上被动观察默认网络，initial 与 `250 ms` 合并后的
   available/lost/capabilities/link-properties 变化只在脱敏事实变化时记录；每次 load 追加 request
   起点快照，导出报告追加主进程当时快照。active/effective transport、VPN、Private DNS、proxy、
   Data Saver、IPv4/IPv6 DNS/默认路由与 process binding 可见，不记录地址、名称或 network handle，
   不绕过 VPN、不绑定物理网络、不自动重试
8. [LOCAL DONE] 在线播放缓存收敛为唯一“省流模式 / 智能均衡 / 流畅优先 / 完整缓存”四档枚举，
   删除未发布的独立 `fullVideoCacheEnabled` UI、持久化和 runtime 字段。“完整缓存”内建
   256/128 MiB、300 秒基础缓冲，只对同一 mpv request 的有限、完整可 seek、大小已知
   HTTP/HTTPS direct VOD 激活会话磁盘 cache；资格只依赖正式属性，不再要求 `stream-start`；
   cache-state 证据、完成锁存、空间/文件上限与 verified session directory 清理已经闭环
9. [DONE] “拉伸画面”按当前有效 Surface width/height 计算比例，区分横屏、竖屏、悬浮和自由窗口；
   Surface resize/转挂不调用 `loadfile`
10. [LOCAL DONE] mpv 初始化后只严格读取一次 version/protocol/demuxer/decoder；hwdec 从
    `option-info/hwdec` Node map 读取可选 choices，投影固定目标三态、metadata evidence 与
    capability digest。合法缺失 choices 不阻断 runtime，结构异常有 warning；实际文件加载后继续
    记录 container/codec/video-track/hwdec/pixel format/codec profile。最终优化将 `no/none` 统一
    为软件解码事实，从选中 video track 读取 codec profile，并在 `VIDEO_RECONFIG` 后去重刷新身份
11. [LOCAL DONE] 保留固定 `2026-06-25` binding 的正式 VO/Surface 生命周期；主进程只把与显式
    用户 seek 命令对应的 `MPV_EVENT_SEEK` 投影为 UI seeking，Surface 重配置 seek 只进入诊断
12. [PENDING] 在目标 vivo Android 16 和至少一台其它 arm64 设备执行真实 VPN/无 VPN、Wi-Fi/蜂窝、
    direct MP4、HLS/DASH、seek、完整缓存、横竖屏、floating/fullscreen 与硬解矩阵
13. [DONE] 阶段 14 已完成 Mbed TLS `3.6.7` 安全刷新，并把播放器与主进程 FFmpegKit 两套
    进程隔离 closure 成对升级到 FFmpeg `n9.0.1`；本段阶段 13 的 mpv `2339eb727`、
    FFmpeg `n8.1.2`、Mbed TLS `3.6.6` 只保留为封板历史基线。Mbed TLS `4.2.0` 仍属于
    单独 major 迁移，不就地混入当前 AAR；FFmpegKit 当前进一步升级为 wrapper r3 与
    OpenH264 `v2.6.0`

本次最终优化本地验证证据：

- `ci.test.test_player_assets` 为 `6/6`；播放器策略/协议/设置定向 JVM 为 `61/61`；
  完整 `:app:testDebugUnitTest` 为 `1310/1310`，失败、错误和跳过均为 `0`
- `:app:compileDebugAndroidTestKotlin` 为 `BUILD SUCCESSFUL in 1m 33s`，`146` 个任务；
  项目 Python `ci/test` 全量 `186/186`
- formal readiness 为 `PASS`；architecture 为 `phase=m03 / errors=[]`；
  工作树 Markdown `306/306`、localization `7/7`、repo hygiene `45/45` 均为零错误
- 完整 App Lint 为 `4 errors / 29 warnings`；阶段 13 播放器文件没有 Lint 条目，剩余四个 error
  全部位于无当前 diff 的
  `WebSessionHistorySheet.kt:399/408/437/450`；未扩大 baseline、未增加 suppression
- `git diff --check` 无 whitespace error；真实报告 URL/query/附件标识反向扫描为 `0`
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 3m 54s`，
  `232 actionable tasks: 23 executed, 209 up-to-date`；
  独立 `:app:verifyDebugPlayerRuntimePackaging` 为 `BUILD SUCCESSFUL in 47s`
- 当前 Debug APK 生成于 `2026-08-16 18:13:41 +08:00`，大小 `471063551` bytes，
  SHA-256 `9A02093155A717EF5018CD6A41DE192A2497F810618F51D4F38DA8C14882CF2A`
- APK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，唯一 `MainActivity` launcher，
  Android Debug V2 单 signer，证书 SHA-256
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`，
  16 KiB ZIP 对齐通过
- APK 仅含 `arm64-v8a` 的 `51` 个 `.so`，无重复 basename；加
  `assets/operit_shell_exec` 共 `52` 个 ELF64/AArch64，`160` 个 `PT_LOAD` 最小均为
  `0x4000`，AAR/APK 播放器 native payload 差异为 `0`
- `libmpv.so` / `libplayer.so` 的 `256` 个版本化 FFmpeg 导入并集和 `99` 个 C++ 导入并集
  均缺失 `0`；44 个 DEX 包含新媒体身份 callback、四档完整缓存和 cache-state evidence
  descriptor；该阶段封板闭包为 mpv `2339eb727`、FFmpeg `n8.1.2`、Mbed TLS `3.6.6`

## 2026-08-16 播放器原生依赖升级与 closure 迁移

状态：`R4 LOCAL DONE / TARGET DEVICE VERIFICATION PENDING`。
最终产品目标已经冻结为两套互斥 native namespace 同时使用 FFmpeg `n9.0.1`：`:player` 使用
namespaced mpv closure，`:ffmpeg` 使用 normal-name FFmpegKit closure；主进程只通过非导出 Binder
runtime 使用 AI/工具箱、媒体预处理和浏览器下载合并能力。两套 M9 已完成源码构建、审计和 exact-hash
成对 promotion；r2 运行时隔离、本地应用回归、Debug APK 与独立静态审计保留为历史基线。
r3 产品 AAR、Python/JVM/native input、AndroidTest 编译、Debug APK 与独立静态审计均已通过，但新
目标设备现场证明所有进入真实 transcode 的命令仍会终止 `:ffmpeg` 进程；r3 只保留为本次归因基线，
不能再表述为运行时修复完成。当前产品已成对 promotion 到 FFmpegKit r4；source 为
`17101059` bytes / `DF332D8F2FECA7508541A2F20EDB348A3BFBC2AD5AE6EEFDEDF2889D679C96CC`，
thin/product 为
`30133322` bytes / `86D97CC0174FF44A8057899BEF7B8E66BD976E5CFA7BBA7D2A9FC819CB8EFCA7`。

- [DONE] 新增 `tools/player_native_build/closure_manifest.json`、源码构建器、source/thin 审计和
  exact-hash promotion 基础合同，固定 mpv `2339eb727`、Mbed TLS `3.6.7`、RSA-PSS enabled、
  curl disabled、arm64/API 24、NDK r29 与 `PT_LOAD >= 0x4000`
- [DONE] 完成 M8 安全刷新基线并曾选入当时的本地产品；source SHA-256 为
  `A13A3079BF9EC543C8C073C6B6D71D259C3002FA21E851DACD18E7E76254D006`，product SHA-256 为
  `9A73F2A9F06161FB967DE47D8EAFDE3269E78F844E18F8F557E532378640CC5E`。该闭包只保留为可归因
  安全刷新与构建链基线，不再是本阶段最终产品
- [DONE] 完成播放器 M9 `n9.0.1` 全 closure 源码构建和静态审计；source SHA-256 为
  `7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0`，thin SHA-256 为
  `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`
- [DONE] 在 WSL2/Ubuntu-22.04 中固定
  `ffmpegkit-maintained/ffmpeg@62b07bf097baf26b416c815aea514e05c9ad6d63`、FFmpeg
  `n9.0.1@bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`、NDK
  `29.0.14206865`，完整同步 FFmpeg 9 `fftools`、SAF protocol patch、scheduler、graph、
  textformat 和 resources，并保持现有 FFmpegKit Java/JNI、取消、统计和请求级多 session 语义；
  顶层 native execution 由单一 FIFO worker 持有
- [DONE] 把 FFmpegKit 9 的固定源码、完整外部库集合、构建脚本、source-lock、AAR member、
  ELF、符号、版本、SAF、JNI、RPATH/RUNPATH 与 16 KiB 审计合同固化到仓库
- [DONE] 先分别审计两套 M9 candidate，再以精确 SHA-256 同时更新
  `app/libs/mpv-player-arm64.aar` 与 `app/libs/ffmpeg-kit-player-arm64.aar`；Gradle 和 APK 必须拒绝
  FFmpeg 8 major、重复 basename、跨 namespace 依赖和混合 closure
- [DONE] 运行 Python/JVM/AndroidTest/native packaging/formal readiness/fresh clone，
  串行构建最终 Debug APK，并独立核验 AAR→APK 字节一致、ELF64/AArch64、`PT_LOAD >= 0x4000`、
  `RPATH/RUNPATH=0`、arm64-only、Debug V2 单签名和 `zipalign -P 16`
- [DONE] 包含 r2 运行时加固的历史 APK 为 `486199552` bytes，SHA-256
  `98406C1F5F5914A1091F7BF65E3063386589259D2D86A818AD479A725A93C08E`；规定构建为
  `BUILD SUCCESSFUL in 2m 43s`，`232` 个任务中 `30` 个 executed、`202` 个 up-to-date。
  APK 含 `44` 个 DEX、51 个 `.so` basename 零重复，加 shell launcher 共 52 个
  ELF64/AArch64，153 个 `PT_LOAD` 为 `0x4000 × 151` 与 `0x10000 × 2`。
  播放器 10 个与 FFmpegKit 9 个 native payload 均与产品 AAR 逐字节一致；两套 FFmpeg 均为
  `n9.0.1`，FFmpeg 8 markers/majors、normal/namespaced 交叉依赖和相关 ELF 的
  `RPATH/RUNPATH` 均为 0
- [PENDING] direct MP4、HLS/DASH、authenticated headers、redirect、seek/cache、Surface、
  MediaCodec、Anime4K、crash isolation 和目标设备矩阵

### 2026-08-17 FFmpegKit 卡死与崩溃扩散修复

- [DONE] 确认本轮开始时产品 AAR 是 wrapper `8.1.7-kiyori-n9.0.1-r3` / FFmpeg `n9.0.1` /
  OpenH264 `v2.6.0`，现场问题不是 FFmpeg major 或编码器缺失
- [DONE] 确认工具箱从 Compose 主调度器同步进入 `FFmpegKit.execute()`，这是界面卡死的确定性根因
- [DONE] 确认旧 normal-name FFmpegKit 由主进程直接加载；native fatal signal 会终止主界面
- [DONE] 记录证据边界：尚无原始命令、session output、logcat、tombstone 或 native backtrace，
  不把 `SIGSEGV`、OpenH264 唯一根因或 `0` 字节文件阶段写成已证实结论
- [DONE] 新增非导出 `:ffmpeg` Binder runtime，按 request/session 维护日志、statistics、
  取消与唯一终态；Binder death 显式失败全部 pending 请求，不重试当前命令
- [DONE] 迁移 AI 三个 FFmpeg 工具、工具箱、MNN/MediaPool、文件媒体信息和浏览器 M3U8 合并，
  并删除主进程直接 FFmpegKit/FFprobeKit/FFmpegKitConfig 调用
- [DONE] host ASan/UBSan 已确认 constrained-baseline profile `578` 与 OpenH264 `EProfileIdc` 不兼容，
  且 `v2.3.1` 的空 `BsFlush` 会执行 32 位移位；固定源码 patch 后 `20/20` 编码与回读通过
- [DONE] 完整重建 r2 source/thin AAR，修复 framework 子目录 `git apply` no-op 假阳性，按精确
  SHA-256 成对 promotion；产品 AAR 为 `30135977` bytes /
  `8FF6A8604FAE1AF0FB5DA160FF191F8D1CBAFE226630E2C04AAE3E6C5B34ED67`，现作为根因修复历史基线
- [DONE] 精确确认 OpenH264 `v2.6.0` 不包含 2026-07-10 的上游 BsFlush 修复，继续保留锁定 patch；
  r3 host ASan/UBSan 矩阵 `20/20 PASS`，全新 Ubuntu-22.04 workspace 的 source AAR 为
  `17101494` bytes / `48D7686C451363B2DCE936AC846D9A5F68CDF5BB5B96183143345A3CD16D5A7C`，
  thin/product AAR 为
  `30133939` bytes / `1E685258788D164209B2C5740F51A87E270E01A571E3428EA6BEE85EBCDF36F4`
- [DONE] 修复 FFmpegKit 构建器的 host `llvm-readelf` 路径合同：Windows 审计明确拒绝 WSL
  `/home/...` 二进制，并在任何 workspace 操作前失败
- [DONE] r3 最终规定构建为 `BUILD SUCCESSFUL in 2m 32s`，`232` 个任务中 `28` executed、
  `204` up-to-date；APK 为 `486200429` bytes /
  `A6B3D11B47BB8B9A87F0A0B696C5129845C434E959CDE7EF8A0B0B98A3D4DBFF`
- [DONE] 独立 APK 审计确认 Android Debug V2 单 signer、16 KiB zipalign、`44` DEX、`51` `.so`、
  `52` AArch64 ELF、`19/19` AAR→APK 字节一致、`153` 个 PT_LOAD 与选定 closure
  `RPATH/RUNPATH=0`
- [DONE] 完成 JVM/AndroidTest、architecture、formal readiness、串行 Debug APK 和独立
  DEX/ELF/AAR→APK/16 KiB 本地审计
- [DONE] 新现场确认 `-codecs` 与 FFprobe 成功，而 `-c copy`、一秒流复制、音频提取和 720p
  转码均在首包前终止独立进程。固定 FFmpeg 源码确认真实 transcode 在 API 35+ 调用
  `ABinderProcess_setThreadPoolMaxThreadCount(1)`；Android 应用 Binder threadpool 已经启动，
  AOSP 对缩小已启动线程池执行 fatal，完整吻合成功/失败边界、`0` 字节输出与 Binder death
- [DONE] 用户补充报告明确现场设备为 vivo V2507A / Android 16 / arm64；原始日志同时确认
  `-1414549496` 是 shell 管道损坏后的普通参数错误，不是进程死亡码。编译器身份中的
  `+pgo/+bolt/+lto/+mlgo` 也不能当成 FFmpeg 产品配置；不采用 Ubuntu 替代通道、核心回退或
  关闭优化规避 Binder 根因
- [DONE] r3 host scheduler sanitizer 夹具的流复制、AAC 提取和 OpenH264 缩放转码为 `3/3 PASS`；
  当前根因不再归到 scheduler TLS 复制本身。反向审查同时确认顶层 2–4 session 并发、嵌套 async
  executor 和终态前未排空 callback 会造成状态与诊断可靠性缺口
- [DONE] 构建 r4：FFmpeg Binder 初始化先查询 threadpool 是否已启动，已启动时保持应用既有
  Binder owner；`:ffmpeg` 顶层命令改为 FIFO 串行 owner，在专用 worker 内同步执行；内部线程
  `sessionId=0` 日志归属当前命令，并在终态前排空回调
- [DONE] 全新 r4 WSL closure、qualified source/thin AAR、exact-hash paired promotion、
  Python `46/46`、`verifyPlayerNativeInputs`、定向 JVM 与 AndroidTest Kotlin 编译通过
- [DONE] 新增目标设备 smoke matrix：纯 lavfi、生成 H.264/AAC 素材、实际解码、流复制、MP3 提取、
  720p OpenH264 转码和 FIFO started 顺序；本轮只完成 AndroidTest 编译，不操作设备
- [DONE] 新增
  [FFmpeg 架构与开发指南](../doc-src/dev-core/FFMPEG_ARCHITECTURE.md)，统一说明 Ubuntu、
  `:ffmpeg`、`:player` 三执行面、AI/内部调用、双 native closure、r3/r4/OpenH264、失败语义、
  验证矩阵和 Ubuntu `n9.0.1` 独立升级门禁
- [DONE] 其余文档/架构哈希同步、formal readiness、规定 Debug 构建、签名/16 KiB 对齐、
  product AAR closure 与独立 APK/ELF/AAR→APK 静态审计。r4 APK 为 `486200460` bytes /
  `14CBE55B2BF1FC11B769D9E14267F474E41C3EF40FC115210E7A7A0CB6CC28C6`；`44` DEX、`51` `.so`、
  `52` AArch64 ELF、`19/19` AAR→APK、`PT_LOAD = 0x4000 × 151 + 0x10000 × 2`、r4 wrapper/Binder
  patch marker 与相关 19 个 ELF 的 `RPATH/RUNPATH=0` 均已核对
- [PENDING] 在目标设备复测原始命令、取消、排队、进程死亡与用户可见错误路径；完成前保持
  `verification_pending`

权威设计与恢复入口为
[`14_player_native_dependency_upgrade.md`](kiyori_browser_product_completion/14_player_native_dependency_upgrade.md)；
不创建第二套播放器、第二个 runtime 或运行时双版本。

## 2026-07-29 播放器原生 HTTP/HTTPS 在线播放修复

### 第二份真机报告纠正与固定 Range 修复计划

用户安装上一版 APK 后，于 `2026-07-29 13:53 +08:00` 再次导出 vivo V2507A / Android 16
播放器报告。新证据证明上一轮只修复了 HTTPS 协议装载，不能表述为在线播放已经修复：

- `mpv 0.41`、FFmpeg `n8.1.2`、Mbed TLS、严格证书校验和固定 CA 包均已加载；
  `https://` 已进入 FFmpeg，Surface、GLES、VO 与 `:player` 进程保持正常
- 实际失败变为
  `Unexpected offset: expected 0, got 19890176`
- 报告中的公开视频当前总长为 `25260223` 字节；同一 URL 使用
  `Range: bytes=19890176-` 会返回
  `Content-Range: bytes 19890176-25260222/25260223`
- FFmpeg `n8.1.2` 的 HTTP 实现会先以内部 `off` 生成自己的 Range；若自定义 headers 已含
  `Range`，则不生成内部 Range，但响应 `Content-Range` 仍会更新实际偏移，最终在期望偏移与
  实际偏移不一致时显式失败
- 当前 `BrowserMediaCandidate` 会保存浏览器某一次网络请求的 `Range`，播放器又把候选的全部
  headers 固定写入 `http-header-fields`，因此浏览器分段位置错误地成为后续 mpv 探测、读取和 seek
  的静态请求头
- `mpv_event_to_node` 把 `END_FILE.reason` 输出为字符串 `error`，错误文本位于
  `file_error`；当前 engine 按整数 reason 和整数 `error` 读取，导致报告误写
  `reason=unknown error=none`

本轮修复边界：

1. [DONE] 以第二份真机报告、公网响应和 FFmpeg/mpv 源码确认固定 Range 是当前唯一可复现根因
2. [DONE] 在 MPV 网络请求边界建立纯策略：保留 URL、User-Agent、Referer、Origin、
   Cookie、Accept 及其他已观察请求头，但不把浏览器捕获的 `Range` 固定交给 mpv；Range 继续保存在
   candidate 中供下载和诊断使用，播放器传输偏移只由 mpv/FFmpeg 当前状态持有
3. [DONE] 日志只记录输入/转发 header 数量、header 名称和 `Range` 由 MPV 管理的决策，
   不记录任何 header 值
4. [DONE] 按 `mpv_event_to_node` 的真实 schema 读取 `END_FILE.reason` 与 `file_error`，
   确保加载失败进入 ERROR 日志和可见播放器错误
5. [DONE] 补充大小写不敏感的 Range 排除、其他请求头原样保留、END_FILE schema 和日志隐私测试，
   同步修正文档中“把 Range 原样交给 mpv”的错误表述
6. [DONE] 执行定向 JVM/Python 检查、formal readiness、差异反向审查和串行 Debug APK 构建
7. [PENDING] 目标设备复测本次 MP4、普通 HTTPS MP4、HLS、带 Referer/Cookie 的链接、首次加载和
   seek；真机成功前任务状态保持 `verification_pending`

当前定向证据：

- `PlayerPolicyTest` `17/17`、`PlayerRuntimeProtocolPolicyTest` `6/6`、
  `BrowserMediaCandidatePolicyTest` `16/16` 通过，合计 `39/39`，零失败、零错误、零跳过
- 播放器 Python 原生依赖与资源门禁 `20/20` 通过，AndroidTest Kotlin 编译和
  formal readiness 通过
- 主机 FFmpeg 在无自定义 Range 时成功读取该公开视频首帧；固定
  `Range: bytes=19890176-` 时立即拒绝输入，验证修复前后的协议差异
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 4m 18s`，233 个任务零失败，末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 14:22:10 +08:00`，大小 `474822245` 字节，SHA-256
  `969C20C2FC6E1A401CFF51812EC1936AB674ECF5B2F51D0A7AB1589FBB35A6A7`
- APK 为 `com.kiyori`、`45 / 0.1.0`、仅 `arm64-v8a`；53 个 native entry 中播放器
  10 个目标库各存在一次。Android Debug v2 签名与 `zipalign -c -P 16 -v 4` 通过
- 未安装 APK、未操作设备；目标设备在线播放仍为 `verification_pending`

用户于 `2026-07-29 13:06 +08:00` 导出的 vivo V2507A / Android 16 播放器诊断报告确认：
浏览器已把直接 HTTPS MP4 及八个请求头交给唯一 `PlayerSession`，`:player` 进程中的 Surface、GLES、
VO 和渲染尺寸均已正常建立，但 `libavformat.so` 返回 `No protocol handler found to open URL`。
二进制审计进一步确认，当前打包给 `libmpv.so` 的 FFmpegKit `n8.1.2` 明确禁用了 OpenSSL 且未启用
其他 TLS 后端；固定 mpv 输入自带的同版本 FFmpeg 则启用了静态 Mbed TLS，并且七个 ELF 均满足
`PT_LOAD >= 0x4000`。

本轮保持唯一 `PlayerSession`、唯一 `:player` MPV runtime、现有 Surface lease 和浏览器请求上下文。
主进程的 FFmpegKit 仍服务 FFmpeg 工具箱、媒体处理与浏览器下载合并；播放器使用的上游 FFmpeg
通过等长 SONAME / `DT_NEEDED` 命名空间隔离，禁止同名覆盖、Gradle `pickFirst`、应用层代理、
第二播放器或 URL 回退路径。

细化步骤：

1. [DONE] 审计诊断报告、两个固定输入 AAR、FFmpeg 编译配置、动态依赖、协议文本与 16 KB 对齐
2. [DONE] 扩展确定性 mpv AAR 转换：保留并等长改名上游七个 FFmpeg ELF，重写其
   SONAME / `DT_NEEDED`，让 `libmpv.so` 只解析隔离且启用 Mbed TLS 的 native 闭包
3. [DONE] 增强输入和 APK 门禁：核对命名空间互斥、旧依赖名清零、Mbed TLS / HTTPS 编译证据、
   唯一 C++ runtime、精确 native 清单和 16 KB ELF
4. [DONE] 初始化时复制固定 `cacert.pem` 并设置 `tls-ca-file`，保持 `tls-verify=yes`；
   禁用未随 APK 分发的 ytdl hook，直接媒体 URL 不再调用缺失的外部脚本
5. [DONE] 补充依赖转换、engine 选项和在线播放错误路径测试，更新 native 栈、构建、NOTICE、
   `CONTEXT.md` 与阶段 8/10/11 的权威说明
6. [DONE] 执行定向 Python/JVM 测试、Kotlin 编译、formal readiness、差异与隐私反向检查，
   最终串行构建并核验 Debug APK
7. [PENDING] 在目标设备复测 HTTPS MP4、HLS、带 Referer/Cookie 的链接、证书失败、重定向、
   网速/缓冲状态及日志复制导出

本地证据：

- 依赖转换测试 `14/14`、播放器资源门禁 `6/6`、`PlayerPolicyTest` 与
  `PlayerRuntimeProtocolPolicyTest` `20/20` 通过，零失败、零错误、零跳过
- `:app:verifyPlayerNativeInputs`、`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、
  formal readiness 和串行 `:app:assembleDebug` 通过；最终构建为 `BUILD SUCCESSFUL in 4m 18s`，
  233 个任务零失败，末尾 `:app:verifyDebugPlayerRuntimePackaging` 通过
- mpv AAR 为 `50543589` 字节、SHA-256
  `FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C`
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间
  `2026-07-29 14:22:10 +08:00`，大小 `474822245` 字节，SHA-256
  `969C20C2FC6E1A401CFF51812EC1936AB674ECF5B2F51D0A7AB1589FBB35A6A7`
- APK 仅 `arm64-v8a`；53 个 native basename 无重复，52 个 ELF 全部
  `PT_LOAD >= 0x4000`，唯一非 ELF 为既有 2 字节 `libsudo.so`
- `libmpv.so` / `libplayer.so` 对正常 `libav*.so` 的 `DT_NEEDED` 为零；256 个版本化 FFmpeg
  符号去重后由 `libmp*.so` 全部提供，缺失 0；99 个唯一 C++ 引用缺失 0
- `libmpformat.so` 包含 FFmpeg `n8.1.2`、`--enable-mbedtls`、Mbed TLS 3.6.6、
  `mbedtls_ssl_handshake` 和 HTTPS 证据；Android Debug v2 与 16 KB ZIP 对齐通过
- 未安装 APK、未运行设备或模拟器；真实在线播放仍为 `verification_pending`

## 2026-07-29 播放器日志弹窗真机反馈修正

用户在 `2026-07-29 12:39 +08:00` 的横屏真机截图中确认：顶部状态两行已经完整显示，但上行使用
粗体、下行使用常规字重；日志弹窗的等权筛选项发生换行，固定 `360dp` 日志区又把复制、导出、清空和
关闭操作挤出屏幕。本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为播放器唯一进度载体，不改变 `PlayerSession`、跨进程日志协议、脱敏规则或导出目录。

细化步骤：

1. [DONE] 统一网速/单位和电量/时间上下两行的 `fontSize`、`lineHeight` 与 `fontWeight`
2. [DONE] 将日志弹窗改为屏幕内固定比例布局，标题、分类、正文和操作区不再相互挤压
3. [DONE] 把分类改为可横向滑动且永不换行的单行选项条，覆盖全部、错误、警告及错误、
   网络与加载、播放控制、画面与 Surface、音轨与字幕、运行时
4. [DONE] 日志缓冲提供结构化条目和变更 revision；查看区使用最新在前的懒加载列表并实时更新，
   复制和导出继续生成时间正序的完整诊断报告
5. [DONE] 顶部固定关闭入口，底部固定清空、复制日志和导出文件；清空使用二次确认，
   导出进行中禁止重复触发
6. [DONE] 更新播放器语义文档和阶段 11，执行定向 JVM、播放器 Python 门禁、Kotlin 编译、
   formal readiness、`git diff --check` 与 Debug APK 构建核验
7. [PENDING] 目标设备上的横竖屏尺寸、分类横滑、实时更新、底部按钮可见性、复制和导出验收

本地证据：

- `PlayerPolicyTest` 与 `PlayerRuntimeProtocolPolicyTest` 共 `20/20` 通过，零失败、零错误、零跳过；
  播放器 Python 资源门禁 `6/6` 通过
- `:app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin` 通过；formal readiness、
  隐私反向扫描和 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 17s`，233 个任务零失败，构建末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 13:00:54 +08:00`，
  大小 `463725699` 字节，SHA-256
  `652ECA3B6A6DED8EEB16B77AF72C2F28BE6BC49C81701B0FDE3D13DE12C6C17E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名与
  `zipalign -c -P 16 4` 验证通过
- 未安装 APK、未运行设备或模拟器；本轮真机视觉与操作验收保持 `verification_pending`

## 2026-07-29 播放器顶部状态与诊断日志增强

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为播放器唯一进度载体，不创建第二套播放器、mpv core、网络请求或日志状态。目标是在保持顶部按钮
外部尺寸和位置不变的前提下修复全屏控制层，并让“查看日志”成为在线播放问题可直接导出的诊断入口。

细化步骤：

1. [DONE] 保持字幕、弹幕、音轨和画面模式四个按钮的外部尺寸与布局权重不变，只缩小内部
   padding，让描边图标从 `20dp` 增大到 `24dp`
2. [DONE] 删除全屏顶部状态列的固定裁切高度，显式设置两行文字的 `lineHeight`、单行和禁用换行，
   保证横屏、竖屏及系统字体缩放下显示网速单位和电量下方时间
3. [DONE] 把独立 `:player` 进程中的 runtime 命令、mpv 初始化、Surface、加载、文件事件和
   `MPVLib.LogObserver` verbose 日志通过现有 AIDL callback 汇入唯一 `PlayerDebugLogBuffer`
4. [DONE] 对诊断内容统一限制行数和单行长度，脱敏 Cookie、Authorization、请求头、URL 查询值及
   私人路径；在线播放保留协议、host、端口和路径结构用于定位
5. [DONE] 让查看日志提供“全部 / 警告+错误 / 仅错误”三级过滤；界面、复制与导出读取同一份
   当前过滤报告，导出文本写入 `Download/Kiyori/exports`
6. [DONE] 更新播放器语义文档和阶段 11，执行定向 JVM、播放器 Python 门禁、Kotlin 编译、
   formal readiness、`git diff --check` 与 Debug APK 构建核验
7. [PENDING] 目标设备上的横竖屏双行状态、图标视觉、在线播放错误日志完整性、复制和导出路径验收

本地证据：

- `PlayerPolicyTest` 与 `PlayerRuntimeProtocolPolicyTest` 共 `18/18` 通过，零失败、零错误、零跳过；
  播放器 Python 资源门禁 `6/6` 通过
- `:app:compileDebugKotlin` 与 `:app:compileDebugAndroidTestKotlin` 通过；formal readiness 和
  `git diff --check` 通过
- `:app:assembleDebug` 通过，233 个任务零失败，构建末尾
  `:app:verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-29 12:27:11 +08:00`，
  大小 `463725699` 字节，SHA-256
  `FDF25649812F22F2C4406D5334E5D84DC4117933B165A911BA4EE9DA6662E50F`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名与
  `zipalign -c -P 16 4` 验证通过
- 未安装 APK、未运行设备或模拟器；横竖屏视觉、在线播放日志完整性、复制和导出仍为
  `verification_pending`

## 2026-07-28 播放器进程崩溃隔离与诊断页规划

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为播放器唯一进度载体，不创建第二套 PlayerSession、mpv core 或崩溃页面。当前播放器、悬浮嗅探
和浏览器位于同一主进程；native fatal signal 会让 Browser Runtime 与播放器一起退出，现有
`GlobalExceptionHandler` 无法在进程已经死亡后启动 `:crash` 页面。

规划采用三个明确进程边界：

1. 主进程保留唯一 `PlayerSession`、Browser Runtime、PlayerActivity 和 Surface lease
2. 非导出的 `:player` 绑定服务只持有唯一 `MpvPlayerEngine`、媒体描述符和串行 MPV 调用线程
3. 现有 `:crash` 继续展示结构化报告，不在该进程创建 `PlayerSession`

后续实施分为结构化报告、AIDL runtime、Binder death 诊断闭环、清理与设备验收四个串行里程碑。
Surface attach/detach 必须等待带 generation 的 remote ACK；runtime 死亡不会自动 bind 或 load，
用户只能通过崩溃页明确请求重新启动播放器。报告持久化禁止保存 headers、Cookie、完整 URL 和私人路径。

完整设计、影响文件、风险、自动测试和真机步骤见
[`12_player_process_crash_isolation.md`](kiyori_browser_product_completion/12_player_process_crash_isolation.md)。
当前本地状态为 `[LOCAL DONE]`。里程碑 12.1、12.2 与 12.3 已完成：结构化崩溃报告、跨进程原子存储、
双模式 `CrashReportActivity`、唯一非导出 `:player` AIDL runtime、串行 MPV owner、事件推送和
Surface 两阶段 ACK、Binder death、退出证据、前后台报告展示和用户明确重启已接通。自动检查与
各里程碑 Debug APK 构建均通过。设备安装和崩溃注入未获授权，最终保持
`verification_pending`。

## 2026-07-28 浏览器视频嗅探抽屉优化

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器视频候选与播放器入口的唯一进度载体，不创建第二套 WebSession、播放器或下载状态。
播放器当前无法正常播放的问题不属于本轮范围；本轮只保证视频候选入口把原始请求交给现有
`PlayerSession`，并让手动播放直接进入既有横向全屏 `PlayerActivity`。

细化步骤：

1. [DONE] 拆分“搜索栏嗅探入口”和“自动悬浮播放”两个设置，删除旧的混合职责字段
2. [DONE] 为候选增加精确视频格式、被动时长、页面播放器状态与确定性推荐排序
3. [DONE] 只向浏览器 UI 投影可执行视频，排除音频、blob/MSE、MIME-only API 和媒体分片
4. [DONE] 重做视频资源抽屉：顶部双开关、横向动态格式选框、链接与时长、推荐标记和紧凑操作按钮
5. [DONE] 点击或长按结果打开“播放资源 / 下载资源 / 复制链接 / 查看链接”居中弹窗
6. [DONE] 手动播放固定进入横向全屏，自动悬浮播放继续使用同一 `PlayerSession`
7. [DONE] 更新语义文档，执行定向测试、formal readiness、`git diff --check` 与 Debug APK 构建核验
8. [PENDING] 目标设备上的字体、间距、横向筛选、点击/长按、开关独立性和横屏启动验收

本地证据：

- `BrowserMediaCandidatePolicyTest` 与 `KiyoriSettingsPagesTest` 共 `24/24` 通过，零失败、零错误、零跳过
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main` 通过
- `git diff --check` 通过；只报告工作树既有 CRLF 到 LF 提示
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 通过，含
  `verifyDebugPlayerRuntimePackaging`
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`463722319` 字节，SHA-256
  `5BA9E3AC56B7133DB44D8C08AC4257CEA221F393C7164DB4B6640A30B706ED21`
- APK 为 `com.kiyori`、`versionCode 45`、`versionName 0.1.0`、min 26/target 34，
  Android Debug V2 签名与 16 KB ZIP 对齐通过
- 本轮权威日期是 `2026-07-28`；构建环境文件时间记录为未来的
  `2026-07-29 02:40:30 +08:00`，仅作为本机时钟元数据，不改变任务日期
- 未安装 APK、未运行设备或模拟器；播放器仍无法实际播放视频，字体/间距、操作弹窗和横屏入口保持
  `verification_pending`

## 2026-07-28 浏览器悬浮球长按关闭交互

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器 presentation 的唯一进度载体，不创建第二套悬浮窗、浏览器状态或关闭流程。悬浮球关闭
动作必须与浏览器下拉抽屉第 4 行第 1 个“退出浏览器”按钮共用 `onExitBrowser`。

细化步骤：

1. [DONE] 单击悬浮球继续打开现有 Browser Home
2. [DONE] 长按悬浮球时消费本次进入动作，并在球体右上角外围显示透明背景的红色关闭叉号
3. [DONE] 长按松手后保留叉号 3 秒，超时自动隐藏
4. [DONE] 点击叉号调用既有 `onExitBrowser`，不复制 Browser Runtime 清理逻辑
5. [DONE] 补充纯逻辑状态测试，更新语义文档并执行 formal readiness、差异检查与 Debug APK 构建
6. [PENDING] 目标设备上的长按阈值、松手事件、叉号触控区域、拖动冲突和关闭结果验收

本地证据：

- indicator 状态、外围窗口 flags / 几何、background anchor 与 presentation release 定向 JVM 测试 `16/16`
- Kotlin 编译、formal readiness、`git diff --check` 与 `:app:assembleDebug` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`463722319` 字节，SHA-256
  `689A7EA123EC0107C7A29E8A82138BE6B383374EBC1365325A3F1816D6BFD463`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug V2 签名与 16 KB ZIP 对齐通过

### 右上角外围迭代

本轮在现有长按状态策略上继续迭代，不扩大常驻 `40dp` indicator 窗口，也不把透明触控区留在
其他应用上方。

1. [DONE] 删除球体内部叉号，把关闭动作改为同一 host 管理的透明 `28dp` 独立临时 overlay，
   只绘制 `16dp` 红色叉号
2. [DONE] 长按期间显示叉号但设置 `FLAG_NOT_TOUCHABLE`，松手后才允许点击
3. [DONE] 按球体右上角外围计算位置，拖动时同步移动，并约束叉号窗口不超出屏幕
4. [DONE] 隐藏 indicator、打开浏览器、下载确认、超时和关闭时统一移除临时 overlay
5. [DONE] 补充几何、flags 和状态测试，更新语义文档并重新生成核验 Debug APK
6. [PENDING] 目标设备上的外围视觉、长按不中断、松手可点击、屏幕边缘和拖动同步验收

## 2026-07-28 全屏搜索顶栏与三行输入细化

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为全屏搜索与浏览器顶栏的唯一进度载体，不创建平行 TODO 目录。实现继续复用同一个
`WebSessionBrowserSearchScreen`、Browser Runtime、搜索引擎 owner 和 Profile owner。

细化步骤：

1. [DONE] 缩小搜索历史垃圾桶，并锁定标题行高度，避免进入编辑态时标题上下移动
2. [DONE] 把复制链接、编辑链接图标稍微下移，使图标与下方文字更紧密
3. [DONE] 让全屏搜索页直接复用浏览器顶栏的左右边距、三槽间距、动作区和搜索框宽度
4. [DONE] 搜索框保持固定宽度和顶部位置，输入在 `1..3` 行内自动换行并平滑向下增高
5. [DONE] 超过三行后只允许输入内容纵向滚动，左右按钮和内部引擎按钮始终垂直居中
6. [DONE] 浏览器顶栏右侧固定为刷新动作，不切换叉号，并使用与无痕按钮一致的圆形点击反馈
7. [DONE] 更新语义文档，执行定向测试、formal readiness、差异检查与 Debug APK 构建核验
8. [PENDING] 目标设备上的多行输入、输入法、动画、触控反馈、旋转与浏览器刷新验收

## 2026-07-28 浏览器搜索引擎切换条与外部跳转提示清理

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器产品能力的唯一进度载体，不创建平行 TODO 目录。搜索引擎切换条严格参考
`D:\10_Project\kiyori-android` 的既有交互，并接入当前唯一 Browser Runtime。

细化步骤：

1. [DONE] 在浏览器顶栏下方增加可横向滑动的搜索引擎切换条，只在用户提交文本搜索后显示
2. [DONE] 点击引擎后使用同一搜索文本重新解析并跳转，当前引擎同步写入既有搜索设置 owner
3. [DONE] 提供右侧关闭按钮，并在地址导航、空输入或用户关闭后隐藏切换条
4. [DONE] 删除 `pendingExternalOpenRequest` 一次性确认状态及 Browser Home、悬浮提示中的对应 UI
5. [DONE] 保留“允许网页打开应用”作为唯一权限 owner：启用时仅显式主框架手势执行外部 Intent
6. [DONE] 把浏览器菜单第 4 行退出与设置按钮向内移动，收起按钮保持水平居中
7. [DONE] 更新浏览器语义文档，执行定向测试、formal readiness、差异检查与 Debug APK 构建核验
8. [PENDING] 目标设备上的引擎切换、外部 scheme、菜单位置与触控反馈验收

## 2026-07-28 全屏搜索页现代化完善

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器产品能力的唯一进度载体，不创建平行 TODO 目录。目标是参考
`D:\03_Default\图片\Kiyori\全屏搜索页` 完善共享全屏搜索页；Kiyori 尚未发布，旧的搜索页布局可直接
替换，不保留兼容开关或回退路径。

细化步骤：

1. [DONE] 接入九个真实搜索引擎图标，搜索框只显示图标与箭头
2. [DONE] 把引擎选择器改成淡蓝背景的覆盖式浮层，选项使用白底黑字和图标
3. [DONE] 把当前网页改成标题/网址双行，并保留复制链接与编辑链接两个纵向图文动作
4. [DONE] 把搜索历史改成自适应标签；垃圾桶进入删除模式，标签删除暂存到“完成”后提交
5. [DONE] 按浏览器下拉抽屉菜单基准大幅缩小全部图标、字体、卡片与上下间距
6. [DONE] 点击引擎面板周围收起；点击当前网页标题/网址区域返回现有网页
7. [DONE] 缩小历史标题与文字、放大垃圾桶；清空改为底部确认后立即执行，标签叉号仍由“完成”提交
8. [PENDING] 真机视觉、键盘、旋转、触控反馈和无痕 Profile 交互验收

实现与证据详见
[`2_software_home_and_fullscreen_search.md`](kiyori_browser_product_completion/2_software_home_and_fullscreen_search.md)。

## 2026-08-03 浏览器页面源码工作台

本轮继续使用 [`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为浏览器菜单能力的唯一进度载体。Kiyori 尚未发布，当前只读源码抽屉直接替换为浏览器宿主内
全屏原生源码工作台，不保留平行旧页面。实现深度参考
`D:\03_Default\图片\Kiyori\查看源码` 与 `D:\10_Project\hikerView`，但继续以当前唯一
`StandardBrowserSessionTools`、WebSession、WebView 和 `NativeCodeEditor` 为状态与能力边界。

细化步骤：

1. [DONE] 把页面源码状态绑定到捕获时的 session、URL 和 Document token，明确展示当前
   运行中 DOM、页面标题、host、字符数、行数和临时修改语义
2. [DONE] 读取源码时保留 doctype，排除 Kiyori 文本选择浮层等浏览器自有临时节点，并对空文档、
   超大文档、标签切换和导航后的失效状态给出明确结果
3. [DONE] 使用现有全屏 `NativeCodeEditor` 提供 HTML 语法着色、行号、补全、查找替换、
   撤销重做、格式化、复制、重新读取和输入法期间的单层符号栏
4. [DONE] 增加统一源码差异预览与显式应用确认；应用只写入捕获时的同一活动 Document，
   使用 `document.open/write/close` 重建当前文档，不创建新 WebView、session、历史项或持久网页副本
5. [DONE] 离开未应用编辑时提供继续编辑、保留编辑返回网页和丢弃三种明确选择；重新读取会先确认
   是否覆盖当前编辑，系统 Back、顶栏返回和浏览器 Back 共用同一状态机
6. [DONE] 增加 `browser_page_source` AI 工具，显式区分读取实时 DOM 与读取当前编辑草稿；
   大结果必须指定输出文件，AI 修改网页继续作用于同一个 Browser Runtime
7. [DONE] 更新 `CONTEXT.md`、README、浏览器菜单能力文档、AI 工具类型文档与中英文工具描述；
   不新增网络源码抓取器、第二编辑器、第二 Browser Runtime 或持久化网页副本
8. [DONE] 增加源码捕获、Document 校验、HTML 应用脚本、大小边界、差异摘要和 Back 路由
   定向测试，并以编译与静态检查核对 AI 工具接线；再执行 formal readiness、资源解析、差异检查和
   Debug APK 构建核验
9. [DONE] 修复压缩 HTML、内联脚本和超长属性产生的极宽横向画布：新增不改源码字符的视觉软换行，
   页面源码默认开启并可切换横向浏览；统一续行绘制、触摸、光标、选区、上下移动、补全锚点和滚动
   映射。顶栏精简为返回/标题/搜索/更多，紧凑工具条增加真实撤销重做状态、格式化、复制、跳转行、
   行列/选区和超长行统计，格式化与替换进入同一可撤销历史。进入横向浏览时强制从源码左上角开始，
   不再由超长行末尾的旧光标触发自动右移或下移
10. [PENDING] 目标设备验收状态栏、窄屏顶栏、输入法、长源码滚动、查找替换、应用后页面行为、
   刷新恢复、跨标签保护和 AI 读取；本轮不安装或操作设备

本轮本地证据：

- `BrowserPageSourceSupportTest` 11 项、`EditorVisualLayoutTest` 9 项与
  `WebSessionBrowserBackPolicyTest` 2 项合计 `22/22`，覆盖源码安全编码、捕获/应用结果、
  Document token、大小边界、普通/超大差异、长行统计、行跳转、字符簇软换行、十万字符连续布局和
  横向浏览进入时的左上角复位、返回软换行时的纵向上下文保留与 Back 顺序
- 完整 `:app:testDebugUnitTest` 为 141 个测试套件、`853/853`，零失败、零错误、零跳过；
  `:app:compileDebugKotlin` 与正式开发准备检查通过
- 七份实际 `strings.xml` 均可解析，44 个 `web_session_source_*` 键及占位符集合一致；
  旧只读源码 UI 和旧抽屉分支引用均为 0，AI 工具在注册、双语提示、执行器、JavaScript 包装和
  工具类型映射中接线完整，`git diff --check` 通过
- ARCH041 语义色消费者快照已登记源码工作台，两项对应架构契约测试通过。完整架构门禁仍报告
  当前 HEAD 已存在的 ARCH046 漂移：`UserscriptSourceExportHelper.kt` 已直接使用 `KiyoriPaths`，
  但 M-05E 固定消费者快照尚未登记；本轮未改动该既有 userscript 导出实现
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 1m 52s`，238 个任务、29 个执行、209 个 up-to-date，
  `verifySingleDebugLauncher` 与 `verifyDebugPlayerRuntimePackaging` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，宿主文件时间
  `2026-08-04 03:43:10 +08:00`，大小 `471581414` 字节，SHA-256
  `F2D7683FE6EE114B7171F635B58A0FB24EECF32DB1B0E445C6C2D4B5547FA97E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26、target 34、compile 37、仅 `arm64-v8a`；
  51 个 native 库无重复 basename，Android Debug V2 签名与
  `zipalign -c -P 16 -v 4` 均通过

## TODO 协作与归档约定

大型修改必须复用现有专项目录或创建职责明确的 TODO，并记录目标、非目标、范围、依赖、风险、
验收和当前状态。每个步骤应保持可独立审查，只在实现和相称验证完成后标记 `DONE`。

完成的专项在更新所有引用并经过项目约定确认后，移入 `docs/.META/legacy/TODO/`。详细命名、
链接、证据和状态要求见 [`docs/doc-src/before_docing.md`](../doc-src/before_docing.md)。

## 2026-07-28 浏览器 presentation 与播放器 Surface lease 正式实现

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为唯一项目进度载体，不创建平行计划目录。Kiyori 尚未发布，完整系统 overlay 浏览器方案在新架构完成后直接删除。

实施严格分为三个串行里程碑：

1. [DONE] 浏览器 presentation
   - `MainActivity`、Kiyori App Shell 和 Browser Home 成为唯一完整浏览器 UI
   - `WindowManager` 只保留同一活动 WebView 的 1×1 后台 attach anchor、最小 indicator 和简短提示
   - indicator 通过显式 action 打开现有 Browser Home
   - 人工 UI 与 AI `browser_*` 继续共用同一 session registry、`activeSessionId` 和 WebView
2. [DONE] 播放器 Surface lease
   - `PlayerSession` 增加 role、owner token、单调 generation、transfer phase、pending target 和一次性 Activity 请求
   - floating 与 fullscreen 必须先确认旧 Surface 销毁和 native detach，再连接新 Surface
   - `MpvPlayerEngine.attachSurface()` 不再隐式 detach，转挂期间不执行媒体重载
3. [DONE] 旧路径与文档收口
   - 删除 expanded overlay 的窗口、Back、cutout、IME、恢复状态和测试
   - 更新 `CONTEXT.md`、`README.md`、正式架构文档及浏览器阶段 1、阶段 11 和总 index
   - 执行静态反向检查、定向测试、Kotlin 编译、正式开发门禁、`git diff --check` 和最终 Debug APK 审计

每个里程碑必须完成独立 Debug 构建并核验 APK 后才能进入下一阶段。真机安装、ADB、MuMu、Release、提交和推送均不属于本轮授权。

当前证据：

- 里程碑 1：浏览器定向 JVM `46/46`，Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和 APK 审计通过；APK SHA-256 `84D7067F361FB9A582DE649DC787B50849AC5D122B6813D2264CF6704F3C7739`
- 里程碑 2：播放器定向 JVM `20/20`，Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 通过；APK SHA-256 `08A443EAB2ED4FE1F2ACE99E5107AF791EB064EF0C82D53EAA2C413855764BAE`
- 里程碑 3：浏览器与播放器综合 JVM `66/66`，Kotlin 编译、formal readiness、静态反向检查、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 通过；最终 APK SHA-256 `689A09C1DF4E23608A4E9E07A7B0E95645F9D5914E3CFB29BA0DFD1A34C2DBA0`

三个本地里程碑均已完成。vivo Android 16 同一网页、同一视频的人工/AI 共用浏览器与 floating/fullscreen 往返仍未执行，因此最终交付状态保持 `verification_pending`。

## 2026-07-28 浏览器 presentation 重复释放与跨窗口交接修复

首轮真机验收发现两条新的 presentation 生命周期故障：

- Browser Home 的显式退出已经完成 `APP_SHELL -> BACKGROUND_ANCHOR`，随后
  `DisposableEffect.onDispose` 再次释放同一 presentation；第二次后台 anchor 请求把已经挂在
  1×1 anchor 的 WebView 当成未挂载对象，触发 `Active WebView must be detached` 断言
- vivo Android 16 在同一时间窗口出现 RenderThread `fdsan` SIGABRT；现场 native 栈只能确认
  图形 Surface 分配路径崩溃，不能仅凭本地代码宣称根治

本轮修复门禁：

1. [DONE] presentation lease 与 host 两层重复 release 都必须幂等
2. [DONE] `APP_SHELL` 与 `BACKGROUND_ANCHOR` 跨 ViewRoot 转移必须先 detach、确认
   `parent == null`，跨过一个渲染帧后才创建或连接目标窗口
3. [DONE] Browser Home 活跃时不保留空的 background anchor 窗口；直接打开 Browser Home
   的入口不得先制造一次无意义的后台挂载
4. [DONE] 定向 JVM、Kotlin 编译、formal readiness、`git diff --check` 与 Debug APK 构建通过
5. [PENDING] vivo Android 16 使用同一网页复测退出、AI 往返、indicator 恢复和悬浮播放；设备
   通过前保持 `verification_pending`

本地证据：

- presentation release gate、background anchor policy 及关联浏览器状态测试合计 `51/51`，零失败、
  零错误、零跳过；测试任务包含 `:app:compileDebugKotlin`
- 静态反向检查确认旧预热入口 `0`、coordinator 提前创建 anchor `0`、严格
  `parent == null` 断言 `1`、显式跨帧 transfer plan 引用 `14`
- formal readiness 与 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务，零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 19:01:32 +08:00`，
  `455956807` 字节，SHA-256
  `8DD13C5D3AA81AEAF3102A341FAF485233FF50F1D14BC690C5A608FA3C96ABA6`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2；
  `zipalign -c -P 16 -v 4` 为 `Verification successful`

Java `IllegalStateException` 的重复释放路径已在状态机与调用层闭环。native `fdsan` tombstone
没有 Kiyori 或 mpv 业务帧，当前实现仅能确认已移除同一时机重复跨 ViewRoot 转挂及空 anchor
预热；目标设备复测前不宣称 native 崩溃已经根治。

## 2026-07-28 播放任意视频即闪退修复

`2026-07-28 19:18:21 +08:00` 的 vivo Android 16 tombstone 显示应用启动 12 秒后在首次视频
渲染期间再次触发 RenderThread `fdsan`。这次没有 Browser presentation 退出前提，必须重新检查
所有播放入口共用的 mpv 启动时序。

本轮门禁：

1. [DONE] 恢复实际打包 mpvlibAndroid 的生命周期：先 `MPVLib.create` 和
   `MPVLib.init`，只在有效 Surface 到达后执行 `attachSurface`
2. [DONE] 保留唯一 `PlayerSession` 和 Surface lease；媒体 request 可先解析，但
   `loadfile` 必须等待当前 lease 完成 native attach
3. [DONE] 增加自动检查，禁止再次在 `MpvPlayerEngine.initialize` 中 attach Surface 或设置
   `force-window=yes`
4. [DONE] 执行播放器定向测试、Kotlin 编译、formal readiness、`git diff --check` 和最终
   Debug APK 构建审计
5. [PENDING] 在原 vivo Android 16 上复测任意网络视频、浏览器悬浮播放和全屏播放；设备通过前
   保持 `verification_pending`

本地证据：

- `ci.test.test_player_assets`：`6/6`，新增启动顺序门禁通过
- `PlayerPolicyTest 12/12` 与 `PlayerSurfaceLeasePolicyTest 8/8`：合计 `20/20`，零失败、
  零错误、零跳过；任务包含 `:app:compileDebugKotlin`
- 静态反向检查：`initialize` 内 `MPVLib.attachSurface=0`、初始化前
  `force-window=yes=0`、`MPVLib.init=1`
- formal readiness 与 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务中
  27 executed / 206 up-to-date；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 19:39:17 +08:00`，
  `455956807` 字节，SHA-256
  `91634B63C7D5D6EDC5FF20FBBE76663F7850D8A446D52874B556353E1174FB9E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2，16 KB ZIP
  对齐通过

## 2026-07-28 hikerView 视频播放器设置页复刻与渲染配置扩展

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为唯一播放器进度载体，不创建平行 TODO 目录。参考图来自
`D:\10_Project\hikerView`；截图定义页面顺序、文案、分组和默认勾选状态，hikerView 源码用于核对
小窗、直接全屏、播放进度、重力感应和 M3U8 等行为语义。

实施门禁：

1. [DONE] 按截图完整呈现 `4/5/8/4/3/1` 六组共 25 个原始选项，保持原顺序、右侧摘要和
   勾选状态
2. [DONE] 追加解码器预设、GPU Next 渲染、Vulkan 渲染上下文、记忆超分模式、记忆播放倍速和
   音量增强六个选项
3. [DONE] 解码器预设复用 mpv 内置 `fast/default/high-quality/gpu-hq/low-latency/sw-fast`
   profile；GPU Next 与 Vulkan 只由唯一 mpv engine 在内核创建时消费
4. [DONE] 记忆超分、记忆倍速和音量增强由唯一 `PlayerSettingsStore` 持久化，并由唯一
   `PlayerSession` 或 `MpvPlayerEngine` 真实消费
5. [DONE] 当前 Kiyori 没有真实 owner 的 hikerView 原始选项沿用浏览器设置页既有契约，只显示
   静态状态或空动作，不新增第二播放器、第二设置 owner 或伪造成功
6. [DONE] 更新 `CONTEXT.md`、`README.md` 和播放器阶段文档，执行定向测试、Kotlin 编译、
   formal readiness、`git diff --check` 与最终 Debug APK 构建和产物核验

真机安装、ADB、MuMu、Release、提交和推送不属于本轮授权。

本地证据：

- `PlayerPolicyTest 13/13` 与 `KiyoriSettingsPagesTest 8/8`，合计 `21/21`，零失败、零错误、
  零跳过
- `ci.test.test_player_assets 6/6`、`:app:compileDebugKotlin`、formal readiness 与
  `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，233 个任务零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 20:26:27 +08:00`，
  大小 `459183137` 字节，SHA-256
  `8F2BD60B097A730C43221B286E0B6F4EB38ACA26AF2BE3E650FD13592B87F1A8`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名通过，
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- [PENDING] 目标设备上的页面逐项视觉、选择抽屉、profile 解码效果、GPU Next、Vulkan、
  Anime4K/倍速记忆和音量增强仍需真机验收

## 2026-08-11 全仓 Lint、测试与开发健康收口

状态：本地实现、全量回归、Lint baseline 交集裁剪、Debug APK 独立静态审计以及双仓提交推送
均已完成。Terminal 为 `d330366cfaff7ca73383b71504ec1a9a42e8a1c2`，父仓为
`02e97e95db8dbb161fab0d33604fdc228f9b87c0`；真机、Release 和远端 GitHub Actions
不属于本轮本地验证结果。

实施与验收顺序：

1. [DONE] 核对 `main`、`origin/main`、子模块、正式开发门禁、CI workflow、Gradle 模块以及
   WebChat、ToolPkg、Python、Android 测试和 Lint 入口，冻结本轮验证矩阵
2. [DONE] Python 门禁单元测试 `183/183`、architecture `phase=m03`、formal readiness 和
   Lint baseline 固定哈希检查通过
3. [DONE] WebChat typecheck/build、GitHub 示例包确定性构建和 ToolPkg 打包检查通过；最大
   WebChat JavaScript chunk 从 `582.56 kB` 降为 `358.81 kB`
4. [DONE] 完整 Android 回归在 `8m42s` 内通过；JVM 汇总为 `182 suites / 1061 tests`，零失败、
   零错误、零跳过，AndroidTest Kotlin/Java 编译通过
5. [DONE] App Lint 从 `68 errors / 63 warnings` 收口为 `0 errors / 2 warnings`；Terminal
   Lint 从 `155 errors / 64 warnings / 6 hints` 收口为 `0 errors / 1 warning`
6. [DONE] 正式 Lint baseline 只删除 `261` 条 stale 记录，保留 `5306` 条已审阅记录，两条
   current-only 依赖提示没有进入 baseline；最终 SHA-256 为
   `9ECD07D023005A6732F2F56F110675EC6AC64BFEC8C64107E8780B7C42396FE5`
7. [DONE] `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 在 `2m27s` 内通过；
   APK 为 `464566245` bytes，SHA-256
   `35FA09189BA93D602164539144EC2D1F8FB296944ED78D010524EC0217A857C5`
8. [DONE] 独立产物审计确认 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`、唯一
   `MainActivity` launcher、Debug V2 单签名和 16 KB ZIP 对齐；APK 仅含 `arm64-v8a`，
   51 个 `.so` 无重复 basename，加上 `assets/operit_shell_exec` 共 52 个 AArch64 ELF64，
   所有 `PT_LOAD >= 0x4000`
9. [DONE] 审计最终差异、暂存树、敏感内容、构建产物、异常大文件、Git mode、重解析点、
   子模块和远端竞争状态；依次提交推送 Terminal 与父仓库，本地、tracking 与远端 ref 均完成对账

本轮保留的诊断均有明确工程边界：

- App 的 `lifecycle-runtime-compose 2.7.0 -> 2.11.0` 涉及 Lifecycle 组件族的大跨度策略升级，
  不在单条依赖提示下冒进
- App 的 `bcprov-jdk18on 1.85 -> 1.85.2` 不能脱离同一版本 owner 管理的 BouncyCastle
  组件族单独升级；当前仓库配置无法解析配套 `bcpkix-jdk18on:1.85.2`
- Terminal 的 `ChromeOsAbiSupport` 与 APK 明确的 `arm64-v8a` native 产品合同一致，不伪造
  不存在的 x86_64 native 制品

本轮未执行 Release、Nightly、Clone、AAB、发布、部署、设备安装、ADB、MuMu、真机交互或远端
GitHub Actions。最终提交、推送和远端 ref 状态以本次 Git 交付核验为准。

## 2026-08-28 设置播放器与法律中心增量

状态：`LOCAL IMPLEMENTATION VERIFIED / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

本增量按 Kiyori 未发布版本处理，不保留旧的合并法律设置入口作为当前 More Features UI。目标是让
播放器默认选择、更多功能顺序、开源清单和两份中文法律文档都有一个清晰的状态与导航 owner。

### 实施合同

- `PlayerSettingsStore` 新增 `PlayerDefaultVideoPlayer`，默认 `KIYORI`；`SYSTEM` 仅处理外部视频
  `ACTION_VIEW`，通过 Android 外部视频活动打开并排除 Kiyori 自身，浏览器候选、历史播放和队列不变。
- “设置 → 更多功能”从上到下固定为“权限 / 网络代理 / 开源协议 / 用户协议 / 隐私政策”，分别进入
  `PERMISSIONS / NETWORK_PROXY / OPEN_SOURCE_LICENSES / USER_AGREEMENT / PRIVACY_POLICY`。
- 开源协议页使用设置页折叠标题、分类分组、搜索、许可证正文和项目地址动作，目录覆盖当前直接
  Gradle 依赖、Kiyori 原生模块、播放器/代理/AI 运行时和随包组件；清单使用独立版本基线，结构测试
  校验名称、说明、许可证、HTTP(S) 地址、分类覆盖、项目名唯一性，并解析
  `tools/ffmpegkit_native_build/source_lock.json` 对账每个固定 native source ID。许可证正文仍以各组件
  随附文件为准。
- 用户协议和隐私政策改为独立直达的只读页面，统一显示当前版本 `2026-08-28-r1`、摘要和排版后的
  中文正文；Settings 直达页使用共享折叠标题和正文分组卡，首次启动同意仍复用同一
  `AgreementPreferences` owner。

### 当前验证

- 播放器策略、设置页和开源目录三个定向 JVM 套件为 `56/56`，其中开源目录测试会解析并逐项对账
  FFmpegKit source lock；`check_formal_readiness.py --require-main` 与 `git diff --check` 通过。
- 规定的 `:app:assembleDebug --no-daemon --console=plain` 最终为 `BUILD SUCCESSFUL in 1m 47s`，`235` 个任务中
  `23` 个 executed、`212` 个 up-to-date；唯一 launcher、脚本代理 runtime 与播放器 runtime packaging
  门禁均通过。播放器和 FFmpegKit AAR SHA-256 分别为
  `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4` 与
  `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394`。
- `app/build/outputs/apk/debug/app-debug.apk` 最终写入时间为 `2026-08-28 15:25:29 +08:00`，大小
  `503676817` bytes，SHA-256 为
  `660135CD28282B5C6317862C58BD6EF7371DDB17ABF0D079A48E546F605755C7`。包名、版本、min/target/compile
  SDK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`；Android Debug V2 单 signer 和
  `zipalign -c -P 16 -v 4` 通过。APK 只含 `arm64-v8a` 的 `53` 个 `.so`，无重复 basename；这 `53`
  个 native library 加 `assets/operit_shell_exec` 共 `54/54` 个 ELF64/AArch64，`161` 个 `PT_LOAD` 为
  `0x4000 × 159` 与 `0x10000 × 2`，shell launcher 不依赖 `libc++_shared.so`。
- 本轮未安装或操作真机，More Features 点击与 Back 链、系统默认播放器直接分流或选择器、content URI
  权限传递、无外部播放器错误提示、协议长滚动与文本选择、开源搜索与外链、浅色/深色、窄屏/横屏和
  系统安全区仍保持 `verification_pending`。

### 首次启动同步优化

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / DEVICE VERIFICATION PENDING`。

本增量继续复用上述 `2026-08-28-r1` 法律正文和 `AgreementPreferences` 同意状态，不复制第二份启动页
协议。实现范围为：补齐协议重确认与插件加载浮层的 `safeDrawing`；让首启协议正文同步显示摘要、
版本与完整正文层级；正文版本由唯一常量格式化；首启权限页改为与 Settings 相同的“应用权限 / 系统
访问 / 高级设备能力”三组和同一状态摘要；删除会串联电话、短信、无障碍、Shizuku、Root 等高影响
能力的全局全选，只处理用户逐项选择的未授权项目；把没有 `READ_MEDIA_IMAGES` 的媒体能力准确写为
视频与音频，照片继续通过系统文件选择入口按次处理。

最终 onboarding/agreement/startup gate/Settings 六个 JVM 套件 `51/51`、首启 ARCH037 正反向
`7/7`、完整 architecture `PASS (phase=m03)`、formal readiness、工作树 18 份 Markdown 链接及差异
检查均通过。串行 Debug 构建和最终 APK 数据见上一节；APK 的 `5512` 个文件、`44` 个 DEX、仅
`arm64-v8a` 的 `53` 个 `.so`、零重复 native basename、`54/54` 个 ELF64/AArch64 以及所有
`PT_LOAD >= 0x4000` 均已独立核验。未安装或操作设备，因此六页视觉、状态栏、长文滚动、授权
拒绝/部分授权、系统页返回、旋转、进程恢复和 OEM 入口仍需真机验收。
