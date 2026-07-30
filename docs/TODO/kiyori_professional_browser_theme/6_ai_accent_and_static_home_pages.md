# 蓝色应用强调、浏览器保护域与三页面静态复刻

本文件记录第六阶段已经完成的实现与构建证据。当前默认色板、文件容量、设置首页入口和浏览器设置边界已由[第七阶段](7_ai_theme_settings_and_browser_settings_removal.md)取代；以下旧色值和当时的空按钮范围仅保留为历史事实。

## 目标

- 为 AI、设置和应用壳建立可自定义的蓝色应用强调色
- 让浏览器主页面、全屏搜索、四行菜单和浏览器子页保持固定中性色
- 修正浏览器工具箱图标、Software Home 底栏反馈和 AI 语音动作
- 以 `D:\\10_Project\\kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766` 为唯一视觉源码，静态复刻负一屏、文件管理首页和设置首页

## 设计问答

### 为什么不把浏览器也染成蓝色

浏览器 chrome 的任务是承载网页和重复导航。地址栏、底栏、菜单和子抽屉使用固定中性色，可以避免用户自定义颜色降低 URL、禁用态和网页边界的可读性，也能保持 Browser Home 与 WebView 加载空白的连续性。

### 为什么不只让 AI 使用蓝色

primary/secondary 是应用级语义 token。AI、设置控件和软件壳选中态共享它们，才能形成稳定的操作反馈；未来主题入口迁移到系统设置时也只需要迁移入口。固定布局的多彩图标和分类渐变不是交互 token，不跟随主题改色。

### 为什么不用旧首页蓝 `#2F6FED`

旧蓝与白色对比度为 `4.548:1`，只略高于普通文字的最低门槛。`#2563EB` 保留相近的现代钴蓝观感，同时把白字对比度提高到 `5.169:1`。低饱和 secondary `#4F6B95` 提供层次，避免界面只有一种高饱和蓝。

### AI 顶栏是否应该变成蓝色

不应该。AI 顶栏继续使用白色 background、近黑内容和细分隔线；蓝色集中在发送、用户消息、活动工具和选择态。这样长对话阅读不会被大面积头部颜色打断。

### 空输入语音按钮为什么与发送按钮不同

空输入时它是与附件、工具并列的模式入口，不是确认动作，因此使用透明容器和次级图标色。出现可发送文本、取消或排队动作时，右侧按钮恢复实心语义容器，保证主动作层级。

### 三个复刻页是否响应主题色

不响应。它们使用参考源码明确指定的灰底、白色卡片、分类渐变和多彩图标。所有按钮保留触控语义但回调为空，不改变导航、文件、数据或浏览器状态。

## 色板合同

| 角色 | 亮色 | 暗色 | 内容色 |
| --- | --- | --- | --- |
| `primary` | `#2563EB` | `#AFC6FF` | `#FFFFFF` / `#0E2D61` |
| `primaryContainer` | `#DCE8FF` | `#184A9A` | `#132F56` / `#DCE8FF` |
| `secondary` | `#4F6B95` | `#C3D0E4` | `#FFFFFF` / `#24354C` |
| `secondaryContainer` | `#E7EEF8` | `#354968` | `#233A5C` / `#E7EEF8` |

背景、surface、outline、error 和其他语义角色继续使用已通过验证的中性方案。浏览器主题使用上一阶段的中性 primary/secondary，并忽略应用自定义颜色。

## 实施范围

1. 增加应用强调色与浏览器中性色两套完整 ColorScheme，并保持主题设置 key、字体、背景媒体和气泡个性化格式不变
2. 在实际浏览器屏幕和 App Shell 浏览器子页边界嵌套中性主题；不改变 Browser Runtime、WebSession 或 WebView owner
3. 固定两个搜索框的黑色边框，复制旧版立方体工具集合图标，并保留菜单中的手提工具箱图标
4. 删除 Software Home 底栏的选中胶囊、圆形裁剪和可见 indication；该阶段的 primary tint 已由
   `2026-07-30` 视觉修订替换为精确 `#FFC153` 填充、页面背景色内部细节和每次点击均可重播的
   压缩—弹性放大动效
5. 同步修改 Classic 和 Agent 两种 AI 输入样式的空输入语音动作
6. 复制旧版 9 个描边 Vector、4 个存储 PNG 和 4 个设置顶栏 PNG，并核验 SHA-256 一致
7. 新增三个独立静态页面实现；删除旧设置首页双入口、宽度布局枚举和只服务该入口的导航来源
8. 更新主题、浏览器、Shell 和静态页面合同测试

## 验证

- 应用亮暗色板精确值和所有文字/容器对比度
- 当前固定应用主题和浏览器保护色域保持逐角色隔离；本历史阶段的用户自定义颜色验证项已取消
- 两个浏览器搜索框共享固定黑色边框合同
- 三个静态页面的分组数、项目数、顺序和关键几何常量
- 现有 `KiyoriShellStateTest`、`KiyoriSoftwareHomeSearchTest` 和 `WebSessionBrowserChromeLayoutTest`
- `git diff --check`
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`
- `./gradlew :app:assembleDebug --no-daemon --console=plain`
- APK 大小、时间、SHA-256、包名、版本、签名和 ZIP 对齐

## 验收边界

源码和自动检查不能证明实际像素、字体光栅化、触控反馈、状态栏或不同设备密度下的视觉结果。Debug APK 生成后仍保持 `verification_pending`，等待目标设备完成默认亮色、暗色、自定义颜色和三个静态页面的视觉验收。

## 实施结果

- [DONE] 应用强调域使用 `#2563EB / #4F6B95`，AI 与共享设置响应主题；浏览器主界面、全屏搜索、四行菜单和浏览器子页使用固定中性色域
- [DONE] 普通地址栏与全屏搜索框使用固定黑色 `1dp` 边框，Software Home 保留五色描边和固定蓝色 Search/AI 选中反馈
- [DONE] App Shell 与浏览器底栏、浏览器四行菜单使用 Kiyori 自有空心描边资源；右下角菜单与工具箱使用不同图标
- [DONE] Classic 与 Agent 输入的空输入语音按钮使用透明容器，Software Home 底栏不绘制圆形点击阴影
- [DONE] 负一屏、文件管理首页和设置首页按 `kiyori-android@24a2dfa9` 完成静态复刻；16 个新增参考资源逐文件 SHA-256 一致，所有页面动作为空
- [DONE] 删除旧设置首页双入口、宽度布局枚举、`RouteEntrySource.KIYORI_SETTINGS` 及专用返回逻辑
- [DONE] 最终聚焦 JVM 测试 `50/50`、`git diff --check`、正式开发准备门禁和 `:app:assembleDebug` 通过
- [DONE] Debug APK 为 `449500158` 字节，SHA-256 `B7DE438B955CEC01962AEFCE08B90E07C038DDED672C97ED1AAFCF80A6384309`，包名 `com.kiyori`，版本 `45 / 0.1.0`，V2 签名和 16 KB ZIP 对齐通过
- [ ] 真机默认亮色、暗色、自定义颜色、触控反馈和三页面视觉矩阵待验收

[DONE]
