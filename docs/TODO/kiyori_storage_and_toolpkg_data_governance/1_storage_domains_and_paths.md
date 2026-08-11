# 存储域与路径

## 唯一目录 owner

`app/src/main/java/com/kiyori/platform/storage/KiyoriPaths.kt` 是目录名称、层级和物理根的唯一 owner。
消费者只能调用路径函数或更高层服务，不得重新拼接 `Download/Kiyori`、`Pictures/Kiyori`、
`Android/data/com.kiyori` 或内部 `Kiyori/toolpkg-runtime`。

`OperitPaths` 与 `OperitBackupDirs` 继续作为兼容 JVM facade，只委派到 Kiyori owner。名称中仍含
Operit 的环境变量、源码 namespace、协议和生态 ID 继续保留；它们不是旧物理目录的证据。

## 四个数据域

### private

保存 ToolPkg 状态、索引、敏感结构化数据和迁移 generation。根目录位于
`context.noBackupFilesDir`，不向 JavaScript 返回绝对路径，不进入 Android 自动备份。

### cache

保存可重建的 ToolPkg 缓存、解压副本和构建事务。根目录位于 `context.cacheDir`。运行时可以按包
删除自己的缓存，不能访问其他包。

### public

保存用户主动管理、可能与其他应用交换的工作文件。ToolPkg 公共工作区位于
`Download/Kiyori/toolpkg/<package-key>/public`。该域不是状态数据库、凭据或隐私信息的默认位置。

### export

只保存用户明确执行导出、保存或下载动作后生成的文件。Kiyori 自有导出通过
`KiyoriPublicStore` 写入，并按领域进入固定子目录。

## 用户可见目录

| 领域 | 公开目录 |
| --- | --- |
| Browser 普通下载 | `Download/Kiyori/browser/downloads` |
| Browser 日志与报告 | `Download/Kiyori/exports/browser` |
| 用户脚本源码 | `Download/Kiyori/exports/userscripts` |
| Player 报告 | `Download/Kiyori/exports/player` |
| 工具箱与 Logcat | `Download/Kiyori/exports/toolbox` |
| AI 配置与提示词 | `Download/Kiyori/exports/ai-config` |
| 用户生成的备份归档 | `Download/Kiyori/exports/backups` |
| ToolPkg 显式导出 | `Download/Kiyori/exports/toolpkg/<package-key>` |
| Markdown 图片 | `Pictures/Kiyori/Markdown` |
| 分享图片 | `Pictures/Kiyori/Shared` |
| AI 与模型设置图片 | `Pictures/Kiyori/AI` |

## 公共写入合同

Android 10 及以上版本使用 MediaStore：

1. 解析固定领域的 `RELATIVE_PATH`
2. 生成安全且不覆盖已有文件的显示名称
3. 以 `IS_PENDING=1` 创建目标
4. 完整写入并刷新输出流
5. 以 `IS_PENDING=0` 提交
6. 任一步失败时删除 pending URI，并把异常交给调用方

Android 8 和 9 使用同一目录投影写入公开目录，完成后调用媒体扫描。两条平台路径必须产生相同的
用户可见层级和文件命名语义。

## 内部数据收口

- `packageLogs`、原始 crash/error、测试输出和 backup staging 不再作为新功能的共享目录
- Browser 的应用专属下载目录使用 `files/Download/browser`，不重复 `Kiyori/browser/downloads`
- ToolPkg 已验证制品不以 `Android/data/.../files/packages` 作为长期唯一副本
- ToolPkg 构建临时文件进入 `cacheDir/Kiyori/toolpkg-build/<transaction-id>`
- ADB Debug JS staging 使用 `/sdcard/Android/data/com.kiyori/files/js_temp`
- UI Automator dump 使用每次调用唯一的 `/data/local/tmp/kiyori-ui-<transaction-id>.xml`

既有目录只在其现有兼容调用仍需要时保留，不建立新消费者，也不增加新旧目录并行读取。
