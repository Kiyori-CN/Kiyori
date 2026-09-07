# 首次安装启动体验重做

> 状态：`IMPLEMENTED / AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。本专项面向未发布版本的首次安装流程；真实设备视觉与触摸验收仍需安装候选 APK 后完成。

## 目标

## 2026-09-08 协议与权限体验深化

状态：`in_progress`。本轮以 `main / 79788d6fc` 为基线，六个既有上传日志保持原样且不进入提交。
用户授权完成本轮修改、验证、提交与推送；设备安装和清数据另行确认。

- 目标：介绍可跳过，协议重点易理解且全文完整，权限按使用场景分组、默认不选，设置入口同步呈现。
- 范围：首启 Compose、共享权限 presentation/快照、协议正文与阅读器、协议版本、专项测试和用户指南。
- 非目标：不新增 Android 权限，不改稳定 ID、持久化键、设备执行器和主应用导航，不发布附件。
- 阶段：核对 Manifest 与能力调用链；实现共享分组和法律阅读层级；回归同意/授权/返回状态；构建并审计精确候选树后推送。
- 设计：保留已有主题与图形；介绍可跳到协议但不能绕过同意；权限常用组展开、其他组可展开查看；取消全局授权激励，保留逐项选择与清空。
- 风险：协议更新触发重确认，系统限制无法可靠探测时不能伪装未授权，折叠组不能藏匿已选项目，长文/大字体不挤压确认动作。
- 回滚：通过独立提交恢复本轮明确文件；不恢复用户日志或改变设备数据。
- 验收：SDK 权限映射、目录无遗漏/重复、版本与同意门、首启及设置架构检查、文档检查、串行 Debug APK；现场视觉与系统授权往返独立保留 `verification_pending`。

让用户第一次打开 Kiyori 时，在一条清晰、短而完整的路径中理解产品价值、建立信任、选择需要的设备能力并进入可用首页。首启页面应具备现代产品首页的节奏：明确的主标题、可扫描的价值点、稳定的视觉锚点和唯一的下一步动作。

## 范围

- `KiyoriOnboardingScreen` 的欢迎页、产品能力介绍页、协议页和权限页的布局、视觉层级、按钮状态、文案与可访问性。
- 首启页面的进度反馈、返回/滑动边界、授权处理中状态、完成后进入首页的状态衔接。
- 首启文案的中文资源，保持法律正文、权限 ID、权限组和 Agreement/Onboarding 持久化键兼容。
- 对应的首启契约测试、表面合同测试和文档记录。

## 非目标

- 不改变 Android application ID、`operit://`、AIDL、ToolPkg/MCP、备份格式或权限枚举。
- 不新增权限、不自动请求未选择的权限、不改变授权队列顺序、不增加第二套首启状态 owner。
- 不改造主应用首页、设置页或其他运行中页面；这些页面只作为首启完成后的落点。
- 不加入远程图片、联网内容或需要额外依赖的设计资产。

## 设计决策

1. 首屏使用“品牌 + 一句话价值主张 + 四个能力入口 + 单一主 CTA”的结构；产品视觉使用本地 Compose 图形，保证冷启动可用、首帧稳定和无网络依赖。
2. 四个介绍页按“内容工作台 / AI 协作 / 本地工作区”组织，每页保持同一标题、摘要、主视觉、四项能力卡和 CTA 节奏；大屏使用双栏，手机使用可滚动单栏。
3. 顶部进度改为更紧凑的阶段指示器，返回按钮保留 48dp 触摸目标；协议页和权限页使用明确的信任/决策文案，避免把高影响能力包装成必选项。
4. 协议必须先阅读并勾选后才能继续；权限默认不替用户选择，已授权、待处理和按需确认三种状态保持可见；授权处理中锁定所有导航输入。
5. 主按钮采用统一 56dp 高度、图标与文案组合、明确的禁用/加载状态；文本按钮只承担次要动作（清空选择、退出、打开正文）。
6. 继续沿用 safe drawing inset、唯一 pager 状态、`KiyoriOnboardingPreferences` 和现有法律文档/权限元数据 owner。

## 预计修改

- `app/src/main/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingScreen.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/KiyoriAgreementScreen.kt`（仅在首启协议呈现确需共享修正时修改）
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/kiyori/integration/operit/onboarding/KiyoriOnboardingContractTest.kt`
- `app/src/test/java/com/kiyori/integration/operit/onboarding/KiyoriStartupExperienceSurfaceTest.kt`

## 验收

- 首次安装从欢迎页开始，能够按下一步、返回和系统 Back 完成全流程；协议未同意时不能越过协议页，授权处理中不能重复点击或滑动。
- 欢迎页和三张能力页在手机窄屏、折叠/横屏和至少 700dp 宽屏均无裁切、重叠或 CTA 被遮挡；长文案可滚动且按钮固定可见。
- 协议正文入口、版本号、勾选状态与现有 Agreement owner 一致；权限页 23 项清单、分组、状态和授权行为与 Settings 共用同一目录。
- 定向 JVM 测试、`git diff --check`、`python -B ci/script/check_formal_readiness.py --repository . --require-main`、串行 `./gradlew :app:assembleDebug --no-daemon --console=plain` 通过，并核验 Debug APK。
- 真机视觉、触摸、系统授权页返回和不同屏幕尺寸仍单独标记为 `verification_pending`，不得用本地构建替代。

## 回滚点

实现前基线为 `main` / `59fc131bc`，工作树干净。回滚只允许恢复本专项明确修改的源码、资源、测试和文档文件，不触碰其他并行专项或生成产物。

## 本轮证据（2026-09-06）

- `:app:testDebugUnitTest --tests "com.kiyori.integration.operit.onboarding.KiyoriOnboardingContractTest" --tests "com.kiyori.integration.operit.onboarding.KiyoriStartupExperienceSurfaceTest"`：`BUILD SUCCESSFUL`。
- `python -B ci/script/check_formal_readiness.py --repository . --require-main`：`PASS`。
- `git diff --check`：通过。
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`；APK 为 `app/build/outputs/apk/debug/app-debug.apk`，大小 `512626322` bytes，SHA-256 `3B269362A5C8365A661021014DED61A899D87B35B71048E2EE05A7C20539B729`。
- 未安装 APK，未操作真机；系统授权返回、不同屏幕尺寸、首帧视觉和触摸反馈保持 `verification_pending`。
