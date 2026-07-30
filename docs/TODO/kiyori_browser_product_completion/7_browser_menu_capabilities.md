# 浏览器四行菜单真实能力

## 原则

以旧版四行菜单顺序、图标、尺寸和行为为权威参考。只移植 `kiyori-android@24a2dfa9` 已经实现的能力；旧版没有实现的按钮保留空占位，不借本轮另行发明阅读、广告规则、站点配置或工具箱能力。

## 能力分组

### 已有能力，完成页面整合

- 加书签、书签、历史、下载、插件、UA 标识、网络日志、AI 对话、查看源码和退出浏览器
- 这些按钮复用现有 owner，只优化来源返回、全屏/抽屉页面和宽屏布局
- UA 标识是菜单中的直接模态动作：点击后收起浏览器菜单并立即显示居中选择弹窗，不进入可拖动子抽屉
- 浏览器设置保留第四行第三个位置并打开第四里程碑完成的唯一设置 owner 与页面；图标与软件首页第五个设置入口共用旧版 `icons/设置-齿轮` 的完整六边形齿轮轮廓

### 旧版没有实现的占位

- 阅读模式、标记广告、网站配置和工具箱继续保持空占位
- 无痕模式复用全屏搜索右上角的真实默认 Profile 切换与短时提示；当前标签 Profile 保持不可变，
  菜单保持显示，不复制旧版共享 Cookie 模式
- 网页插件入口继续复用现有 userscript owner；不新增第二个插件仓库

### 依赖播放器后实现

- 悬浮嗅探：在播放器和媒体 candidate owner 完成前继续留在本计划第九里程碑，不在本里程碑伪造

## 自问自答

### 为什么不按旧按钮名称补写一套新功能？

用户要求只实现旧项目已经实现的功能。按钮名称不是运行时证据；没有旧版消费者的入口只能保留空页面。

## UA 标识实现合同

- 一级弹窗固定为 `Android / PC桌面 / IPhone / 塞班Wap / 自定义全局 / 自定义该网站` 六项，当前生效项显示勾选
- Android、PC 桌面、iPhone 和塞班 Wap 使用固定 UA 预设；自定义全局只有在非空 UA 保存成功后才能成为全局模式
- 自定义网站弹窗提供域名与 UA 两个字段、`默认 / Android / PC桌面 / IPhone / 塞班Wap` 快捷填充和“完整域名”动作；“默认”清空 UA，确认后删除该域名规则
- 域名规则使用规范化 host，规则域名同时匹配自身和其子域；“完整域名”把当前页面的完整 host 写回域名字段，不猜测公共后缀
- UA 解析优先级固定为显式 WebSession UA、命中的站点规则、全局 UA 模式。它们只修改现有 WebSession 的 WebView 设置，不创建新的 session、WebView 或导航状态
- UA 变化立即更新所有没有显式 session UA 的现有窗口；只重载当前活动窗口，避免后台标签被无提示刷新
- `WebSessionBrowserSettingsStore` 是 UA 模式、自定义全局 UA 和站点规则的唯一持久化 owner；旧 `desktop_mode` 布尔状态、UA 子抽屉页面和切换按钮在本里程碑删除

## 网络日志实现合同

- `BrowserNetworkRequestEntry` 继续由当前活动 WebSession 的 `shouldInterceptRequest` 采集并保存在该 session 的内存队列中；Browser Home、悬浮浏览器和 AI 浏览器工具读取同一份最多 500 条记录，不新增持久化日志库或第二个浏览器状态
- 页面开始导航时清空该 session 的控制台和网络事件；抽屉中的“清空”只清当前 session 的网络请求，不改网页、历史、下载、其他窗口或控制台记录
- 抽屉复用书签与下载相同的 Hidden/Partial/Expanded 宿主、`52dp` 标题栏、中性浏览器配色、空态和居中模态弹窗。标题栏提供真实条目数与清空动作，内容依次为 URL 搜索、`全部 / 视频 / 音乐 / 图片 / 网页 / 其他` 横向分类和请求列表
- 分类只依据 Android WebView 当前能够确认的主框架标记、请求 URL、扩展名和 `Accept` 请求头。第三方域名使用明确的“第三方”提示；不把异域请求直接宣称为广告，不凭空补 HTTP status、响应 MIME、失败、拦截或响应体
- 请求行显示资源类型、method、host、紧凑 URL 和时间；不通过 Coil、WebView 或其他网络客户端加载图片缩略图，避免查看日志本身再次发起请求并污染列表
- 点击请求打开与书签、下载一致的居中操作弹窗。复制链接使用系统剪贴板；下载资源进入唯一 `BrowserDownloadManager` 并采用当前默认下载器及活动 Profile 的 User-Agent、Cookie、Referer；外部打开只处理本条已记录的 HTTP/HTTPS URL；详情显示完整 URL、method、分类、主框架/子资源和捕获时间
- 旧版“播放资源”等待播放器与媒体 candidate owner 完成，“拦截网址”等待广告规则 owner 完成；本轮不显示无真实 consumer 的动作，也不复制 legacy X5 响应头或 hikerView Adblock 状态

## 验收

- 加书签、书签、历史、下载和网络日志复用现有真实 owner；UA 标识接入唯一 Browser Settings owner 和活动 WebView
- 网络日志点击后主菜单退出并打开共享可拖动子抽屉；搜索、六类过滤、第三方提示、清空、复制、下载、外部打开和详情均作用于当前 active session 的真实记录
- 自动测试覆盖请求分类、第三方 host 判定、紧凑 URL、过滤与搜索；本地验证不宣称已获得 HTTP 响应、广告拦截或真机 WebView 请求完整性
- 点击 UA 标识时主菜单消失，一级选择弹窗直接显示；系统 Back、遮罩点击和取消均关闭当前最上层 UA 弹窗
- 六种入口、两种自定义编辑路径、域名规则增删、子域匹配及 preset UA 均有定向测试或可复核运行时证据
- 旧版未实现的入口保持明确空占位，不伪造状态
- 普通和无痕窗口分别遵守 Profile 生命周期；无痕关闭后不保留站点会话数据
- AI 操作当前 session 时 UI 状态不分叉
- Debug APK 与本地门禁通过；提交和推送仅在用户另行授权时执行

## 2026-07-27 UA 标识小步 [DONE]

- `USER_AGENT` 路由已从可拖动子抽屉集合移除，菜单收起后直接显示一级选择弹窗；自定义全局和自定义网站在同一模态状态内进入编辑页
- `WebSessionBrowserSettingsStore` 已成为 UA 模式、自定义全局 UA 和规范化域名规则的唯一 owner；旧 `desktop_mode` DataStore 字段、全局布尔状态、UA 信息页和切换文案已删除
- 新建、显式导航、站内跳转、重定向、后退、前进和历史跳转都会在现有 WebView 上应用最终 UA；后台窗口只更新设置，只有活动窗口在偏好变化后重载
- `WebSessionUserAgentPolicyTest` 6 项与 `WebSessionBrowserUserAgentRoutingTest` 1 项通过；正式开发准备门禁、7 份 `strings.xml` 解析、`git diff --check` 和 `:app:assembleDebug` 通过
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-27 22:19:59 +08:00`，`449501741` 字节，`com.kiyori`，`45 / 0.1.0`，SHA-256 `95327BFCACC4598DC44AA921D687E7F90119A2C451730C263AAF1D042506EF63`，V2 Debug 签名且 `zipalign -P 16` 通过
- 真机上的弹窗尺寸、输入法、系统 Back、实际请求 UA、域名跳转和普通/无痕窗口组合仍待用户验收，不由 JVM 测试或 APK 构建替代

## 2026-07-27 网络日志小步 [DONE]

- `NETWORK_LOG` 保持共享三态子抽屉路由；主菜单点击后退出固定菜单并打开同一个 `WebSessionBrowserBottomDrawer`，Browser Home 和悬浮浏览器没有新增第二个 host 或 WebView
- `BrowserNetworkLogPolicy` 根据主框架、URL、扩展名和 `Accept` 请求头生成 `视频 / 音乐 / 图片 / 网页 / 其他` 分类，并提供倒序过滤、搜索、跨 host 判定和紧凑 URL；现有每 session 500 条内存队列继续是唯一日志 owner
- 新抽屉与书签、下载统一使用 `52dp` 标题栏、中性表面、紧凑筛选和居中模态弹窗，提供当前 session 清空、复制链接、按当前默认 engine 下载、外部打开和请求详情；列表不加载资源缩略图，也不显示无法确认的响应状态、广告拦截或播放动作
- `BrowserNetworkLogPolicyTest`、`WebSessionBrowserUserAgentRoutingTest`、`WebSessionBrowserChromeLayoutTest`、`WebSessionBookmarkPolicyTest` 和 `BrowserDownloadDrawerPolicyTest` 合计 `27/27`，零失败、零错误、零跳过；正式开发准备门禁、7 份 `strings.xml` 解析与 `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`。Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-27 23:43:42 +08:00`，`449513873` 字节，`com.kiyori`，`45 / 0.1.0`，SHA-256 `2A32681AC44E53E1C898C5C1E8C1F24F49257EB8EC01216DCE49DA01F3C82577`，V2 Debug 签名且 `zipalign -P 16` 通过
- 真机上的菜单到抽屉转场、半展开/全展开拖动、500 条实时请求滚动、系统 Back、复制/下载/外部打开、普通/无痕窗口隔离和真实网页请求分类仍待用户验收，不由 JVM 测试或 APK 构建替代

## 2026-07-28 外部应用提示与菜单第 4 行修正

- 删除 `PendingExternalOpenRequest`、host projection、Browser Home 提示条和 minimized indicator 提示，
  不再显示“打开外部应用 / 取消 / 允许一次”
- `allowWebPageOpenApp` 继续是唯一权限 owner；只有设置开启、主框架且带用户手势时直接执行外部 Intent，
  自动触发和设置关闭状态均被消费
- 菜单第 4 行从全宽 `SpaceBetween` 改为 `2:1:2` 三槽权重，退出和设置向内移动，收起保持居中
- `BrowserExternalNavigationPolicyTest` 与 `WebSessionBrowserChromeLayoutTest` 锁定外部导航和槽位合同；
  真机上的实际 Intent、网站自动跳转、按钮位置与触控仍待验收
- 四组定向 JVM 合计 `15/15`，零失败、零错误、零跳过；formal readiness、7 份 `strings.xml` 解析、
  一次性外部提示零引用和 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`，233 个任务零失败。Debug APK：
  `app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 23:32:13 +08:00`，`463722827` 字节，
  `com.kiyori`，`45 / 0.1.0`，SHA-256
  `EC13E49350D5A3DB84571082F892A77D24A847B993DA6A3CE6A50E82A6B12678`，V2 Debug 签名且
  `zipalign -c -P 16 -v 4` 通过

## 2026-07-30 AI 对话与无痕菜单行为实施记录

- “AI 对话”先关闭菜单，再显式把浏览器转入 background anchor，进入 AI 首页并请求输入框焦点
- Browser Home 返回软件 Shell 时不消费启动瞬间的旧 `HOME` Pager 快照，避免它覆盖菜单动作
  请求的 `AI_HOME`；AI 页稳定后再触发输入焦点，已在 AI 页时立即触发
- “无痕模式”不改变 `sheetRoute = MENU`；它只切换 Browser Runtime 的默认新窗口 Profile，并显示与
  全屏搜索相同的短时状态提示
- “退出浏览器”继续关闭 presentation 且不显示 indicator；退出不清空 WebSession 窗口
- 本轮以 Shell 状态、presentation release mode 和 host Back 状态机为唯一控制面，不在菜单层复制
  WebView、导航或悬浮球状态
- Kotlin 编译、定向 JVM 回归、formal readiness 与 Debug APK 构建通过；AI 输入框实际唤起、
  菜单保持显示和 indicator 触控行为等待设备验收
