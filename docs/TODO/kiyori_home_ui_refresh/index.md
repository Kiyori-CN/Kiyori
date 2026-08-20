---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
reference: D:/10_Project/kiyori-android
status: verification_pending
---

# Kiyori 首页 UI 重设计

本任务替换尚未发布的旧首页。首页主体只保留 `Kiyori` 标题、极小圆环标志和同背景搜索框，并把天气、浏览器窗口总览与 AI 快捷动作接入现有真实运行时。

## 固定合同

- 删除旧首页大型软件图标、副标题、独立 Search/AI 按钮和输入提示左侧搜索图标。
- 搜索框内部与页面背景同色，无阴影；彩色渐变只形成约 `1dp` 边框，不绘制外侧扩散。
- Search/AI 位于同一个 `120×32dp` 圆角边框内并严格等分，文字和图标尺寸不随边框缩小；选项本身不导航，主框按选中模式进入全屏网页搜索或保留现有会话与草稿的 AI 首页。
- 常规搜索框高 `114dp` 且顶部描边位于可用首页高度 `38.2%`；短布局高 `96dp` 并保持标题完整可见。
- 模式在返回首页和旋转后保持，冷启动默认 Search；提示文案随模式切换。
- 左上角天气使用设备粗略/精确定位、城市解析和 Open-Meteo 当前天气；冷启动可显示仍在展示
  时限内的已验证成功快照，不展示虚假数据。
- 右上角显示真实窗口总数，并直接打开 `WebSessionBrowserTabOverview` 所属窗口总览。
- 附件、语音、相机均先进入 AI Home，再消费一次性动作；语音复用现有全屏语音会话。
- AI 模式点击主框后，AI Home settled 再由真实输入框消费一次性聚焦动作并唤起输入法。
- 所有入口共用同一个全屏搜索页浏览模式控件：睁眼表示普通模式，闭眼表示无痕模式；切换只显示短时底部小提示，不显示长说明。
- 手机、平板和横屏保持居中单列，不保留旧平板双栏。

## 状态所有权

- Browser window 状态继续由唯一 `StandardBrowserSessionTools` / `WebSessionBrowserHost` 持有。
- 首页仅投影窗口数量并发出打开原窗口总览的命令。
- AI 快捷动作使用带请求 ID 的一次性状态，AI Home settled 后只消费一次。
- 天气拥有明确的权限、快照首显、定位、刷新和错误状态；持久数据只接受完整成功结果，不保存或
  显示未确认的城市与温度。

## 非目标

- 不新增 Google Play Services 定位依赖，不实现天气预报详情页。
- 不复制窗口总览 UI，不复制附件、相机或语音实现。
- 不改变 AI `browser_*` 协议、兼容 namespace、持久化 key 或 WebView 所有权。
- 不运行 Release，不提交、不推送，不安装 APK 或操作设备。

## 实施文档

- [1_home_visual_and_adaptive_layout.md](1_home_visual_and_adaptive_layout.md)
- [2_weather_and_browser_windows.md](2_weather_and_browser_windows.md)
- [3_ai_quick_actions.md](3_ai_quick_actions.md)
- [4_validation.md](4_validation.md)
- [5_home_and_search_interaction_refinement.md](5_home_and_search_interaction_refinement.md)
- [6_incognito_lifecycle_and_window_cards.md](6_incognito_lifecycle_and_window_cards.md)

## 当前状态

- [DONE] 用户确认产品与交互合同
- [DONE] 正式开发准备门禁
- [DONE] 首页、天气、窗口和 AI 快捷动作实现
- [DONE] 上一版自动验证与 Debug APK 产物核验
- [DONE] 搜索框黄金中线、等宽圆角分段和共享方框数字图标细化及静态检查
- [DONE] 本次细化后的定向测试、Kotlin 编译、Debug APK 构建与产物核验
- [DONE] 首页分段框、AI 输入聚焦和全屏搜索无痕交互细化
- [DONE] 无痕代际 Profile、历史隔离、纵向窗口卡片与 Debug APK 构建
- [DONE] 首页黄金比例基准改为搜索框顶部描边，标题图标、搜索框高宽和自适应留白完成微调
- [DONE] 天气成功快照首显、短时 last-known 定位、并行城市/天气刷新和失败保持完成定向验证
- [PENDING] 真机视觉和交互验收
