# 最新日志复核：runtime readiness 与诊断噪声

状态：`LOCAL IMPLEMENTATION VERIFIED / TARGET DEVICE VERIFICATION PENDING`

## 证据

2026-08-28 Browser Diagnostics 共 723 条原始记录，使用 Android System WebView `151.0.7922.199`。
其中 7 个 `PLAYER_HANDOFF` 的 candidate/request 标识均唯一，没有新的重复交接、渲染器退出或应用
FATAL。`go.itab.link` 有 2 条 `SSL_ERROR primaryError=3`，应作为独立 TLS 失败证据保留。

同一时段代理日志显示 generation 1、2、3 均由 Mihomo 输出 `shutting down` 后以退出码 `0` 自然
退出，generation 1/2/3 的 uptime 分别约为 62 秒、153 秒、232 秒，均由现有恢复协调重新启动，
generation 4 已达到 controller `200` 和 mixed-port listening。日志没有与这些退出对应的
`正在停止内嵌 Mihomo`，因此只能确认是未标记的自然退出，不能从当前证据推断外部停止信号来源。
`AI_TOOLS` 连接 `127.0.0.1:32145` 与 Mihomo mixed port 不同，归类为目标本地服务不可用。

## 实现决策

1. 自然退出或健康失败会立即使旧 WebView proxy readiness 失效；等待旧 deferred 的 Browser 远程
   主文档会收到明确失败，后续导航等待新代次，不能在恢复窗口使用死亡端口。每次协调携带 generation，
   旧协调在 runtime 失败并创建新代次后不能覆盖新状态。
2. 自动恢复仍由单一 `KiyoriNetworkProxyManager` 和现有 `mutationMutex` 持有，每个 generation 一次，
   五分钟最多两次；超过上限且当前配置仍需要内嵌代理时进入可观察 `FAILED`，用户在恢复期间关闭
   内嵌代理则以成功结束 readiness，不残留失败状态。
3. process-wide WebView proxy override 记录端点、私有网段开关和域名旁路集合指纹。完全相同时复用
   已安装覆盖，runtime failure 会清除缓存后重新安装，避免端口复用造成 stale override。
4. `BrowserDiagnosticLog` 在 1 秒窗口内按完整 console fingerprint 聚合，允许跨 session 交错，保留
   `repeatCount`，计数上限为 1,000,000；报告和诊断抽屉显式显示计数。

## 非目标与风险

- 不推断 `Mihomo shutting down` 的具体发送者；需要设备级进程、signal 和系统日志才能继续定位。
- 不替换 WebView provider，不启用 Android VPN/TUN，不引入第二核心、第二播放器或第二 Browser Runtime。
- TLS 错误、目标本地服务不可用和上游节点 `503` 仍需按各自链路处理，不由 readiness 或诊断聚合隐藏。
- 自动测试不能证明 vivo Android 16 的 WebView readiness、自然退出和全屏播放在现场完全稳定，保留
  `verification_pending`。

## 验收

- 定向 `BrowserDiagnosticModelsTest` 与 `KiyoriMihomoRuntimeProcessPolicyTest` 通过后，再执行完整
  `:app:testDebugUnitTest`、`compileDebugKotlin`、formal readiness、fresh-clone、`git diff --check`
  和 `:app:assembleDebug --no-daemon --console=plain`。
- APK 需核对包名/版本、唯一 launcher、Debug V2 签名、16 KiB zipalign、代理和播放器 runtime
  packaging。真机复测项目包括：冷启动默认代理首个远程页面、runtime 自然退出恢复期间导航、恢复后
  WebView/播放器/下载器/AI 请求、真实 HTTPS/Range/HLS、长时间运行和诊断抽屉导出。

## 2026-08-28 本地验证结果

- M-03/M-05A1 架构正反向定向测试 `5/5`、完整架构单测 `109/109` 和
  `check_architecture_boundaries.py --phase m03 --require-main` 均通过。ARCH018 当前锁定
  `KiyoriApplication` 规范化 SHA-256
  `78FC05DD02A789AF6C633002D2E3C52A7CDE1FF31877E6E86E30E8829260265A`；ARCH040 已登记
  `KiyoriSettingsUi.kt` 对 `KiyoriSettingsTheme` 的真实依赖。
- `check_formal_readiness.py --require-main`、`check_fresh_clone.py` 和 `git diff --check` 通过；新鲜
  克隆基线为 `be4f70ef7154a6945d9ebd577763164348a6ee86`。
- 完整 `:app:testDebugUnitTest` 为 `BUILD SUCCESSFUL in 1m 23s`，`159` 个任务中 `8` 个 executed、
  `151` 个 up-to-date。规定的 `:app:assembleDebug --no-daemon --console=plain` 为
  `BUILD SUCCESSFUL in 48s`，`235` 个任务中 `23` 个 executed、`212` 个 up-to-date；唯一 launcher、
  脚本代理 runtime 与播放器 runtime 三个 packaging gate 均通过。
- 最终 `app-debug.apk` 写入时间为 `2026-08-28 04:09:14 +08:00`，大小 `503669293` bytes，
  SHA-256 为 `50E87C4A97BF2CE7F431D0D0962CE3892B1FA8CA43E7C3AAAB550B751DEB325E`。包名/版本/SDK 为
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`；Android Debug V2、单 signer 与 16 KiB zipalign
  通过。APK 只含 `arm64-v8a` 的 53 个 native library，重复 basename 为 0。
- 本轮未安装 APK、未操作设备、未提交、未推送。上述证据只证明本地实现和构建闭环；vivo Android 16
  上的冷启动默认代理、自然退出恢复窗口、真实播放链路和诊断 UI 仍为 `verification_pending`。
