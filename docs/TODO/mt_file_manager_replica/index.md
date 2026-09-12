# Kiyori 内置文件管理器开发

## 2026-09-12 底部操作与回收站可靠性完善

状态：本轮实现与本地验证完成，设备 `verification_pending`。基线 `main / 8842cab2cb7798205ef477a788f23112d226aaf7`，父仓库与 terminal 初始干净。
本轮授权实现、验证后提交推送；设备安装与操作不在授权范围内。

1. 删除与回收：检查 Android 文件属性、回收隔离、恢复、永久删除及损坏记录；以数据保留和明确结果为验收标准。
2. 复制、移动与粘贴：统一可用位置与目标校验，冻结批次与剪贴板身份，严格识别结果，未知状态停止后续操作。
3. 交互：保留五列菜单与现有主题，精简确认信息，明确来源/目标、进度、停止及逐项结果，接通结果与回收站导航。
4. 验证交付：定向文件操作与 ViewModel 回归、文档和差异检查、串行 Debug APK 构建；审计精确文件清单后提交并核对远端。

用户截图确认共享目录回收到 Android/data 专属目录返回跨文件系统错误；共享回收根已按卷调整。
用户追加 Shizuku 授权后看不到 data 目录：修复文件工具注册时冻结权限、普通枚举失败假空目录、
特权目录路径引用与宿主私有路径前缀误匹配。系统 `/data` 与共享 `Android/data` 的现场权限边界分别验证。

依赖原 `FileManagerViewModel`、标准文件工具与原子不覆盖原语，不新建存储后端，不升级依赖。
风险集中在部分成功、外部并发变化和 Android 文件系统差异；回滚以本轮提交为边界，既有回收记录保持兼容。
真机删除症状、深浅色/大字体/横屏、权限及存储介质验收继续标记 `verification_pending`。

本轮交付与证据（2026-09-12，Asia/Shanghai）：

- 共享回收避开 Android/data 挂载边界，保留旧回收记录；恢复核对指纹，永久删除兼容 Android 创建时间投影。
  损坏记录和不可访问存储分别可见，元数据读取限制 64 KiB，单条恢复不反复扫描全站。
- 精简删除/恢复/永久删除确认与结果页，真实错误优先、复制详情、回收站跳转及最近操作回看；批量任务支持当前项后停止。
  复制/移动提供来源目标卡片、目标预检与待粘贴条；固定来源栏和剪贴板版本，未知结果及残留内容停止后续项。
- Shizuku 权限在调用时解析，修复授权后沿用旧身份、枚举失败显示为空目录，以及目录引用和宿主路径前缀边界。
- `:app:testDebugUnitTest` 的文件管理器、本地文件操作及权限路由筛选：24 套、228 项，0 失败/错误/跳过。
  初次编译缺少导入、旧测试的字符串成功模拟和过期目标预期已修复；最终定向命令 1m41s 成功。
- 文档检查 521 文件无问题、正式准备检查、最终完整架构检查（`phase=m03`）和差异检查均通过。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 4m30s 成功；唯一 Launcher、脚本代理与播放器打包验证通过。
  APK 为 `app/build/outputs/apk/debug/app-debug.apk`，生成于 `2026-09-12 14:42:41 +08:00`，488291759 字节，
  `com.kiyori / 45 / 0.1.0 / arm64-v8a`；V2 单签名和 16 KiB zipalign 通过，包含 `libkiyori_fileops.so`。
  SHA-256：`bbc393607bc428e6c59e3ed62d0558ba6ac3879279f8bc3de42d2cca7aaedd0e`。
- 交付允许清单为 29 个源码、测试与文档文件，目标为 `main`；APK、日志与 work 检查点不纳入 Git，terminal 未修改。
  设备安装、Release 和远端 CI 未执行；手机上的删除/恢复/永久删除、Shizuku data 浏览及 UI 现场验收仍待进行。

## 2026-09-11 菜单、双栏与回收站完善

状态：`in_progress`。本轮在 `main / 934ee797` 及已有未提交改动上增量开发，保留既有选择、滑动、主题和 Shell 会话。

1. 紧凑存储抽屉：行高、图标字号对齐文件列表；路径按实际宽度左省略；补齐路径、真实容量与进度；修复遮罩事件穿透。
2. 菜单操作：固定来源窗格与对象；核对全部按钮、批量操作、目标选择、确认、重复提交、失败及结果呈现。
3. 回收站：复用既有回收存储，双栏统一浏览全部记录；普通删除进入回收站，恢复不覆盖，彻底删除单独确认。
4. 验证：文件管理器与回收站数据完整性定向测试，差异检查，串行 `:app:assembleDebug` 并核验 APK。

不提交、不推送、不操作设备、不升级依赖。数据操作必须保留不覆盖与未知结果不重试合同；设备视觉、手势和真实存储仍需现场验收。

## 当前目标：原创产品化（2026-09-10）

状态：`in_progress`。当前用户要求以专业文件管理能力为目标，完整重做 UI、按钮、文案和交互；
此前截图复刻约束被本次原创设计取代。目录名保留以维持已有链接，本文仍为唯一阶段状态源。
下文旧实现和 APK 记录属于历史证据，不能代表新方案已完成。

详细目标、用户旅程、功能清单、架构、风险与验收见[内置文件管理器设计](../../doc-src/architecture/kiyori_file_manager.md)。
开发基线：`main` / `213be2b3663749daf600af85228565669a269f8b`，初始工作区干净。

| 阶段 | 状态 | 当前范围与下一验收 |
| --- | --- | --- |
| M1 原创基础 | verification_pending | 首轮主题工具栏、位置标签、新建/搜索状态修复与实际长按动作已实现；25 项测试和 Debug APK 通过，设备 UI 待验收 |
| M2 可靠文件操作 | in_progress | 不覆盖复制、同卷移动、清单确认删除、重命名和空项目新建已实现；120项定向测试通过，跨文件系统移动、替换及持久恢复继续开发 |
| M3 浏览与发现 | in_progress | 固定双位置、排序筛选、书签与统一高级搜索已实现；整行滑动、点击反馈、滚动指示与导航等分已接线，各轮证据见下文，最近位置和现场体验待完善 |
| M4 存储访问 | in_progress | 五类折叠抽屉、根目录/内部存储/Linux、SAF 位置与网络配置已接线；各环境权限、可移动存储和真实服务待验收 |
| M5 内容与归档 | in_progress | 手机音视频、图片、UTF-8 编辑另存、属性哈希、ZIP 创建/安全解压和系统分享已接入；原地保存、多编码、大文件及更多归档格式待完善 |
| M6 批量工具 | in_progress | 本地回收站、恢复和永久删除已实现并通过定向测试；重命名预演、比较、去重和容量分析待开发 |
| M7 扩展协作 | in_progress | AI 文件入口、应用内双球共存、工作区目录和五类网络目录客户端已接入；远程读写、系统级文件悬浮球和完整会话重建待完善 |
| M8 完整收口 | planned | 性能、无障碍、全流程验收、最终提交推送 |

首轮确认问题：长按菜单业务为空；旧粘贴读取可变剪贴板且忽略源删除失败；创建完成会请求旧路径；
搜索没有取消/代际，结果跳转使用当时活动环境；名称未经路径穿越校验；固定弹窗与硬编码颜色不适配主题。
按风险先消除会导致错误位置或数据误操作的根因。文件工具仍为唯一执行入口，不增加第二后端。

本轮（M3j）已获授权：实现后审计全部累计修改，提交推送 `main`；每轮生成 APK。
设备、安装与真实网络存储服务器未获具体操作授权，现场验收保持 `verification_pending`。

### M3n 选择归零退出与独立滑动区间（2026-09-11）

- 状态：实现与本地验证完成，设备 `verification_pending`；保留 `main/934ee7978` 上全部既有工作。本轮修正 M3m 的模式退出与区间起点语义。
- 用户补充：点击取消全部选择后立即恢复打开；完成一个区间后，下一次滑动决定新区间首项。
- 方案：模式直接投影原窗格选择集合，删除独立模式状态；点击清空时释放锚点，滑动到另一项完成区间后也释放锚点。
  重复滑动同一起点保持选中；已选文件可作为新区间首项，旧区间选择保留。
- 范围与验收：ViewModel、交互回归和文档；验证归零后打开、相隔区间不误选空缺、反向/重叠区间与两栏隔离，再串行构建核验 Debug APK。
- 风险与回滚：只撤回本轮精确差异，不改视觉/文件后端、不提交推送或操作设备；设备触摸另行验收。
- 验证：文件管理器 12 套 122 项 JVM 测试通过，0 失败/错误/跳过；516 文件文档检查和差异检查通过。
  串行 `:app:assembleDebug --no-daemon --console=plain` 1m49s 成功，Launcher、脚本代理与播放器运行时打包验证通过。
- APK：`2026-09-11 02:28:32 +08:00`，488231589 字节，`com.kiyori / 45 / 0.1.0`；V2 单签名与 16 KiB zipalign 通过。
  SHA-256：`700B5DFABD3D3DD5C6D8858F21A370C35BA0099EA12598B3A8E6AB4AB059B295`。
  真机点击取消最后一项、相隔区间、已选项作为新起点及反向滑动仍待现场验收。

### M3m 滑动区间选择与点击模式（2026-09-11，退出与区间语义已由 M3n 修正）

- 状态：实现与本地验证完成，设备 `verification_pending`；基线 `main/934ee7978`，保留已有未提交界面与文档修改，不提交推送或操作设备。
- 目标：首次左右滑动进入选择模式，滑动幂等选中并按首次锚点将可见区间并入选择；点击切换，零选中仍保留模式，Back 清空两栏并恢复打开。
- 实现范围：原 ViewModel 的窗格模式与回退、Screen 点击/滑动接线、双栏语义；保留原文件工具、显式导航/菜单及手势距离。
- 阶段：核对调用链 → 修复唯一状态所有者和接线 → 回归点击、重复滑动、区间混合选中、两栏/刷新/Back → 串行 Debug APK。
- 风险与回滚：不得误打开或让旧目录选择参与操作，模式与锚点按窗格隔离；回滚仅限本轮精确差异，设备触摸另行验收。
- 验收：文件管理器定向 JVM 测试、文档检查、差异检查与 `:app:assembleDebug --no-daemon --console=plain`，核对最终 APK 元数据与哈希。
- 本地证据：`:app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*" --no-daemon --console=plain`
  通过，12 套 121 项，0 失败/错误/跳过；包含 7 项新增交互回归。正式准备、516 文件文档检查及差异检查通过。
- 串行 Debug 构建 52 秒成功；唯一 Launcher、脚本代理和播放器运行时打包验证通过。APK 时间为
  `2026-09-11 02:16:22 +08:00`，488231589 字节，`com.kiyori / 45 / 0.1.0`，V2 单签名和 16 KiB zipalign 通过。
  SHA-256：`70BF03D031BAB5EF5710209BBEEBC9BFAC21F4EE822235C90FFDF16EB3CAF1E3`。
- 待验收：真机左右滑动、重复滑动、含已选项的区间、点击归零与系统 Back；未安装 APK，未运行 Release 或远端 CI。

### M3l 二级工具箱与跨页面入口（2026-09-11）

- 状态：实现与定向测试通过，真机 `verification_pending`；基线 `main/934ee7978`，保留 M3k 的 19 项未提交改动。本轮不提交推送或操作设备。
- AI 顶栏顺序为网页浏览器、文件管理器、终端、对话详情；文件入口与悬浮球使用紫色线性 Folder。
- 文件一级菜单第三行为全选/全不选、反选、打开方式、加书签、加工作区；工具箱不依赖文件选择。
  二级工具箱第一行显示 AI对话、浏览器，其余槽位留空；固定三行五列，无第四行和全屏展开锚点。
- 浏览器一级菜单第三行为无痕模式、阅读模式、查看源码、标记广告、网站配置。二级工具箱为
  AI对话、文件管理、密码管理、网页翻译、网页朗读 / 页内查找、保存网页、保存PDF、添加桌面。
- 进入二级菜单替换一级菜单，返回直接关闭二级菜单；选择状态保持，原 Shell/WebSession 继续持有导航与会话。
- 用户确认翻译本轮置灰并标明未接入；网页工具复用既有活动 WebView，正文阅读/朗读限制 120000 字符，
  MHTML 通过系统文档选择器保存，PDF 通过系统打印面板，桌面快捷方式由系统确认；真实设备能力另行验收。
- 验收：固定槽位、菜单身份色及对比度、浏览器 Back 与文件选择接线、Shell 导航定向测试；
  架构快照审阅、完整架构检查和串行 Debug 构建；回滚只针对本轮 Git 差异，不覆盖 M3k。
- 本地证据（2026-09-11）：99 项定向 JVM 测试全部通过，0 失败/错误/跳过；主题消费者四项正反例通过，
  完整架构检查 `phase=m03, errors=[]`，正式准备与文档检查通过。
- 补充修复：两行菜单文案显式使用浏览器同款 12sp 行高；MHTML 保存使用可取消回调桥，异常和晚到回调
  均清理临时文件。跨页面恢复和密码设置来源保持由新增两项 Shell 回归测试覆盖。
- 最终 `:app:assembleDebug --no-daemon --console=plain` 成功，APK 时间为 2026-09-11 00:33:58（Asia/Shanghai），
  大小 488464608 字节，SHA-256 `39BD75FCCC1F465675FBDDD3A83B07E0D058B28C2C19FDEEBD0147234F95550D`。
  包名 `com.kiyori`、版本 `45 / 0.1.0`；V2 签名、`zipalign -c -P 16 4` 和新增工具箱/网页工具 DEX 存在性通过。
  真机菜单动画、两行文字/大字体、双球恢复、WebView 导出/打印/朗读与 Launcher 验收仍待进行。

### M3k 栏几何与设置返回稳定（2026-09-10）

- 状态：实现与本地测试/构建完成，真机 `verification_pending`；基线 `main/934ee7978`，开始工作区干净；本轮不提交推送。
- 目标：第一栏与 AI 默认 TopAppBar 同高，去掉环境副标题和搜索按钮底色；上下栏使用浏览器白色主题背景。
  第二栏共用 AI 的 36dp 内容与上下各 2dp 留白、surfaceVariant 20% 叠色；底栏沿用浏览器 50dp 内容高度。
- 抽屉标题共用品牌 Text、字重和 24+6dp 顶部留白；滚动滑块固定 24dp，仅位置随进度变化，短视口内裁限。
- 根因修复：文件页退出 Shell 的通用 AnimatedVisibility 层，设置返回和最小化恢复直接呈现原工作表面。
  原 ViewModel、来源与保存状态继续复用；通用搜索页保留原动画。
- 验收：滚动固定长度/首尾/短视口、设置返回与悬浮恢复策略的定向测试；完整架构检查及 Debug 构建。
  真机几何、系统栏、字号与设置往返仍需现场核验；回滚以本轮精确 Git 差异为边界。
- 本地证据：两套 87 项 JVM 测试，0 失败/错误/跳过；主题消费者两项架构正反例通过。
  完整架构检查最终通过，`errors=[]`；Shell 哈希及共用标题/主题消费者按已审阅差异精确更新。
  正式准备与 513 文件文档检查通过；串行 Debug 构建 2m22s 成功，Launcher/脚本代理/播放器打包验证通过。
- APK：2026-09-10T23:26:14+08:00，485732000 bytes，`com.kiyori / 45 / 0.1.0`；
  SHA-256 `5A08F2A28CCDB2F05743E1B95AE365DDC5BF657E9C8D32DB16E23AA49249987A`。
  V2 单签名、16 KiB zipalign、DEX 共用标题及几何常量核验通过。未操作设备或远端。

### M3j 浏览器交互统一与存储工作区整合（2026-09-10）

- 状态：实现与本地验证完成，设备与真实网络服务保持 `verification_pending`；基线 `main/213be2b`，保留前轮 81 个修改/未跟踪路径。
- 目标：共用浏览器底栏尺寸和无文字灰黑图标；固定三行五列动作及第四行关闭/收起/设置；
  设置来源保持、文件会话最小化、AI 顶栏文件入口；存储抽屉五类折叠、工作区与网络位置。
- 方案：Shell 唯一持有文件会话生命周期，仍复用 FileManagerViewModel、AIToolHandler、ApiPreferences；
  浏览器与文件球共用绘制组件、独立会话，菜单复用浏览器全部身份色；新增归档操作通过原文件工具入口。
- 阶段：基础 UI → 导航与会话 → 抽屉/实际动作 → 定向状态与文件安全测试 → 串行 Debug → 精确暂存审计与推送。
- 风险与验收：目录选择/滚动不能被设置往返清空；网络环境不能误落到 Android 路径；解压越界、冲突、
  回收恢复与跨卷失败必须保留数据；新增状态需覆盖保存恢复；设备动效与真实协议握手另行验收。
- 回滚边界：保持现有脏树，回退仅针对本轮差异；不重置、不清理工作区，不修改 terminal 子模块。

#### M3j 本地交付证据

- 累计文件管理/本地文件安全/网络目录/壳导航 23 套 271 项测试，0 失败、错误、跳过；
  最后网络目录预算和逐项读取修正后 7 项网络测试再次通过。架构主题/存储 8 项正反例通过。
- 修复单击在已有选择时误勾选、设置嵌套覆盖返回来源、回收后源变化、截断 ZIP 接受、
  FTP 无界行读取和网络目录无总预算等边界；新增未实现网络操作的统一拒绝分流。
- 完整架构检查、正式准备、513 文件文档检查和最终暂存空白检查通过。
  架构变动与历史快照遗漏核对见[控制面说明](../../../config/architecture/README.md)。
- 最终 Debug 构建成功，唯一 Launcher、脚本代理与播放器运行时打包检查通过。
  APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-09-10T22:56:04.079310+08:00`，`488444931` bytes，
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`；
  SHA-256 `2F80921D8251A0E15BCF12135EA660239FD873B3693459751BD1798FC768C423`。Android Debug V2 单 signer、16 KiB zipalign 和文件 JNI PT_LOAD 对齐通过，
  DEX 含新会话、网络目录、回收和解压实现。
- 审计允许清单 117 路径，排除 work、构建产物与凭据；terminal gitlink 未修改。
  本轮按用户授权提交推送 main，具体提交和远端核验结果以 Git 及交付回复为准。
- 现场边界：网络只支持连接/目录浏览，远程文件打开与写入未实现；文件球仅在应用内显示，
  与浏览器共享图案、几何和手势但不创建系统级窗口；配置变化/进程重建完整文件工作恢复仍待完善。
  真实设备、服务器、Release、远端 CI 未作为本地通过项。

### M3i 菜单第四行动作与底栏入口（2026-09-10）

- 目标：第四行删除文字，使用浏览器同款“关闭/收起/设置”图标按钮与2:1:2槽位；
  底栏第5项从上一级改为菜单，打开时保留当前两栏选择。
- 实现：直接复用浏览器 BottomMenuAction、图标和布局常量；关闭退出文件管理器，收起只卸载菜单，
  设置通过Shell或旧工具路由进入设置首页。关闭/设置保留文件写入中退出保护。
- 选择逻辑：底栏入口捕获活动栏已选对象，无选择时清除旧长按对象并置灰依赖文件的操作；
  全选、粘贴、传输任务和导航仍可访问，菜单开关不清除两栏选择。
- 验证：菜单选择/页面接线21项和设置导航1项定向测试通过，0失败/错误/跳过；
  Kotlin编译、正式准备、513文件文档检查和差异检查通过。串行Debug构建1m成功，Launcher和运行时打包检查通过。
- 本轮追加：用户要求移除双栏滚动条的整列阴影轨道；已只保留位置滑块，保留原淡入淡出与滚动计算。
- APK：2026-09-10 21:04:46 +08:00，488444931 bytes，`com.kiyori / 45 / 0.1.0`；
  SHA-256 `F0E77CC3CD0957A82FFB53E981BE7BE3830FB18D98C0B251BD9CD6B1943B02CC`。
  V2单签名、16 KiB对齐和DEX菜单入口核验通过。本地交付完成，设备验收 `verification_pending`。
  main/213be2b，81个累计改动路径，无暂存、未提交推送，terminal干净，未操作设备。

### M3h 点击反馈、无加载闪烁与双栏滚动指示（2026-09-10）

- 目标：统计采用英文冒号加空格，文件行点击有阴影反馈，目录进入/返回不闪现加载图标或顶部进度条，
  每栏滚动时右侧显示贯穿可视高度的轨道与位置滑块。
- 实现：行点击使用现有组合生命周期保留80 ms反馈，去重并在离开组合时取消；横滑不叠加按压阴影。
  加载不绘制空目录提示，完成后才显示空目录或错误。滚动条消费原有 LazyListState 的 ScrollIndicatorState，
  无第二位置状态、无触摸拦截；停止700 ms后淡出，滑块限制最小尺寸并在首尾吸附。
- 范围与验证：仅 UI、滚动几何计算和文档；定向验证小窗口、首尾、超大范围及未测量状态，
  然后串行构建核验Debug APK。保留后台与文件操作逻辑，不提交推送或操作设备。
- 验证：滚动几何与页面接线共2套件11项定向测试，0失败/错误/跳过；Kotlin编译、正式准备、
  513文件文档检查及差异检查通过。串行Debug构建成功（1m 1s），Launcher和运行时打包检查通过。
- APK：2026-09-10 20:36:54 +08:00，488444931 bytes，`com.kiyori / 45 / 0.1.0`；
  SHA-256 `D46D36F6E608E6A6AB9DE8B8B07A01DB5AF1559D8A1923A5D7B3B2054B2433B0`。
  V2单签名、16 KiB对齐通过，DEX已确认滚动指示组件与几何类存在。
- 状态：本地实现与验证完成，实际视觉与触摸保持 `verification_pending`。未提交推送或操作设备。

### M3g 恢复独立位置行与淡灰内阴影（2026-09-10）

- 用户纠正：M3f 不应将存储位置按钮和路径合并到顶栏。恢复原标题/环境、搜索、浏览选项，
  下方单独保留存储按钮与路径/统计区域；不恢复刷新按钮、A/B 行或选择操作条。
- 删除活动栏蓝色描边，改为仅非活动栏内侧 6 dp、6.5% 透明度的中性灰渐隐阴影；
  描绘不占布局宽度，选择、手势、文件操作与自动刷新逻辑保持。
- 范围为两个 UI 组件及对应文档；通过差异审阅和串行 Debug 构建验证，设备视觉仍待验收。
- 验证：串行 Debug 构建成功（1m 40s），唯一 Launcher、脚本代理与播放器打包检查通过；
  文档513文件0问题、差异检查通过。纯视觉修正未重跑单元测试，真实视觉仍为 `verification_pending`。
- APK：2026-09-10 20:19:32 +08:00，488444931 bytes，`com.kiyori / 45 / 0.1.0`；
  SHA-256 `D405E10A68AB6CDD39CC2EC0093AE045A9C93B8B94C0FFAFF0488683B3F57ED0`，V2 单签名与16 KiB对齐通过。
  未提交推送或操作设备，main/213be2b及既有工作保留。

### M3f 固定双栏与稳定选择布局（2026-09-10）

- 目标与范围：永久左右双栏、两行顶部、五等分底栏；删除布局选择、A/B 行和选择操作条。
  文件图标保留、文件名最多四行、秒级时间和两位小数大小同行、对称滑动、浏览器五列长按菜单。
- 方案：复用现有 ViewModel/文件工具与浏览器菜单几何；活动栏内侧描边不挤占宽度；
  剪贴板粘贴和传输记录迁入浏览选项/长按菜单，复制移动冻结批次与目标。
- 风险与验收：窄屏元数据需测量缩小字号；前台每 3 秒刷新，跳过在途加载、写入和长按菜单，
  静默加载保留布局与选择并校验代际。定向测试覆盖刷新竞争与选择批次，串行构建核验 APK。
- 授权与回滚：仅本地源码/文档/验证，保留全部既有工作，不提交推送或操作设备；
  本轮基线保存于忽略的 `work/fixed_dual_baseline.json`，按文件恢复本轮差异。
- 已交付：永久等宽双栏，活动栏内侧描边；路径操作行加统计行，选中仅改变行背景与数量。
  文件名最多四行，左侧 30 dp 图标保留，右侧秒级时间与两位小数大小同行，按实际宽度适配字号；
  滑动上限为左侧内边距加图标宽度，两侧一致。长按使用浏览器同高三行五列菜单且不能上拉展开。
- 修复：触摸激活另一栏不重启手势监听；后台刷新不触发同目录滚动恢复；
  容量读取跟随活动路径，异常显示不可用，Linux/SAF 不冒用手机容量；菜单只绘制一层遮罩。
  长按已选项目的复制/移动处理当前栏整组选择，未选项目仍单独处理；传输确认和结果入口完整保留。
- 本地验证（2026-09-10）：文件管理器定向 JVM 11 套件 / 106 项，0 失败、错误或跳过；
  Kotlin 编译、正式准备、513 文件文档检查及 `git diff --check` 通过。
  串行 `:app:assembleDebug --no-daemon --console=plain` 成功（1m 1s），唯一 Launcher、
  脚本代理和播放器打包检查通过。Debug V2 单签名与 16 KiB ZIP 对齐通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，488444931 bytes，2026-09-10 20:08:55 +08:00；
  SHA-256 `35C8D47EC9B9D30A4C7DE8FE5CBB2739A3B6C6DAA2BE07C30EB93E74068F42B3`，
  `com.kiyori / 45 / 0.1.0`。DEX 已核对元数据组件、自动刷新入口、整行回弹及旧布局枚举移除。
- 状态：实现与本地验证完成，设备显示、手势与文件操作保持 `verification_pending`；
  尤其需检查窄屏元数据字号和大目录周期刷新成本。main/213be2b，累计73个改动路径，无暂存、无提交推送，terminal 干净。

### M3e 整行滑动与统一高级搜索（2026-09-10）

- 目标：整行横向滑动、第三行 A/B 全宽等分，移除独立筛选按钮；底栏五等分且新建与其他按钮同样式。
- 搜索：统一顶栏入口，默认当前目录，支持递归、名称包含/通配符/正则、大小预设/自定义、时间预设/自定义、
  文本内容、大小写和隐藏项；表单与结果继续由 FileManagerViewModel 持有，执行复用 find_files 的显式模式。
- 风险：正则耗时、UTF-8/大文件、部分权限与超限、取消后旧结果、筛选残留及双栏滑动侵入另一栏。
  本地搜索需限制扫描/结果/内容读取并公开限制；Linux/SAF 保留旧名称搜索，未实现的高级条件明确拒绝。
- 回滚参考：`work/file_manager_search_baseline.json` 保存此前源码文档；开始时 main/213be2b，64个改动路径，均保留。
- 验收：后端临时目录与正则预算、表单范围、状态代际/结果定位测试；差异和文档检查；串行 Debug APK 与产物核验。
  不提交推送、不操作设备，触摸、动画、大字体和真实权限保持 verification_pending。
- 已实现整行位移/回弹与窗格裁切，A/B 各占一半并增加活动下划线，底栏五项同宽同样式；
  统一搜索抽屉、高级条件、一次元数据结果、部分限制提示、结果类型定位和搜索图标筛选提示点。
- 主动修复原大小写参数错配、Linux 层写死递归/大小写、隐藏结果定位不可见、旧筛选入口移除后的提示文案。
- `:app:compileDebugKotlin` 通过；定向 JVM 测试 125 项通过，0 失败/错误/跳过；新增 24 项验证
  （后端12、表单5、状态6、布局接线1），最终日志 `work/file_manager_search_tests.log`。
- 正式开发准备 PASS、文档513文件0问题、差异空白检查通过。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 成功（7m24s），唯一 Launcher、脚本代理和播放器打包检查通过。
  `app/build/outputs/apk/debug/app-debug.apk` 为 488444931 bytes，2026-09-10 18:54:54 +08:00，SHA-256
  `757BCC64927CC93D0E5C1AB65322AC9E7B8BC758ABFDC68F49D7E6F49B01F0C7`。
  `com.kiyori / 45 / 0.1.0`、Android Debug V2 单签名及 16 KiB ZIP 对齐通过，已在 DEX 核对搜索类、表单与整行回弹标识。
- 本轮新增6个 Kotlin 文件、修改16个既有源码/文档，基线全部保留；main/213be2b，累计70个改动/未跟踪路径，
  无暂存内容、terminal 干净，未提交推送或操作设备。本轮实现与本地验证完成，设备体验保持 verification_pending。
- 后续优先搜索结果分页/增量进度、大目录真实性能与更多文本编码，以及 Linux/SAF 高级搜索。
  现场重点验证双向整行拖动与取消、两栏边界、键盘/大字体下的高级搜索、各环境权限和取消响应。

### M2d / M3d / M5b 长按九项操作（2026-09-10）

- 目标：九项真实长按动作，单/双位置统一复制移动目标选择，复用现有状态、工具和偏好存储。
- 已实现：三列主题动作网格；单位置浏览目标/双位置另一位置快捷选择，完整来源与目标确认；
  同卷原子不覆盖移动、清单复查和隔离删除、ZIP 创建、属性/流式 SHA-256、系统分享及普通持久书签。
- 风险与边界：移动跨文件系统拒绝；永久删除无回收站，失败残留可见；元数据树不是内容快照；
  ZIP 不包含解压；严格动作限手机路径，Linux/SAF 后续开发；进程恢复与真实设备权限仍待验收。
- 主动修复：属性重读失败不复用旧删除清单；无结构化压缩/移动结果不报告成功、不触发分享；
  切换目录不改变操作源，重复提交与执行中退出受保护，剪切结果不清除后来替换的剪贴板。
  首轮 120 项测试发现 NIO `fileKey()` 为空导致指纹计算异常，已修复可空语义并纳入创建时间校验；
  旧“剪切一律拒绝”合同已改为验证原子移动失败保留源、不调用复制或删除；修正测试类型引用后完整定向复测通过。
- 回滚参考：`work/file_manager_actions_baseline.json` 保留此前 57 个改动路径中的源码/文档快照，
  本轮 `ApiPreferences.kt` 原为干净文件，其变更以 Git diff 核对。不回退此前实现。
- 最终定向测试 120 项通过，0 失败/错误/跳过（文件管理器 90、管理操作后端 11、不覆盖复制 13、重命名 6），
  本轮新增 22 项行为测试；`work/file_manager_actions_tests_verified.log` 为最终通过日志。
- 正式开发准备 PASS、文档 513 文件 0 问题、`git diff --check` 通过。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 成功（4m30s），唯一 Launcher、脚本代理和播放器打包检查通过。
  `app/build/outputs/apk/debug/app-debug.apk` 为 488444832 bytes，2026-09-10 18:07:46 +08:00，SHA-256
  `9FE67FB45AA807253194C229813445E1C3240EB60B6E3F30C95726E647A9E4CF`。
  `com.kiyori / 45 / 0.1.0`、Android Debug V2 单签名及 16 KiB ZIP 对齐通过；DEX 中已核对本轮新增类和书签键。
- 本轮 6 个新增 Kotlin 文件，基线既有改动保留；`main / 213be2b3663749daf600af85228565669a269f8b`，
  累计 64 个改动/未跟踪路径，无暂存内容，terminal 干净。未提交、推送或操作设备。
- 设备现场密度、菜单/Back、真实文件系统、分享选择器与书签重启恢复保持 `verification_pending`。
- 本轮实现与本地验证完成，整体项目仍在进行。后续优先跨文件系统移动的持久恢复、Linux/SAF 能力边界、
  文本原地保存与归档解压；设备验收覆盖双位置目标确认、删除残留提示和取消系统分享后返回。

### M3c / M5a 截图反馈增量（2026-09-10）

- 目标：压缩标题、路径与位置栏；取消窗格外框和多余间距；双位置完整显示时间与大小。
- 存储使用限宽模态左抽屉，浏览选项与长按动作复用浏览器三态抽屉；明确关闭、Back 和选择手势。
- 普通文件点击接入已有播放器与文本编辑组件；读取、编辑状态和工具调用保留现有所有者。
- 风险：窄屏大字体换行、滑动与纵向滚动竞争、晚到读取与未保存草稿、内容打开的环境区别。
- 回滚参考：本轮前工作区快照保存在忽略的 `work/file_manager_compact_baseline.json`，不回退此前成果。
- 验收：相关行为测试、差异检查、串行 Debug APK 与产物核验；设备操作未执行，现场状态保持待验证。
- 已实现：标题与路径紧凑化、A/B 合并单行、无窗格外框与大缝隙，单/双位置行高最低 56/52 dp，
  双位置完整时间和大小分别使用整栏宽度。左右滑动切换单项，普通点击打开，选择模式内点击勾选。
- 存储侧抽屉复用 AI 宽度计算；浏览选项和上下文菜单复用浏览器三态底部抽屉，动作等待关闭动画。
- 手机文件音视频复用 PlayerSession，图片复用可缩放 WorkspaceImagePreview，文本复用 CodeEditor。
  有界严格 UTF-8 读取与另存副本经过现有工具，1 MiB 上限；原文件不覆盖，未知保存禁止重试。
- 定向测试最终 92 项通过（文件管理器 79、文本读写 8、原空项目新建 5），失败/错误/跳过均 0；
  新增 16 项行为测试。首轮源码断言与运行中修改的 Back 条件不一致，冻结代码后完整复测通过。
- 正式开发准备 PASS，文档检查 513 文件 0 问题，`git diff --check` 通过。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 成功（5m19s）；APK 为
  `app/build/outputs/apk/debug/app-debug.apk`，488444350 bytes，2026-09-10 17:26:02 +08:00，SHA-256
  `E536FB72BC23F50BE44524461D61F8508D222EC50B5BF284BBA742DCAA5FDA20`。
  `com.kiyori / 45 / 0.1.0`、唯一 Launcher、脚本代理及播放器打包检查、V2 单签名与 16 KiB ZIP 对齐通过。
- 本轮实现与本地验证完成；`main / 213be2b3663749daf600af85228565669a269f8b`，累计 57 个改动/未跟踪路径，
  新增 5 个 Kotlin 文件；已有改动保留，未暂存，terminal 工作区干净。
- 设备上的密度、抽屉动画/Back、横屏/大字体、真实播放与文本保存仍为 `verification_pending`。
  本轮无提交、推送、安装或设备操作；后续优先补原地编辑保存契约、Linux/SAF 打开以及存储访问恢复。

### M1 本地证据（2026-09-10）

- 顶栏、位置标签、底部文字动作、新建类型选择和长按主题弹层重新组织，不再使用截图的窗口偏移、
  禁用背景遮罩、固定弹窗高度和深色硬编码栏。长按实际支持选择、查看名称/大小/路径和复制路径；
  尚未接入的危险动作不显示空按钮。双位置分别提供加载状态和错误重试。
- 创建阻止路径穿越与重复提交，捕获打开弹窗时的位置，失败保留输入；结果不改变用户后来进入的目录。
- 搜索独立进度、取消、结果代际与原环境跳转；移除逐条元数据请求，空搜索文案不再误用“没有包”。
  文件类型元数据、结果分页和高级筛选仍待后续阶段完善。
- `:app:compileDebugKotlin`、`:app:testDebugUnitTest --tests "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*"`
  通过；25 项测试，0 失败、0 错误、0 跳过。7 项新行为测试覆盖名称、重复提交、创建位置、失败保留、
  晚到搜索、取消和原环境跳转。原目录生命周期与导航的 13 项行为测试全部保留并通过。
- `check_formal_readiness.py --repository . --require-main`：PASS；`check_documentation.py --repository . --write-catalogs`：
  513 个文件、0 问题；`git diff --check` 通过。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 成功，唯一 Launcher、脚本代理、播放器打包检查通过。
  `app/build/outputs/apk/debug/app-debug.apk` 为 `485731920` bytes，SHA-256
  `2EC9E6AC89291C1A84CB6993BACDA70A1404D9EA36F64B2CDEB7AD99B9B68B1A`；实际 APK manifest 为
  `com.kiyori / 45 / 0.1.0`，minSdk 26、targetSdk 34、compileSdk 37。
- 未操作设备；深浅色、字体放大、TalkBack、手势与实际文件权限保持 `verification_pending`。
  当前仍在 `main` 的未提交工作区持续开发；以上不是完整文件管理器完成或最终提交推送证明。

### M2a 本地证据（2026-09-10）

- 标准 Android 目录复制提取为同一后端内的 `LocalDirectoryCopy`，子目录、枚举、目标类型和链接错误
  不再被吞掉；拒绝自身与后代目标。5 项真实临时目录测试覆盖嵌套数据、空文件、Unicode、目标冲突、
  源保留以及不存在的源目录。暂保留旧同名普通文件替换行为，不宣称原子不覆盖已完成。
- `pasteFiles` 捕获批次、模式、来源与目标；重复点击不重复提交，切栏或替换剪贴板不改变进行中的任务。
  复制失败不删源，删除失败产生 `COPIED_SOURCE_RETAINED`，只从未被替换的剪贴板移除成功移动项。
  此流程尚未接入 UI；取消、源变化、未知删除终态、冲突及任务恢复仍属 M2 必须完成范围。
- 定向测试首次发现 Windows `canonicalPath` 分隔符与子目录边界比较不一致；修复后重新运行
  文件管理器全部测试及 `LocalDirectoryCopyTest`，最终 **34 项通过、0 失败、0 错误、0 跳过**。
- 第二次串行 `:app:assembleDebug --no-daemon --console=plain` 成功，唯一 Launcher、脚本代理和播放器
  运行时检查通过。最终 Debug APK 仍为 `app/build/outputs/apk/debug/app-debug.apk`，`485731920` bytes，
  SHA-256 `38628E3CDB58601B320159560A443CF60291ADEED895A7ED1EA339938E3B8E27`；实际 manifest
  `com.kiyori / 45 / 0.1.0`，minSdk 26、targetSdk 34、compileSdk 37。
- 文档检查 513 文件、0 问题，差异空白检查通过。设备验收未执行，`terminal` 无改动；整体 Goal 持续进行，
  未形成最终提交或推送。后续先完善同名冲突、不覆盖和未知终态，再接通批量操作界面。

### M2b 本轮计划（2026-09-10）

- 复用 `copy_file`，新增显式 `copy_mode=no_replace`；保留未传该参数的既有工具合同。
  Android 在目标同卷暂存、校验后以 `renameat2(RENAME_NOREPLACE)` 提交；不支持的环境或文件系统
  明确失败，不退回覆盖复制。最小 JNI 只提供原子提交，不成为第二文件后端。
- 接通单项/批量复制、剪贴板来源与目标确认、逐项冲突的跳过/改名保留、停止后续项目、任务结果查看。
  UI 取消为“完成当前项后停止”，不会将已提交结果误报为撤销；退出页面前提示正在进行的任务。
- 继续固定批次与位置。失败、冲突、未开始、未知结果独立呈现，不自动重试未知写入。
  完整移动、永久删除、覆盖替换和持久任务恢复留在 M2 后续，尚不开放入口。
- 首批复制行为验证通过后，本轮追加同目录不覆盖重命名：复用同一原子提交，不允许移动到其他父目录，
  不执行复制后删源。冻结原位置、失败保留名称、结果未知禁止再次提交，成功清除旧名称选择并刷新对应窗格。
- 风险验证：真实临时目录覆盖竞争、空文件/Unicode、子树与链接、源变化、提交失败和暂存清理；
  协程验证冲突等待、停止、切栏和剪贴板替换。随后串行构建 Debug APK并核验新 native 库。
- 回滚边界为本轮精确差异；既有 24 个路径增量继续保留，不改变 `terminal`、稳定标识或存储数据。

### M2c / M3a 本轮计划（2026-09-10）

- 修复后退/前进残留旧选择、刷新未移除失效选择、根目录无效父目录项；选择始终限定当前目录可见项目。
- 增加自动/单位置/双位置布局、每栏独立目录内筛选、自然名称排序与方向选择、空目录/无匹配状态，
  保留同一 ViewModel 的两套位置、历史和滚动状态；完善行选中标识、触摸与元数据布局。
- 新建空文件不再经过拒绝空正文的旧 apply_file 链；既有工具增加显式不覆盖新建模式，同名返回冲突，
  不支持的后端可见失败。冻结创建位置，未知结果阻止原弹窗重试，退出检查覆盖全部在途写入。
- 验证真实临时目录的新建冲突、空文件与 Unicode，增加导航/刷新/筛选/异步新建测试；回归复制与
  重命名后串行构建 Debug APK。设备、移动/删除和持久恢复仍单独待完成；回滚仅限本轮精确增量。

### M2c / M3a 本地证据（2026-09-10）

- 已修复后退/前进残留旧目录选择、刷新后已删除项目仍入选、隐藏/筛选后选择不一致；错误/加载状态
  不再接受批量选择。两栏选择同时可见，活动位置决定批量操作来源，名称集合避免每行线性查找选择。
- 自动布局按宽度和字号选择单/双位置，支持显式切换。单位置有 A/B 标签、勾选控件和两行文件名；
  列表使用主题选中标识，元数据单行省略，空目录/无匹配/读取中分别呈现。根边界关闭无效上级动作。
- 自然数字名称排序支持任意长度数字段；目录保持优先，名称/大小/时间支持方向切换。
  目录内筛选只使用既有快照；按窗格与筛选词隔离滚动位置，拒绝旧筛选状态写回。
- 确认旧 `create_file` 经 `apply_file` 拒绝空正文，`make_directory` 把已存在目录报告为成功。
  新建显式进入独占创建路径，冻结目标、同名冲突、未知结果禁止原弹窗重试；退出检查覆盖所有在途写入。
- 最终定向测试 **93 项通过、0 失败、0 错误、0 跳过**（新增 23 项），覆盖全部文件管理器测试以及
  `LocalDirectoryCopyTest`、`LocalNoReplaceCopyTest`、`LocalNoReplaceRenameTest`、`LocalNoReplaceCreateTest`。
  日志为本地 `work/file_manager_m2c_tests_final.log`。首轮仅磁盘夹具的末尾空格被 Windows 路径 API
  拒绝；改为该平台可表示的中文/emoji/前导空格名称后重跑，Android 名称原样保留仍有独立策略测试。
- 正式开发准备检查 PASS；文档检查 513 文件、0 问题；`git diff --check` 通过。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 成功（5m33s），唯一 Launcher、脚本代理和
  播放器打包检查通过。最终 `app/build/outputs/apk/debug/app-debug.apk` 为 `488444443` bytes，
  SHA-256 `5D034AEC609FA0B0DB4A5518A2722F472A2633850C885C59F18D6C1ED62A17F6`，
  `com.kiyori / 45 / 0.1.0`，minSdk 26、targetSdk 34、compileSdk 37、arm64-v8a。
  `apksigner verify --verbose` 通过（v2），`zipalign -c -P 16 4` 通过。该检查验证 APK ZIP 对齐，
  不将其扩展为本轮未执行的全部 native ELF 审计。
- 当前 `main` / `213be2b3663749daf600af85228565669a269f8b`，累计 49 个修改/新增路径（包含前轮）；
  无暂存、无提交推送、`terminal` 干净。后续优先完成移动/删除的任务恢复和数据安全合同，
  再推进收藏最近、搜索定位和存储后端能力；最终统一提交推送仍待整体候选交付审计。
- 未操作设备，Android 独占创建、JNI 调用、触摸/横屏/大字体/TalkBack 和实际权限仍为
  `verification_pending`；没有把 M2/M3 或整个文件管理器标为完成。

### M3b 设置风格与 Material 3 精修计划（2026-09-10）

- 当前用户明确要求参考软件设置页，使用有语义色彩的现代 Material Design。复用当前
  `KiyoriSettingsTheme`、`KiyoriUiShapes` 和设置图标色板，主题边界同时覆盖页面、抽屉和全部弹层。
- 重组标题/路径、位置标签、可收起筛选、分组浏览选项、底部主次操作及复制状态；文件类型统一分类，
  以低饱和容器和有色图标替换黑色文件夹及高饱和实底；浅深色均读取已有产品 token。
- 修复“清空选择”误清另一栏、长按单项选择误用连续范围、滑动回调或选择对象携带旧元数据问题。
- 保持唯一 ViewModel、两栏历史/筛选/滚动和严格文件写入合同，回滚限本轮精确差异；不修改全局主题，
  不扩展到完整移动/删除或设备操作。定向测试与串行 APK 构建后记录静态证据，现场视觉单独待验收。

### M3b 实现与本地验证（2026-09-10）

- 页面及其全部弹层统一由 `KiyoriSettingsTheme` 提供主题；沿用设置页形状、分层背景和蓝色强调。
  类型图标使用 `resolveSettingsIconColors` 的配套浅深色容器/前景，不再硬编码黑色文件夹。
  文件类别统一驱动图标与色彩，增加 AVIF/WebP、FLAC/Opus、WebM、代码、现代归档及 APK 等展示分类；
  分类只影响展示，不推断真实文件内容或执行权限。
- 标题/路径区、圆角窗格、A/B 位置入口、默认收起的筛选、分组浏览选项、底部主次操作、复制面板与
  长按动作改用统一 Material 3 层级。新建/重命名/复制确认使用明确主按钮；复制面板显示逐项进度。
  搜索结果共用类型图标，长路径限制行数；搜索选项整行可点击，键盘提供搜索动作，页面消费 IME inset。
- “清空当前选择”只清活动栏；长按单项选择不再延伸滑动范围。手势消费最新回调，所有选择入口按
  当前快照解析元数据，避免刷新后传入旧对象；系统 Back 保留会话选择清除语义。
- 本轮相对进入时快照修改 **17 个 Kotlin 路径，增加 550 行、删除 399 行**，包含实现及测试；
  不将前轮累计差异计为本轮工作量。当前全局主题文件及文件写入后端无本轮改动。
- `:app:testDebugUnitTest` 定向文件管理器与 `KiyoriDesignThemeTest` 最终 **84 项通过、0 失败、
  0 错误、0 跳过**：71 项文件管理器测试及 13 项主题测试，新增 7 项选择行为/文件分类测试。
  浅深色设置图标对比度与主题文字配色由已有主题测试覆盖；不将其视为真实屏幕截图验收。
- 正式准备 PASS；文档检查 513 文件、0 问题；`git diff --check` 通过。设备触摸、大字体/横屏、
  弹层实际测量、无障碍与用户视觉接受度继续 `verification_pending`。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 成功（1m09s）；唯一 Launcher、脚本代理及
  播放器打包检查通过。最终 APK `app/build/outputs/apk/debug/app-debug.apk` 为 `488444443` bytes，
  SHA-256 `C656ED6A9BFBB52F53BC0DDB2D58D523BF302C33EC20C843E4975170246AC88A`；
  实际 manifest 为 `com.kiyori / 45 / 0.1.0`、minSdk 26、targetSdk 34、compileSdk 37、arm64-v8a。
  `apksigner verify --verbose` 通过（v2），`zipalign -c -P 16 4` 通过。
- 当前 `main` / `213be2b3663749daf600af85228565669a269f8b`，累计 52 个修改/新增路径；无暂存，
  未提交推送，`terminal` 干净。本轮 UI 增量完成本地验证；下一轮继续完善导航发现与可靠操作，
  设备视觉反馈用于后续调整，不能以当前构建替代真实接受度。

## 历史：手机存储框架

状态：`LOCAL IMPLEMENTATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

## 目标与范围

在文件管理首页的“手机存储”入口中，把现有共享 `FileManagerScreen` 收口为 Kiyori 风格的
MT 管理器双窗格工作台。目标是保留既有 AITool/SAF 文件能力，同时把截图中已经确认的页面结构
和高频交互补齐：深色顶部路径栏、双窗格文件列表、底部五键工具栏、存储抽屉、溢出菜单、长按
文件操作、新建和跳转。本轮继续细化截图中已经确认的行级视觉和触摸语义。

本轮不复制 MT 管理器的私有实现或引入新的文件系统后端；继续使用
`FileManagerViewModel`、`AIToolHandler`、`list_files` 及现有文件操作组件作为唯一能力 owner。
云盘、iCloud、最近删除仍保持文件管理首页的既有空动作。

## 已确认基线

- `KiyoriFileManagementPage` 的“手机存储”已经进入同一个 `KiyoriShellChild.FILE_MANAGER`。
- `FileManagerScreen` 当前持有单个 `FileManagerViewModel`，默认 `/sdcard`、单列列表，顶部工具栏
  的 `onNavigateBack` 与目录向上动作分开，但页面 `BackHandler` 仍直接调用 `onBack`。
- `FileManagerViewModel` 已实现目录读取、搜索、标签、复制/剪切/粘贴、新建、重命名、压缩、解压、
  删除、分享和打开；`FileContextMenu` 已是这些操作的统一呈现入口。
- Shell 的 `KiyoriShellState`、`KiyoriAppShell` 和 `KiyoriPrimaryNavigation` 有架构哈希快照；
  本轮不改变它们的导航控制面，只让页面内部先消费系统 Back。

## 设计合同

### 页面结构

1. 根页面绘制全屏不透明 `#FAFAFA` 背景；顶栏单独消费 `statusBarsPadding` 以把 `#303030`
   延伸到状态栏，底栏单独消费 `navigationBarsPadding`，内容区不再被根级顶部 inset 推离。
2. 顶部工具栏使用 Kiyori 中性深色表面和高对比文字；左上角固定放置“退出文件管理器”图标按钮，
   点击直接调用页面 `onBack`，不读取当前目录、不改变窗格路径。
3. 顶栏路径显示活动窗格路径、文件夹/文件统计和存储占用；汉堡按钮打开存储抽屉，溢出按钮打开
   刷新、搜索、全选、隐藏文件、排序、终端、书签、设置等现有能力入口。
4. 内容区固定为左右两个等宽窗格，窗格之间不留布局间隔。每个窗格由同一个 ViewModel 的
   `FileManagerPaneState` 投影，各自持有路径、目录列表、加载/错误状态、前进/后退目录历史和滚动位置。
   点击或触碰某一栏（包括空白区域和文件项）立即将其设为活动窗格；活动窗格用阴影和更高的
   `zIndex` 叠在另一栏上方，不使用加粗边框作为选中提示。
5. 文件列表是连续的白色行：未选中行保持纯白、行之间没有间隙和卡片圆角；左向右水平滑动一个
   文件项达到触摸阈值时选中该项，已选中项显示中性灰背景，再次点击同一项取消选中。目录 `..`
   不参与多选。文件名下方只显示修改时间；普通文件在同一行追加文件大小，目录不显示“文件夹”。
   目录图标使用黑色文件夹底，常见扩展名使用稳定的类型色和对应图标（图片、音频、视频、文档、
   压缩包、代码/文本等）。
6. 底部工具栏固定为返回、前进、新建、同步路径、上级目录五个触摸目标。返回/前进/上级只作用于
   活动窗格；第四个按钮把活动窗格的路径和环境复制到另一窗格并保持当前活动窗格不变，不交换两栏
   的内容或焦点。

### 回退顺序

页面注册一个高于 Shell child 回退的 `BackHandler`。系统 Back 的顺序固定为：

1. 已打开的对话框、菜单或抽屉先由其自身 owner 消费；
2. 活动窗格存在目录历史时返回上一目录；
3. 没有历史但仍处于普通子目录时返回父目录；处于非手机存储的根路径时先回到手机存储初始目录，
   不把该根路径直接交还给 Shell；
4. 活动窗格已经回到手机存储初始目录 `/storage/emulated/0`（设备真实路径）且无目录历史时，
   调用 `onBack`，由 Shell 或 `NavController` 退出文件管理器；
5. 左上角绝对退出按钮跳过以上目录步骤，始终直接调用 `onBack`。

两侧窗格的目录历史相互独立；关闭文件管理器后由外部 Shell/Router 恢复原 Settings 会话或
Toolbox 路由，页面不创建第二个导航 owner。

### 能力映射

- 双窗格加载继续调用 `list_files`，保留 `environment` 参数和结构化错误。
- 单击目录进入当前活动窗格；单击文件保留既有选中语义；长按继续打开 `FileContextMenu`。
- 新建弹窗区分“文件”和“文件夹”：文件名原样作为路径交给 `create_file`（扩展名仅由名称决定，
  不带扩展名时保持无格式文件），文件夹交给 `make_directory`；创建成功后只刷新发起操作的窗格。
- 长按菜单暂时只复刻 MT 管理器的中间弹窗，复制、移动、删除、重命名、工具、压缩、属性、分享、
  打开方式和添加书签按钮均为空点击；后续增量再接回既有 AITool 能力，避免在 UI 复刻阶段混入旧抽屉行为。
- 搜索、排序、隐藏文件和终端入口通过页面溢出菜单进入；未实现的 MT 专有动作不伪造结果。

### 本轮细节验收矩阵

| 场景 | 必须成立的可观察结果 |
| --- | --- |
| 初始手机存储 | 左右两栏等宽；行连续贴合；未选中行和图标底色与截图一致；顶栏仍显示活动路径统计 |
| 文件项横向手势 | 仅向右滑动超过阈值触发一次选择；向左滑动不改变选择；`..` 不被选中 |
| 文件项点击 | 普通模式目录进入当前栏、文件建立单项选中；多选模式点击已选项取消，列表为空时退出多选 |
| 窗格焦点 | 点击任意栏空白或文件项立即切换活动栏；活动栏有阴影叠层，无高亮边框；滚动位置按栏和路径保持 |
| 底栏第 4 键 | 当前活动栏路径复制到另一栏，当前活动栏和两栏其他历史不被交换；复制后另一栏显示相同路径 |
| 元数据与类型 | 目录不出现“文件夹”；目录仅日期；普通文件日期后显示大小；常见扩展名有独立颜色/图标 |
| 系统返回 | 弹层/抽屉先消费；之后消费活动栏历史或父目录；只有初始手机存储且无历史时调用外部 `onBack` |
| 顶栏绝对退出 | 任意子目录、选择、多选、弹层前置状态点击左上退出都直接调用外部 `onBack` |

## 实施阶段

1. [DONE] 完成截图、入口、ViewModel、Shell Back owner、正式门禁和架构快照基线审计。
2. [DONE] 冻结单 ViewModel 双窗格状态、活动窗格、目录历史和根目录退出合同。
3. [DONE] 按截图收紧双窗格行级视觉和手势：连续白底、灰底选择、日期/大小、类型图标、
   活动栏阴影与焦点切换。
4. [DONE] 将底部第 4 键改为活动路径同步到另一栏，补齐路径跳转与选择状态合同。
5. [DONE] 增补/调整 ViewModel、源码合同和 UI 级可测试策略，运行定向验证与差异检查。
6. [DONE] 串行构建 `:app:assembleDebug --no-daemon --console=plain`，核验 APK 元数据/签名/对齐。
7. [DONE] 审计精确提交树后提交 `main` 并推送 `origin/main`；真机视觉、触摸和系统 Back 复测
   单独保持 `verification_pending`。

## 2026-09-02 新建与长按中间弹窗计划

### 目标

1. 底栏第三个“新建”以及顶栏溢出菜单的“新建”统一打开 MT 风格白色居中弹窗；单行输入框下方
   使用蓝色指示线，底部按截图顺序保留“取消 / 文件 / 文件夹”三个动作。
2. 点击“文件”调用唯一文件创建能力 `create_file`，名称中的后缀不做推断或改写；点击“文件夹”
   只调用 `make_directory`，两者不能互相替代。
3. 长按文件或文件夹统一显示中间双列五行弹窗，移除 `KiyoriModalBottomDrawer`；文件夹的分享、
   打开方式固定置灰；当源栏与目标栏路径/环境相同，文件夹的移动也置灰。
4. 复制/移动行的方向箭头由长按源窗格决定：左栏朝右，右栏朝左；按钮点击保持空动作且不关闭弹窗。

### 实施范围

- `FileManagerViewModel.kt`：新增建项状态、长按源窗格状态和 `createNewFile`，复用现有目录加载与
  `make_directory` 处理；不新增 Router 或 ViewModel。
- `FileManagerScreen.kt`：统一新建入口与弹窗参数，记录长按源栏，移除长按旧操作回调。
- `components/NewFolderDialog.kt`：改为 MT 风格文件/文件夹建项弹窗，保留文件名输入状态。
- `components/FileContextMenu.kt`：重写为居中双列菜单的纯 UI，按文件类型和双栏路径计算置灰状态及箭头方向。
- `FileManagerSourceContractTest.kt`：覆盖建项动作区分、`create_file` 参数、居中弹窗、置灰规则和左右箭头。

### 非目标与验收

- 本轮不接回长按菜单的文件操作业务，不新增复制/移动后端，不改变双窗格导航、选择、Back 或 SAF 合同。
- 定向文件管理器 JVM 源码合同、`git diff --check`、正式开发准备门禁和串行
  `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` 必须通过并核验 Debug APK；不提交、不推送。
- 目标设备未连接时，弹窗宽度、字体栅格、系统 dim、长按触摸和真实文件创建仍标记为
  `verification_pending`。

## 风险与验收

- Android 设备的真实外部存储路径、权限和 `list_files` 环境必须由目标设备确认；本地 JVM 不能替代
  真机双指针/滚动/长按/系统 Back 验收。
- 现有 FileContextMenu 与 Shell child 共存时，必须保持对话框/抽屉先消费 Back，避免页面直接退出。
- 双窗格新增的状态必须全部归属于现有 ViewModel，禁止另起 `ViewModel`、平行 Router 或复制文件操作。
- 文件项手势必须与点击、长按事件保持互斥，避免一次横向滑动同时打开文件或菜单；活动栏阴影不能
  改变两栏可用宽度和列表滚动测量。
- `FileManagerViewModel` 当前接收的标准 Android `list_files` 时间字符串可能不带年份；手机存储和
  工作区优先读取真实本地文件时间，其他环境保留原始时间标签，不能把可见日期渲染成 1970 年。
- 验收证据分为源码/单元测试、Debug 构建/APK 静态审计和目标设备实测；未完成设备实测时状态为
  `verification_pending`。

## 2026-08-30 像素级 UI 对齐计划

### 证据基线

- 参考图固定为 `D:\03_Default\图片\Kiyori\Kiyori.jpg`、`MT管理器.jpg`、
  `左滑某个选项不松手.jpg`、`左滑某个选项松手后.jpg`，均为 `1260x2800`。
- `MT管理器.jpg` 的物理屏幕顶部 `0..328` 行为 `#303030`，内容与底栏主体为 `#FAFAFA`；
  列表图标外框约 `98px`，相邻行起点间隔约 `140px`。
- 当前 `Kiyori.jpg` 的状态栏为白色，顶部黑色表面从约 `134px` 才开始；当前紧凑行周期约
  `217px`、图标约 `149px`，与 MT 参考图存在可观察的密度偏差。
- 参考图的拖动中行会沿手指产生水平位移，抬手后选中行填充浅蓝色；两栏同名文件不能
  因 `FileItem` 值相等而同时显示选中。

### 本轮目标

1. 以 `#303030` 顶部表面绘制到状态栏物理顶边，并通过既有 system-bar owner 请求浅色状态栏图标；内容和底栏使用
   `#FAFAFA`，不改变 Shell 的唯一 system-bar owner。
2. 将双栏文件行收紧到参考图的物理密度：约 `40dp` 行高、`28dp` 图标、`5dp` 内边距和
   `4dp` 图标文字间距，保留完整的文件名、时间和大小排版，并让字体使用 Kiyori 现有
   Material typography。
3. 顶栏重新分配水平空间：退出键与汉堡键靠近左侧，路径/统计文本获得可用宽度，右侧
   溢出键贴近右边缘；不得因按钮槽位挤压掉路径统计的可见信息。
4. 窗格任意按下或水平滑动即激活该窗格；活动窗格使用与参考图一致的窄阴影叠层，
   不引入粗边框，也不缩小另一栏的测量宽度。
5. 文件项横向拖动保留实时位移，抬手后建立选择；再次点击取消。拖动同一窗格的另一项时，
   按列表索引选中锚点与目标之间的连续区间；普通点击不同项追加选择、点击已选项才取消；系统
   Back 清空当前会话全部选择；选择集合按窗格隔离，另一栏同名项始终保持未选中。
6. 底栏第四键显示分离的黑灰方向箭头：活动方向为灰色、另一方向为深黑；左栏活动时左箭头
   为灰、右箭头为黑，右栏活动时反向；点击只把活动栏路径/环境同步到另一栏，焦点和历史语义保持不变。

### 实施顺序与影响面

1. 先修改 `FileManagerScreen` 的 Insets/背景边界和 `FileManagerChrome` 顶/底栏几何。
2. 再修改 `FileListItem`、`FileManagerDualPane` 的行密度、拖动位移、窗格激活监听与
   阴影参数。
3. 在 `FileManagerViewModel` 中增加按窗格的选择锚点/连续范围计算，保持
   `FileContextMenu` 消费活动窗格的既有列表接口。
4. 更新源码合同与 JVM 测试，覆盖颜色、尺寸 token、范围选择、跨栏隔离、Back 清选和底栏方向色。
5. 串行执行定向测试、`compileDebugKotlin`、`git diff --check`、正式门禁和
   `:app:assembleDebug`，核验最终 APK；提交前审阅精确变更清单并推送 `main`。

### 非目标与风险边界

- 不新增文件后端、路由、ViewModel、系统栏 owner 或文件操作协议，不复制 MT 私有实现。
- 不把本地构建或截图静态分析描述为真机视觉/触摸证明；目标设备缺失时专项保持
  `verification_pending`。
- Android 字体栅格、系统密度、状态栏高度和阴影采样仍需在目标设备逐项复测；若设备实测
  与截图存在差异，只调整本轮明确的视觉 token，不改变文件导航和 Back 合同。

## 上一轮本地验收证据（基线提交）

- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`。
- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --tests "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*"`：`BUILD SUCCESSFUL`；包含目录回退策略和页面源码合同测试。
- `git diff --check`、`check_formal_readiness.py --require-main`、`check_fresh_clone.py`：均通过。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`，并通过 `verifySingleDebugLauncher`、`verifyDebugScriptProxyRuntimePackaging`、`verifyDebugPlayerRuntimePackaging`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，503,694,977 字节，SHA-256
  `DDC9E56CD80DC814AC48A653B03F2FD58FD0EFA221876483319C3AFEEE570E43`；`aapt dump badging` 报告
  `com.kiyori / 45 / 0.1.0 / compileSdk 37`，launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`。
- `apksigner verify --verbose --print-certs`：Android Debug V2、单 signer 通过；
  `zipalign -c -P 16 -v 4`：`Verification successful`。APK 共 5512 个 ZIP entry，仅
  `arm64-v8a`、53 个 `.so` 且 basename 无重复。
- 未安装 APK 或操作目标 Android 设备；双窗格视觉、触摸、滚动、长按、SAF 权限和系统 Back
  现场复测继续保持 `verification_pending`。

## 本轮细节迭代验收证据（2026-08-30）

- 参考截图逐张核对：`软件初始状态默认内部存储.jpg`、`不同格式文件图标.jpg`、`点击底栏中间按钮.jpg`、
  `长按文件夹弹出弹窗.jpg`、顶栏抽屉/溢出/路径跳转截图；确认连续白行、类型色图标、活动栏阴影和
  底栏第 4 键路径同步语义。
- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --tests "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*"`：`BUILD SUCCESSFUL`；源码合同覆盖右滑阈值、灰/白行、无“文件夹”、阴影、路径镜像、非初始根返回。
- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`。
- `git diff --check`、`.venv/Scripts/python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`、
  `.venv/Scripts/python.exe -B ci/script/check_fresh_clone.py --repository .`：均通过。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`；
  `verifySingleDebugLauncher`、`verifyDebugScriptProxyRuntimePackaging`、`verifyDebugPlayerRuntimePackaging` 均通过。
- 本轮 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，503,694,977 字节，SHA-256
  `0D345FB6B0CFB7B273788FDF52342CE957C6F807EA8D82642ED12F73F429FA42`；`com.kiyori / 45 / 0.1.0 / compileSdk 37`，
  launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer、16 KiB zipalign、
  5512 ZIP entries、仅 `arm64-v8a`、53 个 `.so` 且 basename 无重复。
- `adb devices` 未发现目标设备；视觉密度、滑动/点击/长按、滚动、SAF 授权和系统 Back 仍需真机验收，状态保持
  `verification_pending`。

## 本轮像素级对齐实现证据（2026-08-30）

- 读取并逐像素抽样四张 `1260x2800` 参考图：MT 顶部 `#303030`、内容/底栏 `#FAFAFA`、
  图标外框约 `98px`、列表行周期约 `140px`、选中填充约 `#7DBEDC`、活动栏阴影在分栏边界
  形成约 `16px` 的灰度渐变。
- `FileManagerScreen` 移除根级 `safeDrawing` 顶部推移，使用全屏 `#FAFAFA` 背景；
  `FileManagerTopBar` 消费 `statusBarsPadding` 并使用 `56dp` 工具栏，同时通过
  `KiyoriStatusBarAppearanceOverride` 让统一 system-bar owner 使用浅色状态栏图标；`FileManagerBottomBar`
  消费导航栏 inset；顶栏左右槽位与路径统计宽度按截图重新分配。
- `FileListItem` 紧凑双栏固定为 `40dp` 行高、`28dp` 图标、`5dp` 内边距、`4dp` 间距和
  `4dp` 圆角；未选中 `#FAFAFA`，拖动实时平移，抬手选中 `#7DBEDC`，目录图标底色为 `#2B2B2B`。
- `FileManagerDualPane` 在栏位按下瞬间激活焦点，活动栏阴影调整为 `8dp`，移除父级整栏 ripple，
  选中判断限定在活动栏；`FileManagerViewModel` 以文件名锚点计算同栏连续滑动范围，普通点击清除锚点，
  不同文件点击追加、已选文件点击移除、系统 Back 清空两栏选择，跨栏同名项不共享选中态。
- `FileManagerScreen` 移除全屏 `LoadingOverlay`，仅保留窗格内空列表加载提示，并将
  `ModalNavigationDrawer.gesturesEnabled` 设为 `false`，抽屉只由顶栏汉堡按钮打开。
- `FileListItem` 紧凑双栏左右拖动均可建立选择，最大位移按左滑完全隐藏左侧图标的距离计算，超过约半个
  图标即选中，元数据字号收紧为 `10sp`；底栏第四键使用两个分离的 `16dp`
  方向箭头，活动方向为 `#BEBEBE`、另一方向为 `#646464`。
- `FileManagerTopBar` 左侧汉堡图标仅视觉左移 `6dp`，右侧溢出键贴齐屏幕右端；统计字号为 `10sp`，文案统一为
  `文件夹：… 文件：… 储存：已用/总量`，容量使用 `totalBytes - availableBytes` 并保留两位小数。
- 定向验证：`:app:testDebugUnitTest` 的文件管理器 suite 与 `KiyoriSettingsPagesTest` 均 `BUILD SUCCESSFUL`；
  `:app:compileDebugKotlin` 已通过。正式门禁和新鲜克隆检查均 `PASS`。
- 串行 `:app:assembleDebug --no-daemon --console=plain` `BUILD SUCCESSFUL`，并通过
  `verifySingleDebugLauncher`、`verifyDebugScriptProxyRuntimePackaging`、
  `verifyDebugPlayerRuntimePackaging`。最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
  `503,694,977` bytes，SHA-256 `3A39636375F933864B0EE74FB9C8686E44923C5DECFD3FDA084C7F2E50F4BCCC`；
  `com.kiyori / 45 / 0.1.0 / compileSdk 37`，唯一 launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer、16 KiB zipalign、
  `5512` ZIP entries、`44` DEX、`arm64-v8a` 的 `53` 个 `.so` 且 basename 无重复。
- `adb devices` 当前无目标设备；状态栏图标明暗、真实密度、拖动/长按/滚动、SAF 和系统 Back 仍保持
  `verification_pending`。

## 2026-08-30 点击闪白与交互边界修正

### 本轮实现

- 移除 `FileManagerScreen` 的页面级 `LoadingOverlay`；目录加载只保留窗格内空列表提示，点击文件或切换目录不再用
  半透明层覆盖整个文件管理器，避免屏幕闪白。
- `ModalNavigationDrawer` 关闭边缘拖动，文件管理器左抽屉只由顶栏汉堡按钮打开；窗格父层移除整栏 `clickable` ripple，
  空白区域轻触只切换焦点，不绘制整栏灰色按压背景。
- 普通点击不同文件追加当前窗格选择，点击已选文件移除；系统 Back 首先清空左右两栏全部选择，底栏后退继续只处理
  目录历史；左右栏的选择集合、单项状态和连续滑动锚点彼此隔离。
- 紧凑文件行元数据字号收紧到 `10sp`，横向拖动左右均可选择，最大位移统一按左滑完全隐藏左侧图标的距离计算，
  约半个图标达到阈值后选中；活动栏阴影提升到 `8dp`，底栏路径同步按钮使用分离的 `16dp` 黑灰箭头。
- 顶栏汉堡图标视觉左移 `6dp`，溢出按钮贴右端；统计使用 `文件夹：… 文件：… 储存：已用/总量`，容量按
  `totalBytes - availableBytes` 计算并显示两位小数。

### 本轮验证

- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --tests "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*" --tests "com.ai.assistance.operit.ui.main.shell.KiyoriSettingsPagesTest"`：通过。
- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`：通过。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：通过，并通过单 launcher、脚本代理和播放器运行时打包检查。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`503,694,977` bytes，SHA-256
  `299DA5B29196E1AC7F17B0E20EC5767727A1194DEEC819400A3886824405B67E`；包名 `com.kiyori`，versionCode `45`，
  唯一 launcher `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer、16 KiB zipalign 通过。
- 正式开发准备门禁与新鲜克隆检查通过；架构边界检查仍报告提交基线既有的 App Shell/AI Drawer/主题哈希漂移，未涉及本轮文件管理器文件。
- `adb devices` 无目标设备；真实设备密度、拖动动画、阴影采样、SAF、长按和系统 Back 现场验收继续保持 `verification_pending`。

## 2026-08-31 顶栏统计与双向滑动点击反馈增量

### 本轮实现

- `FileManagerTopBar` 保持顶栏内容总高度 `56dp`：文件夹路径与退出、汉堡、溢出按钮位于同一
  `40dp` 行并视觉下移 `2dp`；统计文案的 `16dp` 全宽居中行位置保持不变，字号收紧为 `10sp/12sp`，
  内容视觉上移 `2dp` 以缩短两行间距并增加下方留白，不再受到左右按钮槽位限制。
- 底栏第 4 个路径同步按钮保持“当前栏路径复制到另一栏”的单向来源语义，箭头间距收紧为
  `16dp` 图标配 `-9dp/+9dp` 横向偏移和上下 `4dp` 错位，恢复截图中的右上/左下图片样式；
  颜色按截图采样为当前栏浅灰 `#BEBEBE`、另一栏深灰 `#646464`。
- 文件项点击通过独立 `MutableInteractionSource` 绘制 `#E0E0E0` 灰色按压背景和 `2dp` 阴影，
  未按压时继续保持白色或已选蓝色；双栏目录点击会先保持 `80ms` 行级灰色反馈，再执行目录跳转，
  避免路径状态立即替换旧行导致按压效果不可见。
- 底栏第一、第二个按钮使用无横杆的 `KeyboardArrowLeft`/`KeyboardArrowRight` 方向箭头，第四键使用
  上下错位的 `ArrowBack`/`ArrowForward` 图片样式，保留
  原有活动栏后退/前进能力和禁用态颜色。
- 紧凑双栏左右滑动均可建立选择。两个方向统一以“左滑时左侧图标刚好完全隐藏”的位移作为最大值，
  该位移包含左侧内边距；两种方向使用相同的选择阈值与连续选择状态。

### 本轮验证

- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --tests
  "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*"`：`BUILD SUCCESSFUL`；
  源码合同覆盖统计独占行、统计字号、居中布局、灰色按压/阴影、箭头颜色方向、箭头间距以及
  双向滑动和左侧图标隐藏边界。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`，并通过
  `verifySingleDebugLauncher`、`verifyDebugScriptProxyRuntimePackaging`、
  `verifyDebugPlayerRuntimePackaging`。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`503,694,977` bytes，SHA-256
  `904C0F23A91D45D9B84F7B8044FC321F96D049682FCF03ECFE2CF0E4D3ABEDA6`；包名 `com.kiyori`，
  versionCode `45`，versionName `0.1.0`；Android Debug V2 单 signer、16 KiB zipalign 通过。
- `adb devices` 无目标设备；真实设备密度、按压动画、双向拖动边界和系统 Back 仍需现场复测，状态保持
  `verification_pending`。

## 2026-09-02 新建与长按中间弹窗实现证据

### 本轮实现

- 底栏第三个“新建”和顶栏溢出菜单统一打开 `FileManagerNewEntryDialog`；输入框使用透明容器和蓝色
  下划线，底部按截图顺序提供“取消 / 文件 / 文件夹”，空输入时按钮仍保持 MT 的蓝色视觉。
- “文件”调用 `FileManagerViewModel.createNewFile`，向 `create_file` 传入原始名称拼接的路径和空 `new`
  内容；名称是否带 `.txt` 等后缀完全由用户输入决定，不做隐式格式改写。“文件夹”继续只调用
  `make_directory`。
- `FileContextMenu` 从 `KiyoriModalBottomDrawer` 改为白色居中 `Dialog`，按参考图排列头部提示、关闭键和
  双列五行操作；所有操作行点击体为空，关闭键是唯一会关闭弹窗的动作。
- 文件夹的“分享”和“打开方式…”使用禁用灰色；文件夹在左右栏路径与环境相同的情况下“移动”也使用
  禁用灰色。复制/移动箭头由 `contextMenuPane` 决定，长按左栏朝右、长按右栏朝左。
- ViewModel 新增建项状态和长按源窗格状态，未新增导航、ViewModel 或文件系统后端；既有双窗格选择、目录
  历史、Back 和 SAF 路径保持不变。

### 本轮验证

- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --tests
  "com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.*"`：`BUILD SUCCESSFUL`，13 项文件管理器
  测试通过，新增源码合同覆盖建项动作、`create_file` 参数、居中菜单、置灰规则和左右箭头。
- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`：`BUILD SUCCESSFUL`。
- `.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`：`PASS`。
- `.venv\Scripts\python.exe -B ci/script/check_fresh_clone.py --repository .`：`PASS`（基线
  `3ec7f39fa6431bc5cb85ef44fa318d4423daf077`）。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`；唯一 launcher、脚本代理和
  播放器运行时打包检查通过。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，`503,705,425` bytes，SHA-256
  `6B83750E17DB0958CF183CAFA10ADBA33117F62C507802A471F4E86DC9969079`；`com.kiyori / 45 / 0.1.0 / compileSdk 37`，
  launcher 为 `com.ai.assistance.operit.ui.main.MainActivity`；Android Debug V2 单 signer、16 KiB zipalign 通过。
- `adb devices` 未发现目标设备；弹窗宽度/字体栅格、系统 dim、长按触摸、箭头方向现场效果和真实文件创建仍为
  `verification_pending`。本轮未提交、未推送。

## 2026-09-02 对比图像素级弹窗几何校正

### 本轮实现

- 新建弹窗固定为 `310dp × 151dp`，保留 `24dp` 内容边距；标题调整为 `20sp/24sp`，输入区改为
  无额外 Material 最小高度的 `BasicTextField`，以 `32dp` 编辑区和 `2dp` 蓝色下划线对齐 MT 截图。
- 新建按钮栏固定为 `48dp`：左侧“取消”，右侧“文件”和“文件夹”之间保留 `32dp` 间距，文案统一
  `14sp/20sp`；文件/文件夹创建回调和空输入校验保持上一轮状态。
- 长按菜单固定为 `320dp × 269dp`，表头 `28dp`、分隔线 `1dp`、正文 `5 × 48dp`；正文图标收紧为
  `24dp`、文案为 `16sp/20sp`，压缩使用下载箭头图标，添加书签使用集合书签图标。
- 长按菜单窗口清除系统 `FLAG_DIM_BEHIND` 并保留 `8dp` 阴影，匹配 MT2/MT3 中“背景不变暗、弹窗有阴影”
  的像素关系；新建弹窗继续使用默认 dim，匹配 MT1。
- 左栏长按显示“复制 -> • / 移动 -> •”，右栏长按显示“<- 复制 • / <- 移动 •”；蓝点始终位于
  文案末尾，文件夹禁用“移动”（同路径同环境）、“分享”和“打开方式…”的灰色规则不变，所有动作仍为空点击。

## 2026-09-02 对比图更新后的第二轮微调

- 顶栏总高度保持不变，仅将第二行“文件夹 / 文件 / 储存”统计信息的绘制偏移上移到 `-8dp`，贴近
  `/storage/emulated/0/` 路径行；路径行、状态栏和底栏布局不变。
- 长按菜单表头按更新后的 MT 截图缩小为 `12sp/16sp`，左侧内容边距调整为 `10dp`；正文五行仍为
  `16sp/20sp`，菜单底色保持 `#FAFAFA`，窗口上移 `65px` 以匹配 MT2/MT3 的居中位置。
- 本轮只改变像素几何和视觉 token，不恢复长按操作业务；源码合同、Kotlin 编译和文件管理器定向测试
  继续作为自动化验收，真实设备字体栅格、阴影和触摸仍为 `verification_pending`。

### 验收边界

- 已按 `D:\03_Default\图片\Kiyori\对比图\Kiyori1.jpg`、`MT1.jpg`、`Kiyori2.jpg`、`MT2.jpg`、
  `Kiyori3.jpg`、`MT3.jpg` 的原始 `1260×2800` 像素测得弹窗边界、行周期、图标与文字投影，并将尺寸
  token 固化到源码合同测试。
- 文件管理器定向 JVM 测试 `13/13`、Kotlin 编译、正式开发准备门禁和 `git diff --check` 均通过。
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL`；唯一 Launcher、脚本
  代理和播放器运行时打包检查通过。最终 APK 为 `app/build/outputs/apk/debug/app-debug.apk`，
  `487,740,940` bytes，SHA-256 `6DBCF6ACBF7650F511473EEB721C571003A733721598F2633F1DF9E5B8C14663`；
  `com.kiyori / 45 / 0.1.0`、Android Debug V2 单 signer 和 16 KiB zipalign 均通过。
- 未连接目标 Android 设备；系统 dim、字体栅格、阴影、触摸命中、左右箭头现场效果和真实文件创建继续
  保持 `verification_pending`。本轮不提交、不推送。
