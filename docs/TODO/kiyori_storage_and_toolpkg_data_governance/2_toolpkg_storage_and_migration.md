# ToolPkg 存储与显式迁移

## 包身份

ToolPkg Storage API 不接受 package ID。宿主在创建 ToolPkg JavaScript engine 时绑定
`containerPackageName`，子包与容器共享同一个数据命名空间。注册阶段使用的无绑定 engine 不提供
private/cache 数据访问。

`package-key` 由规范化后的容器 ID、可读安全前缀和 SHA-256 摘要共同生成。它必须：

- 对同一容器 ID 稳定
- 不允许路径分隔符或控制字符进入目录名
- 不因仅替换特殊字符而产生碰撞
- 不暴露可由调用方指定的其他包身份

## JavaScript API

```javascript
const storage = ToolPkg.storage();

await storage.privateData.writeJson(
  "state/sidebar_analysis_state.json",
  state
);

const saved = await storage.privateData.readJson(
  "state/sidebar_analysis_state.json"
);
```

`privateData` 与 `cache` 提供：

- `writeText(relativePath, text)`
- `readText(relativePath)`
- `writeJson(relativePath, value)`
- `readJson(relativePath)`
- `exists(relativePath)`
- `delete(relativePath)`

返回值只描述操作结果，不返回内部真实绝对路径。

`getPluginConfigDir()` 与 `ToolPkg.getConfigDir()` 保留为 legacy public workspace。它只适合用户可见、
非敏感的兼容工作文件，不再作为新 ToolPkg 状态保存方案。

## 相对路径验证

Storage API 只接受 UTF-8 相对路径，并拒绝：

- 空路径、空段、`.`、`..`
- `/` 开头、反斜杠、Windows 盘符和 URI
- NUL、控制字符和结尾空格
- 超过 32 层目录
- 超过 240 个字符的规范化路径

解析后的 canonical target 必须仍在当前包和当前 namespace 根目录内。

## 配额与原子写入

- 单次文本或 JSON 读写最大 4 MiB
- privateData 默认总量最大 64 MiB
- cache 默认总量最大 128 MiB
- 写入先检查新内容、现有文件和目录总量，再通过 `AtomicFile` 提交
- 删除只允许文件；目录由宿主在确认内部为空后清理
- 配额或路径失败必须显式抛错，不能改写到另一个目录

## 显式旧数据导入

宿主只接受用户通过 SAF 选择的目录 URI，不枚举整个 `Download/Operit`。导入请求必须指定：

- 当前 ToolPkg 容器 ID
- 迁移器 ID 与版本
- 用户选择的 source tree URI
- 目标 schema 版本
- 冲突策略，由包明确声明为 replace 或 merge

处理顺序：

1. 在当前包私有根创建 staging generation
2. 由包专属迁移器声明并读取允许的文件
3. 计算每个输入文件 SHA-256 与 source tree digest
4. 校验大小、JSON schema、字段类型、时间范围和数量
5. 写入新的 generation
6. 校验 generation tree digest
7. 通过原子 active-generation 记录提交
8. 写入迁移审计
9. 清理 staging，保留用户选择的源目录

没有匹配迁移器、source digest 已成功导入、用户取消、schema 不匹配或目标冲突未决时，不改变当前
active generation。

## “记忆系统”命名建议

第三方包后续版本可采用：

| 旧文件 | 新活动文件 |
| --- | --- |
| `trigger_state.json` | `sidebar_analysis_state.json` |
| `trigger.json` | `scheduled_processing_state.json` |
| `extracted.json` | `extracted_facts.json` |
| `last_ui_state.json` | `ui_state.json` |
| `analyzed_chats.json` | `analyzed_chat_index.json` |
| `memories.json` | 仅作为迁移输入 |

这些名称是包迁移建议，不表示当前仓库已拥有或修改第三方发布包。
