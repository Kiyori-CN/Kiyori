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

构建不能证明 Launcher 缓存、系统文件选择器、快捷方式、通知、默认助手头像和 Terminal DocumentsProvider 的真机显示结果。第一次 iQOO 截图促使所有通知把状态栏 `smallIcon` 改为 `ic_kiyori_notification`；第二次真机复测证明通知卡片左侧仍取应用或大图标。常驻对话通知现已显式指定 `ic_kiyori_app_icon`，Manifest 也改用新的 `ic_kiyori_launcher` 资源 ID。新 APK 未完成第三次真机复测时，最终状态保持 `verification_pending`。

## 验证结果 [DONE]

- `git diff --check`：父仓库与 `terminal` 均通过
- TypeScript：`apktool`、QQ Bot、`remote_operit`、Windows Control 的运行时 `dist/` 已同步；打包内容检查通过
- WebChat：337 个模块完成生产构建并同步到 Android assets；APK 内标题为 Kiyori，favicon SHA-256 为 `36C5C4826007E7A8F872D4F4120AFDB9ADF61D1768AAA9841B5638A91E719DDC`
- 正式门禁：`check_formal_readiness.py --repository . --require-main` 通过
- Debug：第二层通知图标修正的首次构建被 Kotlin 编译器拦截，因为 `setLargeIcon` 需要平台 `Icon` 而不是 `IconCompat`；改用 `Icon.createWithResource` 后再次运行 `assembleDebug --no-daemon --console=plain`，在 1 分 21 秒内成功
- APK：`2026-07-23 23:18:49 +08:00`，`458000918` 字节，SHA-256 `3ECD72FF3EB8CB82E45411640A6289E1CCAE6D0681A3487DB3CAE0A3EDFF0D7F`
- APK manifest：`com.kiyori`、versionCode `45`、versionName `0.1.0`、label `Kiyori`，所有 application icon density 均解析到 `res/mipmap-anydpi-v26/ic_kiyori_launcher.xml`
- APK assets：11 个预置 ToolPkg 均存在，旧 `assets/operit.png` 不存在；快捷方式、WebChat、`apktool`、QQ Bot、`remote_operit` 与 Windows companion 的定向内容检查通过
- 通知图标：7 个 `setSmallIcon` 入口均引用 `R.drawable.ic_kiyori_notification`，`android.R.drawable.ic_dialog_info` 残留为 0；常驻对话通知另显式引用 `R.drawable.ic_kiyori_app_icon` 作为大图标
- Git：`terminal` 品牌显示改动已提交为 `e3ee8d1` 并推送到 `origin/main`；本清单随父仓库品牌清理提交进入 `main`
- 真机：用户两次复测把问题从状态栏小图标进一步定位到 iQOO 通知卡片的应用或大图标层；第二层修复后的 APK 尚未安装复测，任务保持 `verification_pending`
