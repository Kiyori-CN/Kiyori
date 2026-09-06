# 浏览器菜单真实能力

> 当前状态：菜单工具网格为三行 `5/5/5`，底部保留退出、收起、设置三个动作；未实现的阅读模式
> 已彻底移除，诊断日志进入独立 child drawer。输入框继续由 Android WebView 系统原生选区持有，
> 普通网页元素长按菜单按真实目标裁剪动作。历史里程碑的本地证据继续有效；本轮最终门禁、APK
> 与 Git 证据以 [`16_browser_runtime_diagnostics_and_menu_optimization.md`](16_browser_runtime_diagnostics_and_menu_optimization.md)
> 为准，真实网页、设备触控、菜单视觉与 provider 行为保持 `verification_pending`。

## 原则

旧版四行菜单只作为历史设计参考；当前工具网格固定为三行 `5/5/5`，底部动作不与工具入口混排。
hikerView 的广告拦截与网页元素工作台只作为
行为研究来源，运行时继续使用 Kiyori 唯一 Browser Runtime、Android System WebView 和现有
WebSession owner。页面视觉采用 Kiyori 现有浏览器模态和设置语义色，不复制旧版 X5/TBS、
Activity、LitePal 或 native ABP 架构。

## 能力分组

### 已有能力，完成页面整合

- 当前三行依次为“加书签 / 书签 / 历史 / 下载 / 插件”、“UA 标识 / 资源嗅探 / 网络日志 /
  诊断日志 / 工具箱”、“无痕模式 / 查看源码 / 标记广告 / 网站配置 / AI 对话”
- 这些按钮复用现有 owner，只优化来源返回、全屏/抽屉页面和宽屏布局
- UA 标识是菜单中的直接模态动作：点击后收起浏览器菜单并立即显示居中选择弹窗，不进入可拖动子抽屉
- 底部“设置”打开第四里程碑完成的唯一设置 owner 与页面；图标与软件首页第五个设置入口共用旧版
  `icons/设置-齿轮` 的完整六边形齿轮轮廓
- “诊断日志”使用 `StandardBrowserSessionTools` 的进程内 1,000 条有界结构化缓冲区；网络日志仍是
  当前 WebSession 当前文档的资源目录，两者不复制状态。诊断抽屉支持当前/全部会话、级别、类别、
  搜索、当前范围清空，以及对当前筛选结果执行同一脱敏投影的复制和 SAF 文本导出

### 已完成的广告相关能力

- “网络日志”继续由当前 WebSession 持有，并在 `其他` 右侧增加独立的 `拦截` 筛选；请求拦截
  与用户创建的网页元素规则共同显示，条目以 `REQUEST/ELEMENT` 区分。元素条目使用 `DOM`
  method、当前网页 URL 和可读拦截规则表达，不伪装成 HTTP 请求；订阅 cosmetic selector 是
  页面 stylesheet 输入，不批量伪造成日志请求；列表、详情和操作弹窗按条目类型只展示必要操作
- “标记广告”从浏览器菜单进入紧凑固定高度的底部工作台；工作台是实时 WebView 下方的 sibling，
  不覆盖页面内容。WebView 保留剩余实测高度，滚动到文档底部时页面底边与工作台顶边相接；
  轻点在抬手位置选择元素，纵向拖动与达到系统阈值的抬手速度滚动和惯性滑动同一 WebView，
  整段手势不进入 DOM。上栏为“拦截规则 / 节点 / HTML / 父 / 兄 / 弟 / 子 / 关闭”，下栏为
  “保存规则 / 编辑规则 / 预览 / 重置 / 清除拦截 / 拦截网站跳转”；底栏复用浏览器底栏
  `50dp` 内容高度，内部六个文本按钮统一为 `40dp`
- 编辑规则使用独立编辑弹窗；清除拦截使用确认/取消弹窗；网站跳转策略使用底部抽屉，提供
  “默认允许 / 跳转前询问 / 拦截跳转 / 取消”。这三个选项只控制退出标记模式后的正常浏览：
  当前 host 导航继续可用，跨域主框架和 popup 分别允许、询问或阻止。标记模式本身不读取该
  选项来放行导航，而是由原生触摸层独占手势并无条件消费任意 scheme、frame 与 popup 跳转
- 保存规则写入当前站点的元素规则并立即应用；清除确认显示规范化当前域名，并原子删除该
  域名下由用户创建的元素规则及明确针对该域名的网址规则，广告订阅内容和全局规则保留；
  清除后当前 WebSession 的样式、元素日志和原有标记广告立即恢复，不创建第二规则 owner
- 网页元素长按不再按几个可空字段直接拼接长列表，而是先解析稳定元素类型，再从本文的动作矩阵
  生成菜单；没有真实链接、资源、文本或图片能力时不显示对应动作
- 标记脚本在顶层 document 捕获 `pointerdown/pointerup/touchstart/touchend/mousedown/mouseup/click`，
  使用 `composedPath()` 识别媒体真实目标，并读取 `currentSrc`、`<source>`、poster、懒加载属性
  和 CSS `background-image`；原生 WebView 触摸层把跨域 iframe 或媒体层上的触点解析为顶层
  元素并吞掉网页手势。跨域 iframe 内部 DOM 仍受同源策略限制，不宣称能够读取其内部节点树
- 广告规则统一由 `BrowserAdBlockStore` 持有，包含总开关、网址规则、元素规则、站点白名单、
  五条默认启用内置订阅和用户显式添加的自定义订阅。内置订阅分为“广告拦截器 Pro”三条与
  “Adblock Plus”两条；缺少本地载荷时首次自动同步，后续在启用自动更新时按固定周期检查，
  同时保留全部刷新、逐条刷新、单条启停和整组启停。schema v3 保存已提交载荷的 SHA-256、
  字节数和存储版本；原始规则进入内容寻址原子载荷，派生编译快照进入 `noBackupFilesDir`，状态
  只在两类文件完整写入后提交。解析失败、快照损坏、忽略行和载荷缺失均形成明确状态
- Store 构造不读取或编译订阅载荷；设置页分别显示读取设置、加载本地编译规则、编译已变化规则、
  就绪或失败状态，全部重任务在单一 IO 生命周期完成后原子发布 matcher。请求线程不等待初始化；
  每条订阅持有一个已计算 host/token/unindexed 与元素域索引的 Engine 分区，自定义规则保持小型
  独立分区，统一 matcher 继续维持全局例外、`important`、跨订阅 `badfilter` 和元素例外。
  完整快照命中不读取大型规则文本、不解析或完整编译，也不重新计算索引；单条变化只重建该分区，
  相同内容刷新只更新时间和清除错误，不增加 `ruleRevision`。同页并发请求只计算一次 page policy。
  元素 CSS 在后台按 `64 KiB` 分块，同一 document/revision 只注入一次；网络日志 UI 与进程拦截
  计数分别竞态安全地合并发布，避免子资源消息风暴
- 五条实际列表的同机 JVM 探针保持 `286` 个相同命中：`2000` 次请求从 `43397 ms` 降为
  `1142 ms`，编译堆增量从 `268233632` 降为 `164696560` bytes；该数据只证明算法和对象结构
  改善，目标设备 WebView 流畅度、OOM/ANR 和真实网页兼容仍需现场复测
- 当前广告、设置与浏览器定向矩阵覆盖 9 个测试类，合计 `124/124`，零失败、零错误、零跳过；
  Kotlin 编译、跨分区例外/优先级语义、注入 JavaScript 语法、正式准备、
  architecture `phase=m03`、七份资源 XML、差异检查和提交后的官方 Markdown 候选树检查通过，
  Markdown 检查结果为 `errors=0 / warnings=0`；Debug APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，`471058927` bytes，SHA-256
  `64FE05CB67BBCC040923C476AE7E7825407BA60D49EC55DE5282C3F7E8C4E5B7`，
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37 / arm64-v8a`，Android Debug V2 单 signer、
  16 KB ZIP 对齐及 `52/52` 个 ELF64/AArch64 的 `PT_LOAD >= 0x4000` 审计通过

### 当前域名网站配置

- 第三行第四个“网站配置”已从 `PLACEHOLDER` 升级为共享可拖动 `SITE_CONFIG` 子抽屉；点击时冻结
  当前 HTTP(S) 完整 host，抽屉显示期间不会因后台导航静默切换目标
- 新域名十一个负向开关默认全关，未保存规则时完全遵从全局；唯一优先级为
  `globalEnabled && !siteDisabled`，站点规则只能继续禁用
- 四组十一项为：内容与脚本的广告拦截、用户脚本；页面与交互的返回不重载、左右滑动前进后退、
  强制页面缩放、网页元素长按菜单；权限与隐私的网页打开应用、网页定位、网站密码保存与填充；
  音视频的搜索栏嗅探入口和自动悬浮播放
- 广告拦截站点禁用继续写入唯一 `BrowserAdBlockStore.allowlistedDomains`。父域白名单覆盖子域时，
  抽屉显示真实父域来源且不提供虚假的反向启用；清除当前域名只删除精确 host 条目
- 其余十项由 `WebSessionBrowserSettingsStore.siteSettingsRules` 持久化精确 host 的 disabled
  feature set；关闭最后一个站点开关会删除空规则，不保留第二个“已配置”标记
- `about:blank`、`file:` 和非 HTTP(S) 页面打开明确空态且不创建规则。用户脚本开关不会自动刷新
  网页；禁用会立即阻断新的脚本注入、桥接、页面菜单和网络能力，已经执行的 DOM 影响明确要求用户
  刷新当前页清理
- 当前源码与定向测试已经通过；最终 Debug APK 与门禁证据见 [历史证据索引](../history/README.md)。真机上的浅深
  主题、窄屏滚动、抽屉拖动、系统 Back、十一项真实行为和应用重启持久化保持
  `verification_pending`

### 仍保持空占位的其他能力

- 工具箱继续保持浏览器上下文内的明确占位；阅读模式入口、route、图标和文案已经删除，未来只允许
  作为工具箱中的浏览器页面工具重新设计，并绑定明确的活动 session/document
- 无痕模式复用全屏搜索右上角的真实默认 Profile 切换与短时提示；当前标签 Profile 保持不可变，
  菜单保持显示，不复制旧版共享 Cookie 模式
- 网页扩展入口已经升级为 Browser Plugin Center；顶层统一投影扩展，本轮仍只注册内置 userscript
  provider，不新增第二个插件仓库

### 已完成的播放器依赖能力

- 资源嗅探、媒体候选和悬浮/全屏播放继续调用唯一 `PlayerSession`；诊断只记录候选分类与播放器交接
  边界，不复制媒体请求头值、网页正文或播放器内部日志

## 自问自答

### 为什么不按旧按钮名称补写一套新功能？

用户要求只实现旧项目已经实现的功能。按钮名称不是运行时证据；没有旧版消费者的入口只能保留空页面。

## UA 标识实现合同

- 一级弹窗固定为 `Android / PC桌面 / IPhone / 塞班Wap / 自定义全局 / 自定义该网站` 六项，当前生效项显示勾选
- 一级选择、自定义全局和自定义网站使用同一个蓝色语义弹窗容器：语言徽标、圆角 Surface、
  细描边、标题分隔线和全窗口遮罩保持一致
- Android、PC 桌面、iPhone 和塞班 Wap 使用固定 UA 预设；自定义全局只有在非空 UA 保存成功后才能成为全局模式
- 自定义网站弹窗提供域名与 UA 两个字段、`默认 / Android / PC桌面 / IPhone / 塞班Wap` 快捷填充和“完整域名”动作；“默认”清空 UA，确认后删除该域名规则
- 域名规则使用规范化 host，规则域名同时匹配自身和其子域；“完整域名”把当前页面的完整 host 写回域名字段，不猜测公共后缀
- Host 匹配只解析 URL 的 scheme/authority；fragment 不参与域名规则，即使网页地址包含重复 `#` 分隔符也不会阻断 UA 解析或原始导航
- UA 解析优先级固定为显式 WebSession UA、命中的站点规则、全局 UA 模式。它们只修改现有 WebSession 的 WebView 设置，不创建新的 session、WebView 或导航状态
- UA 变化立即更新所有没有显式 session UA 的现有窗口；只重载当前活动窗口，避免后台标签被无提示刷新
- `WebSessionBrowserSettingsStore` 是 UA 模式、自定义全局 UA 和站点规则的唯一持久化 owner；旧 `desktop_mode` 布尔状态、UA 子抽屉页面和切换按钮在本里程碑删除

## 网络日志实现合同

- `BrowserNetworkRequestEntry` 继续由当前活动 WebSession 的 `shouldInterceptRequest` 采集并保存在该 session 的当前文档资源目录中；Browser Home、悬浮浏览器和 AI 浏览器工具读取同一份按 document token 与规范化 URL 聚合的最多 2,000 个资源 identity，不新增持久化日志库或第二个浏览器状态
- 页面开始导航时清空该 session 的控制台和网络事件；抽屉中的“清空”只清当前 session 的网络请求，不改网页、历史、下载、其他窗口或控制台记录
- 抽屉复用书签与下载相同的 Hidden/Partial/Expanded 宿主、`52dp` 标题栏、中性浏览器配色、空态和居中模态弹窗。标题栏提供真实条目数与清空动作，内容依次为 URL 搜索、`全部 / 视频 / 音乐 / 图片 / 网页 / 其他` 横向分类和请求列表
- 分类只依据 Android WebView 当前能够确认的主框架标记、请求 URL、扩展名和 `Accept` 请求头。
  主框架固定为网页；已知 URL 扩展名优先确定资源组；无已知扩展名时按 `Accept` 媒体范围的
  `q` 值与原始顺序选择首个可识别类型。混合导航头中附带的 `image/*` 不能覆盖
  HTML、JavaScript、CSS、JSON 或字体扩展名，通配或证据不足的请求保持“其他”。第三方域名使用
  明确的“第三方”提示；不把异域请求直接宣称为广告，不凭空补 HTTP status、响应 MIME、失败或
  响应体。`拦截` 只表示 Kiyori 客户端在请求进入系统加载前作出的本地规则决定
- 请求行显示资源类型、method、host、紧凑 URL 和时间；图片条目使用 Coil 展示有界缩略图。缩略图和
  查看器只消费原请求 URL 与活动 WebSession 已捕获的 User-Agent、Cookie、Referer 等请求身份，
  不写回网络日志、不创建新条目，也不建立第二资源目录
- 点击请求打开与书签、下载一致的居中操作弹窗。复制链接使用系统剪贴板；下载资源进入唯一 `BrowserDownloadManager` 并采用当前默认下载器及活动 Profile 的 User-Agent、Cookie、Referer；外部打开只处理本条已记录的 HTTP/HTTPS URL；详情显示完整 URL、method、分类、主框架/子资源和捕获时间
- 图片查看使用独立的 edge-to-edge 全屏窗口，不复用带安全区和外边距的通用居中模态。打开时冻结
  当前筛选结果中的图片条目及初始索引；左右滑动切换，左下角显示“当前编号 / 总数”，右下角显示
  保存。图片支持双指缩放，切换到新图片后恢复 `1x`；单击图片退出，长按显示“保存原图”，
  上下拖动按位移降低黑色背景透明度并在松手后退出，使下层网页和网络日志内容直接显现
- 图片显示使用受实际窗口尺寸约束的 Coil 内容节点，`ContentScale.Fit` 保证完整图像可见。
  进程级唯一 Coil `ImageLoader` 注册 `SvgDecoder`，SVG 与位图共用缩略图、共享查看器、
  网页元素看图、二维码识别、请求身份及缓存；失败状态必须明确结束加载状态。保存继续调用唯一
  浏览器下载 owner，并在需要确认时先退出查看器，避免确认层被全屏窗口遮挡
- “拦截过滤网址”写入同一个 `BrowserAdBlockStore`，使用真实网络日志 URL 生成建议规则；不复制 legacy X5 响应头或 hikerView 的旧状态 owner

## 网页长按所有权修复合同

- 编辑型 `input`、`textarea` 与有效 `contenteditable` 由 Android WebView 原生长按、系统
  `ActionMode` 和原生选区手柄持有；系统负责剪切、复制、粘贴、全选和逐字调整选区范围。
  Kiyori 不绘制输入框选区，也不显示输入框专用的复制、全选、取消按钮
- Android 侧保持 WebView 可长按：命中 `EDIT_TEXT_TYPE` 时返回未消费，使 WebView 进入系统选区；
  普通网页元素继续由 Kiyori 消费并进入 `__kiyoriElementActions`
- JavaScript 文字选择 helper 在编辑控件上不启动自绘选择、不调用 `selectAtPoint()`、不调用
  `OperitTextSelectionBridge`；它仍可服务于普通网页正文的既有“选择文本”动作
- WebView 仍只使用现有一套注入脚本、JavaScript bridge 和 Host 瞬态状态；不新增第二套原生选区
  owner、第二 WebView 或输入框自定义操作栏

## 网页元素长按菜单迭代合同

### 元素类型

注入脚本必须在一次命中中给出可复核的 `tagName / text / linkUrl / resourceUrl /
resourceKind / selector / client point`。Host 只接受活动 session、当前页面和有效 HTTP(S) 图片
资源，不从扩展名猜测一个 DOM 元素是不是图片。

| 类型 | 判定事实 | 主要动作 |
| --- | --- | --- |
| 链接 | 存在祖先 `a[href]` 或 `area[href]`，无图片资源 | 新窗口、后台打开、复制链接、外部打开；有文本时复制/选择文本 |
| 图片链接 | 同时存在真实链接与 `img/picture/poster/background-image` 图片资源 | 全部链接动作，加全屏查看、保存图片、看图模式、复制图片链接、识别二维码 |
| 图片 | 存在图片资源且没有链接 | 全屏查看、保存图片、看图模式、复制图片链接、外部打开、识别二维码 |
| 媒体 | `video/audio/source/object/embed/iframe` 等真实资源，不属于图片 | 复制资源链接、外部打开和网址/元素拦截；不显示图片或二维码动作 |
| 文本 | 没有链接/资源但存在可见文本 | 复制文本、选择文本和元素拦截 |
| 普通元素 | 只有有效 selector/HTML | 快速拦截与精细标记；不显示不可执行的链接、文本或图片动作 |

每种普通元素都可以显示两种明确不同的拦截动作：

- “拦截元素”：把当前已解析 selector 与规范化页面域名直接写入唯一 `BrowserAdBlockStore`，
  用于确认目标无误时的一步保存
- “拦截网页元素”：进入现有标记工作台，可移动到父/兄/弟/子节点、编辑 selector、预览后保存

“拦截过滤网址”优先使用真实资源 URL，其次使用真实链接 URL；它写入同一广告规则 owner。
图片链接同时存在跳转链接和图片资源时，菜单分别显示“复制链接”和“复制图片链接”，避免复制对象
含糊。

### UI 与状态

- 使用 `ModalBottomSheet`，不再使用当前居中高信息密度长弹窗，也不复制 hikerView 的左侧窄列表
- 顶部摘要显示元素类型、DOM 标签、当前站点和最多两行文本/URL，不显示大段 selector 或 HTML
- 常用操作按稳定顺序使用四列紧凑图标卡；文本操作和拦截操作进入各自圆角分组，整个内容可滚动
- 图片、链接、文本和拦截动作使用各自语义色；危险/持久化拦截不能与普通复制动作视觉等价
- 系统 Back、遮罩点击和下滑关闭最上层菜单；二维码结果、图片查看器和标记工作台分别拥有自己的
  Back 优先级，不让隐藏的元素菜单抢先处理

### 设置总开关

`WebSessionBrowserSettingsStore` 增加唯一持久化字段 `webElementLongPressMenuEnabled`，
新安装默认 `true`。设置页面在“网页交互”分组显示“长按网页元素菜单”：

- 开启：普通网页元素进入按类型适配的 Kiyori 菜单
- 关闭：立即关闭现有元素菜单，并把状态同步到所有已打开 WebView；后续普通元素长按不再调用
  `OperitWebElementBridge.showActions`
- 无论开关状态，编辑型控件继续走 Android WebView 系统原生 `ActionMode`、剪切/复制/粘贴和
  原生选区手柄

设置变化不重载网页、不重建 session，也不持有第二份 UI 偏好。

### 图片查看、看图模式与二维码

- 网络日志和网页元素共用一个 edge-to-edge 图片 viewer 与一个手势状态机
- “全屏查看”只冻结当前图片；“看图模式”在用户点击后有界收集当前 document 的可解析图片，
  去重后最多保留 200 项，并从长按图片的索引开始
- 每项图片请求使用活动 WebSession 的 User-Agent、Profile Cookie 和 HTTP(S) Referer；不把
  Cookie、Authorization 或请求头写入持久状态
- 保存继续调用唯一 `BrowserDownloadManager`；查看器在下载需要确认时退出，避免确认层被遮挡
- 二维码识别只对图片显示。图片按同一请求身份加载为最长边受限的软件 Bitmap，再使用仓库已有
  ZXing QR 解码；结果弹窗显示完整内容并提供复制，有效 HTTP(S) 结果才显示“打开网页”
- 图片加载失败和未识别到二维码是两个明确终态；不自动改用第二加载器、OCR 或外部服务

### 自动验证

- 纯策略测试锁定六种元素类型、动作顺序和不可出现的动作
- 设置测试锁定默认开启、持久化字段、设置页标题/分组/action 映射和关闭时的即时同步接线
- JavaScript 合同测试锁定编辑控件旁路、`resourceKind`、有界图片收集、总开关和 bridge 方法
- 图片策略测试锁定去重、200 项上限、初始索引、请求头过滤、缩放和退出手势
- 二维码测试使用仓库内生成的标准 QR Bitmap 验证内容解码，并锁定非二维码错误终态
- Back 测试锁定二维码结果、图片查看器、元素菜单、广告工作台与原浏览器覆盖层的顺序

## 验收

- 加书签、书签、历史、下载和网络日志复用现有真实 owner；UA 标识接入唯一 Browser Settings owner 和活动 WebView
- 网络日志点击后主菜单退出并打开共享可拖动子抽屉；搜索、分类过滤、第三方提示、清空、复制、
  下载、图片查看、外部打开和详情均作用于当前 active session 的真实记录
- 自动测试覆盖请求分类、第三方 host 判定、紧凑 URL、过滤与搜索、图片请求身份、查看集合与索引、
  拖动透明度/退出阈值及编辑输入长按优先级；本地验证不宣称已获得 HTTP 响应或真机 WebView
  请求完整性
- 点击 UA 标识时主菜单消失，一级选择弹窗直接显示；系统 Back、遮罩点击和取消均关闭当前最上层 UA 弹窗
- 六种入口、两种自定义编辑路径、域名规则增删、子域匹配及 preset UA 均有定向测试或可复核运行时证据
- 旧版未实现的入口保持明确空占位，不伪造状态
- 普通和无痕窗口分别遵守 Profile 生命周期；无痕关闭后不保留站点会话数据
- AI 操作当前 session 时 UI 状态不分叉
- 广告规则管理、网络日志 blocked 记录、元素高亮和 DOM 注入均不创建第二 Browser Runtime 或第二规则 owner
- Debug APK 与本地门禁通过；提交和推送仅在用户另行授权时执行

### 网络日志图片分类与 SVG 修复本地证据

- `BrowserNetworkLogPolicyTest` `15/15` 与 `BrowserInteractionContractTest` `5/5`，
  合计 `20/20`，零失败、零错误、零跳过；HTML、JavaScript、CSS、JSON 和字体扩展名不会再被
  混合导航 `Accept` 中的 `image/*` 抢占，SVG 扩展名与明确图片媒体范围继续归入图片
- formal readiness、architecture boundaries `phase=m03`、工作树 Markdown links
  `errors=0` 与 `git diff --check` 通过；唯一 `KiyoriApplication` 的受控源码快照已同步
- 串行 `:app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 2m 46s`，`232` 个任务中 `80 executed / 152 up-to-date`；
  唯一 Debug Launcher 与 Player Runtime packaging 门禁通过
- 最终 Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；宿主文件时间观测为
  `2026-08-20 01:44:18 +08:00`，大小 `464647701` bytes，SHA-256
  `C11B566D56912C327F26F7A32F3ECF95918AAF46E0FE60A934229B216366CB12`
- APK 为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37 /
  arm64-v8a`，唯一 Launcher、Android Debug V2 单 signer 与 16 KB ZIP 对齐通过；
  `apkanalyzer` 已确认 DEX 内存在 `coil.decode.SvgDecoder` 和
  `coil.decode.SvgDecoder.Factory`
- 未安装 APK、未运行设备；真实 WebView 的普通 SVG、带查询参数 SVG、登录态/防盗链 SVG、
  缩略图、共享全屏查看和 HTML/JS/CSS/JSON 分类仍为 `verification_pending`

2026-08-19 原生选区与图片缩放纠正证据：

- `BrowserInteractionContractTest` `3/3` 与 `BrowserNetworkLogPolicyTest` `13/13`，
  合计 `16/16`，零失败、零错误、零跳过；`:app:compileDebugKotlin` 通过
- formal readiness、architecture boundaries `phase=m03`、Markdown links
  `errors=0 / warnings=0` 和 `git diff --check` 通过
- 最终 Debug APK 为 `472553854` bytes，SHA-256
  `3E7B488E966C77E5DCB606E2B6238190B889F7CE9E081A027F96917256FD64B9`；
  `com.kiyori / 45 / 0.1.0 / arm64-v8a`，唯一 Launcher、Android Debug V2 单 signer 与
  16 KB ZIP 对齐通过
- 未安装 APK、未运行设备；系统原生输入框选区、选区手柄和图片双指缩放仍为
  `verification_pending`

本轮本地证据：

- `BrowserWebElementActionPolicyTest` `5/5`、`BrowserNetworkLogPolicyTest` `13/13`、
  `BrowserInteractionContractTest` `4/4`、`WebSessionBrowserBackPolicyTest` `3/3` 和
  `KiyoriSettingsPagesTest` `14/14`，合计 `39/39`，零失败、零错误、零跳过
- `:app:compileDebugKotlin`、formal readiness、architecture boundaries `phase=m03` 和
  `git diff --check` 通过；现行源码与文档中的旧六组设置合同、旧图片查看器符号和过期构造器
  残留搜索为零
- 串行 `:app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 1m 35s`，`232` 个任务中 `23 executed / 209 up-to-date`；
  唯一 Debug Launcher 与 Player Runtime packaging 门禁通过
- 最终 Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，写入于
  `2026-08-20 00:47:25 +08:00`，`472553854` bytes，SHA-256
  `75384DABE734B26DCF5E22AA55DBF1A2D7D7A290AB8E3A650444837134332206`
- APK 为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37 /
  arm64-v8a`，唯一 Launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug
  V2 单 signer 与 `zipalign -c -P 16 -v 4` 通过
- APK 含 `51` 个 `.so` 与 `1` 个 shell launcher，共 `52/52` 个 ELF64/AArch64；
  `153` 个 `PT_LOAD` 为 `0x4000 × 151 / 0x10000 × 2`，无低于 16 KB 的对齐
- Markdown 候选树检查为 `errors=0 / warnings=0`；实现提交
  `d803c8bb8cb4c638fd66748994959894573990ef` 已推送到 `origin/main`，首次对账时本地、
  tracking 与远端 ref 一致且分歧为 `0/0`
- 未安装 APK、未调用 ADB/模拟器/真实设备；输入框系统原生选区、设置开关即时触控、每类元素菜单、
  登录态图片、页面看图、二维码识别、保存确认和系统栏视觉仍为 `verification_pending`

## 2026-07-30 Browser Plugin Center 里程碑一

- 浏览器菜单第 1 行第 5 项显示“扩展”并保持原图标，点击后进入新的 `PLUGINS` 可拖动子抽屉；
  未发布的旧 `USERSCRIPTS` UI 路由已删除
- `BrowserPluginCenterFacade` 是无存储纯投影，首期把现有 userscript manager 映射为不可卸载的内置
  “油猴脚本”插件，并发布已安装、已启用、本页命中、页面菜单、支持状态和待确认安装
- 扩展中心提供“本页 / 已安装”、搜索、添加和来源快捷弹窗；来源固定为 Greasy Fork、ScriptCat、
  OpenUserJS、Userscript.Zone 和 GitHub userscript topics，并通过现有 WebSession registry
  创建前台新标签
- 宿主增加 `OVERVIEW / USERSCRIPTS` 子页面。菜单进入概览，userscript 链接和安装预览直达管理页；
  系统 Back 与标题返回先回概览，再关闭下拉抽屉
- AI ToolPkg、Skill、MCP、工作流和包管理继续属于 AI 产品域；Chrome/Edge 扩展直接安装、通用
  WebExtension runtime、沉浸式翻译和 AI 自动安装不在本里程碑实现
- 详细长期分层、安全、AI 创作和扩展兼容合同见
  [`browser_plugin_platform.md`](../../doc-src/architecture/browser_plugin_platform.md)

## 2026-07-31 用户脚本运行授权与 `unsafeWindow`

- 插件概览卡和油猴脚本管理页共用持久化“允许用户脚本”开关，新安装默认开启
- 页面与共享隔离运行时按 WebView 生命周期各注册一次；权限关闭时已安装脚本保留启用意图、状态显示
  “需要授权”，运行时保持惰性且不返回脚本 payload
- 权限撤销会清除 token、reply proxy、页面菜单、webRequest，并取消活动 GM 网络请求
- `auto` 或 `content` 下的 `unsafeWindow + privileged grants` 在共享隔离运行时执行，通过无 native
  权限的同步页面对象桥访问网页原始 `window`
- 显式 `@inject-into page + privileged grants` 继续标记不兼容
- 脚本安装预览和已安装详情显示执行世界、页面 `window` 直连或隔离世界页面对象桥
- 2026-07-31 vivo Android 16 首轮设备报告出现脚本未命中与 WebView 主进程 SIGSEGV；已修复基础匹配
  语义和 document-start URL，并把按脚本世界及原生动态重配收敛为单 WebView 单隔离世界
- 本地 46 项插件/userscript JVM 测试与真实 Chrome 双世界属性、方法、回调、Promise、DOM 节点测试通过；
  修复版 Android WebView 真机验收仍独立保留

## 2026-08-03 插件抽屉与脚本设置初步封板

- 浏览器设置从历史 `6/7/5/6/6` 占位结构收敛到 `4/4/3` 共 11 个真实选项；插件分组只保留总授权、
  扩展中心、权限与网站范围、诊断与日志
- 第四行第三个“设置”进入 Browser Settings 后，扩展中心、脚本诊断和权限页脚本详情会先关闭设置 child，
  再在原活动标签页上显示对应 `PLUGINS` 抽屉；Browser Home 的返回目标和退出展示模式保持不变
- 权限页面不再重复扩展中心、管理、诊断和日志入口；运行环境与无脚本状态改为说明内容，不显示带箭头但
  不可点击的伪导航
- 当前 WebView 不支持 userscript runtime 时，设置、工作台菜单和授权横幅都不能请求开启总授权
- 扩展中心标签数量按过滤后实际 provider 卡片计数；脚本详情源码页可导出 `.user.js` 到
  `Download/Kiyori/exports`
- 删除继续明确清理源码、修订、草稿、值与日志；批量删除逐项隔离失败并记录错误，不让一个异常中断后续项
- `.kbx`、WebExtension、额外 provider、AI 包管理合并和设备操作不属于本轮；目标设备上的抽屉尺寸、
  窄屏、导出、删除失败反馈和真实 runtime 状态仍需独立验收

## 2026-08-03 页面源码工作台实现合同

- 浏览器菜单第 3 行第 3 项继续使用现有“查看源码”名称、蓝色语义色和源码图标；点击后关闭主菜单，
  直接打开浏览器宿主内全屏原生源码工作台，不再进入旧的纯文本可拖动子抽屉
- 工作台读取当前活动 WebView 的运行中 DOM 快照，包含 doctype 与 `documentElement.outerHTML`；
  捕获副本会排除 Kiyori 文本选择 UI 等浏览器自有临时节点。该内容不是重新发起 HTTP 请求获得的
  原始响应，也不复制 Cookie、Authorization、请求头或响应体存储
- `WebSessionPageSourceState` 记录捕获 session、页面 URL、标题、Document token、基线源码、
  编辑源码、加载/应用状态和明确错误。Document token 只用于确认应用目标仍是捕获时的同一文档，
  不写入持久存储或网页源码
- 工作台复用现有 `NativeCodeEditor` 的 HTML 语法着色、行号、补全、撤销重做和符号栏；顶栏使用
  44dp 动作几何并位于 `statusBarsPadding()` 之后，输入法显示时只保留 36dp 符号栏
- `NativeCodeEditor` 增加默认关闭的可选视觉软换行能力；页面源码工作台默认开启，按字符簇和显示
  单元把超长逻辑行映射为连续视觉行，不向源码插入换行。续行保留逻辑行号归属，触摸、光标、选区、
  上下移动、补全锚点和滚动都使用同一偏移映射；用户可显式切换为原横向浏览。进入横向浏览时视口
  固定重置到源码左上角，不追随切换前可能位于超长行末尾的光标；之后的点击、选区或键盘移动再恢复
  正常光标可见性
- 窄屏顶栏只保留返回、标题、搜索和更多；撤销、重做、格式化、复制、跳转行与换行模式进入可横向
  滚动的 36dp 紧凑工具条，并显示当前行列或选区。超长行提示明确显示数量、最长字符数和“只改变
  显示、不改变源码”的语义；提示右侧提供叉号，只关闭当前 Document token 的提示
- 查找替换、格式化、复制、重新读取、差异预览和应用均操作同一编辑缓冲区。格式化、替换下一个和
  全部替换直接进入编辑器历史，撤销/重做按钮按真实历史状态启用。差异预览复用现有
  `java-diff-utils` 生成统一差异和增删统计，不新增 diff 依赖
- 应用必须经过显式确认，并同时满足活动 session 与 Document token 未变化、源码非空且未超过
  规定上限。运行时使用 `document.open()`、`document.write()`、`document.close()` 重建当前文档；
  不新增导航历史、持久网页副本或第二 WebView。刷新或正常导航会重新加载网站内容
- 编辑后返回提供继续编辑、保留编辑返回网页和丢弃三种选择；保留只存在于当前 Browser Host 内存，
  关闭对应 session 或进程后不承诺恢复
- `browser_page_source` AI 工具显式提供 `live` 与 `editor` 两种读取范围。`live` 从当前活动 WebView
  读取同一 DOM；`editor` 读取当前 host 中绑定活动 session 的编辑缓冲区。超出内联结果上限时必须
  指定文件名，输出仍使用现有浏览器临时文件 owner
- AI 若要修改页面继续使用现有 `browser_evaluate` 或 `browser_run_code`，人工源码工作台的“应用”
  保持用户确认边界；本轮不让 AI 静默提交编辑缓冲区
- 本地 JVM 测试覆盖脚本安全编码、doctype、临时节点过滤、大小边界、Document 失效、应用结果、
  普通/超大源码差异和 Back；AI 工具注册、双语提示与 JavaScript 包装由编译和静态接线检查覆盖
- Debug 构建不替代真机状态栏、输入法、复杂站点脚本重建、刷新恢复和 AI 协作验收

## 2026-08-20 页面源码工作台交互与性能增量

- “查看源码”进入工作台后的默认模式改为“横向浏览”；“横向浏览”和“自动换行”均从源码左上角
  开始，模式切换也统一回到 `(0,0)`，不保留旧光标位置造成的跳转
- 工具条使用两个明确的模式按钮，搜索栏在“下一个”旁增加带方向图标的“上一个”；循环查找、
  替换后的实际文本长度和选区状态由同一匹配游标维护
- 横向长行布局为每 128 个 cell 建立源码 offset checkpoint；绘制、触摸定位和光标横坐标映射
  从可见区附近开始，避免源码较多时每帧从行首重复扫描
- 双指缩放期间只更新字体几何和视口，自动换行布局在缩放结束后重建一次；渲染线程按当前布局
  校正滚动范围，不在缩放事件的 UI 调用链中同步构建大布局
- 本地定向回归新增源码查找 `4/4` 与长行/模式 `10/10` 覆盖；真机双指帧率、超长页面实际
  触摸、输入法、状态栏和复杂 DOM 应用仍为 `verification_pending`
- 2026-08-20 自动证据为源码相关定向测试 `25/25`、完整 Debug JVM
  `247 suites / 1458 tests`、Kotlin 编译、formal readiness、architecture `phase=m03`、
  七语种 XML 和 `git diff --check` 全部通过；规定的 Debug APK 构建为 `232` tasks，
  APK SHA-256 为 `563E98259762B2C6F152EF02C075FEDBACB58018168289FCABD98FBB61F517CE`
- 最终 APK 的包身份、唯一 launcher、Android Debug v2 单 signer 和 16 KB ZIP 对齐通过；
  `51` 个 `.so` 加 `operit_shell_exec` 共 `52` 个 ELF64/AArch64，全部 `PT_LOAD >= 0x4000`

本地实现状态为 `[DONE]`，设备验收为 `[PENDING]`。页面源码、软换行与 Back 定向测试 `22/22`、
完整 Debug JVM `853/853`、Kotlin 编译、formal readiness、七语种资源与 `git diff --check` 均通过。
ARCH041 语义色快照和对应两项架构测试已同步；完整架构门禁只剩当前 HEAD 已有的 ARCH046
`UserscriptSourceExportHelper.kt` 消费者快照漂移。Debug APK 为
`app/build/outputs/apk/debug/app-debug.apk`，宿主文件时间 `2026-08-04 03:43:10 +08:00`，
大小 `471581414` 字节，SHA-256
`F2D7683FE6EE114B7171F635B58A0FB24EECF32DB1B0E445C6C2D4B5547FA97E`。
`com.kiyori / 45 / 0.1.0 / min 26 / target 34 / compile 37 / arm64-v8a`，Android Debug
V2 签名和 16 KB ZIP 对齐通过。未安装 APK、未操作设备。

## 历史抽屉实现合同

- `WebSessionHistoryStore` 继续是唯一持久化 owner。现有 `history_json` 增加内容分类、媒体来源和来源页，
  不复制 legacy 的网页、在线视频和本地视频三个仓库
- 普通 Profile 的 `doUpdateVisitedHistory` 只写主框架网页访问；无痕访问继续不写入。网络请求、
  媒体 candidate 和播放器直链不进入网页分类
- 唯一 `PlayerSession` 接受新媒体 request 后写入视频分类。`http/https` 标记为“在线视频”，
  `content/file` 标记为“本地视频”；同一媒体再次播放更新到列表顶部并删除同 URL 的网页重复项
- 历史记录不持久化 Cookie、Authorization 或 request headers。在线历史重播在点击时读取活动
  WebSession 当前 User-Agent、对应 Profile Cookie，并使用记录的来源页作为 Referer
- 抽屉标题栏显示“历史记录”、总数和红色删除动作；其下依次为标题/链接/来源搜索框、
  `全部 / 网页 / 视频 / 音乐 / 小说 / 其他` 横向筛选和按时间倒序的单列表
- 网页条目只显示页面标题、网址和访问时间；视频条目显示标题、媒体链接、来源页摘要、时间及
  “在线视频 / 本地视频”标签。当前没有真实音乐播放器或文档阅读器写入 owner 时，对应筛选显示明确空态
- 删除动作打开底部时间范围选择面板，固定为“请选择删除时间范围 / 过去一小时 / 过去24小时 /
  过去一周 / 所有时间 / 取消”。删除只作用于当前筛选分类；“全部”作用于全部历史分类
- 条目点击行为属于同一运行时：网页在当前浏览器打开；在线视频和本地视频进入唯一 PlayerSession。
  本轮继续补齐共享长按操作：网页记录使用“打开网页 / 加入书签 / 复制链接 / 复制标题 /
  删除记录 / 批量删除”；视频记录使用“播放视频 / 打开来源网页（仅有效来源）/ 复制视频链接 /
  复制标题 / 删除记录 / 批量删除”。视频直链和本地媒体 URI 不作为网页书签输入
- 长按弹窗复用浏览器统一模态表面、历史入口身份与 `48dp` 语义操作行。单条删除和批量删除均二次
  确认；批量模式保留搜索与分类，按 `url / category / visitedAt` 精确身份选择和删除，不改变
  原有分时段删除或历史序列化 schema
- 缩略图生成和隐私模式导出仍不在本轮范围内
- 现有“当前会话历史 / 最近历史”双区 UI 直接删除。WebView Back/Forward 继续由浏览器顶栏和底栏持有，
  不在资料历史抽屉复制导航栈

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

## 2026-08-08 浏览器弹窗视觉统一增量

- 新增浏览器共享语义弹窗 Surface；UA 选择/编辑使用蓝色，媒体候选资源操作和链接查看使用青色，
  下载长按操作使用绿色
- 书签、历史、下载、网络日志、视频资源、占位功能页以及插件/脚本子页统一复用
  `WebSessionDrawerHeader`：顶部固定 `52dp`、语义徽标 `34dp`、图标与标题间距 `8dp`，标题、
  数量、标题后动作与最右动作不再由各页面独立排版
- 网络日志标题不再紧贴图标；网络日志操作/详情、书签编辑/选择/确认、下载全部自定义弹窗、
  用户脚本安装/删除/日志/元数据/审阅弹窗统一复用语义色标题、`20dp` 圆角、细描边、分隔线、
  阴影和全窗口遮罩；历史删除时间范围保留底部选择形态但复用同一标题头
- 媒体候选仍只显示播放、下载、复制和查看链接四个真实动作，继续调用唯一 `PlayerSession`、
  `BrowserDownloadManager` 和系统剪贴板；UI 统一不修改候选排序、请求身份或播放入口
- 页面源码超长行提示关闭状态只保留在当前工作台组合中，并以 session 与 Document token 为键；
  重新抓取新文档后恢复显示
- 当前实现已通过 Debug Kotlin 编译；UA 输入法、嗅探长按、链接长文本滚动、源码关闭与新文档重现
  仍需目标设备验收

## 2026-08-08 四行菜单十八色增量（历史里程碑）

> 本节记录当时的四行布局。2026-08-27 已由三行 `5/5/5` 工具网格加底部三动作替代；
> `READER_MODE` 身份被删除，`DIAGNOSTICS` 成为第二行第四列，十八个现行身份仍保持唯一。

- 菜单固定顺序保持不变：前三行各 5 项、第四行 3 项，共 18 个动作；每个动作使用独立的
  `WebSessionBrowserMenuTone`，浅色和深色主题下的图标色、容器色及组合均两两不同
- 全应用 `KiyoriSemanticTone` 继续只表达七种共享状态语义；浏览器 18 色只属于菜单入口身份，
  不增加持久化颜色字段、用户可配置颜色、第二主题 owner 或 Browser Runtime 状态
- 加书签弹窗使用 `ADD_BOOKMARK`，书签抽屉及其管理弹窗使用 `BOOKMARKS`；历史、下载、插件与
  用户脚本、UA、网络日志、媒体候选、工具箱、阅读模式、页面源码、广告标记和网站配置分别复用
  对应入口身份色
- 抽屉标题徽标、搜索与筛选主强调、空态、选中边框和同页弹窗跟随入口；删除按钮文字、错误、
  成功、媒体操作与内容分类继续保留红、绿及其他状态语义，不用入口色覆盖状态事实
- 浏览器顶栏搜索框右侧媒体数量球直接引用 `FLOATING_SNIFFER` 颜色键，与菜单“悬浮嗅探”在浅色、
  深色主题下都保持完全一致
- 颜色唯一性、行列结构、3:1 非文本对比度和数字球复用关系由
  `WebSessionBrowserMenuColorPolicyTest` 固定；目标设备上的实际综合色差、暗色可读性和窄屏触控
  仍需验收
