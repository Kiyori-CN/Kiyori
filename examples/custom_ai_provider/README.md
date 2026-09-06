# 自定义 AI Provider 示例

本 ToolPkg 演示如何通过 `ToolPkg.registerAiProvider(...)` 注册可在模型设置中选择的自定义供应商。供应商 ID 为 `example_openai_compatible_provider`；实现面向 OpenAI 兼容接口，不声明完整供应商能力。

## 请求来源

- 使用模型配置中的 `apiEndpoint`、`apiKey` 和 `modelName`。
- 向兼容的 `/chat/completions` 入口提交请求。
- 尝试从兼容的 `/models` 入口读取模型列表。

## 使用步骤

1. 按 [ToolPkg 格式指南](../../docs/TOOLPKG_FORMAT_GUIDE.md) 准备并导入本目录对应的包。
2. 启用包，进入模型设置。
3. 选择“示例供应商”（注册 ID：`example_openai_compatible_provider`）。
4. 填写 `API Endpoint`、`API Key` 和 `Model Name`，使用实际服务验证。

包结构以 [manifest.json](manifest.json) 为准；密钥由用户配置，不写入示例或发布产物。
