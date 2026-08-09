---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# AI 助手模型名称标签管理

## 原状

“AI 助手 > 模型与 API”将多个模型名称放在 `ModelConfigData.modelName` 的一个逗号分隔字符串中。设置页使用文本输入框编辑这段字符串，并通过模型获取对话框把选中的模型重新拼接为逗号字符串。

模型列表的顺序不仅用于展示。连接测试默认测试索引 `0`；功能模型映射和固定角色卡绑定也保存模型索引。因此只替换外观而不处理索引，会在排序或删除后静默改变已有功能绑定。

## 目标

- 让模型名称以单个标签呈现，按可用宽度自适应换行。
- 默认折叠为三行，展开后显示完整列表。
- 单击标签主体复制模型名，点击叉号删除，长按标签进入纵向拖拽排序。
- 第一项带有明确“测试模型”标记，连接测试显示实际模型名。
- 支持手动输入单个或多个模型名称，并支持从上游获取、搜索、多选和追加。
- 排序保持功能模型与固定角色卡原有模型绑定；删除绑定模型必须经过明确确认。
- 继续使用 `ModelConfigData.modelName` 作为唯一持久化边界。

## 实施顺序

1. [DONE] 建立模型名称规范化、解析、序列化、合并、排序和索引重映射的纯函数契约
2. [DONE] 建立跨 `ModelConfigManager`、`FunctionalConfigManager` 和 `CharacterCardManager` 的绑定协调器
3. [DONE] 重构模型设置区为标签卡片、手动添加、上游搜索添加、排序面板和批量操作
4. [DONE] 明确首项测试模型并整理连接测试结果展示
5. [DONE] 补充项目语义文档、定向测试和静态差异检查
6. [DONE] 串行构建并核验 Debug APK
7. [PENDING] 目标设备上的标签完整显示、复制、删除、长按排序、上游搜索和模型测试交互验收
8. [DONE] 统一“添加模型 / 从上游获取 / 更多”为单行等高操作栏
9. [DONE] 将上游选择长列表改为现代化底部面板
10. [DONE] 修复搜索框提示文字因固定高度产生的垂直裁切
11. [DONE] 将“重命名 / 删除 / 测试模型”统一为单行操作栏，并保留首项测试规则
12. [DONE] 补充聊天、Tool Call、识图、音频、视频连接测试能力契约
13. [DONE] 补充第二轮 UI 契约测试、文档和静态检查
14. [DONE] 重新构建并核验第二轮 Debug APK
15. [DONE] 让上游面板默认勾选且允许取消当前上游模型
16. [DONE] 按上游选择差异安全应用添加、移除和绑定确认
17. [DONE] 修复折叠标签的固定宽度、内部留白和边界裁切
18. [DONE] 将六个模型操作统一为共享的紧凑尺寸令牌
19. [DONE] 补充第三轮契约测试、文档、门禁和 Debug APK 核验
20. [DONE] 根据现场截图撤销高度裁切方案，改为只布局两行完整标签并重新验证
21. [DONE] 修复普通 `FlowRow` 展开指示器读取 `shownItemCount` 导致的 APP_FATAL，并排除弃用 API
22. [DONE] 将默认折叠调整为三行，并把展开入口改为真实隐藏数量标签
23. [DONE] 修复清空全部在绑定阻止场景下没有可见响应

## 第二轮本地证据

- `ModelNameListTest` 与 `ModelNameTagEditorContractTest` 共 `10` 项测试通过，失败、错误和跳过均为 `0`。
- 正式开发准备门禁通过；`git diff --check` 无 whitespace error。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，唯一 Launcher 与播放器运行时打包校验通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，应用 `com.kiyori`，版本 `0.1.0 (45)`，仅包含 `arm64-v8a`。
- APK 大小 `471616082` 字节，SHA-256 为 `EFB9C79C96B59ED3ABB574E2D868F93FE4F68D3693DF75806562EDF6599CF261`。
- Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 验证通过。

## 第三轮本地证据

- `ModelNameListTest` 增加上游选择差异用例后为 `9/9`，`ModelNameTagEditorContractTest` 为 `3/3`，合计 `12/12` 通过。
- 正式开发准备门禁与 Markdown 解析器 `7/7` 通过；`git diff --check` 无 whitespace error。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，唯一 Launcher 与播放器运行时打包校验通过。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，应用 `com.kiyori`，版本 `0.1.0 (45)`，仅包含 `arm64-v8a`。
- APK 大小 `471619002` 字节，SHA-256 为 `B343051729C55FCAE71F48A196DA1412788AFACDF4466E3C81BAEC7EC1DBC9E3`。
- Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 验证通过。

第三轮制品后的现场截图证明：基于标签真实底边计算高度再裁切完整列表，仍会让第三行先参与布局并露出上半截。该方案已判定无效；后续实现最终改为自定义 `Layout` 只放置两行完整标签和“展开全部”，隐藏标签不执行放置。

## 第四轮首次实现（已否决）

- `ModelNameListTest` 为 `9/9`，`ModelNameTagEditorContractTest` 为 `3/3`，合计 `12/12` 通过。
- 契约测试要求使用 `FlowRowOverflow.expandIndicator` 与两行上限，并明确禁止折叠高度常量、坐标测量、`ModelTagLayout` 和 `clipToBounds`。
- 正式开发准备门禁、Markdown 检查器 `7/7`、8 份相关 Markdown 的本地链接扫描和 `git diff --check` 通过。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，238 个任务中 28 个实际执行，并通过唯一 Launcher 与播放器运行时打包校验。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，应用 `com.kiyori`，版本 `0.1.0 (45)`，仅包含 `arm64-v8a`。
- APK 大小 `471619002` 字节，SHA-256 为 `4EB315694445FFD53781DCBD3000FB230946C84D5CAE6D2743E3F22D6CA99B3F`。
- Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 验证通过。

该 APK 已被崩溃报告 `b79f91c3-11c9-4ea5-87d0-00e34a812f79` 否决：普通 `FlowRow` 的展开指示器在组合阶段读取尚未初始化的 `shownItemCount`，触发 `IllegalStateException`。静态测试和构建成功不能替代运行时组合阶段验证。`ContextualFlowRow` 与 `FlowRowOverflow` 在当前 Compose 都已明确标记为不再维护，因此最终方案改用稳定的自定义 `Layout`：根据标签真实测量宽度生成两行放置计划，为固定“展开全部”预留完整位置，不读取任何溢出作用域或使用像素裁切；总模型数继续由标题右侧“共 N 个”提供。

## 第四轮修正版本地证据

- `ModelNameListTest` 为 `9/9`，`ModelNameTagEditorContractTest` 为 `3/3`，`ModelTagFlowPlanTest` 为 `6/6`，合计 `18/18` 通过。
- 行计划测试覆盖全部放入一行、恰好两行、第二行预留展开入口、整枚移除标签、禁止跨过隐藏模型显示后续短模型，以及展开显示全部。
- 生产组件已清除 `shownItemCount`、`totalItemCount`、`FlowRowOverflow`、`ContextualFlowRow`、高度裁切、坐标测量和旧剩余数量字符串。
- 正式开发准备门禁、Markdown 检查器 `7/7`、8 份相关 Markdown 的本地链接扫描和 `git diff --check` 通过。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，238 个任务中 28 个实际执行，并通过唯一 Launcher 与播放器运行时打包校验。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，应用 `com.kiyori`，版本 `0.1.0 (45)`，compileSdk `37`，targetSdk `34`，仅包含 `arm64-v8a`。
- APK 大小 `471618990` 字节，SHA-256 为 `A34675DE5E4745ABE6BD45C9C5C60EB547865CBC5A3D1566EDC626AB8A09554A`。
- Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 验证通过。
- APK 文件时间由构建主机记录为 `2026-08-09 02:29:49 +08:00`；当前会话日期为 `2026-08-08`，该文件时间只作为产物元数据，不用于判断当前日期。

## 第五轮本地证据

- 折叠行数已改为三行，`ModelTagFlowPlan.hiddenModelCount` 由最终放置结果计算；展开入口显示无删除叉号的“还有 N 个”。
- `SubcomposeLayout` 只放置计划中的完整标签，不通过容器高度裁切后续行，也不读取组合阶段尚未初始化的溢出计数。
- 清空全部在异步绑定检查前立即显示 `Checking`，无绑定时进入 `Confirm`，存在绑定时进入带绑定数量和原因的 `Blocked` 对话框。
- `ModelNameListTest` 为 `9/9`，`ModelTagFlowPlanTest` 为 `6/6`，`ModelNameTagEditorContractTest` 为 `4/4`，合计 `19/19` 通过。
- 正式开发准备门禁、Markdown 检查器 `7/7`、8 份相关 Markdown 的本地链接扫描和 `git diff --check` 通过。
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 构建成功，238 个任务中 32 个实际执行，并通过唯一 Launcher 与播放器运行时打包校验。
- Debug APK 为 `app/build/outputs/apk/debug/app-debug.apk`，应用 `com.kiyori`，版本 `0.1.0 (45)`，compileSdk `37`，targetSdk `34`，仅包含 `arm64-v8a`。
- APK 大小 `471620006` 字节，SHA-256 为 `4642C2CD74B5F7A82729B44D4F004D879B2737807D5D058BD9DC948E7FE5A857`。
- Android Debug V2 单签名与 `zipalign -c -P 16 -v 4` 验证通过。
- APK 文件时间由构建主机记录为 `2026-08-09 02:57:45 +08:00`；当前会话日期为 `2026-08-08`，该文件时间只作为产物元数据，不用于判断当前日期。

## 验收边界

自动化证据需要覆盖：

- 逗号、中文逗号、换行、空项和重复项的规范化
- 追加不改变已有顺序，排序移动保持目标模型名
- 功能模型和固定角色卡索引按模型名重映射
- 删除绑定模型必须提供明确替代模型；删除唯一绑定模型被拒绝
- 模型标签组件使用折叠换行、复制、删除、手动添加、上游追加和排序入口
- 既有模型获取、保存、服务刷新和连接测试链路仍可编译和通过相关检查

设备验收不由本地构建替代，完成前保持 `verification_pending`。

## 文档分工

- `ModelConfigData.kt`：模型名称字符串边界与纯列表辅助函数
- `ModelConfigModelBindingCoordinator.kt`：绑定索引协调，不持有新的持久化事实
- `ModelNameTagEditor.kt`：标签编辑、手动输入、排序和上游列表入口
- `ModelApiSettingsSection.kt`：当前配置的状态、保存、模型获取和绑定影响确认
- `ModelConfigScreen.kt`：连接测试按钮与实际测试模型展示
- `UpstreamModelPickerSheet.kt`：上游模型搜索、多选和固定底部操作区
- [`4_ui_refinement.md`](4_ui_refinement.md)：第二轮视觉和布局精修合同
