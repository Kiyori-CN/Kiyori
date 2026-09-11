---
status: verification_pending
observed_date: 2026-09-11
---

# Operit +6 完整适配与工作树交付

本轮延续 `main@934ee7978e05bce1094bae16843d20193edd944a` 上的全部既有工作，按用户授权
一起审查、验证、提交推送。上游冻结为 `b2c76100e5960a82ec154b62f201d89db099fadc`；
范围是 `f323d6c50..b2c76100e` 的适用源码增量，不复制上游产品身份、数据库迁移编号或发布链。
[前一批审计](01_september_bugfix_audit.md) 保留当时结论，下表记录本轮新增适配与最终取舍。

## 适配矩阵

| 更新 | 本轮处理 | 证据入口 |
| --- | --- | --- |
| ToolPkg API 版本、依赖和顺序 | 实现 `1.0.0/1.0.1` 分发，校验依赖并确定性排序；保留扫描代际、解包与私有存储边界 | `ToolPkgApiCompatibility`、`ToolPkgLoadOrderResolver`、`JsToolPkgApiRuntime` |
| 消息菜单、运行时 Hook、Compose 弹窗 | 接入原 registry 和聊天 owner，模板/类型与宿主同步；旧回调隔离 | `ToolPkgChatMessageMenuItemBridge`、`ChatRuntimeHolder`、`ToolPkgComposeDslScreen` |
| 功能模型调用与市场元数据 | 固定服务租约与统计身份；市场预览/发布携带真实 API 版本 | `EnhancedAIService.callFunctionModel`、`ChatCallCodec`、`ArtifactMarketModels` |
| MCP 就绪即注册 | 单服务就绪后发布；配置身份加单调代际拦截禁用/删除/恢复配置后的旧发现 | `MCPLocalServer`、`McpRegistrationGeneration`、`MCPStarter` |
| Plan mode 防重 | 跨视图共用提交回执、固定目标、工作区互斥；副作用未知禁止重发 | `PlanSubmissionCoordinator`、Node 回归 |
| 媒体检测 `db60f985e/375a3615d` | 采用带标记的图片/音频/视频，严格识别回复，区分通过/未验证/失败；同步 UI 与工具结果 | `MediaCapabilityProbe`、`ModelConfigConnectionTester`、`FunctionTestDisplay` |
| 模型列表刷新 `8d9fa18cc` | 订阅同一 DataStore 快照；保留 Kiyori 旧配置协议解码，摘要去重 | `ModelConfigManager.configSummariesFlow`、Agent/Classic 输入栏 |
| 背景/表情选择 `6424d355e` | 改用系统文档选择器，保持按 URI 授权及既有私有复制流程 | `ThemeSettingsBackgroundTab`、`CustomEmojiManagementScreen` |
| 九月 DAO、manifest、输入、权限、角色、工具输出、工作流和消息事件修复 | 纳入全树交付，沿用前一批根因实现 | 前一批审计及对应 JVM 回归 |
| Token 速度、缓存命中率和离线图标 | 保留既有 `GenerationSpeedMonitor`、`ProviderUsageAggregate` 和完整离线图标解析；纳入已有 UI 差异审查 | 原统计 owner、`MaterialIconNameResolver` |
| 工具历史、Responses 重试和思考参数 | 保留 Kiyori 显式协议、执行账本和 replay projector；不引入未知提交重试或平行统计源 | `ModelRequestCompiler`、`AssistantReplayHistoryProjector` |
| 数据库/偏好破坏性修复、Room 迁移编号 | 不移植；上游修复不了 Kiyori 独有执行记录与审计 seal，不能声称兼容 | `AppDatabase` 与对话审计契约 |
| 摘要配置区块和 dialogue review | 保留 Kiyori 已有上下文预算及完整审计详情；不把上游提示词替换混入本轮可靠性适配 | AI 执行与对话审计契约 |
| 桌面 viewport/UA | 保留共享 WebSession 的既有桌面策略，未用上游浏览器宿主覆盖；目标网站验收仍待执行 | `BrowserWebViewSupport` |
| 日语、供应商预设、APK 逆向产物、WebChat、品牌及 CI | 不捆绑本轮：分别需要翻译、供应商或独立客户端/产物验收；产品版本与发布链保持独立 | 现有领域契约 |

## 额外修正

- MCP 禁用再启用和删除重建属于新代际，不能仅凭配置相等重用旧发现结果。
- MCP 缓存与状态原子写入后才发布，配置失效与注册共用临界区。
- 媒体测试的模型参数来自已冻结配置；取消向上传递，不显示成识别失败。
- 插件加载顺序落盘成功后才更新运行时；保存失败显示错误，连续操作串行执行。
- 模型摘要和单配置流直接解码本次 DataStore 快照，避免流内再次读取不同版本。
- 全量回归发现文件复制结果面板遗漏统一抽屉，已接入 `KiyoriModalBottomDrawer`。
- 办公套件继续使用已有 `0.2.0`，修正过时的统一版本断言；生成器与五个子包统一使用现有
  `File` 分类，避免插件分类落入未知值。
- 公共类型严格检查清除了失效导入、图标别名遗漏、Java bridge 动态命名空间与具名接口
  冲突、Compose props 索引约束和可选 JSON 字段错误。新增类型回归保留错误参数拒绝断言。

## 验证与交付

2026-09-11 本地实现及自动验证完成。恢复时的上一次 JVM 日志没有终态，不计为通过；
恢复后测试夹具导入错误及 5 项全量回归失败均已定位并修正，没有禁用测试或降低验证模式。

- `:app:testDebugUnitTest --no-daemon --console=plain`：467 suites / 2711 tests，
  2710 通过、0 failure、0 error、1 skipped；唯一跳过为无外部样本的 `BilibiliMediaLiveTest`。
- Node 版本分发/计划防重回归：9/9 通过。
- `npm run test:toolpkg:types`：`strict=true`、`skipLibCheck=false` 通过，正向签名和错误
  参数拒绝均验证；普通示例、plan_mode、office_suite 的 TypeScript 项目检查通过。
- 架构边界、正式开发准备、文档检查通过；架构检查器自身 114/114 回归通过；
  TextField 生成器输出与实际 renderer 一致。未运行 Lint、Release 或设备操作。
- 串行 `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 1m 50s`，
  238 tasks（27 executed / 211 up-to-date）。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，2026-09-11 12:57:43 +08:00，
  485525812 bytes；SHA-256 为
  `B070D4690BEF81E962EBA00ACEB16C3BF2A5C3D4CC4C1FC92A59E2BF9DCCAA42`。
- APK 身份 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`；
  Android Debug V2 单签名、唯一 launcher 和 16 KiB ZIP 对齐通过。54 个 arm64 native
  库及 shell launcher 的 164 个 `PT_LOAD` 均达到 16 KiB 对齐；无重复 ZIP 路径。
- APK 内 plan_mode 的 22 个文件、office_suite 的 78 个文件与工作区逐字节一致，
  三份媒体探测素材与源码一致。市场兼容版本为 `1.12.1+6`，应用版本不变。
- 交付清单排除 `work/`、私有配置、构建产物和子模块改动；唯三份二进制修改为媒体探测素材。
  `terminal` 固定 gitlink `3a5f22da2afc3b83f47c05907420b8fbd891e76e` 且工作树干净。
  候选提交的 hygiene、Markdown 链接、本地化和 fresh-clone 在提交形成后、推送前验证。

设备 IME、插件菜单/弹窗重建、真实 MCP 端点、模型媒体检测、文档选择器和现有 UI 交互
保持 `verification_pending`。本轮不安装设备、不使用真实账号探测，也不发布 Release。
