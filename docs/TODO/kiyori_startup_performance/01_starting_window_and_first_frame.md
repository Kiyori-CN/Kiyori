# 系统启动窗口与首帧

## 旧实现

`Theme.Operit` 依赖 Android 12 的默认 Splash 图标选择，因此 Launcher 图标会被系统居中放大。`MainActivity` 随后又把窗口背景强制设置为黑色，浅色模式可能在系统背景和 Compose 首页之间产生黑色过渡。

## 修改意图

- 为 Android 12+ 显式提供完全透明的 Splash drawable
- 把 Splash 图标动画时长设为零
- 保留与浅色和深色主题分别一致的 `kiyori_background`
- 删除 Activity 对黑色窗口背景的运行时覆盖，让系统窗口直接过渡到相同主题背景

Android 12 的系统启动窗口不能由普通应用删除。本步骤删除的是其中可见的大图标，并把其存在时间缩短到首帧出现为止。

## 期待结果

用户从 Launcher 启动 Kiyori 时，视觉上直接从纯色背景进入软件首页，不再看到独立的应用图标全屏页。

[DONE]
