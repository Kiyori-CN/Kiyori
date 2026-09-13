# 验证

## 自动验证

- `python -B ci/script/check_formal_readiness.py --repository . --require-main`
- `git diff --check`
- `./gradlew :app:assembleDebug --no-daemon --console=plain`
- 核验 `app/build/outputs/apk/debug/app-debug.apk`
- 检查合并资源中的 Splash 透明图标与零动画时长

### 2026-07-27 结果

- 正式开发准备检查：`PASS`
- `git diff --check`：通过，仅有工作树既有 CRLF 转换提示
- `KiyoriShellStateTest`：38 项通过，0 失败，0 错误，0 跳过
- `:app:assembleDebug`：`BUILD SUCCESSFUL in 2m 12s`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- APK 大小：`449493405` bytes
- APK SHA-256：`8D0653184B35C80AE7B5F9AF93D5BC442D2D3F3A3E2FE6CBC2287B0731DFFA7F`
- 包名与版本：`com.kiyori`，versionCode `45`，versionName `0.1.0`
- APK V2 签名与 16 KB ZIP 对齐：通过
- APK 资源表：浅色、深色 `Theme.Operit` 均引用 `ic_kiyori_splash_transparent`，动画时长均为 `0`

## 真机验证

- 结束 Kiyori 进程后从 Launcher 冷启动
- 浅色与深色模式分别确认没有全屏大图标和黑色闪屏
- 确认软件首页可交互，权限引导与插件加载仍按原契约出现
- 使用系统启动统计或 Perfetto 对比冷启动首帧耗时

真机结论不能由本地编译替代。在目标设备复测前，本任务保持 `verification_pending`。

自动验证 [DONE]；真机冷启动验证 `verification_pending`。
