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

- 计划中的 capability、feature、integration 领域必须声明精确包根；未声明的新领域视为无人管理并使检查失败。
- 第三方源码根与 Kiyori、Operit 产品包隔离，不通过相互导入绕过分层。
- 持久化调用提取忽略排版与注释，拒绝可能绕过提取器的别名或直接导入。新增持久化创建 API 前，必须先添加提取器与快照。
- 统计只来自 Git 跟踪文件，以及 `app/`、`examples/`、`tools/` 下未忽略的新源码。被忽略的依赖、生成物、缓存和构建目录不参与，保证新鲜克隆与开发工作区使用同一契约。

快照不能代替迁移设计。只有对应契约变化属于已确认里程碑、正式文档已同步，且差异说明旧值、新值与原因时，才更新快照。

## 验证

本地与 CI 使用同一只读入口：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_architecture_boundaries.py `
  --repository . `
  --ownership config\architecture\package-ownership.toml `
  --require-main
```

检查器拒绝无人管理的源码、包与路径不一致、禁止或越层的 import/项目全限定引用、Manifest 漂移、稳定契约计数或关键文件哈希漂移、被跟踪的私密/构建产物、terminal 变化，以及超出 M-01 精确候选清单的变更。依赖提取会屏蔽注释与字符串，不把 package 声明视为依赖边。

## 临时例外

例外只在 `package-ownership.toml` 中按精确文件记录，必须包含规则、字面文件路径、原因、责任所有者及以 `M-` 开头的 `expires_after` 里程碑。通配符、重复记录和未使用记录均使检查失败；例外用于记录待偿还的架构问题，不是永久允许清单。
