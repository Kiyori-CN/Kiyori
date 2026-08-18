---
fork: https://github.com/Kiyori-CN/Kiyori
upstream_reference: D:/10_Project/Operit@ef00abc5099187b4665957e9697cb743c81fa154
status: verification_pending
---

# 插件运行时、Kiyori 存储与 AI 首页输入修复

## 原有问题

- Android 构建只打包 `app/src/main/assets/packages/` 的普通脚本；白名单中的目录型 ToolPkg 依赖 CI 先运行同步脚本，因此直接构建的 APK 没有预置插件。
- 市场协议中的 `package` 实际承载 `toolpkg_v2`，但部分用户可见文案仍称为工具包或 Package，和包管理页中的普通脚本包混淆。
- Kiyori 的公共数据根目录及少数独立模块曾默认写入 `Download/Operit`，MCP 也曾维护第二套目录选择逻辑；本轮统一到 `Download/Kiyori`。
- AI Home 上方的 `scrollable` 直接暴露 `PagerState.isScrollInProgress`。页面视觉上已经到位但 fling 尚未结束时，普通按下会立即接管滚动并触发回吸。

## 目标

1. Gradle 构建根据生产白名单从 `examples/` 生成预置 `.toolpkg` 资产，不提交生成二进制，也不要求构建前手工同步。
2. 保留 Operit 市场 wire type、ToolPkg ID、namespace 与注册接口；用户界面把 `toolpkg_v2` 统一称为插件，把普通 JS/TS/HJSON 项目继续称为包或脚本包。
3. Kiyori 新建的公共数据统一写入 `Download/Kiyori`。旧 Operit 数据只通过用户显式选择的导入流程读取，不自动扫描、复制或删除。
4. 普通点击等待触摸阈值并交给 AI 页面，真实水平拖动仍能接管同一 `PagerState` 并中断未结束的 fling。

## 非目标

- 不修改市场地址、下载协议、发布仓库、wire type 或 `com.operit.*` 插件标识。
- 不重写 PackageManager、ToolPkg 生命周期或市场下载器。
- 不新增旧目录回退、自动迁移或兼容分支。
- 不在本任务中升级依赖或操作设备；构建、Lint 和测试只在获得明确授权后执行。

## 验收状态

- [x] 源码已为生产白名单接入目录型 ToolPkg 生成任务，CI 不再向源码 assets 写入同一批 `.toolpkg`；Debug 构建已确认 APK 包含全部 11 个预置 ToolPkg。
- [x] 插件页、市场筛选和发布文案已区分 ToolPkg 插件与普通脚本包。
- [x] 市场版本检查已与 Kiyori 产品版本拆分；本任务封板时使用 Operit `1.12.0+4`
  兼容基线，后续在补齐 `+9` ToolPkg 与市场契约后由专项任务提升为 `1.12.0+9`，始终避免
  用 Kiyori `0.1.0` 产品版本判断生态插件。
- [x] 插件导入提示、内置编辑器和调试工具已指向 Kiyori 宿主包，同时保留 Operit 插件与广播协议标识。
- [x] 公共路径所有者、MCP、Skill、插件配置、工作区、模型、导出、备份、日志及相关用户提示已改为 Kiyori；真机文件系统待验证。
- [x] AI Home 覆盖层已在触摸阈值前隐藏 pager 的动画滚动状态，真实拖动仍委托同一 `PagerState`；真机交互待验证。
- [x] 2026-08-18 目标设备纠正已落实到本地实现：删除旧 AI 默认 fling 接线，使用当前手势会话
  bridge 执行严格半页、`400dp/s`、单页边界和同一 spring；AI 宽表格、代码、公式和内嵌预览
  通过多 owner 状态取得横向手势优先权。修复后目标设备验收仍由
  [三页首页横向手势一致性修复](../home_pager_gesture_consistency/index.md) 接续。
- [x] `assembleDebug` 已通过，最新 APK（`2026-07-23 19:06:58 +08:00`，SHA-256 `46948D7C83D982221E19F0C0B8543CD308C2DBEFE85D582812F229FAD57A2695`）中包含全部 11 个 ToolPkg；构建目录生成文件与 APK 资产逐项哈希一致。
- [ ] Lint、市场下载安装、抽屉/输入框插件与真机快速点击验证另行执行。
