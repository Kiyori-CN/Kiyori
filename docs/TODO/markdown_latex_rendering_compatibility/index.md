---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Markdown/LaTeX 公式兼容性与化学渲染

## 当前状态

AI 对话公式继续使用 Compose 原生 `StreamMarkdownRenderer`、
`CanvasMarkdownNodeRenderer`、`DisplayMathBlock` 和
`ru.noties:jlatexmath-android:0.2.0`。源码兼容、化学转换、布局、诊断、JVM 测试源码和
Android 后端/流式结构测试源码已经写入；定向 JVM、AndroidTest 编译、正式准备检查、
差异/链接检查和 Debug APK 构建均已通过。目标设备视觉与交互验收尚未执行，因此状态保持
`verification_pending`。

已写入的修复覆盖 `\lvert`/`\rvert` 双线变体、`\ `、反斜杠物理换行、基础
`\ce{...}`、超宽公式和失败源码布局，以及原始/预处理公式、后端版本、命令和位置诊断。

## 本轮意图

在现有唯一 JLaTeXMath 后端内完成可验证的兼容收口：

1. 精确规范化缺失的竖线命令和反斜杠换行，不改写已被当前后端支持的命令
2. 添加覆盖本轮验收样例的 `\ce{...}` 化学语法预处理，并保持化学内容在同一
   JLaTeXMath Drawable 中绘制
3. 将正常公式的宽度策略固定为轻度自适应缩放加横向滚动，将失败源码固定为有失败标识且
   可换行的内容区域
4. 在开发日志中记录原始内容、预处理内容、后端版本、异常、命令和位置
5. 覆盖静态解析、流式未闭合/闭合、多公式块、代码保护、基础公式和化学样例的自动化回归

## 范围

- `app/src/main/java/com/ai/assistance/operit/ui/common/displays/`
- `app/src/main/java/com/ai/assistance/operit/ui/common/markdown/`
- 公式相关 JVM/Android 测试
- 公式错误提示所需的现有 `common_render_failed` 资源复用
- `CONTEXT.md`、`README.md`、`docs/doc-src/architecture/RENDERER_ARCH.md`
  和本目录下的实施记录

## 非目标与硬边界

- 不引入 WebView、KaTeX、MathJax 或第二套公式状态源
- 不把普通公式失败时静默替换为另一渲染后端
- 不全局删除反斜杠，不针对一条完整示例字符串硬编码
- 不改变 Markdown 代码段、代码块、块引用和流式节点所有权
- 只更新 `docs/TODO/README.md` 中本轮 Markdown/LaTeX 与 Browser 段落，保留其余并行改动
- 不安装 APK、不执行 ADB/MuMu/真机操作、不提交、不推送

## 本地验证摘要

- Browser 与 Markdown/LaTeX 联合定向 JVM 共 `8` 个测试类、`63/63` 通过，失败、错误和跳过均为 `0`
- `:app:compileDebugAndroidTestKotlin` 成功，公式 Android 后端和流式结构测试源码完成编译
- 项目 `.venv` 的 formal readiness、`git diff --check` 和本轮 Markdown 本地链接检查通过
- `:app:assembleDebug --no-daemon --console=plain` 成功；Debug APK 的包名、版本、ABI、
  Android Debug v2 签名和 16 KB ZIP 对齐均已核验
- 目标设备上的公式视觉、横向拖动与纵向滚动协同、流式输入时序和不同屏宽仍待验收

## 验收矩阵

| 类别 | 关键输入 | 验收证据 |
| --- | --- | --- |
| 基础回归 | `x = \frac{1}{2}`、积分、矩阵 | JVM 预处理/布局测试，Debug 构建 |
| Dirac 符号 | `\lvert`、`\rvert`、`\langle`、`\rangle`、`\mid` | 兼容映射测试、Android 渲染测试源码编译 |
| 特殊符号 | 集合逻辑、`\therefore`、`\because`、单反斜杠换行 | 换行规范化测试、Android 渲染测试源码编译 |
| 化学 | 三个方程式和核反应式 | `\ce` 预处理测试、Android 后端测试源码编译 |
| 结构 | 多公式块、代码保护、流式未闭合/闭合 | Markdown splitter/renderer 测试 |
| 布局 | 超宽成功公式、失败源码、窄屏容器 | 纯布局策略测试、Debug 构建 |
| 诊断 | 未知命令、错误位置和后端版本 | 诊断格式/位置测试 |

详细实施步骤见：

- [`1_root_cause_and_contract.md`](1_root_cause_and_contract.md)
- [`2_compatibility_and_chemistry.md`](2_compatibility_and_chemistry.md)
- [`3_layout_and_diagnostics.md`](3_layout_and_diagnostics.md)
- [`4_verification.md`](4_verification.md)
