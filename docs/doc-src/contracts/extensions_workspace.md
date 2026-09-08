# 扩展、工具与工作区契约

本页覆盖 registry 生命周期、宿主接口与工作区边界。开发 API 见 [脚本指南](../../SCRIPT_DEV_GUIDE.md)、[ToolPkg 格式](../../TOOLPKG_FORMAT_GUIDE.md) 和 [宿主类型入口](../package-dev/index.md)。

## Registry 与执行上下文

- `PackageManager` 使用 `PackageScanPublicationGate` 与 `initLock`；异步扫描入队前登记 generation，扫描只返回局部缓存和快照，代际检查、缓存、asset snapshot、registry 与 runtime 更新在同一发布锁内完成。
- 主动清缓存推进 generation，晚到扫描不能覆盖较新请求或通知 listener；只有一个 PackageManager 与 ToolPkg registry。
- `ToolPkgManager.clear()` 清空容器/子包索引，在 `executionEngineLock` 内摘除全部 JsEngine 上下文，再逐个销毁；manager 保持可用，后续获取创建新实例。
- `SkillManager` 使用一把 `mutationLock` 串行扫描、删除、导入和读取，局部构造后一次性发布不可变快照；技能与错误必须来自同一快照。
- JavaScript 模块工厂把宿主别名置于外层词法域，每个 CommonJS 源码单独函数作用域；压缩局部名 `_`、`Tools`、`console` 不被前导代码覆盖。入口与相对 require 共用工厂，源码 strict-mode 仍适用。

## MCP

- `MCPManager` 原子持有每服务配置 generation、连接 client 与最后失败；单服务串行连接，bridge I/O 不占注册锁。
- 更新、注销、shutdown 使待发布连接失效；shutdown 清连接和失败但保留已注册配置。一次获取最多尝试连接一次，取消和线程中断继续传播。
- `MCPBridgeClient.callTool` 每命令最多提交一次。超时、连接错误、无响应或异常不能证明服务没执行；后续明确调用可重新连接，首个响应不被替换。
- `disconnect()` 只清本地 connected 标记，不停止服务或关闭共享 bridge socket；ping 失败清连接标记。
- `MCPBridge` 为普通命令持有一条串行连接，spawn 使用短连接。资源先发布再 connect，失败释放；取消关闭该 socket，阻止晚到响应污染下一命令。
- 线协议保持 newline-delimited JSON 与既有 host/port/keepalive。日志不记录参数值和响应正文。
- `MCPToolParameter` 仅对文本输入应用 schema 转换；结构化 JSON 保留解析后的真实类型和 null 位置，不重新解释 quoted string。默认 ToolExecutor 流在 collect 时才 invoke，提前取消没有工具副作用。

## 工作区

- `core.workspace.WorkspaceConfig` / `WorkspaceConfigReader` 持有 `.operit/config.json` 与默认网页配置；UI 编辑，核心服务消费，不反向导入 UI。
- `WorkspaceDownloadDispatcher` 接收 typed 网络或 inline 请求，Browser adapter 委托同一 BrowserDownloadManager，不增加格式或 transport 所有者。
- `WorkspaceRuleFileReader` 使用既有 `read_file_full` 按约定查找根 AGENT.md/AGENTS.md；系统提示词通过核心能力消费。
- `WorkspaceChangeTracker`、`DepthLimitedFileObserver`、`GitIgnoreFilter`、`WorkspaceAttachmentProcessor` 持有观察、过滤和附件格式化，UI 只保留展示与备份编排。

## 内置脚本与市场

- 生产脚本和 ToolPkg metadata 提供双语名称、动作导向描述、明确分类及默认启用字段；声明环境变量的包首次安装默认关闭。
- 当前未发布内置包版本基线为 `1.0.0`；Search 名称统一为“OpenAI 搜索”“Brave 搜索”，协议 ID 保持稳定。
- 学术工具分类为 `Academic`，包含 arXiv、Crossref、PubMed、Semantic Scholar、OpenAlex；名称和 UI 分类不改宿主类型或接口。
- 市场 identity 属于 `com.kiyori.capability.extensions.market`，API、发布描述与安装匹配共用归一化和独立 ID 验证，UI 不持有这些规则。
- GitHub Release 资产是已经发布的独立产物；后续市场登记失败报告 `RegistrationFailed`，不能删除 Release 或资产。
- “扩展”的四页签和各自顶栏操作复用完整 PackageManagerSnapshot，安装成功发布 catalog revision；保留页面刷新，重建页面读取当前快照。

## Windows 工具包

- `com.kiyori.windows_bundle` / `windows_control` 保持稳定标识；手机 Compose DSL 和工具执行共用连接地址规范化。
- 地址与令牌由现有 `EnvPreferences` 持有，`setEnvs` 在同一 SharedPreferences 编辑器中发布关联字段；不会清空其他包变量。
- PC Agent 的执行端口与本机管理端口隔离，仍共用一个配置、文件与进程会话所有者。FRP 只映射执行端口。
- 监听配置由进程内唯一生命周期所有者应用，管理端口和 PTY 不重启；失败明确恢复旧监听。无效启动配置只开放本机管理，不启用替代执行地址。
- 当前网络候选由同一模块提供，代理/TUN 与虚拟网卡不进入自动 LAN 推荐；复制配置前重新核对运行监听、配置和令牌。
- 手机只有收到专用认证探测的明确成功才显示已连接；HTTPS 使用协议默认端口，支持反向代理路径前缀。
- Windows 请求禁用重定向、共享 Cookie 和连接恢复重试。传输丢失报告未知提交状态，不自动重做文件或命令操作。
- 文件移动/复制拒绝覆盖；写入先完成临时文件再发布，Windows 已有文件使用保留 ACL 的系统替换。
- 具体接口、安装、局域网/FRP 配置、限制与升级见 [PC Agent 指南](../../../examples/windows_control/resources/pc_agent/kiyori-pc-agent/README.md)。

## OpenAI 搜索

- `openai_web_search:openai_search` 是独立托管搜索工具；配置唯一来自 package-scoped `PACKAGE + HOST_SERVICE` 的 `ToolPkgHostEnvironmentRepository`，不跟随当前对话模型。
- 宿主持有 Key、认证、请求、解析与结果卡；JavaScript 不读取 Key，也不能提交任意 package ID 或变量名。
- `OpenAIHostedWebSearchRequestLifecycle` 持有提交与原子终态；`AdmissionController` 持有进程 FIFO 并发/RPM，默认排队 60s、请求 300s、connect 上限 30s、write 上限 60s。
- 搜索只发一次非流式 Responses POST；不自动重试、重定向、切模型/端点/Key、轮询或续流。计费探测只由设置页的明确动作发起。
- schema revision 7 保留 `url_citations_and_action_sources / url_citations / action_sources / structured_feeds`。原生 URL annotation 是引用区间依据，不能从回答文本猜 URL。
- 完整 ToolResult 用于 UI，主模型投影省略未引用来源、动作、query/usage 的重复大载荷；XML-safe JSON 保持传输边界。
- 探测证据绑定精确 fingerprint，含 credential 不可逆摘要，最多 16 项；持久化成功后才回调，配置变化需要新的证据。有效 URL 引用探测与时间/天气 structured feed 分开。
- 详细字段、来源与验证见 [OpenAI Hosted Web Search](../architecture/openai_hosted_web_search.md)。

## Bilibili 工具包

- `com.kiyori.bilibili_toolkit` 内的 Media 子包为 `bilibili`，默认关闭，16 项工具共用宿主私密 `BILIBILI_COOKIE`；JavaScript 不接触 Cookie。
- GET-only bridge 验证 URL 和每次重定向；nav `-101` 只在明确未登录且 WBI 图像 key 有效时接受，其他认证失败保留。
- API 仅对 socket timeout 和 500/502/503/504 最多重试两次；下载以 GET Range 验证范围与字节数，同 URL/headers/route 最多三次尝试，403/412/429、证书错误与取消不重试。
- API 与 CDN 使用匹配的桌面 Web 请求身份，媒体不带 Cookie；诊断保留 endpoint/phase/attempt/category，不记录 query 签名或原异常中的敏感数据。
- capture 复用一个视频上下文，逐步报告，失败仍保留非空 message 与 data；既有输出需要 `overwrite=true`。媒体后处理只用现有 Files/FFmpeg，staging 完成后发布。
- protobuf 弹幕最多 50 段，XML 是显式另一来源；两者都不代表完整历史。`user.full=true` 保留 user 并提供上游 profile，不合成缺失字段；`media=false` 不创建或删除媒体目录。
- 用户步骤与设备验收见 [Bilibili 专项](../../TODO/bilibili_toolkit/index.md)。

## 应用内 AI 动作授权

下表描述 Kiyori 产品的权限系统，不构成开发 Agent 的授权。

| 风险级别 | 操作边界 |
| --- | --- |
| R0 只读 | 不改变状态，通过工具门禁后不需要操作确认 |
| R1 低影响 | 本地、有界、易撤销；可由持久工具授权允许 |
| R2 高影响 | 持久、多对象、敏感或准备外部影响；默认一次确认，可对固定目标与范围显式会话授权 |
| R3 关键 | 难以撤销、特权、财务、账户或不可收回的外部动作；逐次确认，不允许会话/持久绕过 |

`FORBID / ASK / ALLOW` 决定工具是否可调用；具体命令的 R0–R3 决定是否可产生副作用。`ALLOW` 不绕过 R2/R3；同一动作的 ASK 与操作确认合并为一次决定。
