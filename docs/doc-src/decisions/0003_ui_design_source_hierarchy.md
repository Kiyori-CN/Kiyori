---
status: accepted
date: 2026-07-22
---

# UI 设计来源层级

## 背景

Kiyori 同时拥有两类设计来源：当前 Operit AI 原版界面提供成熟的 AI 视觉体系，旧 `kiyori-android` 提供 Kiyori 浏览器、首页、负一屏、媒体和文件等页面设计。如果逐页自由混用，两部分会形成明显不同的产品观感。

## 决策

- Kiyori 全局 UI 向 Operit AI 原版看齐
- Operit 原版负责颜色、排版、形状、组件、图标处理、动效、弹窗、设置行、液态玻璃、水波玻璃和交互密度
- `Kiyori-CN/kiyori-android` 负责 Kiyori 自有页面的内容结构、导航意图、浏览器行为和功能布局参考
- 已接受的 Kiyori 产品合同负责最终行为；参考实现不能覆盖本项目已经确认的页面归属和交互规则
- 旧 Kiyori 页面迁移时保留结构和行为，视觉表达改用 Operit 原版主题与组件
- 两份参考仓库只提供可定位到提交、文件和符号的来源证据，不成为运行依赖

当前参考基线：

- Operit：[ef00abc5099187b4665957e9697cb743c81fa154](https://github.com/AAswordman/Operit/tree/ef00abc5099187b4665957e9697cb743c81fa154)
- kiyori-android：[24a2dfa91f0a4166dc58e5c4732d11861173f766](https://github.com/Kiyori-CN/kiyori-android/tree/24a2dfa91f0a4166dc58e5c4732d11861173f766)

## 影响

- 新页面不得建立与 Operit 并行的硬编码颜色、Typography、Shape 或通用组件体系
- UI 评审需要同时检查功能来源和视觉来源
- 浏览器页面可以按旧 Kiyori 进行 source-port，但不能原样保留其独立硬编码主题
- 上游 Operit UI 更新需要先评估是否改变 Kiyori 共用的视觉 token 或组件合同
- 页面对照文档必须记录参考提交、源文件、目标文件和刻意差异

## 相关资料

- [Kiyori 产品壳与导航架构](../architecture/kiyori_product_shell_and_navigation.md)
- [产品壳与 AI 中心导航](0002_product_shell_and_ai_center_navigation.md)
- [产品壳实施计划](../../TODO/kiyori_product_shell/index.md)
