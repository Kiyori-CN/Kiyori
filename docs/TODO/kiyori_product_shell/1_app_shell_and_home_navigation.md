---
status: in_progress
---

# Kiyori App Shell 与首页导航

## 当前实现切片

- `KiyoriShellState` 已建立五个根目的地、三个首页页面、子页面可见性与 Back 决策
- Kiyori App Shell 已接管启动页面、首页 Pager、底部五入口和 AI Home 宿主位置
- 三页首页共享唯一 `PagerState` 和同一产品吸附合同。负一屏与软件首页继续使用原生
  `HorizontalPager` fling；永久 AI Home 已改用公开 API bridge，为当前手势记录独立会话并按
  严格大于半页、`400dp/s`、最多一页、LTR/RTL、边界和同一 spring 吸附，不再读取原生 Pager
  私有手势元数据
- 全屏 AI Center 已删除，按钮触发的模态 AI 左抽屉直接覆盖 AI Home 与 AI 一级页面
- 手机抽屉手势、倾斜缩放变换、平板旧侧栏和跨页面手势全局状态已从主导航调用链删除
- 浏览器首页已接入共享 WebSession；小程序、文件和设置当前只完成根页面骨架，领域内容与每个根页面的独立子栈仍待后续切片接入
- 真机手势、Back、旋转、折叠屏与流式对话持续性保持 `verification_pending`

## 迁移前基线

- Launcher Activity 为 `ui/main/MainActivity.kt`
- `MainActivity` 直接装配 `OperitApp`
- `OperitApp` 同时拥有路由、手机抽屉、平板侧栏和 AI 页面
- `PhoneLayout` 在根布局捕获水平拖动并控制 75% 宽抽屉
- `Screen.AiChat` 已经可以作为独立 AI 对话内容渲染

以上旧壳现状仅用于说明迁移来源；`PhoneLayout`、`TabletLayout`、`DrawerContent`、`NavigationComponents` 和抽屉专属外观实现已在当前切片删除。

## 目标结果

- 新的 Kiyori App Shell 成为 Launcher 内容根
- 顶层状态区分五个 `PrimaryDestination` 与三个 `SoftwareHomePage`
- 首页 Pager 固定为负一屏、软件首页、AI 首页
- AI 首页稳定挂载并保留对话、流式输出和输入草稿
- 三页移动跟随手指，释放使用同一速度、阈值与吸附模型；新手势可以中断尚未完成的 fling，Shell 页面状态只在 `settledPage` 后同步
- 负一屏与 AI 首页隐藏底栏，软件首页显示底栏
- 浏览器首页隐藏 Kiyori 五项底栏并显示浏览器专属底栏；小程序首页、文件管理首页和设置首页显示 Kiyori 五项底栏
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

- 已打开的模态 AI 抽屉 -> 关闭抽屉
- AI 一级页面 -> AI 首页
- AI 首页或负一屏 -> 软件首页
- 子页面 -> 所属根页面
- 非软件首页根页面 -> 软件首页
- 软件首页 -> 退出确认

## 需要保护的行为

- 当前会话、流式响应、附件和草稿不因左右滑动重建
- AI Home 覆盖层继续通过唯一 `PagerState` 跟手移动；等价手势桥在不依赖尚未同步
  `SoftwareHomePage` 的前提下支持动画中反向拖动和普通点击，并在表格、代码、公式或内嵌预览
  声明横向手势所有权时让位
- 悬浮窗、外部 Intent、默认助手和通知进入主聊天的路径保持有效
- `ScreenRouteRegistry` 和 ToolPkg 动态页面仍可从新的 owner 导航
- 系统栏、输入法和预测性返回状态由产品壳统一处理

## 验收

- 冷启动稳定进入软件首页中间页
- 左右分别只能到达负一屏和 AI 首页
- 两侧页面底栏不可见，中间页底栏可见
- 浏览器首页显示浏览器专属底栏，小程序、文件和设置根页面显示 Kiyori 五项底栏，所有子页与其他沉浸页隐藏 Kiyori 底栏
- AI 首页不存在打开抽屉的边缘手势，只能通过三横线按钮打开
- 切走再返回 AI 首页时，对话状态未丢失
- 切换底部入口后，各根页面子栈和滚动状态未丢失
- 屏幕旋转、窗口缩放和进程状态恢复不产生重复 AI 根页面
- Back 与手势状态表测试通过，真机交互保持 `verification_pending` 直到设备验收
- Software Home -> AI Home 后立即反向拖动可以接管未完成的动画，且位置连续、无约一秒触摸失效窗口

## 当前验收结论

- 已通过：Shell 状态 JVM 测试 `23/23`、完整 Debug JVM 测试 `394/394`、Debug Kotlin 编译、47 项 CI Python 测试、正式开发准备门禁、Android lint、`git diff --check`、旧顶层抽屉引用清理和 `assembleDebug`
- 最新构建证据：`app-debug.apk`，生成于 `2026-07-23 03:31:37 +08:00`，大小 `416308263` 字节，`com.kiyori`，版本 `45 / 0.1.0`，SHA-256 `38F1F9A37CD55902428E1E6D42989DCCCE0C232C0D88E72764B3603AFED1C494`
- 部分完成：五个根页面与全屏搜索已建立宿主，但除软件首页与 AI 首页外仍是骨架
- 未完成：每个根页面的独立子栈和滚动状态、真实搜索提交、负一屏数据、自适应 Rail/双栏
- 待验证：真机左右滑动、聊天内部手势仲裁、Back、旋转、折叠姿态与流式对话持续性
- 2026-08-18 设备纠正：中部慢拖和短促 flick 已确认 AI → Home 比真正 Pager 路径更容易切页；
  物理边缘反方向进入 Home 已确认属于系统 Back。根因修复现已完成本地实现；专项 JVM
  `85/85`、完整 App JVM `239 suites / 1404 tests`、AndroidTest Kotlin/Java 编译、architecture
  `phase=m03`、formal readiness、规定 Debug 构建与 APK 静态审计通过。修复后目标设备矩阵继续由
  [三页首页横向手势一致性修复](../home_pager_gesture_consistency/index.md) 跟踪。
