# Android 与系统可见品牌

## 原状

Android 主界面的大部分文案已经使用 Kiyori，但仍存在以下直接可见残留：

- 桌面语音助手小组件显示 `Operit`
- Android 文件选择器显示 `Operit Workspace`、`Operit Data`、`Operit Memory Library`
- 屏幕捕获前台通知显示 `Operit is capturing screen content`
- 对话前台、回复、悬浮聊天、工具和 userscript 通知使用 Android 平台 `ic_dialog_info`；该资源在部分 OEM 通知面板上显示为蓝色机器人图标
- 数据恢复说明、分享图无障碍描述、工具测试内容和蓝牙默认名称仍使用 Operit
- 默认角色名称、提示词和头像仍属于 Operit
- 备份页把当前 `Download/Kiyori` 目录及当前应用原生 JSON 显示为 Operit
- `shortcuts.xml` 的具体 `targetPackage` 仍指向旧 application ID
- 工作区源码注释与预置 `apktool` 的 JVM 兼容目录仍引用旧 application ID 的沙箱路径

## 修改范围

- 只替换上述显示值和当前宿主包引用
- 默认角色头像改为现有 `ic_kiyori_app_icon` 资源，不新建第二份图片
- 内部资源 key、provider root ID、日志 TAG、类名、方法名和文件名保持不变
- 备份内部 schema 和导入检测保持不变，显示层使用 Kiyori 名称
- Shortcut action 与 `com.ai.assistance.operit...` 实现类路径保持不变，只有 `targetPackage` 使用 `com.kiyori`
- 当前应用内部文件与缓存目录使用 `/data/data/com.kiyori`；源码 namespace、Java bridge 和 ToolPkg ID 保持不变
- 所有应用通知统一使用现有 `ic_kiyori_notification`，不再引用平台 `ic_dialog_info`

## 预期结果

Android 桌面、系统文件选择器、通知、默认助手配置、备份页面和调试工具不再把当前应用显示为 Operit，同时既有外部契约保持可用。

## 状态 [DONE]

Android/System 显示层、默认角色、快捷方式与当前宿主沙箱路径已按上述边界实施；用户真机截图发现并修正通知平台图标遗漏，修复后的真机显示仍在第三阶段保持待验证。
