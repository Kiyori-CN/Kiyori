# 身份、渠道与资产

本页记录 [品牌迁移](index.md) 在 2026-07-22 的实施与本地验证；不将历史记录升级为当前设备验收。

## 迁移前后

| 项目 | 迁移前 | 目标 |
| --- | --- | --- |
| 展示名称与项目名 | `Operit Clone` / `Operit`，界面仍使用 Operit AI | 统一展示 Kiyori |
| 项目链接 | 上游仓库与网站 | Kiyori 项目入口 |
| 图标 | 上游应用资产 | Kiyori 品牌资产 |
| 更新与公告 | 启动、关于页获取上游发行；根应用获取远程公告 | 无可达的 Operit 应用更新或远程公告入口 |

## 验证方式

1. 通过 Debug 构建检查合并后的资源与 Manifest。
2. 搜索运行时源码中的更新、公告入口。
3. 比较复制的图标与权威 Kiyori 源资产哈希。
4. 在 Android 设备验收启动器、关于页、分享图片、通知和助手选择。

## 历史证据

- 四组源/目标 Kiyori 资产的 SHA-256 一致。
- 父仓库与 `terminal` 的 `git diff --check` 通过。
- `assembleDebug --console=plain` 成功，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- `aapt dump badging` 显示 `com.kiyori`、`versionCode 45`、`versionName 0.1.0`。

原实施条目标记为 `[DONE]`，只对应上述本地证据；专项设备状态继续为 `verification_pending`。
