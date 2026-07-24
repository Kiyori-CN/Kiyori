# 验证、构建、Git 与真机验收

## 每个里程碑的本地验证

1. 受影响模块的 JVM 或纯逻辑测试
2. `\.venv\Scripts\python.exe -B ci/script/check_formal_readiness.py --repository . --require-main`
3. `git diff --check`
4. 必要的 Kotlin 编译或 Android 单元测试
5. `\.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
6. 核对 `app/build/outputs/apk/debug/app-debug.apk`

APK 证据至少记录：绝对路径、生成时间、大小、SHA-256、application ID、versionCode、versionName、minSdk、targetSdk 和 Android Debug v2 签名。涉及 native 依赖时额外记录 ABI、ZIP 16KB 对齐和每个 arm64 ELF 的 `PT_LOAD` 对齐。

## Git 门禁

- 每次提交前再次确认 `main`、HEAD、工作树和 staged allowlist
- 禁止纳入 `work/`、APK、AAB、Gradle cache、`.venv`、凭据、运行日志和任务日记
- commit message 只描述当前里程碑
- `git push origin main` 成功后使用 `git ls-remote origin refs/heads/main` 核对 SHA
- 推送网络失败只允许有界重试同一提交；不得产生额外空提交或强制推送

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
