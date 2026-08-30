# MT 管理器手机存储复刻

状态：`LOCAL IMPLEMENTATION AND AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。

## 目标与范围

在文件管理首页的“手机存储”入口中，把现有共享 `FileManagerScreen` 收口为 Kiyori 风格的
MT 管理器双窗格工作台。目标是保留既有 AITool/SAF 文件能力，同时把截图中已经确认的页面结构
和高频交互补齐：深色顶部路径栏、双窗格文件列表、底部五键工具栏、存储抽屉、溢出菜单、长按
文件操作、新建和跳转。

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
4. 内容区固定为左右两个等宽窗格。每个窗格由同一个 ViewModel 的 `FileManagerPaneState` 投影，
   各自持有路径、目录列表、加载/错误状态、前进/后退目录历史和滚动位置；点击窗格只改变活动窗格。
5. 底部工具栏固定为返回、前进、新建、交换窗格、上级目录五个触摸目标。返回/前进只作用于活动窗格
   的目录历史，交换只切换活动窗格，向上只改变活动窗格目录。

### 回退顺序

页面注册一个高于 Shell child 回退的 `BackHandler`。系统 Back 的顺序固定为：

1. 已打开的对话框、菜单或抽屉先由其自身 owner 消费；
2. 活动窗格存在目录历史时返回上一目录；
3. 活动窗格已经回到手机存储初始目录 `/storage/emulated/0`（设备真实路径）且无目录历史时，
   调用 `onBack`，由 Shell 或 `NavController` 退出文件管理器；
4. 左上角绝对退出按钮跳过以上目录步骤，始终直接调用 `onBack`。

两侧窗格的目录历史相互独立；关闭文件管理器后由外部 Shell/Router 恢复原 Settings 会话或
Toolbox 路由，页面不创建第二个导航 owner。

### 能力映射

- 双窗格加载继续调用 `list_files`，保留 `environment` 参数和结构化错误。
- 单击目录进入当前活动窗格；单击文件保留既有选中语义；长按继续打开 `FileContextMenu`。
- 复制、剪切、粘贴、重命名、压缩、解压、删除、分享、打开和新建沿用现有 AITool 调用及日志。
- 搜索、排序、隐藏文件和终端入口通过页面溢出菜单进入；未实现的 MT 专有动作不伪造结果。

## 实施阶段

1. [DONE] 完成截图、入口、ViewModel、Shell Back owner、正式门禁和架构快照基线审计。
2. [DONE] 冻结单 ViewModel 双窗格状态、活动窗格、目录历史和根目录退出合同。
3. [DONE] 增加双窗格列表/顶部路径统计/底部五键/存储抽屉与溢出菜单，接入现有操作组件；恢复多选与 SAF 书签校验/删除行为。
4. [DONE] 增补 ViewModel 目录历史与回退单元测试、页面源码合同测试，运行定向验证和差异检查。
5. [DONE] 串行构建 `:app:assembleDebug --no-daemon --console=plain`，核验 APK 元数据/签名/对齐。
6. [DONE] 审计精确提交树后提交 `main` 并推送 `origin/main`；真机视觉、触摸和系统 Back 复测
   单独保持 `verification_pending`。

## 风险与验收

- Android 设备的真实外部存储路径、权限和 `list_files` 环境必须由目标设备确认；本地 JVM 不能替代
  真机双指针/滚动/长按/系统 Back 验收。
- 现有 FileContextMenu 与 Shell child 共存时，必须保持对话框/抽屉先消费 Back，避免页面直接退出。
- 双窗格新增的状态必须全部归属于现有 ViewModel，禁止另起 `ViewModel`、平行 Router 或复制文件操作。
- 验收证据分为源码/单元测试、Debug 构建/APK 静态审计和目标设备实测；未完成设备实测时状态为
  `verification_pending`。

## 本轮本地验收证据

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
