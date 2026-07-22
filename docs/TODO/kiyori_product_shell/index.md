---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: planning
baseline: 1b595a51e46f9d246d3d0ce38b755178e39f42a5
legacy_design_reference: 24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# Kiyori 产品壳与 AI 融合计划

## 目标

把当前由 Operit UI 拥有的应用壳迁移为 Kiyori 产品壳，建立软件首页三页空间、五个顶层入口、全屏 AI 中心、独立网页搜索和自适应布局，并为后续内容域接入 AI 准备能力与授权边界。

## 已确认设计

- Kiyori 是由 Operit AI 驱动的全能型浏览器
- Operit AI 是 Kiyori 的 AI 子模块
- 新页面视觉向 Operit 原版 UI 看齐，Kiyori 自有页面结构与浏览器行为参考旧 `kiyori-android`
- 项目从未面向用户发布，不保留旧 Operit 抽屉入口
- 启动进入软件首页
- 软件首页左侧为负一屏，右侧为全屏 AI 首页
- 负一屏和 AI 首页隐藏底部五入口
- AI 首页三横线按钮打开全屏 AI 中心
- AI 设置是 AI 中心子页，也从设置首页进入
- 原版“权限”高频入口进入 Kiyori 权限中心；设置首页进入同一个目的地
- 权限中心区分设备能力与 AI 工具授权，使用各领域唯一 owner，不新建状态副本
- 权限中心首页采用只读状态总览，点击分项进入唯一 owner 页面，不直接放置权限开关
- AI 中心“权限”卡片保留原版短标签与视觉，徽标只显示设备能力待处理数量，无待处理项时显示“正常”
- 高影响操作确认与 AI 操作记录共同归入权限中心的“AI 安全”分组，完整页面实现前不显示入口
- AI 操作采用 R0-R3 四级风险模型；`ALLOW` 只能直接放行 R0/R1，R2 默认单次确认，R3 每次确认且不能长期免确认
- 对话历史、新建和删除对话保留在 AI 首页，不进入 AI 中心
- 原左抽屉“AI 对话”入口删除，不进入 AI 中心
- 进入 AI 中心时不对 AI 首页施加位移、缩放、倾斜、动态圆角或动态阴影
- 帮助、关于、使用手册和应用语言不进入 AI 中心
- Terminal 第一阶段保留 AI 首页右上角入口，不新增第二入口
- 软件首页搜索卡保留“搜索”和“AI”按钮
- “搜索”进入只负责网页搜索的全屏页，“AI”进入 AI 首页
- 历史、书签、文件和小程序在各自页面中搜索
- 底部五入口只在五个根页面显示
- AI 中心、AI 首页、负一屏、子页和沉浸页面使用已确认的 Back 规则
- 每个顶层入口保留自己的子栈和滚动状态
- 负一屏首期包含收藏、书签、历史和下载
- 手机、平板和折叠屏共享导航状态并使用自适应布局
- 高影响 AI 操作需要显式授权和可查看操作记录

## 非目标

- 不在本计划中实现所有视频、音乐、阅读、下载、文件和广告拦截功能
- 不迁移兼容 namespace、协议、数据库、备份或插件标识
- 不重写 Operit AI runtime
- 不把旧 `kiyori-android` 作为源码依赖
- 不在未达成共识前决定 AI 中心最终分组和搜索窗口策略

## 工作分解

```text
kiyori_product_shell/
├── index.md
├── 1_app_shell_and_home_navigation.md
├── 2_ai_center_and_settings_ownership.md
├── 3_web_search_and_adaptive_layout.md
├── 4_capability_authorization_and_audit.md
└── 5_browser_source_port.md
```

1. [Kiyori App Shell 与首页导航](1_app_shell_and_home_navigation.md)
2. [AI 中心与设置归属](2_ai_center_and_settings_ownership.md)
3. [网页搜索与自适应布局](3_web_search_and_adaptive_layout.md)
4. [AI 能力授权与操作记录](4_capability_authorization_and_audit.md)
5. [浏览器 source-port](5_browser_source_port.md)

## 决策队列

- R2 与 R3 结构化确认页必须展示的事实、按钮和关闭语义
- 预测性返回、输入法展开时的横滑和浏览器网页历史规则
- 是否保留旧搜索窗口语义：进程首次进入浏览器时创建窗口，同一进程已有活动窗口时复用该窗口
- 网页搜索建议与搜索记录展示
- 是否接受负一屏首期只展示四个基础数据入口，并区分跨内容收藏与网页书签
- Terminal 是否在未来增加第二入口
- 高影响操作分级、授权期限、撤销语义和记录保留周期

## 总体验收

- Kiyori App Shell 成为唯一顶层导航 owner
- 手机端不存在从 AI 首页左边缘打开旧抽屉的路径
- 软件首页三页、底栏显示规则和 AI 页面状态保持符合架构文档
- AI 中心与设置首页进入同一个 AI 设置状态
- 对话历史只由 AI 首页原有历史选择器维护
- 全屏网页搜索不返回其他领域结果
- 首页搜索卡的 AI 按钮只进入 AI 首页
- Compact、Medium、Expanded 和分隔铰链布局均有自动检查和设备验收记录
- 新页面通过 Operit 视觉来源与旧 Kiyori 功能来源对照
- 高影响 AI 操作在授权前不执行，并产生可核查记录
- Operit AI 对话、工具、记忆、工作流和兼容标识保持可用

权威设计见 [Kiyori 产品壳与导航架构](../../doc-src/architecture/kiyori_product_shell_and_navigation.md)。
