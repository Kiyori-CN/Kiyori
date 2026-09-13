---
status: completed
---

# 抽屉、标签、转场与 Back

## 底部抽屉状态

| 状态 | 表现 | 进入方式 | 离开方式 |
| --- | --- | --- | --- |
| Hidden | 抽屉位于窗口下方，scrim 不拦截触控 | 初始或关闭完成 | 打开任一 drawer route |
| Partial | 显示工具或功能页的主要部分 | 从 Hidden 打开；Expanded 下拉 | 上拉到 Expanded；下拉到 Hidden |
| Expanded | 顶边位于状态栏下方，可显示完整列表 | Partial 上拉 | 下拉回 Partial |

抽屉偏移是局部动画状态，业务 route 仍由 `WebSessionBrowserHostState.sheetRoute` 管理。窗口尺寸变化时按同一逻辑状态重新计算目标偏移，不把像素值写入 runtime。

## 手势判定

- 只在顶部手柄区域安装纵向 drag
- 拖动开始时停止正在执行的抽屉动画，使方向可立即反转
- 位移超过抽屉高度的约 `8%` 或速度超过阈值时按方向切换相邻状态
- 位移和速度都不足时吸附到最近状态
- Partial 向下进入 Hidden 后请求 route 关闭
- Expanded 向下只回到 Partial，需再次向下才关闭
- scrim 点击和底部“收起”直接请求关闭

## 抽屉转场

- Hidden 到 Partial 使用无回弹 spring，避免 overshoot 暴露 WebView 边缘
- Partial 与 Expanded 使用同一 spring，保持手势结束后的连续速度感
- route 从工具箱切到历史、收藏、下载或 Userscripts 时，抽屉不先关闭；内容在同一 surface 内做短距离淡入切换
- route 关闭时先让 surface 下移并降低 scrim，再释放触控层
- 页面弹窗和外部打开确认位于抽屉之上，不允许抽屉遮住权限决策

## 标签总览转场

- 标签总览是浏览器内全屏覆盖层，不是 App Shell 新 route
- 进入使用短淡入和轻微上移，退出反向执行
- 动画期间后方 BrowserScreen 和 AndroidView 继续 composition，不触发 reattach
- 选择标签只改变 active session，退出动画结束后展示已存在的目标 WebView
- AI 在总览打开时改变标签集合，网格使用稳定 `sessionId` key 更新，不重启动画整个页面

## Back 优先级

| 优先级 | 当前状态 | Back 结果 |
| --- | --- | --- |
| 1 | 网页 JS alert、confirm 或 prompt | 拒绝或关闭当前对话框 |
| 2 | 外部应用打开确认 | 取消本次外部打开 |
| 3 | 标签总览 | 关闭总览，活动标签不变 |
| 4 | 工具箱、历史、收藏、下载或 Userscripts 抽屉 | 关闭当前抽屉 |
| 5 | 地址栏编辑 | 取消编辑并恢复活动 URL |
| 6 | 活动 WebView 可后退 | 执行网页后退 |
| 7 | Browser Home 根 | 返回 Software Home |

抽屉 route 不建立内部 Back 栈。历史等详情关闭后回到浏览器主体；需要访问另一工具时重新打开工具箱。这与旧 Kiyori 的独立 drawer 行为一致，也避免出现系统 Back 和标题返回箭头语义不同的双重导航。

## 底栏按钮与 Back 的区别

- 底栏后退只操作网页历史，不关闭抽屉、标签总览或 Browser Home
- 覆盖层打开时 scrim 拦截底栏点击，因此不会绕过覆盖层状态
- 系统 Back 始终按完整优先级处理
- 地址栏最小化和 Browser Home 根 Back 都离开 Browser Home，但前者是显式 UI 动作，后者是系统导航

## 切换与生命周期

- Browser Home 进入时获取 APP_SHELL presentation lease
- 离开 Browser Home 时释放 lease；如 overlay 原先展开则恢复原 overlay，否则按现有规则恢复 indicator 或无 overlay
- 打开标签总览或抽屉不释放 lease
- 切换标签使用现有 `ensureSessionAttachedOnMain()`，不创建新 host
- UI route、窗口 resize 和动画不得调用 `loadUrl`、`reload`、`destroy`、`clearHistory` 或 Cookie API

## 异常与竞态

- AI 关闭当前标签：UI 从共享 registry 选择 runtime 决定的活动标签，或显示无标签状态
- AI 关闭全部标签：标签总览和底栏计数立即变为零；Browser Home 保持可用
- drawer route 打开期间 session 变为空：历史、收藏、下载和 Userscripts 仍可查看，模式切换保持可用
- renderer crash：继续使用现有关闭标签和提示路径，chrome 不尝试重建网页
- 横竖屏切换：drawer 逻辑状态保留，像素偏移重新计算；tabs 和 WebView owner 不变
- 输入法打开：地址编辑优先响应 Back；drawer 输入场景先由输入控件和系统 IME 处理，再按 route 关闭
