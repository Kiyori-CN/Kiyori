# 插件与包语义

ToolPkg 是插件容器，可注册工具子包、输入框菜单、消息处理、XML 渲染、抽屉页面和配置页面。市场 wire type `package` 与格式 `toolpkg_v2` 是 Operit 生态协议，源码中继续保留；显示层称为“插件”。

普通 JS、TS 与 HJSON 包仍位于包标签页。市场中的 `script` 类型继续走普通包安装链路，不能与 ToolPkg 插件容器合并。

Kiyori 的设备文件路径和显式 receiver component 使用宿主 application ID `com.kiyori`；原有广播 action、Java namespace、`com.operit.*` ToolPkg ID 和市场 wire type 继续保持兼容。

市场 `minAppVer` 和 `maxAppVer` 描述 Operit 插件运行时契约。Kiyori 使用独立的 `OPERIT_MARKET_COMPAT_VERSION` 判定该范围；Kiyori 产品 `versionName` 不参与判断。

源码实现已完成，实时市场代表资产的下载、SHA 和 ToolPkg 结构已验证；Android 安装与插件生命周期待真机验证。

[DONE]
