# 1. 模型数据与绑定安全

## 旧实现

- `ModelConfigData.modelName` 是逗号分隔字符串。
- `getModelList`、`getModelByIndex` 和 `getValidModelIndex` 在多个聊天、功能配置、角色卡和 WebChat 入口重复解析该字符串。
- `FunctionalConfigManager.FunctionConfigMapping.modelIndex` 和 `CharacterCard.chatModelIndex` 只保存位置。

## 新实现

- 保留 `modelName` 字段和逗号序列化边界。
- 统一处理空格、空项、中文逗号、换行和完全重复项。
- UI 操作只生成规范化有序列表，不直接拼接字符串。
- 排序按模型名计算新索引，保持已有功能/角色卡绑定的模型不变。
- 删除被绑定模型时由界面明确指定新的首项模型；协调器不自行猜测替代模型。
- 删除唯一仍被绑定的模型直接拒绝，避免产生无效绑定。

## 失败路径

- 绑定引用无法解析时，协调器保留严格错误并阻止保存。
- 绑定替代模型不在新列表时，阻止保存并报告原因。
- 绑定协调或配置保存失败时，自动保存向现有错误提示链路报告，不宣称成功。
