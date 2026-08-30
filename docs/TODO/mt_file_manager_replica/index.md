# MT 管理器手机存储复刻

状态：`LOCAL PIXEL ALIGNMENT COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

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
- 复制、剪切、粘贴、重命名、压缩、解压、删除、分享、打开和新建沿用现有 AITool 调用及日志。
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
   按列表索引选中锚点与目标之间的连续区间；普通点击只切换单项，不扩展区间；选择集合
   按窗格隔离，另一栏同名项始终保持未选中。
6. 底栏第四键显示“一黑一白”的方向箭头：左栏活动时左箭头为白、右箭头为黑，右栏
   活动时反向；点击只把活动栏路径/环境同步到另一栏，焦点和历史语义保持不变。

### 实施顺序与影响面

1. 先修改 `FileManagerScreen` 的 Insets/背景边界和 `FileManagerChrome` 顶/底栏几何。
2. 再修改 `FileListItem`、`FileManagerDualPane` 的行密度、拖动位移、窗格激活监听与
   阴影参数。
3. 在 `FileManagerViewModel` 中增加按窗格的选择锚点/连续范围计算，保持
   `FileContextMenu` 消费活动窗格的既有列表接口。
4. 更新源码合同与 JVM 测试，覆盖颜色、尺寸 token、范围选择、跨栏隔离和底栏方向色。
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
- `FileManagerDualPane` 在栏位按下瞬间激活焦点，活动栏阴影调整为 `4dp`，选中判断限定在活动栏；
  `FileManagerViewModel` 以文件名锚点计算同栏连续滑动范围，普通点击清除锚点，跨栏同名项不共享选中态。
- 底栏第四键改用两个方向箭头叠加，活动方向为白色、另一方向为黑色；路径同步仍复用既有
  `navigatePaneTo`，不交换焦点或文件操作 owner。
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
