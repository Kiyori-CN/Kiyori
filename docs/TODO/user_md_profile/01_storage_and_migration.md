# 用户资料存储与迁移

原记录状态：DONE（静态验证范围）。

旧 DataStore profile 混合展示 metadata 与结构化字段。新增原子写入的私有 UTF-8 `user.md`，长度限制 12,000 字符。

schema 2 迁移顺序：

1. 活动旧 profile 转为 `user.md`。
2. 非活动结构化 profile 导出为 `legacy-user-profiles.md`。
3. metadata 改为记忆空间术语，保留 ID、活动 ID 和所有 ObjectBox 数据库。
4. 文件与 DataStore 写入都成功后才标记迁移完成。
