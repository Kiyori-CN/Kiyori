---
status: verification_pending
owner: OpenAIHostedWebSearch host + ToolPkg
---

# 2026-08-29 首次调用门槛与中转地址规范化方案

## 目标

让 `openai_web_search:openai_search` 在 package-scoped 环境变量完整有效时直接执行一次 Hosted
Responses Web Search 请求。兼容探测继续提供真实中转合同诊断，但只能由设置页显式触发，不能阻断
普通搜索首次使用。

## 已确认根因

- `ToolPkgOpenAIWebSearchBridge.search()` 调用 `bindingResolver.resolve(requireRelayProbe = true)`。
- relay strict 且没有当前 fingerprint 记录时，`OpenAIHostedWebSearchPolicy.validateCompatibility()`
  返回 `RELAY_PROBE_REQUIRED`，因此 gateway 尚未创建 HTTP call。
- `validateResponsesEndpoint()` 只接受 path 以 `/responses` 结尾；用户给出的
  `https://speed.ai-pixel.online/` 是中转根地址，无法直接通过本地配置校验。
- `.codex/skills/web-search` 的 endpoint 解析区分 base URL 与完整 endpoint，但其 SerpApi/Tavily/Zhipu
  provider 协议不能直接替代 Kiyori 的 Responses hosted web_search 请求。

## 实施决策

1. 普通 `search` 解析绑定时传 `requireRelayProbe = false`。本地环境、endpoint、model、credential、
   auth、headers、search options、admission 和请求参数校验保持不变；域名过滤、证据合同、错误分类、
   cancellation 和 at-most-once 不变。
2. `runCompatibilityProbe` 保持 `RESPONSES_RELAY_STRICT` 专用且显式付费；记录成功/失败 fingerprint
   仅用于状态诊断和 stale 展示。状态页将说明“配置有效即可搜索，探测是可选诊断”，不再声称普通
   search 会被阻断。
3. 在绑定编译阶段将安全的中转根地址规范化为 `/v1/responses`；已是 `/responses` 的完整地址保持
   path，避免重复追加。拒绝非 HTTPS、userinfo、query、fragment、空 host、非 `/responses` 的额外
   path 和含糊的文件段；官方 contract 仍只允许精确 `https://api.openai.com/v1/responses`。
4. endpoint 规范化后参与 compatibility fingerprint、状态 host 和 HTTP request，保证同一配置只
   对一个 URL owner 生效；不引入 endpoint pool、静默切换或重试。
5. 同步更新 source TypeScript、dist、ToolPkg archive 和文档；不改内部包 ID
   `com.kiyori.openai_web_search` 或宿主服务 `openAIWebSearch`。

## 影响文件

- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/ToolPkgOpenAIWebSearchBridge.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIHostedWebSearchPolicy.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIHostedWebSearchBindingResolver.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIHostedWebSearchModels.kt`
- `app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIHostedWebSearchPolicyTest.kt`
- `app/src/test/java/com/ai/assistance/operit/api/chat/llmprovider/ToolPkgOpenAIWebSearchBridgePolicyTest.kt`
- `examples/openai_web_search/src/shared.ts`、`src/ui/index.ui.ts` 及生成 `dist/`
- `app/src/main/assets/packages/openai_web_search.toolpkg`
- `docs/TODO/README.md`、本文件和正式架构文档（如状态/合同发生变化）

## 验收矩阵

| 层级 | 证据 |
| --- | --- |
| 本地策略 | relay 无 probe 记录可通过普通执行校验；显式 `requireRelayProbe=true` 的单元合同仍覆盖探测 API；缺失/非法环境变量仍 `not_sent`。 |
| endpoint | 根地址、`/v1` base、`/v1/responses` complete URL 的规范化与 query/fragment/userinfo/非 HTTPS 拒绝。 |
| 资产 | TypeScript strict 编译、source/dist/ToolPkg/APK asset SHA-256 一致。 |
| 仓库 | 定向 JVM、正式 readiness、差异检查、串行 Debug APK 和 APK 元数据核验。 |
| 真实服务 | 使用用户授权配置只执行一个真实搜索请求；记录 HTTP/响应证据，不在未知提交状态下重发。 |
| 现场 | 设备首次使用不点击 probe 即可搜索、结果卡和状态页可见；未连接设备时保持 `verification_pending`。 |

## 风险与回滚点

- relay 可能不支持 Responses Web Search 或返回无 URL evidence；这应作为真实响应结构化失败/零证据
  诊断，不通过删除字段、换协议或重试掩盖。
- endpoint 根地址规范化只影响 relay contract；官方 endpoint 继续精确校验。
- 代码回滚点为本轮变更前的 `main` HEAD 和未修改的用户工作树；不回退或覆盖其他用户改动。

## 当前证据

- Hosted Web Search 定向与全量 `:app:testDebugUnitTest` 通过；TypeScript strict、正式开发准备、新鲜克隆、
  `git diff --check`、Debug 构建、V2 签名和 16 KiB 对齐通过。
- 生成 APK 内 `openai_web_search.toolpkg` 与 `app/build/generated/bundledToolPkgAssets/packages/`
  中同名制品 SHA-256 一致；manifest 保留 `com.kiyori.openai_web_search`/`1.0.0`，工具导出为
  `openai_search`，旧强制 probe 文案不再存在。
- 用户授权的真实请求发送至 `https://speed.ai-pixel.online/v1/responses`，模型为 `gpt-5.6-terra`，
  HTTP `200`、response `completed`、`web_search_call=1`、URL citation `1`。该证据证明中转合同在
  本次请求上可用，但不替代 Android 设备 UI 验收。
