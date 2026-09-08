---
status: verification_pending
---

# Windows 工具包双端完善

本专项完善 AI 抽屉扩展中的 Windows 工具包、Kiyori PC Agent，以及局域网和 FRP 的连接契约。
正式使用说明以电脑端 [README](../../../examples/windows_control/resources/pc_agent/kiyori-pc-agent/README.md) 为准。

## 目标与边界

- 手机能导出电脑端、导入或编辑连接配置，并只在认证与协议验证成功后显示已连接。
- PC 管理界面与远程执行入口隔离；FRP 只映射执行端口，不暴露配置和令牌。
- Agent 能直接列目录、读取、写入、精确编辑、移动、复制和创建目录；返回真实路径和可诊断错误。
- 保持 `com.kiyori.windows_bundle`、`windows_control`、`WINDOWS_AGENT_*` 和已有工具名。
- 双端采用简洁的状态、连接设置、操作入口与渐进说明；手机复用宿主 Material 语义颜色。
- 本轮授权代码、文档、本地测试、Debug APK、提交和推送；不部署真实云服务器、不安装或操作手机。

## 调查基线与设计来源

2026-09-08：`main`，HEAD `d3d1c2c8cc67915d749129eb1429ddbdfaa2b822`，工作区干净。
上游参考为 Operit `f323d6c50fa661837fad06d4618462861779b562` 的同名 ToolPkg，保留其 Compose DSL、
电脑端组件与进程会话所有者；刻意调整配置步骤、认证边界、结果判断和文件工具。

已确认：手机 URL 修复会将 HTTPS 默认端口改成 58321；未知结果可能显示成功；检测执行 whoami
并硬限 5 秒；电脑配置 GET 泄露令牌、POST 无鉴权；读行和编辑可无界读取文件；整数被静默取整；
文件写入直接截断；缺少移动等基础工具。以上均以当前源码为依据。

## 实施计划

| 阶段 | 实现与验收 | 状态 |
| --- | --- | --- |
| 连接与认证 | 共用严格 URL 规范化；独立本机管理监听；远程认证探测；拒绝重定向与跨站管理请求 | 本地验证通过；真实 LAN/FRP 待验证 |
| 文件与执行 | 补齐文件工具、大小与整数校验、写入完整性、移动冲突反馈、未知提交状态提示 | 本地验证通过；复杂 ACL/长期 PTY 待验证 |
| 双端体验 | 手机连接编辑与状态；PC LAN/FRP 选择、地址预览、令牌隐藏、现代化布局与排障 | 已实现；手机视觉与交互待验证 |
| 验证与交付 | Node 行为/HTTP 集成、TypeScript、文档检查、串行 APK、允许清单审计、提交推送 | 本地验证与 APK 已完成；随本专项交付提交 |

## 关键设计

1. **连接地址**：显式 HTTP/HTTPS 使用协议默认端口；裸主机使用 58321；保留反向代理路径前缀。
   拒绝用户信息、查询、片段、空主机、非法端口与 scheme。手机 localhost 不代表电脑。
2. **管理隔离**：远程执行端口保持 58321 默认；管理 UI 使用只监听 127.0.0.1 的临时端口，启动器读取
   runtime 中的管理地址。FRP 指向执行端口；本机管理接口校验 Host/Origin，不能按远端 socket 为 loopback
   就放行 FRP 转发的管理请求。两监听复用同一配置、文件、进程所有者。
3. **结果真实**：认证检查不执行系统命令；配置保存、工具启用、连接可达分开表达。修改请求不自动重试；
   传输异常保留“结果未知，先检查目标”的语义，避免 Agent 重复执行。
4. **文件约束**：相对路径相对 Agent 根目录，工具结果返回绝对路径；优先使用绝对路径。编辑要求匹配数，
   整数必须精确；有界读取和写入，临时文件完整写入后发布。移动/复制默认拒绝已存在目标，跨卷移动明确失败，
   不静默复制再删。不增加删除工具或自动扩大授权。
5. **界面**：手机突出连接状态、地址与认证输入，将安装说明折叠；PC 提供 LAN/FRP 明确选择和可见目标地址，
   详细配置按需展开；保持现有进程与命令页，不构建第二终端。

## 风险、回滚与验证

- 协议版本与双方资源一起更新，旧代理保留明确升级提示；不静默回退旧的不安全管理行为。
- Windows 文件锁、ACL、符号链接与跨卷行为需要专门测试；不会宣称原子替换等同于事务或跨进程锁。
- 本地 HTTP/转发夹具验证鉴权和请求语义；公网 FRP、TLS 证书、路由、防火墙和手机 DSL 渲染分别现场验收。
- 回滚点为上述基线提交；交付回滚通过明确的反向提交，不重置用户工作区，已有运行配置不随源码回滚删除。
- 验证完成后记录实际命令、产物、提交和远端 ref；必要现场项目保持 `verification_pending`。

## 本轮交付证据（2026-09-08）

- 手机 ToolPkg 与 PC Agent 同步为 `1.1.0`；旧代理明确提示升级，不回退旧管理协议。
- TypeScript：`node node_modules/typescript/bin/tsc -p examples/windows_control/tsconfig.json --pretty false` 通过，生成文件同步提交。
- Node：`node --test tools/example_packages/windows_control.test.mjs`，15 项通过，0 失败/跳过。
  覆盖连接校验、认证与管理隔离、实际双监听和 HTTP 转发、未知提交状态、文件和双端配置控制器。
- Windows PowerShell 5.1：`powershell.exe -NoProfile -File tools/example_packages/windows_agent_launcher.test.ps1`，
  脚本解析与 5 项进程归属判断通过；未停止实际用户进程。
- JVM：`:app:testDebugUnitTest --tests com.ai.assistance.operit.data.preferences.EnvPreferencesBatchTest --no-daemon --console=plain`，
  1 项通过，0 失败/错误/跳过，构建用时 1 分 39 秒。
- 文档检查：498 份文件、0 问题；正式准备检查与 `git diff --check` 通过。
- 最终 APK：`:app:assembleDebug --no-daemon --console=plain`，`BUILD SUCCESSFUL in 36s`，238 个任务。
  产物 `app/build/outputs/apk/debug/app-debug.apk`，483904551 字节，生成时间 22:29:11（Asia/Shanghai）。
  SHA-256：`19956C082DA49E3C23A1E79C64DCC1FE7D7DBB100FCE97A65E28EE9777BDB3DF`。
  包名 `com.kiyori`，应用版本 `0.1.0` / 45；APK 中 Windows ToolPkg 及电脑资源版本均为 `1.1.0`。
  已核验新增连接模块、管理请求策略、进程归属脚本与源码一致，包内无运行配置、`.env` 或 `node_modules`。
- PC 管理页在隔离浏览器预览中完成布局和配置步骤检查；最终控制器逻辑另有 Node 测试。
  该证据不替代手机 Compose DSL 渲染、最终样式的完整视觉验收或真实公网连接。

## 下一验收门槛

1. 手机安装本轮 APK，从扩展导出新版 PC Agent，完成真实局域网连接、错误令牌、断连后重新验证。
2. 使用有效 HTTPS 证书与真实 FRP 部署，验证路径前缀、云端反代、防火墙和超时；确认远端无法读取管理配置。
3. 让手机 Agent 在测试目录完成列举、读写、精确编辑、复制、移动和重名冲突检查；模拟响应中断后先检查目标，禁止盲目重试。
4. 验证中文/长路径、复杂 ACL、文件锁、符号链接与真实跨卷失败；执行长期 PTY、进程退出和日志截断场景。
5. 完成手机窄屏、键盘、深浅色、Back 与状态文案，以及 Windows 默认浏览器的最终视觉验收。

上述现场项目尚未执行，专项状态保持 `verification_pending`。本地 HTTP 转发夹具不是公网 FRP 验收，
完整文件发布不是跨进程事务或目标路径锁；不宣称阻止所有外部程序同时修改文件的竞态。
