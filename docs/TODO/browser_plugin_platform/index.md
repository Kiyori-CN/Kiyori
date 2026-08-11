---
status: phase_1_userscript_ui_runtime_diagnostics
design: ../../doc-src/architecture/browser_plugin_platform.md
baseline_branch: main
baseline_head: 0965a6ac418a351a6527b1cdf1201736b95e2999
last_updated: 2026-07-31
---

# 浏览器插件平台实施计划

## 1. 目标

在浏览器下拉抽屉“插件”入口内完成统一 Browser Plugin Center，并逐步交付：

- 内置油猴脚本插件的完整脚本管理
- Kiyori `.kbx` 原生浏览器扩展
- 安全隔离的 content script 与 background worker
- 安装、更新、权限、诊断、历史版本和插件库
- AI 创建、修改、测试和请求安装插件
- 经过真实验收的 WebExtension 兼容

正式架构见
[浏览器插件平台与插件中心架构](../../doc-src/architecture/browser_plugin_platform.md)。

## 2. 当前基线

- 分支：`main`
- HEAD：`0965a6ac418a351a6527b1cdf1201736b95e2999`
- 工作树：开始本轮前干净，Browser Plugin Center 与 Phase 0 已位于 `main`
- 当前 Browser Runtime：`StandardBrowserSessionTools`
- 当前 userscript owner：`UserscriptRepository` 与 `WebSessionUserscriptManager`
- 当前 AndroidX WebKit：`1.16.0`
- Phase 0 状态：运行时安全、用户脚本总授权、`unsafeWindow` 页面对象桥、定向测试、readiness 和
  Debug APK 已本地验证，设备验收待执行
- userscript 总授权：已实现，新安装默认开启，由现有 userscript registry 唯一持有
- `unsafeWindow + privileged grants`：本地策略、静态安全和真实 Chrome 双世界行为已验证，
  2026-07-31 vivo Android 16 首轮设备报告出现 WebView 主进程 SIGSEGV；已完成单 WebView 单隔离世界、
  固定注册和匹配语义修复，修复版设备验收待执行
- 当前设计状态：详细设计已定案，用户于 2026-07-31 确认开始串行实施
- 提交、推送、发布、部署、设备安装：未授权

## 2.1 已确认的本轮产品决策

- 插件中心与油猴管理页统一使用现有浏览器子抽屉的 `52dp` 标题栏、`40dp` 搜索框、`36dp`
  标签和中性表面
- 当前页 userscript 菜单按脚本 ID 分组并默认收起；搜索命中子命令时只展开搜索投影，不改变持久展开状态
- 编辑器从插件抽屉进入浏览器内全屏子页面，复用现有 `NativeCodeEditor`，不新增 WebView 编辑器
- 沉浸式翻译先作为 userscript 兼容样本，完整浏览器扩展形态留到 WebExtension 阶段
- iTab 方向先定义 `.kbx` `NewTabProvider` 能力；官方 Chrome 扩展包直接兼容不进入当前阶段
- Phase 0 真机门禁继续保持待验证，但不阻塞彼此隔离的 Provider、UI、私有存储和编辑器本地实施；
  未完成设备验收前不扩大 userscript 注入运行时

## 2.2 二次深度收口计划（2026-07-31）

本轮先完成只读审计和设计门禁，再修改代码。已确认的实现边界如下：

- 编辑器全屏覆盖层的背景可以延伸到系统栏，但内容列必须使用 `statusBarsPadding()`；
  顶栏固定 44dp 操作几何，格式化、Metadata 和差异进入更多菜单
- 编辑器底部复用浏览器底栏的 44dp 动作尺寸、底部导航 Insets 和中性表面；输入法出现时
  隐藏校验/差异/应用栏，仅保留 36dp 符号工具栏，避免底部多层叠加
- 状态模型区分无活动页面、已匹配、待运行、运行中、已运行、异常和未命中；未命中必须由
  统一匹配诊断给出实际排除规则或未命中原因
- 运行时、插件中心和已安装列表复用同一匹配结果；活动页面 URL 由 manager 发布到 UI，
  页面世界 bootstrap 也要记录已匹配状态，禁止用缺失 map 项猜测未命中
- 匹配规则补齐常见 `http*`、显式端口、`.example.com`/`*.example.com` 主机形式和正则
  flags；`@connect` 普通域名遵循域名及其子域的权限范围，继续保留 `*`/`self` 明确语义
- 所有脚本都可读取本地只读 `GM_info / GM.info`；`@grant none` 不暴露 native/GM 特权 API；详情、安装预览和更新确认按执行世界、grant、connect、
  页面规则、未知 grant 和阻塞原因分组显示
- 概览“添加”按钮只展示新建、URL 安装、本地导入等已有真实回调；URL 输入在 scheme 不为
  `http/https` 或为空时不能提交，Metadata 结构化编辑错误必须可见
- 本轮不扩大到 `.kbx`、WebExtension、`@run-in`、`@sandbox`、`@unwrap` 的伪兼容实现，
  也不创建第二运行时、第二仓库或回退执行链

实施顺序：

1. [DONE] 只读审计系统 Insets、浏览器底栏、插件 UI、编辑器和运行时状态链
2. [DONE] 形成匹配/权限/按钮/编辑器设计门禁并记录任务日记
3. [DONE] 先修改核心模型与匹配诊断，再接通 manager/UI 状态发布
4. [DONE] 修改插件中心、详情、更新/安装弹窗和编辑器布局
5. [DONE] 增加定向测试、执行正式门禁并构建 Debug APK
6. [PENDING] 真实 Android WebView、输入法和目标脚本验收

## 2.3 真实脚本异常与浏览器设置收口（2026-07-31）

设备截图和 Greasy Fork `539514` 当前源码证明，“轻小说文库+”已经匹配
`http*://*.wenku8.com/.net/.cc/*` 并进入执行，当前失败点是 `GM_info.script`：
runtime 只在显式声明 `GM_info` grant 或 `@grant none` 时创建信息对象，而该脚本与常见
userscript 一样直接读取隐式可用的 `GM_info`。

本轮顺序：

1. [DONE] 获取真实 metadata、API 使用集合和设备堆栈，区分匹配失败与执行异常
2. [DONE] 将 `GM_info / GM.info` 收口为所有脚本可读的同一信息对象，不增加 host capability
3. [DONE] 增加轻小说文库真实 metadata、grant 集合和 bootstrap 回归测试
4. [DONE] 在 `BrowserPresentationCoordinator` 投影现有 userscript 状态与动作
5. [DONE] 在网页浏览器设置增加总授权、管理、权限范围和当前页诊断入口
6. [DONE] 增加权限概览页，逐脚本展示真实执行世界、页面范围、connect、grant 与阻塞原因
7. [DONE] 完成 `86/86` 定向测试、正式门禁、Debug APK 和产物核验
8. [PENDING] 目标 Android WebView 真机复测

最终本地 APK 为 `com.kiyori 45 / 0.1.0`，时间 `2026-07-31 23:05:54 +08:00`，
大小 `494148842` 字节，SHA-256
`E38CD3FEDCE7A2E4885CC0E692CD1A1A1D74A061774EEB3B5E9C7BDA6261C374`；
Android Debug V2 签名与 16 KiB page-size 对齐检查均通过。

## 2.4 插件中心信息架构与日志交互（2026-07-31）

- Overview 的“本页”只展示 provider-level 卡片；userscript 条目只在油猴脚本工作区的“本页”
  显示，避免同一脚本在油猴插件卡片下重复出现
- 插件库不再占用 Overview 页签，统一收纳到右上角加号；来源点击继续在同一 Browser Runtime
  创建前台新标签
- Logs 工作区展示仓库实际保留的最多 200 条日志；单击打开可滚动详情并复制，长按直接复制当前条目；
  “复制全部”和“导出全部”忽略搜索/级别筛选，导出路径为 `Download/Kiyori/exports`
- 浏览器设置只保留具有真实 owner 的 `4/4/3` 共 11 行；插件分组收敛为总授权、插件中心、权限与
  网站范围、诊断与日志四项，工作台的已安装/更新/日志页签不再重复成为设置入口；权限列表的脚本条目
  直达对应 userscript 详情

## 3. 全局不变量

- [x] 不创建第二个 Browser Runtime、WebSession registry 或 active session owner
- [x] 不创建第二个 userscript 仓库
- [x] Browser Plugin 与 AI ToolPkg 不共享协议、目录、注册表或权限
- [x] 不支持的设备能力、manifest 字段和 API 明确显示不可用
- [x] 插件代码不能直接写正式运行目录或 registry
- [x] 安装和更新先 staging、验证、确认，再原子提交
- [x] 页面不能直接调用 privileged native bridge
- [x] 每个已完成阶段独立测试、构建和核验 APK

## 4. Phase 0：运行时安全和可行性

### 4.1 AndroidX WebKit

- [x] 把 `androidx.webkit` 从 `1.12.1` 升级到 `1.16.0`
- [x] 编译核对现有 `WebViewCompat` 调用
- [x] 增加 `JS_INJECTION_IN_FRAME_AND_WORLD` capability
- [x] 建立 isolated world 注册与编译验证
- [x] 在 capability 不满足时显示 `UNSUPPORTED_RUNTIME`
- [x] 不把特权 userscript 改为主世界执行
- [x] 增加持久化“允许用户脚本”总授权，权限状态不混入 compatibility blocked reasons

### 4.2 userscript bridge

- [x] 解析 `@inject-into`
- [x] 定义 userscript world policy
- [x] 每个 WebView 使用一个生命周期固定的共享隔离 userscript 世界和 bridge
- [x] `@grant none` 使用主世界且没有 native bridge
- [x] 每个 frame 使用一次性 bootstrap 和 capability token
- [ ] 增加显式 document ID 和 navigation generation
- [x] Host 重新检查真实脚本和 grant；document-start URL 来自隔离世界自身或与 `sourceOrigin` 一致的页面 URL
- [ ] 把 `sourceOrigin`、iframe URL 和 host permission 绑定到 document lease
- [x] 页面菜单绑定当前 reply proxy 和 userscript owner
- [x] 页面导航、session 关闭、脚本禁用和权限撤销清理旧授权
- [x] 权限关闭时撤销 token、reply proxy、菜单、webRequest 和活动 GM 网络请求
- [x] 权限和脚本列表变化不再移除、重建 WebView 原生 handler/listener
- [x] `unsafeWindow + privileged grants` 在隔离世界使用无 native 权限的同步页面对象桥
- [x] `@inject-into page + privileged grants` 继续严格拒绝

### 4.3 tests

- [x] `UserscriptMetadataParserTest`
- [x] `UserscriptMatcherTest`
- [x] `UserscriptBootstrapUrlPolicyTest`
- [x] `UserscriptCapabilityRegistryTest`
- [x] `UserscriptExecutionWorldPolicyTest`
- [x] `UserscriptBridgeAuthorizationPolicyTest`
- [x] `UserscriptBootstrapScriptSecurityTest`
- [x] `UserscriptTabControlPolicyTest`
- [x] `UserscriptWebRequestEngineOwnershipTest`
- [ ] `UserscriptDocumentLeaseTest`
- [ ] Android isolated world instrumentation test
- [x] 恶意页面伪造脚本 ID、token、grant 和 bridge 消息的 JVM 策略测试
- [x] Chrome main world / isolated world 页面对象桥行为测试
- [ ] 恶意页面真实 WebView instrumentation test
- [ ] Android WebView `unsafeWindow` 页面对象桥 instrumentation test

### 4.4 完成门禁

- [x] 特权 bridge 对页面主世界不可见的代码和静态单元验证
- [x] 页面对象桥源码不包含 native bridge 名称或 authorization token
- [x] 属性、方法、回调、Promise、DOM 节点与临时标记清理在真实 Chrome 隔离世界通过
- [ ] 旧 document 消息被拒绝
- [x] 未声明 grant 的 host call 被拒绝
- [x] 正式开发准备门禁通过
- [x] Debug APK 构建和核验通过
- [ ] 设备 WebViewFeature 报告待真机执行

## 5. Phase 1：userscript 管理闭环

### 5.1 存储与事务

- [x] 定义 userscript registry schema v2
- [x] 增加 immutable revision
- [x] 增加 staging 和 transaction journal
- [x] 脚本、`@require`、`@resource` 全部验证后提交
- [x] registry、日志和值使用原子写入
- [x] 增加大小、条目和网络限制
- [x] 保存 SHA-256、ETag、Last-Modified 和最终 URL
- [x] 把现有 public flat script 迁移到 `context.filesDir` 的 revision 模型
- [x] 运行时不再执行 `Download/Kiyori/websession/userscripts` 中的源码

### 5.2 管理功能

- [x] 新建脚本
- [x] 编辑脚本
- [x] 保存 draft
- [x] metadata 结构化编辑
- [x] 语法与权限检查
- [x] 代码和权限差异
- [x] 版本历史
- [x] 单项更新
- [x] 批量检查更新
- [x] 批量启用和禁用
- [x] 从 URL 和本地文件导入
- [x] 从详情源码页导出当前 active revision 为 `.user.js`
- [x] 删除脚本、修订、草稿、值与日志前明确确认
- [x] 删除统一清理脚本绑定数据；当前数值 `scriptId` 无法安全重新关联，不提供制造孤儿数据的保留选项

### 5.3 UI

- [x] 插件概览卡“允许用户脚本”开关
- [x] 油猴脚本页运行权限卡
- [x] “需要授权”与“不兼容”独立状态
- [x] 脚本运行世界与 `unsafeWindow` 模式诊断
- [x] 本地脚本搜索覆盖名称、命名空间、描述、来源、grant、网站与标签
- [x] `本页 / 已安装 / 更新 / 日志`
- [x] 脚本详情
- [x] 基础匹配规则：省略正向规则、`<all_urls>`、正则 include/exclude、query/fragment
- [x] 权限与 `@connect`
- [x] 资源与依赖
- [x] 浏览器内全屏源码编辑器
- [x] 日志过滤
- [x] 版本详情与统一源码差异
- [x] 未应用内容提示、私有草稿保留与草稿恢复入口

### 5.4 兼容样本

- [ ] 最小 userscript
- [ ] GM storage
- [ ] GM XHR
- [ ] menu command
- [ ] `@require`
- [ ] `@resource`
- [ ] document_start、end、idle
- [ ] 沉浸式翻译安装
- [ ] 沉浸式翻译网页翻译
- [ ] 沉浸式翻译设置持久化
- [ ] 沉浸式翻译更新检查

## 6. Phase 2：Provider 驱动插件中心

- [ ] `BrowserPluginProvider`
- [ ] `BrowserPluginCatalog`
- [ ] `BrowserPluginCommandBus`
- [ ] `BrowserPluginDescriptor`
- [ ] `BrowserPluginPageProjection`
- [ ] `UserscriptPluginProvider`
- [ ] 数据驱动插件卡
- [ ] 本地统一搜索
- [ ] userscript deep link
- [ ] 更新标签
- [ ] 发现标签
- [ ] 添加菜单
- [ ] 插件详情路由
- [ ] Provider 异常状态
- [ ] Back 和抽屉状态测试

## 7. Phase 3：`.kbx` 包与安装器

### 7.1 Package

- [ ] `.kbx` MIME 和文件入口
- [ ] manifest v1 数据类
- [ ] strict JSON parser
- [ ] ID、版本和 runtime version
- [ ] icons、content scripts、background、action、permissions
- [ ] `kiyori.options_schema`
- [ ] manifest compatibility report

### 7.2 安全解包

- [ ] 绝对路径
- [ ] `..` 路径段
- [ ] UNC 和 drive
- [ ] 符号链接和硬链接
- [ ] Unicode 冲突
- [ ] 大小写冲突
- [ ] 重复条目
- [ ] 加密 ZIP
- [ ] 压缩包、展开、单文件、条目和压缩比限制

### 7.3 事务

- [ ] source resolver
- [ ] staging
- [ ] transaction journal
- [ ] immutable package revision
- [ ] registry generation
- [ ] permission diff
- [ ] commit
- [ ] activation health check
- [ ] crash recovery
- [ ] orphan cleanup

### 7.4 UI

- [ ] 本地 `.kbx` 导入
- [ ] 安装预览
- [ ] manifest
- [ ] 权限
- [ ] 文件摘要
- [ ] 兼容状态
- [ ] 安装进度
- [ ] 安装失败诊断
- [ ] developer unpacked

## 8. Phase 4：content-only 扩展

- [ ] `BrowserPluginRuntimeCoordinator`
- [ ] `DocumentLeaseRegistry`
- [ ] per-plugin execution world
- [ ] content script match
- [ ] CSS injection
- [ ] document_start
- [ ] document_end
- [ ] document_idle
- [ ] runtime message
- [ ] `storage.local`
- [ ] `storage.session`
- [ ] action click
- [ ] `activeTab`
- [ ] current page projection
- [ ] navigation revocation
- [ ] renderer crash handling

## 9. Phase 5：background worker

- [ ] QuickJS native memory limit
- [ ] QuickJS max stack
- [ ] deadline interrupt
- [ ] pending job limit
- [ ] 专用 JSON-RPC allowlist
- [ ] worker supervisor
- [ ] event queue
- [ ] startup、idle、stop 和 crash
- [ ] runtime API
- [ ] storage API
- [ ] tabs API
- [ ] action API
- [ ] content-background messaging
- [ ] 日志和资源使用诊断

## 10. Phase 6：签名、catalog 和更新

- [ ] Ed25519 package signature
- [ ] signer identity
- [ ] Kiyori catalog schema
- [ ] catalog search
- [ ] source trust
- [ ] update metadata
- [ ] signer continuity
- [ ] permission expansion confirmation
- [ ] immutable history
- [ ] 显式历史版本选择

## 11. Phase 7：AI 创作

- [ ] userscript draft template
- [ ] `.kbx` draft template
- [ ] AI Browser Capability API
- [ ] AI 搜索插件
- [ ] AI 创建和修改 draft
- [ ] manifest 与语法检查
- [ ] 权限说明
- [ ] 测试生成
- [ ] 当前页测试
- [ ] 差异预览
- [ ] 请求安装确认
- [ ] 诊断分析
- [ ] 确认 AI 不能直接写运行目录

## 12. Phase 8：WebExtension 兼容

- [ ] ZIP 分析
- [ ] CRX3 解析
- [ ] Manifest V2 拒绝和迁移报告
- [ ] Manifest V3 字段矩阵
- [ ] Chrome / Edge / Firefox API 扫描
- [ ] remote code 扫描
- [ ] content script world 分析
- [ ] background 分析
- [ ] 自动转换 draft
- [ ] 目标扩展测试套件
- [ ] 已验证兼容清单

## 13. 每阶段收尾

- [x] 审阅实际 diff
- [x] 更新 `CONTEXT.md`
- [x] 更新正式架构和本 TODO
- [x] 运行最高信号定向测试
- [x] 运行 `:app:compileDebugKotlin`
- [x] 运行 formal readiness
- [x] 运行 `git diff --check`
- [x] 串行运行 `:app:assembleDebug`
- [x] 核验 APK 路径、大小和 SHA-256
- [x] 区分本地、模拟、设备和用户验收证据
- [x] 不提交、不推送，除非当前任务明确授权

## 14. 当前下一步

1. [COMPLETED] 建立 Provider 投影、主机级插件路由栈和按脚本折叠的当前页菜单
2. [COMPLETED] 统一插件中心与油猴管理页的标题、搜索、标签、列表和空态
3. [COMPLETED] 独立验证并构建里程碑一 Debug APK
4. [COMPLETED] 实现 userscript registry v2、私有 immutable revision、draft、transaction journal 和原子迁移
5. [COMPLETED] 油猴脚本“本页 / 已安装 / 更新 / 日志”、详情、多选、批量操作、更新风险审查、
   全屏编辑器、私有草稿恢复、最终本地测试、Debug APK 和产物核验
6. [COMPLETED] 增加 active revision `.user.js` 导出；确认删除必须统一清理脚本绑定数据，
   在没有稳定身份与重新关联协议前不提供数据保留伪选项
7. [PENDING] 安装并验证沉浸式翻译 userscript 的页面翻译、设置、网络和更新
8. [PENDING] 在 Android 设备完成 WebViewFeature、isolated world、恶意页面、旧 document 和 iframe
   origin 验收
