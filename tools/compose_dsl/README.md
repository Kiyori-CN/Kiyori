# Compose DSL 制品生成器

生成器从 Gradle 缓存中的 Compose source JAR 提取组件声明，同步 Kotlin 渲染入口与 TypeScript 类型绑定。它会修改源码制品；只在需要更新 Compose DSL 绑定时执行。

## 输出

| 制品 | 路径 |
| --- | --- |
| Kotlin registry | [ToolPkgComposeDslGeneratedRegistry.kt](../../app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRegistry.kt) |
| Kotlin 渲染器 | [ToolPkgComposeDslGeneratedRenderers.kt](../../app/src/main/java/com/ai/assistance/operit/ui/common/composedsl/ToolPkgComposeDslGeneratedRenderers.kt) |
| TypeScript 绑定 | [compose-dsl.material3.generated.d.ts](../../examples/types/compose-dsl.material3.generated.d.ts) |

## 运行

在仓库根目录使用项目 `.venv`：

```powershell
.\.venv\Scripts\python.exe -B tools/compose_dsl/generate_compose_dsl_artifacts.py
```

Linux/macOS 使用 `.venv/bin/python`。源 JAR 从 `~/.gradle/caches/modules-2/files-2.1` 解析；缺少时先同步或构建依赖。

## 生成约束

- TextField、Icon、Card、Surface 等复杂核心节点保留专用渲染器。
- 自动发现 Material3/Foundation 的公开 `@Composable` 函数；必需参数可映射时才生成通用渲染器、registry 和 TypeScript props。
- `ToolPkgComposeDslGeneratedRenderers.kt.tpl` 只保留框架与辅助函数，组件实现注入到 `// __GENERATED_COMPONENT_RENDERERS__`。
- 生成后同时审阅三份制品并验证受影响的 Compose DSL 调用，不能仅凭脚本退出码确认运行时兼容。
