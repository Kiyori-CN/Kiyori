# 上游与市场兼容审计

## 版本边界

- `1.12.0+8`：`3f14605799bedbb79eeb11a31ecca95eff03c7cc`
- `1.12.0+9`：`4bf0138b8ce8fa7e635f5dd7273fbe78a6fe1355`
- `+9` 后角色卡发送修复：`fe0c8767e06ee57ffe72367ee65a559e34c93d1b`
- 当前审计上游：`93a28251f573227b8fc4ae536b4020deb1cb18e5`

## 市场证据

对 `https://static.operit.app/market/v2/lists/all/updated/` 的 13 个分页进行只读解析，
共读取 `1257` 项。19 个最新 ToolPkg 声明 `minAppVer=1.12.0+9`，覆盖聊天消息 Hook、
聊天输入 Hook、Prompt Hook、Compose DSL 路由、桌面组件、资源读取、IPC，以及
`Tools.Chat / Files / Memory / Net / SoftwareSettings / System`。

所有 19 个最新发布资产均下载到内存并按市场 SHA-256 校验通过。审计不安装或执行第三方包，
只读取清单、源码文本和注册入口。

## 结论

Kiyori 不能只把兼容版本字符串改成 `+9`。必须先补齐缺失的公共接口、Hook 生命周期和
与这些接口直接相关的 AI 对话状态链，再更新市场兼容基线。

[DONE]
