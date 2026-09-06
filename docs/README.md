# Kiyori 文档中心

本目录保存 Kiyori 的正式设计、开发、协议、测试、决策和长期任务文档。根 [`README.md`](../README.md) 面向用户与新贡献者；[`CONTEXT.md`](../CONTEXT.md) 定义项目语义和兼容性合同；本页负责把详细资料连接成一条可查找的阅读路径。

## 快速入口

| 目标 | 文档 |
| --- | --- |
| 了解产品定位与当前能力 | [`README.md`](../README.md) |
| 从安装到日常使用 | [用户指南](user-guide/README.md) |
| 阅读英文项目介绍 | [`README.en.md`](../README.en.md) |
| 查询术语、状态所有者和兼容边界 | [`CONTEXT.md`](../CONTEXT.md) |
| 查询领域级执行与生命周期边界 | [运行时契约](doc-src/contracts/README.md) |
| 查找全部父仓库文档 | [完整目录](CATALOG.md) |
| 准备开发环境并构建 | [构建指南](doc-src/dev-core/BUILDING.md) |
| 参与开发 | [贡献指南](doc-src/dev-core/CONTRIBUTING.md) |
| 理解仓库目录和本地输入边界 | [仓库布局](doc-src/dev-core/REPOSITORY_LAYOUT.md) |
| 理解 AI 对话详情、审计链与导入导出 | [AI 对话详情与完整审计](doc-src/dev-core/AI_CONVERSATION_AUDIT.md) |
| 运行本地与 CI 门禁 | [`ci/README.md`](../ci/README.md) |
| 查看当前长期工作 | [开发任务与验证索引](TODO/README.md) |
| 核对正式开发门禁 | [正式开发准备](TODO/formal_development_readiness/index.md) |

## 目录职责

- `user-guide/`：首次使用、扩展配置、网络媒体与数据说明。
- `doc-src/contracts/`：从精简 CONTEXT 按需进入的领域行为与生命周期契约。
- `doc-src/architecture/`：当前架构、核心运行时和跨模块设计。
- `doc-src/decisions/`：已经接受、具有长期影响的架构与产品决策。
- `doc-src/dev-core/`：构建、贡献、仓库布局、播放器和底层开发资料。
- `doc-src/feature-protocol/`：用户可见功能背后的协议、数据和执行合同。
- `doc-src/package-dev/`：脚本包、ToolPkg 和内置工具开发接口。
- `doc-src/research/`：依赖、外部 API 和技术方案的研究记录。
- `doc-src/test-example/`：测试示例、实验记录和可复核的诊断材料。
- `TODO/`：专项计划与验收；总入口只导航，专项目录列出各 index，`history/` 保存历史证据。
- `assets/`：按需创建主题目录，只提交被正式文档实际引用的图片和静态资源；不得保留零引用截图、历史导出或临时报告。
- `legal/`：仓库随附的第三方许可证正文。
- `.META/`：保留给经过审计的文档元数据和历史归档，不作为临时垃圾目录。

## 权威来源

同一事实只应有一个主要载体：

- 用户如何获取、构建和理解项目：根 `README.md`。
- 高频产品术语、所有权和不变量：根 `CONTEXT.md`；详细领域边界按需进入 `doc-src/contracts/`。
- 开发方式、验证和交付流程：`AGENTS.md`、`doc-src/dev-core/` 与 `ci/README.md`。
- 详细架构、API、schema、测试和决策：`doc-src/`。
- 当前工作、未完成验收和历史证据：`TODO/`。

历史 TODO 中的提交、哈希、APK 大小和测试数量是当时的证据，不代表当前工作树。复用前必须重新验证。

## 当前架构入口

- [Kiyori 产品定位与 Operit AI 边界](doc-src/decisions/0001_kiyori_product_positioning.md)
- [模态 AI 左抽屉导航](doc-src/decisions/0004_modal_ai_drawer_navigation.md)
- [UI 设计来源层级](doc-src/decisions/0003_ui_design_source_hierarchy.md)
- [Kiyori 产品壳与导航架构](doc-src/architecture/kiyori_product_shell_and_navigation.md)
- [浏览器插件平台与插件中心架构](doc-src/architecture/browser_plugin_platform.md)
- [统一模型能力与可恢复执行](doc-src/architecture/model_capability_and_resumable_execution.md)
- [OpenAI Hosted Web Search](doc-src/architecture/openai_hosted_web_search.md)
- [AI 对话详情与完整审计](doc-src/dev-core/AI_CONVERSATION_AUDIT.md)
- [FFmpeg 架构与开发指南](doc-src/dev-core/FFMPEG_ARCHITECTURE.md)

## 文档维护

新增、移动、归档或重写文档前，请阅读 [文档维护规范](doc-src/before_docing.md)。提交前至少运行 Markdown 链接检查，并确认文档中的命令、路径、版本和状态与当前实现一致。
