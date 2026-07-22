---
status: accepted
date: 2026-07-22
---

# Kiyori 产品定位与 Operit AI 边界

## 背景

Kiyori 从 Operit 源码建立新的独立项目。项目需要长期吸收 Operit AI 更新，同时发展浏览器、视频、音乐、阅读、下载、文件管理和广告拦截等 Kiyori 能力。

如果继续让 Operit UI 拥有应用壳，Kiyori 的导航、设置和内容页面会被 AI 项目的页面模型限制；如果重写 AI runtime，则会失去现有能力和上游同步基础。

## 决策

- Kiyori 定位为由 Operit AI 驱动的全能型浏览器
- 浏览器与内容处理是产品主体
- Operit AI 融入 Kiyori，成为 AI 对话、理解、工具和自动化子模块
- Kiyori 拥有应用壳、顶层导航、系统设置、产品品牌、发行与内容域
- Operit AI 通过 Kiyori Capability API 操作内容域，不直接拥有内容页面状态
- 继续保留 `com.ai.assistance.operit`、`operit://`、数据库、备份、插件、ToolPkg、MCP 和其他互操作标识
- Kiyori 从未面向用户发布，继承的 Operit 导航不构成 Kiyori 用户接口合同，迁移时完整移除旧应用壳入口

## 影响

- 后续上游同步需要区分 AI 内部能力和 Operit 应用壳变更
- 新页面必须明确属于 Kiyori 系统、内容域或 AI 子模块
- 全局语言、产品级主题和其他跨域设置迁入 Kiyori 系统设置
- AI 设置只保留 AI 专属配置，并从 AI 中心与设置首页进入同一目的地
- 不实施全局 `Operit` 到 `Kiyori` 字符串或包名替换

## 相关资料

- [Kiyori 产品壳与导航架构](../architecture/kiyori_product_shell_and_navigation.md)
- [品牌与兼容性边界](../../TODO/formal_development_readiness/1_brand_and_compatibility.md)
- [产品壳与 AI 中心导航决策](0002_product_shell_and_ai_center_navigation.md)
