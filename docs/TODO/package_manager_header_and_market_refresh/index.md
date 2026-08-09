---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# 包管理顶栏、固定搜索与市场安装刷新

## 目标

重新组织 AI 左抽屉“包管理”的页面级操作和固定区域，消除右下角浮动按钮对脚本包开关的
遮挡，并让从市场成功安装的脚本或插件立即出现在当前包管理页面，不再依赖应用重启。

## 已确认边界

- Kiyori 始终未发布，旧浮动按钮布局和包管理顶栏搜索方案直接删除
- 顶栏动作固定按“环境变量 / 市场 / 添加 / 刷新”从左到右排列
- 四个动作只在“插件”和“脚本包”页显示；Skill 与 MCP 保留各自现有管理入口
- 搜索框固定在顶栏下方，页签固定在搜索框下方，只有内容列表滚动
- 包加载错误入口改为页签下方的紧凑提示条，不占用顶栏动作位，不遮挡列表
- 手动刷新、市场安装成功、删除包和删除冲突源共用同一个包目录快照重载入口
- `PackageManager` 继续是脚本和 ToolPkg 的唯一运行时 owner
- 不改变市场协议、包格式、启用状态协议、环境变量保存协议或 Skill/MCP 内部页面结构
- 不新增第二份包目录、不保留旧 UI 开关、不提交、不推送、不安装或操作设备

## 文档结构

```text
package_manager_header_and_market_refresh/
    index.md
    1_interaction_and_visual.md
    2_state_and_validation.md
```

## 完成标准

1. 顶栏标题保持“包管理”，右侧动作顺序严格为环境变量、市场、添加、刷新
2. 窄屏下四个动作保持 Material 最小触摸目标，标题不被替换为搜索框
3. 搜索框固定为独立紧凑行，并继续按当前页保存和应用各自搜索状态
4. “插件 / 脚本包 / 技能 / MCP”固定在搜索框下方
5. 插件与脚本包列表不再为浮动按钮保留 `120dp` 底部空白，开关不再被覆盖
6. 包加载错误通过非悬浮提示条进入现有详情对话框
7. 市场成功安装脚本或插件后，包管理无需重启即可重新读取列表、启用状态和加载错误
8. 手动刷新与自动刷新走同一数据重载函数，并在加载时禁用重复刷新
9. 动作顺序、目录修订信号、关键源码结构测试、正式门禁和 Debug APK 构建通过
10. 目标设备上的窄屏布局、触摸、输入法和市场安装返回路径单独记录

## 当前结果

本轮已完成本地实现、自动验证和 Debug APK 构建；Kiyori 当前仍未发布，因此旧的包管理浮动
按钮与顶栏搜索方案没有保留。目标设备上的视觉、触摸和市场安装返回路径仍保持
`verification_pending`。

本地证据：

- `PackageManagerVisualPolicyTest`、`MarketInstallStateStoreTest`、
  `PackageCategoryUiPolicyTest` 和 `PackageEnvironmentVariablesPolicyTest` 共 `19/19` 通过
- `:app:compileDebugKotlin` 通过
- 正式开发准备门禁通过
- 7 个语言目录的新增资源键全部存在；XML 解析通过
- 8 份相关 Markdown 本地链接检查通过；关键源码合同检查通过；`git diff --check` 通过
- `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain` 成功生成
  `app/build/outputs/apk/debug/app-debug.apk`
- APK 大小 `475317754` 字节，SHA-256
  `8807B82473E086428396132915FD44D40D48ACB43C64A6AA25995A4A84C9227B`
- APK 元数据为 `com.kiyori / versionCode 45 / versionName 0.1.0 / arm64-v8a`，
  Android Debug V2 签名和 `zipalign -c -P 16 4` 均通过
