# Mihomo 固定源码构建

此目录保存 Kiyori 内嵌 Mihomo 的可审阅构建输入。任务实现位于
[`PrepareMihomoRuntimeTask`](../../buildSrc/src/main/kotlin/com/kiyori/buildlogic/tasks/PrepareMihomoRuntimeTask.kt)，
`app/build.gradle.kts` 只提供输入并连接生成的 JNI 目录。不会运行第二核心或覆盖共享 Go 模块缓存。

## 来源与修复

`source.properties` 固定 Mihomo `v1.19.30`、sing `v0.5.7`、两者 Go 模块校验和、原始
`splice_linux.go` SHA-256 和 Go `1.26.6`。此 Go 版本与原官方二进制的 build info 一致。
构建先验证模块缓存完整性，再复制到任务临时目录；依赖继续使用上游 `go.mod/go.sum`，
`-mod=readonly` 禁止构建自动修改依赖图。只有本地 sing 替换和以下显式输入发生变化。

- [`splice_linux.go`](splice_linux.go) 源自
  [MetaCubeX/sing v0.5.7](https://github.com/MetaCubeX/sing/blob/v0.5.7/common/bufio/splice_linux.go)，
  保留 [GPL-3.0-or-later 声明](LICENSE)。补丁在读、写系统调用返回 `EINTR` 时继续同一调用。
  `EINTR` 未消费字节，不能终止 TCP 转发，也不能当成 EAGAIN 等待新的就绪边沿。
  每条复制流每个方向最多记录一次恢复日志，不包含 payload、地址或凭据；其余错误仍原样返回。
- [`ca-certificates.crt`](ca-certificates.crt) 保留原官方 Android 运行库中的同一份 121 个根证书，
  避免源码中的空 embed 改变 TLS 信任集合。原运行库 SHA-256 为
  `94344144936968f25e7089bbeac2d87f3caf67574ba433511424724ad7435dad`；证书连续 PEM
  位于文件偏移 `52303232`，长度 `182140`，独立 SHA-256 固定在 `source.properties`。
  这些是公开根证书，不含私钥。来源是官方 v1.19.30 发布流水线的 Ubuntu 系统 CA bundle。

上游构建行为可查
[Mihomo 发布工作流](https://github.com/MetaCubeX/mihomo/blob/v1.19.30/.github/workflows/build.yml)。
Kiyori 保留 `with_gvisor`、CGO、Android API 34、ARM64 与嵌入 CA，使用项目已有 NDK，
固定版本字符串 `v1.19.30-kiyori.1`、构建时间字符串和空 build ID。
源构建的本地 ELF SHA-256 由任务报告，APK 校验要求逐字节等于该次已验证的产物；
不同宿主/NDK 的 ELF 哈希不被冒充为固定跨平台哈希。

## 构建与回归

需要 PATH 中可用的 Go（或 `-Pkiyori.go.executable=<绝对路径>`），Go 工具链自动选择固定版本，
Android SDK/NDK 使用项目配置。首次构建会获取锁定 Go 工具链与模块，后续复用 Go 缓存。
源码副本、二进制、测试产物均进入 `app/build/`，不提交。

```powershell
.\gradlew.bat :app:prepareMihomoRuntime --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

已运行准备任务的 Linux x86_64 宿主（安装 `strace`）执行：

```bash
bash tools/mihomo_runtime/test_splice.sh
```

测试从同一份已验证/已修补源码编译 Linux 核心，只使用回环 TCP。正常与 EINTR 注入场景
都必须只发送一个请求、精确收到 512 KiB 非均匀字节序列、命中关键词 DIRECT；注入场景还必须
实际覆盖读和写两个方向。未注入、未命中 DIRECT 或字节损坏都不能算通过。CI 完整 Android
检查和构建执行此入口。它验证核心转发，不能证明目标 Android 内核上的实际中断原因。

手机对话是否恢复、首包/长流/工具后续请求和真实节点协议仍需现场验收；不把该补丁的
本地回归结果宣称为所有网络都不再断开。代理层不重新提交未知状态的 AI POST。
