---
fork: https://github.com/Kiyori-CN/Kiyori
status: verification_pending
baseline: c036a03e
date: 2026-07-29
deadline: 2026-07-30 10:00 Asia/Shanghai
---

# 全量 UI 与功能逻辑链路审计优化

## 目标

本阶段在固定 Kiyori 浅色、深色与跟随系统主题，以及 `KiyoriSemanticTone` 已经建立的基础上，
对当前仓库全部用户可达界面和既有功能链路进行一次系统审计与收口。

任务只有两个目标：

1. 全面优化现有 UI 的视觉质量、主题适配、图标语义、排版、间距、状态反馈和跨页面一致性
2. 检查现有功能从入口、导航、状态 owner、执行层、持久化到结果反馈的完整链路，并修复真实缺陷

本阶段不新增产品能力。空入口、尚未建立 owner 的页面和规划中的功能不能因为视觉优化而获得伪实现。

## 权威边界

- Kiyori 尚未发布，未形成用户合同的旧 UI 内部方案可以直接清理
- `CONTEXT.md` 中的产品术语、唯一状态 owner、Browser Runtime、Player Runtime 和兼容标识保持权威
- 既有专项 TODO 继续记录对应功能的历史和专项验收；本目录只作为本轮横向审计的唯一进度载体
- 应用主题只允许固定浅色、深色和跟随系统；AI 对话局部外观不能影响应用壳、设置、工具或浏览器
- 不建立第二 WebSession、第二播放器、第二下载数据库、第二设置 owner 或平行导航状态
- 不增加 fallback、降级、兜底或伪成功路径

## 文档

- [UI 覆盖矩阵与设计规则](1_ui_inventory_and_design.md)
- [功能逻辑链路矩阵](2_function_chain_inventory.md)
- [实施阶段与验证门禁](3_implementation_and_validation.md)

## 串行里程碑

1. [DONE] 确认 Goal、授权边界、Git 基线、正式开发门禁和既有任务状态
2. [DONE] 建立全量页面、组件、特殊渲染域和功能链路矩阵
3. [DONE] 完成共享视觉组件、主产品页面和设置体系收口
4. [DONE] 完成 AI、记忆、包管理、权限、工作流与工具箱页面收口
5. [DONE] 完成浏览器、下载、播放器及全部抽屉、Sheet、Dialog 和 Overlay 复核
6. [DONE] 修复功能链路中的阻塞、竞态、生命周期、取消、资源释放和错误反馈缺陷
7. [DONE] 反向审查全部修改，清理旧配色、重复实现、无效交互和范围外变化
8. [DONE] 完成自动测试、正式门禁、Debug APK 构建与产物审计
9. [VERIFICATION PENDING] 目标设备上的视觉、手势、系统栏、输入法、横竖屏和真实系统能力验收

## 当前基线

- 分支与提交：`main@c036a03e`，`origin/main` 指向同一提交
- 工作树：已有设置、主题和视觉统一修改，必须保留并继续在其上工作
- Compose 文件：`ui/features` 约 269 个，另有 `main`、`floating`、`common`、恢复页和系统覆盖层
- 正式开发准备：`check_formal_readiness.py --require-main` 已通过
- 设备、模拟器、提交、推送、发布和部署均不属于本轮授权

## 已完成的实现收口

- 设置首页和拆分后的账号、AI 对话器、语音合成器、小程序管理页采用统一设置外壳；小程序管理只保留诚实的空状态，不接入 AI 包管理
- 删除用户颜色对应用全局主题的控制，只保留固定 Kiyori 浅色、深色和跟随系统；AI 对话的头像、气泡、背景和输入区个性化继续局部生效
- 建立稳定 ID 驱动的语义彩色图标和状态色体系，并收口设置、负一屏、AI、包、权限、工作流、工具箱、浏览器抽屉、播放器日志和恢复页
- 文件管理、负一屏和设置首页中的规划入口改为明确禁用或静态状态，不再用空点击伪装可用功能
- 修复头像组合期阻塞、导出取消与临时文件清理、ZIP 路径越界、权限请求竞态、协程生命周期、WebView 销毁、资源释放和错误不可见等既有链路缺陷
- 保持单一 Browser Runtime、Player Runtime、下载管理器和设置 owner；未新增功能、数据库、运行时、协议或 fallback

## 已完成的自动验证

- `:app:testDebugUnitTest`：100 个报告文件、660 个测试、0 failure、0 error、0 skipped
- `:app:compileDebugAndroidTestKotlin`：通过
- `:app:lintDebug`：通过；未更新 baseline、未关闭规则，报告剩余 234 个 warning 和 5 个 hint
- 正式开发准备检查：通过
- `git diff --check`：通过，仅有 Git 的 CRLF 转换提示
- `:app:assembleDebug` 与 `verifyDebugPlayerRuntimePackaging`：通过
- APK：`app/build/outputs/apk/debug/app-debug.apk`，482,615,791 bytes
- SHA-256：`69CB31B66209DB45DD4C7273E96653208AF9F6C227F0A41F7C6960BCBB670EFC`
- 包信息：`com.kiyori`，`0.1.0 (45)`，minSdk 26，targetSdk 34，ABI `arm64-v8a`
- 签名：Android Debug，APK Signature Scheme v2 验证通过
- `zipalign -c -P 16 4`：退出码 0

## 完成定义

只有以下条件同时满足时，本地实施可以结束：

- UI 与逻辑矩阵中的每个域都有代码证据、明确结论和完成状态
- 所有修改都保持唯一状态 owner 和已有协议
- 范围内发现的真实缺陷已经修复或以证据说明为什么不能在本轮处理
- 旧全局颜色、普通页面固定浅色和无语义单色图标完成反向检查
- 相关定向测试、必要的全量 JVM 回归、正式准备检查、差异检查和 Debug 构建通过
- 最终 APK 的路径、哈希、包信息、签名和 16 KB ZIP 对齐经过核验
- 未执行的设备验收明确保持 `verification_pending`
