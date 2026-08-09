---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# 包管理环境变量配置抽屉

## 目标

把 AI 首页左侧抽屉“包管理”原右下角第一个按钮打开的环境变量配置弹窗，替换为与浏览器
子页面一致的三态可拖动底部抽屉。面对大量工具包和变量时，用户应能先按工具包类型分类和搜索，
再按工具包展开所需内容，不必在一张固定高度弹窗中持续向下滑动。

## 已确认边界

- Kiyori 始终未发布，旧弹窗 UI、状态和专用实现直接删除
- `ToolPackage.env` 继续定义变量名、说明、必填状态和默认值
- `EnvPreferences` 继续是唯一环境变量持久化 owner
- 同名变量在多个工具包中出现时继续共享同一草稿值和保存结果
- 下拉关闭、遮罩点击、系统 Back 和“取消”均丢弃本次草稿
- 只有明确点击“保存”才合并写入现有环境变量集合
- 不改变包启用前的必填变量校验，不修改 MCP 环境变量配置
- 不新增回退、降级、兼容开关或第二条保存链路

## 文档结构

```text
package_environment_variables_drawer/
	index.md
	1_interaction_and_state.md
	2_implementation_and_validation.md
```

## 完成标准

1. 环境变量入口打开三态底部抽屉，部分展开、全屏展开和下拉关闭行为与浏览器一致
2. 标题区显示工具包、变量和必填未配置摘要
3. 搜索匹配工具包显示名、内部包名、变量名和变量说明，不匹配变量值
4. 横向分类提供全部和当前工具包声明中的动态类型，例如 `Automatic / Chat / Draw / Search`
5. 类型按英文名称 A 到 Z 排列，工具包按英文或中文拼音首字母排列
6. 内容按类型和工具包显示名稳定分组，可独立收起和展开
7. 每个已登记类型使用独立的浅深主题颜色组合和匹配其含义的图标
8. 工具包头使用紧凑两行结构和 `32dp` 类型徽标，不再显示名称首字母
9. 横向分类条、环境变量分组和脚本包页面使用同一分类视觉与排序策略
10. 搜索时匹配组自动展开，清空搜索后恢复用户折叠状态
11. 保存继续写入唯一 `EnvPreferences`，取消不产生持久化修改
12. 旧 `PackageEnvironmentVariablesDialog` 与旧弹窗状态引用归零
13. 定向测试、正式开发准备检查、差异检查和 Debug APK 构建通过
14. 目标设备上的拖动、滚动、输入法和触摸结果单独记录
15. 每次打开时只自动展开所有存在未填写必填变量的工具包，其余工具包默认收起

## 当前结果

首轮抽屉替换、分类视觉、紧凑密度、全页面统一排序和每次打开的缺失必填项自动展开已经完成。
目标设备上的拖动、滚动、输入法、搜索、分类、折叠和保存结果仍需单独验收，因此本任务保持
`verification_pending`，不以本地 Compose 编译或 APK 构建替代。

本轮本地证据：

- 四个相关测试类共 `23/23` 项通过，失败、错误和跳过均为 `0`
- 17 类图标映射和浅深主题颜色唯一性通过；浅色图标/容器最低对比度 `4.13:1`，深色最低
  `6.09:1`
- 内置脚本类型覆盖检查通过，旧弹窗、旧状态、旧人工类型排序和字母头像反向扫描均为 `0`
- `:app:compileDebugKotlin`、正式开发准备检查、7 个 `strings.xml`、7 个工作树 Markdown 链接
  和 `git diff --check` 通过
- 默认展开策略确认同时展开所有缺失必填项的包，填写完成或只有可选变量时默认全收起
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，大小 `475317754` 字节，
  SHA-256 `263252033D05E2E5FBCAECF882CAE1688F2032D329D1248070472CF7AE439776`
- APK 为 `com.kiyori / versionCode 45 / versionName 0.1.0 / arm64-v8a`，Android Debug
  V2 签名和 `zipalign -c -P 16 4` 均通过
- 本轮未安装 APK、未执行 ADB/MuMu/真机操作，未创建提交或推送

详细交互见
[`1_interaction_and_state.md`](1_interaction_and_state.md)，实施与验证见
[`2_implementation_and_validation.md`](2_implementation_and_validation.md)。
