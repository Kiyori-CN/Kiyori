# 实施阶段与验证门禁

## 阶段 1：共享视觉与主产品页面

- 完善语义图标的稳定 ID 映射和紧凑尺寸
- 收口 `CustomScaffold`、通用空态、加载态和错误态的使用边界
- 优化 App Shell、软件首页、负一屏、设置体系、文件管理页、About、Help 和 Agreement
- 保留规划中的空入口，不增加行为
- 运行主题、Shell、设置和导航相关测试及 Kotlin 编译

## 阶段 2：AI、记忆、包、权限与工作流

- 优化 AI 系统动作、历史、附件、导出和通用 Dialog
- 保留 AI 气泡和输入区局部个性化
- 统一记忆搜索、文件夹、图谱状态和浮动动作
- 覆盖包管理深层详情、市场、MCP、Skill 和发布弹窗
- 统一权限工具和工作流详情、节点、调度与日志
- 修复已确认的主线程阻塞、取消、竞态和错误反馈问题
- 运行对应 ViewModel、repository、route 和 UI policy 测试

## 阶段 3：浏览器、下载与播放器

- 逐项复核 Browser Home、chrome、窗口概览、所有 Drawer、Sheet、Dialog 和 Overlay
- 验证书签、历史、下载、脚本、媒体、网络、UA 和网页 Dialog 的显示与返回状态
- 复核下载封板链路，避免本轮 UI 修改造成状态或操作回归
- 审计播放器控制层、悬浮层、日志和 Surface 状态的 UI 投影
- 不改变 Browser Runtime、Download Manager 或 Player Runtime
- 运行浏览器、下载、播放器的现有高信号回归

## 阶段 4：工具、浮窗、恢复与特殊渲染域

- 统一工具箱根和普通工具页的卡片、图标、空态、加载和错误
- 检查终端、代码、日志、图像、屏幕识别和自动化覆盖层的特殊主题边界
- 检查浮动 AI 的权限、关闭、模式切换和资源释放
- 检查启动、崩溃报告和数据恢复的状态表达
- 不把特殊内容域强制改成普通页面配色

## 阶段 5：反向审查

- 检查普通页面固定浅色和旧全局颜色标识
- 检查空 `onClick`，区分点击阻断、明确禁用、产品占位和真实失效入口
- 检查 `runBlocking`、`GlobalScope`、无 owner 协程、未释放监听器和过期异步响应
- 检查 Dialog、Drawer、Sheet、Overlay 的点击穿透、Back 和隐藏态交互
- 检查新代码是否引入 fallback、第二状态源、重复 helper 或范围外功能
- 检查文档、术语、状态 owner 和实际实现是否一致

## 分阶段验证

每个实现阶段先执行最窄检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

再运行该阶段对应的 JVM 测试类。涉及纯策略或状态映射时增加不依赖设备的单元测试。

## 最终验证

最终至少执行：

```powershell
.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
git diff --check
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

若全量测试存在与本轮无关的历史失败，必须记录测试名、现有基线和与差异的关系，不能隐藏或改低断言。

最终 APK 需要核验：

- 文件存在、大小、时间和 SHA-256
- `applicationId`、versionCode、versionName、minSdk、targetSdk 和 ABI
- Android Debug 签名
- `zipalign -c -P 16 -v 4`
- `verifyDebugPlayerRuntimePackaging`

## 设备验收队列

本轮不安装 APK、不操作设备。以下项目保持 `verification_pending`：

- 浅色、深色和跟随系统切换
- 状态栏、导航栏、输入法、横竖屏、折叠屏和大屏
- 所有抽屉、Sheet、Dialog、Overlay 的真实高度、遮罩和触控热区
- 浏览器 WebView、播放器 Surface、浮窗和系统权限交互
- TTS、STT、Shizuku、Root、文件选择、下载和真实网络

## 当前自动验证证据

已完成：

```text
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
BUILD SUCCESSFUL
100 个测试报告文件，660 tests，0 failures，0 errors，0 skipped

.\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
BUILD SUCCESSFUL

.\gradlew.bat :app:lintDebug --no-daemon --console=plain
BUILD SUCCESSFUL
0 errors，234 warnings，5 hints；未更新 lint-baseline.xml，未关闭规则

.\.venv\Scripts\python.exe -B ci\script\check_formal_readiness.py --repository . --require-main
PASS

git diff --check
PASS，仅输出 CRLF 转换提示
```

最终产物门禁：

```text
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
BUILD SUCCESSFUL

.\gradlew.bat :app:verifyDebugPlayerRuntimePackaging --no-daemon --console=plain
由 assembleDebug 执行并通过
```

APK 核验：

```text
路径：app/build/outputs/apk/debug/app-debug.apk
大小：482,615,791 bytes
SHA-256：69CB31B66209DB45DD4C7273E96653208AF9F6C227F0A41F7C6960BCBB670EFC
applicationId：com.kiyori
version：0.1.0 (45)
compileSdk：36
minSdk：26
targetSdk：34
ABI：arm64-v8a
签名：Android Debug，APK Signature Scheme v2
zipalign -c -P 16 4：退出码 0
```

本地代码、自动测试、构建和产物门禁均已完成。由于本轮没有设备授权，设备验收队列继续保持
`verification_pending`，不能把本地构建证据表述为真机视觉或系统交互验收。
