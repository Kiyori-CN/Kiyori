# Android 16 播放器关闭与重复交接修复

状态：`LOCAL IMPLEMENTATION / AUTOMATED VALIDATION COMPLETE / DEVICE VERIFICATION PENDING`。

## 现场证据

- Android 16 `NetworkOnMainThreadException` 的调用链为 `BrowserPlayerSupport.closeBrowserPlayer` ->
  `PlayerSession.close` -> `PlayerMediaTransportResolver.close` -> `PlayerMediaStreamBridge.close` ->
  `OkHttp ConnectionPool.evictAll`。播放器的主线程状态机本身需要同步切换，但连接池回收会关闭网络
  socket，不能在该回调线程执行。
- Browser 诊断在同一 `candidateId/requestId` 上记录两次 `PLAYER_HANDOFF`。这是候选观察和 Compose
  重组共同触发的重复入口，不是第二个媒体 URL；全屏 Activity 请求必须由同一 `PlayerSurfaceLease`
  的一次性请求 ID 负责投影。
- 播放器诊断中的 `UPSTREAM_CONNECT type=SSLHandshakeException` 后，mpv 从 loopback bridge 收到
  502 并显示 `loading failed`。这说明 bridge listener 已经可达，实际失败在上游 TLS/代理节点链路；
  客户端不能把外部节点失败改写为成功。

## 修复合同

1. `PlayerMediaStreamBridge.close()` 使用原子门保证只执行一次。主线程立即关闭 loopback listener、
   清空资源索引和停止请求 executor；活动请求取消、`ConnectionPool.evictAll()` 与 dispatcher
   executor 关闭统一在专用 daemon cleanup executor 中执行，切断 Android 主线程网络 I/O。
2. `resolvePlayerOpenTransition()` 对同一 request 仅在运行时仍有效且没有错误时复用状态。运行时死亡或
   媒体加载错误时，同一候选可重新建立 load generation，用户不需要依赖刷新网页才能重播。
3. `BrowserPlayerSupport.openMediaCandidate()` 在同一 request/presentation 仍有效时直接复用 handoff，
   只标记当前文档已消费，不再次调用 `PlayerSession.open()` 或触发新的 Activity 启动。错误状态不被
   误判为重复，从而保留明确的重播入口。
4. bridge 诊断将 TLS 握手异常标记为 `UPSTREAM_TLS`，响应头已发送后的读取错误继续标记为
   `UPSTREAM_BODY`，其它连接错误标记为 `UPSTREAM_CONNECT`；三者均保持合法 HTTP 错误响应和原有
   无回退边界。

## 验证矩阵

- `:app:testDebugUnitTest --tests ...PlayerPolicyTest --tests ...PlayerMediaProxyBridgePolicyTest
  --tests ...BrowserMediaCandidatePolicyTest`：通过。
- 完整 `:app:testDebugUnitTest`、formal readiness、fresh-clone、`assembleDebug` 和 APK 审计需在
  提交前重新执行。
- 目标设备仍需验证：关闭悬浮/全屏播放器、重复点击与自动嗅探、上游可用节点下的 HTTPS/Range/HLS、
  失败后重播、旋转和进程重建。未启用 VPN/TUN、未创建第二播放器、第二代理核心或静默直连/重试。
