---
status: verification_pending
observed_date: 2026-09-11
---

# Operit 九月修复审计与 Kiyori 根因完善

本记录覆盖最近三个正式版本及上次同步快照之后的提交，属于现有同步专项。
目标是修复 Kiyori 实际存在的问题；版本号、提交数量和构建成功都不能替代行为证据。
不包含整分支合并、发布、设备操作或新插件 API 的完整兼容承诺。

## 基线与版本

- Kiyori：`934ee7978e05bce1094bae16843d20193edd944a`，`main`，开始时 198 项既有改动。
- 上次同步快照：`f323d6c50fa661837fad06d4618462861779b562`。
- 本次上游：`b2c76100e`，2026-09-10 22:08:09 +08:00，产品增量 `1.12.1+6`。
- 从旧快照到本次主线共 106 个提交（72 个非 merge），335 个变更路径，23935 行新增、2394 行删除。
  已读取该区间提交清单与候选补丁；按行为簇审查，不把 merge 提交重复计算为修复。
- 共同祖先：`ef00abc5099187b4665957e9697cb743c81fa154`；合并关系不能证明语义已同步。
- [v1.11.0](https://github.com/AAswordman/Operit/releases/tag/v1.11.0)，2026-05-16：插件生态、Compose DSL、流式备份与长历史。
- [v1.12.0](https://github.com/AAswordman/Operit/releases/tag/v1.12.0)，2026-07-01：统一市场、工具与语音、恢复入口、模型兼容及安全修复。
- [v1.12.1](https://github.com/AAswordman/Operit/releases/tag/v1.12.1)，2026-08-08：角色记忆、消息 Hook、长消息、取消与队列、流式渲染、远程 MCP。

上述版本发布说明已通过 GitHub API 实时读取；旧版本适配证据见本专项及
[1.12.0+8/+9](../operit_1_12_0_plus_8_plus_9_ai_plugin_update/index.md)。本轮重点是
上次快照之后仍存在的缺口，而非重新套用历史发布补丁。

## 当前采纳与主动改进

| 问题 | 上游线索 | Kiyori 方案与待验证边界 |
| --- | --- | --- |
| 大量消息变体超出 SQLite bind 上限 | `7d1b72743` | 去重排序后有界分批查询，保留事务、精确集合及长内容分块；避免上游范围查询读取稀疏选择之间的无关正文 |
| ToolPkg 预览与安装选中不同 manifest | `e7fadf0e5`、`f978f906b`、`aa97fff56` | 共用确定性排序，保留 Kiyori 路径、大小、重复项防护；预览流读取不得解除现有限额 |
| 插件输入旧值回写、光标跳动 | `1e89d4f2f` | 文本回声识别与串行派发；额外检查旧回调、取消和重建后新队列隔离，模板与生成文件同步 |
| 并发工具描述注册 | `5a332577b` | 使用并发 registry，不改变权限级别与动作授权 |
| 默认语音提示词清空后恢复 | `128d4265a` | 区分键不存在与显式空字符串，复用原偏好迁移 |
| 工具结果紧贴模型正文导致 XML 不识别 | `99830874e` | 各终态和权限拒绝共用行边界，实时与保存警告内容一致 |
| 工作流静默刷新覆盖可见加载导致转圈不消失 | `a92581565` | 按 Kiyori 当前代际所有者处理加载结束与异常，不复制上游另一套 ViewModel |
| 重新生成回答缺少消息持久化 Hook | `863fb0d8d`、`2349946b1`、`5ce7d623f` | 在 Kiyori 原审计事务成功后返回基底与变体的不可变快照，原委托派发事件；保留原收藏、时间戳、角色和完整正文 |

## 其余更新的取舍

`+4` 的主轴是版本化 ToolPkg runtime 与插件 API；`+5` 集中合入插件、模型、渲染及多语言修复；
`+6` 增加数据库与配置手动修复，并包含 9 月 8–10 日的后续修正。它们是主线产品增量，
不能称为三个新的 GitHub 正式 Release。

| 更新簇与提交线索 | 结论 | 源码依据与原因 |
| --- | --- | --- |
| 重试恢复 `8f60d9d3e`，DeepSeek endpoint 分流 `c7b7c995f`、`d566fc774`、`1140f8d54` | 不直接移植 | Kiyori `ModelRequestCompiler` 持有显式协议；`OpenAIResponsesProvider` 与执行仓库持有未知提交/续接边界。扩大重试可能重复提交，不能用上游恢复逻辑覆盖 |
| 工具历史配对 `36e370f73`、`2454f4bf9`、`f71b14970`、`a19d7ef8d`、`923276652` | 保留现有语义 | `OpenAIProvider` 已调用 `AssistantReplayHistoryProjector.replaySafeHistory`，请求前及 regeneration_completion 有闭合断言；原始流与审计不伪造取消。行边界缺口由本轮单独补齐 |
| 思考参数 `e9c891499`、`ed57021c3`，模型列表刷新 `8d9fa18cc` | 保留 Kiyori 配置 owner | 当前供应商身份、协议、能力解析和五档映射已分离；旧上游 ModelConfigManager 规则不能替换当前统一编译合同 |
| 速度与缓存命中率 `17a0213c6`、`1c4d0da84` | 已有对应能力 | `GenerationSpeedMonitor` 按模型请求单调时钟计量，`ChatStatisticsSheet` 消费 `ProviderUsageAggregate.cacheHitRate`；不重复新增累计速度或缓存统计源 |
| ToolPkg API version、加载顺序和消息菜单 `5369548f9`、`fce4f6e2e`、`12f614806` | 留作独立兼容里程碑 | `fce4f6e2e` 涉及 125 文件、JS runtime、公共类型、Compose DialogHost、市场 API 与 load order；当前不存在完整 api_version 分发。保留 `OPERIT_MARKET_COMPAT_VERSION=1.12.1+3`，不能只提升字符串 |
| 数据库及偏好修复 `72c1216b9`、`f24144934` | 不纳入本批次 | 上游新增健康管理器与破坏性修复操作；Kiyori 数据库还拥有执行记录与审计 seal，需独立备份/修复兼容设计。当前 DataRecoveryActivity 有用户既有改动 |
| Room 20→21 补索引 `6c51fa190` | 不复制迁移编号 | Kiyori 早期迁移已创建 messages 索引，当前 20→21 创建 provider_executions；上游同编号迁移却属于 token usage。编号相同不代表 schema 相同 |
| MCP 发现后注册 `d3c107dd8` | 记录缺口，暂不套用 | 上游依赖 `McpRuntimeDescriptor` 与三参数 registerServer；Kiyori 当前仍经 MCPBridge、MCPStarter 批量验证及两参数注册。提前注册需同时核验禁用/删除代际，不能只复制一个回调宣称远程可用 |
| 桌面 viewport/UA `fb7b8232c` | 不覆盖浏览器 owner | Kiyori `BrowserWebViewSupport` 已按当前 WebSession 与用户代理策略应用 viewport；上游同时修改宿主、脚本与浏览器工具，须用目标网站验证 UA metadata 缺口。该宿主本轮开始已有用户改动 |
| summary sections/dialogue review `e295a82a8`、`212125304` | 独立功能，不捆绑修复 | 新增模型配置、摘要区块 UI 与逐工具结果限制；需与 Kiyori 实际上下文/审计裁剪设计统一，不能作为简单 bugfix 替换提示词 |
| 离线图标、AutoGLM 主题、日语和标签调整 | 保留当前 UI 工作 | MaterialIconNameResolver、自动化覆盖层、聊天及市场页面已有未提交视觉统一修改；不覆盖。没有把已有改动计入本轮修复 |
| Plan mode 防重复启动 `b55a2ac22` | 记录独立后续项 | 上游按计划正文跨视图记录开始状态；Kiyori 需要连同 chat 身份、发送失败和副作用确认统一设计，当前不声称已修复 |
| APK 逆向工具 runtime、Opencode 供应商、WebChat、发布版本与 CI | 不纳入本批次 | 涉及独立工具依赖/产物、供应商合同或另一客户端；与当前 Android 原生修复批次不共用验收，版本、签名和 CI 不跟随上游覆盖 |

上表“保留”表示已有明确的 Kiyori 所有权/对应机制，不代表上游每个边缘案例都已完成设备验收。
后续优先评估完整 ToolPkg API 兼容、MCP 发现生命周期与 Plan mode 防重；数据库修复需单独数据契约。

## 主动发现并补齐的边界

- DAO 使用 998 个时间戳加 1 个 chatId 的分批上限，避免上游稀疏时间范围的过量读取。
- 预览只保留当前最佳 manifest；未选择条目仍按既有解包预算读完，拒绝重复/大小写冲突和非法路径。
- 备份目录中的 hjson 不能凭格式优先级抢占真正包根的 json；预览与加载共用既有排除目录策略。
- 输入队列清理取消等待者；旧/重复回调不能释放新队列，页面重建隔离旧树、错误与 loading 发布。
- 输入失焦但 flush 未完成时仍拦截旧回声；重复中间快照不恢复已删除文字。
- 工作流列表与详情的读取各自持有代际；静默刷新接管原加载态，详情日志的二次读取也检查代际。
- 重新生成事件来自原审计事务提交后的快照，不能使用上游中间补丁的当前页面快照或吞错回读。

## 当前验证记录

- 生产代码 Kotlin/Java 编译通过。第一轮新增 DAO 测试夹具遗漏抽象方法，已补齐后重跑。
- `check_formal_readiness.py --require-main`：PASS。
- `check_documentation.py --write-catalogs`：516 文件、0 问题；后续文档更新继续复核。
- Compose DSL TextField 生成器函数输出与已提交 renderer 文本一致。
- 最终 JVM 回归：10 suite / 37 test / 0 failure / 0 error / 0 skipped；其中新增 23 项，
  同时执行原有 14 项 ToolPkg logo、scanner、builder、store 回归。
- 架构边界检查：PASS；相关 5 个 Markdown 文件使用仓库链接检查器核对工作区，0 问题。
- `git diff --check`：通过。未运行全量 JVM、Lint、Release 或 fresh-clone；本轮没有候选提交。
- 串行 `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 1m 26s`，238 tasks / 23 executed / 215 up-to-date。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，2026-09-11 01:58:37 +08:00，
  `488231589` bytes，SHA-256
  `E1BE84C86DC688B84CEF2048515319977EA2BC72E3B6DD935F811E96B2240987`。
- `aapt dump badging`：`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`；
  `apksigner verify --verbose`：V2 有效、1 signer；Gradle 的单启动入口、脚本代理和播放器打包检查通过。
- 本轮 26 文件增量（17 个已有文件、9 个新文件）；基线 198 项既有改动中，只有自动
  `docs/CATALOG.md` 与本轮交叠，其他既有源码逐文件哈希保持。
- `main@934ee7978e05bce1094bae16843d20193edd944a` 未变；没有暂存、提交或推送。
  terminal 工作树干净且 gitlink 未变；结束前实时 `ls-remote upstream refs/heads/main`
  仍为 `b2c76100e5960a82ec154b62f201d89db099fadc`。

## 设备验收与恢复

本轮状态为本地实现及自动验证完成、`verification_pending`。尚未执行：中文 IME 快速输入/删除及
失焦保存、插件页面重建、真实 message_persisted 消费者、超长聊天变体读取、工作流列表/详情交互。
不把 JVM 假 DAO 的 999 参数模拟解释为 Android SQLite 真机证明，也不把快照字段测试解释为
第三方插件端到端验收。最短下一步是使用上述 APK 按这五组场景复测，记录设备与结果。

本轮工作区恢复资料位于忽略的 `work/operit-sync-20260911-baseline.json` 和
`work/operit-sync-20260911-delivery.json`；它们只记录文件哈希及精确增量，不进入提交。
历史章节的提交推送记录继续保持其原日期与授权范围。

## 验证计划

- DAO：空集合、重复/乱序、999 参数边界、多批次、稀疏时间戳和异常传播。
- ZIP：顺序不影响选择、根目录优先、同深度排序、重复/越界/大小限制及预览与加载一致。
- 输入：快速输入/删除、IME 回声、外部更新、队列顺序、旧回调、新代际、取消等待。
- 工具输出：流式/非流式、多个结果、拒绝/失败、正文只出现一次。
- 工作流：静默刷新穿插、异常/取消、迟到读取和详情切换。
- 运行相关 JVM 回归、文档检查与 `:app:assembleDebug --no-daemon --console=plain`。
- 真机与外部 MCP/插件行为另行验收；本轮不触发实际工具操作。
