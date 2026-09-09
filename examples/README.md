# 脚本与 ToolPkg 开发示例

本目录保存 Kiyori 内置脚本、ToolPkg 源码与共享类型声明。面向扩展作者的起点是 [脚本开发指南](../docs/SCRIPT_DEV_GUIDE.md)；本页负责选择示例、说明参数契约与本地调试入口。

## 选择示例

| 目标 | 入口 |
| --- | --- |
| 注册自定义 AI 供应商 | [custom_ai_provider](custom_ai_provider/README.md) |
| 调用 AssemblyScript WASM | [toolpkg_wasm_demo](toolpkg_wasm_demo/README.md) |
| 在 Android 加载 APK 逆向运行资源 | [APK 逆向资源](apktool/resources/apktool/README.md) |
| 连接 Windows 代理 | [Kiyori PC Agent](windows_control/resources/pc_agent/kiyori-pc-agent/README.md) |
| 处理 Word/Excel/PPT/PDF | [办公文档套件](office_suite/manifest.json) · [工作区模板](../app/src/main/assets/templates/office/README.md) |
| 查询共享 API 与结果类型 | [类型总入口](types/index.d.ts) · [API 文档](../docs/doc-src/package-dev/index.md) |
| 了解包结构、资源与生命周期 | [ToolPkg 格式](../docs/TOOLPKG_FORMAT_GUIDE.md) |

独立 JavaScript 包以文件顶部 `METADATA`、工具函数和公共导出描述能力。目录型 ToolPkg 使用 `manifest.json` 声明入口与资源；历史 HJSON 输入保留兼容用途，新包采用当前指南中的 JS/TS 或 ToolPkg 结构。各示例的构建命令与生成产物以自身 `package.json` 和 README 为准。

## 参数与返回值

Kotlin 桥接根据每个工具 metadata 的 `type` 与 `required` 转换参数，然后调用 JavaScript/TypeScript。作者应保持 metadata 与实际函数类型一致：

- `required: false` 对应 TypeScript 可选字段 `?`。
- 不重复添加用于兼容旧透传行为的 string → number/boolean/array/object 转换或必填检查。
- 枚举、业务范围、跨字段依赖、嵌套对象/数组结构等 metadata 无法完整表达的规则，仍由工具实现验证。
- 工具调用返回 Promise，使用 `await` 后按真实结果结构读取字段。
- 独立脚本按宿主契约调用 `complete(...)` 返回终态；ToolPkg 按注册入口的返回契约实现。

文件、网络、系统及 UI 接口分别见 [Files](../docs/doc-src/package-dev/files.md)、[Network](../docs/doc-src/package-dev/network.md)、[System](../docs/doc-src/package-dev/system.md) 和 [UI](../docs/doc-src/package-dev/ui.md)。API 签名以 `types/` 为准，本页不再复制一份可能漂移的方法清单。

## 在 Android 调试脚本

需要兼容的 Kiyori Debug 应用、已授权 USB 调试的 Android 设备，以及 PATH 中的 ADB。以下命令会在设备执行脚本；先核对脚本的实际副作用与目标设备。

在仓库根目录运行，替换示例路径、函数名和参数文件：

```powershell
.\tools\adb\execute_js.bat "<script.js>" "<function_name>" "@params.json"
```

Linux/macOS：

```bash
./tools/adb/execute_js.sh "<script.js>" "<function_name>" "@params.json"
```

`params.json` 必须包含有效 JSON。`@参数文件` 避免 Windows shell 对引号和特殊字符的多次解释；完整参数、环境文件与排障见 [ADB 脚本说明](../tools/adb/JS_ADB_README_zh.md)。

使用 `run_sandbox_script.*` 或包内 `debug_run_sandbox_script` 的 `source_code` 时，代码按顶层脚本执行。使用 `console.log(...)`、`emit(...)`、`complete(...)`，不要假定存在工具函数的 `params.xxx` 或 `intermediate(...)`。

## 包隔离与验证

- 每个包独立声明并导出公共函数。跨包组合前明确模块边界，不把多个示例直接拼接到同一全局作用域。
- TypeScript 本地构建遵循对应 `tsconfig.json` 与打包命令；不能把任意 ES Module import 当作宿主直接支持的入口。
- 修改后执行该包声明的类型检查与打包验证，再在应用中验证注册、参数、结果和生命周期。
- ADB 日志、密钥、环境配置与生成的包留在本地；生产预置清单和资产生成流程见 [可复现开发](../docs/TODO/formal_development_readiness/2_reproducible_development.md)。
