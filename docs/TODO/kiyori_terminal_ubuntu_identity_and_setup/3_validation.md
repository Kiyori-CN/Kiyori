# 验证与 APK 交付

## 定向检查

1. `terminal` 单元测试验证横幅内容、手机宽度和 Node 安装命令顺序
2. 父仓库正式准备单元测试验证旧 Operit 横幅会被拒绝
3. 正式准备门禁验证当前父仓库、子模块与品牌契约
4. 父仓库和 `terminal` 分别运行 `git diff --check`

## 构建

按项目默认门禁串行执行：

```text
./gradlew :app:assembleDebug --no-daemon --console=plain
```

核验 `app/build/outputs/apk/debug/` 中 Debug APK 的时间、大小和 SHA-256。

## 真机边界

自动测试和 APK 构建不能证明目标手机上的终端字符宽度、首次联网安装，以及可见会话与 AI 后台命令的真实运行结果。交付后仍需在干净应用数据上完成一次真实首装验收。

## 验证结果 [DONE]

- `terminal:testDebugUnitTest`：通过，3 个定向测试覆盖安装命令、完成 marker 和横幅宽度
- `ci.test.test_formal_readiness`：通过，5 个测试覆盖私有工具元数据、旧终端展示拒绝、Kiyori 横幅和上游运行时 URL
- `check_formal_readiness.py --repository . --require-main`：通过
- 父仓库与 `terminal` 的 `git diff --check`：通过
- 渲染层旧终端品牌定向搜索：0 个命中
- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL in 1m 44s`，230 个任务、零失败
- APK：`app/build/outputs/apk/debug/app-debug.apk`，时间 `2026-07-27 00:22:43 +08:00`，大小 `449493010` 字节，SHA-256 `7F874786BFC62070D705AB3AF30015DB5BE5FB4B9939A41276FDD706346EE9D2`
- APK 元数据：`com.kiyori`、versionCode `45`、versionName `0.1.0`、label `Kiyori`、minSdk `26`、targetSdk `34`
- 未运行安装、设备操作、Release、签名、提交或推送；真机首次安装仍为 `verification_pending`
