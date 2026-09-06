# Kiyori 仓库布局

本文说明 Kiyori 根目录、Gradle 模块、子模块、生成目录和本机构建输入的职责。目录是否可以删除应由 Git 跟踪状态、忽略规则、构建来源和进程占用共同判断，不能只依据名称。

## 根目录总览

```text
Kiyori/
├── .github/          GitHub Actions、Issue 与 PR 模板
├── app/              Android 主应用
├── buildSrc/         Gradle 自定义任务实现与行为测试，不进入 APK
├── ci/               可复现检查、门禁脚本与测试
├── cmake/            跨模块 CMake 支持
├── config/           机器可读架构与工程合同
├── docs/             正式文档、任务计划、资源与许可证
├── dragonbones/      DragonBones Android/JNI 模块
├── examples/         脚本、ToolPkg 与工具示例
├── fbx/              FBX Android/JNI 模块
├── gradle/           Gradle Wrapper 与版本目录
├── llama/            llama.cpp Android/JNI 模块
├── mmd/              MMD Android/JNI 模块
├── mnn/              MNN Android/JNI 模块
├── quickjs/          QuickJS Android/JNI 模块
├── showerclient/     Shower 虚拟显示客户端模块
├── terminal/         KiyoriTerminalCore Git 子模块
├── tools/            仓库开发、构建和诊断工具
└── web-chat/         React、Vite 与 TypeScript WebChat
```

根 `settings.gradle.kts` 当前声明九个 Android 模块：

```text
:app
:dragonbones
:terminal
:mnn
:llama
:mmd
:fbx
:showerclient
:quickjs
```

## 主要目录

### `app/`

主 Android application 模块。它负责应用壳、浏览器、Operit AI 集成、设置、下载、播放器宿主、工具系统、Android 资源和最终 APK 组装。

`app/build.gradle.kts` 配置第三方制品净化、ToolPkg 生成、native 输入验证、单 launcher 检查和 APK runtime packaging 验证。拆分构建实现时必须保持任务输入、输出和验证合同不变。

其中五个自定义任务类型已移入 [`buildSrc/`](../../../buildSrc/README.md)；应用脚本只注册这些
任务、供应固定输入并连接 Android Variant Sources。播放器输入/包体断言和依赖净化配置仍由
app 持有。运行时包层次与依赖方向见 [仓库与源码架构](../architecture/repository_architecture.md)。

### `buildSrc/`

Gradle 自动编译的辅助工程，源码采用 `com.kiyori.buildlogic.tasks` 包；任务类与文件同名。
生产白名单归档、Rust/NDK 构建、Mihomo 准备和 launcher 检查可以在独立测试中验证。
该目录没有 Android 插件或运行时业务依赖，测试版本复用根 version catalog。
`buildSrc` 变更会触发 Gradle 脚本重新配置，CI 按完整 Android 构建范围处理。

### `ci/`

仓库级可复现检查入口。`ci/script/` 保存检查与准备脚本，`ci/test/` 保存相应单元测试。GitHub Actions 负责准备外部工具链并调用这些入口；详细合同见 [`ci/README.md`](../../../ci/README.md)。

### `config/`

机器可读架构和关键文件合同，包括模块所有权、稳定标识、关键哈希及边界检查输入。修改受保护文件时必须同步核对相应清单。

### `docs/`

文档总入口见 [`docs/README.md`](../../README.md)。`doc-src/` 是长期正式文档源，`TODO/` 保存专项计划和待验收状态，`assets/` 保存文档资源。

### `examples/`

脚本、ToolPkg、WASM、桌面代理和集成示例。部分目录同时提交 TypeScript 源码和确定性生成的 JavaScript/分发文件；修改时必须使用对应构建入口验证生成结果一致。

### `terminal/`

[KiyoriTerminalCore](https://github.com/Kiyori-CN/KiyoriTerminalCore) Git 子模块，也是 `:terminal` Gradle 模块。父仓库只记录固定 gitlink；修改和交付时必须先完成子模块审计、提交和推送，再更新父仓库 gitlink。

### `tools/`

保存仓库开发、构建、诊断和资产生成工具，包括示例包同步、native ripgrep、Shower 工具、MCP bridge 和其他宿主辅助入口。

完整目录、入口与副作用见 [工具索引](../../../tools/README.md)。本地化脚本位于
`tools/localization/`，环境检查/修复位于 `tools/environment/`。工具不因被 CI 调用就迁入
`ci/`；CI 编排仍调用其唯一入口。既有 [工具重整记录](../../TODO/tools_directory_reorganization/index.md)
维护迁移证据，[构建系统草稿](../../TODO/refactor_building_sys/index.md) 描述尚未实施的额外变更。

### `web-chat/`

React、Vite 和 TypeScript 前端。源码位于 `web-chat/`，生成的 `web-chat/dist/` 与同步到 Android assets 的内容均为构建输出，不提交到 Git。

## Native 与功能模块

- `dragonbones/`：DragonBones 骨骼动画与 JNI 渲染。
- `fbx/`：基于 ufbx 的 FBX 解析与渲染集成。
- `llama/`：llama.cpp 本地大语言模型运行时。
- `mmd/`：MMD 模型、Bullet 物理与渲染集成。
- `mnn/`：MNN 本地推理与语音相关 native 能力。
- `quickjs/`：QuickJS JNI runtime 与宿主桥。
- `showerclient/`：Shower 虚拟显示、输入、截图和视频客户端。

这些模块的 `build/` 与 `.cxx/` 是可再生成输出，但模块源码、固定第三方源码、CMake 配置和打包输入不是缓存。

## Git 子模块

根 `.gitmodules` 当前声明：

- `terminal/`：常规 Android 构建所需的 KiyoriTerminalCore。
- `tools/hotbuild/OperitNightlyRelease/`：独立夜间构建工具子模块；未初始化状态不代表垃圾，也不属于常规 Debug 构建。

不得直接删除 gitlink 路径或把未初始化子模块误判为无用空目录。

## 本地生成目录

以下内容通常被 Git 忽略并可由工具重新生成：

- `.gradle/`、`.gradle-user-home-*/`、`.kotlin/`
- 根与各模块的 `build/`
- 各 native 模块的 `.cxx/`
- `node_modules/`、`web-chat/node_modules/`、`web-chat/dist/`
- Python `__pycache__/` 与 `*.pyc`
- `tools/native_ripgrep/target/`
- `work/` 中经确认无恢复价值的临时审计产物

清理前必须确认没有 Gradle、Kotlin、CMake、Node、Python 或 IDE 进程正在使用目标。Windows 下还要检查 Junction、符号链接和其他 reparse point，避免递归操作越过仓库边界。

## 受保护的本地构建输入

以下路径虽然通常被 Git 忽略，但可能是构建所需的本机输入，不能通过通用清理命令删除：

- `app/libs/`：固定 AAR/JAR 输入。
- `app/src/main/assets/models/`：本地语音或推理模型。
- `app/src/main/assets/subpack/`：Android/Windows 子包。
- `app/src/main/jniLibs/`：由依赖准备流程物化的 native 库。
- `.venv/`：仓库自有 Python 检查入口使用的本地环境。

这些输入应由正式依赖准备脚本、固定来源、哈希和许可证合同管理。`git clean -X` 会同时列出缓存与这些输入，因此不适合作为本仓库的一键清理方式。

## 命名原则

命名遵循对应生态，不把多语言仓库统一改成同一种大小写风格：

| 对象 | 约定 | 示例与保留边界 |
| --- | --- | --- |
| 根入口 | 生态标准文件名 | `README.md`、`AGENTS.md`、`CONTEXT.md`、`LICENSE`、Wrapper、`buildSrc` |
| Android 模块 | 稳定的小写职责名 | `app`、`quickjs`、`showerclient`；模块 ID 不为排版改名 |
| Kotlin/Java 包 | 小写领域层级，与目录相符 | `com.kiyori.platform.permission` |
| Kotlin/Java 文件 | 主要类型的 PascalCase 名称；扩展/组合文件使用明确主题 | `BuildNativeRipgrepTask.kt`，避免 `Utils2.kt`、`NewManager.kt` |
| Android 资源 | Android 要求的小写下划线与已有领域前缀 | `kiyori_onboarding_*`；已使用资源 key 不因展示文案变化改名 |
| Python/Shell 与工具目录 | 描述性 `snake_case` | `tools/localization/check_strings.py` |
| Web/Node | 沿用子工程的组件与模块约定 | React 组件 PascalCase，已有 `.test.mjs` / `.live.mjs` 区分离线与真实服务 |
| 配置 | 遵守消费工具的约定 | `package-ownership.toml`、`libs.versions.toml`、`package-lock.json` |
| 正式文档与 TODO | 新主题小写 `snake_case`；编号使用两位；稳定入口保留 | `23_repository_structure_and_build_logic.md`、已有 `BUILDING.md` 与 `docs/TODO` |

兼容 namespace、AIDL、JNI、Intent、authority、数据库、公开安装 URL 和协议标识不因目录
整理机械改名。第三方源码保留上游名称；新工具声明 owner、输入、输出、权限、测试与文档。
禁止只按大小写制造重名路径，避免 Windows 与 Linux checkout 行为不同；不提前创建只有
`.gitkeep` 的架构占位目录，实际实现进入时再建立职责边界。

## 根文件职责

| 文件 | 唯一用途 |
| --- | --- |
| `settings.gradle.kts` | Gradle 工程身份、模块清单与仓库解析策略 |
| `build.gradle.kts` | 根插件声明与明确的聚合任务 |
| `gradle.properties` | 共享 Gradle/Android 属性，不保存本机凭据 |
| `gradle/libs.versions.toml` | Android 与构建测试使用的依赖坐标/版本 |
| `package.json`、`package-lock.json` | 私有 Node 开发工具及当前 npm 锁定树 |
| `pnpm-workspace.yaml` | 预留的安装脚本许可配置；不表示已完成 pnpm workspace/lockfile 迁移 |
| `.editorconfig`、`.gitattributes` | 编辑器编码/空白与 Git 换行、二进制处理 |
| `.gitignore` | 本机配置、缓存、生成物与受保护输入的跟踪边界 |
| `.gitmodules` | 固定 Git 子模块路径与来源 |
| `local.properties.example` | 无凭据的本机构建配置样例 |
| `LICENSE`、`NOTICE` | 项目许可与第三方归属，不是品牌重命名对象 |

`res/` 等仅在本机出现而未被 Git 跟踪的目录不构成仓库接口；处理前单独确认内容与来源。
`git status` 干净不表示这些目录可以删除。
