# Kiyori 开发任务索引

本页只提供开发入口与协作约定。每个专项的 `index.md` 是该专项状态、范围和验收证据的唯一入口；历史日志不再追加到本页。

## 最近整理

2026-09-06 [仓库结构与构建职责整理](kiyori_architecture_refactor/23_repository_structure_and_build_logic.md)
已完成：构建任务独立到 `buildSrc`，工具按职责归位，修复首启旧架构断言，完善目录与命名规则，并通过全量 Python 回归与 Debug APK 审计。

2026-09-06 [文档体系整理与开发入口优化](documentation_system_refinement/index.md)已完成：统一中文开发文档、压缩上下文、分离用户指南与详细契约、保留历史证据，并交付许可修复、验证结果与 Debug APK。其他专项的待验收状态保持原义。

## 按领域继续开发

| 领域 | 专项入口 | 关注点 |
| --- | --- | --- |
| 工程基线 | [正式开发准备](formal_development_readiness/index.md) | 五份准备清单、构建、设备和发行边界 |
| 架构 | [全项目架构重构](kiyori_architecture_refactor/index.md) | 真实状态所有者、包方向和阶段验收 |
| 浏览器 | [产品能力收口](kiyori_browser_product_completion/index.md) | 搜索、广告、媒体与交互 |
| 窗口与设置返回 | [导航及会话恢复](kiyori_browser_navigation_and_session_restoration/index.md) | 历史、来源、Back 与恢复 |
| 网络 | [应用代理](application_network_proxy/index.md) | 路由、就绪、故障及真实网络 |
| AI 运行 | [中断恢复](ai_interrupted_turn_recovery/index.md) · [模型能力](unified_model_capability_and_resumable_execution/index.md) | 提交、取消、重放和工具闭合 |
| AI 渲染 | [渲染可靠性](ai_chat_rendering_reliability/index.md) | Markdown、公式和工具内容边界 |
| AI 配置 | [供应商与协议](api_provider_configuration_redesign/index.md) | 配置来源、请求编译与 UI |
| 扩展 | [内置脚本契约](kiyori_extension_script_brand_migration/index.md) · [Bilibili](bilibili_toolkit/index.md) | 品牌、宿主变量与远程连接 |
| 搜索 | [OpenAI 搜索](openai_hosted_web_search/index.md) | 独立工具配置、证据与请求边界 |
| 终端 | [代码运行与环境](code_runner_terminal_toolchain/index.md) · [Ubuntu rootfs](kiyori_ubuntu_26_rootfs_upgrade/index.md) | PTY、工具链、安装和设备执行 |
| 平台 | [Shizuku 与日志](kiyori_shizuku_privileged_execution_and_log_migration/index.md) | 权限、执行身份和诊断 |
| 媒体 | [FFmpeg 运行时](ffmpeg_runtime_completion/index.md) · [native 与 16 KB](android_toolchain_16kb_native/index.md) | 进程、制品、解码与打包 |

完整专项见 [专项目录](catalog.md)。上表是阅读路径，不代表这些专项已经完成或正在由本轮修改。

## 证据与状态

| 状态 | 含义 |
| --- | --- |
| 待实施 / planned | 方案已记录，尚无实现证据 |
| 进行中 / in_progress | 当前范围仍有未完成工作 |
| 部分完成 / partial | 已交付部分成果，剩余范围明确记录 |
| 待验证 / verification_pending | 实现或本地验证已具备，仍缺必要设备、远端或用户验收 |
| 已完成 / completed | 本次范围和所需验收全部满足 |
| 受阻 / blocked | 无法继续的依赖、原因与最短下一步已记录 |

旧任务中的 `DONE`、`complete` 等历史标记保留原义。检查框只表示对应条目，不会自动覆盖专项的设备或发行状态。

## 新增与恢复任务

1. 先查找现有专项，避免同一目标出现多个状态源。
2. 在专项 index 中记录目标、范围、非目标、依赖、风险、计划和验收。
3. 分阶段更新当前事实与证据；把长期契约同步到 [正式文档](../doc-src/README.md)。
4. 历史结果标明日期及提交或产物，不把旧 APK 哈希和测试数量复制成当前结果。
5. 结束时明确已交付、待验收和下一步。不要把全部源码、日志、一次性提示词或工作区恢复数据写入正式文档。

## 历史查询

旧总索引逐节保存在 [历史记录](history/README.md)，原计划、检查结果和未完成验收仍可追溯。历史内容用于理解当时决策，恢复前重新核对源码、Git 和设备状态。

文档命名、分层与验证方式见 [文档维护规范](../doc-src/before_docing.md)。
