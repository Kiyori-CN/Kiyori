# 首页与浏览器控件对齐迭代

## 目标

- 以旧版 Kiyori 的五等分底栏几何统一浏览器与 App Shell 五按钮
- 按旧版 Kiyori 的紧凑四行结构重新排列浏览器菜单，不再用固定高度拉伸行距
- 三处产品 chrome 统一使用 Kiyori 自有空心描边 Vector，不混入 Material 实心图标
- 让普通浏览器顶栏和全屏搜索页的搜索框拥有完全相同的左右边界、上下边界和高度
- 恢复 Software Home 已确认的彩色渐变描边与 Search/AI 彩色选中效果
- 删除 Software Home 底栏选中胶囊、圆形裁剪和可见圆形 indication，只保留应用强调 tint

## 固定几何

| 区域 | 合同 |
| --- | --- |
| 底部五按钮 | 横向 `16dp`、顶部 `0dp`、底部 `6dp`，五等分槽位，点击目标 `44dp`，普通图标 `26dp`，首页中间图标 `25dp` |
| 浏览器窗口计数 | `20dp` 方框，圆角和边框均为 `1.75dp`，普通数字 `9sp`，`99+` 为 `7sp` |
| 菜单整体 | 内容自适应高度，顶部圆角 `30dp`，内容边距 `14/18/14/8dp`，四行间距 `1dp` |
| 菜单上方三行 | 单元格水平 `2dp`、垂直 `8dp`；图标容器 `32dp`、图标 `21dp`、图文间距 `6dp`、文字 `11sp Normal` |
| 菜单第四行 | 水平 `6dp`、顶部 `8dp`；三个 `46×36dp` 按钮直接 `SpaceBetween` 分布，图标 `22dp` |
| 顶部搜索行 | 横向与纵向外边距 `8dp`，槽间距 `6dp`，左右按钮槽均为 `40dp` |
| 顶部搜索框 | 固定高 `42dp`，普通顶栏和全屏搜索均使用 `background` |

## 视觉合同

Software Home 搜索框使用既有颜色序列 `#54C878 → #45B9D4 → #8277DA → #F09A6C → #54C878` 绘制 `1dp` 闭环渐变描边。Search/AI 选中项在亮色使用 `#2F6FED`，暗色使用 `#79A7FF`，背景为同色 `10%` 不透明度。

浏览器普通地址栏和全屏搜索框均使用固定 `#000000` 的 `1dp` 边框。浏览器底栏第五项使用立方体工具集合图标，四行菜单第二行第五项使用手提工具箱图标。

这些颜色是 Software Home 的固定页面装饰，不修改应用蓝色强调 ColorScheme，也不传播到 AI 顶栏和浏览器 chrome。

## 非目标

- 不改变 WebSession、活动 WebView、搜索提交、搜索历史或 Profile 状态
- 不改变 App Shell、浏览器菜单和全屏搜索的导航或 Back 行为
- 不向网页注入样式，不改变网页自身颜色
- 只增加实际引用的 Kiyori Vector 资源，不增加依赖或第二套主题状态

## 验证

- JVM 契约测试固定共享底栏尺寸与菜单四行几何
- 现有首页搜索与 Browser Chrome 布局测试继续通过
- `git diff --check`、正式准备门禁和 `:app:assembleDebug` 通过
- 真机覆盖手机/平板、亮色/暗色、普通顶栏/全屏搜索和菜单第四行视觉对齐

## 本轮结果

- [DONE] App Shell 与浏览器底栏使用旧版 Kiyori 的同一组五槽几何常量
- [DONE] App Shell 五按钮、浏览器四个动作和菜单十八个动作全部接入 Kiyori 自有空心描边 Vector；窗口计数继续使用 Compose 描边方框
- [DONE] 菜单删除固定高度与五槽空位，前三行紧凑排列，第四行直接分布三个按钮；退出浏览器使用关机图标
- [DONE] 普通顶栏与全屏搜索共享顶部几何常量和 `background` 填充
- [DONE] Software Home 五色描边与固定明暗蓝色选中效果按 Git 基线恢复
- [PASS] `WebSessionBrowserChromeLayoutTest` 已通过并覆盖新底栏、菜单和顶部搜索几何
- [PASS] `KiyoriSoftwareHomeSearchTest` 与 `WebSessionBrowserChromeLayoutTest` 共 `14/14` 通过
- [PASS] `git diff --check` 与正式开发准备门禁通过
- [PASS] `:app:assembleDebug`：`BUILD SUCCESSFUL in 35s`
- [PASS] `app-debug.apk`：`449500501` 字节，SHA-256 `88779DDF0C88C2D55C1B94EACC407334D5438531B7A289BBB753412A9B4158C3`，`com.kiyori`，版本 `45 / 0.1.0`，`arm64-v8a`，V2 Debug 签名，16 KB ZIP 对齐通过
- [PENDING] 真机像素级对齐、亮暗主题与触控验收

状态：`verification_pending`
