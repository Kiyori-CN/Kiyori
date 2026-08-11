# 实现与验证

## 2026-08-11-r9 首启精确闹钟权限链收口

1. [DONE] 删除首启 `EXACT_ALARM` 权限枚举、真实状态读取、系统设置 Intent 和权限元数据
2. [DONE] 删除 Manifest 中无实际消费者的 `SCHEDULE_EXACT_ALARM` 声明
3. [DONE] 将首启偏好命名空间升级为 `r9`，避免旧 r8 队列中的 `EXACT_ALARM` 名称进入新状态机
4. [DONE] 更新中文、英文及现有本地化首启资源，移除精确闹钟权限文案和不再成立的法律能力描述
5. [DONE] 更新首启架构门禁，要求剩余特殊访问覆盖并禁止精确闹钟合同重新出现
6. [DONE] 保留 WorkManager 工作流调度和普通 `ACTION_SET_ALARM` 闹钟能力
7. [DONE] 完成定向 JVM、首启架构门禁、正式开发准备和差异审计
8. [DONE] 完成 Debug APK 构建并核验产物
9. [PENDING] 在目标设备复测第六页授权流程、系统设置往返和首启状态重建

本轮属于未发布 Kiyori 的开发期合同收口。`2026-08-11-r9` 使用新的首启偏好命名空间，
不读取旧 r8 的选择集合；协议正文版本仍保持 `2026-08-09-r3`。

本轮最终本地验证结果：

- `KiyoriOnboardingContractTest` `12/12` 与 `KiyoriOnboardingPermissionsTest` `5/5`
  通过，失败、错误和跳过均为 `0`
- 首启架构正反向定向测试 `3/3` 通过，正式开发准备检查通过
- `git diff --check` 通过，仅输出仓库已有的 AndroidManifest CRLF/LF 转换提示
- 同步 Manifest 语义快照后，完整 architecture `phase=m03` 返回 `errors: []`
- 七个现有本地化 `strings.xml` 中的首启精确闹钟权限资源和法律能力声明反向搜索为 `0`
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL`；`238` 个任务中 `33` 个执行、`205` 个为最新状态，唯一 Debug launcher
  与 Player runtime packaging 检查通过
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `475435325` bytes，
  SHA-256 `253A6A426A342014BD55432EB9DF3531D6CFD804487C7D6970B01598592FF291`
- APK 为 `com.kiyori`、`0.1.0 (45)`、min/target/compile SDK `26 / 34 / 37`，仅
  `arm64-v8a`；51 个 native `.so` basename 唯一，Android Debug V2 单 signer 与
  `zipalign -c -P 16 -v 4` 验证通过
- 未安装 APK、未运行 ADB 或操作设备；目标设备验收保持 `verification_pending`

## 2026-08-10-r8 首启收口与进入正式使用

1. [DONE] 将第一页“欢迎使用 Kiyori”在共享标题槽内居中，后三张带眉题介绍页维持左对齐
2. [DONE] 保持六页顺序、唯一 `HorizontalPager`、协议门禁、权限选择队列和授权结束直达应用
3. [DONE] 首次进入正式内容前读取持久化当前对话事实；缺失或失效时创建并选中真实空白对话
4. [DONE] 保持“每次启动新建空白聊天”原有偏好语义，不把它改成全局默认开启
5. [DONE] 删除未被调用且判断语义相反的旧 ViewModel/Delegate 新对话检查函数
6. [DONE] 增加第一页标题对齐、首个对话决策以及六页状态机相关 JVM 合同测试
7. [DONE] 首启专属架构检查、106 项架构单测、正式准备、差异检查、定向 JVM、Debug APK
   构建与静态产物核验通过；完整门禁仍只剩工作树既有的 ARCH009/010、AppDatabase 和 M-05E
   漂移
8. [PENDING] 在目标设备验收六页视觉、手势、系统授权往返和首次 AI 首页会话可见性

由于 Kiyori 尚未发布，首启偏好版本升级为 `2026-08-10-r8`，让开发设备重新执行完整六页流程。
协议正文没有变化，协议版本继续保持 `2026-08-09-r3`。模型/API 仅影响发送能力，不再成为空白
会话存在的前置条件。

本轮本地验证结果：

- 首启、协议、启动门禁、默认新对话、设置首页和设计主题共 `50/50` 项 JVM 测试通过
- 架构单测 `106/106` 通过，首启专属架构检查通过，正式开发准备与 `git diff --check` 通过
- Debug APK 为 `475435894` bytes，SHA-256
  `ABA70E52CA7099C2C9AE644EFF5295D8670F64AF75625EE2EB97B1CC35CF762C`
- 包名、版本、target SDK、唯一 arm64 ABI、51 个原生库、内置无障碍支持资产、Debug v2 签名和
  16 KB ZIP 对齐均通过静态审计

## 2026-08-10-r7 前四页共享基线与16能力图标

1. [DONE] 将前四页统一为同一主视觉、标题、介绍和 2×2 能力卡版式基线
2. [DONE] 让主视觉与能力卡共同消费唯一 `OnboardingFeatureCard` 列表
3. [DONE] 删除能力卡正文两行省略与固定 86dp 高度，保证中文文案完整显示
4. [DONE] 将四页图标调整为 16 个互不重复的 Kiyori 能力图标
5. [DONE] 删除第一页“AI 浏览器 · 内容工作台”眉题，并为介绍正文加入两个中文字符首行缩进
6. [DONE] 完成定向检查、正式准备、差异检查、Debug APK 构建和产物核验
7. [PENDING] 进行真机视觉、字体缩放和四页卡片坐标验收

本轮只修改中文默认首启内容和 Compose 视觉实现；第 5 页协议、第 6 页权限授权及其状态机、
左右滑动和授权完成直达应用行为不属于本轮改动目标。由于 Kiyori 尚未发布，首启偏好版本升级为
`2026-08-10-r7`，便于开发阶段重新观察完整流程。

本地验证结果：

- 首启相关 JVM 测试通过：`KiyoriOnboardingContractTest`、`KiyoriOnboardingPermissionsTest`、
  `KiyoriAgreementReadinessTest`、`KiyoriMainStartupGateCoordinatorTest`
- 架构边界 Python 单测 `106/106` 通过，正式开发准备门禁通过，`git diff --check` 无错误
- 源码合同自动核对提取到 `16` 个能力卡图标且 `16` 个唯一；主视觉区域硬编码图标为 `0`
- Debug APK 构建和产物审计通过；完整架构门禁仍保留本轮开始前已存在的 ARCH009/010、
  AppDatabase 哈希、M-05A2 消费者和 M-05E 路径消费者漂移

## 2026-08-10-r6 无障碍支持图标一致性与首页 UI 精修

1. [DONE] 将支持应用启动器与首页顶部图标统一为主应用
   `app/src/main/res/drawable-nodpi/ic_kiyori_launcher_foreground.png` 的逐字节副本
2. [DONE] 移除支持应用首页仍引用的旧 `drawable/ic_launcher_foreground.xml` Operit 图标资源
3. [DONE] 缩小首页图标视觉占位至 64dp、提升标题层级至 24sp，并将服务状态调整为紧凑的中性状态提示
4. [DONE] 重写首页说明与系统无障碍服务描述，明确界面理解、点击、输入、滑动、返回和主动请求边界
5. [DONE] 将支持应用升级为 `versionCode 6 / versionName 1.7`，保留 provider 包名、服务类和 AIDL action
6. [DONE] 将 ARCH045 升级为主应用图标与支持 APK 两处前景图的逐字节一致性门禁，并检查旧 XML 不得存在
7. [DONE] 完成支持 APK 重建、16 KB ZIP 对齐、Debug v2/v3 签名与 APK 内部文案/资源审计

本轮仍只修改中文默认支持页文案。主应用 onboarding 的协议版本与六页流程合同不变；支持应用的
IPC 兼容标识不变。真机启动器图标、首页视觉与无障碍设置往返继续保持 `verification_pending`。

## 2026-08-09-r5 产品信息密度与支持应用品牌化

1. [DONE] 对照最新六张截图确认前四页的主要问题是产品结构缺失，而不是单纯的外边距过大
2. [DONE] 用“主题导语 + 2×2 能力卡片”替换旧标签流，并进一步压缩手机端插画高度
3. [DONE] 重写前四页中文标题、正文和卡片说明，覆盖 Kiyori 的浏览、内容、AI、连接与本地工作区
4. [DONE] 保持唯一 `HorizontalPager`、协议门禁、权限选择、单主按钮和授权完成直达应用合同
5. [DONE] 将内置支持应用升级为 `1.6`，应用名、服务名、描述、主题名和图标全部切换为 Kiyori
6. [DONE] 保留 provider 包名、服务类和 AIDL action，避免破坏主应用与支持应用的 IPC
7. [DONE] 增加支持 APK 的版本、SHA-256、Kiyori 文案、图标和旧品牌反向架构门禁
8. [DONE] 将已安装旧版本判定为需要配置并重新进入安装流程，避免继续直接打开旧品牌服务
9. [DONE] 完成定向测试、完整架构门禁、Debug APK 构建与最终静态审计

本轮仍只修改中文默认首启资源。`KiyoriOnboardingPreferences` 升级为
`2026-08-09-r5`，两份法律文档正文未改变，因此协议版本继续保持 `2026-08-09-r3`。

继承的 `1.5` 支持 APK 使用旧签名且仓库不包含对应私钥；已安装旧版的开发设备需要先手动卸载
`com.ai.assistance.operit.provider` 一次，才能安装当前 Kiyori 品牌版本。全新安装不受影响。

## 2026-08-09-r4 截图精修与左右滑动

1. [DONE] 对照六张真机截图收紧首屏与介绍页顶部留白，缩小手机端插图、标题和正文密度
2. [DONE] 删除第 2 至第 4 页插图下方的重复功能图标，修复中文长标题生硬断行
3. [DONE] 将进度替换为无端点装饰的紧凑自绘条，步骤文案固定在进度条下方并保持单行
4. [DONE] 用唯一 `HorizontalPager` 统一主按钮、顶部返回、左右滑动、进度和步骤持久化
5. [DONE] 限制单次手势最多移动一页；协议未同意时阻止前进，授权处理中锁定页面切换
6. [DONE] 轻量化协议入口、复选区域和主按钮；压缩权限卡片并把状态移入标题行
7. [DONE] 重写首屏与三张介绍页的中文标题和说明，保持 AI 浏览器定位并覆盖浏览、媒体、AI、
   文件与扩展能力
8. [DONE] 更新架构门禁、定向测试、正式准备、差异检查与 Debug APK 静态审计

本轮只修改中文默认首启资源。`KiyoriOnboardingPreferences` 升级为
`2026-08-09-r4`，便于未发布版本重新执行完整流程；两份法律文档正文未改变，因此协议版本继续
保持 `2026-08-09-r3`。

## 2026-08-09-r3 六页中文重排

1. [DONE] 状态机改为产品总览、浏览器与媒体、AI 协作、文件与扩展、协议、权限
2. [DONE] 重写第 1 至第 4 页中文标题、正文、能力点、插图和单一主按钮
3. [DONE] 删除协议阅读进度与独立已读状态，只保留双文档入口、复选框和同意按钮
4. [DONE] 删除权限分区、权限等级、刷新和继续下一项按钮，改为统一选择清单
5. [DONE] 实现用户所选权限队列、系统设置返回刷新和第 6 页结束后直接进入
6. [DONE] 删除 `READY` 页面、旧三页语义、旧生产资源引用和过时测试合同
7. [DONE] 同步 `CONTEXT.md`、首启架构门禁、协议历史索引和项目 TODO 文档
8. [DONE] 完成定向测试、正式准备、差异检查、Debug 构建和 APK 静态审计

本阶段只维护中文默认资源。其他语言不属于当前中文首启验收范围，等中文六页完成真机视觉
确认后再统一国际化。

## 最终实现合同

- `KiyoriOnboardingStep` 固定为
  `WELCOME → BROWSER_AND_MEDIA → AI_ASSISTANT → FILES_AND_TOOLS → AGREEMENT → PERMISSIONS`
- 顶部统一显示 Kiyori、紧凑进度条和单行“第 x 步，共 6 步”
- 第一页“欢迎使用 Kiyori”居中，带眉题的第 2 至第 4 页标题继续左对齐
- 六页使用唯一 `HorizontalPager`；第 1 至第 4 页正常双向滑动，未同意协议不能向前进入权限页，
  授权队列处理中锁定滑动和顶部返回
- 第 2 至第 6 页可通过顶部图标返回；第 5 页未同意时仍可通过向右手势返回第 4 页
- 第 1 至第 4 页底部各只有一个完整宽度主按钮，不再提供跳过或重复返回
- 第 1 页使用四张产品总览卡片；第 2 至第 4 页使用主题导语和四张能力卡片填充主要信息区，
  不再恢复稀疏标签流
- 第 5 页分别打开 Kiyori 用户协议和隐私政策；打开、滚动和返回不改变接受条件
- 用户只需勾选“我已阅读并同意当前版本的用户协议与隐私政策”，即可点击“同意并继续”
- 第 6 页按一个统一清单展示所有权限和设备能力，不显示运行时、特殊访问、高级能力等分区
- 已授权、无需授权和使用时确认的项目不可加入队列；用户选择在真实状态刷新后自动清理
- 运行时权限共享唯一 `RequestMultiplePermissions` launcher；其他所选项目按清单顺序处理
- 没有选择权限时按钮显示“进入 Kiyori”；存在选择时显示“授权并进入 Kiyori”
- 所选队列处理完毕后直接完成首次启动并进入现有 Software Home，不再显示独立完成页
- 进入正式内容前保证聊天仓库存在并选中一个真实当前对话；缺失或失效时创建空白对话，已有
  有效对话时尊重“每次启动新建空白聊天”偏好
- 无障碍安装包的应用名、服务名、描述、主题名和启动图标使用 Kiyori 品牌；内部 provider
  包名和 AIDL action 保持兼容
- 授权队列只属于当前界面会话；用户选择由 `KiyoriOnboardingPreferences` 持久化，进程重建后
  可从真实状态继续选择，不会停在不可操作的处理中状态

## 自动验证结果

- Kotlin/Android 资源编译：PASS
- onboarding 权限、状态机和协议定向 JVM：
  `KiyoriOnboardingContractTest`、`KiyoriOnboardingPermissionsTest`、
  `KiyoriAgreementReadinessTest`、`KiyoriMainStartupGateCoordinatorTest` 共 21 个测试，
  结果为 0 failure、0 error、0 skipped
- 首启架构正反向测试及完整架构单测：106/106 PASS
- 真实仓库首启 ARCH036/ARCH037/ARCH045 合同：PASS；支持 APK 的版本、哈希、Kiyori 文案、
  图标入口和旧品牌缺失均通过
- 正式开发准备：
  `python -B ci/script/check_formal_readiness.py --repository . --require-main` PASS
- `git diff --check`：PASS，仅输出仓库现有 CRLF/LF 提示
- 生产 Kotlin/XML 搜索：旧三页步骤、权限分区、权限等级、`READY`、阅读进度和重复底部返回
  均无首启生产引用
- 规定 Debug 构建：
  `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` PASS
- `verifySingleDebugLauncher`：PASS，唯一 Debug launcher 为
  `com.ai.assistance.operit.ui.main.MainActivity`
- `verifyDebugPlayerRuntimePackaging`：PASS
- 内嵌 `assets/accessibility.apk` 与源码资产逐字节一致；历史 r5 版本为 `1.6`，应用名
  `Kiyori 无障碍支持`，服务名 `Kiyori UI 自动化服务`，旧
  `Accessibility Operit Support`/`Theme.OperitAccessibilitySupport` 均不存在

完整 architecture gate 仍报告本任务开始前已经存在的以下漂移；本轮未改写这些所有者或快照：

- 市场重新提交冷却偏好已迁移，但 ARCH009/ARCH010 持久化合同尚未同步
- `AppDatabase.kt` 关键哈希与快照不一致
- M-05A2 语义色消费者新增 `PackageEnvironmentVariablesSheet`
- M-05E 路径消费者新增 `UserscriptSourceExportHelper`

## Debug APK

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 宿主文件时间：`2026-08-10 03:13:31 +08:00`
- 大小：`475437318` bytes
- SHA-256：`81661360722DE4A40DB5B69379E78D75300BBB7849D104A0EA72139410141C9A`
- package/version：`com.kiyori` / `45` / `0.1.0`
- min/target/compile SDK：`26` / `34` / `37`
- launcher：`com.ai.assistance.operit.ui.main.MainActivity`
- native code：`arm64-v8a`
- Android Debug certificate，APK Signature Scheme v2：PASS
- 16 KB ZIP alignment：PASS

## Kiyori 无障碍支持 APK

- 路径：`app/src/main/assets/accessibility.apk`
- 当前 r6 版本：`6` / `1.7`
- 大小：`2964605` bytes
- SHA-256：`9C8117F02EDB7A0297EC37D8E869F3E8C95BB97578C6CA17F3415513CC809F5E`
- package：`com.ai.assistance.operit.provider`，作为 IPC 兼容标识保留
- application label：`Kiyori 无障碍支持`
- accessibility service label：`Kiyori UI 自动化服务`
- launcher foreground：`res/drawable-nodpi/ic_kiyori_brand_foreground.png` 与
  `res/drawable-nodpi/ic_launcher_foreground.png` 均逐字节等于主应用前景图
- min/target/compile SDK：`26` / `34` / `34`
- Android Debug certificate，APK Signature Scheme v2/v3：PASS
- 16 KB ZIP alignment：PASS
- 主 Debug APK 内嵌副本 SHA-256 与源码资产一致

## 真机待验证

- 六页在深浅主题、横竖屏、大字体、折叠屏和 TalkBack 下的视觉与可操作性
- 左右滑动、单页吸附、协议前进限制、已同意后返回以及授权中手势锁定的真机触控验收
- Android 8 至 Android 16 的运行时权限组合和部分授权结果
- OEM 所有文件、悬浮窗、系统设置、使用情况、安装、电池和通知读取入口
- 无障碍提供者安装/启用、Shizuku 安装/启动/授权、Root 请求和默认助手设置
- 拒绝、永久拒绝、系统设置返回、旋转、进程回收和再次启动后的状态恢复

当前交付状态为 `verification_pending`：本地实现、编译、测试、构建和 APK 静态审计已经完成，
但未执行设备安装或真机视觉验收。

## 历史说明

`2026-08-09-r2` 曾要求两份协议分别滚动到底并确认，且仍保留旧三页介绍和独立完成页。
这些合同已被 r3 删除，不再作为当前实现或验收依据。
