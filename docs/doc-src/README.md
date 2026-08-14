# Kiyori 正式文档源

`doc-src/` 是 Kiyori 长期维护的设计与开发文档源。这里记录当前有效的架构、决策、协议、开发入口和可复核研究；当前工作进度与未完成验收保留在 [`../TODO/`](../TODO/README.md)。

## 目录结构

- `architecture/`：整体架构、核心模块、运行时与跨模块数据流。
- `decisions/`：已经接受且具有长期影响的架构和产品决策。
- `dev-core/`：构建、贡献、仓库布局、播放器与底层开发资料。
- `feature-protocol/`：具体功能、协议、数据和执行合同。
- `package-dev/`：脚本包、ToolPkg、工具类型和宿主 API。
- `research/`：外部依赖、API 和候选方案的研究记录。
- `test-example/`：测试示例、实验记录和问题分析。

## 推荐阅读顺序

1. 根 [`README.md`](../../README.md)：产品与构建入口。
2. 根 [`CONTEXT.md`](../../CONTEXT.md)：产品语言、所有权和兼容性合同。
3. [仓库布局](dev-core/REPOSITORY_LAYOUT.md)：模块、生成目录和本地输入。
4. [构建指南](dev-core/BUILDING.md) 与 [贡献指南](dev-core/CONTRIBUTING.md)。
5. 与任务相关的 `architecture/`、`decisions/` 或 `feature-protocol/` 文档。

## 维护规则

- 新文档放入最匹配的主题目录，并使用能长期表达主题的文件名。
- 方案、接口、状态所有者或用户可见行为变化时，同步更新对应的权威文档。
- 不在正式文档中保存临时聊天记录、大段日志或仅对一次执行有效的检查点。
- 移动文件前使用 `rg` 查找引用并同步修改相对链接、代码路径和测试。
- 构建产物不应依赖文档目录的偶然布局；需要机器消费时使用显式 schema 或清单。
- 详细格式、状态和归档要求见 [文档维护规范](before_docing.md)。

## 关键入口

- [Kiyori 产品壳与导航架构](architecture/kiyori_product_shell_and_navigation.md)
- [浏览器插件平台与插件中心架构](architecture/browser_plugin_platform.md)
- [统一模型能力与可恢复执行](architecture/model_capability_and_resumable_execution.md)
- [OpenAI Hosted Web Search](architecture/openai_hosted_web_search.md)
- [Kiyori 产品定位与 Operit AI 边界](decisions/0001_kiyori_product_positioning.md)
- [模态 AI 左抽屉导航](decisions/0004_modal_ai_drawer_navigation.md)
- [UI 设计来源层级](decisions/0003_ui_design_source_hierarchy.md)
