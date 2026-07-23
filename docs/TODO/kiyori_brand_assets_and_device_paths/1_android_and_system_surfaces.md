# Android 与系统可见品牌

## 原状

Android 主界面的大部分文案已经使用 Kiyori，但仍存在以下直接可见残留：

- 桌面语音助手小组件显示 `Operit`
- Android 文件选择器显示 `Operit Workspace`、`Operit Data`、`Operit Memory Library`
- 屏幕捕获前台通知显示 `Operit is capturing screen content`
- 对话前台、回复、悬浮聊天、工具和 userscript 通知使用 Android 平台 `ic_dialog_info`
- iQOO 通知卡片左侧徽标使用应用或大图标，而不直接使用状态栏的单色 `smallIcon`；Manifest 又沿用了 Operit 时代的 `ic_launcher_simple` 资源 ID，替换资源内容后仍可能命中 OEM 旧图标缓存
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
- 所有应用通知的状态栏图标统一使用现有 `ic_kiyori_notification`，不再引用平台 `ic_dialog_info`
- 对话常驻通知显式使用 `ic_kiyori_app_icon` 作为通知卡片大图标；Manifest 改用新的 `ic_kiyori_launcher` 资源 ID，使系统重新解析 Kiyori 应用图标

## 预期结果

Android 桌面、系统文件选择器、通知、默认助手配置、备份页面和调试工具不再把当前应用显示为 Operit，同时既有外部契约保持可用。

## 状态 [DONE]

Android/System 显示层、默认角色、快捷方式与当前宿主沙箱路径已按上述边界实施；两轮真机复测先后暴露状态栏小图标和 iQOO 通知卡片大图标属于不同显示层，现已分别指定 Kiyori 资源，修复后的真机显示仍在第三阶段保持待验证。
