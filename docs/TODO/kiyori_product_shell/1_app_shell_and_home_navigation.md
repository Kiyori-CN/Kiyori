---
status: planned
---

# Kiyori App Shell 与首页导航

## 当前情况

- Launcher Activity 为 `ui/main/MainActivity.kt`
- `MainActivity` 直接装配 `OperitApp`
- `OperitApp` 同时拥有路由、手机抽屉、平板侧栏和 AI 页面
- `PhoneLayout` 在根布局捕获水平拖动并控制 75% 宽抽屉
- `Screen.AiChat` 已经可以作为独立 AI 对话内容渲染

## 目标结果

- 新的 Kiyori App Shell 成为 Launcher 内容根
- 顶层状态区分五个 `PrimaryDestination` 与三个 `SoftwareHomePage`
- 首页 Pager 固定为负一屏、软件首页、AI 首页
- AI 首页稳定挂载并保留对话、流式输出和输入草稿
- 负一屏与 AI 首页隐藏底栏，软件首页显示底栏
- 浏览器首页、小程序首页、文件管理首页和设置首页显示同一底栏
- 全屏搜索、子页面和沉浸页面隐藏底栏
- 手机端旧抽屉手势和布局彻底移除
- 每个顶层入口保留自己的子栈和滚动状态

## 实施边界

1. 先提取可独立挂载的 AI conversation surface，不改聊天 runtime。
2. 建立 Kiyori Shell 状态、首页 Pager 和五入口导航。
3. 将 AI conversation surface 挂入 AI 首页。
4. 接入页面可见性、底栏和窗口 inset。
5. 删除 `PhoneLayout` 抽屉手势及不再使用的抽屉状态。
6. 平板侧栏只在新自适应规则落地后删除，不能留下第二套顶层导航 owner。

## Back 状态

- AI 中心 -> AI 首页
- AI 首页或负一屏 -> 软件首页
- 子页面 -> 所属根页面
- 非软件首页根页面 -> 软件首页
- 软件首页 -> 退出确认

## 需要保护的行为

- 当前会话、流式响应、附件和草稿不因左右滑动重建
- 悬浮窗、外部 Intent、默认助手和通知进入主聊天的路径保持有效
- `ScreenRouteRegistry` 和 ToolPkg 动态页面仍可从新的 owner 导航
- 系统栏、输入法和预测性返回状态由产品壳统一处理

## 验收

- 冷启动稳定进入软件首页中间页
- 左右分别只能到达负一屏和 AI 首页
- 两侧页面底栏不可见，中间页底栏可见
- 其余四个根页面显示底栏，所有子页与沉浸页隐藏底栏
- AI 首页不存在打开旧抽屉的边缘手势
- 切走再返回 AI 首页时，对话状态未丢失
- 切换底部入口后，各根页面子栈和滚动状态未丢失
- 屏幕旋转、窗口缩放和进程状态恢复不产生重复 AI 根页面
- Back 与手势状态表测试通过，真机交互保持 `verification_pending` 直到设备验收
