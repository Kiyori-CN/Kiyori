# 架构边界控制面

本目录保存 Kiyori 架构重构的机器可读契约。它与运行时源码分离，不持有应用状态。迁移计划见 [架构专项](../../docs/TODO/kiyori_architecture_refactor/index.md)，高频所有权见 [CONTEXT](../../CONTEXT.md)。

## 文件职责

| 文件 | 保护的契约 |
| --- | --- |
| `package-ownership.toml` | 将 `app/src/main/java` 下每个 Kotlin/Java 文件归属到唯一所有者，记录同步区域与包依赖方向 |
| `manifest-components.txt` | 主 source set 中组件、action、category、authority、scheme、host、MIME、process、权限及出现次数；M-01 明确列出 application 类的新旧映射 |
| `debug-manifest-components.txt` | Debug 专属导出 QA receiver、action 与 `android.permission.DUMP` 保护，禁止迁入 main/release |
| `manifest-structure-hashes.txt`、`debug-manifest-structure-hashes.txt` | 主迁移阶段及 Debug 的完整 Manifest 语义树，保护层级与全部元素/属性值，忽略 XML 排版、属性顺序和同级顺序 |
| `stable-identifiers.txt` | 产品、生态、序列化与外部身份字面量及其精确次数 |
| `persistence-names.txt` | DataStore、SharedPreferences、数据库、备份等带引号的持久化名称及其精确次数 |
| `persistence-api-calls.txt` | DataStore、SharedPreferences、Room、WorkManager unique-work 调用的源码路径、API、关键参数和精确次数 |
| `native-ipc-identifiers.txt` | native/JNI/AIDL 包、导出 JNI 符号及 `System.loadLibrary` 标识和次数 |
| `critical-file-hashes.txt` | AIDL、Room schema/entity、ObjectBox UID/路径、持久化 Worker/调度入口、备份恢复实现的完整内容哈希，计算前统一 CRLF 为 LF |

## 提取与所有权

本目录约束 Android 运行时，`buildSrc` 是单独的 Gradle 构建工具工程，不纳入 app 包所有权
或稳定运行时标识计数。其任务实现通过行为测试、正式准备中的 native 生成合同和最终 APK
验证；app 中的类型 import、任务注册与 Variant 接线继续被构建器检查。

- 计划中的 capability、feature、integration 领域必须声明精确包根；未声明的新领域视为无人管理并使检查失败。
- 第三方源码根与 Kiyori、Operit 产品包隔离，不通过相互导入绕过分层。
- 持久化调用提取忽略排版与注释，拒绝可能绕过提取器的别名或直接导入。新增持久化创建 API 前，必须先添加提取器与快照。
- 统计只来自 Git 跟踪文件，以及 `app/`、`examples/`、`tools/` 下未忽略的新源码。被忽略的依赖、生成物、缓存和构建目录不参与，保证新鲜克隆与开发工作区使用同一契约。

快照不能代替迁移设计。只有对应契约变化属于已确认里程碑、正式文档已同步，且差异说明旧值、新值与原因时，才更新快照。

2026-09-10 文件管理器 M3j 审阅更新了 Shell/root 哈希、三个 Shell 桥接 import，以及原子文件 JNI 的
`Java_com_ai_assistance_operit_` 计数 8→9、`System.loadLibrary(` 计数 9→10；新增符号为
`NativeNoReplaceCommit_renameNoReplace` 和 `kiyori_fileops`。主题消费者按实际文件页与悬浮球新增，
语义色 import 104→109，保持精确消费者约束。共用抽屉几何位于 design 层，AI 页面没有反向依赖 Shell。

同次审查核对 `213be2b` 后补齐四个既有快照遗漏：Application 初始化哈希、MainActivity 源码哈希、
ToolExecutionManager 的 KiyoriPaths 引用、StandardFileSystemTools 的 `terminal_settings` 读取。
前两个文件和 ToolExecutionManager 与该 HEAD 完全一致，偏好读取也已存在于 HEAD；本次没有改变这些行为，
只同步机器记录。相关代码与测试验收见[文件管理器专项](../../docs/TODO/mt_file_manager_replica/index.md)。

2026-09-10 文件管理器 M3k 将文件工作表面移出通用 child 滑入层，修复设置返回时的整页位移；
审阅对应 Shell 差异后，归一化哈希从 `C3059EF1` 更新为 `EC0F667D`。
`FileManagerChrome.kt` 成为浏览器主题的第五个精确消费者，仅上下栏及抽屉标题复用中性主题，
列表继续使用设置主题；不改变主题所有者或放宽跨层限制。
AI 抽屉精确 import 表同时新增共用标题组件和三个原值不变的几何常量，标题绘制与文件抽屉保持同源。

## M3l 二级工具箱接线（2026-09-11）

浏览器工具箱通过根 CompositionLocal 接入原文件管理会话和浏览器来源的密码设置；
文件工具箱通过 Shell 最小化文件管理后打开原浏览器。审阅导航差异后，根哈希更新为
`89125AFA`、Shell 归一化哈希更新为 `89F27AD9`。共享二级工具箱成为浏览器主题第六个
精确消费者；文件悬浮球使用既有语义紫色，语义消费者由 61 增至 62、import 由 109 增至 111。
主题所有者、会话所有者和越层规则保持原约束，正反例继续验证消费者漂移。

## 验证

2026-09-12 设置与抽屉管理：审阅一次性文件快捷请求接线及关闭会话清理后，root 哈希
`89125AFA → 04DE0837`，Shell 状态 `C64A0D5D → 0A0ABB39`，Shell 归一化哈希
`777A2DDA → 400A5F39`。解析、目录准备及错误提示在文件管理器适配层，Shell 仍复用原会话。
语义色消费者增加设置页与入口管理两处（62 → 64），import 增加 5 条（111 → 116），
只消费现有 tone 与 resolver；对应正反例同步覆盖，主题所有者不变。
`FileManagerShortcuts` 成为 `KiyoriPaths` 的一个精确消费者，用于准备已有默认工作区；
未扩张旧日志或旧路径入口的允许范围，不更改存储根目录及实际文件。

2026-09-12 文件悬浮恢复：逐项审阅 Shell 的会话可见性推导、写入中关闭保护和设置返回后不复活已关闭会话。
状态哈希由 `7150A1AC` 更新为 `C64A0D5D`，Shell 归一化哈希由 `BB3E8918` 更新为 `777A2DDA`；
没有增加会话或导航所有者，精确 import 与其他边界保持原约束。导航行为由定向 JVM 测试覆盖。

2026-09-12 文件管理设置：审阅新增专属设置页及来源返回后，Shell 归一化哈希由 `89F27AD9`
更新为 `BB3E8918`，状态哈希由 `C64B7219` 更新为 `7150A1AC`，Shell 精确 import 增加
`KiyoriFileManagerSettingsPage`。持久化 API 清单仅新增 `FileManagerPreferences` 的
`kiyori_file_manager` 调用，保存显示偏好，不保存权限、路径或第二份会话。导航往返与双栏同步由行为测试验证。

2026-09-11 材质统一：文件操作菜单局部接入 `KiyoriBrowserTheme`，作为第七个精确消费者，
使其与浏览器一级菜单、两套二级工具箱共用中性表面材质。列表仍沿用设置主题；新表面 token
只派生当前 Material ColorScheme，不增加主题状态或放宽跨层依赖。消费者正反例同步覆盖该入口。

本地与 CI 使用同一只读入口：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

检查器拒绝无人管理的源码、包与路径不一致、禁止或越层的 import/项目全限定引用、Manifest 漂移、稳定契约计数或关键文件哈希漂移、被跟踪的私密/构建产物、terminal 变化，以及超出 M-01 精确候选清单的变更。依赖提取会屏蔽注释与字符串，不把 package 声明视为依赖边。

## 临时例外

首启检查以当前 `OnboardingProgressHeader` 的步骤来源、snapshot 可处理项和处理中选择锁定为
契约；营销标题文字不属于架构不变量。更新这类检查时必须同时保留绕过真实 owner 的负例，
不能仅修改文本或哈希取得通过。

例外只在 `package-ownership.toml` 中按精确文件记录，必须包含规则、字面文件路径、原因、责任所有者及以 `M-` 开头的 `expires_after` 里程碑。通配符、重复记录和未使用记录均使检查失败；例外用于记录待偿还的架构问题，不是永久允许清单。
