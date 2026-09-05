---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
---

# Kiyori 完整首次启动体验

## 改造前状况

改造前的首次启动沿用 Operit 的“协议页 + 三张宣传页 + 重复欢迎页 + 基础权限 +
权限级别”六页结构。页面只做过 Kiyori 语义色适配，产品定位、授权范围、导航和启动时序仍以
Operit AI 为中心。

旧协议页同时承担首次确认与设置内查看，使用固定五秒倒计时；权限页只覆盖文件、悬浮窗、电池和
位置，却把四项全部描述为正常运行所必需。通知权限可能在协议尚未确认时弹出，协议与权限完成
回调还会重复调用 `setContent`。协议资源把仓库实际的 GPL-3.0-or-later 错写成 LGPLv3。

## 目标

- 用六页中文流程准确表达 Kiyori 的 AI 浏览器定位，以及浏览、下载、播放、AI 对话、文件管理、
  本地工具和扩展生态
- 建立简洁、现代、响应式的统一视觉，每页只承担一个任务并只保留一个主操作
- 用户协议与隐私政策保持两个独立查看入口，但不设置打开、阅读进度或滚动到底门槛
- 权限页与设置内“权限与设备能力”复用同一 21 项目录、三组结构、真实 snapshot、状态口径和授权动作
- 首次启动不提供跨运行时权限、系统访问和高影响能力的全局全选；用户逐项决定本次需要处理的项目
- 完成用户所选授权后直接进入现有 Software Home，不再保留独立完成页
- 保留 Android 12+ 透明系统启动窗口、单次 Compose 挂载和首帧后初始化
- Kiyori 尚未发布，旧 Operit 页面语义、旧状态枚举、旧资源和旧测试合同直接删除
- 当前只维护中文默认资源，其他语言在六页中文完成视觉验收后统一处理

## 2026-09-06 权限管理能力迭代计划

状态：`IMPLEMENTED, VALIDATION PENDING`。本轮按未发布版本处理，继续由 `KiyoriPermissionId`、
`KiyoriPermissionSnapshot`、共享 metadata 和授权动作作为唯一状态所有者。

### 目标与边界

- 将设置首页的“更多功能”入口改名为“更多功能-权限管理”，权限子页标题改为“权限管理”。
- 在首启权限页和 Settings 权限管理页共同加入“读取已安装应用列表”与“解除设置限制”两项；两页保持相同顺序、状态和动作。
- 首启权限页支持“全选可处理项”和逐项手动选择；全选只加入当前设备真实可处理且尚未完成的条目，不把使用时确认或已完成能力伪装成可授予。
- “读取已安装应用列表”复用已声明的 `QUERY_ALL_PACKAGES` 能力并以真实 PackageManager 可见性判断状态；它不是运行时弹窗权限。
- “解除设置限制”使用 Android 公开可用的应用详情设置入口，明确由用户在系统页完成“允许受限设置”和设备认证；不调用设备监管专用或受保护的系统接口，不伪造应用内已解除状态。
- 设置首页底部安全留白由 `96dp` 缩短为 `56dp`，保留导航栏可达空间和最后一项的触摸范围。

### 实施顺序

1. [DONE] 复核 Android SDK/API、Manifest 查询能力、现有权限队列和系统页返回刷新路径。
2. [DONE] 扩展共享权限目录、快照、动作解析、文案和分组；保持 onboarding/Settings 同一 owner。
3. [DONE] 在首启权限页增加全选/取消全选状态控制，并继续保存用户选择与授权队列。
4. [DONE] 更新 Settings 入口标题、权限页标题和首页底部留白；同步测试合同与 `CONTEXT.md`。
5. [IN PROGRESS] 运行定向 JVM/架构检查、`git diff --check`、正式开发门禁和串行 Debug 构建，核对 APK。
6. [PENDING DELIVERY] 审阅最终差异和 staged allowlist 后提交并推送 `main`；真机系统页、指纹/密码弹窗和 OEM 视觉保持 `verification_pending`。

## 流程

1. 欢迎使用 Kiyori
2. 从发现到播放，内容自然流动
3. 让 AI 读懂上下文，也能继续行动
4. 文件、终端与扩展能力，一处展开
5. 用户协议与隐私政策
6. 权限设置并进入 Kiyori

第 1 页使用四张产品域卡片建立完整定位；第 2 至第 4 页使用主题导语和 2×2 能力卡片继续展开。
页面不把当前只有产品域入口的模块写成已经拥有完整详情页，但会准确展示 Kiyori 的浏览、内容、
AI、语音、连接、文件、终端、工具箱、小程序和日志等工作空间方向。

顶部始终显示 Kiyori、进度条和单行步骤文案，并消费状态栏、显示缺口、横向边缘与导航栏的
`WindowInsets.safeDrawing`。第 1 页系统返回退出；第 2 至第 6 页可以返回上一步。打开协议正文时
隐藏首启顶部，只显示文档自己的标题、当前版本、摘要、正文层级和返回入口。已完成首启但协议
版本过期时使用同一安全区和法律文档组件，不建立第二份正文或同意状态。

六页共用一个 `HorizontalPager`。第 1 至第 4 页可左右滑动，单次手势最多移动一页；第 5 页
在用户明确同意协议前只允许向右返回，不能向左滑入权限页，确认后恢复双向滑动；第 6 页可向右
返回协议页。授权队列处理中同时锁定 Pager 和顶部返回，系统设置返回后刷新真实权限状态再继续。

## 授权范围

### 运行时权限

- 通知
- 媒体音频与视频
- 相机
- 麦克风
- 精确与粗略位置
- 蓝牙扫描与连接
- 电话拨号
- 短信发送、读取与接收
- Android 12 及以下读取外部存储；Android 9 及以下写入外部存储

### 特殊访问

- 所有文件访问
- 在其他应用上层显示
- 修改系统设置
- 使用情况访问
- 安装未知来源应用
- 忽略电池优化
- 通知读取
- 系统默认助手

### 高级设备能力

- Kiyori 无障碍支持 / Kiyori UI 自动化服务
- Shizuku / ADB
- Root

当前工作流调度使用 WorkManager，普通设备闹钟使用 `ACTION_SET_ALARM`，首启不声明或请求
`SCHEDULE_EXACT_ALARM`。精确 `AlarmManager` 调度没有当前产品消费者，未来需要时必须另立
功能合同和目标 OEM 验收范围。

屏幕捕获等 Android 不提供长期预授权的能力只在清单中说明“使用时由系统确认”，不得保存虚假的
永久授权状态。

## 作用域

```text
app/src/main/java/com/kiyori/integration/operit/onboarding/
app/src/main/java/com/kiyori/app/startup/
app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/
app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt
app/src/main/java/com/ai/assistance/operit/ui/main/screens/OperitScreens.kt
app/src/main/assets/accessibility.apk
app/src/main/assets/accessibility_version.txt
app/src/main/res/values*/
config/architecture/
ci/script/check_architecture_boundaries.py
ci/test/test_architecture_boundaries.py
CONTEXT.md
README.md
docs/doc-src/
docs/TODO/
```

不改变数据库、备份、ToolPkg、MCP、OAuth、Intent、AIDL、JNI 或其他稳定兼容标识。不安装应用、
操作设备、构建 Release 或发布商店产物。本轮经用户明确授权，在完整本地验证和精确暂存审计后提交
并推送 `main`。

## 验收

- 全新安装按六页新顺序进入 Software Home，生产 UI 不出现旧 Operit 首启文案
- 第 1 至第 4 页分别覆盖产品总览、浏览器与媒体、AI 协作、文件与扩展，信息不重复
- 每页底部只有一个主按钮，返回使用顶部入口，不再堆叠跳过、刷新、继续下一项和摘要按钮
- 第 5 页只由一个同意复选框控制主按钮，不要求打开或滚动两份协议
- 第 1 至第 6 页的主按钮、顶部返回、左右滑动、步骤进度和持久化当前页指向同一个 Pager 状态
- 单次滑动不能跨页；协议未同意不能滑入权限页；授权处理中不能切换页面
- 设置内协议入口继续提供两份只读文档
- 第 6 页不显示权限等级；按“应用权限 / 系统访问 / 高级设备能力”复用设置页同一目录，只有当前
  可操作且由用户逐项选择的项目显示选择框
- 已授权、不适用和使用时确认状态真实显示；只对用户选中的可操作项目发起处理
- 不提供会一次选择电话、短信、通知读取、无障碍、Shizuku、Root 等高影响能力的全局全选
- 没有选择权限时可以直接进入；完成所选授权后直接进入，不存在 `READY` 页面
- 首启中断后恢复当前步骤；系统设置往返后刷新状态和继续处理用户选择
- 协议版本、首启步骤和完成版本拥有唯一持久 owner
- `MainActivity.setContent` 只有一个启动挂载入口，通知权限不在协议前自动请求
- 定向 JVM、首启正反向架构测试、正式开发准备、差异检查和 Debug APK 构建通过
- 真机视觉、OEM 系统设置、授权拒绝和进程回收路径保持 `verification_pending`
- 系统安装页、应用列表和无障碍设置中只显示 Kiyori 品牌；内部 provider 包名和 AIDL action
  保持现有 IPC 合同

## 2026-08-28 协议、隐私与权限同步优化

状态：`LOCAL IMPLEMENTATION, AUTOMATED VALIDATION AND DEBUG APK AUDIT COMPLETE / DEVICE VERIFICATION PENDING`。

### 本轮问题证据

- 全新安装六页根容器已经消费 `WindowInsets.safeDrawing`，但完成过 onboarding 后由协议版本升级触发的
  独立确认分支没有自己的安全区，edge-to-edge 下可能让顶部内容进入状态栏。
- Settings 权限中心已按三组展示共享 21 项事实，首次启动仍平铺 `KiyoriPermissionId.entries` 并提供
  全局全选，信息层级和高影响授权边界不一致。
- `MEDIA` 实际只声明并请求视频、音频与 Android 14 选择性视觉媒体权限，没有
  `READ_MEDIA_IMAGES`；“照片、视频与音频”会把照片全库读取能力描述得比实现更宽。
- 协议版本同时出现在 `AgreementPreferences.CURRENT_AGREEMENT_VERSION` 和两份正文文字中，版本升级时
  存在显示内容漂移风险。
- 首次启动完成后显示的插件加载浮层位于最高 zIndex，但根容器没有消费 `safeDrawing`。

### 实施与验收合同

1. `AgreementPreferences` 继续是唯一同意版本 owner；两份正文继续由 `KiyoriLegalDocument` 同时供
   首次启动与 Settings 消费，正文显示版本由该常量格式化，不复制版本字面量。
2. 首次安装与协议重确认都完整消费 `safeDrawing`；法律正文显示返回、文档摘要、版本和“完整正文”
   层级，长文本可滚动、可选择复制，不遮挡系统栏。
3. 首次启动权限页直接遍历 `kiyoriPermissionGroups`，复用 Settings 的三组顺序、说明、metadata、
   status 与 action；不复制权限清单或状态。
4. 删除全局全选，只保留逐项选择和清空已选；授权中显示明确进度并锁定返回、滑动和重复点击，失败
   继续记录真实异常并提示对应项目。
5. `MEDIA` 文案明确为视频与音频，照片通过系统文件选择入口按次选择；隐私政策的权限说明同步。
6. 插件加载浮层消费 `safeDrawing`，TalkBack 折叠动作使用中文描述，不改变其 loading state owner、
   超时、跳过或 MCP 启动协议。
7. 定向 onboarding/startup/agreement JVM、首启架构门禁、formal readiness、Markdown 链接、差异检查、
   串行 Debug APK 构建和产物审计通过；真机深浅主题、大字体、横竖屏、系统设置往返和 OEM 入口继续
   保持 `verification_pending`。

### 本地验证结果

- onboarding、agreement、startup gate 与 Settings 六个 JVM 套件 `51/51` 通过，失败、错误和跳过均为 0
- 首启 ARCH037 正反向 `7/7`、相关 M-04B/M-05A1/M-05A2/M-05A3 架构夹具及完整
  `check_architecture_boundaries.py` 通过，完整结果为 `PASS (phase=m03)`
- formal readiness、工作树 18 份 Markdown 链接和 `git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为 `BUILD SUCCESSFUL in 1m 47s`；
  `235` 个任务中 `23` 个执行、`212` 个为最新状态，唯一 launcher、脚本代理 runtime 和播放器
  runtime packaging 门禁通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `503676817` bytes，SHA-256
  `660135CD28282B5C6317862C58BD6EF7371DDB17ABF0D079A48E546F605755C7`
- APK 为 `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`，Android Debug V2 单 signer、16 KiB ZIP
  对齐和唯一 `MainActivity` launcher 通过；`53` 个 `.so` 仅含 `arm64-v8a` 且 basename 零重复，
  连同 shell launcher 共 `54/54` 个 ELF64/AArch64，`161` 个 `PT_LOAD` 为
  `0x4000 x 159` 与 `0x10000 x 2`
- 未安装 APK、未运行 ADB、模拟器或真机；设备视觉、触控、权限和系统设置往返继续保持
  `verification_pending`
