---
status: implemented
plan_version: 3
last_reviewed: 2026-07-31
---

# 架构门禁与机器可读所有权规范

## 目的

文档只能解释架构，自动门禁负责阻止新代码重新越过边界。

方案 v3 把该实现定义为 G-00，已经在 M-01 前完成首版实现：

```text
config/architecture/
├── README.md
├── package-ownership.toml
├── stable-identifiers.txt
├── manifest-components.txt
├── manifest-structure-hashes.txt
├── persistence-names.txt
├── persistence-api-calls.txt
├── native-ipc-identifiers.txt
└── critical-file-hashes.txt

ci/script/
└── check_architecture_boundaries.py

ci/test/
└── test_architecture_boundaries.py
```

脚本必须使用项目 `.venv`，只读源码和 Git 索引，不修改文件。

## ownership schema

每条 ownership record 至少包含：

```toml
[[ownership]]
id = "browser-runtime"
path = "app/src/main/java/com/kiyori/feature/browser/runtime/**"
owner = "kiyori-browser"
sync_zone = "C"
phase = "browser"
allowed_import_roots = [
  "com.kiyori.capability.browser",
  "com.kiyori.platform",
]
forbidden_import_roots = [
  "com.kiyori.feature.player.ui",
  "com.ai.assistance.operit.ui.main",
]
stable_contracts = [
  "web_session_browser_store",
  "browser_download_settings",
]
required_tests = [
  "BrowserPresentationReleaseGateTest",
  "WebSessionBrowserBackPolicyTest",
]
```

字段规则：

- `id` 全局唯一且稳定
- `path` 必须匹配至少一个受管文件，除非 phase 尚未开始并显式声明 `planned = true`
- `owner` 对应文档中的唯一状态 owner
- `sync_zone` 只能是 `A/B/C/D`
- `allowed_import_roots` 使用包前缀，同时约束普通/static import 与源码中的完全限定项目引用
- `stable_contracts` 必须能在兼容合同清单中找到
- `required_tests` 必须映射到真实测试文件或明确的 future test
- 临时 exception 必须有 expiry milestone 和理由

规划中的 `com.kiyori` 不能只用一个宽泛 `feature/**` 或 `integration/**` owner。当前
machine schema 已按 browser、player、files、downloads、miniapp、home、settings、backup、
recovery、Operit integration、Shower、Shizuku 和 Tasker 分开登记；新增未登记领域直接
触发 unmanaged source。feature 只允许 capability、design、自身 feature 与 platform，
仅 `integration.operit` 可以直接依赖 `com.ai.assistance.operit`。仓库内 vendored
AndroidX/Sherpa/UUID 源码只允许保持自身包依赖，不得反向导入 Kiyori 或 Operit 产品代码。
依赖提取同时覆盖普通/static import 和源码中的完全限定 `com.ai.assistance.operit`/
`com.kiyori` 引用；注释、字符串、package 声明不会形成依赖边。

## 已实现的诊断规则

| 代码 | 规则 | 严重性 |
| --- | --- | --- |
| `ARCH001` | `com.ai.assistance.operit` 直接依赖 `com.kiyori.app` | error |
| `ARCH002` | Operit AI 直接依赖 Kiyori feature UI/ViewModel | error |
| `ARCH003` | capability 依赖具体 feature/runtime/UI | error |
| `ARCH004` | feature 直接依赖另一 feature 的内部实现 | error |
| `ARCH005` | design/platform 拥有业务状态或依赖 feature | error |
| `ARCH006` | 新 `Operit*` 产品所有权标识未登记 | 后续实现 |
| `ARCH007` | 新硬编码 `com.ai.assistance.operit.*` FQCN 未登记 | 后续实现 |
| `ARCH008` | Manifest component/action/authority 与 snapshot 无解释差异 | error |
| `ARCH009` | DataStore、SharedPreferences、数据库、备份或路径合同变化 | error |
| `ARCH010` | AIDL/JNI/native 名称在非专项里程碑变化 | error |
| `ARCH011` | 同一 capability 出现多个 runtime/store owner | 后续实现 |
| `ARCH012` | source path 与 package declaration 不一致 | error |
| `ARCH013` | ownership path 无文件或文件未被任何 owner 覆盖 | warning/error by phase |
| `ARCH014` | terminal gitlink 或内容变化 | error |
| `ARCH015` | build/APK/bundle/仓库外备份/private config 进入 Git 索引 | error |
| `ARCH016` | M-01 超出精确允许文件或出现非规范化命名差异 | error |

## 旧标识登记

`Operit*` 名称必须属于以下集合之一：

```text
product_rename
split_before_rename
operit_subsystem
ecosystem_contract
serialization_contract
android_component_contract
native_or_ipc_contract
historical_attribution
```

门禁不能使用“源码中不允许出现 Operit”作为规则。它应拒绝未分类的新标识，而不是误删合法生态名称。

## stable contract snapshot

当前已生成并审阅：

```text
config/architecture/
├── package-ownership.toml
├── stable-identifiers.txt
├── manifest-components.txt
├── manifest-structure-hashes.txt
├── persistence-names.txt
├── persistence-api-calls.txt
├── native-ipc-identifiers.txt
└── critical-file-hashes.txt
```

这些文件由人工批准后进入 Git。脚本重新提取当前源码状态并以精确出现次数与
snapshot 比较，因此新增和删除同类字面量都会触发检查。Manifest snapshot 还保留
component、action、category、authority、scheme、host、MIME type、process 和 permission
的重复次数；`manifest-structure-hashes.txt` 进一步对每个迁移阶段保存完整 XML 语义树
哈希，忽略格式、属性顺序和同级元素顺序，但保留节点层级与全部属性值，因此权限删除、
`exported`/`launchMode`/备份配置变化和 intent-filter 归属漂移都会失败。关键文件 snapshot
直接核对 AIDL、Room schema/entity、ObjectBox model/目录映射、已持久化 WorkManager
worker/scheduler 与备份/恢复实现
经 CRLF-to-LF 规范化后的 SHA-256，确保 Windows/Linux checkout 一致。
`persistence-api-calls.txt` 同时固定 65 个持久化 API 调用记录，扫描时忽略源码字符串、
注释和排版差异，保存文件路径、API、目标参数与重复次数，防止只新增新名称而旧字面量
计数不变时绕过 ARCH009；`preferencesDataStore` alias 和 `Room.databaseBuilder` 直接导入
会被拒绝，避免改写调用名绕过扫描；尚未建模的 DataStore/default SharedPreferences/
SQLite 创建 API 也会失败，必须先增加提取器和合同设计。

snapshot 更新要求：

1. 当前任务明确包含对应合同变化
2. 相关正式文档先更新
3. diff 中显示旧值、新值和迁移理由
4. 对应测试与当前任务授权范围内的运行验证完成
5. 不能只更新 snapshot 让检查变绿

M-01 还需要一份 candidate rule，引用
[M-01 Application 原包改名精确影响清单](15_m01_application_rename_exact_manifest.md)：

- 允许 16 个实现文件
- 允许 49 次旧符号到新符号的映射
- 规范化名称后 Application 文件内容一致
- Manifest 只改变 Application 类名
- Lint baseline 只改变 6 个文件路径
- 任何计数漂移都要求重新生成影响清单并重新批准，不能扩大允许范围

## 命令接口

当前 CLI：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

可选参数：

```text
--base <commit>
--phase <auto|baseline|m01|post-m01>
--json
```

默认行为：

- 检查当前工作树或 candidate tree
- 输出稳定诊断 code、文件、行号和规则
- 任一 error 返回非零
- warning 不隐藏 error
- 不自动修复源码

`auto` 同时检查 base tree 与当前 tree：只有 base 仍包含
`OperitApplication.kt`、当前 tree 已包含 `KiyoriApplication.kt` 时才进入 `m01`。
M-01 完成后的 main 构建和未来 PR 使用 `post-m01`，继续核对新 Manifest 入口，但不会重复
套用一次性的 16 文件纯改名清单。

## CI 接入

已接入：

- `check_formal_readiness.py` 继续负责身份、子模块、品牌和仓库卫生
- `check_architecture_boundaries.py` 负责 package、owner、合同和依赖方向
- `pr_check.py` 按 scope 触发 architecture gate
- Android build lane 在 Gradle 前运行同一检查

本地与 CI 必须调用同一脚本，不在 workflow 复制规则。

## exception 规则

exception 只允许处理真实的过渡边界：

```toml
[[exception]]
rule = "ARCH001"
path = "..."
reason = "..."
expires_after = "M-03"
owner = "..."
```

禁止：

- 无期限 exception
- 通配符或目录级宽泛忽略
- 未被实际诊断使用的 exception
- 因检查失败扩大 allowlist
- 使用 exception 隐藏第二状态 owner
- 在完成里程碑后留下过期 exception

当前基线有 6 条 ARCH012 文件级例外，分别记录历史 package/path
错位或 vendored UUID 来源；每条都绑定责任 owner 和 M-05 后续清债里程碑。

## G-00 验收证据

- 架构专测试：36 项通过，包含正向、负向、Windows 路径、例外、未暂存改名、
  Git ignored dependency tree、Java static import、重复 Manifest component 和关键文件
  hash drift、完整 Manifest 语义漂移、JNI 完整符号替换、新持久化调用识别，
  未建模持久化 API 拒绝、持久化 alias 绕过拒绝、vendored 反向依赖拒绝，
  普通/static import 与完全限定项目引用绕过拒绝，以及 feature/Operit 双向依赖拒绝场景
- 全量 `ci/test`：102 项通过
- 当前工作树架构检查：`phase=post-m01` PASS
- formal readiness：PASS
- fresh clone reproducibility：PASS
- 最终 bundle 恢复演练促使扫描范围收口为 Git tracked + non-ignored untracked，
  恢复克隆与开发工作树的 stable literal 计数一致
- 深度合同审计补齐 Manifest Intent/authority/process、全部已登记 SharedPreferences、
  ObjectBox 目录、provider AIDL、备份格式、WorkManager 名称/worker 入口，以及
  AIDL/Room/ObjectBox/backup 文件哈希
- 未修改 Android 运行时代码、Manifest、资源、AIDL、native 或 terminal

## 门禁自身验收

- 正常文件通过
- 每条 error 规则至少有一个失败测试
- 路径分隔符在 Windows/Linux 一致
- 未跟踪文件和 candidate tree 都可检查
- 非 UTF-8 或生成文件边界明确
- 诊断顺序稳定
- JSON 输出可被 CI 和后续可视化工具读取
- 不访问网络、不读取 `.env`、不输出私密内容
