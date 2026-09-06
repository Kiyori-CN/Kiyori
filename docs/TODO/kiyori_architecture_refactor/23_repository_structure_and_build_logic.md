---
document_type: repository-refinement-record
status: completed
last_reviewed: 2026-09-06
---

# 仓库结构与构建职责规范化

本记录说明根目录、构建代码、开发工具与文档的本轮整理。进度唯一入口为
[架构专项](index.md)，长期规则归入仓库布局、构建指南和贡献指南。

## 目标与范围

从根目录建立可维护的职责地图，修复已有混杂、错误命名、跟踪缓存、检查漂移和文档冲突。
保留九个 Android 模块、application ID、namespace、JNI/AIDL、数据和协议、任务名与 APK
输出路径。不迁移包管理器，不升级依赖，不改变应用行为；本轮完成提交和推送，不操作设备。

## 基线与发现

2026-09-06 起点为 `3bf5015b11bc7603f8ecd543cad1d05f888debc1`，父子仓库均干净。

| 领域 | 证据 | 判断与实施 |
| --- | --- | --- |
| 根目录 | settings 声明九个 Android 模块；模块名与 project 依赖、CMake 输入一致 | 保持 Gradle 模块结构，补全根文件、生成物及受保护输入的职责 |
| 应用构建 | `app/build.gradle.kts` 同时定义五个任务类、二进制辅助函数、应用配置和依赖 | 任务实现进入 `buildSrc`，应用脚本保留配置、任务注册及 Android Variant 接线 |
| 源码分层 | `package-ownership.toml` 已声明 Kiyori、Operit 与第三方所有权 | 保持领域和兼容边界，修复失效语义断言，不机械重命名稳定标识 |
| 工具 | ADB、ToolPkg、Shower 等已经分目录，旧重整计划仍描述为未实施 | 以当前入口建立工具索引，明确副作用与调用方式，修正计划 |
| 环境工具 | `repair_repo_enviroment/windows` 拼写错误，内容是宿主子模块修复工具 | 迁入 `tools/environment/`，保持默认只读和显式 `--apply` |
| 仓库卫生 | `tools/github/__pycache__/import_anthropic_skills.cpython-312.pyc` 被跟踪 | 移除确定的可再生字节码，补对应检查防止重新引入 |
| 文档 | 491 份 Markdown 工作区检查通过；构建/工具计划含目标结构与旧授权文字 | 保持现有文档层次，同步实际职责、当前实现和草稿边界 |
| 架构基线 | ARCH037 报告 7 个首启旧文案、全选与进度位置问题 | 追溯代码及历史，再更新仍能阻止真实回归的断言和负例 |

## 构建方案选择

Gradle 的 `buildSrc` 自动编译有包名的任务类型，适合当前由 app 消费的五个任务，能直接
测试实现而无需 Android 插件或模拟运行应用。任务命名、输入输出和缓存属性原样保持。
应用仍负责注册任务和供应 Android SDK/Variant 输入，不让构建辅助代码依赖业务源码。

独立 included build 适合后续共享 convention plugins，但本轮没有跨仓发布或多个消费者的
插件契约；暂不增加空插件或运行时模块。代价是 `buildSrc` 变化会使各项目构建脚本重新配置，
首次迁移需要重编译。Gradle Kotlin DSL 使用 Wrapper 对应编译器，与应用 Kotlin 版本分离。

## 风险与回滚

- 构建类型移出脚本后，隐式 import、Gradle SAM receiver 与任务注册可能变化，先编译构建逻辑和执行任务行为测试。
- CI 必须把 `buildSrc/**` 纳入 Android 构建范围，避免只改任务实现时漏验 APK。
- 工具移动必须核对相对路径、文档与脚本调用；不保留第二个转发入口。
- 仅清理明确受 Git 跟踪的缓存；忽略的模型、AAR、JNI、子包、虚拟环境和未知本地文件保留。
- 回滚点为本轮起始提交；没有数据迁移与远端变更。恢复时只撤销本轮精确文件差异，不重置整个工作区。

## 验收

1. 构建任务有独立源码归属，应用脚本继续持有所有注册与生成源接线。
2. 任务行为测试覆盖确定性打包、失败输入和唯一 launcher；相关 Python CI 回归通过。
3. 架构、正式准备与文档检查通过；所有移动入口和文档引用闭合。
4. 串行执行 `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`，核验实际 APK 身份、签名和打包断言。
5. 审阅最终差异、Git 状态和 terminal gitlink；设备、远端 CI、Release 及旧专项未验收项保持原状态。

## 本轮验证证据

- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：BUILD SUCCESSFUL，238 actionable tasks。
- `python -B -m unittest discover -s ci/test -p 'test_*.py'`：316/316 通过。
- `check_architecture_boundaries.py --require-main`：PASS（`phase=m03`）。
- `check_formal_readiness.py --require-main`：PASS；文档检查 495 个文件、0 个问题；`git diff --check` 通过。
- `app/build/outputs/apk/debug/app-debug.apk`：475680510 bytes，SHA-256
  `055017931794368F29BC60F86C0826606AD6B16648F957EB10CA8FB8968FD839`；`apksigner verify --verbose`
  报告 Debug V2 签名有效且 1 个 signer，`zipalign -c -P 16 -v 4` 通过。
- terminal 子模块工作树保持干净；设备、远端 CI、Release 及旧专项未验收项保持原状态。
