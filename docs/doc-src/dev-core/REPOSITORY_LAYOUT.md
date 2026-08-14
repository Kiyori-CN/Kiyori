# Kiyori 仓库布局

本文说明 Kiyori 根目录、Gradle 模块、子模块、生成目录和本机构建输入的职责。目录是否可以删除应由 Git 跟踪状态、忽略规则、构建来源和进程占用共同判断，不能只依据名称。

## 根目录总览

```text
Kiyori/
├── .github/          GitHub Actions、Issue 与 PR 模板
├── app/              Android 主应用
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

`app/build.gradle.kts` 还持有固定第三方制品净化、ToolPkg 生成、native 输入验证、单 launcher 检查和 APK runtime packaging 门禁。该文件当前职责较多，后续拆分必须保持任务输入、输出和验证合同不变。

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

该目录仍处于职责整理阶段。移动入口前必须复用 [`docs/TODO/tools_directory_reorganization/`](../../TODO/tools_directory_reorganization/index.md) 与 [`docs/TODO/refactor_building_sys/`](../../TODO/refactor_building_sys/index.md) 的既有计划，不保留旧路径转发脚本。

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

- Gradle 模块和根功能目录使用稳定的小写名称。
- 新文档目录使用小写 `snake_case`；根入口使用行业约定名称。
- 兼容 namespace、AIDL、JNI、Intent、authority、数据库和协议标识不因目录整理被机械改名。
- 新工具应按唯一职责归档，并同时声明 owner、输入、输出、权限、测试和文档入口。
