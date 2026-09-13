# Kiyori 默认存储

Kiyori 新建数据的公共根目录是 `/sdcard/Download/Kiyori`。插件配置、MCP、Skill、工作区、导出、备份、临时文件和 WebSession 子目录都从同一个路径所有者派生，模块不得重复硬编码品牌目录。

旧 Operit 安装与 Kiyori 使用不同 application ID。旧数据不从 `Download/Operit` 自动扫描、复制、删除或合并；用户需要通过明确的备份或文件选择入口导入。

源码实现已完成，真机目录创建与显式导入流程待验证。

[DONE]
