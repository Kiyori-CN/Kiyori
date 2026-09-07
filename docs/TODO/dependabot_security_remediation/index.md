# Dependabot 安全告警修复

## 范围与计划

- 修复 `web-chat/package-lock.json` 中 `browserslist` 与 `postcss` 的传递依赖告警。
- 将 `tools/mcp_bridge` 的直接 `uuid` 依赖升级到首个修复版本，并重新生成 Android bridge bundle。
- 不修改引导页、terminal 子模块或无关依赖。

## 验收

- [ ] 依赖树中不存在受影响版本。
- [ ] WebChat typecheck/build 通过。
- [ ] MCP bridge 编译、ncc 打包及 Android assets 复制通过。
- [ ] Debug APK 构建并审查产物。
- [ ] 提交推送后复核三个 GitHub Dependabot 告警状态。
