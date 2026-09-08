# 首次安装启动体验重做

> 状态：`IMPLEMENTED / AUTOMATED VALIDATION COMPLETE / DEBUG APK VERIFIED / DEVICE VERIFICATION PENDING`。本专项面向未发布版本的首次安装流程；真实设备视觉与触摸验收仍需安装候选 APK 后完成。

## 2026-09-08 引导页重设计 v1.0 高保真复刻

状态：`verification_pending`。六页 Compose 呈现、专项自动验证和 Debug APK 已完成；本轮已授权审计后提交并推送 `origin/main`，真实设备视觉、手势与系统权限往返仍待验收。实现基线为 `main / 757f51e19baec48876ede35b4a70ec2f471b2baa`，开始时工作区干净。

- 设计基线：完整核查 `D:\03_Default\下载\Kiyori引导页重设计-v1.0\kiyori` 当前 22 个文件，包括 9 个文档/规范/脚本文件和 13 张 PNG；12 张单页图均为 `720 × 1600`，对应 `360 × 800dp` 的二倍图。页面内容以 `spec/onboarding_spec.json` 和 `spec/strings_onboarding.xml` 为准，HTML 与明暗主题 PNG 用于视觉对照。
- 视觉实现：新增引导专用明暗色板及尺寸 token；六页统一 48dp 顶栏、4dp 六段进度、20dp 页面边距和 50dp 主按钮。P1-P4 使用 104dp 导航地图、设计稿的 16 张能力卡、四色图标、五个状态徽标、补充说明与渐隐底栏；1.3 倍字体起改为单列，1.6 倍起卡片保留第一行说明。
- 协议页：P5 使用三项风险说明、两张内置文档卡、会话内已读状态、协议版本、默认未勾选复选框、禁用主按钮、重看已同意状态和退出确认；协议正文与版本继续由 `KiyoriAgreementDocumentScreen` 和 `AgreementPreferences.CURRENT_AGREEMENT_VERSION` 持有。
- 权限页：P6 优先展示通知、视频与音频、麦克风及两项“使用时确认”，支持推荐两项和全部暂不开启；完整 23 项真实目录仍按用途折叠展示，逐项选择、系统快照、授权代际与手动继续队列继续复用现有 owner。首次默认推荐两项，用户主动清空后通过已保存空选择保持为空。
- 事实差异：没有照抄设计包中与当前 Manifest 冲突的 `READ_MEDIA_IMAGES`，也没有展示“Kiyori 不申请定位、短信与通话”的失实声明；没有新增权限、第二个运行时 launcher、第二套协议或权限状态。
- 自动验证：`compileDebugKotlin` 通过；引导专项 `testDebugUnitTest` 41 项全部通过；首启架构门禁的 2 个定向正反例通过；全仓 `check_architecture_boundaries.py --repository . --require-main` 为 `PASS (phase=m03)`；`check_formal_readiness.py --repository . --require-main` 为 `PASS`；`git diff --check` 通过。权限摘要架构 token 从旧资源同步为设计稿的 `onb_p6_summary`，其余唯一 owner 与授权边界检查未放宽。
- Debug APK：最终串行 `:app:assembleDebug --no-daemon --console=plain` 在 47 秒内通过，238 个任务中 24 个执行、214 个复用；产物 `app/build/outputs/apk/debug/app-debug.apk`，`483904551` bytes，时间 `2026-09-08 18:17:49 +08:00`，SHA-256 `644350A8F0FB65F2BF51CE2EB6EE1A867000FF763189AA2A07E5DA37D5798492`。身份为 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，V2 单签名与 16 KiB ZIP 对齐验证通过。
- 设备边界：本轮没有安装 APK、清数据或操作设备。全新首启与设置重看下的六页像素级视觉、横纵向和快速反向手势、字体 100%/130%/160%/200%、显示尺寸、TalkBack、明暗主题即时切换、协议弹窗及系统授权返回保持 `verification_pending`。

## 2026-09-08 Claude 方案取舍与引导重设计

状态：`verification_pending`。实现、自动回归与 Debug APK 已完成，设备触摸与视觉待验收。基线 `main / 88076f0c71aca770420e8a6cec32a066f4b3258d`，开始时工作区干净；本轮不提交推送，不操作设备。

- 目标：修复滑动及重看异常跳第五页，完善六页视觉、文案、授权和返回边界；重看入口成为“更多功能”底部独立“使用引导”分组。
- 设计取舍：采用紧凑导航示意、能力与真实入口说明、配置提示、窄屏/大字体单列和完整文字。保留主题 token、唯一 Pager、既有协议版本 owner、完整共享权限目录和手动继续授权；不照搬硬编码色板、第二偏好存储、隐藏大字体说明、未实现的音乐/阅读器宣传及不申请定位/短信的错误声明。
- 根因调查：旧触摸资格在 Final pass 结束时重置，延迟点击可能在取消后重新放行；改为 Initial pass 对无效触摸的抬起事件取消，保留原生按钮的键盘/无障碍语义。重看会话继续明确从第一页创建；页面动作需同时校验当前页及滚动状态。
- 阶段：源码与设计核对 → 触摸/导航和设置分组 → 介绍页与协议/权限布局 → 定向回归 → 串行 Debug APK → 差异与文档核验。
- 影响文件：onboarding Screen/Contract、共享协议摘要、更多功能规格、中文资源、对应 JVM 测试与首启架构检查、首启契约和用户指南。
- 非目标：不改 Manifest 权限、协议正文/版本、持久化键、应用身份、子模块、其他功能实现；不引入依赖。
- 风险与回滚：Compose 原生事件分发、窗口重建、大字体与系统授权返回需设备验收；本轮补丁可独立逆向恢复，无数据迁移。
- 验收：取消后延迟点击/多指/折返、快速导航/重看起点、权限和协议约束、分组顺序、完整文案与布局分支、文档及 Debug APK；现场结果未取得前保留 `verification_pending`。

### 当前交付与证据

- 已落地：底部独立“使用引导”分组；取消触摸的 up 在 Initial pass 消费，结束后不复活取消资格；协议/权限页操作使用相同触摸防护，运行时权限弹窗未返回时锁定翻页。重看继续隔离旧页码与首启偏好。
- 前四页内容依次为“首页搜索 / AI 助手 / 扩展 / 界面定制”、“网页浏览器 / 文件下载器 / 视频播放器 / 广告拦截器”、“模型配置 / 语音服务 / 记忆管理 / 工具箱”、“文件管理器 / 终端 / 工作流 / 数据备份”。说明按源码入口核对；例如“模型与 API”、语音分组、“记忆库”、工具箱内“命令终端”和浏览器设置内“内容过滤”。
- 删除旧装饰插画和字号二分压缩；引导改用紧凑三屏位置示意、自适应卡片、配置提示。大字体保留全文；顶栏是唯一页码播报所有者，预组合页不单独声明 pane。权限摘要强调所选数量，条目标题/状态分行，取消列表高度动画；正文和操作栏按实际高度分配空间。
- 定向 JVM：`:app:testDebugUnitTest --tests 'com.kiyori.integration.operit.onboarding.*' --tests '*KiyoriMainStartupGateCoordinatorTest' --tests '*KiyoriSettingsPagesTest' --tests '*KiyoriAgreement*Test' --no-daemon --console=plain` 通过，69 项、0 失败、0 错误、0 跳过。后续仅修正模型入口字符串和无行为变化的格式，最终 16 项说明另经 XML/双句完整性检查通过，最终 APK 覆盖全部修改。
- 首启架构的 11 项正反例及实际 `check_kiyori_first_run_flow` 通过。旧三数字摘要的源码断言改为所选数量摘要断言，新增缺失摘要必须失败的反例；保留唯一权限 owner、逐项选择及集中 launcher 检查。文档检查 497 文件、0 问题；`git diff --check` 通过。
- 最终串行 `:app:assembleDebug --no-daemon --console=plain` 成功，35 秒，238 任务（28 executed、210 up-to-date）；唯一 launcher、脚本代理与播放器打包检查通过。产物 `app/build/outputs/apk/debug/app-debug.apk`，`483844435` bytes，`2026-09-08 13:47:16 +08:00`；SHA-256 `8B81196371CFAD98CB1FE7F66F655C52E3FAF542FF0FB0D06563B4A892BE7681`。身份 `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / arm64-v8a`，V2 单签名和 16 KiB ZIP 对齐通过。
- 当前 15 个变更文件全部留在本地，暂存区空、terminal 无修改；没有提交、推送或设备操作。本轮未运行 Lint、Release、全仓架构及新鲜克隆检查；后者需要候选提交，本轮没有创建。
- 剩余验收：安装上述 APK 后反复重看、按钮区域纵向/横向/斜向/折返/多指拖动、显式跳过、快速连续操作、协议正文/权限返回、旋转/短屏、字体 100%/130%/160%/200% 与 TalkBack。JVM 和源码回归仅证明已修复的取消状态与接线，不能证明现场异常已完全消失；无新增本地交付阻塞。

## 2026-09-08 滚动稳定性、重看会话与 16 项内容精修

状态：`verification_pending`。实现、定向自动测试与 Debug APK 已完成；真实设备交互待验收。
基线 `main / 54d890d25e552b79ba051df6954d96536adc75c0`，开始时工作区干净。
本轮授权研究、实现、必要验证、Debug APK、提交并推送到 `origin/main`。

### 问题与方案

1. 导航按钮的禁用配色直接依赖 `isScrollInProgress`，拖动会反复切换外观。保留操作时的滚动互斥，视觉 enabled 只表达实际业务可用性；固定顶栏和底栏布局。
2. 重看虽指定 `initialPage`，`rememberPagerState` 的恢复值优先于初值。重看使用仅属于本次组合的 Pager，首次安装仍保留原生保存恢复；退出重进以及窗口重建均从第一页开始，不写首启偏好。
3. 现有导航只校验目标可达，允许预组合页回调跨页。增加发起页与相邻步骤校验，只有显式跳过动作可向前跨页到协议；按钮拖动超过系统阈值、多指、取消后不触发点击，涵盖底栏和顶部。
4. 四页统一眉题，第一页新增 `Kiyori`。卡片固定 2×2，图标、标题与说明分层；说明用明确换行的两句短文，每句不超过 7 个汉字宽度，窄屏按实际文字测量收敛字号，不省略内容。保留主题字体、色彩与正文滚动。
5. 移除超出实现的“音乐与小说”“小程序安装管理”宣传。16 项使用真实入口和能力名称；卡片仅说明，不伪装可点击入口。

### 文案设计

表内斜线代表固定换行，标题与图标语义对应。

| 页面 | 项目一 | 项目二 | 项目三 | 项目四 |
| --- | --- | --- | --- | --- |
| Kiyori | 我的账号：连接服务账号 / 管理登录状态 | AI助手：从问题出发 / 与助手协作 | 扩展：按需添加能力 / 组合任务工具 | 网络代理：配置连接规则 / 管理应用网络 |
| 内容工作台 | 网页浏览器：搜索与多窗浏览 / 收藏精彩网页 | 文件下载器：查看下载进度 / 管理下载任务 | 视频播放器：播放本地视频 / 也能在线播放 | 广告拦截器：订阅过滤规则 / 减少网页干扰 |
| 你的 AI 助手 | 模型配置：选择模型服务 / 按用途配模型 | 语音服务：用语音来输入 / 听回答被朗读 | 记忆管理：保存参考资料 / 检索关联内容 | 工具箱：查看可用工具 / 按授权执行 |
| 本地工作区 | 文件管理器：浏览本地目录 / 查找所需文件 | 终端：准备运行环境 / 执行命令脚本 | 工作流：编排任务步骤 / 按需重复执行 | 日志记录器：查看运行记录 / 定位问题线索 |

### 实施与验收

- 阶段：调用链调查 → 方案与文案 → 导航/触摸/布局实现 → 定向 JVM 和源码/文档检查 → 串行 Debug 构建 → 精确差异审计、提交推送及 ref 核对。
- 文件：onboarding Screen/Contract/测试、中文资源、用户指南及应用壳首启契约；复用现有状态与视觉组件，不新增依赖。
- 非目标：不改变协议正文/版本、权限事实/执行器、持久化键、应用身份、其他页面功能，不安装或操作设备。
- 回滚：单独提交可逆；无需数据迁移，保留已有首启偏好。
- 自动验收：逐页导航、跨页只能显式 skip、非当前页面/繁忙回调拒绝、重看与首启状态分离、16 项双行完整文案、文档检查、实际 APK 元数据与 Git 三方 ref。
- 设备验收：反复重看、纵向/横向/斜向/折返/多指/按钮内拖动、快速连续点击、协议正文与授权往返、旋转、窄屏和大字体；未实测前保留 `verification_pending`，不将静态/JVM 结果视为触摸复现证据。

### 本轮证据与补充发现（2026-09-08）

- 实现已落地；重看协议正文也改为临时会话状态，防止窗口重建后旧正文遮住第一页。按下时正在翻页的触摸不能在抬手时转化为跳过，预组合权限页不能提前授权或完成引导。
- 最终源码定向 JVM：`:app:testDebugUnitTest --tests 'com.kiyori.integration.operit.onboarding.*' --tests '*KiyoriMainStartupGateCoordinatorTest' --tests '*KiyoriSettingsPagesTest' --no-daemon --console=plain` 通过，65 项、0 失败、0 错误、0 跳过，`BUILD SUCCESSFUL in 4m 3s`。
- 首启架构检查的 10 个正反例通过。全仓检查首次暴露四处要求旧宣传文案的过时断言，已改为实际能力名称并新增缺失文案反例，未关闭或降低检查。
- `check_formal_readiness.py --repository . --require-main` 通过。文档标准脚本补齐基线 Dependabot 专项遗漏的两条目录导航；目录之外未改动该安全专项。
- 触摸判定的 JVM 测试只验证状态机，源码断言只验证接线；这两者不替代 Android 真实事件分发、字体渲染与屏幕录制。未安装 APK、未操作设备，未运行 Lint 或 Release。
- 串行 `:app:assembleDebug --no-daemon --console=plain` 通过，`BUILD SUCCESSFUL in 2m 41s`，238 任务（23 executed、215 up-to-date）；单 launcher、脚本代理和播放器打包检查通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`483836319` bytes，生成时间 `2026-09-08 06:00:29 +08:00`；SHA-256 为 `82C4D9C2AB0CC0AADA11F3E7334BA90238D124E9FFECB0D74B953BA0A0FCFF91`。`com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，仅 `arm64-v8a`；V2 单 signer、16 KiB ZIP 对齐通过。
- 最终全仓 `check_architecture_boundaries.py --repository .`：`PASS (phase=m03)`；文档检查 497 文件、0 问题；`git diff --cached --check` 通过。精确提交清单 14 文件，未含产物、凭据或子模块修改。

## 2026-09-08 手势、重看导航与六页布局完善

状态：`verification_pending`。实现、定向自动验证与 Debug APK 核验完成；设备体验待验证。
基线 `main / 99a02bb60cf28c2b030c3073f597356358fdee5b`。
用户授权实现、代码验证、Debug 构建和最终提交推送；明确不进行设备安装或操作。

- 范围：六页引导、设置重看入口、共享协议摘要布局、对应行为与架构检查、用户指南。
- 根因：重看包装在 Final pass 无条件消费事件；顶栏随按钮消失改变测量；未同意协议使用第二手势检测器；宽屏内容没有纵向滚动；重看第一页 Back 会结束 Activity；授权队列返回后自动打开下一项且没有取消入口。
- 方案：独立全屏模态窗口隔离设置；唯一 Pager 通过页数约束协议门；固定顶栏与稳定进度；窄屏/大字体卡片自适应、宽屏内容可滚动；重看退出保持设置来源；授权逐步确认且可停止。
- 阶段：完成源码调用链调查 → 实现与文案完善 → 定向 JVM、架构与文档检查 → 串行 Debug APK → 审计提交推送。
- 非目标：不改权限 ID、协议正文和版本、应用身份、模型配置、其他领域运行时；不发布 APK 附件。
- 风险与回滚：Compose Window/inset/Back 与保存状态协同需现场验收；回滚仅针对本轮独立提交。六份上传日志在核验后移到忽略的 `work/` 留存，不删除证据。
- 验收：覆盖协议边界、连续导航、重看起点与退出、授权取消/继续、静态布局和触摸隔离合同；核验 APK 和三个 Git ref。设备触摸、动画与视觉结果保留 `verification_pending`。

架构快照同步说明：本轮已逐行审阅 App Shell 差异，仅删除重看路由的 Final pass
拦截包装、接入 `onExitReview` 并移除失效 import。归一化 SHA-256 从
`2D785F8C7C2C54219BCE3FB1A53E5A0AEA952C2E7E3B28162A997072D5D6DBE2`
更新为 `1B6017566A231C639837A794A6FA30D24EEA415337AEE07035F1512EBFC433B9`；
import 快照补记之前重看入口已经使用的 `KiyoriOnboardingScreen`。模态隔离、Back、
起点、步骤和选择锁定另由专项回归验证，没有取消快照或放宽包边界。

### 本轮验证证据（2026-09-08）

- 定向 `:app:testDebugUnitTest` 覆盖 `com.kiyori.integration.operit.onboarding.*`、
  `KiyoriMainStartupGateCoordinatorTest`、协议测试与 `KiyoriSettingsPagesTest`：
  59 项、0 失败、0 错误，`BUILD SUCCESSFUL in 1m 2s`。修正了既有设置测试中遗漏的
  `OPEN_ONBOARDING_REVIEW` 分支及入口期望。恢复前中断的编译日志不作为通过证据。
- 全仓 `check_architecture_boundaries.py`：`PASS`；首启相关架构检查的 9 个正反例通过。
  `check_formal_readiness.py --repository . --require-main`：`PASS`；
  `check_documentation.py --repository .`：496 文件、0 问题；`git diff --check` 通过。
- 最后补充小标题 `heightIn(min = 22.dp)` 以避免大字体固定高度裁切，并修正局部缩进；
  以下 Debug 构建验证最终源码。共享协议勾选区随正文滚动，主按钮允许文字换行；
  重看说明明确已有状态不会重置，但用户仍能主动处理权限。
- 串行 `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 1m 58s`，
  238 任务（27 executed、211 up-to-date），唯一 launcher、脚本代理与播放器打包检查通过。
- APK：`app/build/outputs/apk/debug/app-debug.apk`；生成时间 `2026-09-08 04:43:15 +08:00`，
  `483836055` bytes，SHA-256
  `3E340A6E1756DBABF6E4DE20ED348425ACD5D49FA8BB5E46D7AF3046B8A09313`。
  `com.kiyori / 45 / 0.1.0 / minSdk 26 / targetSdk 34 / compileSdk 37`，仅 `arm64-v8a`，
  V2 单 signer 与 16 KiB ZIP 对齐通过。
- 六份旧上传日志已可恢复地移入忽略目录 `work/onboarding/upload-logs-20260908/`；
  两份错误内容为找不到 `0.1.0`，属于旧上传记录。本轮不上传或发布 APK 附件。
- 未安装 APK、未操作设备，也未运行 Lint/Release。现场需覆盖全新首启、重复重看均从第一页开始、
  设置页不响应穿透点击、六页顶栏高度、横纵向及斜向快速滑动、按钮区域拖动、连续点击、
  协议正文往返和系统 Back、授权拒绝/取消/继续/停止，以及窄屏/横屏/大字体。
  JVM 和源码检查不能代替上述触摸、转场与视觉验收。

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
