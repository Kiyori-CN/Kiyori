# AI、浏览器与产品壳视觉整合

## AI 顶栏

默认 AI 顶栏使用 `background`，内容使用 `onBackground`，tonal/shadow elevation 为零。非透明顶栏底部绘制 `0.5dp outlineVariant` 分隔线，使顶栏与页面连续但边界清楚。

顶栏颜色由固定应用主题决定，不提供透明工具栏、自定义 AppBar 或强制前景色入口。系统状态栏保持
透明，图标明暗跟随当前浅深主题。

AI 电脑和工作区活动状态使用 `primaryContainer` 圆形容器与 `onPrimaryContainer` 图标。普通状态保持透明容器和顶栏内容色。活动态不能只把图标 tint 改成浅灰，否则会与禁用态混淆。

## AI 对话

- 用户默认消息：蓝色 `primaryContainer / onPrimaryContainer`
- AI 默认消息：`surface / onSurface`
- 系统与思考过程：`surfaceVariant / onSurfaceVariant`
- 输入框：`surface` 或 `surfaceContainerHigh`，边框使用 `outlineVariant`
- 发送和确认：`primary / onPrimary`
- 模型、角色、历史、附件与工具选中态：使用 primary/secondary container 表达应用强调

用户保存的气泡颜色、背景图片、玻璃效果和独立消息字体继续生效。默认主题不覆盖显式个性化。

## AI 抽屉

抽屉 panel 使用 `surface`，普通行使用 `surfaceContainerLow`，选中行使用 `secondaryContainer`。标题、Wi-Fi 图标和普通入口使用 `primary`，在亮色下表现为近黑。在线/离线圆点继续绿/红。

抽屉不增加黑色品牌头块、渐变、专属背景色或新的主题设置。

## Software Home

- Search/AI 分段按产品迭代恢复固定明暗蓝色文字与 `10%` 同色背景
- 搜索框恢复既有五色 `1dp` 闭环渐变描边，不增加外发光、模糊或阴影
- 搜索框、标题、天气和窗口数继续使用共享背景与内容色
- 固定彩色强调只属于 Software Home；固定应用 primary/secondary 独立作用于 AI 和软件壳，不传播到浏览器 chrome。设置页由专用设置 token 与语义图标 tone 表达层级

## 浏览器 chrome

- 顶栏、底栏、Browser Home 根与 WebView 宿主使用同一 `background`
- 普通地址栏与全屏搜索输入都使用浏览器 `background`，保证框内外背景一致；两者始终使用固定黑色 `1dp` 边框
- 两个顶部搜索行统一外边距、左右 `40dp` 单按钮槽、`6dp` 间距和 `42dp` 搜索框高度
- Tabs 选中态使用浏览器中性 `primary/onPrimary` 或 `primaryContainer/onPrimaryContainer`
- 菜单和子抽屉使用 surface 层级，不增加 tonal elevation 染色
- Profile 反馈使用 `inverseSurface/inverseOnSurface`，同时适配亮暗模式
- App Shell 五按钮、浏览器底栏和浏览器四行菜单只使用 Kiyori 自有空心描边 Vector，不混入 Material 实心图标；浏览器底栏工具集合与菜单手提工具箱使用不同资源
- 两类底栏复用 `16/0/16/6dp` 边距、五等分槽位、`44dp` 点击区和 `26dp` 图标；浏览器菜单按四行内容自适应，第四行三个 `46×36dp` 动作直接分布
- 源码预览的代码块保留深色代码 surface，不强制灰白

WebView 宿主在默认亮色中明确为白色，避免 `about:blank` 和加载间隙出现色带。网站 DOM、CSS、图片和视频保持原样。

## 自问自答

### 浏览器顶栏和底栏是否应该使用浅灰

不应该整条使用浅灰。白色 chrome 与白色页面背景能扩大内容感；地址栏和活动控件使用灰色 container 提供层级。整条灰色会产生厚重工具栏感。

### AI 顶栏是否应该使用黑色

不建议。黑色顶栏只适合全屏播放器、代码编辑器等沉浸工具。AI 是产品主页面，应与浏览器共享白色页面结构，黑色用于按钮、图标和文字。

### 浏览器网页是暗色时，白色 chrome 会不会冲突

网页和 chrome 是不同责任域，成熟浏览器也允许网页与浏览器框架不同色。Kiyori 的暗色主题可提供深色 chrome；本轮不根据每个网页自动切换 chrome，以免闪烁和状态不稳定。

### 为什么首页仍保留多色边框

Software Home 是产品入口而不是浏览器 chrome，其细线渐变能保留 Kiyori 的识别度。边框严格限制为 `1dp`，不增加外发光、模糊或阴影；Search/AI 使用固定蓝色，AI 交互使用应用强调蓝，浏览器仍由独立中性主题保护。

[DONE]
