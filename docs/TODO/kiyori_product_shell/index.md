---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: in_progress
baseline: 12f33da6
legacy_design_reference: 24a2dfa91f0a4166dc58e5c4732d11861173f766
---

# Kiyori 产品壳与 AI 融合计划

## 当前进度

- App Shell 首个切片已实现五个根目的地、三页首页 Pager、底栏可见性和 Shell Back 状态；模态 AI 左抽屉已经取代全屏 AI Center
- AI 对话宿主保持单实例挂载，Terminal 与对话历史入口继续留在 AI 首页
- 旧顶层抽屉、平板旧侧栏、透视变换、边缘拖动、全局手势持有者及抽屉专属主题设置已删除
- 浏览器首页首期已接入 WebSession 共用宿主并完成 Debug 构建，真机验收保持 `verification_pending`；小程序、文件管理和设置仍是根页面骨架
- 全屏网页搜索和负一屏已建立页面骨架，但搜索提交、浏览器窗口合同与真实数据仍未接入
- 模态 AI 左抽屉的无手势容器、一级页面状态、AI 设置来源返回和原版信息密度已在本切片实现；权限总览继续由后续切片完成
- 首页三页已共享 Pager 输入与 fling；抽屉面板从状态栏底部开始且保留全屏遮罩；ToolPkg 一级根开始严格服从自身 `keepAlive` 合同
- 2026-07-23 累积自动验证通过：Debug Kotlin 编译、定向 Shell 测试 `23/23`、完整 Debug JVM 测试 `394/394`、47 项 CI Python 测试、正式开发准备门禁、Android lint、`git diff --check` 和 `assembleDebug`
- 2026-07-24 浏览器首页首期通过正式准备门禁、源码审计、`git diff --check` 与 Debug APK 构建；本轮未运行 JVM、UI、设备或联网测试
- 当前告警复采完成：Kotlin 告警从 453 降至 436，lint 从 59 warning 降至 52 warning；本轮新增和高风险候选已消失，CMake 仅保留第三方 OpenFST 与本机工具链告警
- 最新 Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于 `2026-07-24 05:09:59 +08:00`，大小 `442753282` 字节，包名 `com.kiyori`，版本 `45 / 0.1.0`，SHA-256 `D824529560D448DB695823277946ED207616C5172454D29BC750CD2B028E435C`
- 自动编译和 JVM 状态测试不替代真机手势、Back、旋转、折叠屏及流式对话持续性验收

## 目标

把当前由 Operit UI 拥有的应用壳迁移为 Kiyori 产品壳，建立软件首页三页空间、五个顶层入口、模态 AI 左抽屉、独立网页搜索和自适应布局，并为后续内容域接入 AI 准备能力与授权边界。

## 已确认设计

- Kiyori 是由 Operit AI 驱动的全能型浏览器
- Operit AI 是 Kiyori 的 AI 子模块
- 新页面视觉向 Operit 原版 UI 看齐，Kiyori 自有页面结构与浏览器行为参考旧 `kiyori-android`
- 项目从未面向用户发布，不保留旧 Operit 抽屉入口
- 启动进入软件首页
- 软件首页左侧为负一屏，右侧为全屏 AI 首页
- 负一屏和 AI 首页隐藏底部五入口
- AI 首页及 AI 一级页面的三横线按钮打开模态 AI 左抽屉，不提供边缘或拖动手势
- AI 设置是抽屉一级目标，也从设置首页进入同一页面并保留不同 Back 来源
- 抽屉“权限”高频入口进入 `Screen.ShizukuCommands`；设置首页的权限入口进入 Kiyori 权限中心
- 权限中心区分设备能力与 AI 工具授权，使用各领域唯一 owner，不新建状态副本
- 权限中心首页采用只读状态总览，点击分项进入唯一 owner 页面，不直接放置权限开关
- 抽屉“权限”卡片保留原版短标签与视觉，并继续进入 `Screen.ShizukuCommands`
- 高影响操作确认与 AI 操作记录共同归入权限中心的“AI 安全”分组，完整页面实现前不显示入口
- AI 操作采用 R0-R3 四级风险模型；`ALLOW` 只能直接放行 R0/R1，R2 默认单次确认，R3 每次确认且不能长期免确认
- 对话历史、新建和删除对话保留在 AI 首页，不进入抽屉
- 抽屉保留“AI 对话”入口，用于返回现有 AI Home，不创建会话或清空草稿
- 打开抽屉时不对底层页面施加位移、缩放、倾斜、动态圆角、阴影或透明度
- 帮助、关于、使用手册和应用语言不进入抽屉
- Terminal 第一阶段保留 AI 首页右上角入口，不新增第二入口
- 软件首页搜索卡保留“搜索”和“AI”按钮
- “搜索”进入只负责网页搜索的全屏页，“AI”进入 AI 首页
- 历史、书签、文件和小程序在各自页面中搜索
- 底部五入口只在五个根页面显示
- 模态抽屉、AI 一级页面、AI 首页、负一屏、子页和沉浸页面使用已确认的 Back 规则
- 每个顶层入口保留自己的子栈和滚动状态
- 负一屏首期包含收藏、书签、历史和下载
- 手机、平板和折叠屏共享导航状态并使用自适应布局
- 高影响 AI 操作需要显式授权和可查看操作记录

## 非目标

- 不在本计划中实现所有视频、音乐、阅读、下载、文件和广告拦截功能
- 不迁移兼容 namespace、协议、数据库、备份或插件标识
- 不重写 Operit AI runtime
- 不把旧 `kiyori-android` 作为源码依赖
- 不在未达成共识前决定权限总览最终分组和搜索窗口策略

## 工作分解

```text
kiyori_product_shell/
├── index.md
├── 1_app_shell_and_home_navigation.md
├── 2_modal_ai_drawer_and_settings_ownership.md
├── 3_web_search_and_adaptive_layout.md
├── 4_capability_authorization_and_audit.md
└── 5_browser_source_port.md
```

1. [Kiyori App Shell 与首页导航](1_app_shell_and_home_navigation.md)
2. [模态 AI 左抽屉与设置归属](2_modal_ai_drawer_and_settings_ownership.md)
3. [网页搜索与自适应布局](3_web_search_and_adaptive_layout.md)
4. [AI 能力授权与操作记录](4_capability_authorization_and_audit.md)
5. [浏览器 source-port](5_browser_source_port.md)

浏览器首页首期共享运行时与 App Shell 适配见 [浏览器首页与 WebSession 共用计划](../kiyori_browser_home_websession/index.md)。

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
- 模态 AI 抽屉与设置首页进入同一个 AI 设置页面与持久状态，并按来源返回
- 对话历史只由 AI 首页原有历史选择器维护
- 全屏网页搜索不返回其他领域结果
- 首页搜索卡的 AI 按钮只进入 AI 首页
- Compact、Medium、Expanded 和分隔铰链布局均有自动检查和设备验收记录
- 新页面通过 Operit 视觉来源与旧 Kiyori 功能来源对照
- 高影响 AI 操作在授权前不执行，并产生可核查记录
- Operit AI 对话、工具、记忆、工作流和兼容标识保持可用

权威设计见 [Kiyori 产品壳与导航架构](../../doc-src/architecture/kiyori_product_shell_and_navigation.md)。
