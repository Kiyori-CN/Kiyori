# 残留审计与构建验收

## 静态审计

- 对父仓库和 `terminal` 分别运行 `git diff --check`
- 重新搜索 `Operit`、`operit` 和 `OPERIT`，逐项确认剩余命中属于兼容、生态、归属或内部实现
- 确认 `Download/Operit` 不再作为新建数据路径
- 确认当前宿主沙箱不再硬编码 `/data/data/com.ai.assistance.operit`
- 确认 Launcher、round icon、monochrome icon、通知、About、分享、WebChat favicon 和默认角色头像均引用 Kiyori 资产
- 确认 `shortcuts.xml` 使用 `com.kiyori` 作为目标包，但 action 与实现类名不变

## 构建

1. 生成并同步 WebChat Android assets
2. 运行正式开发准备静态门禁
3. 运行父仓库 `assembleDebug`
4. 核验新 APK 的时间、大小、SHA-256、package、label、icon、roundIcon 和 WebChat 资产

不运行 release、签名、发布、安装或设备操作。按用户后续授权，将父仓库与 `terminal` 的预期改动提交并推送到各自的 `main`，不创建额外分支。

## 真机边界

构建不能证明 Launcher 缓存、系统文件选择器、快捷方式、通知、默认助手头像和 Terminal DocumentsProvider 的真机显示结果。未安装复测时，最终状态保持 `verification_pending`。

## 验证结果 [DONE]

- `git diff --check`：父仓库与 `terminal` 均通过
- TypeScript：`apktool`、QQ Bot、`remote_operit`、Windows Control 的运行时 `dist/` 已同步；打包内容检查通过
- WebChat：337 个模块完成生产构建并同步到 Android assets；APK 内标题为 Kiyori，favicon SHA-256 为 `36C5C4826007E7A8F872D4F4120AFDB9ADF61D1768AAA9841B5638A91E719DDC`
- 正式门禁：`check_formal_readiness.py --repository . --require-main` 通过
- Debug：`assembleDebug --no-daemon --console=plain` 在 4 分 17 秒内成功
- APK：`2026-07-23 22:06:16 +08:00`，`450295648` 字节，SHA-256 `DBA43BE71DA09B852AE11378A75EF671492CF0E8D494F364ADB63896D8A803F2`
- APK manifest：`com.kiyori`、versionCode `45`、versionName `0.1.0`、label `Kiyori`，icon 与 roundIcon 分别为 `ic_launcher_simple` 和 `ic_launcher_simple_round`
- APK assets：11 个预置 ToolPkg 均存在，旧 `assets/operit.png` 不存在；快捷方式、WebChat、`apktool`、QQ Bot、`remote_operit` 与 Windows companion 的定向内容检查通过
- Git：`terminal` 品牌显示改动已提交为 `e3ee8d1` 并推送到 `origin/main`；本清单随父仓库品牌清理提交进入 `main`
- 真机：未安装、未执行设备验收，任务保持 `verification_pending`
