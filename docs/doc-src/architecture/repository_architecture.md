# Kiyori 仓库与源码架构

本文描述当前实现的工程边界和代码归属，适用于定位修改、评估依赖方向和选择重构范围。
产品状态与生命周期以 [CONTEXT](../../../CONTEXT.md) 和 [领域契约](../contracts/README.md)
为准；目录规则见 [仓库布局](../dev-core/REPOSITORY_LAYOUT.md)，未实施重构见专项 TODO。

## 工程边界

| 边界 | 当前实现 | 所有权 |
| --- | --- | --- |
| Android 组装 | 根 settings 中九个模块，`:app` 直接消费八个 Android library | app 配置产品、资源、依赖、Variant 和最终 APK |
| 构建逻辑 | `buildSrc` 独立编译任务类型；app 注册并供应输入 | 构建代码不进入运行时，不依赖应用源码 |
| AI 与产品 | app 内的 `com.ai.assistance.operit` 与 `com.kiyori` 双包根 | 分界由实际领域与兼容消费者决定，不按品牌机械替换 |
| Native 与 IPC | 各 Android library 的 JNI/CMake，以及稳定 AIDL、组件和动态加载名称 | 各模块持有绑定和实现，APK 只接收经过验证的固定输入 |
| WebChat 与扩展 | `web-chat`、`examples`、`tools/mcp_bridge` 等独立 JS 工程 | npm lockfile 管依赖；明确生成或分发入口 |
| 仓库检查 | `ci/script`、`ci/test`、`config/architecture` | 可复现检查、对应回归与机器契约分开 |
| 宿主工具 | `tools/<职责>` | 构建、诊断、设备和服务操作入口不与应用源码混放 |
| 文档 | 根入口、正式领域文档、TODO、派生目录 | 定义、流程、当前计划和历史证据各有主要载体 |

`terminal` 是独立 Git 子模块；其他 Android library 是父仓库源码。`tools/shower` 是自有
Wrapper 的辅助工程，不能与 `:showerclient` 混同。`buildSrc` 不是第十个 Android 模块。

## app 包结构

```text
app/src/main/java/
├── com/kiyori/
│   ├── app/                 Application、根组合、壳、首启门控与窗口编排
│   ├── capability/          browser、settings、AI memory、扩展市场等能力契约
│   ├── design/              共享语义颜色与主题基础
│   ├── integration/operit/  Operit UI/运行时适配、导航与首启集成
│   └── platform/            Android、日志、权限、存储、网络、生命周期与窗口基础
├── com/ai/assistance/operit/
│   ├── api/                 模型供应商与协议接入
│   ├── core/                AI、工具、浏览器、媒体、工作区等现有运行时
│   ├── data/                模型、偏好、仓库与持久化
│   ├── services/            Android 服务与长期执行
│   ├── ui/                  AI 与尚在原包根的产品领域展示
│   └── ...                  集成、插件、稳定 provider 与其他已有领域
└── ...                      明确隔离的第三方绑定与兼容源码
```

当前 `com.kiyori.feature` 是后续领域迁移的目标，不是已经存在的完整功能模块树。
Operit 根中仍有产品实现，也不能把整个根称作纯上游或永不修改的兼容区。
`package-ownership.toml` 对源码归属、允许与禁止的项目 import 进行实际约束。

## 依赖与状态方向

1. 应用壳装配领域能力与集成适配，持有全局导航；兼容 Activity 保留 Android 系统入口。
2. 展示层读取真实 owner 的状态并发出命令，不能重新创建 Browser、Player、Download 或权限存储。
3. capability 表达消费者需要的命令和事实；integration 适配具体实现，不把 UI 状态变成第二份持久化事实。
4. 数据与工具层避免依赖具体 Activity、Composable、ViewModel 或展示模型；Android 平台事实仍由平台组件持有。
5. 第三方包、JNI/AIDL 和序列化身份通过显式契约保护，内部目录变化不能隐含改变外部名字。

人工与 AI 浏览器工具共用 `StandardBrowserSessionTools.getSharedInstance`；播放器通过
`PlayerSession` 连接独立 `:player` 进程；终端通过固定子模块的服务和 AIDL 连接。
文件、代理、下载、工作区与扩展各自的失败和恢复边界继续遵守领域契约。

## 构建输入与生成物

- 版本与依赖坐标由 version catalog、Wrapper、Gradle properties 和已提交 lockfile 定义。
- app 的自定义任务类型来自 `buildSrc`，实例仍由 app 注册；Android SDK/NDK 和生成源通过公开 Variant API 连接。
- AAR、models、subpack、JNI 等被忽略的本机输入有真实消费者，不能与缓存一起清理。
- 生成 ToolPkg、ripgrep、shell launcher、Mihomo 和 WebChat assets 使用既有生成入口，不新增源码目录里的预编译副本。
- 构建成功只证明本机输入与任务图可用；外部制品完整内容寻址、冷缓存重建、设备及发布仍有各自验收。

## 演进条件

提取 Android library 前必须证明依赖无环、资源和第三方输入的唯一归属、可独立测试或构建的
实际收益。仅为视觉整齐移动八个模块到新根目录会扩大 Gradle、CMake、脚本及外部路径影响，
不能增加这些收益。当前先按已存在的领域 owner 和构建职责拆分，避免空模块与公共杂物层。

每次迁移同步调用方、测试、CI 路由和正式文档；稳定名称变化单独给出兼容设计。历史 TODO
中的目标结构和交付授权仅描述当时计划，不能覆盖当前工作规则。已知未完成领域继续在
[全项目架构专项](../../TODO/kiyori_architecture_refactor/index.md) 追踪。
