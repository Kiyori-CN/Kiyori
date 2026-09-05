# Kiyori 扩展脚本品牌与运行契约迁移

## 状态与范围

- 状态：`verification_pending`，实现与本地自动验证完成，Android 和跨设备现场验收待执行。
- 目标：统一 AI 左抽屉“扩展”中的内置 JS/TS 脚本和目录型 ToolPkg 的 Kiyori 产品标识，修复脚本公共路径、环境变量、调试 marker、包 ID、路由和导出名的 Operit 残留，并保持源码、生成 JS、生产 assets 与白名单一致。
- 范围：`examples/` 生产白名单脚本及 ToolPkg、`app/src/main/assets/packages/`、脚本类型声明、同步工具、脚本合同测试和对应维护文档。
- 非目标：不改 Android application ID 以外的既有 Java namespace、Intent/AIDL、MCP/Market wire type、外部 API 路径或宿主桥接类名；这些是运行时互操作协议，不是脚本产品品牌。
- 发布假设：Kiyori 当前未正式发布，脚本包的新 ID、文件名和环境变量直接切换为 Kiyori；仓库内不保留同一内置包的双 ID 并行实现。

## 设计决策

1. 直接暴露给模型或用户的包名、显示名、说明、导出工具名、临时文件路径、环境变量、日志 marker 和 ToolPkg `com.*` 标识使用 Kiyori。
2. Java/Android 宿主桥接类名与已存在的协议 action 保持不变，脚本通过现有桥接调用工作。宿主仍向显式导入的旧脚本提供原有目录常量 ABI，值与 Kiyori 常量来自同一目录 owner；内置脚本和类型声明只使用 Kiyori 名称，不增加失败后切换路径。
3. 普通脚本继续由根级 TypeScript 生成 JS；目录型 ToolPkg 继续由各自构建入口生成 `dist/`，生产 assets 只通过 `sync_example_packages.py` 同步。
4. `worldbook` 的 Operit 条目格式、`operit://` 等明确外部生态格式只在兼容说明中保留，不把外部格式名称伪装为 Kiyori 自有协议。
5. `.operit/config.json` 和 plan mode 的工作区目录继续遵守宿主 `WorkspaceConfigReader` 与变更观察器的排除合同。市场端点保持真实的 `https://api.operit.app/market-stats`；不为品牌改名构造不存在的地址。

## 行为修复

- `kiyori_editor:set_context_summary_config` 只更新明确传入的字段，空更新在调用宿主前拒绝；使用宿主类型声明，不以可选字段和虚构默认值覆盖用户配置。
- 编辑器识别宿主返回的结构化错误对象，保留真实错误消息；日志只记录失败类型，不记录模型配置或令牌。
- `remote_kiyori` 通过宿主 `java.net.URI` 解析地址，拒绝无效端口、用户信息、查询串和片段；未指定端口的产品默认仍为 `8094`。
- 完整配置在写入前校验；宿主多次环境变量写入不具备事务接口，中途失败返回 `persistedKeys`，不重复写入，健康检查失败保留真实的 `configured` 状态。
- 健康检查必须满足 HTTP 2xx 与实际健康字段；聊天必须同时满足 HTTP 2xx 和协议 `success=true`。空或畸形 JSON 不再报告成功。
- 聊天请求把 `timeout_ms` 传给远端执行器；配置页发送数字超时，通过宿主解析工具名后调用一次，移除失败后换名称重复执行的链路。
- 开发版旧包 ID、环境变量和 PC 程序目录不会自动迁移。已有本地开发配置需按新变量名重新填写；外部导入包继续走既有安装流程。

## 阶段

- [x] 清点并分类生产脚本中的 Operit 标识，记录允许保留的宿主协议项。
- [x] 迁移 `kiyori_editor`、`remote_kiyori` 以及内置脚本的 Kiyori 名称、ID、参数、路径和 marker。
- [x] 同步生成 JS/assets/白名单，补充脚本品牌和漂移合同测试。
- [x] 修复编辑器参数覆盖、错误消息丢失和远程配置/响应/重复调用问题。
- [x] 运行 TypeScript/ToolPkg 同步、定向 JVM/Python、formal readiness 和 Debug APK 验证。
- [x] 审计交付文件、敏感内容、生成产物、子模块与远端分歧。
- [ ] 完成 Android 扩展页、脚本运行和跨设备连接的现场验收。

本轮按用户授权交付到 `main`；提交号、新鲜克隆、文档链接和推送后的三方 ref 证据记录于 Git 与任务日记。

## 验收边界

- 自动化证据必须证明生产白名单可解析、源码与 assets 逐字节一致、所有用户可见脚本元数据与导出名使用 Kiyori、Kiyori 环境变量和 ToolPkg ID 完整闭环。
- Debug APK 必须生成并完成包名、版本、签名、16 KiB 对齐和预置脚本资产检查。
- 真机上“扩展”抽屉显示、脚本启停、调试安装、目录型 ToolPkg 路由和跨设备连接仍需用户现场验收，完成前保持 `verification_pending`。

## 自动验证记录

- TypeScript：根普通脚本、GitHub、Linux SSH、Remote Kiyori、Windows Control 编译通过，GitHub 按既有 esbuild 入口生成。
- Node：编辑器 12 项、远程工具与配置页 31 项、终端输入 9 项通过；采用真实生成 JS 和宿主边界 mock，不代表 Android 或远程设备实测。
- Python：`ci.test.test_toolpkg_sync` 15 项通过；生产同步 `50/50`，缺失 `0`，使用 `--no-hot-reload`。
- JVM：`BuiltInPackageMetadataContractTest` 6 项与 `JsRuntimeConstantsContractTest` 1 项通过；全白名单 metadata、Kiyori ID、旧包缺失和目录常量转义合同已检查。
- `check_formal_readiness.py --repository . --require-main` 与 `git diff --check` 通过；PC companion 两个 PowerShell 入口语法解析通过。
- 两条 GitHub workflow 已接入编辑器与远程工具生成漂移和 Node 行为测试；远端 CI 未作为本轮通过证据。
- Debug：规定的 `:app:assembleDebug --no-daemon --console=plain` 成功，235 个任务中 26 个执行；`com.kiyori / 45 / 0.1.0`、Android Debug V2 单 signer、唯一 launcher 与 16 KiB ZIP 对齐通过。
- APK：`512626322` bytes，SHA-256 `7562D914E7AA3B9AEA0D1FF8B19392DBE5D9DD573B27B7707F184F43F0F25A00`；37 个普通脚本、13 个 ToolPkg 与 146 个包内文件和源码逐字节一致，包 ID、远程入口与 PC 资源闭环通过，无旧包文件名。
