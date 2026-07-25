# AI 主题精调、设置入口与浏览器设置移除

## 原状

- 应用强调域使用 `#2563EB / #4F6B95`，其中 primary 比用户在颜色选择器中指定的 Material Blue 600 更锐利
- 文件管理首页的分类标题与计数行距偏松，网格两行之间留白偏大；手机存储容量为固定示例文字
- 设置首页四张白色卡片绘制了浅灰外边框，所有入口均为空，无法从产品级设置首页进入现有 AI 设置
- 浏览器设置已经形成页面、搜索记录子页、Shell 子路由、内部 Intent、菜单动作和运行时设置快照，但产品决定后续重新设计该页面

## 设计决策

### 默认应用强调色

- 亮色 primary 使用颜色选择器“推荐颜色”第一行第七个 `#1E88E5`
- 亮色 secondary 使用低饱和蓝灰 `#536D79`，与 primary 保持冷色协调，同时降低整页蓝色饱和度
- `#1E88E5` 作为控件、图标和选择态强调；其上的内容使用深色高对比前景，不以白色小字承担可读性
- primary 与 secondary 的亮暗容器采用固定 token，不依赖通用混白结果
- 浏览器主题继续使用固定中性色，不读取应用强调色

### 文件管理首页

- 分类 tile 的图标到标题距离略收紧，标题与计数使用明确行高并取消额外间隔
- 网格行距从 `18dp` 收紧到 `10dp`，保持四列和现有图标尺寸不变
- 手机存储读取应用实际所在数据卷的 `StatFs.availableBytes` 与 `StatFs.totalBytes`，使用系统本地化容量格式展示
- 容量在页面组合和宿主恢复到前台时更新，不申请广泛存储权限

### 设置首页

- 四张白色卡片保留 `16dp` 圆角和内部细分隔线，删除外边框描边
- 第一张卡首项新增“AI 设置”，四组数量调整为 `4/4/4/4`
- “AI 设置”打开现有唯一 AI 设置根页，不复制页面、偏好或业务状态
- 从设置首页进入时显示返回语义，Back 返回 Kiyori 设置首页；从 AI 抽屉进入时继续返回 AI 首页
- 其余旧版复刻入口继续保持空按钮

### 浏览器设置删除边界

- 删除浏览器设置页面及只由该页面进入的搜索记录子页
- 删除 `BROWSER_SETTINGS`、`BROWSER_SEARCH_HISTORY` 子路由和外部打开设置 Intent
- 保留浏览器抽屉第四行原“浏览器设置”按钮、图标和三按钮位置，点击暂时为空；删除 App Shell、悬浮浏览器和 WebView host 的设置回调链
- 删除只为设置页面组装的运行时快照和桌面模式 setter
- 删除不再引用的页面字符串和测试；菜单与软件底栏共享的设置图标资源继续保留
- 保留浏览器搜索引擎、搜索记录、普通/无痕 Profile、UA、历史和 WebSession 状态所有者，因为它们仍被浏览器主流程使用

## 文件与架构

- `ui/theme/ThemeColorSchemeResolver.kt` 继续是应用与浏览器两套 ColorScheme 的唯一入口
- `ui/main/shell/KiyoriFileManagementPage.kt` 负责文件首页展示和只读容量快照
- `ui/main/shell/KiyoriSettingsHomePage.kt` 只描述设置首页分组和动作标识
- `ui/main/OperitApp.kt` 负责 AI 设置跨产品入口的路由来源与返回语义
- 删除 `ui/main/shell/KiyoriSettingsPages.kt`，不保留空页面或失效路由

## 验收

1. 主题单元测试校验精确色值、浏览器中性色不变和所有前景/容器对比度
2. Shell 测试校验 AI 设置双来源返回、四组设置顺序以及浏览器设置路由零引用
3. 文件页测试校验动态手机容量标识与分类、快捷访问、存储顺序
4. `git diff --check`
5. `python -B ci/script/check_formal_readiness.py --repository . --require-main`
6. `./gradlew :app:assembleDebug --no-daemon --console=plain`
7. 核验 Debug APK 包名、版本、SHA-256、V2 签名和 16 KB ZIP 对齐
8. 审计暂存内容后提交并推送 `main`，核验本地 HEAD 与 `origin/main` 一致

## 验收边界

自动检查不能证明不同屏幕密度下的字距、系统容量文案是否完整显示、返回手势触感或真实设备的亮暗色视觉。最终 APK 生成并推送后，本阶段仍保持 `verification_pending`，等待目标设备检查。

## 实施结果

- [DONE] 应用亮色 primary/secondary 已精调为 `#1E88E5 / #536D79`，暗色为 `#90CAF9 / #B9CBD4`；浏览器中性色域保持隔离
- [DONE] 文件分类标题、计数与网格行距已收紧，手机存储显示应用实际数据卷的可用与总容量
- [DONE] 设置首页四张卡片取消外描边，第一项“AI 设置”接入唯一 AI 设置页，其余按钮保持空动作
- [DONE] 浏览器设置页、搜索记录管理子页、Shell 路由、外部 Intent 和页面专用逻辑已删除；第四行设置按钮、原图标和三按钮位置保留为空占位
- [DONE] `KiyoriThemeTest`、`KiyoriSettingsPagesTest` 与 `KiyoriShellStateTest` 共 `37/37` 项通过，零失败、零错误、零跳过
- [DONE] `git diff --check` 与正式开发准备门禁通过，已删除浏览器设置路由和 Intent 零引用
- [DONE] `:app:assembleDebug` 通过，`230` 个任务零失败
- [DONE] Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，生成于 `2026-07-26 03:39:17 +08:00`，大小 `449485670` 字节，SHA-256 `F33D327D840F4D45BDE5167A1A2FCCC8E4F393A3CCE643D004C5AA1DFF3C6B9E`
- [DONE] APK 包名 `com.kiyori`，版本 `45 / 0.1.0`，`minSdk 26`、`targetSdk 34`，Android Debug V2 签名与 `zipalign -c -P 16 4` 通过
- [ ] 真机亮暗色、字体与控件密度、实际容量显示、AI 设置返回和四行菜单视觉仍为 `verification_pending`

[DONE]
