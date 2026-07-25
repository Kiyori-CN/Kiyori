# 验证与真机验收

## 自动验证

1. `git diff --check`
2. 正式开发准备检查
3. 色板单元测试：精确 token、亮暗角色和 WCAG 对比度
4. `KiyoriSoftwareHomeSearchTest`
5. `KiyoriShellStateTest`
6. `WebSessionBrowserChromeLayoutTest`
7. `./gradlew :app:assembleDebug --no-daemon --console=plain`
8. 核验 `app/build/outputs/apk/debug/app-debug.apk` 的时间、大小、SHA-256、package、version 和 zip alignment

## 静态审计

- `rg` 确认默认主题路径不再引用 `Purple*`、dynamic color 或 XML purple/teal
- `rg` 确认 Software Home 只保留合同指定的五色描边与 Search/AI 明暗蓝色，不把固定装饰色写入应用 ColorScheme
- 确认 `Theme.kt`、WebChat resolver、Floating、Utility、分享图片和 XML 使用同一 Kiyori 基线
- 检查主题设置默认 swatch 与当前 theme token 一致
- 检查用户偏好 key、序列化 `ColorScheme` 和浏览器状态 owner 未改变

## 真机视觉矩阵

### 默认亮色

- 冷启动无紫色、动态壁纸色或不连续色带
- Software Home 为纯白背景，搜索框边界清楚但不抢眼
- AI 顶栏白底、黑字、黑图标，状态栏深色图标可读
- 用户消息浅蓝、AI 消息白色、输入和蓝色发送按钮层级清楚
- AI 抽屉普通项、选中项和联网状态可区分
- Browser Home 顶栏、底栏、空白区和加载间隙无接缝
- 自定义应用颜色后，Browser Home、全屏搜索、四行菜单和浏览器子页仍保持中性色
- 地址栏与页面分层明确，禁用后退/前进仍可识别
- Tabs、菜单、搜索、历史、收藏和设置选中态清晰

### 暗色与系统模式

- 暗色 surface 逐级可辨，无纯黑大块吞噬边界
- 状态栏和导航栏图标明暗正确
- 系统模式只跟随亮暗，不随壁纸出现彩色主题
- WebView 白色网页与暗色 chrome 的边界稳定，不闪烁重建

### 显式个性化

- 自定义 primary/secondary 在按钮和容器中可读
- 自定义顶栏颜色与强制内容色正确
- 背景图片/视频、透明顶栏、玻璃输入和自定义气泡继续工作
- 自定义字体和字体缩放不裁切顶栏、消息、地址栏和按钮

### 独立界面

- 悬浮聊天和悬浮浏览器
- 权限确认 overlay
- WebChat 页面
- 分享图片
- 崩溃报告与数据恢复 Activity
- 主题设置预览和重置

## 验收边界

自动检查和 Debug APK 只能证明代码与构建链路。颜色、状态栏、字体渲染、OLED 观感、网页/AI 连续性、横竖屏与不同 Android 版本必须由目标设备确认。真机矩阵完成前保持 `verification_pending`。

## 本轮自动验证结果

- `git diff --check`：通过
- 正式开发准备检查：通过
- 默认主题旧紫色/动态色与 Software Home 装饰色静态扫描：通过
- 主题切换实现阶段的 `KiyoriThemeTest`、`KiyoriSoftwareHomeSearchTest`、`KiyoriShellStateTest`、`WebSessionBrowserChromeLayoutTest`：`45/45` 通过
- 图标与四行菜单迭代的 `KiyoriSoftwareHomeSearchTest`、`WebSessionBrowserChromeLayoutTest`：`14/14` 通过
- `:app:assembleDebug`：`BUILD SUCCESSFUL in 35s`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 元数据：`com.kiyori`，`45 / 0.1.0`，`minSdk 26`，`targetSdk 34`，`arm64-v8a`
- 大小、时间与 SHA-256：`449500501` 字节，`2026-07-26 01:15:15 +08:00`，`88779DDF0C88C2D55C1B94EACC407334D5438531B7A289BBB753412A9B4158C3`
- 签名：Android Debug V2 签名通过
- ZIP 对齐：`zipalign -c -P 16 4` 通过
- 真机视觉矩阵：未执行，保持 `verification_pending`
