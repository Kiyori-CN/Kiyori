# 验证、构建、Git 与真机验收

## 每个里程碑的本地验证

1. 受影响模块的 JVM 或纯逻辑测试
2. `\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
3. `git diff --check`
4. 必要的 Kotlin 编译或 Android 单元测试
5. `\.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
6. 核对 `app/build/outputs/apk/debug/app-debug.apk`

APK 证据至少记录：绝对路径、生成时间、大小、SHA-256、application ID、versionCode、versionName、minSdk、targetSdk 和 Android Debug v2 签名。涉及 native 依赖时额外记录 ABI、ZIP 16KB 对齐和每个 arm64 ELF 的 `PT_LOAD` 对齐。

## 当前最新本地制品

- 浏览器与负一屏共享下载抽屉切片：`D:\10_Project\Kiyori\app\build\outputs\apk\debug\app-debug.apk`
- 生成时间 `2026-07-27 12:44:52 +08:00`，大小 `449493010` 字节，SHA-256 `B658EEEB9E854011F58C475321279269E80443C2E3E113E8451E08B75B9269F9`
- `applicationId com.kiyori`、`versionCode 45`、`versionName 0.1.0`、`minSdk 26`、`targetSdk 34`、`compileSdk 36`
- Android Debug V2 签名通过；`zipalign -c -P 16 -v 4` 为 `Verification successful`
- 本轮 Shell 回归定向 `KiyoriShellStateTest` 为 `35/35`，覆盖抽屉显示、退出动画、完全隐藏、AI 非根路由下负一屏入口、Back 优先级和完整 Shell 保存恢复；此前下载全链路五组测试基线为 `80/80`。Formal readiness、旧拆分式 Shell 保存变量零匹配与 `git diff --check` 通过
- `:app:assembleDebug` 为 `BUILD SUCCESSFUL in 1m 56s`，230 个任务零失败，其中 25 个执行、205 个为 up-to-date；该制品让 `OperitApp` 直接保存完整 `KiyoriShellState`，不再在顶层桥接中丢弃 `isDownloadDrawerOpen`。软件首页与负一屏实际触摸、抽屉视觉与拖动、菜单周围点击关闭、长按动作、输入法遮挡、SAF 文件与权限、Android `DownloadManager`、真实 M3U8 remux 和 APK 自动清理仍为 `verification_pending`

## Git 门禁

- 当前 Goal 默认只保留本地修改和 Debug APK，不创建提交、不推送
- 用户在当前任务中另行明确授权提交时，才再次确认 `main`、HEAD、工作树和 staged allowlist
- 禁止纳入 `work/`、APK、AAB、Gradle cache、`.venv`、凭据、运行日志和任务日记
- commit message 只描述当前里程碑
- 另行授权推送后，`git push origin main` 成功才使用 `git ls-remote origin refs/heads/main` 核对 SHA
- 已授权推送发生网络失败时，只允许有界重试同一提交；不得产生额外空提交或强制推送

## 终局真机清单

- overlay 背景覆盖状态栏，图标明暗正确，实体/手势 Back 按优先级工作
- 人工普通/无痕窗口可被 AI list、select、snapshot、click 和 type
- 普通与无痕网站数据隔离，最后一个无痕窗口关闭后数据消失
- 首页、全屏搜索、窗口缩略图、Settings Home、下载中心和负一屏在手机、平板、横屏和旋转后正常
- 下载队列、暂停、恢复、失败、重试、打开、删除和设置生效
- 阅读模式、网站配置、广告标记和工具箱不会刷新当前网页
- 视频 Intent 进入播放器，播放、seek、手势、方向变化、悬浮和全屏转场正常
- 浏览器播放器三态切换不刷新、重载、重缓存或改变网页滚动/表单状态

## 交付状态

自动检查和 Debug 构建完成后，只能声明本地实现与构建通过。状态栏、系统 Back、设备 WebView Multi-Profile、OEM overlay、硬件解码、手势和站点兼容必须由用户安装最终 APK 后验收，因此最终任务状态为 `verification_pending`，直到用户反馈关闭。
