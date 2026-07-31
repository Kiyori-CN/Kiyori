---
status: accepted_design
plan_version: 3
last_reviewed: 2026-07-31
---

# 风险登记与停止条件

## 风险等级

- `P0`：可能损坏开发数据、外部协议、系统入口或无法恢复，立即停止
- `P1`：会阻断上游同步或主要功能，需要当前里程碑内解决
- `P2`：局部行为或维护性风险，可在当前领域关闭前解决
- `P3`：文档、命名或低影响清理，不能掩盖 P0/P1

## 风险矩阵

| ID | 风险 | 等级 | 触发证据 | 预防 | 停止条件 |
| --- | --- | --- | --- | --- | --- |
| R-01 | 全局替换误改协议或数据 | P0 | stable contract scan 出现差异 | contract inventory + allowlist | 任一 FROZEN 值变化 |
| R-02 | 序列化 FQCN 无法读取旧 JSON | P0 | workflow fixture/decode 失败 | 保留类型名或独立迁移 | 旧 fixture 不能读取 |
| R-03 | WorkManager worker 无法实例化 | P0 | 已排队任务启动失败 | 稳定 worker entrypoint | 已有任务变为 failed |
| R-04 | Android 系统组件失效 | P0 | Manifest/设备角色/Widget 失败 | KEEP-CONTRACT + component matrix | 默认助手/通知/Widget/shortcut 丢失 |
| R-05 | JNI 导出或 AIDL 断裂 | P0 | native link/IPC test 失败 | 普通 move 不触碰 native contract | 任一 native/IPC 入口失败 |
| R-06 | Browser 出现第二状态 owner | P0 | 两个 registry/WebView/store | capability 单 owner 检查 | 第二 session/WebView/store 出现 |
| R-07 | Player Surface lease 语义变化 | P0 | floating/fullscreen 回归失败 | Player 特征测试 + device test | reload、黑屏、双 Surface 或 native 崩溃 |
| R-08 | 当前文件夹备份不完整 | P0 | 未提交文件、私有配置或 refs 不在备份中 | working-tree snapshot + bundle + allowlist | 任一用户开发文件无法恢复 |
| R-09 | 上游同步冲突被整文件覆盖 | P1 | A/B/C/D 审查缺失 | sync baseline + manual conflict | 无法解释的 ours/theirs |
| R-10 | UI 行为被目录整理改变 | P1 | route/back/theme test 差异 | 纯移动提交 + characterization tests | UI/route/Back 行为变化 |
| R-11 | namespace 提前变化 | P1 | R/BuildConfig/Manifest 大面积变化 | phase 1 freeze | namespace 在非 RFC 批次变化 |
| R-12 | `util` 或 `core` 重新成为垃圾目录 | P2 | 新文件无 owner | ownership manifest review | 新增未分类大类 |
| R-13 | 文档与源码边界漂移 | P2 | matrix 路径不存在或漏项 | docs and CI gate | docs 不能映射源码 |
| R-14 | 构建产物或私密配置进入提交 | P0 | Git hygiene 检查失败 | staged-content audit | APK、bundle、key、local config 入索引 |
| R-15 | terminal 被间接修改 | P0 | gitlink/submodule diff 变化 | explicit EXCLUDE | terminal gitlink 或内容变化 |
| R-16 | 上游新提交覆盖 Kiyori owner | P1 | overlap path 未分类 | sync ownership zones | C 区被直接覆盖 |
| R-17 | 兼容入口演变成第二实现 | P1 | old/new 均持有状态 | adapter-only review | 两份事实源或并行生命周期 |
| R-18 | 只验证编译未验证用户数据 | P1 | APK 通过但旧数据未启动 | device data acceptance | 数据/系统入口未实测却宣称完成 |
| R-19 | bundle 只验证格式但不能真实恢复 | P0 | 未做临时克隆或 `git fsck` | bundle restore drill | refs、M-00 或安全分支不能 checkout |
| R-20 | M-01 范围由字符串替换失控 | P1 | 超出 16 个允许实现文件 | exact manifest + normalized diff gate | 出现非命名逻辑差异 |

## 反向审查问题

每个里程碑结束后必须回答：

1. 如果旧设备数据已经存在，启动时读取的是哪个文件、哪个类和哪个 owner？
2. 如果 WorkManager 在更新前已排队，更新后谁实例化它？
3. 如果第三方脚本仍通过旧 FQCN 访问，哪个类响应？
4. 如果 Android 系统保存旧组件名，Manifest 仍能解析吗？
5. 如果上游同时修改了同一文件，如何证明没有整文件覆盖 Kiyori 行为？
6. 如果新包编译通过但旧协议失败，哪个 gate 能阻止交付？
7. 如果 Browser UI 和 AI 工具同时操作，状态是否来自同一 registry？
8. 如果 Player 进程重启，Surface、request 和日志状态如何恢复？
9. 如果备份只包含 Git bundle，未提交文档和本机私有配置由什么证据恢复？
10. 如果任务中断，最近一个可恢复点在哪里？

## 停止后的处理顺序

1. 停止后续源码写入
2. 保存命令、错误、Git 状态和 APK/数据状态
3. 更新任务日记和风险 ID
4. 判断是否可在当前里程碑内修复
5. 若不可修复，回到最近安全点并等待用户决策
6. 不通过删除测试、扩大 allowlist、隐藏异常或引入第二实现来继续

## 风险接受

只有用户明确接受某个残余风险，且该风险不属于 P0 数据/协议/系统入口破坏时，才能标记为
`accepted_risk`。`verification_pending` 不是风险接受，也不是验证替代。
