---
For_Agent: 对项目大规模动工前按本规范协作
---

# TODO不误砍柴功

## 无组织无纪律是谓乌合

如果你想做一个消费一定时间、有一定规模的改动：
- 接下的issue可能被别人捷足先登，努力只能存档吃灰
- 一个承诺就此忘却，对应的issue高高挂起
- 一合并激起千重浪，矛盾...冲突！

## 优雅的协作始于你知道我知道你知道

那么，起一个TODO吧：
- 创建一个以你的修改特性命名的文件夹
- 写下index.md，在元数据中填入您的fork仓库地址
- 原本状况是什么样的？你的大致意图是什么？你期待什么样的结果？
- 再写下你的大致作用域，PR，然后干活吧

为什么不是issue: 
- 在BugReport和featureRequest里面捞协作者，是一种奢求
- Agent大概率不会看issue，但绝对不会不瞪一眼文档

## Step By Step

- 按照顺序创建一些以数字+步骤意图命名的MarkDown文档
- 添加每个功能元的旧实现情况，意图修正和期待的新实现情况
- 如果你写下细化的作用域，我们就能更快跟进
- 每一个文档分拆出一个最小可用功能单位，完成你的代码的时候在结尾加一个[DONE]，一起传上去吧
- 别人就可以让Agent根据该文档的git历史捞出对应的differ，没有压缩的话
- 完成最后一个更改时，你可以上传您的详细文档、实验记录、稳定API，并执行自动化i18n
- squansh时尽量不要把i18n和文档更改放进一个pr，这会导致git历史不那么整洁
- 用单独的pr将您的文件夹移入 `docs/.Meta/Legacy/TODO`

## 如果你想鸽了

在你的命名文件夹前加上give-up_，我们的收尾人会迅速继承您的衣钵
如果你忘了这回事，我们的赛博监工会时不时看看你的仓库有没有动静

## 实在懒得写呀...

事实上哪怕您使用Agent完成代码，必要的计划也能使得使得准确率更高，咋一看本计划会留下大量不可压缩的历史更改，但是事实上因为高度可读，不会是一个仓库的负担。并且大型修改在无充分文档和测试记录条件下快速上线，一旦出现问题，新增修改反而会产生更多不可合并的脏历史。

当然，如果您是纯粹新增或修改少量代码，那么的确可以快速上线，本文档是给那些雄心勃勃，致力于大型计划的潜在贡献者们的
以及遇事不决开个goal的Agent

## 2026-07-28 浏览器 presentation 与播放器 Surface lease 正式实现

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为唯一项目进度载体，不创建平行计划目录。Kiyori 尚未发布，完整系统 overlay 浏览器方案在新架构完成后直接删除。

实施严格分为三个串行里程碑：

1. [DONE] 浏览器 presentation
   - `MainActivity`、Kiyori App Shell 和 Browser Home 成为唯一完整浏览器 UI
   - `WindowManager` 只保留同一活动 WebView 的 1×1 后台 attach anchor、最小 indicator 和简短提示
   - indicator 通过显式 action 打开现有 Browser Home
   - 人工 UI 与 AI `browser_*` 继续共用同一 session registry、`activeSessionId` 和 WebView
2. [DONE] 播放器 Surface lease
   - `PlayerSession` 增加 role、owner token、单调 generation、transfer phase、pending target 和一次性 Activity 请求
   - floating 与 fullscreen 必须先确认旧 Surface 销毁和 native detach，再连接新 Surface
   - `MpvPlayerEngine.attachSurface()` 不再隐式 detach，转挂期间不执行媒体重载
3. [DONE] 旧路径与文档收口
   - 删除 expanded overlay 的窗口、Back、cutout、IME、恢复状态和测试
   - 更新 `CONTEXT.md`、`README.md`、正式架构文档及浏览器阶段 1、阶段 11 和总 index
   - 执行静态反向检查、定向测试、Kotlin 编译、正式开发门禁、`git diff --check` 和最终 Debug APK 审计

每个里程碑必须完成独立 Debug 构建并核验 APK 后才能进入下一阶段。真机安装、ADB、MuMu、Release、提交和推送均不属于本轮授权。

当前证据：

- 里程碑 1：浏览器定向 JVM `46/46`，Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和 APK 审计通过；APK SHA-256 `84D7067F361FB9A582DE649DC787B50849AC5D122B6813D2264CF6704F3C7739`
- 里程碑 2：播放器定向 JVM `20/20`，Kotlin 编译、formal readiness、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 通过；APK SHA-256 `08A443EAB2ED4FE1F2ACE99E5107AF791EB064EF0C82D53EAA2C413855764BAE`
- 里程碑 3：浏览器与播放器综合 JVM `66/66`，Kotlin 编译、formal readiness、静态反向检查、`git diff --check`、Debug 构建和 `verifyDebugPlayerRuntimePackaging` 通过；最终 APK SHA-256 `689A09C1DF4E23608A4E9E07A7B0E95645F9D5914E3CFB29BA0DFD1A34C2DBA0`

三个本地里程碑均已完成。vivo Android 16 同一网页、同一视频的人工/AI 共用浏览器与 floating/fullscreen 往返仍未执行，因此最终交付状态保持 `verification_pending`。

## 2026-07-28 浏览器 presentation 重复释放与跨窗口交接修复

首轮真机验收发现两条新的 presentation 生命周期故障：

- Browser Home 的显式退出已经完成 `APP_SHELL -> BACKGROUND_ANCHOR`，随后
  `DisposableEffect.onDispose` 再次释放同一 presentation；第二次后台 anchor 请求把已经挂在
  1×1 anchor 的 WebView 当成未挂载对象，触发 `Active WebView must be detached` 断言
- vivo Android 16 在同一时间窗口出现 RenderThread `fdsan` SIGABRT；现场 native 栈只能确认
  图形 Surface 分配路径崩溃，不能仅凭本地代码宣称根治

本轮修复门禁：

1. [DONE] presentation lease 与 host 两层重复 release 都必须幂等
2. [DONE] `APP_SHELL` 与 `BACKGROUND_ANCHOR` 跨 ViewRoot 转移必须先 detach、确认
   `parent == null`，跨过一个渲染帧后才创建或连接目标窗口
3. [DONE] Browser Home 活跃时不保留空的 background anchor 窗口；直接打开 Browser Home
   的入口不得先制造一次无意义的后台挂载
4. [DONE] 定向 JVM、Kotlin 编译、formal readiness、`git diff --check` 与 Debug APK 构建通过
5. [PENDING] vivo Android 16 使用同一网页复测退出、AI 往返、indicator 恢复和悬浮播放；设备
   通过前保持 `verification_pending`

本地证据：

- presentation release gate、background anchor policy 及关联浏览器状态测试合计 `51/51`，零失败、
  零错误、零跳过；测试任务包含 `:app:compileDebugKotlin`
- 静态反向检查确认旧预热入口 `0`、coordinator 提前创建 anchor `0`、严格
  `parent == null` 断言 `1`、显式跨帧 transfer plan 引用 `14`
- formal readiness 与 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务，零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 19:01:32 +08:00`，
  `455956807` 字节，SHA-256
  `8DD13C5D3AA81AEAF3102A341FAF485233FF50F1D14BC690C5A608FA3C96ABA6`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2；
  `zipalign -c -P 16 -v 4` 为 `Verification successful`

Java `IllegalStateException` 的重复释放路径已在状态机与调用层闭环。native `fdsan` tombstone
没有 Kiyori 或 mpv 业务帧，当前实现仅能确认已移除同一时机重复跨 ViewRoot 转挂及空 anchor
预热；目标设备复测前不宣称 native 崩溃已经根治。

## 2026-07-28 播放任意视频即闪退修复

`2026-07-28 19:18:21 +08:00` 的 vivo Android 16 tombstone 显示应用启动 12 秒后在首次视频
渲染期间再次触发 RenderThread `fdsan`。这次没有 Browser presentation 退出前提，必须重新检查
所有播放入口共用的 mpv 启动时序。

本轮门禁：

1. [DONE] 恢复实际打包 mpvlibAndroid 的生命周期：先 `MPVLib.create` 和
   `MPVLib.init`，只在有效 Surface 到达后执行 `attachSurface`
2. [DONE] 保留唯一 `PlayerSession` 和 Surface lease；媒体 request 可先解析，但
   `loadfile` 必须等待当前 lease 完成 native attach
3. [DONE] 增加自动检查，禁止再次在 `MpvPlayerEngine.initialize` 中 attach Surface 或设置
   `force-window=yes`
4. [DONE] 执行播放器定向测试、Kotlin 编译、formal readiness、`git diff --check` 和最终
   Debug APK 构建审计
5. [PENDING] 在原 vivo Android 16 上复测任意网络视频、浏览器悬浮播放和全屏播放；设备通过前
   保持 `verification_pending`

本地证据：

- `ci.test.test_player_assets`：`6/6`，新增启动顺序门禁通过
- `PlayerPolicyTest 12/12` 与 `PlayerSurfaceLeasePolicyTest 8/8`：合计 `20/20`，零失败、
  零错误、零跳过；任务包含 `:app:compileDebugKotlin`
- 静态反向检查：`initialize` 内 `MPVLib.attachSurface=0`、初始化前
  `force-window=yes=0`、`MPVLib.init=1`
- formal readiness 与 `git diff --check` 均通过
- `:app:assembleDebug --no-daemon --console=plain`：PASS，233 个任务中
  27 executed / 206 up-to-date；`verifyDebugPlayerRuntimePackaging` 通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-07-28 19:39:17 +08:00`，
  `455956807` 字节，SHA-256
  `91634B63C7D5D6EDC5FF20FBBE76663F7850D8A446D52874B556353E1174FB9E`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34、Android Debug v2，16 KB ZIP
  对齐通过

## 2026-07-28 hikerView 视频播放器设置页复刻与渲染配置扩展

本轮继续使用
[`kiyori_browser_product_completion/`](kiyori_browser_product_completion/index.md)
作为唯一播放器进度载体，不创建平行 TODO 目录。参考图来自
`D:\10_Project\hikerView`；截图定义页面顺序、文案、分组和默认勾选状态，hikerView 源码用于核对
小窗、直接全屏、播放进度、重力感应和 M3U8 等行为语义。

实施门禁：

1. [DONE] 按截图完整呈现 `4/5/8/4/3/1` 六组共 25 个原始选项，保持原顺序、右侧摘要和
   勾选状态
2. [DONE] 追加解码器预设、GPU Next 渲染、Vulkan 渲染上下文、记忆超分模式、记忆播放倍速和
   音量增强六个选项
3. [DONE] 解码器预设复用 mpv 内置 `fast/default/high-quality/gpu-hq/low-latency/sw-fast`
   profile；GPU Next 与 Vulkan 只由唯一 mpv engine 在内核创建时消费
4. [DONE] 记忆超分、记忆倍速和音量增强由唯一 `PlayerSettingsStore` 持久化，并由唯一
   `PlayerSession` 或 `MpvPlayerEngine` 真实消费
5. [DONE] 当前 Kiyori 没有真实 owner 的 hikerView 原始选项沿用浏览器设置页既有契约，只显示
   静态状态或空动作，不新增第二播放器、第二设置 owner 或伪造成功
6. [DONE] 更新 `CONTEXT.md`、`README.md` 和播放器阶段文档，执行定向测试、Kotlin 编译、
   formal readiness、`git diff --check` 与最终 Debug APK 构建和产物核验

真机安装、ADB、MuMu、Release、提交和推送不属于本轮授权。

本地证据：

- `PlayerPolicyTest 13/13` 与 `KiyoriSettingsPagesTest 8/8`，合计 `21/21`，零失败、零错误、
  零跳过
- `ci.test.test_player_assets 6/6`、`:app:compileDebugKotlin`、formal readiness 与
  `git diff --check` 通过
- `:app:assembleDebug --no-daemon --console=plain` 通过，233 个任务零失败；
  `verifyDebugPlayerRuntimePackaging` 通过
- APK 为 `app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-28 20:26:27 +08:00`，
  大小 `459183137` 字节，SHA-256
  `8F2BD60B097A730C43221B286E0B6F4EB38ACA26AF2BE3E650FD13592B87F1A8`
- APK 为 `com.kiyori`、`45 / 0.1.0`、min 26/target 34，Android Debug v2 签名通过，
  `zipalign -c -P 16 -v 4` 为 `Verification successful`
- [PENDING] 目标设备上的页面逐项视觉、选择抽屉、profile 解码效果、GPU Next、Vulkan、
  Anime4K/倍速记忆和音量增强仍需真机验收
