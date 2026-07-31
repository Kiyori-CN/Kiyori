---
status: phase_0_runtime_permission_local_verified
design: ../../doc-src/architecture/browser_plugin_platform.md
baseline_branch: main
baseline_head: 6708413c53b8e2ed65100ee40ea853368efe6154
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
- HEAD：`6708413c53b8e2ed65100ee40ea853368efe6154`
- 工作树：存在 Browser Plugin Center 里程碑一未提交改动
- 当前 Browser Runtime：`StandardBrowserSessionTools`
- 当前 userscript owner：`UserscriptRepository` 与 `WebSessionUserscriptManager`
- 当前 AndroidX WebKit：`1.16.0`
- Phase 0 状态：运行时安全、用户脚本总授权、`unsafeWindow` 页面对象桥、定向测试、readiness 和
  Debug APK 已本地验证，设备验收待执行
- userscript 总授权：已实现，默认关闭，由现有 userscript registry 唯一持有
- `unsafeWindow + privileged grants`：本地策略、静态安全和真实 Chrome 双世界行为已验证，
  2026-07-31 vivo Android 16 首轮设备报告出现 WebView 主进程 SIGSEGV；已完成单 WebView 单隔离世界、
  固定注册和匹配语义修复，修复版设备验收待执行
- 当前设计状态：详细设计已定案
- 提交、推送、发布、部署、设备安装：未授权

## 3. 全局不变量

- [x] 不创建第二个 Browser Runtime、WebSession registry 或 active session owner
- [x] 不创建第二个 userscript 仓库
- [x] Browser Plugin 与 AI ToolPkg 不共享协议、目录、注册表或权限
- [x] 不支持的设备能力、manifest 字段和 API 明确显示不可用
- [ ] 插件代码不能直接写正式运行目录或 registry
- [ ] 安装和更新先 staging、验证、确认，再原子提交
- [x] 页面不能直接调用 privileged native bridge
- [ ] 每个阶段独立测试、构建和核验 APK

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

- [ ] 定义 userscript registry schema v2
- [ ] 增加 immutable revision
- [ ] 增加 staging 和 transaction journal
- [ ] 脚本、`@require`、`@resource` 全部验证后提交
- [ ] registry、日志和值使用原子写入
- [ ] 增加大小、条目和网络限制
- [ ] 保存 SHA-256、ETag、Last-Modified 和最终 URL
- [ ] 把现有 public flat script 迁移到 `context.filesDir` 的 revision 模型
- [ ] 运行时不再执行 `Download/Kiyori/websession/userscripts` 中的源码

### 5.2 管理功能

- [ ] 新建脚本
- [ ] 编辑脚本
- [ ] 保存 draft
- [ ] metadata 结构化编辑
- [ ] 语法与权限检查
- [ ] 代码和权限差异
- [ ] 版本历史
- [ ] 单项更新
- [ ] 批量检查更新
- [ ] 批量启用和禁用
- [ ] 导入与导出
- [ ] 删除与数据选择

### 5.3 UI

- [x] 插件概览卡“允许用户脚本”开关
- [x] 油猴脚本页运行权限卡
- [x] “需要授权”与“不兼容”独立状态
- [x] 脚本运行世界与 `unsafeWindow` 模式诊断
- [x] 本地脚本搜索覆盖名称、命名空间、描述、来源、grant、网站与标签
- [ ] `本页 / 已安装 / 更新 / 日志`
- [ ] 脚本详情
- [x] 基础匹配规则：省略正向规则、`<all_urls>`、正则 include/exclude、query/fragment
- [ ] 权限与 `@connect`
- [ ] 资源与依赖
- [ ] 源码编辑器
- [ ] 日志过滤
- [ ] 版本详情与差异
- [ ] 未保存内容提示

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

1. 在 Android 设备执行 WebViewFeature 与 isolated world 验收
2. 在 Android WebView 验证 `unsafeWindow` 属性、方法、回调、Promise、DOM 节点和导航后撤销
3. 执行恶意页面、旧 document 和 iframe origin instrumentation
4. 安装并验证沉浸式翻译 userscript 的页面翻译、设置、网络和更新
5. 设备门禁通过后进入 Phase 1
