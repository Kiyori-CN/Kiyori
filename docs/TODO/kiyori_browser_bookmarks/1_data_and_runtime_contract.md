# 数据与运行时合同

## 模型

`WebSessionBookmark` 在现有 `url`、`title`、`createdAt`、`updatedAt` 基础上增加稳定 ID、图标 URL、文件夹 ID、手动顺序和秘密空间标记。新增 `WebSessionBookmarkFolder` 表示父子文件夹关系。

旧 JSON 不含新增字段。Kotlin Serialization 默认值从旧字段确定稳定 ID 和初始顺序，使旧数据保持根目录普通书签，不另建迁移存储，也不删除旧 key。

## 唯一 owner

`WebSessionHistoryStore` 继续负责：

- `bookmarksFlow` 与新增 `bookmarkFoldersFlow`
- 新建、编辑、移动、删除和排序书签
- 新建、重命名、移动和删除文件夹
- 普通空间与秘密空间迁移
- JSON 导入后的原子合并

UI 只提交明确的 mutation。App Shell、overlay 和 AI 浏览器工具观察同一 DataStore Flow，不缓存第二份持久状态。

## 页面打开语义

- 单击书签在当前活动 WebSession 导航
- 后台打开在当前 Profile 新建 session，然后恢复原活动 session
- 新窗口打开在当前 Profile 新建并激活 session
- 抽屉开关、路径和搜索仅属于 Compose 展示状态，不进入 Browser Runtime

## 数据操作规则

- URL 归一化继续复用当前 HTTP/HTTPS 规则
- 同一 URL 可以位于不同文件夹或空间；普通主菜单只识别并删除该 URL 的普通空间记录，不暴露或误删秘密空间记录
- 删除文件夹同时删除该文件夹及后代内相同空间的书签
- 文件夹不能移动到自身或后代
- 普通和秘密空间共享一个 owner，但列表、目标文件夹和批量操作严格按 `secret` 隔离
- 导出内容包含版本、文件夹和书签；导入先解析与验证，再一次性写入

## 验证

- 旧扁平记录解码后仍可搜索、打开和移除
- 文件夹后代计数、路径、移动合法性和排序结果有纯 Kotlin 测试
- 导出后重新导入保持标题、URL、图标、层级、顺序和空间标记

[DONE]
