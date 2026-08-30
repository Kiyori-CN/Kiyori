# MT 管理器手机存储复刻

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

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

1. 根页面消费 `WindowInsets.safeDrawing`，背景使用 Kiyori `background`/`surface` 语义色。
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
