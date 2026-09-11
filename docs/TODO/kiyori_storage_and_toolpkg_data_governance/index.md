---
status: active
owner: Kiyori storage and ToolPkg runtime
updated: 2026-09-11
---

# Kiyori 存储路径与 ToolPkg 数据治理

本 TODO 是 Kiyori 存储域、ToolPkg 私有数据、制品构建、安装事务和旧数据显式导入的唯一进度入口。
它不改变 `com.ai.assistance.operit`、`com.operit.*`、`operit://`、`.operit/config.json`、
`OPERIT_DOWNLOAD_DIR`、ToolPkg/MCP ID 或市场 wire type 等兼容合同。

Kiyori 新建的公共数据只进入 `Download/Kiyori` 与 `Pictures/Kiyori`。应用不得自动扫描、复制、
合并或删除 `Download/Operit`；旧数据只能由用户通过 SAF 明确选择，并交给匹配的包专属迁移器。

## 文档树

```text
kiyori_storage_and_toolpkg_data_governance/
├── index.md
├── 1_storage_domains_and_paths.md
├── 2_toolpkg_storage_and_migration.md
├── 3_artifact_pipeline_and_install_transaction.md
└── 4_validation_and_rollout.md
```

## 目标

- 由 `KiyoriPaths` 唯一声明目录名称、层级和物理根
- 由 `KiyoriStorageService` 统一创建内部目录、包命名空间和事务目录
- 由 `KiyoriPublicStore` 唯一执行 Kiyori 自有 MediaStore 写入和公开文件命名
- 为 ToolPkg 提供宿主绑定身份的 private/cache API，不返回真实内部绝对路径
- 让 ToolPkg 构建、导入和市场安装执行同一组路径、预算、敏感内容与旧路径扫描规则
- 把已安装 ToolPkg 制品写入内部内容寻址存储，通过原子 active 记录切换版本
- 为旧公共数据提供显式、包专属、可审计、可中止的 generation 导入框架

## 非目标

- 不把兼容标识机械改成 Kiyori
- 不自动发现或读取 `Download/Operit`
- 不双写新旧目录
- 不在缺少包专属迁移器时复制未知数据
- 不在本轮修改第三方“记忆系统”发布包或宣称其已完成迁移
- 不安装 APK，不操作手机、模拟器或远端市场

## 实施状态

1. [DONE] 完成旧路径、用户可见保存位置、ToolPkg parser、构建器和市场安装链的只读审计
2. [DONE] 固化四域存储、包命名空间、制品预算、安装事务和显式迁移合同
3. [DONE] 实现统一路径投影、公共保存服务和 ToolPkg private/cache API
4. [DONE] 实现 ToolPkg 制品 scanner、确定性 builder、内容寻址 store 与 active 事务
5. [DONE] 统一现有导出、Markdown、分享图片和 Browser 下载消费者
6. [DONE] 完成定向测试、ARCH046、正式开发门禁和 Debug APK 构建核验
7. [PENDING] 目标 Android 设备路径、SAF、MediaStore、市场更新和旧数据导入验收

## 当前数据边界

## AI 产物位置深度复查（2026-09-11）

基线 `main@80156b864`，工作区干净。本轮授权实现、验证、提交和推送，不操作设备。

1. [DONE] 统一原生工具、脚本和浏览器的调用工作区投影，校验 SAF/网络边界；按环境读取，避免不相关的非法偏好阻断输出。
2. [DONE] 设置页按 Android/Ubuntu 分组，提供目录选择、字段错误、路径预览、草稿恢复默认和离开保护，复用既有设置主题。
3. [DONE] 复查 Office、Bilibili、code_runner 的默认交付、环境安装与失败恢复；增加有意义的回归。
4. [DONE] 定向验证、串行 Debug APK 构建与内置脚本核验；最终提交/远端 ref 以本轮 Git 交付记录为准。
5. [PENDING] 真机目录选择、权限、软键盘、返回、Ubuntu 与实际产物现场验收。

风险与回滚：复用现有偏好、对话绑定和终端 provider；不迁移已有文件或依赖，不改写显式目标。
本轮提交可独立回退。自动检查只证明本地逻辑与构建，现场验收保留 `verification_pending`。

本轮修复包括原生查询/浏览器缺工作区投影、异步脚本丢失调用上下文、SAF URI 被误作本地目录、
两端偏好互相阻断、保存失败仍更新内存、Snackbar 占用操作状态、分类目录符号链接越界、
Office 生成前未解析目标及交付失败丢失恢复路径、Rust 文件执行误用默认目录。
代码运行器所有内联语言先解析本次目标，避免无效目录仍触发依赖准备或源码暂存；环境安装通过
新会话的显式 HOME/工作目录保持原受管位置。文档合同见[产物存储](../../doc-src/contracts/artifact_storage.md)。

UI 复用 `KiyoriSettingsWorkspacePage`、`KiyoriSettingsGroupSection`、Material3 与现有
AppContent IME/当前页标记；参考 Operit `f323d6c50fa661837fad06d4618462861779b562` 的
`ContextSummarySettingsScreen` 表单控件与主题使用，按两端路径及保存草稿需求重新组织内容。

验证（2026-09-11 Asia/Shanghai）：路径/符号链接、偏好提交回滚、并发调用上下文共 18 项 Kotlin
测试通过；Office/Bilibili/真实 JS 宿主 72 项、终端输入/Office 控制台/浏览器 30 项通过，
真实 WSL PTY 12 项通过且无跳过。`tsc --noEmit -p examples/tsconfig.json` 与
`tsc -p tools/compose_dsl/tsconfig.contracts.json` 通过；旧 Java 桥接递归声明展开导致包链索引
消失的问题以递归接口修正，不增加 `any` 或断言。Office 与 Bilibili 类型检查/构建通过，
生成分发文件与源码资产同步。

Debug 构建 `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` 通过；
`app/build/outputs/apk/debug/app-debug.apk` 为 487,787,852 bytes，SHA-256
`9c2b035e22caa1eefe33da57547203267af3198bbccd4baea60fb4d3c98c9f74`。
已核对 `com.kiyori / 45 / 0.1.0` 以及 APK 内 `code_runner.js`、Office runtime、Bilibili
bundle 与工作区逐字节一致。没有安装 APK、修改设备权限或执行真实下载。

## AI 产物默认目录闭环（2026-09-11）

本增量以 `main@ccc4ba4fa` 为基线，修复此前只有提示词与部分浏览器路径的实现。用户授权包含审查并交付其他任务留下的 Responses 图片历史与审计导出改动。

1. [DONE] 调查策略、设置、浏览器输出、Terminal、code_runner、Office、Bilibili 和显式路径工具调用链。
2. [DONE] 统一绝对根、旧输入兼容、路径校验、可恢复设置 UI，以及实时脚本 API 与既有工作区绑定投影。
3. [DONE] 接通本地 AI 新会话 cwd、源码执行目录、Office 两端任务目录、Bilibili 默认根及浏览器原子同名分配。
4. [DONE] 执行路径/并发、Office/Bilibili/PTY、内置包资产同步、Responses 与审计回归，串行构建 Debug APK 并核对提交清单。
5. [PENDING] Android 权限、SAF、真实 Ubuntu/PRoot、真实 Office/Bilibili 和第三方工具现场验收。

风险与回滚：只改变未指定目标时的默认位置，不迁移现有文件，不搬移虚拟环境/依赖缓存，不重置已有终端；可在设置中恢复旧专用目录。显式目标、覆盖编辑、网络/SAF、人工下载和系统导出保持各自原契约。运行时边界见[产物存储契约](../../doc-src/contracts/artifact_storage.md)。

复查还修正了三处真实缺陷：`getArtifactPaths().linuxIsLocal` 原先取终端页当前选中的会话类型，
现在以运行中的 provider 为准（provider 未建立时才回退会话与已保存 SSH 偏好），避免 SSH 场景把
本地 Ubuntu 目录当成本地路径；`code_runner` 在路径校验通过后才新建终端会话，不再为无效路径
留下空会话；Office skill 与工作区模板中失效的 `${KIYORI_DOWNLOAD_DIR}/Office/` 说明改为实际
的 `office/<task_id>/` 交付规则。

`tools/example_packages/bilibili_host_runtime_support.mjs` 未跟随 runtime 拆分更新：它只装配了
6 个 bootstrap 模块中的 4 个，缺 `toolpkg-api-runtime`，也没有 `getArtifactPathsForCall`
原生面，因此 `bilibili_host_runtime.test.mjs` 7 项全部在 host 构造阶段就失败（与本轮差异无关，
用 HEAD 版本 bundle 复现同样失败）。本轮按 `buildInitRuntimeModules` 的顺序补齐装配并补上默认
产物根查询，7 项全部通过；测试 1 也因此真正跑通打包 bundle 的默认输出目录路径。

验证（2026-09-11 Asia/Shanghai，工作区基线 `main@ccc4ba4fa`）：`ArtifactPathRulesTest`、
`OpenAIResponsesPayloadAdapterTest`、`OpenAIResponsesImageHistoryRequestTest`、
`ConversationAuditMarkdownRendererTest` 与 `packTool` 契约测试共 100 项全部通过；
`office_suite`/`bilibili_toolkit`/`terminal_input` 71 项与 PTY `code_runner_python` 12 项通过；
`bilibili_host_runtime`、`office_console`、`browser_development` 追加 20 项通过（合计 91 项）；
`examples/code_runner.js` 与 `app/src/main/assets/packages/code_runner.js` 逐字节一致。
`examples/bilibili_toolkit/dist` 原先漏掉了 `build.mjs` 重编译，CI 的
`git diff --exit-code -- examples/bilibili_toolkit/dist` 会失败；已重新生成并核对包内
`dist/packages/bilibili.js` 与工作区逐字节一致。`./gradlew.bat :app:assembleDebug --no-daemon
--console=plain` 成功，2026-09-11 21:04:17 UTC+8 的 `app/build/outputs/apk/debug/app-debug.apk`
为 495,731,084 bytes，SHA-256
`89efbfc3e66ade2a73604d6dd1b36d8d1c257aaa2b00d0fc62eebfe0cb1bb30d`；包内
`assets/packages/code_runner.js`（70,272 bytes）与 `bilibili_toolkit.toolpkg` 内
`dist/packages/bilibili.js`（78,199 bytes）均与工作区一致，`office_suite.toolpkg` 为 267,152 bytes。
`tsc -p examples/tsconfig.json` 仍失败于既有 `examples/java_bridge.ts` 类型声明冲突；
该文件不在本轮改动范围，未在本次修复。

## 当前存储布局

```text
应用私有且不备份/
└── Kiyori/toolpkg/v1/<package-key>/
    ├── data/
    ├── generations/
    ├── active-generation.json
    └── migration-audit/

应用缓存/
└── Kiyori/
    ├── toolpkg/v1/<package-key>/
    └── toolpkg-build/<transaction-id>/

应用内部文件/
└── Kiyori/toolpkg-runtime/v1/
    ├── artifacts/<sha256>.toolpkg
    ├── extracted/<sha256>/
    ├── active/<package-key>.json
    ├── audit/<sha256>.json
    └── market/<package-key>/

共享下载/
└── Kiyori/
    ├── browser/downloads/
    ├── toolpkg/<package-key>/public/
    └── exports/
        ├── browser/
        ├── userscripts/
        ├── player/
        ├── toolbox/
        ├── ai-config/
        ├── backups/
        └── toolpkg/<package-key>/

共享图片/
└── Kiyori/
    ├── Markdown/
    ├── Shared/
    └── AI/
```

详细合同见：

- [存储域与路径](1_storage_domains_and_paths.md)
- [ToolPkg 存储与显式迁移](2_toolpkg_storage_and_migration.md)
- [制品流水线与安装事务](3_artifact_pipeline_and_install_transaction.md)
- [验证、上线与完成标准](4_validation_and_rollout.md)
